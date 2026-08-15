"""
BYSEL Portfolio Health Score Engine

Snapshot heuristic (0-100) of current paper-book *quality*, not a return forecast.
It does not predict future P&L, volatility, beta, or drawdown.

  1. Diversification (25) — name count + mapped sector spread
  2. Risk (25)            — concentration, marked losers, volatile-sector weight
  3. Quality (25)         — static large-cap / blue-chip lists
  4. Balance (25)         — deviation from equal weight + sector cap

Cash/wallet is not in the book (equity holdings only). Missing quotes must not
be treated as flat/healthy P&L.
"""

import yfinance as yf
import numpy as np
import logging
from datetime import datetime
from typing import Dict, List, Tuple, Optional
from .market_data import _yf_ticker, INDIAN_STOCKS, fetch_quote

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────
# SECTOR CLASSIFICATION (for diversification analysis)
# ──────────────────────────────────────────────────────────────

SECTOR_MAP = {
    # Banking & Finance
    "HDFCBANK": "Banking", "ICICIBANK": "Banking", "SBIN": "Banking",
    "KOTAKBANK": "Banking", "AXISBANK": "Banking", "INDUSINDBK": "Banking",
    "PNB": "Banking", "BANKBARODA": "Banking", "CANBK": "Banking",
    "IDFCFIRSTB": "Banking", "FEDERALBNK": "Banking", "BANDHANBNK": "Banking",
    "AUBANK": "Banking", "RBLBANK": "Banking", "YESBANK": "Banking",
    "BAJFINANCE": "NBFC", "BAJAJFINSV": "NBFC", "HDFCLIFE": "Insurance",
    "SBILIFE": "Insurance", "ICICIPRULI": "Insurance", "ICICIGI": "Insurance",
    "MUTHOOTFIN": "NBFC", "CHOLAFIN": "NBFC", "MANAPPURAM": "NBFC",
    "LICHSGFIN": "NBFC", "PEL": "NBFC", "SHRIRAMFIN": "NBFC",

    # IT
    "TCS": "IT", "INFY": "IT", "WIPRO": "IT", "HCLTECH": "IT",
    "TECHM": "IT", "LTIM": "IT", "MPHASIS": "IT", "COFORGE": "IT",
    "PERSISTENT": "IT", "LTTS": "IT", "HAPPSTMNDS": "IT",

    # Semiconductor / OSAT / electronics manufacturing
    "MOSCHIP": "Semiconductor", "KAYNES": "Semiconductor", "SYRMA": "Semiconductor",
    "DIXON": "Semiconductor", "AVALON": "Semiconductor", "CYIENTDLM": "Semiconductor",
    "CGPOWER": "Semiconductor", "TATAELXSI": "Semiconductor", "CYIENT": "Semiconductor",
    "RIR": "Semiconductor", "PGEL": "Semiconductor", "CENTUM": "Semiconductor",
    "SPELS": "Semiconductor",

    # Pharma & Healthcare
    "SUNPHARMA": "Pharma", "DRREDDY": "Pharma", "CIPLA": "Pharma",
    "DIVISLAB": "Pharma", "LUPIN": "Pharma", "AUROPHARMA": "Pharma",
    "BIOCON": "Pharma", "TORNTPHARM": "Pharma", "ALKEM": "Pharma",
    "IPCALAB": "Pharma", "LAURUSLABS": "Pharma", "GLENMARK": "Pharma",
    "APOLLOHOSP": "Healthcare", "MAXHEALTH": "Healthcare", "FORTIS": "Healthcare",

    # Auto
    "TMPV": "Auto", "TMCV": "Auto", "MARUTI": "Auto", "BAJAJ-AUTO": "Auto",
    "HEROMOTOCO": "Auto", "EICHERMOT": "Auto", "TVSMOTOR": "Auto",
    "ASHOKLEY": "Auto", "MOTHERSON": "Auto", "BHARATFORG": "Auto",
    "MRF": "Auto", "BALKRISIND": "Auto", "BOSCHLTD": "Auto",

    # Energy & Power
    "RELIANCE": "Energy", "ONGC": "Energy", "BPCL": "Energy",
    "IOC": "Energy", "NTPC": "Power", "POWERGRID": "Power",
    "TATAPOWER": "Power", "ADANIGREEN": "Power", "ADANIENT": "Energy",
    "GAIL": "Energy", "PETRONET": "Energy", "COALINDIA": "Mining",
    "VEDL": "Mining", "NMDC": "Mining", "HINDPETRO": "Energy",

    # Metals & Mining
    "TATASTEEL": "Metals", "JSWSTEEL": "Metals", "HINDALCO": "Metals",
    "SAIL": "Metals", "NATIONALUM": "Metals", "JINDALSTEL": "Metals",
    "APLAPOLLO": "Metals",

    # FMCG
    "HINDUNILVR": "FMCG", "ITC": "FMCG", "NESTLEIND": "FMCG",
    "BRITANNIA": "FMCG", "DABUR": "FMCG", "MARICO": "FMCG",
    "COLPAL": "FMCG", "GODREJCP": "FMCG", "TATACONSUM": "FMCG",
    "VBL": "FMCG", "UBL": "FMCG", "RADICO": "FMCG",

    # Infra & Construction
    "LT": "Infra", "ADANIPORTS": "Infra", "IRCON": "Infra",
    "RVNL": "Infra", "NBCC": "Infra", "NCC": "Infra",
    "KEC": "Infra", "ULTRACEMCO": "Cement", "AMBUJACEM": "Cement",
    "SHREECEM": "Cement", "DALMIACEM": "Cement", "ACC": "Cement",

    # Real Estate
    "DLF": "Real Estate", "GODREJPROP": "Real Estate",
    "OBEROIRLTY": "Real Estate", "PRESTIGE": "Real Estate",
    "BRIGADE": "Real Estate", "LODHA": "Real Estate", "SOBHA": "Real Estate",

    # Defence
    "HAL": "Defence", "BEL": "Defence", "BDL": "Defence",
    "MAZDOCK": "Defence", "COCHINSHIP": "Defence",

    # Telecom
    "BHARTIARTL": "Telecom", "IDEA": "Telecom",

    # Consumer Durables
    "TITAN": "Consumer", "TRENT": "Consumer", "HAVELLS": "Consumer",
    "VOLTAS": "Consumer", "CROMPTON": "Consumer", "BLUESTARLT": "Consumer",
    "BATAINDIA": "Consumer", "PAGEIND": "Consumer",

    # Chemicals
    "PIDILITIND": "Chemicals", "ASIANPAINT": "Chemicals",
    "BERGERPAINTS": "Chemicals", "SRF": "Chemicals",
    "AARTI": "Chemicals", "DEEPAKNTR": "Chemicals",
    "NAVINFLUOR": "Chemicals", "CLEAN": "Chemicals",
}


