from fastapi import APIRouter, Depends, Query, HTTPException, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from typing import Dict, List, Optional
import asyncio
import logging
import os
import re
import threading
import time
import hashlib
from math import erf, exp, log, sqrt
from urllib import request as urllib_request
from sqlalchemy import text
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
from .dependencies import get_current_user, get_optional_current_user
from ..models.schemas import (
    Quote, Holding, Order, OrderResponse, Alert, AlertCreate,
    AlertResponse, HealthCheck, TradeHistory, HistoryCandle, OrderTraceLookupResponse, PortfolioSummary, PortfolioValue,
    Wallet, WalletTransaction, WalletResponse,     MarketStatus,
    IntradayTip,
    IntradayTipsResponse,
    InvestorTip,
    InvestorTopicInfo,
    InvestorTipsResponse,
    MarketNewsResponse,
    MarketMoversResponse,
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
    ScannerResponse,
    ScannerRow,
    ScoreHistoryResponse,
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
from ..stock_enricher import normalize_hinglish
from ..market_data import (
    fetch_quote, fetch_quote_history, fetch_quotes, get_all_symbols, get_default_symbols,
    search_stocks, get_symbols_with_names, get_stock_name, INDIAN_STOCKS,
    fetch_market_movers, get_stock_catalog,
)
from ..market_news import (
    MARKET_NEWS_TIMEOUT_SECONDS,
    empty_market_news,
    get_market_headlines,
    peek_stale_news,
)
from ..ai_engine import (
    analyze_stock, predict_price, ai_assistant,
    get_stock_detail_fast, get_best_stocks_to_buy,
    get_stop_loss_take_profit, calculate_drawdown_risk, calculate_relative_strength,
    calculate_trade_accuracy, get_sector_rotation_signals, get_earnings_calendar,
    advanced_stock_screener
)
from ..portfolio_scorer import calculate_portfolio_health
from ..market_heatmap import SECTOR_STOCKS, get_market_heatmap, get_sector_detail
from ..market_scanner import SCANNER_MODES, get_market_scanner, get_score_history, get_symbol_xray

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


def _mf_from_db_row(row: MutualFundModel) -> MutualFund:
    return MutualFund(
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


def _warm_mf_live_map() -> dict[str, MutualFund]:
    """Use the in-memory AMFI cache only. Never hits the network."""
    cached = _MF_LIVE_CACHE.get("funds")
    fetched_at = float(_MF_LIVE_CACHE.get("fetched_at", 0.0) or 0.0)
    if (
        isinstance(cached, list)
        and cached
        and (time.time() - fetched_at) < _MF_LIVE_CACHE_TTL_SECONDS
    ):
        return {fund.schemeCode: fund for fund in cached}
    return {}


def _merge_compare_fund(live: MutualFund | None, stored: MutualFund | None) -> MutualFund | None:
    if live is None:
        return stored
    if stored is None:
        return live
    return MutualFund(
        schemeCode=live.schemeCode,
        schemeName=live.schemeName or stored.schemeName,
        category=live.category or stored.category,
        nav=live.nav,
        navDate=live.navDate or stored.navDate,
        returns1Y=live.returns1Y if live.returns1Y is not None else stored.returns1Y,
        returns3Y=live.returns3Y if live.returns3Y is not None else stored.returns3Y,
        returns5Y=live.returns5Y if live.returns5Y is not None else stored.returns5Y,
        fundHouse=live.fundHouse or stored.fundHouse,
        riskLevel=live.riskLevel or stored.riskLevel,
    )


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
    categories = list(dict.fromkeys((fund.category or "OTHER").upper() for fund in funds))
    dates = list(dict.fromkeys(fund.navDate for fund in funds if fund.navDate))
    has_returns = any(
        fund.returns1Y is not None or fund.returns3Y is not None or fund.returns5Y is not None
        for fund in funds
    )
    bits = [f"Compared {len(funds)} schemes"]
    if len(dates) == 1:
        bits[0] += f" (NAV as of {dates[0]})"
    bits[0] += "."
    if len(categories) > 1:
        bits.append(
            "Categories differ (" + " vs ".join(categories) + ") — not a like-for-like race."
        )
    elif categories:
        bits.append(f"All {categories[0]} schemes.")
    if has_returns:
        bits.append("Return badges use available CAGR — past returns are not guaranteed.")
    else:
        bits.append(
            "AMFI daily NAV has no 1Y/3Y/5Y CAGR. Compare category, risk, and latest NAV, "
            "then check the scheme factsheet for returns."
        )
    bits.append("Educational snapshot only — not a fund recommendation.")

    return MutualFundCompareResponse(
        funds=funds,
        bestReturns1YSchemeCode=_best_scheme_for("returns1Y"),
        bestReturns3YSchemeCode=_best_scheme_for("returns3Y"),
        bestReturns5YSchemeCode=_best_scheme_for("returns5Y"),
        lowestRiskSchemeCode=lowest_risk,
        summary=" ".join(bits),
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

    # Educational demo IPOs — keep dates relative to "today" so Open/Upcoming stay useful.
    today = datetime.utcnow().date()
    open_start = (today - timedelta(days=1)).isoformat()
    open_end = (today + timedelta(days=3)).isoformat()
    open_list = (today + timedelta(days=8)).isoformat()
    up_start = (today + timedelta(days=10)).isoformat()
    up_end = (today + timedelta(days=14)).isoformat()
    up_list = (today + timedelta(days=20)).isoformat()
    demo_ipos = {
        "IPO-DEMO-OPEN": dict(
            company_name="Acme Infra Limited (demo)",
            symbol="ACME",
            status="OPEN",
            issue_open_date=open_start,
            issue_close_date=open_end,
            listing_date=open_list,
            price_band_min=345.0,
            price_band_max=362.0,
            lot_size=41,
        ),
        "IPO-DEMO-UPCOMING": dict(
            company_name="Nova Renewables Limited (demo)",
            symbol="NOVA",
            status="UPCOMING",
            issue_open_date=up_start,
            issue_close_date=up_end,
            listing_date=up_list,
            price_band_min=215.0,
            price_band_max=228.0,
            lot_size=65,
        ),
    }
    for ipo_id, fields in demo_ipos.items():
        row = db.query(IPOModel).filter(IPOModel.ipo_id == ipo_id).first()
        if row is None:
            db.add(IPOModel(ipo_id=ipo_id, **fields))
        else:
            for key, value in fields.items():
                setattr(row, key, value)
    # Refresh legacy fixed-date demo rows in place (keeps any practice applications).
    legacy_map = {
        "IPO-2026-001": demo_ipos["IPO-DEMO-OPEN"],
        "IPO-2026-002": demo_ipos["IPO-DEMO-UPCOMING"],
    }
    for legacy_id, fields in legacy_map.items():
        legacy = db.query(IPOModel).filter(IPOModel.ipo_id == legacy_id).first()
        if legacy is not None:
            for key, value in fields.items():
                setattr(legacy, key, value)

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


def _option_contract_from_row(row: dict) -> OptionContract:
    return OptionContract(
        strike=float(row.get("strike") or 0.0),
        callLtp=float(row.get("callLtp") or 0.0),
        putLtp=float(row.get("putLtp") or 0.0),
        callOi=int(row.get("callOi") or 0),
        putOi=int(row.get("putOi") or 0),
        callOiChange=int(row.get("callOiChange") or 0),
        putOiChange=int(row.get("putOiChange") or 0),
        impliedVolatility=float(row.get("impliedVolatility") or 0.0),
        callDelta=float(row.get("callDelta") or 0.0),
        putDelta=float(row.get("putDelta") or 0.0),
        gamma=float(row.get("gamma") or 0.0),
        theta=float(row.get("theta") or 0.0),
        vega=float(row.get("vega") or 0.0),
        callIv=float(row["callIv"]) if row.get("callIv") is not None else None,
        putIv=float(row["putIv"]) if row.get("putIv") is not None else None,
    )


_QUOTE_ALIASES = {
    "NIFTY": "NIFTY50",
    "NIFTYBANK": "BANKNIFTY",
}

# Teaching-board levels used only when a live quote is unavailable.
_EDUCATIONAL_SPOT = {
    "NIFTY": 24500.0,
    "NIFTY50": 24500.0,
    "BANKNIFTY": 55000.0,
    "NIFTYBANK": 55000.0,
    "FINNIFTY": 26500.0,
    "MIDCPNIFTY": 13000.0,
    "NIFTYNXT50": 69000.0,
}


def _resolve_derivatives_spot(symbol: str) -> tuple[float, bool]:
    """Return (spot, illustrative). Never fail a teaching chain on a missing quote."""
    raw = (symbol or "").strip().upper()
    lookup = _QUOTE_ALIASES.get(raw, raw)
    last = 0.0
    try:
        last = float((fetch_quote(lookup) or {}).get("last") or 0.0)
        if last <= 0 and lookup != raw:
            last = float((fetch_quote(raw) or {}).get("last") or 0.0)
    except Exception as exc:
        logger.debug("derivatives_spot_quote_failed symbol=%s reason=%s", raw, exc)
        last = 0.0
    if last > 0:
        return last, False
    return _EDUCATIONAL_SPOT.get(raw, 1000.0), True


def _generate_synthetic_option_chain(symbol: str, expiry: str) -> OptionChainResponse:
    spot, illustrative = _resolve_derivatives_spot(symbol)

    step = 50.0 if spot >= 1000 else 20.0 if spot >= 300 else 10.0
    atm = round(spot / step) * step
    base_seed = sum(ord(ch) for ch in f"{symbol.upper()}:{expiry}")
    contracts: list[OptionContract] = []
    for index in range(-10, 11):
        strike = round(atm + (index * step), 2)
        strike_seed = base_seed + int(strike * 10)
        # Slight put-wing premium so educational IV skew is non-zero.
        call_iv = max(0.12, min(0.55, 0.20 + (max(index, 0) * 0.012) + ((strike_seed % 17) / 1000.0)))
        put_iv = max(0.12, min(0.60, 0.23 + (max(-index, 0) * 0.015) + (((strike_seed + 11) % 19) / 1000.0)))
        iv = round((call_iv + put_iv) / 2.0, 4)
        greeks = _black_scholes_greeks(
            spot=spot,
            strike=strike,
            time_years=21 / 365,
            rate=0.065,
            iv=iv,
        )
        call_oi = max(250, int(15000 - (abs(index) * 780) + (strike_seed % 500)))
        # Mild put-heavy OI near ATM so PCR > 1 in typical educational boards.
        put_oi = max(250, int(16200 - (abs(index) * 720) + ((strike_seed + 37) % 500)))
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
                impliedVolatility=iv,
                callDelta=greeks["callDelta"],
                putDelta=greeks["putDelta"],
                gamma=greeks["gamma"],
                theta=greeks["theta"],
                vega=greeks["vega"],
                callIv=round(call_iv, 4),
                putIv=round(put_iv, 4),
            )
        )

    from ..derivatives_data import compute_chain_metrics

    metrics = compute_chain_metrics(
        [
            {
                "strike": c.strike,
                "callOi": c.callOi,
                "putOi": c.putOi,
                "impliedVolatility": c.impliedVolatility,
                "callIv": c.callIv,
                "putIv": c.putIv,
            }
            for c in contracts
        ],
        spot,
    )
    return OptionChainResponse(
        symbol=symbol.upper(),
        expiry=expiry,
        spot=round(spot, 2),
        generatedAt=datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        contracts=contracts,
        source="synthetic",
        pcr=metrics.get("pcr"),
        ivSkew=metrics.get("ivSkew"),
        atmIv=metrics.get("atmIv"),
        notes=[
            "Teaching chain — live exchange chain unavailable.",
            "PCR / IV skew are computed on this educational board — verify with your broker.",
            *(
                ["Spot is illustrative because a live index print was unavailable."]
                if illustrative
                else []
            ),
        ],
    )


def _generate_option_chain(symbol: str, expiry: str) -> OptionChainResponse:
    try:
        from ..derivatives_data import fetch_nse_option_chain

        live = fetch_nse_option_chain(symbol=symbol, expiry=expiry)
        if live and live.get("contracts"):
            contracts = [_option_contract_from_row(row) for row in live["contracts"]]
            return OptionChainResponse(
                symbol=str(live.get("symbol") or symbol).upper(),
                expiry=str(live.get("expiry") or expiry),
                spot=float(live.get("spot") or 0.0),
                generatedAt=str(live.get("generatedAt") or datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")),
                contracts=contracts,
                source=str(live.get("source") or "nse"),
                pcr=live.get("pcr"),
                ivSkew=live.get("ivSkew"),
                atmIv=live.get("atmIv"),
                notes=list(live.get("notes") or []),
            )
    except Exception as exc:
        logger.warning("option_chain_live_failed symbol=%s reason=%s", symbol, exc)
    return _generate_synthetic_option_chain(symbol=symbol, expiry=expiry)


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


def _generate_synthetic_futures_contracts(symbol: str) -> FuturesContractsResponse:
    spot, illustrative = _resolve_derivatives_spot(symbol)

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
        pct_change = round(idx * 0.08, 2)

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
        source="synthetic",
        notes=[
            "Teaching futures board — live exchange list unavailable.",
            "Contract metrics are indicative — validate against broker RMS before execution.",
            "Margin preview excludes span spikes and intraday leverage changes.",
            *(
                ["Spot is illustrative because a live print was unavailable."]
                if illustrative
                else []
            ),
        ],
    )


def _generate_futures_contracts(symbol: str) -> FuturesContractsResponse:
    normalized_symbol = symbol.strip().upper()
    try:
        from ..derivatives_data import fetch_nse_futures_contracts

        spot, _illustrative = _resolve_derivatives_spot(normalized_symbol)
        lot_size = _lot_size_for_symbol(normalized_symbol, spot)
        live = fetch_nse_futures_contracts(normalized_symbol, spot=spot, lot_size=lot_size)
        if live and live.get("contracts"):
            contracts = [
                FuturesContract(
                    contractSymbol=str(row.get("contractSymbol") or ""),
                    expiry=str(row.get("expiry") or ""),
                    lotSize=int(row.get("lotSize") or lot_size),
                    last=float(row.get("last") or 0.0),
                    pctChange=float(row.get("pctChange") or 0.0),
                    oi=int(row.get("oi") or 0),
                    oiChange=int(row.get("oiChange") or 0),
                    volume=int(row.get("volume") or 0),
                    basis=float(row.get("basis") or 0.0),
                    marginPct=float(row.get("marginPct") or 0.15),
                    marginPerLot=float(row.get("marginPerLot") or 0.0),
                )
                for row in live["contracts"]
                if row.get("contractSymbol") and row.get("expiry")
            ]
            if contracts:
                return FuturesContractsResponse(
                    symbol=normalized_symbol,
                    spot=float(live.get("spot") or spot),
                    generatedAt=str(live.get("generatedAt") or datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")),
                    contracts=contracts,
                    source=str(live.get("source") or "nse"),
                    notes=list(live.get("notes") or []),
                )
    except Exception as exc:
        logger.warning("futures_contracts_live_failed symbol=%s reason=%s", normalized_symbol, exc)
    return _generate_synthetic_futures_contracts(symbol=normalized_symbol)


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
    wanted = (payload.expiry or "").strip()
    selected = next((c for c in contracts.contracts if c.expiry == wanted), None)
    if selected is None and contracts.contracts:
        selected = min(
            contracts.contracts,
            key=lambda c: abs(_expiry_ord(c.expiry) - _expiry_ord(wanted)),
        )
    if selected is None:
        contracts = _generate_synthetic_futures_contracts(payload.symbol)
        selected = contracts.contracts[0]

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


def _expiry_ord(value: str) -> int:
    try:
        return datetime.strptime((value or "").strip()[:10], "%Y-%m-%d").toordinal()
    except Exception:
        return 0


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
        title="Momentum Movers",
        thesis=(
            "High-participation movers with elevated absolute moves — a proxy for "
            "event-style volatility, not an official earnings calendar."
        ),
        proxy=True,
        generatedAt=generated_at,
        candidates=[candidate for _, candidate in ranked[:limit_per_bucket]],
        notes=[
            "Proxy bucket: live move + volume participation (not NSE results calendar).",
            "Use Earnings Calendar screen for actual reporting dates.",
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
        title="Sector Leadership (proxy)",
        thesis="Proxy bucket combining sector leadership, liquidity, trend, and size signals — not FII/DII filings.",
        proxy=True,
        generatedAt=generated_at,
        candidates=[candidate for _, candidate in ranked[:limit_per_bucket]],
        notes=[
            "Proxy only — not official FII/DII or bulk-deal data.",
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

# The market-data helpers below are synchronous and network-bound (yfinance). Calling them
# directly from an async handler blocks the event loop, which stalls every other in-flight
# request — costly under the deployment's --limit-concurrency ceiling. Run them in the
# default thread pool instead.



def _optional_float(value: object) -> Optional[float]:
    try:
        if value is None:
            return None
        number = float(value)
        if number != number:  # NaN
            return None
        return number
    except (TypeError, ValueError):
        return None


def _optional_int(value: object) -> Optional[int]:
    try:
        if value is None:
            return None
        return int(float(value))
    except (TypeError, ValueError):
        return None


def _quote_from_raw(raw: dict, now_ms: Optional[int] = None) -> Quote:
    """Map market_data quote dict → API Quote (keeps snapshot fundamentals)."""
    from ..market_data import _safe_number, fundamentals_from_yahoo_quote

    raw = dict(raw or {})
    last_hint = _safe_number(raw.get("last") or raw.get("regularMarketPrice"), 0.0)
    mapped = fundamentals_from_yahoo_quote(raw, last_price=last_hint)
    for key, incoming in mapped.items():
        if incoming in (None, 0, 0.0):
            continue
        if raw.get(key) in (None, 0, 0.0):
            raw[key] = incoming
    if raw.get("last") in (None, 0, 0.0) and last_hint > 0:
        raw["last"] = last_hint

    stamp = now_ms if now_ms is not None else int(time.time() * 1000)
    ts_raw = raw.get("timestamp")
    try:
        ts = int(ts_raw) if ts_raw is not None else stamp
    except (TypeError, ValueError):
        ts = stamp

    prev_close = _optional_float(
        raw.get("prevClose") if raw.get("prevClose") is not None else raw.get("previousClose")
    )
    pe = _optional_float(raw.get("trailingPE") if raw.get("trailingPE") is not None else raw.get("pe"))
    if pe is not None and pe <= 0:
        pe = None

    market_cap = _optional_int(raw.get("marketCap"))
    if market_cap is not None and market_cap <= 0:
        market_cap = None

    volume = _optional_int(
        raw.get("volume") if raw.get("volume") is not None else raw.get("regularMarketVolume")
    )
    avg_volume = _optional_int(
        raw.get("avgVolume")
        if raw.get("avgVolume") is not None
        else (
            raw.get("averageDailyVolume3Month")
            or raw.get("averageVolume")
            or raw.get("averageDailyVolume10Day")
        )
    )
    if avg_volume is not None and avg_volume <= 0:
        avg_volume = None

    dividend = _optional_float(raw.get("dividendYield"))
    if dividend is not None and dividend <= 0:
        dividend = None

    bid = _optional_float(raw.get("bid"))
    ask = _optional_float(raw.get("ask"))
    if bid is not None and bid <= 0:
        bid = None
    if ask is not None and ask <= 0:
        ask = None

    eps = _optional_float(
        raw.get("eps")
        if raw.get("eps") is not None
        else raw.get("epsTrailingTwelveMonths") or raw.get("trailingEps")
    )
    target = _optional_float(
        raw.get("targetMeanPrice")
        if raw.get("targetMeanPrice") is not None
        else raw.get("targetPrice")
    )

    return Quote(
        symbol=str(raw.get("symbol") or "").upper(),
        last=round(_safe_number(raw.get("last"), 0.0), 2),
        pctChange=round(_safe_number(raw.get("pctChange"), 0.0), 2),
        timestamp=ts,
        open=_optional_float(raw.get("open")),
        prevClose=prev_close,
        previousClose=prev_close,
        high=_optional_float(raw.get("high")),
        low=_optional_float(raw.get("low")),
        volume=volume,
        avgVolume=avg_volume,
        marketCap=market_cap,
        trailingPE=pe,
        pe=pe,
        eps=eps,
        fiftyTwoWeekHigh=_optional_float(raw.get("fiftyTwoWeekHigh")),
        fiftyTwoWeekLow=_optional_float(raw.get("fiftyTwoWeekLow")),
        targetMeanPrice=target,
        bid=bid,
        ask=ask,
        dividendYield=dividend,
        fiftyDayAverage=_optional_float(raw.get("fiftyDayAverage")),
        twoHundredDayAverage=_optional_float(raw.get("twoHundredDayAverage")),
    )


@router.get("/quotes", response_model=list[Quote], response_model_exclude_none=True)
async def get_quotes_endpoint(
    symbols: str = Query(""),
    db: Session = Depends(get_db)
):
    """Get live quotes for specified symbols (comma-separated) or defaults."""
    if symbols:
        sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    else:
        sym_list = get_default_symbols()

    try:
        raw_quotes = await asyncio.wait_for(
            asyncio.to_thread(fetch_quotes, sym_list),
            timeout=8.0,
        )
    except asyncio.TimeoutError:
        logger.warning("quotes_fetch_timeout symbols=%s", len(sym_list))
        try:
            from ..market_data import QUOTE_CACHE_STORAGE_SECONDS

            raw_quotes = await asyncio.to_thread(
                fetch_quotes,
                sym_list,
                float(QUOTE_CACHE_STORAGE_SECONDS),
            )
        except Exception:
            raw_quotes = []
    except Exception as exc:
        logger.error("quotes_fetch_failed reason=%s", exc)
        raise HTTPException(status_code=503, detail="Quote provider temporarily unavailable") from exc

    # Triggers/alerts must not delay the live tape. Fire-and-forget after quotes return.
    try:
        from ..database.db import SessionLocal

        def _run_triggers():
            session = SessionLocal()
            try:
                return evaluate_pending_triggers(db=session, user_id=None, symbols=sym_list)
            finally:
                session.close()

        threading.Thread(target=_run_triggers, name="quote-triggers", daemon=True).start()
    except Exception as exc:
        logger.warning("trigger_evaluation_failed reason=%s", str(exc))

    try:
        from ..alert_push import evaluate_price_alerts
        from ..database.db import SessionLocal as AlertSessionLocal

        quotes_for_alerts = list(raw_quotes or [])

        def _run_alerts():
            session = AlertSessionLocal()
            try:
                return evaluate_price_alerts(db=session, quotes=quotes_for_alerts, symbols=sym_list)
            finally:
                session.close()

        threading.Thread(target=_run_alerts, name="quote-alerts", daemon=True).start()
    except Exception as exc:
        logger.warning("alert_push_evaluation_failed reason=%s", str(exc))

    now_ms = int(time.time() * 1000)
    safe = []
    for q in raw_quotes or []:
        try:
            safe.append(_quote_from_raw(q, now_ms=now_ms))
        except Exception:
            continue
    return safe


@router.get("/quotes/all", response_model=list[Quote], response_model_exclude_none=True)
async def get_all_quotes_endpoint():
    """Get live quotes for ALL supported NSE symbols."""
    raw_quotes = await asyncio.to_thread(fetch_quotes, get_all_symbols())
    now_ms = int(time.time() * 1000)
    return [_quote_from_raw(q, now_ms=now_ms) for q in (raw_quotes or [])]


@router.get("/quotes/{symbol}", response_model=Quote, response_model_exclude_none=True)
async def get_single_quote_endpoint(symbol: str):
    """Get a live quote for a single stock symbol."""
    q = await asyncio.to_thread(fetch_quote, symbol.upper())
    if q["last"] == 0:
        raise HTTPException(status_code=404, detail=f"Quote not found for {symbol}")
    return _quote_from_raw(q)


@router.get("/quotes/{symbol}/history", response_model=list[HistoryCandle])
async def get_quote_history_endpoint(
    symbol: str,
    period: str = Query("1mo"),
    interval: str = Query("1d"),
):
    """Get OHLCV candles for a symbol and timeframe."""
    try:
        candles = await asyncio.to_thread(
            fetch_quote_history, symbol.upper(), period=period, interval=interval
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return [HistoryCandle(**candle) for candle in candles]


# ==================== HOLDINGS ====================

def _holdings_in_thread(user_id: int) -> list[Holding]:
    """Run holdings load on a fresh Session — request Session is not thread-safe."""
    from ..database.db import SessionLocal

    session = SessionLocal()
    try:
        return get_holdings(session, user_id)
    finally:
        session.close()


def _holdings_book_in_thread(user_id: int) -> list[dict]:
    """Symbols + qty + book last from DB. No Yahoo refresh, no invented P&L."""
    from ..database.db import SessionLocal

    session = SessionLocal()
    try:
        rows = session.query(HoldingModel).filter(HoldingModel.user_id == user_id).all()
        book: list[dict] = []
        for row in rows:
            symbol = str(getattr(row, "symbol", "") or "").strip().upper()
            if not symbol:
                continue
            book.append(
                {
                    "symbol": symbol,
                    "qty": int(getattr(row, "quantity", 0) or 0),
                    "book_last": float(getattr(row, "last_price", 0.0) or 0.0),
                }
            )
        return book
    finally:
        session.close()


def _normalize_watchlist(raw) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for item in raw or []:
        symbol = str(item or "").strip().upper().split(".")[0]
        if symbol.startswith("NSE:") or symbol.startswith("BSE:"):
            symbol = symbol.split(":", 1)[1]
        if not symbol or symbol in seen:
            continue
        if not re.match(r"^[A-Z0-9&-]{1,12}$", symbol):
            continue
        seen.add(symbol)
        out.append(symbol)
        if len(out) >= 24:
            break
    return out


@router.get("/holdings", response_model=list[Holding])
async def get_holdings_endpoint(user=Depends(get_current_user)):
    """Get holdings for the authenticated user."""
    try:
        return await asyncio.to_thread(_holdings_in_thread, user.id)
    except Exception as exc:
        logger.error("holdings_endpoint_failed user_id=%s reason=%s", user.id, exc)
        raise HTTPException(status_code=503, detail="Holdings temporarily unavailable") from exc


@router.get("/holdings/{symbol}", response_model=Holding)
async def get_holding_endpoint(symbol: str, user=Depends(get_current_user)):
    """Get a single holding by symbol for the authenticated user."""
    from ..database.db import SessionLocal

    def _one():
        session = SessionLocal()
        try:
            return get_holding(session, symbol.upper(), user.id)
        finally:
            session.close()

    holding = await asyncio.to_thread(_one)
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
    user=Depends(get_current_user),
):
    """Place a buy or sell order at live market price for the authenticated user."""
    return place_order(db, order, user_id=user.id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/buy", response_model=OrderResponse)
async def buy_stock_endpoint(
    order: Order,
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Buy stock at live market price for the authenticated user."""
    order.side = "BUY"
    return place_order(db, order, user_id=user.id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/trade/sell", response_model=OrderResponse)
async def sell_stock_endpoint(
    order: Order,
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Sell stock at live market price for the authenticated user."""
    order.side = "SELL"
    return place_order(db, order, user_id=user.id, idempotency_key=x_idempotency_key, trace_id=x_trace_id)


@router.post("/orders/pre-trade-estimate", response_model=PreTradeEstimateResponse)
async def pre_trade_estimate_endpoint(
    payload: PreTradeEstimateRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    quote = fetch_quote(payload.order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    if live_price <= 0:
        raise HTTPException(status_code=503, detail=f"Could not fetch live price for {payload.order.symbol.upper()}")

    market = is_market_open()
    wallet_balance = payload.walletBalance if payload.walletBalance is not None else get_wallet(db, user.id).balance
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
async def get_trade_history_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Get trade history for the authenticated user."""
    orders = (
        db.query(OrderModel)
        .filter(OrderModel.user_id == user.id)
        .order_by(OrderModel.created_at.desc())
        .all()
    )
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
async def get_trade_history_for_symbol(
    symbol: str,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Get trade history for a specific symbol for the authenticated user."""
    orders = db.query(OrderModel).filter(
        OrderModel.user_id == user.id,
        OrderModel.symbol == symbol.upper(),
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
    user=Depends(get_current_user),
):
    normalized_trace = trace_id.strip()
    if not normalized_trace:
        raise HTTPException(status_code=400, detail="trace_id is required")

    order = (
        db.query(OrderModel)
        .filter(OrderModel.trace_id == normalized_trace, OrderModel.user_id == user.id)
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
async def get_portfolio_endpoint(user=Depends(get_current_user)):
    """Get portfolio summary with live values for the authenticated user."""
    try:
        holdings = await asyncio.to_thread(_holdings_in_thread, user.id)
    except Exception as exc:
        logger.error("portfolio_endpoint_failed user_id=%s reason=%s", user.id, exc)
        raise HTTPException(status_code=503, detail="Portfolio temporarily unavailable") from exc

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
async def get_portfolio_value_endpoint(user=Depends(get_current_user)):
    """Get portfolio current value with live prices for the authenticated user."""
    try:
        holdings = await asyncio.to_thread(_holdings_in_thread, user.id)
    except Exception as exc:
        logger.error("portfolio_value_endpoint_failed user_id=%s reason=%s", user.id, exc)
        raise HTTPException(status_code=503, detail="Portfolio temporarily unavailable") from exc

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
async def export_portfolio_endpoint(
    fmt: str = "csv",
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Export portfolio as CSV. Usage: /portfolio/export?fmt=csv"""
    from fastapi.responses import StreamingResponse
    import io, csv as csvmod

    holdings = get_holdings(db, user.id)

    if fmt == "csv":
        output = io.StringIO()
        writer = csvmod.writer(output)
        writer.writerow(["Symbol", "Qty", "Avg Price (₹)", "Current Price (₹)", "Value (₹)", "P&L (₹)", "P&L %"])
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
    """Cash/credits only. Never waits on Yahoo quotes."""
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
    """Check if NSE/BSE session is open (9:15 IST through latest equity close; CAS/F&O rules from 3 Aug 2026)."""
    return is_market_open()


def _habit_activity_for_user(
    db: Session,
    user,
    *,
    topic: str = "long_term",
    kind: str = "session",
) -> dict:
    """Score paper-trade habits when a Bearer user is present. Never breaks anonymous tips."""
    if user is None:
        return {}
    try:
        from ..habits import activity_from_db, score_investor_habits, score_session_habits

        raw = activity_from_db(db, int(user.id))
        if kind == "investor":
            return score_investor_habits(
                raw.get("orders") or [],
                holdings=raw.get("holdings") or [],
                goals=raw.get("goals") or [],
                alert_count=int(raw.get("alert_count") or 0),
                wallet_balance=raw.get("wallet_balance"),
                journal_entries=raw.get("journal") or [],
                topic=topic,
            )
        return score_session_habits(
            raw.get("orders") or [],
            trigger_count=int(raw.get("trigger_count") or 0),
            journal_entries=raw.get("journal") or [],
        )
    except Exception as exc:
        logger.warning("habit_activity_failed user_id=%s reason=%s", getattr(user, "id", None), exc)
        return {}


@router.get("/market/intraday-tips", response_model=IntradayTipsResponse)
async def market_intraday_tips_endpoint(
    limit: int = Query(4, ge=1, le=8),
    advanceShare: Optional[float] = Query(
        None,
        ge=0.0,
        le=1.0,
        description="Optional advances/(advances+declines) for mood-aware tip",
    ),
    db: Session = Depends(get_db),
    user=Depends(get_optional_current_user),
):
    """Session-phase habit tips, personalized from paper trades when logged in."""
    from ..intraday_tips import build_intraday_tips
    from ..market_session import IST
    from .trading import NSE_HOLIDAYS_2026

    now_ist = datetime.now(IST)
    is_holiday = now_ist.strftime("%Y-%m-%d") in NSE_HOLIDAYS_2026
    scored = _habit_activity_for_user(db, user, kind="session")
    payload = build_intraday_tips(
        limit=limit,
        advance_share=advanceShare,
        is_holiday=is_holiday,
        now=now_ist,
        activity=scored or None,
    )
    return IntradayTipsResponse(
        phase=payload["phase"],
        phaseLabel=payload["phaseLabel"],
        isOpen=payload["isOpen"],
        mood=payload.get("mood"),
        tips=[IntradayTip(**tip) for tip in payload.get("tips") or []],
        disclaimer=payload.get("disclaimer") or "",
        generatedAt=payload.get("generatedAt") or "",
        sampleSize=int(payload.get("sampleSize") or 0),
        hasEnoughData=bool(payload.get("hasEnoughData")),
        paperNote=payload.get("paperNote") or "",
    )


@router.get("/market/investor-tips", response_model=InvestorTipsResponse)
async def market_investor_tips_endpoint(
    topic: str = Query(
        "long_term",
        description="long_term | mutual_funds | ipo | fno | sgb",
    ),
    limit: int = Query(4, ge=1, le=8),
    db: Session = Depends(get_db),
    user=Depends(get_optional_current_user),
):
    """Long-horizon educational tips, plus paper-book habits when logged in."""
    from ..investor_tips import build_investor_tips

    scored = _habit_activity_for_user(db, user, topic=topic, kind="investor")
    payload = build_investor_tips(
        topic=topic,
        limit=limit,
        activity=scored or None,
    )
    return InvestorTipsResponse(
        topic=payload["topic"],
        topicLabel=payload["topicLabel"],
        tips=[InvestorTip(**tip) for tip in payload.get("tips") or []],
        topics=[InvestorTopicInfo(**t) for t in payload.get("topics") or []],
        disclaimer=payload.get("disclaimer") or "",
        generatedAt=payload.get("generatedAt") or "",
        sampleSize=int(payload.get("sampleSize") or 0),
        hasEnoughData=bool(payload.get("hasEnoughData")),
        paperNote=payload.get("paperNote") or "",
    )


@router.get("/market/news", response_model=MarketNewsResponse)
async def market_news_endpoint(
    symbols: str = Query("", description="Optional comma-separated stock symbols"),
    limit: int = Query(10, ge=1, le=20),
):
    """Latest headlines. Hard ~2.5s budget; stale cache if Google/Yahoo is slow."""
    requested_symbols = [value.strip().upper() for value in symbols.split(",") if value.strip()]
    timeout = max(0.8, float(MARKET_NEWS_TIMEOUT_SECONDS) + 0.15)
    try:
        payload = await asyncio.wait_for(
            asyncio.to_thread(get_market_headlines, requested_symbols or None, limit),
            timeout=timeout,
        )
        return payload
    except asyncio.TimeoutError:
        logger.warning(
            "market_news_timeout symbols=%s limit=%s",
            len(requested_symbols) or "default",
            limit,
        )
        stale = peek_stale_news(requested_symbols or None, limit)
        return stale or empty_market_news(requested_symbols or None, limit)


@router.get("/market/movers", response_model=MarketMoversResponse)
async def market_movers_endpoint(
    limit: int = Query(10, ge=1, le=25, description="Top N gainers / losers / most-active"),
):
    """Market-wide day gainers, losers, and most-active from the curated NSE universe."""
    payload = await asyncio.to_thread(fetch_market_movers, limit)
    return MarketMoversResponse(**payload)


# ==================== ALERTS ====================

def _alert_created_at_ms(created_at) -> int:
    if created_at is None:
        return int(time.time() * 1000)
    try:
        return int(created_at.timestamp() * 1000)
    except Exception:
        return int(time.time() * 1000)


def _alert_to_schema(a) -> Alert:
    return Alert(
        id=a.id,
        symbol=a.symbol,
        thresholdPrice=a.threshold_price,
        alertType=a.alert_type,
        isActive=a.is_active,
        createdAt=_alert_created_at_ms(a.created_at),
    )


@router.get("/alerts", response_model=list[Alert], response_model_exclude_none=True)
async def get_alerts_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Get alerts for the authenticated user."""
    alerts = db.query(AlertModel).filter(AlertModel.user_id == user.id).all()
    return [_alert_to_schema(a) for a in alerts]


@router.get("/alerts/active", response_model=list[Alert], response_model_exclude_none=True)
async def get_active_alerts_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Get active alerts for the authenticated user."""
    alerts = (
        db.query(AlertModel)
        .filter(AlertModel.user_id == user.id, AlertModel.is_active == True)
        .all()
    )
    return [_alert_to_schema(a) for a in alerts]


@router.post("/alerts", response_model=Alert, response_model_exclude_none=True)
async def create_alert_endpoint(
    alert: AlertCreate,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Create a new price alert for the authenticated user."""
    alert_db = AlertModel(
        user_id=user.id,
        symbol=alert.symbol.upper(),
        threshold_price=alert.thresholdPrice,
        alert_type=alert.alertType,
        is_active=True
    )
    db.add(alert_db)
    db.commit()
    db.refresh(alert_db)
    return _alert_to_schema(alert_db)


@router.put("/alerts/{alert_id}", response_model=Alert, response_model_exclude_none=True)
async def update_alert_endpoint(
    alert_id: int,
    alert: AlertCreate,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Update an existing alert owned by the authenticated user."""
    alert_db = (
        db.query(AlertModel)
        .filter(AlertModel.id == alert_id, AlertModel.user_id == user.id)
        .first()
    )
    if not alert_db:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    alert_db.symbol = alert.symbol.upper()
    alert_db.threshold_price = alert.thresholdPrice
    alert_db.alert_type = alert.alertType
    db.commit()
    db.refresh(alert_db)
    return _alert_to_schema(alert_db)


@router.delete("/alert/{alert_id}", response_model=AlertResponse)
async def delete_alert_endpoint(
    alert_id: int,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Delete/deactivate an alert owned by the authenticated user."""
    alert_db = (
        db.query(AlertModel)
        .filter(AlertModel.id == alert_id, AlertModel.user_id == user.id)
        .first()
    )
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
    """Search Indian stocks by symbol or company name (curated catalog + NSE equity master)."""
    results = search_stocks(q, limit=limit)
    return results


@router.get("/symbols")
async def get_symbols_endpoint():
    """Get searchable stock symbols with company names (curated + NSE equity master)."""
    return get_symbols_with_names()


@router.get("/symbols/count")
async def get_symbols_count():
    """Get count of available symbols (NSE + BSE current listings)."""
    catalog = get_stock_catalog()
    coverage = {}
    try:
        from ..stock_enricher import get_listing_coverage

        coverage = get_listing_coverage()
    except Exception:
        coverage = {}
    return {
        "count": len(catalog),
        "curatedCount": len(INDIAN_STOCKS),
        "exchange": "NSE/BSE",
        "source": "curated+nse_equity_master+bse_active_scrips",
        "nseCount": coverage.get("nseCount"),
        "bseCodeCount": coverage.get("bseCodeCount"),
        "bseScripIdCount": coverage.get("bseScripIdCount"),
        "dualListedApprox": coverage.get("dualListedApprox"),
        "bseOnlyApprox": coverage.get("bseOnlyApprox"),
    }

# ==================== HEALTH ====================

_last_background_warm_at = 0.0
_BACKGROUND_WARM_MIN_INTERVAL = 45.0


def _kick_background_warmup(force: bool = False) -> bool:
    """Non-blocking quote + listing warm. Never loads the 10k LLM catalog here."""
    global _last_background_warm_at
    now = time.time()
    if not force and (now - _last_background_warm_at) < _BACKGROUND_WARM_MIN_INTERVAL:
        return False
    _last_background_warm_at = now

    def _warm():
        try:
            from ..market_data import fetch_quotes, get_default_symbols
            fetch_quotes(get_default_symbols())
        except Exception as exc:
            logger.warning("warmup.quotes_failed reason=%s", exc)
        try:
            from ..market_heatmap import kick_heatmap_refresh
            kick_heatmap_refresh()
        except Exception as exc:
            logger.warning("warmup.heatmap_failed reason=%s", exc)
        try:
            from ..stock_enricher import _kick_listing_warmup
            _kick_listing_warmup()
        except Exception as exc:
            logger.warning("warmup.listings_failed reason=%s", exc)
        try:
            from ..alert_push import evaluate_active_alert_symbols
            evaluate_active_alert_symbols()
        except Exception as exc:
            logger.warning("warmup.alerts_failed reason=%s", exc)
        try:
            from ..market_scanner import get_market_scanner
            get_market_scanner("long_term", limit=30, force_refresh=False)
        except Exception as exc:
            logger.warning("warmup.scanner_failed reason=%s", exc)

    threading.Thread(target=_warm, name="bysel-warmup", daemon=True).start()
    return True


@router.get("/health", response_model=HealthCheck)
async def health_check():
    """Fast liveness for Render. Kicks a throttled background warm so the
    next user request is less likely to pay a full Yahoo/ISM cold start."""
    _kick_background_warmup(force=False)
    return HealthCheck(status="healthy", version="2.0.0")


@router.get("/warmup")
async def warmup_endpoint(db: Session = Depends(get_db)):
    """
    Fast wake helper for app resume / external keepalive.
    Pings DB and kicks background quote warm — does not wait on Yahoo.
    """
    db_ok = False
    try:
        db.execute(text("SELECT 1"))
        db_ok = True
    except Exception as exc:
        logger.warning("warmup.db_ping_failed reason=%s", exc)

    # Throttled only. force=True used to start Yahoo+scanner on every app resume
    # and starve the single Cloud Run instance so quotes timed out ("Waking server").
    from ..market_session import is_within_equity_session

    warmed = False
    if is_within_equity_session():
        warmed = _kick_background_warmup(force=False)
    return {
        "status": "warming" if warmed else "ready",
        "db": db_ok,
        "version": "2.0.0",
    }


# ==================== AI STOCK ASSISTANT ====================

class AiQuery(BaseModel):
    query: str
    conversation_history: Optional[List[Dict]] = None  # last N turns: [{"role":"user"|"assistant","content":str}]
    # auto/fast: Indian Stock LLM only (then our rule engine)
    # groq|gemini|indian-stock-llm|rule-engine: explicit tier
    tier: Optional[str] = "auto"
    screen_context: Optional[Dict] = None  # {symbol, scanner_mode, source} from the open screen
    watchlist: Optional[List[str]] = None  # client symbols only — never treated as live P&L


class AiFeedbackBody(BaseModel):
    query: str
    answer: str
    helpful: bool = True
    intent: Optional[str] = None


@router.post("/ai/feedback")
async def ai_feedback_endpoint(
    body: AiFeedbackBody,
    user=Depends(get_optional_current_user),
):
    """Record thumbs-up/down on an AI answer into the Indian Stock LLM learning loop."""
    from ..llm_integration import record_chat_feedback

    ok = record_chat_feedback(
        query=body.query,
        answer=body.answer,
        helpful=body.helpful,
        intent=body.intent,
    )
    return {
        "status": "ok" if ok else "skipped",
        "authenticated": user is not None,
        "message": "Feedback recorded" if ok else "Learning loop unavailable",
    }


@router.post("/internal/ism/cluster-misses")
def cluster_misses_endpoint(
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    """Off-request nightly cluster. Human review file only — no learned_knowledge write."""
    from .auth import _require_admin_access
    from ..miss_cluster import cluster_and_write

    _require_admin_access(x_admin_token)
    return cluster_and_write()


@router.post("/ai/ask")
async def ai_ask_endpoint(
    body: AiQuery,
    db: Session = Depends(get_db),
    user=Depends(get_optional_current_user),
):
    """Natural language AI stock assistant with optional tier selection.

    Auto / fast (app default): Indian Stock LLM only, then our rule engine.
    Groq / Gemini run only when the caller sets tier explicitly.

    When a Bearer token is present, portfolio context is scoped to that user.
    Anonymous callers (website demo) still work without personalisation.

    Parameters:
    - query: User's stock market question
    - conversation_history: (Optional) Previous turns for context
    - tier: (Optional) "auto" (default) | "groq" | "gemini" | "indian-stock-llm" | "rule-engine"

    Response fields:
    - answer: Analysis or recommendation
    - source: Which LLM answered (groq | gemini | indian-stock-llm | rule-engine)
    - tier_requested: What tier was requested (useful for debugging)
    - symbol: Detected stock symbol (if applicable)
    - current_price: Live price (if applicable)
    """
    from ..response_validator import ResponseValidator
    from ..stock_enricher import extract_symbol_from_query, enrich, format_news_for_prompt

    auth_user_id = int(user.id) if user is not None else None

    def _validated(result: dict, source: str, tier_requested: str = "auto") -> dict:
        from ..groq_llm import _strip_internal_metadata

        # Keep only user-facing fields, remove internal metadata
        answer = result.get("answer", "")

        # Only strip metadata from fallback LLM responses, not Groq
        if source != "groq" and answer:
            answer = _strip_internal_metadata(answer)
        elif answer:
            from ..groq_llm import redact_vendor_names_for_display

            answer = redact_vendor_names_for_display(answer)
        if answer:
            try:
                from indian_stock_llm.query_language import localize_assistant_answer

                answer = localize_assistant_answer(user_text, answer)
            except Exception:
                pass
            try:
                from ..telugu_reply import polish_telugu_answer

                answer = polish_telugu_answer(user_text, answer)
            except Exception:
                pass
            try:
                from ..telugu_reply import localize_suggestions

                tips = localize_suggestions(user_text, result.get("suggestions") or [])
                if tips:
                    result = dict(result)
                    result["suggestions"] = tips
            except Exception:
                pass

        user_response = {
            "answer": answer,
            "source": source,
            "tier_requested": tier_requested,
        }
        if result.get("confidence") is not None:
            try:
                user_response["confidence"] = float(result.get("confidence"))
            except Exception:
                pass
        if result.get("citations"):
            user_response["citations"] = result.get("citations")

        # Optionally include other user-relevant fields if present
        if "symbol" in result and result.get("symbol"):
            user_response["symbol"] = result["symbol"]
        if "current_price" in result and result.get("current_price") not in (None, "", 0, 0.0):
            user_response["current_price"] = result["current_price"]
            # Also expose under data.* so older Android clients can read a reference price.
            data = dict(user_response.get("data") or {})
            data["currentPrice"] = result["current_price"]
            data["price"] = result["current_price"]
            user_response["data"] = data
        if result.get("signal"):
            user_response["signal"] = result.get("signal")
        feedback_intent = result.get("intent") or (intent_result or {}).get("intent")
        if feedback_intent:
            user_response["intent"] = str(feedback_intent)

        # Keep follow-up chips linked to the answered stock / query.
        # Never invent stock chips for greetings / education / clarifiers.
        # Never invent single-stock chips for sector screens.
        suggestions = result.get("suggestions") or []
        intent_name = (intent_result or {}).get("intent", "")
        if (
            source not in {"small-talk", "clarifier", "education"}
            and not suggestions
            and intent_name != "SECTOR_SCREEN"
            and result.get("type") != "screening"
        ):
            try:
                from ..ai_engine import _build_stock_suggestions
                symbol = str(result.get("symbol") or "").strip().upper()
                if not symbol:
                    symbol = str(extract_symbol_from_query(normalized_query) or "").strip().upper()
                if symbol:
                    exclude = {
                        "PREDICT": "prediction",
                        "BUY_SELL": "buy_sell",
                        "TECHNICAL": "technical",
                        "FUNDAMENTAL": "fundamentals",
                        "NEWS": "news",
                        "SENTIMENT": "sentiment",
                        "QUOTE": "quote",
                    }.get(intent_name, "")
                    suggestions = _build_stock_suggestions(
                        symbol,
                        exclude=exclude,
                        query=normalized_query,
                        intent=intent_name,
                    )
            except Exception:
                suggestions = []
        if intent_name == "SECTOR_SCREEN" or result.get("type") == "screening":
            user_response.pop("symbol", None)
            user_response.pop("signal", None)
        if suggestions:
            user_response["suggestions"] = list(suggestions)[:8]

        return user_response

    # Parse tier preference
    requested_tier = (body.tier or "auto").lower().strip()
    valid_tiers = {"auto", "fast", "groq", "gemini", "indian-stock-llm", "rule-engine"}
    if requested_tier not in valid_tiers:
        requested_tier = "auto"
    from ..groq_llm import (
        classify_intent,
        expand_acronyms_in_query,
        get_small_talk_response,
        infer_response_style,
    )
    from ..ai_engine import _extract_user_query

    # App may wrap as "user_query:… | context:…". Intent / greetings must see only
    # the user's words — otherwise "hi" never short-circuits and becomes a stock.
    user_text = _extract_user_query(body.query)
    expanded_query = expand_acronyms_in_query(user_text)
    normalized_query = normalize_hinglish(expanded_query)
    query_contract = None
    try:
        from indian_stock_llm.query_contract import resolve_query_contract

        query_contract = resolve_query_contract(
            normalized_query,
            conversation_history=body.conversation_history,
            screen_context=body.screen_context,
        )
        if query_contract.resolved_query:
            normalized_query = query_contract.resolved_query
    except Exception:
        query_contract = None
    intent_result = classify_intent(normalized_query, conversation_history=body.conversation_history)
    if query_contract and int(query_contract.confidence or 0) >= 70:
        intent_result["intent"] = query_contract.groq_intent
        intent_result["confidence"] = max(
            int(intent_result.get("confidence") or 0),
            int(query_contract.confidence),
        )
        intent_result["profile"] = query_contract.profile
        intent_result["format_instructions"] = query_contract.format_instructions
        intent_result["resolved_query"] = query_contract.resolved_query
    response_style = infer_response_style(normalized_query, body.conversation_history)

    small_talk_reply = get_small_talk_response(user_text, response_style=response_style)
    if small_talk_reply:
        return _validated(
            {"answer": small_talk_reply},
            "small-talk",
            requested_tier,
        )

    # Home / F&O Learn chips: return the habit lesson before any symbol clarifier.
    # "Teach the NSE opening range…" is literacy, not a stock ask.
    from ..habit_lessons import get_habit_lesson

    habit_answer = get_habit_lesson(normalized_query)
    if habit_answer:
        return _validated(
            {
                "answer": habit_answer,
                "intent": "market_literacy",
                "citations": ["bysel_habit_lessons"],
            },
            "education",
            requested_tier,
        )

    # Extra belt-and-suspenders: never let ultra-short greetings reach stock search.
    if re.fullmatch(r"\s*(hi|hii|hiii|hello|hey|yo|namaste|thanks|thank you|bye)\s*[!.?]*\s*", normalized_query, flags=re.I):
        return _validated(
            {"answer": "Hi! I am BYSEL AI. Ask me about stock prices, buy/sell signals, comparisons, or valuation."},
            "small-talk",
            requested_tier,
        )

    stock_intents = {
        "PREDICT", "COMPARE", "BUY_SELL", "TECHNICAL", "FUNDAMENTAL",
        "SECTOR_SCREEN", "PORTFOLIO", "CALCULATION", "DERIVATIVES",
        "NEWS", "SENTIMENT", "QUOTE",
    }
    detected_intent = intent_result.get("intent", "GENERAL")
    intent_confidence = int(intent_result.get("confidence", 0) or 0)
    explicit_symbol = extract_symbol_from_query(normalized_query)
    educational_like = (
        detected_intent in {"EDUCATIONAL", "CALCULATION", "COMPARE_CONCEPTS"}
        or bool(query_contract and query_contract.profile in {"literacy", "session", "compare_concepts"})
        or bool(re.search(
            r"\b(what is|what are|explain|define|definition|meaning of|formula|equation|"
            r"teach|paper habit|not a stock pick|educational (paper|investor|session))\b",
            normalized_query,
            flags=re.IGNORECASE,
        ))
    )
    if query_contract and query_contract.clarifier and not educational_like:
        return _validated({"answer": query_contract.clarifier}, "clarifier", requested_tier)
    if (
        detected_intent in stock_intents
        and intent_confidence < 60
        and not explicit_symbol
        and not educational_like
        and not (query_contract and query_contract.slots.symbol)
    ):
        if response_style == "concise":
            clarifier = (
                "I need one quick clarification: please share the stock symbol and whether you want "
                "buy/sell, technicals, fundamentals, comparison, prediction, or calculation."
            )
        else:
            clarifier = (
                "I am not fully confident about your exact stock request yet. "
                "Please clarify these so I can give precise analysis:\n"
                "1. Stock symbol or company name\n"
                "2. Analysis type: buy/sell, technicals, fundamentals, comparison, prediction, or calculation\n"
                "3. Time horizon (intraday, swing, 1-month, long-term)"
            )
        return _validated({"answer": clarifier}, "clarifier", requested_tier)

    # Instant glossary / equation answers for common market terms (no LLM needed).
    # Skip only for stock-specific live asks (S/R, sentiment, buy/sell, etc.).
    # Do not skip on false symbol hits like CAS/TIMINGS from literacy questions.
    from ..market_education import get_education_answer
    education_answer = get_education_answer(normalized_query)
    stock_live_ask = bool(explicit_symbol) and bool(
        re.search(
            r"\b(support|resistance|s/?r|trading levels?|pivots?|sentiment|"
            r"should i|buy|sell|pe ratio|p/?e\b|pb ratio|p/?b\b|technical|"
            r"technical analysis|chart analysis|price action|"
            r"fundamental|target|stop ?loss|macd|rsi|ema|sma|dma|"
            r"price of|quote|live price|cmp|expir(y|ies)|why (did|is|has))\b",
            normalized_query,
            flags=re.IGNORECASE,
        )
    )
    live_ism_profile = bool(
        query_contract
        and query_contract.profile
        and query_contract.profile not in {"literacy", "compare_concepts", "small_talk"}
    )
    if education_answer and not stock_live_ask and not live_ism_profile:
        return _validated({"answer": education_answer}, "education", requested_tier)

    # For sector/compare (and always when user named tickers), run rule engine on the
    # bare user text so holdings/selected-quote context cannot inject extra stocks
    # (e.g. HCLTECH/ICICIBANK leaking into "Compare TMPV with MARUTI").
    rule_query = (
        normalized_query
        if detected_intent in {"SECTOR_SCREEN", "COMPARE"} or bool(explicit_symbol)
        else body.query
    )

    # Heavy Yahoo rule-engine first ONLY when required. For normal chat (auto/fast/groq),
    # skip it and let Groq answer from lightweight enrich context — biggest latency win.
    needs_rule_first = (
        requested_tier == "rule-engine"
        or detected_intent in {"SECTOR_SCREEN", "COMPARE", "DERIVATIVES"}
    )
    rule_result: dict = {}
    if needs_rule_first:
        rule_result = await asyncio.to_thread(ai_assistant, rule_query, None, auth_user_id)
    else:
        light_symbol = (
            explicit_symbol
            or extract_symbol_from_query(normalized_query)
            or extract_symbol_from_query(body.query)
        )
        rule_result = {"symbol": light_symbol, "answer": "", "data": {}}
        # Price comes from enrich() on the LLM path — a second Yahoo fetch_quote
        # (ticker.info) used to add 5–15s before the model even started.

    enriched_ctx_cache: Optional[dict] = None

    async def _build_enriched_context() -> dict:
        nonlocal enriched_ctx_cache
        if enriched_ctx_cache is not None:
            return enriched_ctx_cache

        # Extract symbols from USER TEXT ONLY — never from the Android
        # "user_query:… | context:holdings=HCLTECH:…,ICICIBANK:…" wrapper.
        from ..stock_enricher import extract_all_symbols_from_query, extract_entities_from_query, normalize_hinglish, extract_time_window_from_query, order_symbols_in_query
        all_symbols = extract_all_symbols_from_query(normalized_query)
        # Preserve left-to-right order as written, but never let prefix tokens
        # like TECH beat TECHM in "tech mahindra".
        if all_symbols:
            all_symbols = order_symbols_in_query(all_symbols, normalized_query)

        # Resolve primary: prefer tickers named in the user question (compare/buy),
        # then rule-engine, then context wrapper as last resort.
        query_symbol = all_symbols[0] if all_symbols else extract_symbol_from_query(normalized_query)
        if detected_intent == "COMPARE" and all_symbols:
            symbol = all_symbols[0]
        else:
            symbol = (
                query_symbol
                or rule_result.get("symbol")
                or (rule_result.get("detected_stock") or {}).get("symbol")
                or extract_symbol_from_query(body.query)
            )

        entities = extract_entities_from_query(normalized_query)
        time_window = extract_time_window_from_query(normalized_query)

        # data dict from rule-engine (contains technical/fundamental/trading_levels/sentiment)
        data = rule_result.get("data") or {}

        # Detect user sentiment for tone/profile-aware responses
        from ..groq_llm import detect_sentiment_from_query
        user_sentiment = detect_sentiment_from_query(body.query)

        # Symbols only — DB book, no Yahoo refresh, no invented MTM/P&L.
        watchlist_symbols = _normalize_watchlist(body.watchlist)
        portfolio_context = {
            "total_holdings": 0,
            "symbols": [],
            "concentrations": {},
            "watchlist": watchlist_symbols,
        }
        light_profile = bool(
            query_contract and query_contract.profile in {
                "small_talk",
                "session",
                "literacy",
                "compare_concepts",
            }
        )
        if auth_user_id is not None and not light_profile:
            try:
                book = await asyncio.to_thread(_holdings_book_in_thread, auth_user_id)
                if book:
                    portfolio_context = {
                        "total_holdings": len(book),
                        "symbols": [row["symbol"] for row in book],
                        "concentrations": {row["symbol"]: row["qty"] for row in book},
                        "watchlist": watchlist_symbols,
                    }
            except Exception as e:
                logger.debug("Could not extract portfolio context: %s", e)

        ctx: dict = {
            "symbol": symbol,
            "all_symbols": all_symbols,  # list of ALL detected symbols
            "entities": entities,  # extracted price targets, time horizons, etc.
            "current_price": rule_result.get("current_price"),
            "user_sentiment": user_sentiment,  # urgency, risk_appetite, emotion, user_profile
            "portfolio_context": portfolio_context,  # user's current holdings
            "technical": data.get("technical", {}),
            "fundamental": data.get("fundamental", {}),
            "trading_levels": data.get("trading_levels", {}),
            "sentiment": data.get("sentiment", {}),
        }

        # Skip Yahoo enrich on session / literacy / corp-action / greeting asks.
        skip_enrich = bool(
            query_contract
            and query_contract.profile in {
                "session",
                "small_talk",
                "literacy",
                "corporate_actions",
                "compare_concepts",
            }
        )
        if symbol and not skip_enrich:
            try:
                live = await enrich(
                    symbol,
                    deadline_seconds=(
                        6.0
                        if requested_tier in ("auto", "fast", "indian-stock-llm", "groq")
                        else None
                    ),
                )
                if live:
                    # Price
                    if not ctx["current_price"]:
                        ctx["current_price"] = live.get("current_price")
                    if live.get("company_name"):
                        ctx["company_name"] = live.get("company_name")
                    if live.get("sector"):
                        ctx["sector"] = live.get("sector")

                    # Fundamentals: live wins, rule-engine fills gaps
                    live_fund = live.get("fundamental", {})
                    rule_fund = {k: v for k, v in ctx["fundamental"].items() if v}
                    ctx["fundamental"] = {**live_fund, **rule_fund}

                    # Technicals: live wins (has BB, full MAs), rule-engine fills gaps
                    live_tech = live.get("technical", {})
                    rule_tech = {k: v for k, v in ctx["technical"].items() if v}
                    ctx["technical"] = {**live_tech, **rule_tech}

                    # Trading levels: live always wins (rule-engine rarely has these)
                    live_tl = live.get("trading_levels", {})
                    rule_tl = {k: v for k, v in ctx["trading_levels"].items() if v}
                    ctx["trading_levels"] = {**live_tl, **rule_tl}

                    # Sentiment: live always wins (has news-based sentiment + sector trend)
                    live_sent = live.get("sentiment", {})
                    rule_sent = {k: v for k, v in ctx["sentiment"].items() if v}
                    ctx["sentiment"] = {**live_sent, **rule_sent}

                    headlines = live.get("news_headlines", []) or []
                    if headlines:
                        ctx["news_headlines"] = headlines
                        ctx["news_summary"] = format_news_for_prompt(headlines)
                        # Keep recent_events populated for composer + sentiment pack.
                        sent = dict(ctx.get("sentiment") or {})
                        if not sent.get("recent_events"):
                            sent["recent_events"] = list(headlines[:5])
                        ctx["sentiment"] = sent

                    # Pre-computed signal conclusions from enricher
                    if live.get("pre_signals"):
                        ctx["pre_signals"] = live["pre_signals"]

                # Add historical data if temporal query detected
                if time_window and symbol:
                    try:
                        from ..market_data import fetch_quote_history
                        history = await asyncio.to_thread(
                            fetch_quote_history, symbol, time_window['period']
                        )
                        if history and len(history) > 0:
                            start_price = history[0].get('close', 0)
                            end_price = history[-1].get('close', 0)
                            if start_price > 0:
                                change_pct = ((end_price - start_price) / start_price) * 100
                                ctx["historical_data"] = {
                                    'period': time_window['period'],
                                    'lookback_days': time_window['lookback_days'],
                                    'data_points': len(history),
                                    'start_price': round(start_price, 2),
                                    'end_price': round(end_price, 2),
                                    'change_percent': round(change_pct, 2),
                                    'high': round(max(h.get('high', 0) for h in history), 2),
                                    'low': round(min(h.get('low', 0) for h in history), 2),
                                }
                    except Exception as e:
                        logger.debug(f"Could not fetch historical data: {e}")

                # Link news to price moves (catalyst detection)
                if symbol and "sentiment" in data:
                    try:
                        from ..stock_enricher import link_news_to_price_moves
                        pct_change = rule_result.get("pct_change", 0)
                        headlines = live.get("news_headlines", []) if 'live' in locals() and live else []

                        if headlines and pct_change:
                            catalyst = link_news_to_price_moves(symbol, headlines, pct_change)
                            if catalyst:
                                ctx["catalyst_info"] = catalyst
                    except Exception as e:
                        logger.debug(f"Could not link catalyst: {e}")

            except Exception as exc:
                logger.warning("Enrichment failed for %s: %s", symbol, exc)

        enriched_ctx_cache = ctx
        return ctx

    logger.info(f"DEBUG: Tier requested = {requested_tier}")

    # Sector themes (defence / pharma / …) are more reliable from the curated
    # rule screener than free-form LLM answers that invent unrelated tickers.
    if detected_intent == "SECTOR_SCREEN" and requested_tier in ("auto", "fast", "rule-engine"):
        rule_answer = (rule_result.get("answer") or "").strip()
        rule_stocks = rule_result.get("stocks") or []
        answer_l = rule_answer.lower()
        looks_like_sector = bool(rule_stocks) or any(
            marker in answer_l
            for marker in (
                "top defence", "top defense", "top pharma", "top bank", "top it",
                "top auto", "top fmcg", "top energy", "top metal", "top infra",
                "top psu", "top realty", "top railway", "top cement", "top ",
            )
        )
        if rule_answer and looks_like_sector and "popular stocks" not in answer_l:
            # Never let a selected-quote ticker (e.g. INFY) become the sector CTA.
            rule_result = {**rule_result, "symbol": None, "signal": None}
            if not rule_result.get("suggestions") and rule_result.get("stocks"):
                tips = []
                for row in (rule_result.get("stocks") or [])[:3]:
                    sym = str((row or {}).get("symbol") or "").upper()
                    if sym:
                        tips.append(f"Analyze {sym}")
                        tips.append(f"Should I buy {sym}?")
                rule_result["suggestions"] = tips[:6]
            return _validated(rule_result, "rule-engine", requested_tier)

    # Tier 1 (default): Indian Stock LLM — custom grounded model (no paid API).
    # Groq/Gemini remain optional fallbacks when ISM confidence is too low.
    if requested_tier in ("auto", "fast", "indian-stock-llm"):
        try:
            from ..llm_integration import llm_available, ask_llm
            if llm_available():
                llm_context = {}
                # Pronoun / multi-turn resolution for ISM (previously Groq-only).
                ism_query = (
                    (query_contract.resolved_query if query_contract else "")
                    or normalized_query
                )
                try:
                    from ..groq_llm import resolve_pronouns, detect_sentiment_from_query

                    if body.conversation_history:
                        if query_contract is None:
                            resolved = resolve_pronouns(
                                normalized_query, body.conversation_history
                            )
                            if resolved and resolved.strip():
                                ism_query = resolved.strip()
                        llm_context["conversation_history"] = list(
                            body.conversation_history or []
                        )[-6:]
                        llm_context["user_sentiment"] = detect_sentiment_from_query(
                            ism_query
                        )
                    if query_contract:
                        llm_context["query_profile"] = query_contract.profile
                        llm_context["intent"] = query_contract.ism_intent
                        if query_contract.slots.symbol:
                            llm_context["symbol"] = query_contract.slots.symbol
                except Exception:
                    ism_query = normalized_query
                try:
                    # Same enrich depth Groq gets — required for ISM accuracy on
                    # buy/sell, valuation, and compare asks.
                    enriched_ctx = await _build_enriched_context()
                    for key in (
                        "symbol",
                        "current_price",
                        "technical",
                        "fundamental",
                        "trading_levels",
                        "sentiment",
                        "company_name",
                        "sector",
                        "all_symbols",
                        "news_summary",
                        "news_headlines",
                        "pre_signals",
                        "catalyst_info",
                        "portfolio_context",
                        "historical_data",
                    ):
                        if enriched_ctx.get(key) not in (None, {}, [], ""):
                            llm_context[key] = enriched_ctx.get(key)
                    groq_to_ism = {
                        "NEWS": "events_news",
                        "SENTIMENT": "events_news",
                        "QUOTE": "price_action",
                        "TECHNICAL": "stock_analysis",
                        "BUY_SELL": "price_action",
                        "PREDICT": "prediction",
                        "FUNDAMENTAL": "fundamentals",
                        "COMPARE": "compare",
                        "CALCULATION": "market_calculations",
                    }
                    mapped_intent = groq_to_ism.get(str(detected_intent or "").upper())
                    if mapped_intent:
                        llm_context["intent"] = mapped_intent
                        llm_context["groq_intent"] = detected_intent
                except Exception:
                    pass
                llm_context["original_query"] = user_text
                llm_result = await asyncio.to_thread(ask_llm, ism_query, llm_context or None)
                ism_answer = str((llm_result or {}).get("answer") or "").strip()
                # App chat is ISM-first: keep a real ISM answer even if confidence is
                # modest. Groq/Gemini only run when ISM has nothing usable.
                if llm_result and ism_answer:
                    logger.info("DEBUG: Using Indian Stock LLM (confidence=%.2f)", llm_result.get("confidence", 0))
                    merged = {
                        **rule_result,
                        "answer": llm_result["answer"],
                    }
                    # Prefer ISM confidence / symbol / live price — rule_result often
                    # has no confidence (or 0), which previously made the app show 0%.
                    if llm_result.get("confidence") is not None:
                        merged["confidence"] = llm_result.get("confidence")
                    if llm_result.get("citations"):
                        merged["citations"] = llm_result.get("citations")
                    if detected_intent == "SECTOR_SCREEN":
                        # Sector answers must not inherit selected-quote context (Buy INFY leak).
                        merged["symbol"] = None
                        merged["signal"] = None
                        if rule_result.get("suggestions"):
                            merged["suggestions"] = list(rule_result.get("suggestions") or [])[:6]
                        elif rule_result.get("stocks"):
                            tips = []
                            for row in (rule_result.get("stocks") or [])[:3]:
                                sym = str((row or {}).get("symbol") or "").upper()
                                if sym:
                                    tips.append(f"Analyze {sym}")
                                    tips.append(f"Should I buy {sym}?")
                            merged["suggestions"] = tips[:6]
                    else:
                        if not merged.get("symbol"):
                            merged["symbol"] = (
                                llm_result.get("symbol")
                                or llm_context.get("symbol")
                            )
                        if merged.get("current_price") in (None, "", 0, 0.0):
                            price = llm_context.get("current_price")
                            if price not in (None, "", 0, 0.0):
                                merged["current_price"] = price
                        if not merged.get("signal"):
                            # Surface paper-plan action for the Android profit card.
                            try:
                                import re as _re
                                m = _re.search(
                                    r"\*\*Action:\*\*\s*(BUY|SELL|HOLD|TRIM|WAIT)",
                                    str(llm_result.get("answer") or ""),
                                    flags=_re.I,
                                )
                                if m:
                                    merged["signal"] = m.group(1).upper()
                            except Exception:
                                pass
                        if not merged.get("suggestions") and merged.get("symbol"):
                            try:
                                from ..ai_engine import _build_stock_suggestions
                                merged["suggestions"] = _build_stock_suggestions(
                                    str(merged["symbol"]).upper(),
                                    query=normalized_query,
                                    intent=str(
                                        llm_result.get("intent")
                                        or (intent_result or {}).get("intent")
                                        or ""
                                    ),
                                )
                            except Exception:
                                pass
                    return _validated(merged, "indian-stock-llm", requested_tier)
                else:
                    logger.info("DEBUG: Indian Stock LLM returned no answer")
                    if requested_tier == "indian-stock-llm":
                        return _validated(llm_result or {"answer": "I could not build an answer from live data. Try a symbol or a clearer ask."}, "indian-stock-llm", requested_tier)
        except Exception as e:
            logger.error("Indian Stock LLM error: %s", e)
            if requested_tier == "indian-stock-llm":
                return _validated({"answer": f"Indian Stock LLM error: {str(e)}"}, "none", requested_tier)

    # Paid models only when the caller asked for them — not for the BYSEL app path.
    if requested_tier == "groq":
        try:
            from ..groq_llm import groq_available, ask_groq
            logger.info(f"DEBUG: groq_available() = {groq_available()}")
            if groq_available():
                enriched_ctx = await _build_enriched_context()
                logger.info(f"DEBUG: Calling Groq with intent={intent_result.get('intent')}, symbol={enriched_ctx.get('symbol')}")
                groq_result = await ask_groq(
                    normalized_query,
                    context=enriched_ctx,
                    conversation_history=body.conversation_history,
                    intent_result=intent_result,
                    response_style=response_style,
                )
                if groq_result.get("answer"):
                    logger.info("DEBUG: Groq returned answer, using Groq response")
                    merged = {
                        **rule_result,
                        "answer": groq_result["answer"],
                    }
                    try:
                        from ..groq_llm import get_small_talk_response as _gst
                        if _gst(normalized_query) or len(normalized_query.strip()) < 3:
                            merged.pop("symbol", None)
                            merged.pop("suggestions", None)
                    except Exception:
                        pass
                    if detected_intent == "SECTOR_SCREEN":
                        merged["symbol"] = None
                        merged["signal"] = None
                        if rule_result.get("suggestions"):
                            merged["suggestions"] = list(rule_result.get("suggestions") or [])[:6]
                    else:
                        if not merged.get("symbol") and enriched_ctx.get("symbol"):
                            merged["symbol"] = enriched_ctx["symbol"]
                        if not merged.get("suggestions") and merged.get("symbol"):
                            try:
                                from ..ai_engine import _build_stock_suggestions
                                intent = (intent_result or {}).get("intent", "")
                                exclude = {
                                    "PREDICT": "prediction",
                                    "BUY_SELL": "buy_sell",
                                    "TECHNICAL": "technical",
                                    "FUNDAMENTAL": "fundamentals",
                                    "NEWS": "news",
                                }.get(intent, "")
                                merged["suggestions"] = _build_stock_suggestions(
                                    str(merged["symbol"]).upper(),
                                    exclude=exclude,
                                    query=normalized_query,
                                    intent=intent,
                                )
                            except Exception:
                                pass
                    return _validated(merged, "groq", requested_tier)
                else:
                    logger.info("DEBUG: Groq returned empty, falling back")
            else:
                logger.info("DEBUG: Groq not available")
                if requested_tier == "groq":
                    return _validated({"answer": "Groq LLM not available. Please use tier='auto' for fallback."}, "none", requested_tier)
        except Exception as e:
            logger.error("Groq LLM error: %s", e)
            if requested_tier == "groq":
                return _validated({"answer": f"Groq LLM error: {str(e)}"}, "none", requested_tier)

    # Gemini only when the caller asked for it.
    if requested_tier == "gemini":
        try:
            from ..gemini_llm import gemini_available, ask_gemini
            logger.info(f"DEBUG: gemini_available() = {gemini_available()}")
            if gemini_available():
                enriched_ctx = await _build_enriched_context()
                logger.info(f"DEBUG: Calling Gemini with intent={intent_result.get('intent')}, symbol={enriched_ctx.get('symbol')}")
                if response_style == "concise":
                    gemini_style_prompt = "Response style: concise. Give direct answer first with minimal explanation."
                else:
                    gemini_style_prompt = "Response style: detailed. Give structured analysis with clear steps and reasoning."

                if intent_result.get("intent") == "CALCULATION":
                    gemini_style_prompt += " If this is a calculation query, show formula and step-by-step math."

                gemini_timeout = float(os.getenv("GEMINI_TIMEOUT_SECONDS", "20"))
                gemini_result = await asyncio.wait_for(
                    asyncio.to_thread(
                        ask_gemini,
                        normalized_query,
                        enriched_ctx,
                        gemini_style_prompt,
                    ),
                    timeout=gemini_timeout,
                )
                if gemini_result.get("answer") and not gemini_result.get("error"):
                    logger.info("DEBUG: Gemini returned answer, using Gemini response")
                    merged = {
                        **rule_result,
                        "answer": gemini_result["answer"],
                    }
                    if not merged.get("symbol") and enriched_ctx.get("symbol"):
                        merged["symbol"] = enriched_ctx["symbol"]
                    if not merged.get("suggestions") and merged.get("symbol"):
                        try:
                            from ..ai_engine import _build_stock_suggestions
                            intent = (intent_result or {}).get("intent", "")
                            exclude = {
                                "PREDICT": "prediction",
                                "BUY_SELL": "buy_sell",
                                "TECHNICAL": "technical",
                                "FUNDAMENTAL": "fundamentals",
                                "NEWS": "news",
                            }.get(intent, "")
                            merged["suggestions"] = _build_stock_suggestions(
                                str(merged["symbol"]).upper(),
                                exclude=exclude,
                                query=normalized_query,
                                intent=intent,
                            )
                        except Exception:
                            pass
                    return _validated(merged, "gemini", requested_tier)
                else:
                    error_msg = gemini_result.get("error", "Unknown error")
                    logger.info(f"DEBUG: Gemini returned error or empty ({error_msg}), falling back")
            else:
                logger.info("DEBUG: Gemini not available")
                if requested_tier == "gemini":
                    return _validated({"answer": "Gemini LLM not available. Please use tier='auto' for fallback."}, "none", requested_tier)
        except Exception as e:
            logger.error("Gemini LLM error: %s", e, exc_info=True)
            if requested_tier == "gemini":
                return _validated({"answer": f"Gemini LLM error: {str(e)}"}, "none", requested_tier)

    # Tier 3: rule-engine — run now if we skipped it for the fast path
    if not needs_rule_first or not (rule_result.get("answer") or "").strip():
        logger.info("DEBUG: Running rule-engine fallback (needs_rule_first=%s)", needs_rule_first)
        try:
            rule_result = await asyncio.to_thread(ai_assistant, rule_query, None, auth_user_id)
        except Exception as e:
            logger.error("Rule-engine fallback error: %s", e)
    logger.info("DEBUG: Falling back to rule-engine only")
    return _validated(rule_result, "rule-engine", requested_tier)


@router.get("/ai/analyze/{symbol}")
async def ai_analyze_endpoint(symbol: str):
    """Get comprehensive AI analysis for a stock including technical,
    fundamental analysis, score, prediction, and plain-English summary."""
    result = await asyncio.to_thread(analyze_stock, symbol.upper())
    if "error" in result and "predictions" not in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@router.get("/ai/analyze-fast/{symbol}")
async def ai_analyze_fast_endpoint(symbol: str):
    """Ultra-fast stock detail loading (<1s) with 20-second cache.
    Perfect for real-time price updates during market hours."""
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
    result = await asyncio.to_thread(get_best_stocks_to_buy, limit)
    return result


@router.get("/ai/practice-ideas")
async def ai_practice_ideas_endpoint(limit: int = 6):
    """Educational paper-trade practice drills (entry/target/SL + coaching).

    Not SEBI tips or investment advice — drills for simulation discipline.
    """
    from ..ai_engine import get_practice_ideas
    return get_practice_ideas(limit=limit)


@router.get("/ai/trade-levels/{symbol}")
async def ai_trade_levels_endpoint(symbol: str):
    """Get risk-adjusted stop loss and take profit levels for a stock.
    Includes entry signals, position sizing, and risk:reward ratios."""
    result = get_stop_loss_take_profit(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/drawdown-risk/{symbol}")
async def ai_drawdown_risk_endpoint(symbol: str):
    """Get historical drawdown risk, current distance from peak, and risk scoring.
    Helps users understand maximum downside potential."""
    result = calculate_drawdown_risk(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/relative-strength/{symbol}")
async def ai_relative_strength_endpoint(symbol: str):
    """Get relative strength vs sector and market.
    Compare stock performance to peers and benchmark."""
    result = calculate_relative_strength(symbol.upper())
    if "error" in result:
        raise HTTPException(status_code=404, detail=result.get("error", "Analysis failed"))
    return result


@router.get("/ai/trade-accuracy")
async def ai_trade_accuracy_endpoint(timeframe: str = "one_month"):
    """Get backtesting accuracy of ML recommendations from N days ago.
    Shows win rate, average profit, and Sharpe ratio."""
    # calculate_trade_accuracy is imported at module top from ..ai_engine
    
    if timeframe not in ["one_day", "one_month", "three_months"]:
        timeframe = "one_month"
    
    result = calculate_trade_accuracy(timeframe=timeframe)
    return result


@router.get("/market/sector-rotation")
async def sector_rotation_signals_endpoint():
    """Get sector rotation signals based on momentum, strength, and valuation.
    Identifies which sectors to accumulate, hold, or reduce."""
    result = get_sector_rotation_signals()
    return result


@router.get("/market/earnings-calendar")
async def earnings_calendar_endpoint(next_days: int = 30):
    """Get upcoming earnings calendar with pre-earnings volatility alerts.
    Helps avoid gap risk and identifies volatility trading opportunities."""
    # get_earnings_calendar is imported at module top from ..ai_engine
    
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
    # advanced_stock_screener is imported at module top from ..ai_engine
    
    if filters is None:
        filters = {}
    
    result = advanced_stock_screener(filters)
    return result


# ==================== PORTFOLIO HEALTH SCORE ====================

@router.get("/portfolio/health")
async def portfolio_health_endpoint(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Get portfolio health score (0-100) for the authenticated user's holdings."""
    holdings_db = db.query(HoldingModel).filter(HoldingModel.user_id == user.id).all()
    holdings_list = []
    for h in holdings_db:
        holdings_list.append({
            "symbol": h.symbol,
            "quantity": h.quantity,
            "avgPrice": h.avg_price,
            "lastPrice": h.last_price,
        })
    try:
        result = await asyncio.to_thread(calculate_portfolio_health, holdings_list)
        return result
    except Exception as exc:
        logger.warning("portfolio_health_endpoint failed reason=%s", exc)
        return {
            "overallScore": 0,
            "grade": "N/A",
            "breakdown": {},
            "suggestions": ["Health score is temporarily unavailable. Try refresh in a few seconds."],
            "summary": "Couldn't refresh portfolio health right now. Your holdings are unchanged.",
            "sectorAllocation": {},
            "riskLevel": "none",
        }


@router.get("/portfolio/risk")
async def portfolio_risk_endpoint(user=Depends(get_current_user)):
    """Phase 1.3 paper risk snapshot from current holdings + quotes.

    Auth required. No Postgres. Does not invent 1Y drawdown or a correlation matrix.
    Nifty −5%/−10% is a beta=1 illustration on equity value, not a forecast.
    """
    try:
        holdings = await asyncio.to_thread(_holdings_in_thread, user.id)
    except Exception as exc:
        logger.error("portfolio_risk_endpoint_failed user_id=%s reason=%s", user.id, exc)
        raise HTTPException(status_code=503, detail="Portfolio risk temporarily unavailable") from exc

    def _compute():
        from ..market_data import fetch_quotes
        from ..portfolio_risk import compute_portfolio_risk_from_holdings

        holdings_list = [
            {
                "symbol": h.symbol,
                "qty": h.qty,
                "avgPrice": h.avgPrice,
                "last": h.last,
                "pnl": h.pnl,
            }
            for h in holdings
        ]
        symbols = [row["symbol"] for row in holdings_list if row.get("symbol")]
        quotes = []
        if symbols:
            try:
                quotes = fetch_quotes(symbols) or []
            except Exception as exc:
                logger.warning("portfolio_risk quote batch failed reason=%s", exc)
                quotes = []
        return compute_portfolio_risk_from_holdings(holdings_list, quotes)

    try:
        return await asyncio.to_thread(_compute)
    except Exception as exc:
        logger.warning("portfolio_risk_compute_failed reason=%s", exc)
        from ..portfolio_risk import empty_portfolio_risk

        payload = empty_portfolio_risk()
        payload["message"] = "Couldn't refresh risk snapshot. Your holdings are unchanged."
        return payload


# ==================== MARKET HEATMAP ====================

@router.get("/market/heatmap")
async def market_heatmap_endpoint():
    """Get real-time market heatmap with sector-wise performance,
    market breadth, mood indicator, and individual stock data."""
    try:
        result = await asyncio.to_thread(get_market_heatmap)
        return result
    except Exception as exc:
        logger.error("market_heatmap_endpoint_failed reason=%s", exc)
        from ..market_heatmap import _empty_heatmap_payload
        return _empty_heatmap_payload(mood_desc="Heatmap temporarily unavailable. Please retry.")


@router.get("/market/sector/{sector_name}")
async def sector_detail_endpoint(sector_name: str):
    """Get detailed data for a specific sector."""
    result = await asyncio.to_thread(get_sector_detail, sector_name)
    if not result:
        raise HTTPException(status_code=404, detail=f"Sector '{sector_name}' not found")
    return result


@router.get("/market/signal-lab/buckets", response_model=SignalLabBucketsResponse)
async def signal_lab_buckets_endpoint(
    limitPerBucket: int = Query(8, ge=3, le=20),
    forceRefresh: bool = Query(False),
):
    """Get curated Signal Lab phase-2 discovery buckets (Momentum Movers / Sector Leadership).

    Buckets currently include:
    - Momentum Movers: high-participation names with elevated absolute moves (proxy)
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

    payload = await asyncio.to_thread(
        _build_signal_lab_buckets_payload, limitPerBucket
    )
    _SIGNAL_LAB_CACHE[limitPerBucket] = (now, payload)
    _trim_signal_lab_cache()
    return payload


@router.get("/market/scanner", response_model=ScannerResponse)
async def market_scanner_endpoint(
    mode: str = Query(
        "long_term",
        description="long_term, swing, high_quality, momentum, value, custom, quality_screen",
    ),
    limit: int = Query(30, ge=5, le=40),
    forceRefresh: bool = Query(False),
):
    """Hybrid scanner shortlist with BYSEL Score pillars.

    Universe is NIFTY 50 + the default watchlist catalog (not a full NSE crawl).
    Missing fields stay null and are skipped in the weighted blend.
    """
    normalized = (mode or "long_term").strip().lower()
    if normalized not in SCANNER_MODES:
        raise HTTPException(
            status_code=400,
            detail="mode must be long_term, swing, high_quality, momentum, value, custom, or quality_screen",
        )
    return await asyncio.to_thread(get_market_scanner, normalized, limit, forceRefresh)


@router.get("/market/scanner/xray/{symbol}", response_model=ScannerRow)
async def market_scanner_xray_endpoint(symbol: str):
    """Single-symbol BYSEL Score x-ray (same pillars as the scanner)."""
    row = await asyncio.to_thread(get_symbol_xray, symbol)
    if not row:
        raise HTTPException(status_code=404, detail="No quoted snapshot to score")
    return row


@router.get("/market/scanner/history/{symbol}", response_model=ScoreHistoryResponse)
async def market_scanner_history_endpoint(
    symbol: str,
    days: int = Query(90, ge=1, le=90),
):
    """Daily BYSEL Score snapshots for 30/90-day history. Empty until snapshots exist."""
    window = 30 if days <= 30 else 90
    return await asyncio.to_thread(get_score_history, symbol, window)


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

    live_map = _warm_mf_live_map()
    compared_funds: list[MutualFund] = []
    missing: list[str] = []
    for code in deduped_codes:
        row = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == code).first()
        stored = _mf_from_db_row(row) if row is not None else None
        fund = _merge_compare_fund(live_map.get(code), stored)
        if fund is None:
            missing.append(code)
        else:
            compared_funds.append(fund)

    if missing:
        # Only download AMFI if selected codes are not in cache or DB.
        try:
            live_map = {fund.schemeCode: fund for fund in _fetch_live_mutual_funds()}
        except Exception as exc:
            logger.warning("mutual_funds.compare.live_fetch_failed reason=%s", str(exc))
            live_map = _warm_mf_live_map()
        still_missing: list[str] = []
        recovered: list[MutualFund] = []
        for code in missing:
            row = db.query(MutualFundModel).filter(MutualFundModel.scheme_code == code).first()
            stored = _mf_from_db_row(row) if row is not None else None
            fund = _merge_compare_fund(live_map.get(code), stored)
            if fund is None:
                still_missing.append(code)
            else:
                recovered.append(fund)
        if still_missing:
            raise HTTPException(
                status_code=404,
                detail=f"Mutual fund '{still_missing[0]}' not found",
            )
        compared_funds.extend(recovered)
        order = {code: idx for idx, code in enumerate(deduped_codes)}
        compared_funds.sort(key=lambda item: order.get(item.schemeCode, 99))

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
    user=Depends(get_current_user),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
):
    market = is_market_open()
    quote = fetch_quote(order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    wallet_balance = get_wallet(db, user.id).balance
    signal_data = build_pretrade_signal(
        order=order,
        live_price=live_price,
        wallet_balance=wallet_balance,
        market_open=market.isOpen,
    )

    response = place_order(
        db,
        order,
        user_id=user.id,
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
    user=Depends(get_current_user),
):
    order_type = (order.orderType or "").strip().upper()
    validity = (order.validity or "DAY").strip().upper()
    if order_type not in {"LIMIT", "SL", "SLM"}:
        raise HTTPException(status_code=400, detail="orderType must be LIMIT, SL or SLM for trigger orders")
    if validity not in {"DAY", "IOC", "GTC"}:
        raise HTTPException(status_code=400, detail="validity must be DAY, IOC or GTC")

    trigger = TriggerOrderModel(
        user_id=user.id,
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
async def get_trigger_orders_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    rows = (
        db.query(TriggerOrderModel)
        .filter(TriggerOrderModel.user_id == user.id)
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
    user=Depends(get_current_user),
):
    symbol_list = [item.strip().upper() for item in (symbols or "").split(",") if item.strip()]
    processed = evaluate_pending_triggers(db=db, user_id=user.id, symbols=symbol_list)
    return {
        "status": "ok",
        "processedCount": len(processed),
        "processed": processed,
    }


@router.post("/orders/baskets", response_model=BasketOrderResponse)
async def create_basket_order_endpoint(
    request: BasketOrderRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    if not request.legs:
        raise HTTPException(status_code=400, detail="Basket must contain at least one leg")

    basket = BasketOrderModel(
        user_id=user.id,
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
async def get_baskets_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    rows = (
        db.query(BasketOrderModel)
        .filter(BasketOrderModel.user_id == user.id)
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
    user=Depends(get_current_user),
    x_idempotency_key: str | None = Header(default=None, alias="X-Idempotency-Key"),
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
):
    basket = db.query(BasketOrderModel).filter(BasketOrderModel.id == basket_id, BasketOrderModel.user_id == user.id).first()
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
            user_id=user.id,
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
    current_user=Depends(get_current_user),
):
    user_id = int(current_user.id)
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
async def family_dashboard_endpoint(db: Session = Depends(get_db), current_user=Depends(get_current_user)):
    user_id = int(current_user.id)
    members = db.query(FamilyMemberModel).filter(FamilyMemberModel.user_id == user_id).all()
    holdings = get_holdings(db, user_id)
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
    current_user=Depends(get_current_user),
):
    user_id = int(current_user.id)
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
async def get_goals_endpoint(db: Session = Depends(get_db), current_user=Depends(get_current_user)):
    user_id = int(current_user.id)
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
    current_user=Depends(get_current_user),
):
    user_id = int(current_user.id)
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
    user=Depends(get_current_user),
):
    quote = fetch_quote(payload.order.symbol.upper())
    live_price = float(quote.get("last") or 0.0)
    market = is_market_open()
    wallet_balance = payload.walletBalance if payload.walletBalance is not None else get_wallet(db, user.id).balance
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
async def copilot_post_trade_endpoint(
    payload: CopilotPostTradeRequest,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    order = (
        db.query(OrderModel)
        .filter(OrderModel.id == payload.orderId, OrderModel.user_id == user.id)
        .first()
    )
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
async def copilot_portfolio_actions_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    holdings = get_holdings(db, user.id)
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
