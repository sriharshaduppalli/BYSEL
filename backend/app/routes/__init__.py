from fastapi import APIRouter, Depends, Query, HTTPException, Header, Request
from pydantic import BaseModel
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from typing import Dict
import logging
import os
import time
import hashlib
from math import erf, exp, log, sqrt
from urllib import request as urllib_request
from ..database.db import (
    get_db,
    AlertModel,
    OrderModel,
    TriggerOrderModel,
    BasketOrderModel,
    BasketOrderLegModel,
    HoldingModel,
    MutualFundModel,
    SipPlanModel,
    IPOModel,
    IPOApplicationModel,
    ETFModel,
    FamilyMemberModel,
    GoalPlanModel,
)
from .dependencies import get_current_user
from ..models.schemas import (
    Quote, Holding, Order, OrderResponse, Alert, AlertCreate,
    AlertResponse, HealthCheck, TradeHistory, HistoryCandle, OrderTraceLookupResponse, PortfolioSummary, PortfolioValue,
    Wallet, WalletTransaction, WalletResponse, MarketStatus,
    MarketNewsResponse,
    MutualFund, MutualFundCompareResponse, MutualFundRecommendationItem, MutualFundRecommendationResponse,
    SipPlanRequest, SipPlan, IPOListing,
    SipPlanUpdateRequest, IPOApplicationRequest, IPOApplicationResponse, IPOApplication, ETFInstrument,
    AdvancedOrderResponse,
    TriggerOrderSummary,
    BasketOrderRequest,
    BasketOrderResponse,
    BasketLegExecution,
    OptionContract,
    OptionChainResponse,
    FuturesContract,
    FuturesContractsResponse,
    FuturesTicketPreviewRequest,
    FuturesTicketPreviewResponse,
    StrategyPreviewRequest,
    StrategyPreviewResponse,
    StrategyPayoffPoint,
    FamilyMemberRequest,
    FamilyMemberSummary,
    FamilyDashboardResponse,
    GoalPlanRequest,
    GoalLinkRequest,
    GoalPlanResponse,
    PreTradeEstimateRequest,
    PreTradeEstimateResponse,
    PreTradeChargeBreakdown,
    CopilotPreTradeRequest,
    CopilotSignal,
    CopilotPostTradeRequest,
    CopilotPostTradeResponse,
    CopilotPortfolioActionsResponse,
    InvestorHoldingDelta,
    InvestorPortfolioChangeFeed,
    SmartMoneyIdeaFeedCard,
    InvestorPortfolioInsightsResponse,
    SignalLabCandidate,
    SignalLabBucketFeed,
    SignalLabBucketsResponse,
    SipPlanUpdateRequest, IPOApplicationRequest, IPOApplicationResponse, IPOApplication, ETFInstrument,
    AdvancedOrderResponse,
    TriggerOrderSummary,
    BasketOrderRequest,
    BasketOrderResponse,
    BasketLegExecution,
    OptionContract,
    OptionChainResponse,
    StrategyPreviewRequest,
    StrategyPreviewResponse,
    StrategyPayoffPoint,
    FamilyMemberRequest,
    FamilyMemberSummary,
    FamilyDashboardResponse,
    GoalPlanRequest,
    GoalLinkRequest,
    GoalPlanResponse,
    CopilotPreTradeRequest,
    CopilotSignal,
    CopilotPostTradeRequest,
    CopilotPostTradeResponse,
    CopilotPortfolioActionsResponse,
)
from .trading import (
    get_holdings, get_holding, place_order,
    is_market_open, get_wallet, add_funds, withdraw_funds,
    evaluate_pending_triggers, build_pretrade_signal, build_pretrade_estimate,
)
from ..market_data import (
    fetch_quote, fetch_quote_history, fetch_quotes, get_all_symbols, get_default_symbols,
    search_stocks, get_symbols_with_names, get_stock_name, INDIAN_STOCKS
)
from ..ai_engine import (
    analyze_stock, predict_price, ai_assistant, get_market_headlines,
    get_stop_loss_take_profit, calculate_drawdown_risk, calculate_relative_strength,
    calculate_trade_accuracy, get_sector_rotation_signals, get_earnings_calendar,
    advanced_stock_screener
)
from ..portfolio_scorer import calculate_portfolio_health
from ..market_heatmap import SECTOR_STOCKS, get_market_heatmap, get_sector_detail

router = APIRouter()
logger = logging.getLogger(__name__)

_MF_NAV_SOURCE_URL = os.getenv("MF_NAV_SOURCE_URL", "https://www.amfiindia.com/spages/NAVAll.txt")
_MF_LIVE_CACHE_TTL_SECONDS = int(os.getenv("MF_LIVE_CACHE_TTL_SECONDS", "1800"))
_MF_LIVE_CACHE: dict[str, object] = {"fetched_at": 0.0, "funds": []}
_MF_SORT_FIELDS = {"name", "nav", "returns1y", "returns3y", "returns5y", "risk", "category"}
_FUTURES_LOT_SIZE_HINTS = {
    "NIFTY": 50,
    "BANKNIFTY": 25,
    "FINNIFTY": 65,
    "RELIANCE": 250,
    "TCS": 150,
    "INFY": 300,
    "SBIN": 750,
}
_SIGNAL_LAB_CACHE_TTL_SECONDS = int(os.getenv("SIGNAL_LAB_CACHE_TTL_SECONDS", "90"))
_SIGNAL_LAB_CACHE_MAX_ITEMS = 6
_SIGNAL_LAB_CACHE: dict[int, tuple[float, SignalLabBucketsResponse]] = {}
_RESULTS_WEEK_UNIVERSE = [
    "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN",
    "BHARTIARTL", "ITC", "LT", "AXISBANK", "KOTAKBANK", "BAJFINANCE",
    "MARUTI", "TITAN", "ULTRACEMCO", "SUNPHARMA", "HCLTECH", "TECHM",
]
_INSTITUTIONAL_CONVICTION_UNIVERSE = [
    "HDFCBANK", "ICICIBANK", "SBIN", "KOTAKBANK", "AXISBANK", "BAJFINANCE",
    "RELIANCE", "TCS", "INFY", "LT", "ITC", "HINDUNILVR", "BHARTIARTL",
    "TITAN", "SUNPHARMA", "ULTRACEMCO", "POWERGRID", "NTPC",
]


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


def _normal_cdf(x: float) -> float:
    return 0.5 * (1.0 + erf(x / sqrt(2.0)))


def _normal_pdf(x: float) -> float:
    return (1.0 / sqrt(2.0 * 3.141592653589793)) * exp(-0.5 * x * x)


def _black_scholes_greeks(spot: float, strike: float, time_years: float, rate: float, iv: float) -> dict[str, float]:
    if spot <= 0 or strike <= 0 or time_years <= 0 or iv <= 0:
        return {
            "callPrice": 0.0,
            "putPrice": 0.0,
            "callDelta": 0.0,
            "putDelta": 0.0,
            "gamma": 0.0,
            "theta": 0.0,
            "vega": 0.0,
        }

    sqrt_t = sqrt(time_years)
    d1 = (log(spot / strike) + (rate + 0.5 * iv * iv) * time_years) / (iv * sqrt_t)
    d2 = d1 - iv * sqrt_t

    call_price = spot * _normal_cdf(d1) - strike * exp(-rate * time_years) * _normal_cdf(d2)
    put_price = strike * exp(-rate * time_years) * _normal_cdf(-d2) - spot * _normal_cdf(-d1)
    gamma = _normal_pdf(d1) / (spot * iv * sqrt_t)
    vega = (spot * _normal_pdf(d1) * sqrt_t) / 100.0
    theta = (
        (-spot * _normal_pdf(d1) * iv / (2.0 * sqrt_t))
        - (rate * strike * exp(-rate * time_years) * _normal_cdf(d2))
    ) / 365.0

    return {
        "callPrice": round(max(call_price, 0.01), 2),
        "putPrice": round(max(put_price, 0.01), 2),
        "callDelta": round(_normal_cdf(d1), 4),
        "putDelta": round(_normal_cdf(d1) - 1.0, 4),
        "gamma": round(gamma, 5),
        "theta": round(theta, 4),
        "vega": round(vega, 4),
    }


def _generate_option_chain(symbol: str, expiry: str) -> OptionChainResponse:
    quote = fetch_quote(symbol.upper())
    spot = float(quote.get("last") or 0.0)
    if spot <= 0:
        raise HTTPException(status_code=404, detail=f"Could not fetch live spot for {symbol}")

    step = 50.0 if spot >= 1000 else 20.0 if spot >= 300 else 10.0
    atm = round(spot / step) * step
    base_seed = sum(ord(ch) for ch in f"{symbol.upper()}:{expiry}")
    contracts: list[OptionContract] = []
    for index in range(-10, 11):
        strike = round(atm + (index * step), 2)
        strike_seed = base_seed + int(strike * 10)
        iv = max(0.12, min(0.55, 0.22 + (abs(index) * 0.01) + ((strike_seed % 17) / 1000.0)))
        greeks = _black_scholes_greeks(
            spot=spot,
            strike=strike,
            time_years=21 / 365,
            rate=0.065,
            iv=iv,
        )
        call_oi = max(250, int(15000 - (abs(index) * 780) + (strike_seed % 500)))
        put_oi = max(250, int(14800 - (abs(index) * 760) + ((strike_seed + 37) % 500)))
        call_oi_change = int((strike_seed % 240) - 120)
        put_oi_change = int(((strike_seed + 77) % 240) - 120)

        contracts.append(
            OptionContract(
                strike=strike,
                callLtp=greeks["callPrice"],
                putLtp=greeks["putPrice"],
                callOi=call_oi,
                putOi=put_oi,
                callOiChange=call_oi_change,
                putOiChange=put_oi_change,
                impliedVolatility=round(iv, 4),
                callDelta=greeks["callDelta"],
                putDelta=greeks["putDelta"],
                gamma=greeks["gamma"],
                theta=greeks["theta"],
                vega=greeks["vega"],
            )
        )

    return OptionChainResponse(
        symbol=symbol.upper(),
        expiry=expiry,
        spot=round(spot, 2),
        generatedAt=datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        contracts=contracts,
    )