def _resolve_mark(holding: Dict, live_price: Optional[float]) -> Tuple[float, str]:
    """Pick a mark for weights. Never invent a 0% P&L from cost alone."""
    avg_price = float(holding.get("avgPrice") or holding.get("avg_price") or 0)
    stored_last = float(holding.get("lastPrice") or holding.get("last_price") or 0)
    if live_price is not None and live_price > 0:
        return float(live_price), "live"
    if stored_last > 0:
        return stored_last, "stale"
    return avg_price, "cost"


def calculate_portfolio_health(holdings: List[Dict]) -> Dict:
    """
    Snapshot book-quality score for the current paper holdings.

    Args:
        holdings: dicts with symbol, quantity/qty, avgPrice, optional lastPrice

    Returns:
        Dict with overall score (0-100), breakdown, grade, and suggestions.
        Does not forecast returns.
    """
    empty = {
        "overallScore": 0,
        "grade": "N/A",
        "breakdown": {},
        "suggestions": ["Start by buying some stocks to build your portfolio!"],
        "summary": "Your portfolio is empty. Start investing to see your health score.",
        "sectorAllocation": {},
        "riskLevel": "none",
        "scoreType": "snapshot",
        "snapshotNote": "This is a current book-quality score, not a forecast of future returns.",
        "quoteCoverage": 0.0,
        "quotedCount": 0,
        "unquotedCount": 0,
        "pnlReliable": False,
    }
    if not holdings:
        return empty

    # Fetch current prices in one batch (never N serial Yahoo calls — that 429s refresh).
    symbols = [
        str(h.get("symbol") or "").upper()
        for h in holdings
        if (h.get("quantity", 0) or h.get("qty", 0) or 0) > 0 and h.get("symbol")
    ]
    live_by_symbol: Dict[str, float] = {}
    try:
        from .market_data import fetch_quotes, _safe_number
        for quote in fetch_quotes(symbols) or []:
            sym = str(quote.get("symbol") or "").upper()
            last = _safe_number(quote.get("last"), 0.0)
            if last <= 0:
                last = _safe_number(quote.get("ltp") or quote.get("price"), 0.0)
            if sym and last > 0:
                live_by_symbol[sym] = last
    except Exception as exc:
        logger.warning("portfolio_health quote batch failed reason=%s", exc)

    portfolio_data = []
    total_value = 0
    total_invested = 0

    for h in holdings:
        sym = str(h.get("symbol") or "").upper()
        qty = h.get("quantity", 0) or h.get("qty", 0)
        avg_price = h.get("avgPrice", 0) or h.get("avg_price", 0)

        if qty <= 0:
            continue

        current_price, price_source = _resolve_mark(h, live_by_symbol.get(sym))
        value = current_price * qty
        invested = avg_price * qty
        has_mark = price_source != "cost"
        pnl = (value - invested) if has_mark else 0
        pnl_pct = ((current_price - avg_price) / avg_price * 100) if has_mark and avg_price > 0 else 0

        sector = SECTOR_MAP.get(sym, "Other")

        portfolio_data.append({
            "symbol": sym,
            "quantity": qty,
            "avgPrice": avg_price,
            "currentPrice": current_price,
            "value": value,
            "invested": invested,
            "pnl": pnl,
            "pnlPercent": pnl_pct,
            "sector": sector,
            "weight": 0,  # calculated below
            "priceSource": price_source,
        })
        total_value += value
        total_invested += invested

    if not portfolio_data:
        return empty

    # Calculate weights
    for item in portfolio_data:
        item["weight"] = (item["value"] / total_value * 100) if total_value > 0 else 0

    # Calculate all sub-scores
    div_score, div_details = _diversification_score(portfolio_data)
    risk_score, risk_details = _risk_score(portfolio_data)
    quality_score, quality_details = _quality_score(portfolio_data)
    balance_score, balance_details = _balance_score(portfolio_data)

    overall = div_score + risk_score + quality_score + balance_score
    overall = min(max(overall, 0), 100)

    # Grade
    if overall >= 85:
        grade = "A+"
    elif overall >= 75:
        grade = "A"
    elif overall >= 65:
        grade = "B+"
    elif overall >= 55:
        grade = "B"
    elif overall >= 45:
        grade = "C+"
    elif overall >= 35:
        grade = "C"
    else:
        grade = "D"

    # Sector allocation
    sector_allocation = {}
    for item in portfolio_data:
        s = item["sector"]
        if s not in sector_allocation:
            sector_allocation[s] = {"value": 0, "weight": 0, "stocks": []}
        sector_allocation[s]["value"] += item["value"]
        sector_allocation[s]["stocks"].append(item["symbol"])
    for s in sector_allocation:
        sector_allocation[s]["weight"] = round(sector_allocation[s]["value"] / total_value * 100, 1) if total_value > 0 else 0

    # Risk level
    if risk_score >= 20:
        risk_level = "low"
    elif risk_score >= 14:
        risk_level = "moderate"
    elif risk_score >= 8:
        risk_level = "high"
    else:
        risk_level = "very_high"

    # Generate suggestions
    suggestions = _generate_suggestions(
        portfolio_data, sector_allocation, div_score, risk_score,
        quality_score, balance_score, total_value, total_invested
    )

    marked = [item for item in portfolio_data if item.get("priceSource") != "cost"]
    quoted_live = [item for item in portfolio_data if item.get("priceSource") == "live"]
    unquoted_count = len(portfolio_data) - len(quoted_live)
    quote_coverage = (len(quoted_live) / len(portfolio_data)) if portfolio_data else 0.0
    pnl_reliable = bool(marked)

    if marked:
        marked_value = sum(item["value"] for item in marked)
        marked_invested = sum(item["invested"] for item in marked)
        overall_pnl = marked_value - marked_invested
        overall_pnl_pct = (overall_pnl / marked_invested * 100) if marked_invested > 0 else 0
    else:
        overall_pnl = 0
        overall_pnl_pct = 0

    snapshot_note = "This is a current book-quality score, not a forecast of future returns."
    if unquoted_count:
        snapshot_note += (
            f" Live quotes missing for {unquoted_count} name(s); "
            "P&L is not treated as flat/healthy."
        )

    summary = _generate_health_summary(
        overall, grade, len(portfolio_data), len(sector_allocation),
        total_value, overall_pnl, overall_pnl_pct, risk_level,
        pnl_reliable=pnl_reliable, unquoted_count=unquoted_count,
    )

    return {
        "overallScore": round(overall),
        "grade": grade,
        "breakdown": {
            "diversification": {"score": div_score, "maxScore": 25, "details": div_details},
            "risk": {"score": risk_score, "maxScore": 25, "details": risk_details},
            "quality": {"score": quality_score, "maxScore": 25, "details": quality_details},
            "balance": {"score": balance_score, "maxScore": 25, "details": balance_details},
        },
        "sectorAllocation": sector_allocation,
        "riskLevel": risk_level,
        "suggestions": suggestions,
        "summary": summary,
        "totalValue": round(total_value, 2),
        "totalInvested": round(total_invested, 2),
        "totalPnl": round(overall_pnl, 2),
        "totalPnlPercent": round(overall_pnl_pct, 2),
        "stockCount": len(portfolio_data),
        "sectorCount": len(sector_allocation),
        "lastUpdated": datetime.utcnow().isoformat(),
        "scoreType": "snapshot",
        "snapshotNote": snapshot_note,
        "quoteCoverage": round(quote_coverage, 4),
        "quotedCount": len(quoted_live),
        "unquotedCount": unquoted_count,
        "pnlReliable": pnl_reliable,
    }


