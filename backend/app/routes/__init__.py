from fastapi import APIRouter, Depends, Query, HTTPException, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
import logging
import os
import time
from urllib import request as urllib_request
from ..database.db import (
    get_db,
    AlertModel,
    OrderModel,
    HoldingModel,
    MutualFundModel,
    SipPlanModel,
    IPOModel,
    IPOApplicationModel,
    ETFModel,
)
from .dependencies import get_current_user
from ..models.schemas import (
    Quote, Holding, Order, OrderResponse, Alert, AlertCreate,
    AlertResponse, HealthCheck, TradeHistory, PortfolioSummary, PortfolioValue,
    Wallet, WalletTransaction, WalletResponse, MarketStatus,
    MutualFund, MutualFundCompareResponse, MutualFundRecommendationItem, MutualFundRecommendationResponse,
    SipPlanRequest, SipPlan, IPOListing,
    SipPlanUpdateRequest, IPOApplicationRequest, IPOApplicationResponse, IPOApplication, ETFInstrument
)
from .trading import (
    get_holdings, get_holding, place_order,
    is_market_open, get_wallet, add_funds, withdraw_funds
)
from ..market_data import (
    fetch_quote, fetch_quotes, get_all_symbols, get_default_symbols,
    search_stocks, get_symbols_with_names, get_stock_name, INDIAN_STOCKS
)
from ..ai_engine import analyze_stock, predict_price, ai_assistant
from ..portfolio_scorer import calculate_portfolio_health
from ..market_heatmap import get_market_heatmap, get_sector_detail

router = APIRouter()
logger = logging.getLogger(__name__)

_MF_NAV_SOURCE_URL = os.getenv("MF_NAV_SOURCE_URL", "https://www.amfiindia.com/spages/NAVAll.txt")
_MF_LIVE_CACHE_TTL_SECONDS = int(os.getenv("MF_LIVE_CACHE_TTL_SECONDS", "1800"))
_MF_LIVE_CACHE: dict[str, object] = {"fetched_at": 0.0, "funds": []}
_MF_SORT_FIELDS = {"name", "nav", "returns1y", "returns3y", "returns5y", "risk", "category"}


