from fastapi import APIRouter, Depends, Query, HTTPException, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
import logging
import os
import time
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
    AlertResponse, HealthCheck, TradeHistory, OrderTraceLookupResponse, PortfolioSummary, PortfolioValue,
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
    fetch_quote, fetch_quotes, get_all_symbols, get_default_symbols,
    search_stocks, get_symbols_with_names, get_stock_name, INDIAN_STOCKS
)
from ..ai_engine import analyze_stock, predict_price, ai_assistant, get_market_headlines
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

@router.get("/health", response_model=HealthCheck)
async def health_check():
    """Health check endpoint."""
    return HealthCheck(status="healthy", version="2.0.0")


# ==================== AI STOCK ASSISTANT ====================

class AiQuery(BaseModel):
    query: str

@router.post("/ai/ask")
async def ai_ask_endpoint(body: AiQuery, db: Session = Depends(get_db)):
    """Natural language AI stock assistant.
    Examples: 'Should I buy RELIANCE?', 'Predict TCS price', 'Compare INFY and TCS'"""
    result = ai_assistant(body.query, db=db)
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


# ==================== ADVANCED ORDER ENGINE ====================

@router.post("/orders/advanced", response_model=AdvancedOrderResponse)
async def place_advanced_order_endpoint(
    order: Order,
    db: Session = Depends(get_db),
    user_id: int = Header(1),
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

    response = place_order(db, order, user_id=user_id)

    trigger_status: str | None = None
    order_id: int | None = None
    executed_price: float | None = None

    if "server-side trigger" in response.message:
        trigger = (
            db.query(TriggerOrderModel)
            .filter(TriggerOrderModel.user_id == user_id, TriggerOrderModel.symbol == order.symbol.upper())
            .order_by(TriggerOrderModel.id.desc())
            .first()
        )
        trigger_status = "PENDING" if trigger else "PENDING"
    else:
        order_row = (
            db.query(OrderModel)
            .filter(OrderModel.user_id == user_id, OrderModel.symbol == order.symbol.upper())
            .order_by(OrderModel.id.desc())
            .first()
        )
        if order_row:
            order_id = order_row.id
            executed_price = order_row.price
            trigger_status = order_row.status

    return AdvancedOrderResponse(
        status=response.status,
        orderId=order_id,
        order=order,
        message=response.message or "Order processed",
        executedPrice=executed_price,
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
):
    basket = db.query(BasketOrderModel).filter(BasketOrderModel.id == basket_id, BasketOrderModel.user_id == user_id).first()
    if not basket:
        raise HTTPException(status_code=404, detail=f"Basket '{basket_id}' not found")

    legs = db.query(BasketOrderLegModel).filter(BasketOrderLegModel.basket_id == basket_id).all()
    if not legs:
        raise HTTPException(status_code=400, detail="Basket has no legs")

    results: list[BasketLegExecution] = []
    for leg in legs:
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
        )
        order_row = (
            db.query(OrderModel)
            .filter(OrderModel.user_id == user_id, OrderModel.symbol == leg.symbol)
            .order_by(OrderModel.id.desc())
            .first()
        )
        results.append(
            BasketLegExecution(
                symbol=leg.symbol,
                side=leg.side,
                qty=leg.quantity,
                status=response.status,
                message=response.message or "",
                orderId=order_row.id if order_row else None,
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