def _diversification_score(portfolio: List[Dict]) -> Tuple[int, str]:
    """Score based on number of stocks and sector spread. Max 25."""
    n_stocks = len(portfolio)
    sectors = set(item["sector"] for item in portfolio)
    n_sectors = len(sectors)

    score = 0

    # Number of stocks (0-12 pts)
    if n_stocks >= 15:
        score += 12
    elif n_stocks >= 10:
        score += 10
    elif n_stocks >= 7:
        score += 8
    elif n_stocks >= 5:
        score += 6
    elif n_stocks >= 3:
        score += 4
    else:
        score += 2

    # Sector spread (0-13 pts)
    if n_sectors >= 6:
        score += 13
    elif n_sectors >= 4:
        score += 10
    elif n_sectors >= 3:
        score += 7
    elif n_sectors >= 2:
        score += 5
    else:
        score += 2

    details = f"{n_stocks} stocks across {n_sectors} sectors"
    return min(score, 25), details


def _risk_score(portfolio: List[Dict]) -> Tuple[int, str]:
    """Score based on book structure. Max 25. Higher = lower structural risk.

    Not realized vol, beta, or drawdown. P&L penalties only use marked names
    so a missing quote cannot look like a healthy 0% book.
    """
    score = 15  # base

    # Check for over-concentration
    max_weight = max(item["weight"] for item in portfolio) if portfolio else 0
    if max_weight > 50:
        score -= 8
    elif max_weight > 30:
        score -= 4
    elif max_weight > 20:
        score -= 2
    else:
        score += 3

    marked = [item for item in portfolio if item.get("priceSource") in ("live", "stale")]
    losers = [item for item in marked if item["pnlPercent"] < -10]
    if marked:
        loser_ratio = len(losers) / len(marked)
        if loser_ratio > 0.5:
            score -= 5
        elif loser_ratio > 0.3:
            score -= 3
        elif loser_ratio < 0.1 and len(marked) == len(portfolio):
            score += 3
    # else: no marks — do not award a "few losers" bonus

    # Volatility proxy: sector concentration in volatile sectors
    volatile_sectors = {"Metals", "Mining", "Real Estate", "Defence", "Chemicals"}
    volatile_weight = sum(
        item["weight"] for item in portfolio if item["sector"] in volatile_sectors
    )
    if volatile_weight > 40:
        score -= 4
    elif volatile_weight > 25:
        score -= 2
    elif volatile_weight < 10:
        score += 2

    unmarked = len(portfolio) - len(marked)
    coverage = ""
    if unmarked:
        coverage = f"; P&L not scored for {unmarked} name(s) without a mark"
    details = (
        f"Max single stock weight: {max_weight:.1f}%, "
        f"{len(losers)} marked positions in loss > 10%{coverage}"
    )
    return min(max(score, 0), 25), details