def _normalize_nav_date(value: str) -> str:
    text = value.strip()
    for date_format in ("%d-%b-%Y", "%d-%m-%Y", "%d/%m/%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(text, date_format).strftime("%Y-%m-%d")
        except ValueError:
            continue
    return text


def _derive_mf_category(category_context: str, scheme_name: str) -> str:
    token = f"{category_context} {scheme_name}".lower()
    if any(word in token for word in ["index", "nifty", "sensex", "etf"]):
        return "INDEX"
    if any(word in token for word in ["equity", "elss", "large cap", "mid cap", "small cap", "flexi", "focused"]):
        return "EQUITY"
    if any(word in token for word in ["debt", "bond", "gilt", "liquid", "money market", "ultra short", "credit risk"]):
        return "DEBT"
    if any(word in token for word in ["hybrid", "balanced", "arbitrage", "multi asset", "asset allocation"]):
        return "HYBRID"
    if any(word in token for word in ["solution", "retirement", "children"]):
        return "SOLUTION"
    if "fof" in token or "fund of funds" in token:
        return "FOF"
    return "OTHER"


def _derive_risk_level(category: str) -> str:
    return {
        "EQUITY": "HIGH",
        "INDEX": "MODERATE_HIGH",
        "DEBT": "LOW_MODERATE",
        "HYBRID": "MODERATE",
        "SOLUTION": "MODERATE",
        "FOF": "MODERATE",
        "OTHER": "MODERATE",
    }.get(category, "MODERATE")


def _risk_rank(risk_level: str | None, category: str | None = None) -> int:
    token = (risk_level or "").strip().upper()
    mapping = {
        "LOW": 1,
        "LOW_MODERATE": 1,
        "MODERATE": 2,
        "MODERATE_HIGH": 3,
        "HIGH": 3,
        "VERY_HIGH": 4,
    }
    if token in mapping:
        return mapping[token]

    category_token = (category or "").strip().upper()
    category_mapping = {
        "DEBT": 1,
        "HYBRID": 2,
        "SOLUTION": 2,
        "INDEX": 3,
        "EQUITY": 3,
        "FOF": 2,
        "OTHER": 2,
    }
    return category_mapping.get(category_token, 2)


def _fetch_live_mutual_funds(force_refresh: bool = False) -> list[MutualFund]:
    now = time.time()
    cached_funds = _MF_LIVE_CACHE.get("funds")
    fetched_at = float(_MF_LIVE_CACHE.get("fetched_at", 0.0) or 0.0)

    if (
        not force_refresh
        and isinstance(cached_funds, list)
        and len(cached_funds) > 0
        and (now - fetched_at) < _MF_LIVE_CACHE_TTL_SECONDS
    ):
        return cached_funds

    req = urllib_request.Request(_MF_NAV_SOURCE_URL, headers={"User-Agent": "BYSEL/1.0"})
    with urllib_request.urlopen(req, timeout=8) as response:
        payload = response.read().decode("utf-8", errors="ignore")

    category_context = ""
    fund_house_context = ""
    funds: list[MutualFund] = []
    seen_codes: set[str] = set()

    for raw_line in payload.splitlines():
        line = raw_line.strip()
        if not line:
            continue

        if ";" not in line:
            lowered = line.lower()
            if "schemes" in lowered and "(" in line:
                category_context = line
            elif "mutual fund" in lowered:
                fund_house_context = line
            continue

        parts = [part.strip() for part in line.split(";")]
        if len(parts) < 6:
            continue

        scheme_code = parts[0]
        if not scheme_code.isdigit() or scheme_code in seen_codes:
            continue

        scheme_name = parts[3]
        nav_text = parts[4]
        nav_date_text = parts[5]

        if not scheme_name:
            continue

        try:
            nav = float(nav_text)
        except ValueError:
            continue

        category = _derive_mf_category(category_context, scheme_name)
        funds.append(
            MutualFund(
                schemeCode=scheme_code,
                schemeName=scheme_name,
                category=category,
                nav=nav,
                navDate=_normalize_nav_date(nav_date_text),
                returns1Y=None,
                returns3Y=None,
                returns5Y=None,
                fundHouse=fund_house_context or None,
                riskLevel=_derive_risk_level(category),
            )
        )
        seen_codes.add(scheme_code)

    if not funds:
        raise RuntimeError("AMFI feed returned no mutual fund rows")

    funds.sort(key=lambda item: item.schemeName.lower())
    _MF_LIVE_CACHE["funds"] = funds
    _MF_LIVE_CACHE["fetched_at"] = now
    return funds


def _filter_mutual_funds(
    funds: list[MutualFund],
    category: str | None,
    search_query: str | None,
) -> list[MutualFund]:
    filtered = funds
    if category:
        category_token = category.strip().lower()
        filtered = [
            fund
            for fund in filtered
            if fund.category.lower() == category_token or category_token in fund.category.lower()
        ]
    if search_query:
        query_token = search_query.strip().lower()
        filtered = [
            fund
            for fund in filtered
            if query_token in fund.schemeName.lower()
            or query_token in fund.schemeCode
            or (fund.fundHouse and query_token in fund.fundHouse.lower())
        ]
    return filtered


def _sort_mutual_funds(
    funds: list[MutualFund],
    sort_by: str,
    sort_order: str,
) -> list[MutualFund]:
    field = (sort_by or "name").strip().lower()
    if field not in _MF_SORT_FIELDS:
        field = "name"

    order = (sort_order or "asc").strip().lower()
    reverse = order == "desc"

    if field == "name":
        key_fn = lambda item: item.schemeName.lower()
    elif field == "nav":
        key_fn = lambda item: float(item.nav or 0.0)
    elif field == "returns1y":
        key_fn = lambda item: float(item.returns1Y if item.returns1Y is not None else -999.0)
    elif field == "returns3y":
        key_fn = lambda item: float(item.returns3Y if item.returns3Y is not None else -999.0)
    elif field == "returns5y":
        key_fn = lambda item: float(item.returns5Y if item.returns5Y is not None else -999.0)
    elif field == "risk":
        key_fn = lambda item: _risk_rank(item.riskLevel, item.category)
    elif field == "category":
        key_fn = lambda item: item.category.lower()
    else:
        key_fn = lambda item: item.schemeName.lower()

    return sorted(funds, key=key_fn, reverse=reverse)


def _find_live_mutual_fund(scheme_code: str) -> MutualFund | None:
    target = scheme_code.strip()
    if not target:
        return None
    for fund in _fetch_live_mutual_funds():
        if fund.schemeCode == target:
            return fund
    return None


def _funds_from_db(db: Session) -> list[MutualFund]:
    rows = db.query(MutualFundModel).order_by(MutualFundModel.scheme_name.asc()).all()
    return [
        MutualFund(
            schemeCode=row.scheme_code,
            schemeName=row.scheme_name,
            category=row.category,
            nav=row.nav,
            navDate=row.nav_date,
            returns1Y=row.returns_1y,
            returns3Y=row.returns_3y,
            returns5Y=row.returns_5y,
            fundHouse=row.fund_house,
            riskLevel=row.risk_level,
        )
        for row in rows
    ]


def _score_recommendation(
    fund: MutualFund,
    risk_profile: str,
    goal: str | None,
    horizon_years: int,
) -> tuple[float, str]:
    desired_risk_rank = {
        "LOW": 1,
        "MODERATE": 2,
        "HIGH": 3,
    }.get(risk_profile, 2)

    fund_risk_rank = _risk_rank(fund.riskLevel, fund.category)
    score = 78.0 - (abs(fund_risk_rank - desired_risk_rank) * 16.0)
    reasons: list[str] = []

    if desired_risk_rank == fund_risk_rank:
        reasons.append("Risk profile match")

    category = (fund.category or "OTHER").upper()
    scheme_name_lower = fund.schemeName.lower()
    goal_lower = (goal or "").strip().lower()

    if horizon_years <= 3:
        if category in {"DEBT", "HYBRID", "SOLUTION"}:
            score += 10.0
            reasons.append("Suited for shorter horizon")
        elif category in {"EQUITY", "INDEX"}:
            score -= 8.0
    elif horizon_years >= 7:
        if category in {"EQUITY", "INDEX"}:
            score += 10.0
            reasons.append("Aligned with long-term growth horizon")

    if goal_lower:
        if "tax" in goal_lower and "elss" in scheme_name_lower:
            score += 20.0
            reasons.append("Tax-saving ELSS fit")
        if any(term in goal_lower for term in ["income", "stability", "capital protection"]):
            if category in {"DEBT", "HYBRID"}:
                score += 12.0
                reasons.append("Better stability profile")
        if any(term in goal_lower for term in ["growth", "wealth", "long term", "long-term"]):
            if category in {"EQUITY", "INDEX"}:
                score += 10.0
                reasons.append("Growth-oriented category")
        if "index" in goal_lower and category == "INDEX":
            score += 12.0
            reasons.append("Index preference match")

    if "index" in scheme_name_lower and category == "INDEX":
        score += 3.0

    score = max(0.0, min(100.0, round(score, 2)))
    rationale = "; ".join(dict.fromkeys(reasons)) if reasons else "Balanced fit based on current profile inputs"
    return score, rationale


def _build_compare_response(funds: list[MutualFund]) -> MutualFundCompareResponse:
    def _best_scheme_for(metric_name: str) -> str | None:
        candidates = [
            fund for fund in funds
            if getattr(fund, metric_name) is not None
        ]
        if not candidates:
            return None
        best_fund = max(candidates, key=lambda fund: float(getattr(fund, metric_name) or -999.0))
        return best_fund.schemeCode

    lowest_risk = min(funds, key=lambda fund: _risk_rank(fund.riskLevel, fund.category)).schemeCode if funds else None
    summary = (
        f"Compared {len(funds)} funds across risk, NAV and available return metrics. "
        "Use this with your horizon and goal for final selection."
    )

    return MutualFundCompareResponse(
        funds=funds,
        bestReturns1YSchemeCode=_best_scheme_for("returns1Y"),
        bestReturns3YSchemeCode=_best_scheme_for("returns3Y"),
        bestReturns5YSchemeCode=_best_scheme_for("returns5Y"),
        lowestRiskSchemeCode=lowest_risk,
        summary=summary,
    )


def _upsert_mutual_fund_model(db: Session, payload: MutualFund) -> MutualFundModel:
    row = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == payload.schemeCode).first()
    if row is None:
        row = MutualFundModel(scheme_code=payload.schemeCode)
        db.add(row)

    row.scheme_name = payload.schemeName
    row.category = payload.category
    row.nav = payload.nav
    row.nav_date = payload.navDate
    row.returns_1y = payload.returns1Y
    row.returns_3y = payload.returns3Y
    row.returns_5y = payload.returns5Y
    row.fund_house = payload.fundHouse
    row.risk_level = payload.riskLevel

    db.commit()
    db.refresh(row)
    return row