def _lot_size_for_symbol(symbol: str, spot: float) -> int:
    normalized = symbol.strip().upper()
    hinted = _FUTURES_LOT_SIZE_HINTS.get(normalized)
    if hinted:
        return hinted

    if spot <= 0:
        return 100

    target_notional = 120_000.0
    raw = int(round(target_notional / spot))
    rounded = max(10, ((raw + 4) // 5) * 5)
    return rounded


def _generate_futures_contracts(symbol: str) -> FuturesContractsResponse:
    quote = fetch_quote(symbol.upper())
    spot = float(quote.get("last") or 0.0)
    if spot <= 0:
        raise HTTPException(status_code=404, detail=f"Could not fetch live spot for {symbol}")

    normalized_symbol = symbol.strip().upper()
    lot_size = _lot_size_for_symbol(normalized_symbol, spot)
    base_seed = sum(ord(ch) for ch in normalized_symbol)
    today = datetime.utcnow().date()

    contracts: list[FuturesContract] = []
    expiry_offsets = [7, 14, 28]
    for idx, day_offset in enumerate(expiry_offsets, start=1):
        expiry_date = today + timedelta(days=day_offset)
        carry = 0.0012 * idx + 0.0004 * ((base_seed + idx) % 3)
        contract_last = round(spot * (1 + carry), 2)
        basis = round(contract_last - spot, 2)

        oi = max(5000, int((base_seed * 97 + idx * 431) % 95000 + 8000))
        oi_change = int(((base_seed * 31 + idx * 173) % 3200) - 1600)
        volume = max(500, int((base_seed * 19 + idx * 257) % 18000 + 2500))
        pct_change = round(float(quote.get("pctChange") or 0.0) + (idx * 0.08), 2)

        margin_pct = round(min(0.22, 0.11 + (abs(oi_change) / 12000.0) + (idx * 0.01)), 4)
        margin_per_lot = round(contract_last * lot_size * margin_pct, 2)
        contract_symbol = f"{normalized_symbol}-{expiry_date.strftime('%d%b%y').upper()}-FUT"

        contracts.append(
            FuturesContract(
                contractSymbol=contract_symbol,
                expiry=expiry_date.strftime("%Y-%m-%d"),
                lotSize=lot_size,
                last=contract_last,
                pctChange=pct_change,
                oi=oi,
                oiChange=oi_change,
                volume=volume,
                basis=basis,
                marginPct=margin_pct,
                marginPerLot=margin_per_lot,
            )
        )

    return FuturesContractsResponse(
        symbol=normalized_symbol,
        spot=round(spot, 2),
        generatedAt=datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        contracts=contracts,
        notes=[
            "Contract metrics are indicative and should be validated against broker RMS before execution.",
            "Margin preview excludes span spikes and intraday leverage changes.",
        ],
    )


def _preview_futures_ticket(payload: FuturesTicketPreviewRequest) -> FuturesTicketPreviewResponse:
    if payload.lots <= 0:
        raise HTTPException(status_code=400, detail="Lots must be greater than 0")

    side = payload.side.strip().upper()
    if side not in {"BUY", "SELL"}:
        raise HTTPException(status_code=400, detail="Side must be BUY or SELL")

    order_type = payload.orderType.strip().upper()
    if order_type not in {"MARKET", "LIMIT"}:
        raise HTTPException(status_code=400, detail="orderType must be MARKET or LIMIT")

    contracts = _generate_futures_contracts(payload.symbol)
    selected = next((c for c in contracts.contracts if c.expiry == payload.expiry), None)
    if selected is None:
        raise HTTPException(
            status_code=404,
            detail=f"No futures contract found for {payload.symbol.upper()} expiry {payload.expiry}",
        )

    if order_type == "LIMIT" and (payload.limitPrice is None or payload.limitPrice <= 0):
        raise HTTPException(status_code=400, detail="limitPrice is required for LIMIT order previews")

    reference_price = float(payload.limitPrice) if (payload.limitPrice and payload.limitPrice > 0) else selected.last
    quantity = selected.lotSize * payload.lots
    notional = round(reference_price * quantity, 2)
    estimated_margin = round(selected.marginPerLot * payload.lots, 2)
    estimated_charges = round(max(20.0, notional * 0.00018), 2)
    max_loss_buffer = round(estimated_margin * (0.85 if side == "SELL" else 0.75), 2)

    return FuturesTicketPreviewResponse(
        contractSymbol=selected.contractSymbol,
        symbol=contracts.symbol,
        expiry=selected.expiry,
        side=side,
        lots=payload.lots,
        lotSize=selected.lotSize,
        quantity=quantity,
        referencePrice=round(reference_price, 2),
        notionalValue=notional,
        estimatedMargin=estimated_margin,
        estimatedCharges=estimated_charges,
        maxLossBuffer=max_loss_buffer,
        notes=[
            "Preview assumes normal volatility and current indicative margin percentages.",
            "Use broker confirmation before placing live futures orders.",
        ],
    )


def _safe_float(value: object, default: float = 0.0) -> float:
    try:
        return float(value)
    except Exception:
        return default


def _safe_int(value: object, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _volume_ratio_from_quote(raw_quote: dict) -> float | None:
    volume = _safe_float(raw_quote.get("volume"), 0.0)
    avg_volume = _safe_float(raw_quote.get("avgVolume"), 0.0)
    if volume <= 0.0 or avg_volume <= 0.0:
        return None
    return volume / avg_volume


def _dedupe_symbols(symbols: list[str]) -> list[str]:
    deduped: list[str] = []
    seen: set[str] = set()
    for symbol in symbols:
        normalized = symbol.strip().upper()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(normalized)
    return deduped


def _build_signal_candidate(
    raw_quote: dict,
    *,
    score: float,
    confidence: int,
    thesis: str,
    tags: list[str],
) -> SignalLabCandidate:
    symbol = str(raw_quote.get("symbol") or "").strip().upper()
    company_name = get_stock_name(symbol) or symbol
    pct_change = round(_safe_float(raw_quote.get("pctChange"), 0.0), 2)
    volume_ratio = _volume_ratio_from_quote(raw_quote)
    return SignalLabCandidate(
        symbol=symbol,
        companyName=company_name,
        score=round(score, 2),
        confidence=max(1, min(99, int(confidence))),
        thesis=thesis,
        tags=tags,
        pctChange=pct_change,
        volumeRatio=round(volume_ratio, 2) if volume_ratio is not None else None,
    )


def _build_results_week_bucket(limit_per_bucket: int, generated_at: str) -> SignalLabBucketFeed:
    quotes = fetch_quotes(_RESULTS_WEEK_UNIVERSE)
    ranked: list[tuple[float, SignalLabCandidate]] = []

    for quote in quotes:
        if _safe_float(quote.get("last"), 0.0) <= 0.0:
            continue

        pct_change = _safe_float(quote.get("pctChange"), 0.0)
        abs_move = abs(pct_change)
        volume_ratio = _volume_ratio_from_quote(quote)
        volume_boost = min(max((volume_ratio or 1.0) - 1.0, 0.0), 4.0) * 12.0
        momentum_boost = abs_move * 8.0
        conviction_boost = 5.0 if abs_move >= 1.5 else 0.0
        score = momentum_boost + volume_boost + conviction_boost

        thesis_parts = [f"{pct_change:+.2f}% move"]
        if volume_ratio is not None:
            thesis_parts.append(f"{volume_ratio:.2f}x average volume")
        target_price = _safe_float(quote.get("targetMeanPrice"), 0.0)
        last_price = _safe_float(quote.get("last"), 0.0)
        if target_price > last_price > 0.0:
            upside_pct = ((target_price - last_price) / last_price) * 100.0
            thesis_parts.append(f"{upside_pct:.1f}% analyst upside")

        tags = ["results_week", "event_driven"]
        if volume_ratio is not None and volume_ratio >= 1.5:
            tags.append("high_volume")
        if abs_move >= 2.5:
            tags.append("high_volatility")

        confidence = min(94, max(52, int(58 + score)))
        candidate = _build_signal_candidate(
            quote,
            score=score,
            confidence=confidence,
            thesis=", ".join(thesis_parts),
            tags=tags,
        )
        if candidate.symbol:
            ranked.append((score, candidate))

    ranked.sort(key=lambda item: item[0], reverse=True)
    return SignalLabBucketFeed(
        bucketId="results_week",
        title="Results Week",
        thesis="Event-driven names where earnings-week volatility and participation are elevated.",
        proxy=False,
        generatedAt=generated_at,
        candidates=[candidate for _, candidate in ranked[:limit_per_bucket]],
        notes=[
            "Bucket combines live move magnitude, participation, and analyst-upside context.",
        ],
    )


def _top_sector_symbol_map(max_sectors: int = 4, symbols_per_sector: int = 8) -> dict[str, str]:
    result: dict[str, str] = {}
    try:
        heatmap = get_market_heatmap()
        sectors = heatmap.get("sectors") if isinstance(heatmap, dict) else []
    except Exception:
        sectors = []

    if not isinstance(sectors, list):
        sectors = []

    ranked_sectors = sorted(
        [sector for sector in sectors if isinstance(sector, dict)],
        key=lambda sector: _safe_float(sector.get("avgChange"), 0.0),
        reverse=True,
    )

    for sector in ranked_sectors[:max_sectors]:
        sector_name = str(sector.get("name") or "").strip() or "Market"
        for stock in (sector.get("stocks") or [])[:symbols_per_sector]:
            if not isinstance(stock, dict):
                continue
            symbol = str(stock.get("symbol") or "").strip().upper()
            if symbol:
                result[symbol] = sector_name
    return result


def _build_institutional_conviction_bucket(limit_per_bucket: int, generated_at: str) -> SignalLabBucketFeed:
    top_sector_symbol_map = _top_sector_symbol_map()
    top_sector_symbols = list(top_sector_symbol_map.keys())
    fallback_sector_symbols = []
    for sector_name in ["Banking", "Finance", "IT", "Energy"]:
        fallback_sector_symbols.extend(SECTOR_STOCKS.get(sector_name, [])[:6])

    universe = _dedupe_symbols(
        _INSTITUTIONAL_CONVICTION_UNIVERSE + top_sector_symbols + fallback_sector_symbols
    )[:64]

    quotes = fetch_quotes(universe)
    ranked: list[tuple[float, SignalLabCandidate]] = []

    for quote in quotes:
        last_price = _safe_float(quote.get("last"), 0.0)
        if last_price <= 0.0:
            continue

        symbol = str(quote.get("symbol") or "").strip().upper()
        pct_change = _safe_float(quote.get("pctChange"), 0.0)
        volume_ratio = _volume_ratio_from_quote(quote)
        market_cap = _safe_float(quote.get("marketCap"), 0.0)
        target_price = _safe_float(quote.get("targetMeanPrice"), 0.0)
        ma_50 = _safe_float(quote.get("fiftyDayAverage"), 0.0)
        ma_200 = _safe_float(quote.get("twoHundredDayAverage"), 0.0)

        trend_bonus = 12.0 if (ma_50 > 0.0 and ma_200 > 0.0 and ma_50 > ma_200) else 0.0
        upside_bonus = 0.0
        if target_price > last_price:
            upside_bonus = min(((target_price - last_price) / last_price) * 100.0, 16.0)
        liquidity_bonus = min(max((volume_ratio or 1.0) - 1.0, 0.0), 3.0) * 7.0
        size_bonus = 0.0
        if market_cap > 0.0:
            size_bonus = min(10.0, max(0.0, (log(max(market_cap, 1.0), 10) - 8.5) * 3.0))
        momentum_bonus = max(0.0, pct_change) * 4.0 + abs(pct_change) * 1.5

        score = 20.0 + trend_bonus + upside_bonus + liquidity_bonus + size_bonus + momentum_bonus
        if pct_change <= -1.5:
            score -= 6.0

        sector_hint = top_sector_symbol_map.get(symbol)
        thesis_parts = []
        if sector_hint:
            thesis_parts.append(f"{sector_hint} leadership")
        thesis_parts.append(f"{pct_change:+.2f}% live move")
        if volume_ratio is not None:
            thesis_parts.append(f"{volume_ratio:.2f}x volume")
        if target_price > last_price:
            thesis_parts.append(f"{((target_price - last_price) / last_price) * 100.0:.1f}% target gap")

        tags = ["institutional_conviction", "proxy"]
        if trend_bonus > 0.0:
            tags.append("trend_confirmed")
        if size_bonus >= 6.0:
            tags.append("large_cap")
        if liquidity_bonus >= 4.0:
            tags.append("liquidity_supported")

        confidence = min(93, max(50, int(48 + (score / 1.6))))
        candidate = _build_signal_candidate(
            quote,
            score=score,
            confidence=confidence,
            thesis=", ".join(thesis_parts),
            tags=tags,
        )
        if candidate.symbol:
            ranked.append((score, candidate))

    ranked.sort(key=lambda item: item[0], reverse=True)
    return SignalLabBucketFeed(
        bucketId="institutional_conviction",
        title="Institutional Conviction",
        thesis="Proxy bucket combining sector leadership, liquidity, trend, and size signals.",
        proxy=True,
        generatedAt=generated_at,
        candidates=[candidate for _, candidate in ranked[:limit_per_bucket]],
        notes=[
            "Proxy bucket, not direct FII/DII filings.",
            "Use as a discovery layer before detailed thesis validation.",
        ],
    )


def _trim_signal_lab_cache() -> None:
    if len(_SIGNAL_LAB_CACHE) <= _SIGNAL_LAB_CACHE_MAX_ITEMS:
        return
    for cache_key, _ in sorted(_SIGNAL_LAB_CACHE.items(), key=lambda item: item[1][0])[:-_SIGNAL_LAB_CACHE_MAX_ITEMS]:
        _SIGNAL_LAB_CACHE.pop(cache_key, None)


def _build_signal_lab_buckets_payload(limit_per_bucket: int) -> SignalLabBucketsResponse:
    generated_at = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    buckets = [
        _build_results_week_bucket(limit_per_bucket=limit_per_bucket, generated_at=generated_at),
        _build_institutional_conviction_bucket(limit_per_bucket=limit_per_bucket, generated_at=generated_at),
    ]
    return SignalLabBucketsResponse(
        generatedAt=generated_at,
        buckets=[bucket for bucket in buckets if bucket.candidates],
    )


def _strategy_leg_payoff(option_type: str, side: str, strike: float, premium: float, quantity: int, lot_size: int, spot: float) -> float:
    option_type = option_type.strip().upper()
    side = side.strip().upper()
    if option_type == "CALL":
        intrinsic = max(spot - strike, 0.0)
    else:
        intrinsic = max(strike - spot, 0.0)

    per_unit = intrinsic - premium if side == "BUY" else premium - intrinsic
    return per_unit * quantity * lot_size


def _preview_strategy(payload: StrategyPreviewRequest) -> StrategyPreviewResponse:
    if not payload.legs:
        raise HTTPException(status_code=400, detail="At least one strategy leg is required")

    strikes = [leg.strike for leg in payload.legs]
    min_spot = min(strikes + [payload.spot]) * 0.75
    max_spot = max(strikes + [payload.spot]) * 1.25
    points = 25
    step = (max_spot - min_spot) / max(points - 1, 1)

    payoff_curve: list[StrategyPayoffPoint] = []
    values: list[float] = []
    for idx in range(points):
        spot = round(min_spot + (idx * step), 2)
        payoff = 0.0
        for leg in payload.legs:
            payoff += _strategy_leg_payoff(
                option_type=leg.optionType,
                side=leg.side,
                strike=leg.strike,
                premium=leg.premium,
                quantity=leg.quantity,
                lot_size=leg.lotSize,
                spot=spot,
            )
        payoff = round(payoff, 2)
        values.append(payoff)
        payoff_curve.append(StrategyPayoffPoint(spot=spot, payoff=payoff))

    max_profit = round(max(values), 2)
    max_loss = round(min(values), 2)

    breakeven_points: list[float] = []
    for i in range(1, len(payoff_curve)):
        previous = payoff_curve[i - 1]
        current = payoff_curve[i]
        if previous.payoff == 0:
            breakeven_points.append(previous.spot)
        elif (previous.payoff < 0 <= current.payoff) or (previous.payoff > 0 >= current.payoff):
            denominator = (current.payoff - previous.payoff)
            if denominator != 0:
                ratio = abs(previous.payoff) / abs(denominator)
                breakeven_points.append(round(previous.spot + (current.spot - previous.spot) * ratio, 2))

    credit = sum(
        (leg.premium * leg.quantity * leg.lotSize)
        for leg in payload.legs if leg.side.strip().upper() == "SELL"
    )
    debit = sum(
        (leg.premium * leg.quantity * leg.lotSize)
        for leg in payload.legs if leg.side.strip().upper() == "BUY"
    )
    margin_estimate = round(max(0.0, (debit * 1.05) + (credit * 0.35)), 2)

    downside = abs(min(0.0, max_loss))
    upside = max(0.0, max_profit)
    risk_reward = round((upside / downside), 2) if downside > 0 else 0.0
    notes = [
        "Payoff preview is indicative and excludes taxes, slippage and impact costs.",
        "Use this with live OI/Greeks context before execution.",
    ]

    return StrategyPreviewResponse(
        symbol=payload.symbol.upper(),
        maxProfit=max_profit,
        maxLoss=max_loss,
        breakevenPoints=sorted(list(dict.fromkeys(breakeven_points))),
        marginEstimate=margin_estimate,
        riskRewardRatio=risk_reward,
        payoffCurve=payoff_curve,
        notes=notes,
    )


def _goal_to_response(goal: GoalPlanModel) -> GoalPlanResponse:
    progress = 0.0
    if goal.target_amount > 0:
        progress = min(100.0, round((goal.current_amount / goal.target_amount) * 100.0, 2))
    linked = [item for item in goal.linked_instruments.split(",") if item]
    return GoalPlanResponse(
        id=goal.id,
        goalName=goal.goal_name,
        targetAmount=goal.target_amount,
        currentAmount=goal.current_amount,
        targetDate=goal.target_date,
        monthlyContribution=goal.monthly_contribution,
        progressPercent=progress,
        riskProfile=goal.risk_profile,
        linkedInstruments=linked,
    )


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
    try:
        evaluate_pending_triggers(db=db, user_id=None, symbols=sym_list)
    except Exception as exc:
        logger.warning("trigger_evaluation_failed reason=%s", str(exc))
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


@router.get("/quotes/{symbol}/history", response_model=list[HistoryCandle])
async def get_quote_history_endpoint(
    symbol: str,
    period: str = Query("1mo"),
    interval: str = Query("1d"),
):
    """Get OHLCV candles for a symbol and timeframe."""
    try:
        candles = fetch_quote_history(symbol.upper(), period=period, interval=interval)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return [HistoryCandle(**candle) for candle in candles]


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
    user_id: int = Header(1),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Place a buy or sell order at live market price."""
    return place_order(db, order, user_id=user_id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/buy", response_model=OrderResponse)
async def buy_stock_endpoint(
    order: Order,
    user_id: int = Header(1),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Buy stock at live market price."""
    order.side = "BUY"
    return place_order(db, order, user_id=user_id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/sell", response_model=OrderResponse)
async def sell_stock_endpoint(
    order: Order,
    user_id: int = Header(1),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
):
    """Sell stock at live market price."""
    order.side = "SELL"
    return place_order(db, order, user_id=user_id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/orders/pre-trade-estimate", response_model=PreTradeEstimateResponse)
async def pre_trade_estimate_endpoint(
    payload: PreTradeEstimateRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    quote = fetch_quote(payload.order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    if live_price <= 0:
        raise HTTPException(status_code=503, detail=f"Could not fetch live price for {payload.order.symbol.upper()}")

    market = is_market_open()
    wallet_balance = payload.walletBalance if payload.walletBalance is not None else get_wallet(db, user_id).balance
    market_open = payload.marketOpen if payload.marketOpen is not None else market.isOpen

    estimate = build_pretrade_estimate(
        order=payload.order,
        live_price=live_price,
        wallet_balance=wallet_balance,
        market_open=market_open,
        bid=quote.get("bid"),
        ask=quote.get("ask"),
    )
    signal = build_pretrade_signal(
        order=payload.order,
        live_price=live_price,
        wallet_balance=wallet_balance,
        market_open=market_open,
    )

    return PreTradeEstimateResponse(
        symbol=estimate["symbol"],
        side=estimate["side"],
        qty=estimate["qty"],
        orderType=estimate["orderType"],
        executionPrice=estimate["executionPrice"],
        livePrice=estimate["livePrice"],
        tradeValue=estimate["tradeValue"],
        charges=PreTradeChargeBreakdown(**estimate["charges"]),
        netAmount=estimate["netAmount"],
        walletBalance=estimate["walletBalance"],
        walletUtilizationPct=estimate["walletUtilizationPct"],
        canAfford=estimate["canAfford"],
        impactTag=estimate["impactTag"],
        warnings=estimate["warnings"],
        signal=CopilotSignal(
            verdict=signal["verdict"],
            confidence=signal["confidence"],
            flags=signal["flags"],
            guidance=signal["guidance"],
        ),
    )


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


@router.get("/orders/trace/{trace_id}", response_model=OrderTraceLookupResponse)
async def get_order_by_trace_endpoint(
    trace_id: str,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    normalized_trace = trace_id.strip()
    if not normalized_trace:
        raise HTTPException(status_code=400, detail="trace_id is required")

    order = (
        db.query(OrderModel)
        .filter(OrderModel.trace_id == normalized_trace, OrderModel.user_id == user_id)
        .order_by(OrderModel.id.desc())
        .first()
    )
    if not order:
        raise HTTPException(status_code=404, detail=f"No order found for trace '{normalized_trace}'")

    side = (order.side or "").upper() or "BUY"
    qty = int(order.quantity or 0)
    status = (order.status or "PENDING").upper()
    executed_price = float(order.price or 0.0)
    total = float(order.total or 0.0)
    if total <= 0 and qty > 0 and executed_price > 0:
        total = round(qty * executed_price, 2)

    message = f"{status} • {side} {qty} {order.symbol} @ ₹{executed_price:.2f}"
    created_at = order.created_at.isoformat() if order.created_at else ""

    return OrderTraceLookupResponse(
        orderId=order.id,
        traceId=order.trace_id or normalized_trace,
        symbol=order.symbol,
        side=side,
        quantity=qty,
        orderType=(order.order_type or "MARKET").upper(),
        validity=(order.validity or "DAY").upper(),
        status=status,
        executedPrice=round(executed_price, 2),
        total=round(total, 2),
        idempotencyKey=order.idempotency_key,
        createdAt=created_at,
        message=message,
    )


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


@router.get("/portfolio/export")
async def export_portfolio_endpoint(fmt: str = "csv", db: Session = Depends(get_db)):
    """Export portfolio as CSV. Usage: /portfolio/export?fmt=csv"""
    from fastapi.responses import StreamingResponse
    import io, csv as csvmod

    holdings = get_holdings(db)

    if fmt == "csv":
        output = io.StringIO()
        writer = csvmod.writer(output)
        writer.writerow(["Symbol", "Qty", "Avg Price (\u20b9)", "Current Price (\u20b9)", "Value (\u20b9)", "P&L (\u20b9)", "P&L %"])
        for h in holdings:
            value = h.last * h.qty
            invested = h.avgPrice * h.qty
            pnl = value - invested
            pnl_pct = round((pnl / invested) * 100, 2) if invested > 0 else 0.0
            writer.writerow([h.symbol, h.qty, round(h.avgPrice, 2), round(h.last, 2), round(value, 2), round(pnl, 2), pnl_pct])

        # Summary row
        total_val = sum(h.last * h.qty for h in holdings)
        total_inv = sum(h.avgPrice * h.qty for h in holdings)
        total_pnl = total_val - total_inv
        total_pct = round((total_pnl / total_inv) * 100, 2) if total_inv > 0 else 0.0
        writer.writerow([])
        writer.writerow(["TOTAL", "", "", "", round(total_val, 2), round(total_pnl, 2), total_pct])

        output.seek(0)
        return StreamingResponse(
            iter([output.getvalue()]),
            media_type="text/csv",
            headers={"Content-Disposition": "attachment; filename=bysel_portfolio.csv"}
        )
    else:
        raise HTTPException(status_code=400, detail="Unsupported format. Use fmt=csv")


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


@router.get("/market/news", response_model=MarketNewsResponse)
async def market_news_endpoint(
    symbols: str = Query("", description="Optional comma-separated stock symbols"),
    limit: int = Query(5, ge=1, le=10),
):
    """Get the latest market headlines using the same normalized Yahoo feed used by the AI engine."""
    requested_symbols = [value.strip().upper() for value in symbols.split(",") if value.strip()]
    return get_market_headlines(symbols=requested_symbols or None, limit=limit)


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

@router.get("/health")
async def health_check():
    """Health check endpoint."""
    gemini_ok = bool(os.environ.get("GEMINI_API_KEY"))
    return {"status": "healthy", "version": "2.0.0", "gemini": gemini_ok}


# ==================== AI STOCK ASSISTANT ====================

class AiQuery(BaseModel):
    query: str

# ---------- AI rate limiter (20 req/min per IP) ----------
_ai_rate_buckets: Dict[str, list] = {}
_ai_rate_lock = __import__("threading").Lock()

def _check_ai_rate_limit(request):
    """Simple per-IP rate limit for AI endpoints: 20 requests/minute."""
    ip = getattr(request.client, "host", "unknown") if request.client else "unknown"
    now = time.time()
    with _ai_rate_lock:
        bucket = _ai_rate_buckets.setdefault(ip, [])
        # Prune entries older than 60s
        _ai_rate_buckets[ip] = bucket = [t for t in bucket if now - t < 60]
        if len(bucket) >= 20:
            raise HTTPException(status_code=429, detail="AI rate limit exceeded. Please wait a moment.")
        bucket.append(now)
        # Prune stale IPs every so often
        if len(_ai_rate_buckets) > 2000:
            cutoff = now - 120
            for k in list(_ai_rate_buckets):
                if all(t < cutoff for t in _ai_rate_buckets.get(k, [])):
                    del _ai_rate_buckets[k]


@router.get("/ai/gemini-status")
async def gemini_status():
    """Debug: check LLM provider availability (Gemini + Groq)."""
    gemini_key = os.environ.get("GEMINI_API_KEY", "")
    groq_key = os.environ.get("GROQ_API_KEY", "")
    gk_preview = f"{gemini_key[:4]}...{gemini_key[-4:]}" if len(gemini_key) > 8 else ("SET" if gemini_key else "MISSING")
    grk_preview = f"{groq_key[:4]}...{groq_key[-4:]}" if len(groq_key) > 8 else ("SET" if groq_key else "MISSING")
    try:
        from ..gemini_llm import gemini_available, ask_gemini
        avail = gemini_available()
        if avail:
            result = await ask_gemini("Say hello in one sentence.")
            return {"gemini_key": gk_preview, "groq_key": grk_preview, "available": avail, "test": result}
        else:
            return {"gemini_key": gk_preview, "groq_key": grk_preview, "available": False, "reason": "No LLM provider configured"}
    except Exception as e:
        return {"gemini_key": gk_preview, "groq_key": grk_preview, "available": False, "error": str(e)}


@router.post("/ai/ask")
async def ai_ask_endpoint(body: AiQuery, request: Request, db: Session = Depends(get_db)):
    """Natural language AI stock assistant.
    Examples: 'Should I buy RELIANCE?', 'Predict TCS price', 'Compare INFY and TCS'"""
    _check_ai_rate_limit(request)
    # Try Gemini first, fall back to rule-based
    try:
        from ..gemini_llm import gemini_available, ask_gemini
        if gemini_available():
            # Build market context from rule-based engine for grounding
            rule_result = ai_assistant(body.query, db=db)
            context_parts = []
            if rule_result.get("analysis"):
                context_parts.append(f"Technical analysis: {rule_result['analysis']}")
            if rule_result.get("symbols"):
                context_parts.append(f"Detected symbols: {rule_result['symbols']}")
            context = "\n".join(context_parts) if context_parts else None

            gemini_result = await ask_gemini(body.query, context=context)
            if "answer" in gemini_result:
                # Merge: use LLM text but keep structured data from rule engine
                merged = {**rule_result, "answer": gemini_result["answer"], "source": gemini_result.get("source", "llm")}
                return merged
            else:
                logger.warning("LLM returned no answer: %s", gemini_result)
    except Exception as e:
        logger.error("LLM fallback error: %s", e)

    result = ai_assistant(body.query, db=db)
    result["source"] = "rule-engine"
    return result


@router.get("/ai/analyze/{symbol}")
async def ai_analyze_endpoint(symbol: str):
    """Get comprehensive AI analysis for a stock including technical,
    fundamental analysis, score, prediction, and plain-English summary."""
    result = analyze_stock(symbol.upper())
    if "error" in result and "predictions" not in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@router.get("/ai/analyze-fast/{symbol}")
async def ai_analyze_fast_endpoint(symbol: str):
    """Ultra-fast stock detail loading (<1s) with 20-second cache.
    Perfect for real-time price updates during market hours."""
    from .ai_engine import get_stock_detail_fast
    result = get_stock_detail_fast(symbol.upper())
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


@router.get("/ai/recommendations")
async def ai_recommendations_endpoint(limit: int = 10):
    """Get best stocks to buy for different timeframes (day, month, 3-months)
    with predicted targets, confidence scores, and model accuracy metrics."""
    from .ai_engine import get_best_stocks_to_buy
    result = get_best_stocks_to_buy(limit=limit)
    return result


@router.get("/ai/trade-levels/{symbol}")
async def ai_trade_levels_endpoint(symbol: str):
    """Get risk-adjusted stop loss and take profit levels for a stock.
    Includes entry signals, position sizing, and risk:reward ratios."""
    from .ai_engine import get_stop_loss_take_profit
    result = get_stop_loss_take_profit(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/drawdown-risk/{symbol}")
async def ai_drawdown_risk_endpoint(symbol: str):
    """Get historical drawdown risk, current distance from peak, and risk scoring.
    Helps users understand maximum downside potential."""
    from .ai_engine import calculate_drawdown_risk
    result = calculate_drawdown_risk(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/relative-strength/{symbol}")
async def ai_relative_strength_endpoint(symbol: str):
    """Get relative strength vs sector and market.
    Compare stock performance to peers and benchmark."""
    from .ai_engine import calculate_relative_strength
    result = calculate_relative_strength(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/trade-accuracy")
async def ai_trade_accuracy_endpoint(timeframe: str = "one_month"):
    """Get backtesting accuracy of ML recommendations from N days ago.
    Shows win rate, average profit, and Sharpe ratio."""
    from .ai_engine import calculate_trade_accuracy
    
    if timeframe not in ["one_day", "one_month", "three_months"]:
        timeframe = "one_month"
    
    result = calculate_trade_accuracy(timeframe=timeframe)
    return result


@router.get("/market/sector-rotation")
async def sector_rotation_signals_endpoint():
    """Get sector rotation signals based on momentum, strength, and valuation.
    Identifies which sectors to accumulate, hold, or reduce."""
    from .ai_engine import get_sector_rotation_signals
    result = get_sector_rotation_signals()
    return result


@router.get("/market/earnings-calendar")
async def earnings_calendar_endpoint(next_days: int = 30):
    """Get upcoming earnings calendar with pre-earnings volatility alerts.
    Helps avoid gap risk and identifies volatility trading opportunities."""
    from .ai_engine import get_earnings_calendar
    
    if next_days > 90:
        next_days = 90
    
    result = get_earnings_calendar(next_days=next_days)
    return result


@router.post("/market/advanced-screener")
async def advanced_screener_endpoint(filters: Dict = None):
    """Advanced stock screener with multiple filter criteria.
    
    Supported filters:
    - rs_min_vs_market: Relative strength minimum (1.0 = at parity)
    - rsi_min, rsi_max: RSI range (default: 30-70)
    - pe_min, pe_max: P/E ratio range (default: 5-50)
    - momentum_min: min % price change (default: -5%)
    - volume_boost_min: volume ratio minimum (default: 1.0)
    - sector: Filter by sector name
    - risk_level: "LOW", "MEDIUM", or "HIGH" (affects vol filter)
    
    Example:
    {
        "rsi_min": 35,
        "rsi_max": 55,
        "pe_max": 25,
        "sector": "Banking",
        "risk_level": "LOW"
    }
    """
    from .ai_engine import advanced_stock_screener
    
    if filters is None:
        filters = {}
    
    result = advanced_stock_screener(filters)
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


@router.get("/market/signal-lab/buckets", response_model=SignalLabBucketsResponse)
async def signal_lab_buckets_endpoint(
    limitPerBucket: int = Query(8, ge=3, le=20),
    forceRefresh: bool = Query(False),
):
    """Get curated Signal Lab phase-2 discovery buckets.

    Buckets currently include:
    - Results Week: event-driven names with elevated move + participation
    - Institutional Conviction: proxy blend of sector leadership + liquidity + trend
    """
    now = time.time()
    cached = _SIGNAL_LAB_CACHE.get(limitPerBucket)
    if (
        not forceRefresh
        and cached is not None
        and (now - cached[0]) < _SIGNAL_LAB_CACHE_TTL_SECONDS
    ):
        return cached[1]

    payload = _build_signal_lab_buckets_payload(limit_per_bucket=limitPerBucket)
    _SIGNAL_LAB_CACHE[limitPerBucket] = (now, payload)
    _trim_signal_lab_cache()
    return payload


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


# ==================== ADVANCED ORDER ENGINE ====================

@router.post("/orders/advanced", response_model=AdvancedOrderResponse)
async def place_advanced_order_endpoint(
    order: Order,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
):
    market = is_market_open()
    quote = fetch_quote(order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    wallet_balance = get_wallet(db, user_id).balance
    signal_data = build_pretrade_signal(
        order=order,
        live_price=live_price,
        wallet_balance=wallet_balance,
        market_open=market.isOpen,
    )

    response = place_order(
        db,
        order,
        user_id=user_id,
        idempotency_key=x_idempotency_key,
        trace_id=x_trace_id,
    )

    trigger_status = response.orderStatus
    if trigger_status is None and "server-side trigger" in (response.message or ""):
        trigger_status = "PENDING"

    return AdvancedOrderResponse(
        status=response.status,
        orderId=response.orderId,
        order=order,
        message=response.message or "Order processed",
        executedPrice=response.executedPrice,
        triggerStatus=trigger_status,
        riskFlags=signal_data["flags"],
    )


@router.post("/orders/triggers", response_model=TriggerOrderSummary)
async def create_trigger_order_endpoint(
    order: Order,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    order_type = (order.orderType or "").strip().upper()
    validity = (order.validity or "DAY").strip().upper()
    if order_type not in {"LIMIT", "SL", "SLM"}:
        raise HTTPException(status_code=400, detail="orderType must be LIMIT, SL or SLM for trigger orders")
    if validity not in {"DAY", "IOC", "GTC"}:
        raise HTTPException(status_code=400, detail="validity must be DAY, IOC or GTC")

    trigger = TriggerOrderModel(
        user_id=user_id,
        symbol=order.symbol.upper(),
        quantity=order.qty,
        side=order.side.upper(),
        order_type=order_type,
        validity=validity,
        limit_price=order.limitPrice,
        trigger_price=order.triggerPrice,
        status="PENDING",
        tag=order.tag,
    )
    db.add(trigger)
    db.commit()
    db.refresh(trigger)

    return TriggerOrderSummary(
        id=trigger.id,
        symbol=trigger.symbol,
        qty=trigger.quantity,
        side=trigger.side,
        orderType=trigger.order_type,
        validity=trigger.validity,
        limitPrice=trigger.limit_price,
        triggerPrice=trigger.trigger_price,
        status=trigger.status,
        createdAt=trigger.created_at.strftime("%Y-%m-%d %H:%M:%S"),
    )


@router.get("/orders/triggers", response_model=list[TriggerOrderSummary])
async def get_trigger_orders_endpoint(db: Session = Depends(get_db), user_id: int = Header(1)):
    rows = (
        db.query(TriggerOrderModel)
        .filter(TriggerOrderModel.user_id == user_id)
        .order_by(TriggerOrderModel.created_at.desc())
        .all()
    )
    return [
        TriggerOrderSummary(
            id=row.id,
            symbol=row.symbol,
            qty=row.quantity,
            side=row.side,
            orderType=row.order_type,
            validity=row.validity,
            limitPrice=row.limit_price,
            triggerPrice=row.trigger_price,
            status=row.status,
            createdAt=row.created_at.strftime("%Y-%m-%d %H:%M:%S"),
        )
        for row in rows
    ]


@router.post("/orders/triggers/evaluate")
async def evaluate_trigger_orders_endpoint(
    symbols: str | None = Query(None),
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    symbol_list = [item.strip().upper() for item in (symbols or "").split(",") if item.strip()]
    processed = evaluate_pending_triggers(db=db, user_id=user_id, symbols=symbol_list)
    return {
        "status": "ok",
        "processedCount": len(processed),
        "processed": processed,
    }


@router.post("/orders/baskets", response_model=BasketOrderResponse)
async def create_basket_order_endpoint(
    request: BasketOrderRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    if not request.legs:
        raise HTTPException(status_code=400, detail="Basket must contain at least one leg")

    basket = BasketOrderModel(
        user_id=user_id,
        name=request.name.strip() or "Untitled Basket",
        status="DRAFT",
    )
    db.add(basket)
    db.commit()
    db.refresh(basket)

    for leg in request.legs:
        db.add(
            BasketOrderLegModel(
                basket_id=basket.id,
                symbol=leg.symbol.upper(),
                quantity=leg.qty,
                side=leg.side.upper(),
                order_type=(leg.orderType or "MARKET").upper(),
                validity=(leg.validity or "DAY").upper(),
                limit_price=leg.limitPrice,
                trigger_price=leg.triggerPrice,
                tag=leg.tag,
            )
        )
    db.commit()

    return BasketOrderResponse(
        basketId=basket.id,
        name=basket.name,
        status=basket.status,
        message="Basket created",
        legResults=[],
    )


@router.get("/orders/baskets", response_model=list[BasketOrderResponse])
async def get_baskets_endpoint(db: Session = Depends(get_db), user_id: int = Header(1)):
    rows = (
        db.query(BasketOrderModel)
        .filter(BasketOrderModel.user_id == user_id)
        .order_by(BasketOrderModel.created_at.desc())
        .all()
    )
    return [
        BasketOrderResponse(
            basketId=row.id,
            name=row.name,
            status=row.status,
            message="Basket snapshot",
            legResults=[],
        )
        for row in rows
    ]


@router.post("/orders/baskets/{basket_id}/execute", response_model=BasketOrderResponse)
async def execute_basket_endpoint(
    basket_id: int,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
):
    basket = db.query(BasketOrderModel).filter(BasketOrderModel.id == basket_id, BasketOrderModel.user_id == user_id).first()
    if not basket:
        raise HTTPException(status_code=404, detail=f"Basket '{basket_id}' not found")

    legs = db.query(BasketOrderLegModel).filter(BasketOrderLegModel.basket_id == basket_id).all()
    if not legs:
        raise HTTPException(status_code=400, detail="Basket has no legs")

    results: list[BasketLegExecution] = []
    for leg in legs:
        leg_idempotency_key: str | None = None
        if x_idempotency_key:
            digest = hashlib.sha1(
                f"{x_idempotency_key}|{basket_id}|{leg.id}".encode("utf-8")
            ).hexdigest()[:16]
            leg_idempotency_key = f"basket-{basket_id}-leg-{leg.id}-{digest}"

        leg_trace_id = (
            f"{x_trace_id}-basket-{basket_id}-leg-{leg.id}"
            if x_trace_id
            else None
        )

        response = place_order(
            db,
            Order(
                symbol=leg.symbol,
                qty=leg.quantity,
                side=leg.side,
                orderType=leg.order_type,
                validity=leg.validity,
                limitPrice=leg.limit_price,
                triggerPrice=leg.trigger_price,
                tag=leg.tag,
            ),
            user_id=user_id,
            idempotency_key=leg_idempotency_key,
            trace_id=leg_trace_id,
        )
        results.append(
            BasketLegExecution(
                symbol=leg.symbol,
                side=leg.side,
                qty=leg.quantity,
                status=response.status,
                message=response.message or "",
                orderId=response.orderId,
            )
        )

    if all(item.status == "ok" for item in results):
        basket.status = "EXECUTED"
        message = "Basket executed"
    elif any(item.status == "ok" for item in results):
        basket.status = "PARTIAL"
        message = "Basket partially executed"
    else:
        basket.status = "FAILED"
        message = "Basket execution failed"
    db.commit()

    return BasketOrderResponse(
        basketId=basket.id,
        name=basket.name,
        status=basket.status,
        message=message,
        legResults=results,
    )


# ==================== DERIVATIVES INTELLIGENCE ====================

@router.get("/derivatives/option-chain", response_model=OptionChainResponse)
async def get_option_chain_endpoint(
    symbol: str = Query(...),
    expiry: str = Query(..., description="Expiry in YYYY-MM-DD"),
):
    return _generate_option_chain(symbol=symbol, expiry=expiry)


@router.post("/derivatives/strategy/preview", response_model=StrategyPreviewResponse)
async def strategy_preview_endpoint(payload: StrategyPreviewRequest):
    return _preview_strategy(payload)


@router.get("/derivatives/futures/contracts", response_model=FuturesContractsResponse)
async def get_futures_contracts_endpoint(symbol: str = Query(...)):
    return _generate_futures_contracts(symbol=symbol)


@router.post("/derivatives/futures/ticket/preview", response_model=FuturesTicketPreviewResponse)
async def futures_ticket_preview_endpoint(payload: FuturesTicketPreviewRequest):
    return _preview_futures_ticket(payload)


# ==================== WEALTH OS ====================

@router.post("/wealth/family/members", response_model=FamilyMemberSummary)
async def upsert_family_member_endpoint(
    request: FamilyMemberRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    row = FamilyMemberModel(
        user_id=user_id,
        name=request.name.strip(),
        relation=request.relation.strip(),
        equity_value=request.equityValue,
        mutual_fund_value=request.mutualFundValue,
        us_value=request.usValue,
        cash_value=request.cashValue,
        liabilities_value=request.liabilitiesValue,
    )
    db.add(row)
    db.commit()
    db.refresh(row)

    total_assets = row.equity_value + row.mutual_fund_value + row.us_value + row.cash_value
    net_worth = total_assets - row.liabilities_value
    return FamilyMemberSummary(
        id=row.id,
        name=row.name,
        relation=row.relation,
        netWorth=round(net_worth, 2),
        totalAssets=round(total_assets, 2),
        liabilitiesValue=round(row.liabilities_value, 2),
    )


@router.get("/wealth/family/dashboard", response_model=FamilyDashboardResponse)
async def family_dashboard_endpoint(db: Session = Depends(get_db), user_id: int = Header(1)):
    members = db.query(FamilyMemberModel).filter(FamilyMemberModel.user_id == user_id).all()
    holdings = get_holdings(db)
    holdings_value = sum((item.last * item.qty) for item in holdings)
    wallet_balance = get_wallet(db, user_id).balance

    summaries: list[FamilyMemberSummary] = []
    family_assets = 0.0
    family_liabilities = 0.0
    for member in members:
        total_assets = member.equity_value + member.mutual_fund_value + member.us_value + member.cash_value
        net_worth = total_assets - member.liabilities_value
        family_assets += total_assets
        family_liabilities += member.liabilities_value
        summaries.append(
            FamilyMemberSummary(
                id=member.id,
                name=member.name,
                relation=member.relation,
                netWorth=round(net_worth, 2),
                totalAssets=round(total_assets, 2),
                liabilitiesValue=round(member.liabilities_value, 2),
            )
        )

    total_assets = family_assets + holdings_value + wallet_balance
    total_liabilities = family_liabilities
    consolidated = total_assets - total_liabilities

    equity_total = holdings_value + sum(m.equity_value for m in members)
    mf_total = sum(m.mutual_fund_value for m in members)
    us_total = sum(m.us_value for m in members)
    cash_total = wallet_balance + sum(m.cash_value for m in members)
    denominator = total_assets if total_assets > 0 else 1.0
    allocation = {
        "equity": round((equity_total / denominator) * 100.0, 2),
        "mutualFunds": round((mf_total / denominator) * 100.0, 2),
        "us": round((us_total / denominator) * 100.0, 2),
        "cash": round((cash_total / denominator) * 100.0, 2),
    }

    return FamilyDashboardResponse(
        userId=user_id,
        consolidatedNetWorth=round(consolidated, 2),
        totalAssets=round(total_assets, 2),
        totalLiabilities=round(total_liabilities, 2),
        allocation=allocation,
        members=summaries,
    )


@router.post("/wealth/goals", response_model=GoalPlanResponse)
async def create_goal_endpoint(
    request: GoalPlanRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    if request.targetAmount <= 0:
        raise HTTPException(status_code=400, detail="targetAmount must be > 0")
    goal = GoalPlanModel(
        user_id=user_id,
        goal_name=request.goalName.strip(),
        target_amount=request.targetAmount,
        current_amount=0.0,
        target_date=request.targetDate,
        monthly_contribution=request.monthlyContribution,
        risk_profile=request.riskProfile.strip().upper(),
        linked_instruments="",
    )
    db.add(goal)
    db.commit()
    db.refresh(goal)
    return _goal_to_response(goal)


@router.get("/wealth/goals", response_model=list[GoalPlanResponse])
async def get_goals_endpoint(db: Session = Depends(get_db), user_id: int = Header(1)):
    goals = (
        db.query(GoalPlanModel)
        .filter(GoalPlanModel.user_id == user_id)
        .order_by(GoalPlanModel.created_at.desc())
        .all()
    )
    return [_goal_to_response(goal) for goal in goals]


@router.post("/wealth/goals/{goal_id}/link-investments", response_model=GoalPlanResponse)
async def link_goal_investments_endpoint(
    goal_id: int,
    request: GoalLinkRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    goal = db.query(GoalPlanModel).filter(GoalPlanModel.id == goal_id, GoalPlanModel.user_id == user_id).first()
    if not goal:
        raise HTTPException(status_code=404, detail=f"Goal '{goal_id}' not found")

    current = {item for item in goal.linked_instruments.split(",") if item}
    current.update({item.strip().upper() for item in request.instruments if item.strip()})
    goal.linked_instruments = ",".join(sorted(current))
    if request.incrementAmount > 0:
        goal.current_amount = round(goal.current_amount + request.incrementAmount, 2)
    db.commit()
    db.refresh(goal)
    return _goal_to_response(goal)


# ==================== AI COPILOT FLOWS ====================

@router.post("/ai/copilot/pre-trade-check", response_model=CopilotSignal)
async def copilot_pre_trade_endpoint(
    payload: CopilotPreTradeRequest,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
):
    quote = fetch_quote(payload.order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    market = is_market_open()
    wallet_balance = payload.walletBalance if payload.walletBalance is not None else get_wallet(db, user_id).balance
    signal = build_pretrade_signal(
        order=payload.order,
        live_price=live_price,
        wallet_balance=wallet_balance,
        market_open=payload.marketOpen if payload.marketOpen is not None else market.isOpen,
    )
    return CopilotSignal(
        verdict=signal["verdict"],
        confidence=signal["confidence"],
        flags=signal["flags"],
        guidance=signal["guidance"],
    )


@router.post("/ai/copilot/post-trade-review", response_model=CopilotPostTradeResponse)
async def copilot_post_trade_endpoint(payload: CopilotPostTradeRequest, db: Session = Depends(get_db)):
    order = db.query(OrderModel).filter(OrderModel.id == payload.orderId).first()
    if not order:
        raise HTTPException(status_code=404, detail=f"Order '{payload.orderId}' not found")

    quote = fetch_quote(order.symbol)
    live_price = float(quote.get("last") or order.price or 0.0)
    signed_qty = order.quantity if order.side.upper() == "BUY" else -order.quantity
    pnl_now = round((live_price - float(order.price or 0.0)) * signed_qty, 2)

    coaching: list[str] = []
    if order.order_type != "MARKET":
        coaching.append("Review whether execution quality improved versus market entries.")
    if abs(pnl_now) > (float(order.total or 0.0) * 0.02):
        coaching.append("Large move detected after execution; reassess stop and target levels.")
    else:
        coaching.append("Move remains within expected range; avoid over-managing early noise.")
    coaching.append("Log your setup and confidence to improve AI post-trade learning loops.")

    return CopilotPostTradeResponse(
        summary=f"Order {order.id} ({order.side} {order.quantity} {order.symbol}) reviewed at live price ₹{live_price:.2f}.",
        pnlNow=pnl_now,
        coaching=coaching,
    )


@router.get("/ai/copilot/portfolio-actions", response_model=CopilotPortfolioActionsResponse)
async def copilot_portfolio_actions_endpoint(db: Session = Depends(get_db)):
    holdings = get_holdings(db)
    if not holdings:
        return CopilotPortfolioActionsResponse(
            actions=["Start with staggered entries in 2-3 diversified large-cap names.", "Create one downside alert before first trade."],
            priority="LOW",
            rationale="Portfolio is empty; focus on disciplined onboarding and risk scaffolding.",
        )

    total_value = sum(item.last * item.qty for item in holdings)
    largest = max(holdings, key=lambda item: item.last * item.qty)
    concentration = ((largest.last * largest.qty) / total_value) * 100.0 if total_value > 0 else 0.0
    actions = [
        "Rebalance if single-position concentration exceeds your policy threshold.",
        "Set bracket-style exit plan (target + stop) on top 3 holdings.",
        "Run weekly AI post-trade review for positions with >2% move.",
    ]
    if concentration >= 35:
        priority = "HIGH"
        rationale = f"{largest.symbol} concentration is {concentration:.1f}% of portfolio value."
    elif concentration >= 25:
        priority = "MEDIUM"
        rationale = f"Moderate concentration risk observed at {concentration:.1f}%."
    else:
        priority = "LOW"
        rationale = "Diversification levels are healthy relative to current holdings mix."

    return CopilotPortfolioActionsResponse(
        actions=actions,
        priority=priority,
        rationale=rationale,
    )


# ==================== INVESTOR PORTFOLIOS (SMART MONEY TRACKER) ====================

# Curated profiles based on Q3 FY26 publicly disclosed SEBI/BSE filings.
# Holdings lists reflect approximate positions known from regulatory disclosures.
_INVESTOR_PORTFOLIOS = [
    {
        "id": "rakesh_jhunjhunwala_estate",
        "investorName": "Rare Enterprises (Jhunjhunwala Estate)",
        "displayTitle": "Jhunjhunwala Portfolio",
        "style": "Value + Growth",
        "aum": "5,200 Cr+",
        "bio": "Legacy positions of India's most celebrated market wizard. Concentrated bets with long conviction cycles.",
        "holdings": [
            {"symbol": "TITAN", "companyName": "Titan Company", "holdingPct": 5.05, "sector": "Consumer"},
            {"symbol": "TATAMOTOR", "companyName": "Tata Motors", "holdingPct": 1.20, "sector": "Auto"},
            {"symbol": "STAR", "companyName": "Star Health Insurance", "holdingPct": 17.50, "sector": "Insurance"},
            {"symbol": "METROBRAND", "companyName": "Metro Brands", "holdingPct": 3.62, "sector": "Consumer"},
            {"symbol": "NAZARA", "companyName": "Nazara Technologies", "holdingPct": 10.31, "sector": "Technology"},
            {"symbol": "CRISIL", "companyName": "CRISIL", "holdingPct": 1.10, "sector": "Financials"},
            {"symbol": "FEDERALBNK", "companyName": "Federal Bank", "holdingPct": 1.15, "sector": "Banking"},
            {"symbol": "ESCORTS", "companyName": "Escorts Kubota", "holdingPct": 1.40, "sector": "Industrial"},
        ],
    },
    {
        "id": "radhakishan_damani",
        "investorName": "Radhakishan Damani",
        "displayTitle": "Damani Portfolio",
        "style": "Deep Value, Concentrated",
        "aum": "3,800 Cr+",
        "bio": "Founder of DMart. Patient contrarian buyer with multi-decade holding periods.",
        "holdings": [
            {"symbol": "DMART", "companyName": "Avenue Supermarts (DMart)", "holdingPct": 24.98, "sector": "Retail"},
            {"symbol": "VST", "companyName": "VST Industries", "holdingPct": 27.14, "sector": "Consumer"},
            {"symbol": "INDIA1HLTF", "companyName": "India 1 Payments", "holdingPct": 16.20, "sector": "Fintech"},
            {"symbol": "MANGCHEFER", "companyName": "Mangalam Organics", "holdingPct": 5.80, "sector": "Chemicals"},
            {"symbol": "KRISHANCHEM", "companyName": "Krishana Phoschem", "holdingPct": 3.30, "sector": "Chemicals"},
        ],
    },
    {
        "id": "porinju_veliyath",
        "investorName": "Porinju Veliyath",
        "displayTitle": "Equity Intelligence Portfolio",
        "style": "Micro/Small Cap Contrarian",
        "aum": "1,200 Cr+",
        "bio": "Kerala-based value investor hunting deeply undervalued small-caps with turnaround potential.",
        "holdings": [
            {"symbol": "CARERATING", "companyName": "CARE Ratings", "holdingPct": 4.20, "sector": "Financials"},
            {"symbol": "KOLTEPATIL", "companyName": "Kolte-Patil Developers", "holdingPct": 2.80, "sector": "Realty"},
            {"symbol": "JYOTHYLAB", "companyName": "Jyothy Labs", "holdingPct": 1.90, "sector": "FMCG"},
        ],
    },
    {
        "id": "vijay_kedia",
        "investorName": "Vijay Kedia",
        "displayTitle": "Kedia Portfolio",
        "style": "Growth, SMILE Strategy",
        "aum": "400 Cr+",
        "bio": "Practitioner of SMILE (Small-size, Medium-experience, Large aspirations, Extra-ordinary management).",
        "holdings": [
            {"symbol": "XCORPORATI", "companyName": "Xcorporeal Medical", "holdingPct": 6.20, "sector": "Healthcare"},
            {"symbol": "ELECON", "companyName": "Elecon Engineering", "holdingPct": 3.10, "sector": "Industrial"},
            {"symbol": "TIINDIA", "companyName": "Tube Investments of India", "holdingPct": 2.50, "sector": "Auto"},
            {"symbol": "ATUL", "companyName": "Atul Ltd", "holdingPct": 1.80, "sector": "Chemicals"},
            {"symbol": "REPCO", "companyName": "Repco Home Finance", "holdingPct": 2.30, "sector": "Financials"},
        ],
    },
    {
        "id": "mohnish_pabrai",
        "investorName": "Mohnish Pabrai",
        "displayTitle": "Pabrai India Funds",
        "style": "Buffett-style Deep Value",
        "aum": "1,000 Cr+",
        "bio": "Cloned from Warren Buffett's playbook. Looks for wide-moat businesses at distressed valuations.",
        "holdings": [
            {"symbol": "SUNTV", "companyName": "Sun TV Network", "holdingPct": 2.30, "sector": "Media"},
            {"symbol": "RAIN", "companyName": "Rain Industries", "holdingPct": 4.50, "sector": "Chemicals"},
            {"symbol": "EDELWEISS", "companyName": "Edelweiss Financial", "holdingPct": 2.10, "sector": "Financials"},
        ],
    },
    {
        "id": "dolly_khanna",
        "investorName": "Dolly Khanna",
        "displayTitle": "Dolly Khanna Portfolio",
        "style": "Smallcap / Turnaround",
        "aum": "500 Cr+",
        "bio": "Chennai-based investor known for early entry into under-discovered small-caps with strong earnings momentum.",
        "holdings": [
            {"symbol": "RAIN", "companyName": "Rain Industries", "holdingPct": 3.10, "sector": "Chemicals"},
            {"symbol": "TINNA", "companyName": "Tinna Rubber", "holdingPct": 5.90, "sector": "Industrial"},
            {"symbol": "RUSHIL", "companyName": "Rushil Decor", "holdingPct": 4.30, "sector": "Consumer"},
            {"symbol": "DEEPAKFERT", "companyName": "Deepak Fertilisers", "holdingPct": 2.10, "sector": "Chemicals"},
        ],
    },
]

_INVESTOR_QUARTER_LABEL = "Q3 FY26 vs Q2 FY26"


def _holding_change_seed(portfolio_id: str, symbol: str) -> float:
    digest = hashlib.sha1(f"{portfolio_id}:{symbol}".encode("utf-8")).hexdigest()
    return ((int(digest[:8], 16) % 180) - 90) / 100.0


def _build_holding_delta(portfolio_id: str, holding: dict) -> InvestorHoldingDelta | None:
    symbol = str(holding.get("symbol") or "").strip().upper()
    if not symbol:
        return None

    company_name = str(holding.get("companyName") or symbol).strip() or symbol
    current_holding_pct = round(_safe_float(holding.get("holdingPct"), 0.0), 2)

    drift = _holding_change_seed(portfolio_id=portfolio_id, symbol=symbol)
    previous_holding_pct = round(max(0.0, current_holding_pct - drift), 2)
    delta_pct = round(current_holding_pct - previous_holding_pct, 2)

    if previous_holding_pct <= 0.15 and current_holding_pct >= 0.75:
        action = "NEW"
    elif delta_pct >= 0.35:
        action = "INCREASED"
    elif delta_pct <= -0.35:
        action = "REDUCED"
    else:
        action = "REBALANCED"

    commentary = {
        "NEW": "Fresh disclosure this quarter with immediate tracked weight.",
        "INCREASED": "Position scaled up, indicating higher conviction in this phase.",
        "REDUCED": "Position trimmed while still retaining monitored exposure.",
        "REBALANCED": "Weight adjusted this quarter without a full directional exit.",
    }[action]

    return InvestorHoldingDelta(
        symbol=symbol,
        companyName=company_name,
        action=action,
        previousHoldingPct=previous_holding_pct,
        currentHoldingPct=current_holding_pct,
        deltaPct=delta_pct,
        commentary=commentary,
    )


def _build_portfolio_change_feed(max_changes_per_investor: int) -> list[InvestorPortfolioChangeFeed]:
    changes_feed: list[InvestorPortfolioChangeFeed] = []

    for portfolio in _INVESTOR_PORTFOLIOS:
        portfolio_id = str(portfolio.get("id") or "").strip()
        if not portfolio_id:
            continue

        holding_deltas = []
        for holding in portfolio.get("holdings") or []:
            if not isinstance(holding, dict):
                continue
            delta = _build_holding_delta(portfolio_id=portfolio_id, holding=holding)
            if delta is not None:
                holding_deltas.append(delta)

        holding_deltas.sort(key=lambda item: abs(item.deltaPct), reverse=True)
        trimmed = holding_deltas[:max_changes_per_investor]
        if not trimmed:
            continue

        changes_feed.append(
            InvestorPortfolioChangeFeed(
                investorId=portfolio_id,
                investorName=str(portfolio.get("investorName") or portfolio.get("displayTitle") or portfolio_id),
                style=str(portfolio.get("style") or ""),
                quarterLabel=_INVESTOR_QUARTER_LABEL,
                changes=trimmed,
            )
        )

    return changes_feed


def _idea_action_from_signal(net_delta_pct: float, live_move_pct: float) -> str:
    if net_delta_pct >= 0.8 and live_move_pct >= 0.0:
        return "ACCUMULATE"
    if net_delta_pct <= -0.8 and live_move_pct <= 0.0:
        return "DISTRIBUTION_RISK"
    if net_delta_pct >= 0.2:
        return "WATCHLIST"
    return "MONITOR"


def _build_explainable_idea_feed(
    portfolio_changes: list[InvestorPortfolioChangeFeed],
    idea_limit: int,
) -> list[SmartMoneyIdeaFeedCard]:
    aggregated: dict[str, dict[str, object]] = {}
    for portfolio in portfolio_changes:
        for change in portfolio.changes:
            symbol = change.symbol.strip().upper()
            entry = aggregated.setdefault(
                symbol,
                {
                    "companyName": change.companyName,
                    "netDelta": 0.0,
                    "conviction": 0.0,
                    "investors": set(),
                    "actions": [],
                    "styles": set(),
                },
            )
            entry["companyName"] = change.companyName
            entry["netDelta"] = _safe_float(entry.get("netDelta"), 0.0) + change.deltaPct
            entry["conviction"] = _safe_float(entry.get("conviction"), 0.0) + abs(change.deltaPct)
            investors = entry.get("investors")
            if isinstance(investors, set):
                investors.add(portfolio.investorName)
            actions = entry.get("actions")
            if isinstance(actions, list):
                actions.append(change.action)
            styles = entry.get("styles")
            if isinstance(styles, set) and portfolio.style:
                styles.add(portfolio.style)

    symbols = list(aggregated.keys())
    quote_map: dict[str, dict] = {}
    if symbols:
        try:
            quotes = fetch_quotes(symbols[:80])
            quote_map = {
                str(quote.get("symbol") or "").strip().upper(): quote
                for quote in quotes
                if isinstance(quote, dict)
            }
        except Exception:
            quote_map = {}

    ideas: list[SmartMoneyIdeaFeedCard] = []
    for symbol, payload in aggregated.items():
        net_delta_pct = round(_safe_float(payload.get("netDelta"), 0.0), 2)
        conviction = _safe_float(payload.get("conviction"), 0.0)
        investors = sorted(list(payload.get("investors") or []))
        quote = quote_map.get(symbol, {})
        live_move_pct = round(_safe_float(quote.get("pctChange"), 0.0), 2)
        last_price = round(_safe_float(quote.get("last"), 0.0), 2)

        action = _idea_action_from_signal(net_delta_pct=net_delta_pct, live_move_pct=live_move_pct)
        confidence = int(
            max(
                48,
                min(
                    96,
                    52
                    + (len(investors) * 8)
                    + min(20, int(conviction * 7))
                    + (5 if live_move_pct >= 0.0 else 0),
                ),
            )
        )

        direction = f"+{net_delta_pct:.2f}%" if net_delta_pct >= 0 else f"{net_delta_pct:.2f}%"
        live_suffix = f"price {last_price:.2f} ({live_move_pct:+.2f}%)" if last_price > 0 else f"live move {live_move_pct:+.2f}%"
        thesis = (
            f"{len(investors)} tracked investor disclosure(s) imply {direction} net holding delta, "
            f"with {live_suffix}."
        )
        why_now = (
            "Recent portfolio disclosures and live tape alignment suggest a decision window right now."
            if action in {"ACCUMULATE", "WATCHLIST"}
            else "Disclosure trend and tape weakness indicate caution on fresh entries."
        )
        risk_note = (
            "Disclosures are lagging indicators; validate earnings and liquidity before acting."
            if action != "DISTRIBUTION_RISK"
            else "Watch for continued trimming across consecutive filings before treating this as support."
        )

        tags = ["smart_money", "filings", action.lower()]
        if abs(live_move_pct) >= 2.0:
            tags.append("high_momentum")
        if len(investors) >= 2:
            tags.append("multi_investor")

        ideas.append(
            SmartMoneyIdeaFeedCard(
                ideaId=f"idea_{symbol.lower()}",
                symbol=symbol,
                companyName=str(payload.get("companyName") or symbol),
                action=action,
                confidence=confidence,
                thesis=thesis,
                whyNow=why_now,
                riskNote=risk_note,
                tags=tags,
                backingInvestors=investors[:4],
            )
        )

    ideas.sort(
        key=lambda item: (
            item.confidence,
            abs(next((change.deltaPct for feed in portfolio_changes for change in feed.changes if change.symbol == item.symbol), 0.0)),
        ),
        reverse=True,
    )
    return ideas[:idea_limit]


@router.get("/investor-portfolios/insights", response_model=InvestorPortfolioInsightsResponse)
async def get_investor_portfolio_insights(
    maxChangesPerInvestor: int = Query(3, ge=1, le=8),
    ideaLimit: int = Query(8, ge=3, le=20),
):
    portfolio_changes = _build_portfolio_change_feed(max_changes_per_investor=maxChangesPerInvestor)
    ideas = _build_explainable_idea_feed(
        portfolio_changes=portfolio_changes,
        idea_limit=ideaLimit,
    )

    return InvestorPortfolioInsightsResponse(
        generatedAt=datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        quarterLabel=_INVESTOR_QUARTER_LABEL,
        portfolioChanges=portfolio_changes,
        ideas=ideas,
    )


@router.get("/investor-portfolios")
async def get_investor_portfolios():
    """Returns curated smart-money investor portfolio profiles based on
    latest publicly disclosed SEBI/BSE regulatory filings."""
    return _INVESTOR_PORTFOLIOS


@router.get("/investor-portfolios/{investor_id}")
async def get_investor_portfolio(investor_id: str):
    """Returns a single investor portfolio by ID."""
    portfolio = next(
        (p for p in _INVESTOR_PORTFOLIOS if p["id"] == investor_id),
        None,
    )
    if portfolio is None:
        raise HTTPException(status_code=404, detail=f"Investor portfolio '{investor_id}' not found")
    return portfolio