def _quality_score(portfolio: List[Dict]) -> Tuple[int, str]:
    """Score based on stock quality (blue-chip vs small-cap). Max 25."""
    blue_chips = {
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR",
        "SBIN", "BHARTIARTL", "ITC", "KOTAKBANK", "LT", "AXISBANK",
        "TITAN", "BAJFINANCE", "ASIANPAINT", "MARUTI", "HCLTECH",
        "SUNPHARMA", "NTPC", "TATASTEEL", "WIPRO", "NESTLEIND",
        "TMPV", "BAJAJ-AUTO", "POWERGRID", "ONGC",
        "ADANIPORTS", "ULTRACEMCO", "DRREDDY", "CIPLA",
        "DIVISLAB", "BRITANNIA", "EICHERMOT", "HAL",
    }

    large_caps = blue_chips | {
        "HDFCLIFE", "SBILIFE", "BAJAJFINSV", "TECHM", "INDUSINDBK",
        "HEROMOTOCO", "BPCL", "IOC", "GAIL", "DLF",
        "TATAPOWER", "COALINDIA", "PNB", "BEL", "TATACONSUM",
    }

    blue_weight = sum(item["weight"] for item in portfolio if item["symbol"] in blue_chips)
    large_weight = sum(item["weight"] for item in portfolio if item["symbol"] in large_caps)

    score = 12  # base
    if blue_weight >= 50:
        score += 10
    elif blue_weight >= 30:
        score += 7
    elif blue_weight >= 15:
        score += 4

    if large_weight >= 70:
        score += 3
    elif large_weight >= 50:
        score += 2

    details = f"{blue_weight:.1f}% in blue-chips, {large_weight:.1f}% in large-caps"
    return min(max(score, 0), 25), details