def _seed_phase1_master_data(db: Session):
    if db.query(MutualFundModel).count() == 0:
        today = datetime.utcnow().strftime("%Y-%m-%d")
        db.add_all([
            MutualFundModel(
                scheme_code="120503",
                scheme_name="SBI Nifty Index Fund - Direct Plan - Growth",
                category="INDEX",
                nav=102.34,
                nav_date=today,
                returns_1y=14.2,
                returns_3y=12.1,
                returns_5y=11.3,
                fund_house="SBI Mutual Fund",
                risk_level="MODERATE",
            ),
            MutualFundModel(
                scheme_code="120871",
                scheme_name="Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                category="EQUITY",
                nav=78.92,
                nav_date=today,
                returns_1y=18.7,
                returns_3y=16.5,
                returns_5y=15.1,
                fund_house="PPFAS Mutual Fund",
                risk_level="MODERATE_HIGH",
            ),
        ])

    if db.query(IPOModel).count() == 0:
        db.add_all([
            IPOModel(
                ipo_id="IPO-2026-001",
                company_name="Acme Infra Limited",
                symbol="ACME",
                status="OPEN",
                issue_open_date="2026-03-01",
                issue_close_date="2026-03-05",
                listing_date="2026-03-11",
                price_band_min=345.0,
                price_band_max=362.0,
                lot_size=41,
            ),
            IPOModel(
                ipo_id="IPO-2026-002",
                company_name="Nova Renewables Limited",
                symbol="NOVA",
                status="UPCOMING",
                issue_open_date="2026-03-12",
                issue_close_date="2026-03-16",
                listing_date="2026-03-22",
                price_band_min=215.0,
                price_band_max=228.0,
                lot_size=65,
            ),
        ])

    if db.query(ETFModel).count() == 0:
        db.add_all([
            ETFModel(
                symbol="NIFTYBEES",
                name="Nippon India ETF Nifty BeES",
                category="INDEX",
                last=245.75,
                pct_change=0.62,
                aum_cr=16250.0,
                expense_ratio=0.05,
            ),
            ETFModel(
                symbol="GOLDBEES",
                name="Nippon India ETF Gold BeES",
                category="GOLD",
                last=62.13,
                pct_change=0.21,
                aum_cr=10850.0,
                expense_ratio=0.79,
            ),
        ])

    db.commit()


# ==================== QUOTES (LIVE DATA) ====================

@router.get("/quotes", response_model=list[Quote])
async def get_quotes_endpoint(
    symbols: str = Query(""),
    db: Session = Depends(get_db)
):
    """Get live quotes for specified symbols (comma-separated) or defaults."""
    if symbols:
        sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    else:
        sym_list = get_default_symbols()

    raw_quotes = fetch_quotes(sym_list)
    return [Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    ) for q in raw_quotes]


@router.get("/quotes/all", response_model=list[Quote])
async def get_all_quotes_endpoint():
    """Get live quotes for ALL supported NSE symbols."""
    raw_quotes = fetch_quotes(get_all_symbols())
    return [Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    ) for q in raw_quotes]


@router.get("/quotes/{symbol}", response_model=Quote)
async def get_single_quote_endpoint(symbol: str):
    """Get a live quote for a single stock symbol."""
    q = fetch_quote(symbol.upper())
    if q["last"] == 0:
        raise HTTPException(status_code=404, detail=f"Quote not found for {symbol}")
    return Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    )


# ==================== HOLDINGS ====================

@router.get("/holdings", response_model=list[Holding])
async def get_holdings_endpoint(db: Session = Depends(get_db)):
    """Get all holdings with live prices."""
    return get_holdings(db)


@router.get("/holdings/{symbol}", response_model=Holding)
async def get_holding_endpoint(symbol: str, db: Session = Depends(get_db)):
    """Get a single holding by symbol."""
    holding = get_holding(db, symbol.upper())
    if not holding:
        raise HTTPException(status_code=404, detail=f"No holding found for {symbol}")
    return holding


# ==================== TRADING ====================

@router.post("/order", response_model=OrderResponse)
async def place_order_endpoint(
    order: Order,
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Place a buy or sell order at live market price."""
    return place_order(db, order, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/buy", response_model=OrderResponse)
async def buy_stock_endpoint(
    order: Order,
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Buy stock at live market price."""
    order.side = "BUY"
    return place_order(db, order, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/sell", response_model=OrderResponse)
async def sell_stock_endpoint(
    order: Order,
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Sell stock at live market price."""
    order.side = "SELL"
    return place_order(db, order, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.get("/trades/history", response_model=list[TradeHistory])
async def get_trade_history_endpoint(db: Session = Depends(get_db)):
    """Get all trade history."""
    orders = db.query(OrderModel).order_by(OrderModel.created_at.desc()).all()
    return [TradeHistory(
        id=o.id,
        symbol=o.symbol,
        side=o.side,
        quantity=o.quantity,
        price=o.price or 0,
        total=o.total or 0,
        timestamp=int(o.created_at.timestamp() * 1000) if o.created_at else 0
    ) for o in orders]


@router.get("/trades/history/{symbol}", response_model=list[TradeHistory])
async def get_trade_history_for_symbol(symbol: str, db: Session = Depends(get_db)):
    """Get trade history for a specific symbol."""
    orders = db.query(OrderModel).filter(
        OrderModel.symbol == symbol.upper()
    ).order_by(OrderModel.created_at.desc()).all()
    return [TradeHistory(
        id=o.id,
        symbol=o.symbol,
        side=o.side,
        quantity=o.quantity,
        price=o.price or 0,
        total=o.total or 0,
        timestamp=int(o.created_at.timestamp() * 1000) if o.created_at else 0
    ) for o in orders]


# ==================== PORTFOLIO ====================

@router.get("/portfolio", response_model=PortfolioSummary)
async def get_portfolio_endpoint(db: Session = Depends(get_db)):
    """Get portfolio summary with live values."""
    holdings = get_holdings(db)

    total_value = sum(h.last * h.qty for h in holdings)
    total_invested = sum(h.avgPrice * h.qty for h in holdings)
    total_pnl = total_value - total_invested
    total_pnl_pct = round((total_pnl / total_invested) * 100, 2) if total_invested > 0 else 0.0

    return PortfolioSummary(
        totalValue=round(total_value, 2),
        totalInvested=round(total_invested, 2),
        totalPnL=round(total_pnl, 2),
        totalPnLPercent=total_pnl_pct,
        holdingsCount=len(holdings)
    )


@router.get("/portfolio/value", response_model=PortfolioValue)
async def get_portfolio_value_endpoint(db: Session = Depends(get_db)):
    """Get portfolio current value with live prices."""
    holdings = get_holdings(db)

    total_value = sum(h.last * h.qty for h in holdings)
    total_invested = sum(h.avgPrice * h.qty for h in holdings)
    total_pnl = total_value - total_invested
    total_pnl_pct = round((total_pnl / total_invested) * 100, 2) if total_invested > 0 else 0.0

    return PortfolioValue(
        value=round(total_value, 2),
        invested=round(total_invested, 2),
        pnl=round(total_pnl, 2),
        pnlPercent=total_pnl_pct
    )


# ==================== WALLET ====================


@router.get("/wallet", response_model=Wallet)
async def get_wallet_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Get current wallet balance for the authenticated user."""
    return get_wallet(db, user.id)



@router.post("/wallet/add", response_model=WalletResponse)
async def add_funds_endpoint(txn: WalletTransaction, db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Add funds to the authenticated user's wallet."""
    return add_funds(db, user.id, txn.amount)



@router.post("/wallet/withdraw", response_model=WalletResponse)
async def withdraw_funds_endpoint(txn: WalletTransaction, db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Withdraw funds from the authenticated user's wallet."""
    return withdraw_funds(db, user.id, txn.amount)


# ==================== MARKET STATUS ====================

@router.get("/market/status", response_model=MarketStatus)
async def market_status_endpoint():
    """Check if NSE market is currently open (9:15 AM - 3:30 PM IST, Mon-Fri)."""
    return is_market_open()


# ==================== ALERTS ====================

@router.get("/alerts", response_model=list[Alert])
async def get_alerts_endpoint(db: Session = Depends(get_db)):
    """Get all alerts."""
    alerts = db.query(AlertModel).all()
    return [Alert(
        id=a.id,
        symbol=a.symbol,
        thresholdPrice=a.threshold_price,
        alertType=a.alert_type,
        isActive=a.is_active,
        createdAt=a.created_at
    ) for a in alerts]


@router.get("/alerts/active", response_model=list[Alert])
async def get_active_alerts_endpoint(db: Session = Depends(get_db)):
    """Get active alerts only."""
    alerts = db.query(AlertModel).filter(AlertModel.is_active == True).all()
    return [Alert(
        id=a.id,
        symbol=a.symbol,
        thresholdPrice=a.threshold_price,
        alertType=a.alert_type,
        isActive=a.is_active,
        createdAt=a.created_at
    ) for a in alerts]


@router.post("/alerts", response_model=Alert)
async def create_alert_endpoint(alert: AlertCreate, db: Session = Depends(get_db)):
    """Create a new price alert."""
    alert_db = AlertModel(
        symbol=alert.symbol.upper(),
        threshold_price=alert.thresholdPrice,
        alert_type=alert.alertType,
        is_active=True
    )
    db.add(alert_db)
    db.commit()
    db.refresh(alert_db)
    return Alert(
        id=alert_db.id,
        symbol=alert_db.symbol,
        thresholdPrice=alert_db.threshold_price,
        alertType=alert_db.alert_type,
        isActive=alert_db.is_active,
        createdAt=alert_db.created_at
    )


@router.put("/alerts/{alert_id}", response_model=Alert)
async def update_alert_endpoint(alert_id: int, alert: AlertCreate, db: Session = Depends(get_db)):
    """Update an existing alert."""
    alert_db = db.query(AlertModel).filter(AlertModel.id == alert_id).first()
    if not alert_db:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    alert_db.symbol = alert.symbol.upper()
    alert_db.threshold_price = alert.thresholdPrice
    alert_db.alert_type = alert.alertType
    db.commit()
    db.refresh(alert_db)
    return Alert(
        id=alert_db.id,
        symbol=alert_db.symbol,
        thresholdPrice=alert_db.threshold_price,
        alertType=alert_db.alert_type,
        isActive=alert_db.is_active,
        createdAt=alert_db.created_at
    )


@router.delete("/alert/{alert_id}", response_model=AlertResponse)
async def delete_alert_endpoint(alert_id: int, db: Session = Depends(get_db)):
    """Delete/deactivate an alert."""
    alert_db = db.query(AlertModel).filter(AlertModel.id == alert_id).first()
    if not alert_db:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    alert_db.is_active = False
    db.commit()
    return AlertResponse(status="ok", message=f"Alert {alert_id} deactivated", id=alert_id)


# ==================== SEARCH ====================

@router.get("/search")
async def search_stocks_endpoint(
    q: str = Query("", description="Search query (symbol or company name)"),
    limit: int = Query(50, description="Max results"),
):
    """Search for Indian stocks by symbol or company name.
    Covers NIFTY 500+ stocks. Unknown symbols are tried on Yahoo Finance."""
    results = search_stocks(q, limit=limit)
    return results


@router.get("/symbols")
async def get_symbols_endpoint():
    """Get all available stock symbols with company names.
    Returns 500+ Indian stocks (NSE & BSE)."""
    return get_symbols_with_names()


@router.get("/symbols/count")
async def get_symbols_count():
    """Get count of available symbols."""
    return {"count": len(INDIAN_STOCKS), "exchange": "NSE/BSE"}


# ==================== HEALTH ====================

@router.get("/health", response_model=HealthCheck)
async def health_check():
    """Health check endpoint."""
    return HealthCheck(status="healthy", version="2.0.0")


# ==================== AI STOCK ASSISTANT ====================

class AiQuery(BaseModel):
    query: str

@router.post("/ai/ask")
async def ai_ask_endpoint(body: AiQuery):
    """Natural language AI stock assistant.
    Examples: 'Should I buy RELIANCE?', 'Predict TCS price', 'Compare INFY and TCS'"""
    result = ai_assistant(body.query)
    return result


@router.get("/ai/analyze/{symbol}")
async def ai_analyze_endpoint(symbol: str):
    """Get comprehensive AI analysis for a stock including technical,
    fundamental analysis, score, prediction, and plain-English summary."""
    result = analyze_stock(symbol.upper())
    if "error" in result and "predictions" not in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@router.get("/ai/predict/{symbol}")
async def ai_predict_endpoint(symbol: str):
    """Get AI price predictions for 1-week, 1-month, and 3-month horizons
    with confidence intervals and direction signals."""
    result = predict_price(symbol.upper())
    if "error" in result and not result.get("predictions"):
        raise HTTPException(status_code=404, detail=result["error"])
    return result


# ==================== PORTFOLIO HEALTH SCORE ====================

@router.get("/portfolio/health")
async def portfolio_health_endpoint(db: Session = Depends(get_db)):
    """Get portfolio health score (0-100) with breakdown and suggestions.
    Analyzes diversification, risk, quality, and balance."""
    holdings_db = db.query(HoldingModel).all()
    holdings_list = []
    for h in holdings_db:
        holdings_list.append({
            "symbol": h.symbol,
            "quantity": h.quantity,
            "avgPrice": h.avg_price,
        })
    result = calculate_portfolio_health(holdings_list)
    return result


# ==================== MARKET HEATMAP ====================

@router.get("/market/heatmap")
async def market_heatmap_endpoint():
    """Get real-time market heatmap with sector-wise performance,
    market breadth, mood indicator, and individual stock data."""
    result = get_market_heatmap()
    return result


@router.get("/market/sector/{sector_name}")
async def sector_detail_endpoint(sector_name: str):
    """Get detailed data for a specific sector."""
    result = get_sector_detail(sector_name)
    if not result:
        raise HTTPException(status_code=404, detail=f"Sector '{sector_name}' not found")
    return result


# ==================== PHASE 1: MUTUAL FUNDS & SIP ====================

@router.get("/mutual-funds", response_model=list[MutualFund])
async def get_mutual_funds_endpoint(
    category: str | None = Query(None),
    q: str | None = Query(None),
    sortBy: str = Query("name"),
    sortOrder: str = Query("asc"),
    limit: int = Query(500, ge=1, le=2000),
    db: Session = Depends(get_db)
):
    try:
        live_funds = _fetch_live_mutual_funds()
        filtered_funds = _filter_mutual_funds(live_funds, category=category, search_query=q)
        sorted_funds = _sort_mutual_funds(filtered_funds, sort_by=sortBy, sort_order=sortOrder)
        return sorted_funds[:limit]
    except Exception as exc:
        logger.warning("mutual_funds.live_fetch_failed reason=%s", str(exc))

    _seed_phase1_master_data(db)
    db_funds = _funds_from_db(db)
    filtered_funds = _filter_mutual_funds(db_funds, category=category, search_query=q)
    sorted_funds = _sort_mutual_funds(filtered_funds, sort_by=sortBy, sort_order=sortOrder)
    return sorted_funds[:limit]


@router.get("/mutual-funds/compare", response_model=MutualFundCompareResponse)
async def compare_mutual_funds_endpoint(
    schemeCodes: str = Query(..., description="Comma-separated mutual fund scheme codes"),
    db: Session = Depends(get_db),
):
    codes = [item.strip() for item in schemeCodes.split(",") if item.strip()]
    deduped_codes = list(dict.fromkeys(codes))
    if len(deduped_codes) < 2:
        raise HTTPException(status_code=400, detail="Provide at least 2 scheme codes for comparison")
    if len(deduped_codes) > 4:
        raise HTTPException(status_code=400, detail="Compare up to 4 funds at a time")

    try:
        live_map = {fund.schemeCode: fund for fund in _fetch_live_mutual_funds()}
    except Exception:
        live_map = {}

    compared_funds: list[MutualFund] = []
    for code in deduped_codes:
        fund = live_map.get(code)
        if fund is None:
            row = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == code).first()
            if row is None:
                raise HTTPException(status_code=404, detail=f"Mutual fund '{code}' not found")
            fund = MutualFund(
                schemeCode=row.scheme_code,
                schemeName=row.scheme_name,
                category=row.category,
                nav=row.nav,
                navDate=row.nav_date,
                returns1Y=row.returns_1y,
                returns3Y=row.returns_3y,
                returns5Y=row.returns_5y,
                fundHouse=row.fund_house,
                riskLevel=row.risk_level,
            )
        compared_funds.append(fund)

    return _build_compare_response(compared_funds)


@router.get("/mutual-funds/recommend", response_model=MutualFundRecommendationResponse)
async def recommend_mutual_funds_endpoint(
    riskProfile: str = Query("MODERATE", description="LOW, MODERATE, or HIGH"),
    goal: str | None = Query(None, description="Optional goal like growth, income, tax, index"),
    horizonYears: int = Query(5, ge=1, le=30),
    limit: int = Query(5, ge=1, le=10),
    db: Session = Depends(get_db),
):
    normalized_risk = riskProfile.strip().upper()
    if normalized_risk not in {"LOW", "MODERATE", "HIGH"}:
        raise HTTPException(status_code=400, detail="riskProfile must be LOW, MODERATE, or HIGH")

    try:
        all_funds = _fetch_live_mutual_funds()
    except Exception as exc:
        logger.warning("mutual_funds.recommend.live_fetch_failed reason=%s", str(exc))
        _seed_phase1_master_data(db)
        all_funds = _funds_from_db(db)

    ranked: list[MutualFundRecommendationItem] = []
    for fund in all_funds:
        score, rationale = _score_recommendation(
            fund=fund,
            risk_profile=normalized_risk,
            goal=goal,
            horizon_years=horizonYears,
        )
        ranked.append(
            MutualFundRecommendationItem(
                schemeCode=fund.schemeCode,
                schemeName=fund.schemeName,
                category=fund.category,
                nav=fund.nav,
                navDate=fund.navDate,
                fundHouse=fund.fundHouse,
                riskLevel=fund.riskLevel,
                suitabilityScore=score,
                rationale=rationale,
            )
        )

    ranked.sort(key=lambda item: item.suitabilityScore, reverse=True)
    recommendations = ranked[:limit]

    return MutualFundRecommendationResponse(
        riskProfile=normalized_risk,
        goal=goal,
        horizonYears=horizonYears,
        recommendations=recommendations,
        generatedAt=datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    )


@router.get("/mutual-funds/{scheme_code}", response_model=MutualFund)
async def get_mutual_fund_detail_endpoint(scheme_code: str, db: Session = Depends(get_db)):
    try:
        live_fund = _find_live_mutual_fund(scheme_code)
        if live_fund is not None:
            return live_fund
    except Exception as exc:
        logger.warning("mutual_fund_detail.live_fetch_failed scheme_code=%s reason=%s", scheme_code, str(exc))

    _seed_phase1_master_data(db)
    fund = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == scheme_code).first()
    if fund:
        return MutualFund(
            schemeCode=fund.scheme_code,
            schemeName=fund.scheme_name,
            category=fund.category,
            nav=fund.nav,
            navDate=fund.nav_date,
            returns1Y=fund.returns_1y,
            returns3Y=fund.returns_3y,
            returns5Y=fund.returns_5y,
            fundHouse=fund.fund_house,
            riskLevel=fund.risk_level,
        )
    raise HTTPException(status_code=404, detail=f"Mutual fund '{scheme_code}' not found")


@router.post("/sip/plans", response_model=SipPlan)
async def create_sip_plan_endpoint(
    request: SipPlanRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    fund = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == request.schemeCode).first()
    if not fund:
        try:
            live_fund = _find_live_mutual_fund(request.schemeCode)
            if live_fund is not None:
                fund = _upsert_mutual_fund_model(db, live_fund)
        except Exception as exc:
            logger.warning("sip_plan.live_fund_lookup_failed scheme_code=%s reason=%s", request.schemeCode, str(exc))

    if not fund:
        _seed_phase1_master_data(db)
        fund = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == request.schemeCode).first()

    if not fund:
        raise HTTPException(status_code=404, detail=f"Mutual fund '{request.schemeCode}' not found")

    next_date = (datetime.utcnow() + timedelta(days=30)).strftime("%Y-%m-%d")
    plan = SipPlanModel(
        user_id=int(user.id),
        scheme_code=request.schemeCode,
        scheme_name=fund.scheme_name,
        amount=request.amount,
        frequency=request.frequency,
        day_of_month=request.dayOfMonth,
        next_installment_date=next_date,
        is_active=True,
    )
    db.add(plan)
    db.commit()
    db.refresh(plan)

    return SipPlan(
        id=f"SIP-{plan.id}",
        schemeCode=plan.scheme_code,
        schemeName=plan.scheme_name,
        amount=plan.amount,
        frequency=plan.frequency,
        nextInstallmentDate=plan.next_installment_date,
        isActive=plan.is_active,
    )


@router.get("/sip/plans", response_model=list[SipPlan])
async def get_sip_plans_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    plans = (
        db.query(SipPlanModel)
        .filter(SipPlanModel.user_id == int(user.id))
        .order_by(SipPlanModel.created_at.desc())
        .all()
    )
    return [
        SipPlan(
            id=f"SIP-{item.id}",
            schemeCode=item.scheme_code,
            schemeName=item.scheme_name,
            amount=item.amount,
            frequency=item.frequency,
            nextInstallmentDate=item.next_installment_date,
            isActive=item.is_active,
        )
        for item in plans
    ]


@router.put("/sip/plans/{sip_id}", response_model=SipPlan)
async def update_sip_plan_endpoint(
    sip_id: str,
    request: SipPlanUpdateRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    numeric_id = int(sip_id.replace("SIP-", "")) if sip_id.startswith("SIP-") else int(sip_id)
    plan = db.query(SipPlanModel).filter(SipPlanModel.id == numeric_id, SipPlanModel.user_id == int(user.id)).first()
    if not plan:
        raise HTTPException(status_code=404, detail=f"SIP plan '{sip_id}' not found")

    if request.amount is not None:
        if request.amount <= 0:
            raise HTTPException(status_code=400, detail="SIP amount must be positive")
        plan.amount = request.amount
    if request.frequency is not None:
        plan.frequency = request.frequency.upper()
    if request.dayOfMonth is not None:
        if request.dayOfMonth < 1 or request.dayOfMonth > 28:
            raise HTTPException(status_code=400, detail="Installment day must be between 1 and 28")
        plan.day_of_month = request.dayOfMonth
    if request.isActive is not None:
        plan.is_active = request.isActive

    db.commit()
    db.refresh(plan)

    return SipPlan(
        id=f"SIP-{plan.id}",
        schemeCode=plan.scheme_code,
        schemeName=plan.scheme_name,
        amount=plan.amount,
        frequency=plan.frequency,
        nextInstallmentDate=plan.next_installment_date,
        isActive=plan.is_active,
    )


@router.post("/sip/plans/{sip_id}/pause", response_model=SipPlan)
async def pause_sip_plan_endpoint(sip_id: str, db: Session = Depends(get_db), user=Depends(get_current_user)):
    return await update_sip_plan_endpoint(
        sip_id=sip_id,
        request=SipPlanUpdateRequest(isActive=False),
        db=db,
        user=user,
    )


@router.post("/sip/plans/{sip_id}/resume", response_model=SipPlan)
async def resume_sip_plan_endpoint(sip_id: str, db: Session = Depends(get_db), user=Depends(get_current_user)):
    return await update_sip_plan_endpoint(
        sip_id=sip_id,
        request=SipPlanUpdateRequest(isActive=True),
        db=db,
        user=user,
    )


# ==================== PHASE 1: IPO ====================

@router.get("/ipos", response_model=list[IPOListing])
async def get_ipos_endpoint(status: str | None = Query(None), db: Session = Depends(get_db)):
    _seed_phase1_master_data(db)
    query = db.query(IPOModel)
    if status:
        query = query.filter(IPOModel.status.ilike(status))
    listings = query.order_by(IPOModel.issue_open_date.asc()).all()
    return [
        IPOListing(
            ipoId=item.ipo_id,
            companyName=item.company_name,
            symbol=item.symbol,
            status=item.status,
            issueOpenDate=item.issue_open_date,
            issueCloseDate=item.issue_close_date,
            listingDate=item.listing_date,
            priceBandMin=item.price_band_min,
            priceBandMax=item.price_band_max,
            lotSize=item.lot_size,
        )
        for item in listings
    ]


@router.get("/ipos/my-applications", response_model=list[IPOApplication])
async def get_my_ipo_applications_endpoint(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    _seed_phase1_master_data(db)
    applications = (
        db.query(IPOApplicationModel, IPOModel)
        .join(IPOModel, IPOApplicationModel.ipo_id == IPOModel.ipo_id)
        .filter(IPOApplicationModel.user_id == int(user.id))
        .order_by(IPOApplicationModel.created_at.desc())
        .all()
    )
    return [
        IPOApplication(
            applicationId=f"APP-{application.id}",
            ipoId=application.ipo_id,
            companyName=ipo.company_name,
            lots=application.lots,
            bidPrice=application.bid_price,
            upiId=application.upi_id,
            status=application.status,
            appliedAt=application.created_at.strftime("%Y-%m-%d %H:%M:%S") if application.created_at else "",
        )
        for application, ipo in applications
    ]


@router.get("/ipos/{ipo_id}", response_model=IPOListing)
async def get_ipo_detail_endpoint(ipo_id: str, db: Session = Depends(get_db)):
    _seed_phase1_master_data(db)
    item = db.query(IPOModel).filter(IPOModel.ipo_id == ipo_id).first()
    if item:
        return IPOListing(
            ipoId=item.ipo_id,
            companyName=item.company_name,
            symbol=item.symbol,
            status=item.status,
            issueOpenDate=item.issue_open_date,
            issueCloseDate=item.issue_close_date,
            listingDate=item.listing_date,
            priceBandMin=item.price_band_min,
            priceBandMax=item.price_band_max,
            lotSize=item.lot_size,
        )
    raise HTTPException(status_code=404, detail=f"IPO '{ipo_id}' not found")


@router.post("/ipos/apply", response_model=IPOApplicationResponse)
async def apply_ipo_endpoint(
    request: IPOApplicationRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    _seed_phase1_master_data(db)
    ipo = db.query(IPOModel).filter(IPOModel.ipo_id == request.ipoId).first()
    if not ipo:
        raise HTTPException(status_code=404, detail=f"IPO '{request.ipoId}' not found")

    if ipo.status.upper() != "OPEN":
        raise HTTPException(status_code=400, detail="IPO is not open for applications")

    if request.lots <= 0:
        raise HTTPException(status_code=400, detail="Lots must be greater than zero")

    if ipo.price_band_min is not None and request.bidPrice < ipo.price_band_min:
        raise HTTPException(status_code=400, detail=f"Bid price must be >= {ipo.price_band_min}")

    if ipo.price_band_max is not None and request.bidPrice > ipo.price_band_max:
        raise HTTPException(status_code=400, detail=f"Bid price must be <= {ipo.price_band_max}")

    application = IPOApplicationModel(
        user_id=int(user.id),
        ipo_id=request.ipoId,
        lots=request.lots,
        bid_price=request.bidPrice,
        upi_id=request.upiId,
        status="PENDING",
    )
    db.add(application)
    db.commit()
    db.refresh(application)

    return IPOApplicationResponse(
        applicationId=f"APP-{application.id}",
        status="PENDING",
        message="IPO application accepted for processing"
    )


# ==================== PHASE 1: ETF ====================

@router.get("/etfs", response_model=list[ETFInstrument])
async def get_etfs_endpoint(
    category: str | None = Query(None),
    q: str | None = Query(None),
    db: Session = Depends(get_db)
):
    _seed_phase1_master_data(db)
    query = db.query(ETFModel)
    if category:
        query = query.filter(ETFModel.category.ilike(category))
    if q:
        needle = f"%{q}%"
        query = query.filter((ETFModel.symbol.ilike(needle)) | (ETFModel.name.ilike(needle)))
    etfs = query.order_by(ETFModel.symbol.asc()).all()
    return [
        ETFInstrument(
            symbol=item.symbol,
            name=item.name,
            category=item.category,
            last=item.last,
            pctChange=item.pct_change,
            aumCr=item.aum_cr,
            expenseRatio=item.expense_ratio,
        )
        for item in etfs
    ]