def _balance_score(portfolio: List[Dict]) -> Tuple[int, str]:
    """Score based on allocation balance. Max 25."""
    if not portfolio:
        return 0, "Empty portfolio"

    weights = [item["weight"] for item in portfolio]

    # Ideal: equal weight. Measure deviation from equal weight
    n = len(portfolio)
    ideal_weight = 100 / n
    deviations = [abs(w - ideal_weight) for w in weights]
    avg_deviation = sum(deviations) / n

    score = 15  # base

    if avg_deviation < 5:
        score += 10  # very balanced
    elif avg_deviation < 10:
        score += 7
    elif avg_deviation < 20:
        score += 3
    elif avg_deviation > 30:
        score -= 5

    # Sector balance
    sectors = {}
    for item in portfolio:
        s = item["sector"]
        sectors[s] = sectors.get(s, 0) + item["weight"]

    max_sector_weight = max(sectors.values()) if sectors else 0
    if max_sector_weight > 50:
        score -= 5
    elif max_sector_weight > 35:
        score -= 2
    elif max_sector_weight < 25:
        score += 3

    details = f"Avg weight deviation: {avg_deviation:.1f}%, Max sector: {max_sector_weight:.1f}%"
    return min(max(score, 0), 25), details


def _generate_suggestions(
    portfolio, sectors, div_score, risk_score, quality_score, balance_score,
    total_value, total_invested
) -> List[str]:
    """Generate actionable suggestions to improve portfolio health."""
    suggestions = []
    n_stocks = len(portfolio)
    n_sectors = len(sectors)

    # Diversification
    if n_stocks < 5:
        suggestions.append("📌 Add more stocks — aim for at least 8-10 for good diversification.")
    if n_sectors < 3:
        missing = [s for s in ["Banking", "IT", "Pharma", "FMCG", "Energy"]
                    if s not in sectors]
        if missing:
            suggestions.append(f"📌 Diversify into: {', '.join(missing[:3])} sectors.")

    # Concentration risk
    for item in portfolio:
        if item["weight"] > 30:
            suggestions.append(
                f"⚠️ {item['symbol']} is {item['weight']:.1f}% of your portfolio. "
                f"Consider reducing to under 20%."
            )

    # Sector over-concentration
    for sector_name, data in sectors.items():
        if data["weight"] > 40:
            suggestions.append(
                f"⚠️ {sector_name} sector is {data['weight']:.1f}% — too concentrated. "
                f"Diversify into other sectors."
            )

    # Quality
    if quality_score < 12:
        suggestions.append(
            "💎 Consider adding blue-chip stocks (RELIANCE, TCS, HDFCBANK) "
            "for stability."
        )

    # Big losers (only marked names — cost-basis 0% is not a real P&L)
    big_losers = [
        item for item in portfolio
        if item.get("priceSource") in ("live", "stale") and item["pnlPercent"] < -20
    ]
    for loser in big_losers[:2]:
        suggestions.append(
            f"📉 {loser['symbol']} is down {abs(loser['pnlPercent']):.1f}%. "
            f"Review if fundamentals still hold or consider cutting losses."
        )

    # Big winners — book partial profits
    big_winners = [
        item for item in portfolio
        if item.get("priceSource") in ("live", "stale")
        and item["pnlPercent"] > 50
        and item["weight"] > 15
    ]
    for winner in big_winners[:2]:
        suggestions.append(
            f"🎯 {winner['symbol']} is up {winner['pnlPercent']:.1f}%. "
            f"Consider booking partial profits to lock in gains."
        )

    # General
    if not suggestions:
        suggestions.append("✅ Your portfolio looks well-balanced! Keep monitoring regularly.")

    return suggestions[:8]  # Max 8 suggestions


def _generate_health_summary(
    score, grade, n_stocks, n_sectors, total_value, pnl, pnl_pct, risk_level,
    pnl_reliable=True, unquoted_count=0,
) -> str:
    """Generate a human-readable portfolio health summary."""
    parts = []

    if score >= 75:
        parts.append(f"🏆 Strong book quality: {score}/100 (Grade {grade}).")
    elif score >= 55:
        parts.append(f"👍 Decent book quality: {score}/100 (Grade {grade}).")
    elif score >= 35:
        parts.append(f"⚡ Book quality needs attention: {score}/100 (Grade {grade}).")
    else:
        parts.append(f"⚠️ Book quality is weak: {score}/100 (Grade {grade}).")

    parts.append("This is a snapshot of current holdings, not a return forecast.")

    parts.append(
        f"You hold {n_stocks} stocks across {n_sectors} sectors "
        f"worth ₹{total_value:,.2f}."
    )

    if not pnl_reliable:
        parts.append("Mark-to-market P&L unavailable — quotes missing.")
    elif unquoted_count:
        if pnl >= 0:
            parts.append(
                f"Marked P&L: +₹{pnl:,.2f} ({pnl_pct:+.2f}%) 🟢 "
                f"({unquoted_count} name(s) still on cost)."
            )
        else:
            parts.append(
                f"Marked P&L: -₹{abs(pnl):,.2f} ({pnl_pct:.2f}%) 🔴 "
                f"({unquoted_count} name(s) still on cost)."
            )
    elif pnl >= 0:
        parts.append(f"Overall P&L: +₹{pnl:,.2f} ({pnl_pct:+.2f}%) 🟢")
    else:
        parts.append(f"Overall P&L: -₹{abs(pnl):,.2f} ({pnl_pct:.2f}%) 🔴")

    risk_text = {
        "low": "Structural risk is LOW.",
        "moderate": "Structural risk is MODERATE.",
        "high": "Structural risk is HIGH — consider rebalancing.",
        "very_high": "Structural risk is VERY HIGH — rebalancing recommended.",
    }
    parts.append(risk_text.get(risk_level, ""))

    return " ".join(parts)


def _get_sector_from_yahoo(symbol: str) -> str:
    """Try to get sector from Yahoo Finance if not in our map."""
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        info = ticker.info
        return info.get("sector", "Other")
    except Exception:
        return "Other"
