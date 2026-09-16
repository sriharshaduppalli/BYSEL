"""
Long-term + Swing hybrid scanner.

BYSEL Score = 0.35Q + 0.25V + 0.20T + 0.20M (balanced default).
Style tilt changes only pillar weights, never metric math:
  long-term 45/30/15/10, swing 25/20/30/25, F&O 15/10/35/40.
Missing metrics are skipped and remaining weights renormalized.
A pillar with <50% coverage is Incomplete and the total is capped at 70.
Fields we do not have (auditor, RPT, SuperTrend, 63d RS, G-Sec, CFO)
stay as — and are never invented.

Band labels are Strong / Good / Mixed / Weak / Poor — never Buy / Sell.
Long-term M is quantitative 12-2 (skip last month), not RSI.
"""

from __future__ import annotations

import logging
import os
import statistics
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

logger = logging.getLogger(__name__)

SCANNER_CACHE_TTL_SECONDS = int(os.getenv("SCANNER_CACHE_TTL_SECONDS", "600"))
SCANNER_MODES = (
    "long_term",
    "swing",
    "high_quality",
    "momentum",
    "value",
    "custom",
    "quality_screen",
)
QUALITY_SCREEN_MIN_PASSES = 4
MCAP_MIN_INR = 500 * 10**7  # ₹500 crore
BANK_LIKE_SECTORS = {"Banking", "NBFC", "Finance", "Insurance"}

# NIFTY 50-style large-cap universe (codebase tickers; TATAMOTORS → TMPV).
NIFTY50_UNIVERSE: Tuple[str, ...] = (
    "ADANIENT", "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT", "AXISBANK",
    "BAJAJ-AUTO", "BAJFINANCE", "BAJAJFINSV", "BEL", "BHARTIARTL",
    "BPCL", "BRITANNIA", "CIPLA", "COALINDIA", "DRREDDY",
    "EICHERMOT", "GRASIM", "HCLTECH", "HDFCBANK", "HDFCLIFE",
    "HEROMOTOCO", "HINDALCO", "HINDUNILVR", "ICICIBANK", "INDUSINDBK",
    "INFY", "ITC", "JIOFIN", "JSWSTEEL", "KOTAKBANK",
    "LT", "M&M", "MARUTI", "NESTLEIND", "NTPC",
    "ONGC", "POWERGRID", "RELIANCE", "SBILIFE", "SBIN",
    "SHRIRAMFIN", "SUNPHARMA", "TATACONSUM", "TMPV", "TATASTEEL",
    "TCS", "TECHM", "TITAN", "TRENT", "ULTRACEMCO", "WIPRO",
)

LARGE_CAPS = set(NIFTY50_UNIVERSE) | {
    "HDFCLIFE", "SBILIFE", "BAJAJFINSV", "TECHM", "INDUSINDBK",
    "HEROMOTOCO", "BPCL", "IOC", "GAIL", "DLF",
    "TATAPOWER", "COALINDIA", "PNB", "BEL", "TATACONSUM",
}

EDUCATION_FILTERS = (
    {
        "id": "roce",
        "label": "ROCE > 15%",
        "applied": False,
        "status": "Not available on this snapshot — shown as —",
    },
    {
        "id": "roe",
        "label": "ROE > 15%",
        "applied": True,
        "status": "Used when ROE is on the quote; otherwise —",
    },
    {
        "id": "de",
        "label": "D/E < 1",
        "applied": True,
        "status": "Used when D/E is on the quote (non-banks); otherwise —",
    },
    {
        "id": "peg",
        "label": "PEG < 1.5",
        "applied": True,
        "status": "Used when PEG is on the quote; otherwise —",
    },
    {
        "id": "pledge",
        "label": "Low promoter pledge",
        "applied": False,
        "status": "Not on this snapshot — shown as —",
    },
)

QUALITY_SCREEN_FILTERS = (
    {
        "id": "mcap",
        "label": "MCap > ₹500 Cr",
        "applied": True,
        "status": "Market cap in ₹",
    },
    {
        "id": "peg",
        "label": "PEG < 1",
        "applied": True,
        "status": "PEG when present",
    },
    {
        "id": "pe_vs_sector",
        "label": "PE < sector PE",
        "applied": True,
        "status": "NSE pdSectorPe when present; else median PE in this batch",
    },
    {
        "id": "roe",
        "label": "ROE > 20%",
        "applied": True,
        "status": "Trailing ROE — not 5-year average",
    },
    {
        "id": "roce",
        "label": "ROCE (5Y avg) > 15%",
        "applied": True,
        "status": "Statements: 5Y avg when 5 years exist; else latest/N-year avg",
    },
    {
        "id": "promoter",
        "label": "Promoter holding > 50%",
        "applied": True,
        "status": "NSE shareholding when the filing is available",
    },
    {
        "id": "sales",
        "label": "Sales growth > 15%",
        "applied": True,
        "status": "3Y CAGR when 4 annual rows exist; else TTM YoY",
    },
    {
        "id": "profit",
        "label": "Profit growth > 15%",
        "applied": True,
        "status": "5Y CAGR when history exists; else available-year CAGR or TTM YoY",
    },
    {
        "id": "pledge",
        "label": "Pledged % < 1",
        "applied": True,
        "status": "NSE shareholding when the filing is available",
    },
    {
        "id": "opm",
        "label": "OPM > 15%",
        "applied": True,
        "status": "Operating margin when present",
    },
    {
        "id": "ps",
        "label": "Price to Sales < 7",
        "applied": True,
        "status": "Trailing P/S when present",
    },
    {
        "id": "ev_ebitda",
        "label": "EV/EBITDA < 20",
        "applied": True,
        "status": "EV/EBITDA when present",
    },
)

CUSTOM_EDUCATION_FILTERS = (
    {
        "id": "minScore",
        "label": "Min BYSEL score",
        "applied": True,
        "status": "Chip on the Custom tab; uses the computed score",
    },
    {
        "id": "rsi",
        "label": "RSI range",
        "applied": True,
        "status": "Skipped when RSI is missing on the quote",
    },
    {
        "id": "dma",
        "label": "Price vs 50/200 DMA",
        "applied": True,
        "status": "Skipped when the DMA is missing",
    },
    {
        "id": "volume",
        "label": "Volume vs average",
        "applied": True,
        "status": "Uses session volume / 3-month average when both exist",
    },
    {
        "id": "pe",
        "label": "PE max",
        "applied": True,
        "status": "Skipped when trailing PE is missing",
    },
    {
        "id": "dayChange",
        "label": "Day-change min",
        "applied": True,
        "status": "Uses the quote percent change",
    },
)

DISCLAIMER = (
    "Not investment advice. Paper practice only. BYSEL is not a broker "
    "and does not place live orders."
)

FORMULA_CHANGED_DATE = "2026-09-14"
INCOMPLETE_PILLAR_COVERAGE = 0.50
INCOMPLETE_TOTAL_CAP = 70
PILLAR_WEIGHTS = {
    "quality": 0.35,
    "valuation": 0.25,
    "trend": 0.20,
    "momentum": 0.20,
}
STYLE_WEIGHTS = {
    "balanced": dict(PILLAR_WEIGHTS),
    "long_term": {"quality": 0.45, "valuation": 0.30, "trend": 0.15, "momentum": 0.10},
    "swing": {"quality": 0.25, "valuation": 0.20, "trend": 0.30, "momentum": 0.25},
    "fno": {"quality": 0.15, "valuation": 0.10, "trend": 0.35, "momentum": 0.40},
}
QUALITY_WEIGHTS = {
    "roce": 0.25,
    "roe": 0.20,
    "de": 0.20,
    "cfo": 0.15,
    "margin": 0.10,
    "gov": 0.10,
}
VALUATION_WEIGHTS = {
    "rel": 0.40,
    "earn": 0.25,
    "fcf": 0.20,
    "growth": 0.15,
}
TREND_WEIGHTS = {
    "ma": 0.30,
    "st": 0.25,
    "hhhl": 0.20,
    "rs": 0.15,
    "volume": 0.10,
}
MOMENTUM_WEIGHTS = {
    "rsi": 0.30,
    "macd": 0.25,
    "ret": 0.25,
    "breadth": 0.20,
}
QM_MOMENTUM_WEIGHTS = {
    "r122": 0.40,
    "smooth": 0.25,
    "h52": 0.20,
    "earn": 0.15,
}
PB_SECTORS = BANK_LIKE_SECTORS
EV_EBITDA_SECTORS = {
    "Metals", "Energy", "Oil & Gas", "OilGas", "Commodities", "Cement",
    "Chemicals", "Mining", "Power", "Steel",
}
PE_SECTORS = {"IT", "FMCG", "Consumer", "Software", "Platforms"}
FORMULA_NOTE = (
    "BYSEL Score is an educational rank from Quality, Valuation, Trend, and Momentum "
    "using only fields we actually have. Missing metrics stay as —. "
    "A pillar with thin coverage is marked Incomplete. "
    "Ranking/analysis only — never Buy/Sell."
)
METRIC_LABELS = {
    "roce": "ROCE",
    "roe": "ROE",
    "roa": "RoA",
    "de": "D/E",
    "debtToEquity": "D/E",
    "cfo": "CFO / PAT",
    "margin": "OP margin trend",
    "gov": "Governance",
    "interestCoverage": "Interest cover",
    "salesCagr": "Sales CAGR",
    "profitCagr": "Profit CAGR",
    "promoterPledge": "Pledge",
    "rel": "Relative multiple",
    "earn": "Earnings yield vs G-Sec",
    "fcf": "FCF yield",
    "growth": "PEG",
    "pe": "PE vs baseline",
    "peg": "PEG",
    "pb": "P/B",
    "evEbitda": "EV/EBITDA",
    "ma": "50/200 DMA",
    "st": "SuperTrend",
    "vs200": "vs 200 DMA",
    "vs50": "vs 50 DMA",
    "cross": "50 vs 200 DMA",
    "hhhl": "HH/HL",
    "rs": "RS vs Nifty 500",
    "week52": "52-week range",
    "fiftyDayAverage": "50 DMA",
    "twoHundredDayAverage": "200 DMA",
    "rsi": "RSI",
    "macd": "MACD",
    "ret": "21d risk-adjusted",
    "breadth": "Up-close breadth",
    "rsNifty": "RS vs Nifty",
    "volume": "Volume",
    "volumeRatio": "Volume",
    "roc": "ROC",
    "r122": "Momentum rank",
    "smooth": "Path smoothness",
    "h52": "52-week closeness",
    "earn": "EPS revision 3m",
}
QUALITY_METRIC_WEIGHTS = {
    "roce": QUALITY_WEIGHTS["roce"],
    "roe": QUALITY_WEIGHTS["roe"],
    "debtToEquity": QUALITY_WEIGHTS["de"],
    "cfo": QUALITY_WEIGHTS["cfo"],
    "margin": QUALITY_WEIGHTS["margin"],
    "gov": QUALITY_WEIGHTS["gov"],
}
VALUATION_METRIC_WEIGHTS = dict(VALUATION_WEIGHTS)
TREND_METRIC_WEIGHTS = dict(TREND_WEIGHTS)
MOMENTUM_METRIC_WEIGHTS = dict(MOMENTUM_WEIGHTS)

_CACHE_LOCK = threading.Lock()
_BUILD_LOCK = threading.Lock()
_SCANNER_CACHE: Dict[str, Tuple[float, Dict[str, Any]]] = {}


def _limit_for_mode(mode: str, requested_limit: int) -> int:
    if mode == "custom":
        return 40
    return min(max(int(requested_limit or 30), 5), 40)


def _cached_scanner_payload(cache_key: str, now: float) -> Optional[Dict[str, Any]]:
    with _CACHE_LOCK:
        cached = _SCANNER_CACHE.get(cache_key)
        if cached is None:
            mode = cache_key.split(":", 1)[0]
            cached = _SCANNER_CACHE.get(mode)
    if cached and (now - cached[0]) < SCANNER_CACHE_TTL_SECONDS:
        payload = dict(cached[1])
        payload["cached"] = True
        return payload
    return None


def _store_all_mode_payloads(
    quotes: Sequence[Dict[str, Any]],
    *,
    requested_limit: int,
    universe_size: int,
) -> Dict[str, Dict[str, Any]]:
    """Score once per mode from the same quotes so tab switches stay cache-hot."""
    now = time.time()
    stored: Dict[str, Dict[str, Any]] = {}
    with _CACHE_LOCK:
        for mode in SCANNER_MODES:
            mode_limit = _limit_for_mode(mode, requested_limit)
            payload = build_scanner_payload(
                quotes,
                mode=mode,
                limit=mode_limit,
                universe_size=universe_size,
            )
            snapshot = dict(payload)
            stored[mode] = snapshot
            _SCANNER_CACHE[mode] = (now, snapshot)
            _SCANNER_CACHE[f"{mode}:{mode_limit}"] = (now, snapshot)
            if mode_limit != requested_limit:
                _SCANNER_CACHE[f"{mode}:{requested_limit}"] = (now, snapshot)
    return stored


def _with_by_mode(payload: Dict[str, Any], stored: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    out = dict(payload)
    out["byMode"] = {
        mode: list(item.get("rows") or [])
        for mode, item in stored.items()
    }
    return out


def _safe_float(value: Any) -> Optional[float]:
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number != number or number in (float("inf"), float("-inf")):  # NaN / ±inf
        return None
    return number


def _safe_int(value: Any) -> Optional[int]:
    number = _safe_float(value)
    if number is None:
        return None
    return int(number)


def normalize_roe_pct(value: Any) -> Optional[float]:
    """Yahoo often sends ROE as 0.18; enricher already stores percent."""
    number = _safe_float(value)
    if number is None:
        return None
    if abs(number) <= 1.5:
        return round(number * 100.0, 2)
    return round(number, 2)


def normalize_de_ratio(value: Any) -> Optional[float]:
    """Yahoo debtToEquity is usually a percent (37.5 → 0.375)."""
    number = _safe_float(value)
    if number is None or number < 0:
        return None
    if number > 5:
        return round(number / 100.0, 3)
    return round(number, 3)


def _first_present(row: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in row and row.get(key) not in (None, "", 0, 0.0):
            return row.get(key)
        if key in row and row.get(key) == 0:
            # Zero can be a real RSI/ROE; allow explicit 0 for those later.
            return row.get(key)
    return None


def _optional_metric(row: Dict[str, Any], *keys: str) -> Optional[float]:
    for key in keys:
        if key not in row:
            continue
        value = row.get(key)
        if value in (None, ""):
            continue
        number = _safe_float(value)
        if number is not None:
            return number
    return None


def sector_for_symbol(symbol: str) -> str:
    key = (symbol or "").strip().upper()
    try:
        from .market_heatmap import SECTOR_STOCKS

        for sector, names in SECTOR_STOCKS.items():
            if key in names:
                return sector
    except Exception:
        pass
    try:
        from .portfolio_scorer import SECTOR_MAP

        return SECTOR_MAP.get(key, "Other")
    except Exception:
        return "Other"


def volume_ratio(volume: Optional[float], avg_volume: Optional[float]) -> Optional[float]:
    if volume is None or avg_volume is None or avg_volume <= 0 or volume <= 0:
        return None
    return round(float(volume) / float(avg_volume), 2)


def renormalized_score(
    values: Dict[str, Optional[float]],
    weights: Dict[str, float],
) -> Optional[float]:
    """Blend available metrics only; renormalize remaining weights."""
    used = {
        key: float(score)
        for key, score in values.items()
        if score is not None and key in weights
    }
    if not used:
        return None
    weight_sum = sum(weights[key] for key in used)
    if weight_sum <= 0:
        return None
    blended = sum(used[key] * weights[key] for key in used) / weight_sum
    return min(max(blended, 0.0), 100.0)


def _metric(value: Optional[float], score: Optional[int]) -> Dict[str, Any]:
    return {"value": value, "score": score, "used": score is not None}


def _score_int(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    return int(round(float(value)))


def top_contributing_metrics(
    metrics: Dict[str, Dict[str, Any]],
    weights: Dict[str, float],
    limit: int = 3,
) -> List[Dict[str, Any]]:
    """Top used metrics by weighted contribution. Missing metrics are skipped."""
    used: List[Tuple[str, float, float, Any]] = []
    for key, weight in weights.items():
        cell = metrics.get(key) or {}
        if not cell.get("used"):
            continue
        score = cell.get("score")
        if score is None:
            continue
        used.append((key, float(score), float(weight), cell.get("value")))
    if not used:
        return []
    weight_sum = sum(item[2] for item in used)
    ranked: List[Dict[str, Any]] = []
    for key, score, weight, value in used:
        contribution = (score * weight / weight_sum) if weight_sum else 0.0
        ranked.append({
            "id": key,
            "label": METRIC_LABELS.get(key, key),
            "value": value,
            "score": int(round(score)),
            "contribution": round(contribution, 1),
        })
    ranked.sort(key=lambda item: (-item["contribution"], -item["score"], item["id"]))
    return ranked[: max(0, int(limit))]


def _pillar_payload(
    score: Optional[int],
    metrics: Dict[str, Dict[str, Any]],
    weights: Dict[str, float],
    *,
    coverage: Optional[float] = None,
    incomplete: bool = False,
) -> Dict[str, Any]:
    return {
        "score": score,
        "colorBand": color_band(score),
        "metrics": metrics,
        "topMetrics": top_contributing_metrics(metrics, weights, limit=3),
        "coverage": None if coverage is None else round(coverage, 2),
        "incomplete": bool(incomplete),
        "status": "incomplete" if incomplete else ("scored" if score is not None else "missing"),
    }


def _linear_band(
    value: Optional[float],
    x0: float,
    x1: float,
    y0: float,
    y1: float,
    *,
    below: Optional[float] = None,
    above: Optional[float] = None,
) -> Optional[float]:
    if value is None:
        return None
    if value <= x0:
        return float(y0 if below is None else below)
    if value >= x1:
        return float(y1 if above is None else above)
    return y0 + (y1 - y0) * (value - x0) / (x1 - x0)


def _style_weights(mode: str) -> Dict[str, float]:
    if mode == "long_term":
        return STYLE_WEIGHTS["long_term"]
    if mode == "swing":
        return STYLE_WEIGHTS["swing"]
    if mode == "fno":
        return STYLE_WEIGHTS["fno"]
    return STYLE_WEIGHTS["balanced"]


def _pillar_coverage(parts: Dict[str, Optional[float]], slots: Sequence[str]) -> Tuple[int, int, float, bool]:
    used = sum(1 for key in slots if parts.get(key) is not None)
    total = len(slots)
    coverage = (used / total) if total else 0.0
    incomplete = used > 0 and coverage < INCOMPLETE_PILLAR_COVERAGE
    return used, total, coverage, incomplete


def band_roce(value: Optional[float]) -> Optional[int]:
    raw = _linear_band(value, 8.0, 30.0, 0.0, 100.0)
    return _score_int(raw)


def band_roe(value: Optional[float]) -> Optional[int]:
    raw = _linear_band(value, 8.0, 28.0, 0.0, 100.0)
    return _score_int(raw)


def band_roa(value: Optional[float]) -> Optional[int]:
    raw = _linear_band(value, 0.6, 2.0, 0.0, 100.0)
    return _score_int(raw)


def band_de(value: Optional[float]) -> Optional[int]:
    raw = _linear_band(value, 0.3, 1.8, 100.0, 20.0)
    return _score_int(raw)


def band_car(value: Optional[float]) -> Optional[int]:
    raw = _linear_band(value, 11.0, 18.0, 0.0, 100.0)
    return _score_int(raw)


def band_cfo_ratio(ratio: Optional[float], *, pat: Optional[float] = None, cfo: Optional[float] = None) -> Optional[int]:
    if pat is not None and cfo is not None:
        if pat < 0 and cfo > 0:
            return 55
        if pat < 0 and cfo < 0:
            return 10
    if ratio is None:
        return None
    raw = _linear_band(ratio, 0.3, 1.2, 0.0, 100.0)
    return _score_int(raw)


def band_margin_trend(delta_bps: Optional[float], current_margin: Optional[float] = None) -> Optional[int]:
    if current_margin is not None and current_margin < 0:
        return 0
    if delta_bps is None:
        return None
    if delta_bps >= 150:
        return 100
    if delta_bps <= -150:
        return 20
    return 60


def band_interest_coverage(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if value >= 8:
        return 100
    if value >= 4:
        return 85
    if value >= 2:
        return 65
    if value >= 1:
        return 40
    return 15


def band_cagr(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if value >= 15:
        return 100
    if value >= 10:
        return 85
    if value >= 5:
        return 65
    if value >= 0:
        return 40
    return 15


def band_cheapness(cheapness: Optional[float]) -> Optional[int]:
    if cheapness is None:
        return None
    if cheapness >= 1.4:
        return 100
    if cheapness < 0.6:
        return 0
    return _score_int(50.0 + 125.0 * (cheapness - 1.0))


def band_earn_spread(spread_pct: Optional[float]) -> Optional[int]:
    raw = _linear_band(spread_pct, -2.0, 6.0, 0.0, 100.0)
    return _score_int(raw)


def band_fcf_yield(yield_pct: Optional[float], sales_growth: Optional[float] = None) -> Optional[int]:
    if yield_pct is None:
        return None
    if yield_pct < 0:
        return 15 if (sales_growth is not None and sales_growth >= 20) else 0
    raw = _linear_band(yield_pct, 0.0, 8.0, 0.0, 100.0)
    return _score_int(raw)


def band_peg(peg: Optional[float], *, negative_earnings: bool = False) -> Optional[int]:
    if negative_earnings:
        return 10
    if peg is None:
        return None
    if peg <= 0.8:
        return 100
    if peg > 2.0:
        return 10
    return _score_int(100.0 - 80.0 * (peg - 0.8) / 1.2)


def band_pe_vs_median(ratio: Optional[float]) -> Optional[int]:
    if ratio is None or ratio <= 0:
        return None
    return band_cheapness(1.0 / ratio)


def band_dma_pct(last: Optional[float], dma: Optional[float]) -> Optional[int]:
    if not last or not dma or dma <= 0:
        return None
    dist = (last - dma) / dma * 100.0
    if dist >= 10:
        return 100
    if dist >= 3:
        return 85
    if dist >= 0:
        return 70
    if dist >= -3:
        return 45
    return 20


def band_ma_stack(
    last: Optional[float],
    fifty: Optional[float],
    two_hundred: Optional[float],
    *,
    fifty_rising: Optional[bool] = None,
) -> Optional[int]:
    if last is None or (fifty is None and two_hundred is None):
        return None
    above_50 = fifty is not None and last > fifty
    below_50 = fifty is not None and last < fifty
    above_200 = two_hundred is not None and last > two_hundred
    below_200 = two_hundred is not None and last < two_hundred
    fifty_above_200 = (
        fifty is not None and two_hundred is not None and fifty > two_hundred
    )
    fifty_below_200 = (
        fifty is not None and two_hundred is not None and fifty < two_hundred
    )
    if above_50 and fifty_above_200 and fifty_rising is not False:
        return 100 if fifty_rising is True or fifty_rising is None else 70
    if above_200 and (fifty is None or not fifty_above_200):
        return 70
    if fifty is not None and two_hundred is not None:
        low_ma, high_ma = (fifty, two_hundred) if fifty <= two_hundred else (two_hundred, fifty)
        if low_ma <= last <= high_ma:
            return 45
    if below_200 and (fifty_rising is False or fifty is None):
        return 20
    if below_50 and fifty_below_200:
        return 5
    if below_200:
        return 20
    if below_50:
        return 5
    return 45


def band_supertrend(
    *,
    above: Optional[bool] = None,
    days_above: Optional[int] = None,
    fresh_flip_up: Optional[bool] = None,
    fresh_flip_down: Optional[bool] = None,
) -> Optional[int]:
    if fresh_flip_down:
        return 5
    if above is False:
        return 15
    if fresh_flip_up:
        return 75
    if above is True and days_above is not None and days_above >= 10:
        return 95
    if above is True:
        return 90
    return None


def band_hhhl(structure: Optional[str]) -> Optional[int]:
    if not structure:
        return None
    key = str(structure).strip().lower()
    if key in {"hhhl", "higher_highs_higher_lows"}:
        return 100
    if key in {"mixed"}:
        return 50
    if key in {"lhll", "lower_highs_lower_lows"}:
        return 10
    return None


def band_rs_63(rs: Optional[float]) -> Optional[int]:
    raw = _linear_band(rs, -0.15, 0.20, 0.0, 100.0)
    return _score_int(raw)


def band_volume_trend(
    vol_ratio: Optional[float],
    *,
    uptrend: Optional[bool] = None,
) -> Optional[int]:
    if vol_ratio is None or uptrend is None:
        return None
    if uptrend and vol_ratio >= 1.3:
        return 100
    if uptrend and vol_ratio < 0.8:
        return 35
    if not uptrend and vol_ratio >= 1.3:
        return 20
    return 55


def band_rsi(value: Optional[float], *, trend_score: Optional[int] = None) -> Optional[int]:
    if value is None:
        return None
    if value < 30 and trend_score is not None and trend_score >= 60:
        return 80
    if value < 30:
        return 40
    if value <= 55:
        return int(round(40 + 2 * (value - 30)))
    if value <= 68:
        return 90
    if value <= 75:
        return 70
    return 35


def band_macd_state(
    macd: Optional[float],
    *,
    signal: Optional[float] = None,
    histogram: Optional[float] = None,
    histogram_prev: Optional[float] = None,
) -> Optional[int]:
    expanding = (
        histogram is not None
        and histogram_prev is not None
        and histogram > histogram_prev
    )
    shrinking = (
        histogram is not None
        and histogram_prev is not None
        and histogram < histogram_prev
    )
    if histogram is not None and histogram > 0 and expanding:
        return 100
    if signal is not None and macd is not None and macd > signal and shrinking:
        return 65
    if signal is not None and macd is not None and macd < signal and expanding:
        return 45
    if macd is not None and signal is not None and macd < 0 and signal < 0 and shrinking:
        return 10
    if macd is None:
        return None
    # Sign-only fallback — do not invent expanding/shrinking.
    return 65 if macd > 0 else 45


def band_risk_adjusted(ratio: Optional[float]) -> Optional[int]:
    raw = _linear_band(ratio, -1.0, 1.5, 0.0, 100.0)
    return _score_int(raw)


def band_up_closes(up_closes: Optional[int]) -> Optional[int]:
    if up_closes is None:
        return None
    if up_closes >= 8:
        return 90
    if up_closes >= 5:
        return 55
    return 20


def color_band(score: Optional[int]) -> str:
    """Strong teal, Good blue, Mixed grey, Weak amber, Poor red."""
    if score is None:
        return "none"
    if score >= 80:
        return "teal"
    if score >= 65:
        return "blue"
    if score >= 50:
        return "grey"
    if score >= 35:
        return "amber"
    return "red"


def score_label_token(score: Optional[int]) -> str:
    if score is None:
        return "insufficient"
    if score >= 80:
        return "strong"
    if score >= 65:
        return "good"
    if score >= 50:
        return "mixed"
    if score >= 35:
        return "weak"
    return "poor"


def conviction_label(score: Optional[int]) -> str:
    token = score_label_token(score)
    return {
        "strong": "Strong",
        "good": "Good",
        "mixed": "Mixed",
        "weak": "Weak",
        "poor": "Poor",
        "insufficient": "Insufficient data",
    }[token]


def score_quality(
    *,
    symbol: str = "",
    market_cap: Optional[float] = None,
    roe: Optional[float] = None,
    roce: Optional[float] = None,
    debt_to_equity: Optional[float] = None,
    interest_coverage: Optional[float] = None,
    sales_cagr: Optional[float] = None,
    profit_cagr: Optional[float] = None,
    pledge: Optional[float] = None,
    promoter: Optional[float] = None,
    fcf: Optional[float] = None,
    sector: str = "Other",
    roa: Optional[float] = None,
    capital_adequacy: Optional[float] = None,
    cfo: Optional[float] = None,
    pat: Optional[float] = None,
    margin: Optional[float] = None,
    margin_avg_3y: Optional[float] = None,
    auditor_change: Optional[bool] = None,
    qualified_opinion: Optional[bool] = None,
    rpt_revenue_pct: Optional[float] = None,
    asm_gsm: Optional[bool] = None,
    promoter_delta_4q: Optional[float] = None,
) -> Tuple[Optional[int], List[str], Dict[str, Optional[float]]]:
    """Quality from available fundamentals only. Never invent missing filings."""
    _ = symbol, market_cap, sales_cagr, profit_cagr, fcf, promoter
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}
    bank_like = sector in BANK_LIKE_SECTORS

    if bank_like:
        parts["roce"] = band_roa(roa)
        notes.append(f"RoA {roa:.2f}%" if roa is not None else "RoA — (bank; ROCE skipped)")
    else:
        parts["roce"] = band_roce(roce)
        notes.append(f"ROCE {roce:.0f}%" if roce is not None else "ROCE —")

    parts["roe"] = band_roe(roe)
    notes.append(f"ROE {roe:.0f}%" if roe is not None else "ROE —")

    if bank_like:
        parts["de"] = band_car(capital_adequacy)
        notes.append(
            f"CAR {capital_adequacy:.1f}%" if capital_adequacy is not None else "CAR — (bank; D/E skipped)"
        )
    else:
        de_score = band_de(debt_to_equity)
        if de_score is not None and interest_coverage is not None and interest_coverage < 2:
            de_score = int(round(de_score * 0.6))
            notes.append(f"D/E {debt_to_equity:.2f} (interest cover {interest_coverage:.1f}×0.6)")
        else:
            notes.append(f"D/E {debt_to_equity:.2f}" if debt_to_equity is not None else "D/E —")
        parts["de"] = de_score

    cfo_ratio = None
    if cfo is not None and pat is not None and pat != 0:
        cfo_ratio = cfo / pat
    parts["cfo"] = band_cfo_ratio(cfo_ratio, pat=pat, cfo=cfo)
    notes.append(f"CFO/PAT {cfo_ratio:.2f}" if cfo_ratio is not None else "CFO/PAT —")

    delta_bps = None
    if margin is not None and margin_avg_3y is not None:
        delta_bps = (margin - margin_avg_3y) * 100.0
    parts["margin"] = band_margin_trend(delta_bps, current_margin=margin)
    if margin is not None and margin < 0:
        notes.append(f"OP margin {margin:.1f}% (negative)")
    elif delta_bps is not None:
        notes.append(f"OP margin vs 3Y {delta_bps:+.0f} bps")
    else:
        notes.append("OP margin trend —")

    gov_inputs = any(
        value is not None
        for value in (
            pledge, auditor_change, qualified_opinion, rpt_revenue_pct, asm_gsm, promoter_delta_4q,
        )
    )
    if gov_inputs:
        gov = 80
        if pledge is not None and pledge >= 20:
            gov -= 25
            notes.append(f"Pledge {pledge:.0f}% (−25)")
        elif pledge is not None and pledge >= 5:
            gov -= 10
            notes.append(f"Pledge {pledge:.0f}% (−10)")
        elif pledge is not None:
            notes.append(f"Pledge {pledge:.0f}%")
        if auditor_change:
            gov -= 15
            notes.append("Auditor change (−15)")
        if qualified_opinion:
            gov -= 40
            notes.append("Qualified/adverse opinion (−40)")
        if rpt_revenue_pct is not None and rpt_revenue_pct >= 10:
            gov -= 15
            notes.append(f"RPT revenue {rpt_revenue_pct:.0f}% (−15)")
        if asm_gsm:
            gov -= 30
            notes.append("ASM/GSM (−30)")
        if promoter_delta_4q is not None and promoter_delta_4q < -5:
            gov -= 10
            notes.append(f"Promoter {promoter_delta_4q:.1f} pp (−10)")
        parts["gov"] = max(gov, 0)
    else:
        parts["gov"] = None
        notes.append("Governance —")

    blended = renormalized_score(parts, QUALITY_WEIGHTS)
    if blended is None:
        return None, notes, parts
    return int(round(blended)), notes, parts


def _primary_multiple(
    *,
    sector: str,
    pe: Optional[float],
    pb: Optional[float],
    ev_ebitda: Optional[float],
    ps: Optional[float],
    sales_growth: Optional[float],
    earnings: Optional[float],
) -> Tuple[Optional[float], str]:
    if sector in PB_SECTORS:
        return pb, "PB"
    if sector in EV_EBITDA_SECTORS:
        return ev_ebitda, "EV/EBITDA"
    if earnings is not None and earnings <= 0:
        if sales_growth is not None and sales_growth > 20:
            return ps, "PS"
        return None, "PE skipped (loss-making)"
    if sector in PE_SECTORS or pe is not None:
        return pe, "PE"
    if ev_ebitda is not None:
        return ev_ebitda, "EV/EBITDA"
    if pb is not None:
        return pb, "PB"
    return None, "multiple"


def score_value(
    *,
    pe: Optional[float],
    sector_pe: Optional[float] = None,
    pe_median_5y: Optional[float] = None,
    peg: Optional[float] = None,
    pb: Optional[float] = None,
    ev_ebitda: Optional[float] = None,
    sector: str = "Other",
    sector_pb: Optional[float] = None,
    sector_ev_ebitda: Optional[float] = None,
    own_median: Optional[float] = None,
    eps: Optional[float] = None,
    last: Optional[float] = None,
    gsec_10y: Optional[float] = None,
    fcf: Optional[float] = None,
    market_cap: Optional[float] = None,
    sales_growth: Optional[float] = None,
    profit_cagr: Optional[float] = None,
    price_to_sales: Optional[float] = None,
) -> Tuple[Optional[int], List[str], Dict[str, Optional[float]]]:
    """Valuation vs sector/own history. Never force PE on banks or loss-makers."""
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    multiple, multiple_name = _primary_multiple(
        sector=sector,
        pe=pe,
        pb=pb,
        ev_ebitda=ev_ebitda,
        ps=price_to_sales,
        sales_growth=sales_growth,
        earnings=eps,
    )
    sector_median = None
    if multiple_name == "PB":
        sector_median = sector_pb
    elif multiple_name == "EV/EBITDA":
        sector_median = sector_ev_ebitda
    elif multiple_name in {"PE", "PS"}:
        sector_median = sector_pe
    history_median = own_median if own_median and own_median > 0 else pe_median_5y

    sector_score = None
    history_score = None
    if multiple is not None and multiple > 0 and sector_median and sector_median > 0:
        sector_score = band_cheapness(sector_median / multiple)
        notes.append(f"{multiple_name} {multiple:.1f} vs sector {sector_median:.1f}")
    else:
        notes.append(f"{multiple_name} vs sector —")
    if multiple is not None and multiple > 0 and history_median and history_median > 0:
        history_score = band_cheapness(history_median / multiple)
        notes.append(f"{multiple_name} vs own 5Y {history_median:.1f}")
    else:
        notes.append(f"{multiple_name} vs own 5Y —")
    if sector_score is not None and history_score is not None:
        parts["rel"] = 0.6 * sector_score + 0.4 * history_score
    elif sector_score is not None:
        parts["rel"] = float(sector_score)
    elif history_score is not None:
        parts["rel"] = float(history_score)
    else:
        parts["rel"] = None

    if eps is not None and last and last > 0 and gsec_10y is not None:
        spread = (eps / last) * 100.0 - gsec_10y
        parts["earn"] = band_earn_spread(spread)
        notes.append(f"EY spread {spread:+.1f} pp vs G-Sec")
    else:
        parts["earn"] = None
        notes.append("EY vs G-Sec —")

    if fcf is not None and market_cap and market_cap > 0:
        fcf_yield = (fcf / market_cap) * 100.0
        parts["fcf"] = band_fcf_yield(fcf_yield, sales_growth)
        notes.append(f"FCF yield {fcf_yield:.1f}%")
    else:
        parts["fcf"] = None
        notes.append("FCF yield —")

    growth_peg = peg
    if growth_peg is None and pe is not None and pe > 0 and profit_cagr is not None:
        growth_peg = pe / max(profit_cagr, 1.0)
    negative_eps = eps is not None and eps < 0
    parts["growth"] = band_peg(growth_peg, negative_earnings=negative_eps)
    notes.append(f"PEG {growth_peg:.1f}" if growth_peg is not None else "PEG —")

    blended = renormalized_score(parts, VALUATION_WEIGHTS)
    if blended is None:
        return None, notes, parts
    return int(round(blended)), notes, parts


def score_trend(
    *,
    last: Optional[float],
    fifty_day: Optional[float],
    two_hundred: Optional[float],
    week52_high: Optional[float] = None,
    week52_low: Optional[float] = None,
    hhhl: Optional[float] = None,
    fifty_rising: Optional[bool] = None,
    supertrend_above: Optional[bool] = None,
    supertrend_days: Optional[int] = None,
    supertrend_flip_up: Optional[bool] = None,
    supertrend_flip_down: Optional[bool] = None,
    hhhl_structure: Optional[str] = None,
    rs_63: Optional[float] = None,
    volume_ratio: Optional[float] = None,
) -> Tuple[Optional[int], List[str], Dict[str, Optional[float]]]:
    """Trend from DMA stack plus optional SuperTrend / HHHL / 63d RS. No invented series."""
    _ = week52_high, week52_low
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    parts["ma"] = band_ma_stack(last, fifty_day, two_hundred, fifty_rising=fifty_rising)
    if parts["ma"] is not None:
        notes.append(f"DMA stack {int(parts['ma'])}")
    else:
        notes.append("50/200 DMA —")

    parts["st"] = band_supertrend(
        above=supertrend_above,
        days_above=supertrend_days,
        fresh_flip_up=supertrend_flip_up,
        fresh_flip_down=supertrend_flip_down,
    )
    notes.append("SuperTrend present" if parts["st"] is not None else "SuperTrend —")

    structure = hhhl_structure
    if structure is None and hhhl is not None:
        if hhhl > 0:
            structure = "hhhl"
        elif hhhl < 0:
            structure = "lhll"
        else:
            structure = "mixed"
    parts["hhhl"] = band_hhhl(structure)
    notes.append("HH/HL present" if parts["hhhl"] is not None else "HH/HL —")

    parts["rs"] = band_rs_63(rs_63)
    notes.append(f"RS 63d {rs_63:+.1%}" if rs_63 is not None else "RS vs Nifty 500 —")

    uptrend = None
    if last is not None and (fifty_day or two_hundred):
        uptrend = bool(
            (fifty_day and last > fifty_day) or (two_hundred and last > two_hundred)
        )
    parts["volume"] = band_volume_trend(volume_ratio, uptrend=uptrend)
    notes.append(
        f"vol {volume_ratio:.1f}x" if volume_ratio is not None and parts["volume"] is not None else "vol trend —"
    )

    blended = renormalized_score(parts, TREND_WEIGHTS)
    if blended is None:
        return None, notes, parts
    return int(round(blended)), notes, parts


def score_momentum(
    *,
    last: Optional[float] = None,
    fifty_day: Optional[float] = None,
    two_hundred: Optional[float] = None,
    rsi: Optional[float] = None,
    vol_ratio: Optional[float] = None,
    pct_change: Optional[float] = None,
    macd: Optional[float] = None,
    rs_vs_nifty: Optional[float] = None,
    delivery_pct: Optional[float] = None,
    roc: Optional[float] = None,
    trend_score: Optional[int] = None,
    macd_signal: Optional[float] = None,
    macd_hist: Optional[float] = None,
    macd_hist_prev: Optional[float] = None,
    ret_21: Optional[float] = None,
    vol_21: Optional[float] = None,
    up_closes: Optional[int] = None,
    oi_build: Optional[str] = None,
    fno_mode: bool = False,
) -> Tuple[Optional[int], List[str], Dict[str, Optional[float]]]:
    """Momentum: usable RSI, honest MACD, optional 21d ratio / breadth. No invented series."""
    _ = last, fifty_day, two_hundred, vol_ratio, pct_change, rs_vs_nifty, delivery_pct, roc
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    parts["rsi"] = band_rsi(rsi, trend_score=trend_score)
    notes.append(f"RSI {rsi:.0f}" if rsi is not None else "RSI —")

    parts["macd"] = band_macd_state(
        macd,
        signal=macd_signal,
        histogram=macd_hist,
        histogram_prev=macd_hist_prev,
    )
    notes.append("MACD present" if parts["macd"] is not None else "MACD —")

    ratio = None
    if ret_21 is not None:
        ratio = ret_21 / max(vol_21 if vol_21 is not None else 0.01, 0.01)
    parts["ret"] = band_risk_adjusted(ratio)
    notes.append(f"21d ratio {ratio:.2f}" if ratio is not None else "21d risk-adjusted —")

    if fno_mode and oi_build:
        build = str(oi_build).strip().lower()
        if build in {"long_build", "long"}:
            parts["breadth"] = 90
            notes.append("OI long build-up")
        elif build in {"short_build", "short"}:
            parts["breadth"] = 15
            notes.append("OI short build-up")
        else:
            parts["breadth"] = 40
            notes.append("OI unwinding")
    else:
        parts["breadth"] = band_up_closes(up_closes)
        notes.append(f"{up_closes}/10 up closes" if up_closes is not None else "Up-close breadth —")

    blended = renormalized_score(parts, MOMENTUM_WEIGHTS)
    if blended is None:
        return None, notes, parts
    return int(round(blended)), notes, parts


def score_risk(
    *,
    debt_to_equity: Optional[float],
    pledge: Optional[float],
    vol_ratio: Optional[float],
    sector: str = "Other",
) -> Tuple[Optional[int], str, List[str]]:
    """Separate risk readout. None when debt/pledge/vol are all missing."""
    notes: List[str] = []
    points: List[int] = []

    if debt_to_equity is not None and sector not in BANK_LIKE_SECTORS:
        if debt_to_equity < 0.5:
            points.append(22)
        elif debt_to_equity < 1.0:
            points.append(38)
        elif debt_to_equity < 1.5:
            points.append(58)
        else:
            points.append(78)
        notes.append(f"D/E {debt_to_equity:.2f}")
    elif sector in BANK_LIKE_SECTORS:
        notes.append("D/E n/a (bank)")
    else:
        notes.append("D/E —")

    if pledge is not None:
        if pledge < 5:
            points.append(20)
        elif pledge < 20:
            points.append(48)
        else:
            points.append(80)
        notes.append(f"Pledge {pledge:.0f}%")
    else:
        notes.append("Pledge —")

    if vol_ratio is not None and vol_ratio >= 1.8:
        points.append(55)
        notes.append(f"Vol {vol_ratio:.1f}x")
    elif vol_ratio is None:
        notes.append("Vol —")

    if not points:
        return None, "Risk —", notes

    score = min(max(int(round(sum(points) / len(points))), 0), 100)
    if score >= 60:
        label = "Elevated risk inputs"
    elif score >= 40:
        label = "Mixed risk inputs"
    else:
        label = "Lower risk inputs"
    return score, label, notes


def stance_labels(score: Optional[int]) -> List[str]:
    """Educational conviction only — never Buy / Hold / Avoid."""
    label = conviction_label(score)
    return [label] if label else []


def practice_setup(
    row: Dict[str, Any],
    momentum_score: Optional[int] = None,
) -> Optional[Dict[str, Any]]:
    """Paper education levels for swing cards. Labeled not-advice."""
    last = _safe_float(row.get("last"))
    if last is None or last <= 0:
        return None
    fifty = _safe_float(row.get("fiftyDayAverage"))
    two_hundred = _safe_float(row.get("twoHundredDayAverage"))
    rsi = _safe_float(row.get("rsi"))
    vol_r = _safe_float(row.get("volumeRatio"))
    setup_type: Optional[str] = None
    title: Optional[str] = None

    near_fifty = bool(fifty and fifty > 0 and abs(last - fifty) / fifty <= 0.02)
    above_dmas = bool(
        fifty and two_hundred and last >= fifty and last >= two_hundred
    )
    volume_pop = vol_r is not None and vol_r >= 1.5
    rsi_pullback = rsi is not None and 40 <= rsi <= 65

    if rsi_pullback and (near_fifty or (fifty and last <= fifty * 1.02)):
        setup_type, title = "pullback", "Pullback · RSI 40–65 zone"
    elif near_fifty and not volume_pop:
        setup_type, title = "pullback", "Pullback near 50 DMA"
    elif above_dmas and volume_pop:
        setup_type, title = "breakout", "Breakout above 50/200 DMA"
    elif fifty and last >= fifty * 1.02 and volume_pop:
        setup_type, title = "breakout", "Breakout with volume"
    elif fifty and last >= fifty:
        setup_type, title = "breakout", "Above 50 DMA"
    elif rsi_pullback:
        setup_type, title = "pullback", "Pullback · RSI 40–65 zone"
    if not setup_type:
        return None

    if setup_type == "pullback":
        stop = round(min(last * 0.98, (fifty * 0.97) if fifty else last * 0.98), 2)
        t1 = round(last * 1.03, 2)
        t2 = round(last * 1.06, 2)
    else:
        stop = round((fifty * 0.99) if fifty else last * 0.97, 2)
        t1 = round(last * 1.04, 2)
        t2 = round(last * 1.08, 2)
    if stop >= last:
        stop = round(last * 0.98, 2)
    risk = abs(last - stop)
    reward = abs(t1 - last)
    rr = round(reward / risk, 2) if risk > 0 else None
    return {
        "kind": setup_type,
        "setupType": setup_type,
        "title": title,
        "entry": round(last, 2),
        "stop": stop,
        "target": t1,
        "t1": t1,
        "t2": t2,
        "riskReward": rr,
        "momentumScore": momentum_score,
        "note": "Paper — not advice. Practice levels only.",
        "winRate": None,
        "winRateNote": "n/a until we have journal data",
    }


def _weighted_bysel_score(
    quality: Optional[int],
    valuation: Optional[int],
    trend: Optional[int],
    momentum: Optional[int],
    *,
    mode: str = "balanced",
    incomplete: bool = False,
) -> Optional[int]:
    blended = renormalized_score(
        {
            "quality": float(quality) if quality is not None else None,
            "valuation": float(valuation) if valuation is not None else None,
            "trend": float(trend) if trend is not None else None,
            "momentum": float(momentum) if momentum is not None else None,
        },
        _style_weights(mode),
    )
    if blended is None:
        return None
    score = int(round(blended))
    if incomplete:
        score = min(score, INCOMPLETE_TOTAL_CAP)
    return score


def detect_anomalies(row: Dict[str, Any]) -> List[Dict[str, str]]:
    """Flag only events we can compute. Never invent promoter-selling or related-party."""
    flags: List[Dict[str, str]] = []
    vol_r = _safe_float(row.get("volumeRatio"))
    if vol_r is not None and vol_r > 2.0:
        flags.append({
            "id": "unusual_volume",
            "label": "Unusual volume",
            "detail": f"{vol_r:.1f}× average",
        })
    pledge = _safe_float(row.get("pledge"))
    if pledge is not None and pledge > 0:
        flags.append({
            "id": "pledging",
            "label": "Pledging",
            "detail": f"Pledge {pledge:.0f}%",
        })
    margin = _safe_float(row.get("marginPct") if row.get("marginPct") is not None else row.get("margin"))
    if margin is not None:
        flags.append({
            "id": "margin",
            "label": "Margin",
            "detail": f"Margin {margin:.1f}%",
        })
    return flags


def _soft_filter_multiplier(mode: str, row: Dict[str, Any]) -> float:
    """Apply textbook filters only when the field exists."""
    factor = 1.0
    roe = row.get("roe")
    de = row.get("debtToEquity")
    peg = row.get("peg")
    sector = row.get("sector") or "Other"
    rsi = row.get("rsi")
    last = row.get("last")
    fifty = row.get("fiftyDayAverage")
    vol_r = row.get("volumeRatio")

    if mode == "custom":
        return 1.0
    if mode == "long_term":
        if roe is not None and roe < 15:
            factor *= 0.85
        if de is not None and sector not in BANK_LIKE_SECTORS and de >= 1.0:
            factor *= 0.90
        if peg is not None and peg >= 1.5:
            factor *= 0.90
        return factor

    two_hundred = row.get("twoHundredDayAverage")
    matched = False
    if last and fifty and fifty > 0 and last > fifty:
        matched = True
    if last and two_hundred and two_hundred > 0 and last > two_hundred:
        matched = True
    if rsi is not None and 40 <= rsi <= 65:
        matched = True
    if vol_r is not None and vol_r > 1.5:
        matched = True
    if not matched:
        factor *= 0.88
    return factor


def missing_fields(row: Dict[str, Any]) -> List[str]:
    missing: List[str] = []
    for key, label in (
        ("roe", "roe"),
        ("roce", "roce"),
        ("debtToEquity", "debtToEquity"),
        ("cfo", "cfo"),
        ("pat", "pat"),
        ("marginAvg3y", "marginAvg3y"),
        ("peg", "peg"),
        ("pb", "pb"),
        ("evEbitda", "evEbitda"),
        ("peMedian5y", "peMedian5y"),
        ("eps", "eps"),
        ("fcf", "fcf"),
        ("rsi", "rsi"),
        ("macd", "macd"),
        ("hhhl", "hhhl"),
        ("rs63", "rs63"),
        ("ret21", "ret21"),
        ("r122", "r122"),
        ("smooth", "smooth"),
        ("h52", "h52"),
        ("supertrendAbove", "supertrend"),
        ("gsec10y", "gsec"),
        ("pe", "pe"),
        ("fiftyDayAverage", "fiftyDayAverage"),
        ("twoHundredDayAverage", "twoHundredDayAverage"),
    ):
        if row.get(key) is None:
            missing.append(label)
    if row.get("pledge") is None:
        missing.append("pledge")
    return missing


def _fmt_pillar(name: str, score: Optional[int], note: str) -> str:
    if score is None:
        return f"{name} — ({note})"
    return f"{name} {score} ({note})"


def explain_score(
    bysel: Optional[int],
    quality: Optional[int],
    valuation: Optional[int],
    trend: Optional[int],
    momentum: Optional[int],
    q_notes: Sequence[str],
    v_notes: Sequence[str],
    t_notes: Sequence[str],
    m_notes: Sequence[str],
    risk_label: str,
    top_bits: Optional[Sequence[str]] = None,
    missing: Optional[Sequence[str]] = None,
) -> str:
    """2–4 sentence educational summary. Not investment advice."""
    if bysel is None:
        return (
            "Insufficient fields to compute a BYSEL Score — pillars stay as —. "
            "Missing metrics are skipped rather than invented. "
            "This is an educational readout, not investment advice."
        )
    q_bit = next((n for n in q_notes if not n.endswith("—")), "only available quality metrics")
    v_bit = next((n for n in v_notes if not n.endswith("—")), "valuation incomplete")
    t_bit = next((n for n in t_notes if not n.endswith("—")), "trend incomplete")
    m_bit = next((n for n in m_notes if not n.endswith("—")), "momentum incomplete")
    sentences = [
        (
            f"BYSEL Score is {bysel}/100 from available fields: "
            f"{_fmt_pillar('Quality', quality, q_bit)}, "
            f"{_fmt_pillar('Valuation', valuation, v_bit)}, "
            f"{_fmt_pillar('Trend', trend, t_bit)}, "
            f"{_fmt_pillar('Momentum', momentum, m_bit)}."
        )
    ]
    if top_bits:
        sentences.append(
            "Largest contributions among metrics we actually have: "
            + "; ".join(list(top_bits)[:4])
            + "."
        )
    skip_note = "Missing metrics are skipped rather than invented"
    if missing:
        shown = ", ".join(list(missing)[:5])
        sentences.append(f"{skip_note} ({shown} stay as —).")
    else:
        sentences.append(f"{skip_note} — we do not invent ROCE, pledge, MACD, or delivery.")
    sentences.append(
        f"{risk_label}. Educational labels only, not Strong Buy / Buy / Hold / Avoid. "
        "Not investment advice."
    )
    return " ".join(sentences[:4])


def score_row(
    row: Dict[str, Any],
    mode: str,
    sector_pe: Optional[float],
    nifty_change: Optional[float] = None,
) -> Dict[str, Any]:
    gsec_10y = _safe_float(row.get("gsec10y"))
    if gsec_10y is None:
        raw_gsec = os.getenv("INDIA_GSEC_10Y", "").strip()
        gsec_10y = _safe_float(raw_gsec) if raw_gsec else None
    quality, q_notes, q_parts = score_quality(
        symbol=str(row.get("symbol") or ""),
        market_cap=_safe_float(row.get("marketCap")),
        roe=row.get("roe"),
        roce=row.get("roce"),
        debt_to_equity=row.get("debtToEquity"),
        interest_coverage=row.get("interestCoverage"),
        sales_cagr=row.get("salesCagr"),
        profit_cagr=row.get("profitCagr"),
        pledge=row.get("pledge"),
        promoter=row.get("promoter"),
        fcf=row.get("fcf"),
        sector=str(row.get("sector") or "Other"),
        roa=row.get("roa"),
        capital_adequacy=row.get("capitalAdequacy"),
        cfo=row.get("cfo"),
        pat=row.get("pat"),
        margin=row.get("marginPct"),
        margin_avg_3y=row.get("marginAvg3y"),
        auditor_change=row.get("auditorChange"),
        qualified_opinion=row.get("qualifiedOpinion"),
        rpt_revenue_pct=row.get("rptRevenuePct"),
        asm_gsm=row.get("asmGsm"),
        promoter_delta_4q=row.get("promoterDelta4q"),
    )
    valuation, v_notes, v_parts = score_value(
        pe=row.get("pe"),
        sector_pe=sector_pe,
        pe_median_5y=row.get("peMedian5y"),
        peg=row.get("peg"),
        pb=row.get("pb"),
        ev_ebitda=row.get("evEbitda"),
        sector=str(row.get("sector") or "Other"),
        sector_pb=row.get("sectorPb"),
        sector_ev_ebitda=row.get("sectorEvEbitda"),
        own_median=row.get("ownMultipleMedian5y"),
        eps=row.get("eps"),
        last=row.get("last"),
        gsec_10y=gsec_10y,
        fcf=row.get("fcf"),
        market_cap=row.get("marketCap"),
        sales_growth=row.get("salesCagr") if row.get("salesCagr") is not None else row.get("revenueGrowth"),
        profit_cagr=row.get("profitCagr"),
        price_to_sales=row.get("priceToSales"),
    )
    trend, t_notes, t_parts = score_trend(
        last=row.get("last"),
        fifty_day=row.get("fiftyDayAverage"),
        two_hundred=row.get("twoHundredDayAverage"),
        week52_high=row.get("fiftyTwoWeekHigh"),
        week52_low=row.get("fiftyTwoWeekLow"),
        hhhl=row.get("hhhl"),
        fifty_rising=row.get("fiftyRising"),
        supertrend_above=row.get("supertrendAbove"),
        supertrend_days=row.get("supertrendDays"),
        supertrend_flip_up=row.get("supertrendFlipUp"),
        supertrend_flip_down=row.get("supertrendFlipDown"),
        hhhl_structure=row.get("hhhlStructure"),
        rs_63=row.get("rs63"),
        volume_ratio=row.get("volumeRatio"),
    )
    rs_vs_nifty = None
    if nifty_change is not None and row.get("pctChange") is not None:
        rs_vs_nifty = float(row["pctChange"]) - float(nifty_change)
    from .quantitative_momentum import score_momentum_qm, uses_qm_momentum

    use_qm = uses_qm_momentum(mode)
    if use_qm:
        momentum, m_notes, m_parts = score_momentum_qm(
            r122_percentile=row.get("r122Pct"),
            smoothness=row.get("smooth"),
            h52=row.get("h52"),
            earn_percentile=row.get("earnPct"),
        )
    else:
        momentum, m_notes, m_parts = score_momentum(
            last=row.get("last"),
            rsi=row.get("rsi"),
            vol_ratio=row.get("volumeRatio"),
            pct_change=row.get("pctChange"),
            macd=row.get("macd"),
            rs_vs_nifty=rs_vs_nifty,
            delivery_pct=row.get("deliveryPct"),
            roc=row.get("roc"),
            trend_score=trend,
            macd_signal=row.get("macdSignal"),
            macd_hist=row.get("macdHist"),
            macd_hist_prev=row.get("macdHistPrev"),
            ret_21=row.get("ret21"),
            vol_21=row.get("vol21"),
            up_closes=row.get("upCloses"),
            oi_build=row.get("oiBuild"),
            fno_mode=mode == "fno",
        )
    risk_score, risk_label, risk_notes = score_risk(
        debt_to_equity=row.get("debtToEquity"),
        pledge=row.get("pledge"),
        vol_ratio=row.get("volumeRatio"),
        sector=str(row.get("sector") or "Other"),
    )
    missing = missing_fields(row)
    q_used, q_total, q_cov, q_incomplete = _pillar_coverage(q_parts, list(QUALITY_WEIGHTS))
    v_used, v_total, v_cov, v_incomplete = _pillar_coverage(v_parts, list(VALUATION_WEIGHTS))
    t_used, t_total, t_cov, t_incomplete = _pillar_coverage(t_parts, list(TREND_WEIGHTS))
    momentum_weights = QM_MOMENTUM_WEIGHTS if use_qm else MOMENTUM_WEIGHTS
    m_used, m_total, m_cov, m_incomplete = _pillar_coverage(m_parts, list(momentum_weights))
    _ = q_used, q_total, v_used, v_total, t_used, t_total, m_used, m_total
    any_incomplete = q_incomplete or v_incomplete or t_incomplete or m_incomplete
    bysel = _weighted_bysel_score(
        quality, valuation, trend, momentum,
        mode=mode,
        incomplete=any_incomplete,
    )
    rank_score = bysel if bysel is not None else 0
    rank_score = min(max(int(round(rank_score * _soft_filter_multiplier(mode, row))), 0), 100)
    label = conviction_label(bysel)
    quality_metrics = {
        "roce": _metric(row.get("roa") if str(row.get("sector") or "") in BANK_LIKE_SECTORS else row.get("roce"), _score_int(q_parts.get("roce"))),
        "roe": _metric(row.get("roe"), _score_int(q_parts.get("roe"))),
        "debtToEquity": _metric(row.get("debtToEquity"), _score_int(q_parts.get("de"))),
        "cfo": _metric(row.get("cfo"), _score_int(q_parts.get("cfo"))),
        "margin": _metric(row.get("marginPct"), _score_int(q_parts.get("margin"))),
        "gov": _metric(row.get("pledge"), _score_int(q_parts.get("gov"))),
    }
    valuation_metrics = {
        "rel": _metric(row.get("pe"), _score_int(v_parts.get("rel"))),
        "earn": _metric(row.get("eps"), _score_int(v_parts.get("earn"))),
        "fcf": _metric(row.get("fcf"), _score_int(v_parts.get("fcf"))),
        "growth": _metric(row.get("peg"), _score_int(v_parts.get("growth"))),
    }
    trend_metrics = {
        "ma": _metric(row.get("fiftyDayAverage"), _score_int(t_parts.get("ma"))),
        "st": _metric(row.get("supertrendAbove"), _score_int(t_parts.get("st"))),
        "hhhl": _metric(row.get("hhhl"), _score_int(t_parts.get("hhhl"))),
        "rs": _metric(row.get("rs63"), _score_int(t_parts.get("rs"))),
        "volume": _metric(row.get("volumeRatio"), _score_int(t_parts.get("volume"))),
    }
    if use_qm:
        momentum_metrics = {
            "r122": _metric(row.get("r122Pct"), _score_int(m_parts.get("r122"))),
            "smooth": _metric(row.get("smooth"), _score_int(m_parts.get("smooth"))),
            "h52": _metric(row.get("h52"), _score_int(m_parts.get("h52"))),
            "earn": _metric(row.get("earnPct"), _score_int(m_parts.get("earn"))),
        }
    else:
        momentum_metrics = {
            "rsi": _metric(row.get("rsi"), _score_int(m_parts.get("rsi"))),
            "macd": _metric(row.get("macd"), _score_int(m_parts.get("macd"))),
            "ret": _metric(row.get("ret21"), _score_int(m_parts.get("ret"))),
            "breadth": _metric(row.get("upCloses"), _score_int(m_parts.get("breadth"))),
        }
    pillars = {
        "quality": _pillar_payload(
            quality, quality_metrics, QUALITY_METRIC_WEIGHTS,
            coverage=q_cov, incomplete=q_incomplete,
        ),
        "valuation": _pillar_payload(
            valuation, valuation_metrics, VALUATION_METRIC_WEIGHTS,
            coverage=v_cov, incomplete=v_incomplete,
        ),
        "trend": _pillar_payload(
            trend, trend_metrics, TREND_METRIC_WEIGHTS,
            coverage=t_cov, incomplete=t_incomplete,
        ),
        "momentum": _pillar_payload(
            momentum, momentum_metrics, momentum_weights,
            coverage=m_cov, incomplete=m_incomplete,
        ),
    }
    top_bits: List[str] = []
    for name in ("quality", "valuation", "trend", "momentum"):
        for metric in (pillars[name].get("topMetrics") or [])[:1]:
            label_txt = metric.get("label") or metric.get("id")
            top_bits.append(f"{name.title()} {label_txt} {metric.get('score')}")
    explanation = explain_score(
        bysel, quality, valuation, trend, momentum,
        q_notes, v_notes, t_notes, m_notes, risk_label,
        top_bits=top_bits,
        missing=missing,
    )
    token = score_label_token(bysel)
    setup = practice_setup(row, momentum_score=momentum) if mode == "swing" else None
    anomalies = detect_anomalies(row)
    for flag in row.get("qmFlags") or []:
        if isinstance(flag, dict) and flag.get("id"):
            anomalies.append(flag)
    return {
        "quality": quality,
        "valuation": valuation,
        "value": valuation,
        "trend": trend,
        "momentum": momentum,
        "risk": risk_score,
        "riskLabel": risk_label,
        "riskNotes": risk_notes,
        "byselScore": bysel,
        "bysel_score": bysel,
        "overall": rank_score,
        "colorBand": color_band(bysel),
        "convictionLabel": label,
        "score_label": token,
        "scoreLabel": token,
        "styleMode": "long_term" if mode == "long_term" else ("swing" if mode == "swing" else ("fno" if mode == "fno" else "balanced")),
        "styleWeights": _style_weights(mode),
        "incomplete": any_incomplete,
        "formulaChangedDate": FORMULA_CHANGED_DATE,
        "explanation": explanation,
        "ai_summary": explanation,
        "aiSummary": explanation,
        "stance": [label],
        "pillars": pillars,
        "setup": setup,
        "why": explanation,
        "missing": missing,
        "anomalies": anomalies,
    }


def _sector_pe_map(rows: Iterable[Dict[str, Any]]) -> Dict[str, float]:
    buckets: Dict[str, List[float]] = {}
    for row in rows:
        pe = row.get("pe")
        sector = str(row.get("sector") or "Other")
        if pe is None or pe <= 0:
            continue
        buckets.setdefault(sector, []).append(float(pe))
    return {
        sector: round(statistics.median(values), 2)
        for sector, values in buckets.items()
        if values
    }


def normalize_quote_row(raw: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    symbol = str(raw.get("symbol") or "").strip().upper()
    if not symbol or symbol in {"NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT", "NIFTYBANK"}:
        return None
    last = _safe_float(raw.get("last") or raw.get("ltp") or raw.get("price"))
    if last is None or last <= 0:
        return None

    pe = _optional_metric(raw, "trailingPE", "pe")
    if pe is not None and pe <= 0:
        pe = None

    roe = normalize_roe_pct(_first_present(raw, "roe", "returnOnEquity", "roe_pct"))
    # Treat explicit None-equivalent: _first_present treats 0 as present.
    if raw.get("roe") in (None, "") and raw.get("returnOnEquity") in (None, "") and raw.get("roe_pct") in (None, ""):
        roe = None

    roce_raw = _first_present(raw, "roce", "returnOnCapitalEmployed")
    roce = normalize_roe_pct(roce_raw) if roce_raw not in (None, "") else None

    de = normalize_de_ratio(_first_present(raw, "debtToEquity", "debt_to_equity"))
    if raw.get("debtToEquity") in (None, "") and raw.get("debt_to_equity") in (None, ""):
        de = None

    peg = _optional_metric(raw, "peg", "pegRatio")
    rsi = _optional_metric(raw, "rsi", "rsi14")
    pb = _optional_metric(raw, "pb", "priceToBook")
    ev_ebitda = _optional_metric(raw, "evEbitda", "enterpriseToEbitda")
    price_to_sales = _optional_metric(raw, "priceToSales", "priceToSalesTrailing12Months")
    revenue_growth = normalize_roe_pct(_first_present(raw, "revenueGrowth", "salesGrowth"))
    if raw.get("revenueGrowth") in (None, "") and raw.get("salesGrowth") in (None, ""):
        revenue_growth = None
    earnings_growth = normalize_roe_pct(_first_present(raw, "earningsGrowth", "profitGrowth"))
    if raw.get("earningsGrowth") in (None, "") and raw.get("profitGrowth") in (None, ""):
        earnings_growth = None
    opm_raw = _first_present(raw, "operatingMargins", "marginPct", "margin")
    opm = normalize_roe_pct(opm_raw) if opm_raw not in (None, "") else None
    interest_coverage = _optional_metric(raw, "interestCoverage")
    sales_cagr = _optional_metric(raw, "salesCagr")
    profit_cagr = _optional_metric(raw, "profitCagr")
    pe_median_5y = _optional_metric(raw, "peMedian5y")
    delivery_pct = _optional_metric(raw, "deliveryPct", "delivery_pct")
    macd = _optional_metric(raw, "macd")
    fcf = _optional_metric(raw, "fcf", "freeCashflow")
    volume = _safe_float(raw.get("volume") or raw.get("regularMarketVolume"))
    avg_volume = _safe_float(raw.get("avgVolume") or raw.get("averageDailyVolume3Month"))

    name = str(raw.get("name") or raw.get("companyName") or "").strip()
    if not name:
        try:
            from .market_data import get_stock_name

            name = get_stock_name(symbol)
        except Exception:
            name = symbol

    return {
        "symbol": symbol,
        "name": name or symbol,
        "last": round(last, 2),
        "pctChange": round(_safe_float(raw.get("pctChange")) or 0.0, 2),
        "pe": round(pe, 2) if pe is not None else None,
        "marketCap": _safe_int(raw.get("marketCap")),
        "volume": _safe_int(volume),
        "avgVolume": _safe_int(avg_volume),
        "volumeRatio": volume_ratio(volume, avg_volume),
        "fiftyDayAverage": _optional_metric(raw, "fiftyDayAverage"),
        "twoHundredDayAverage": _optional_metric(raw, "twoHundredDayAverage"),
        "roe": roe,
        "roce": roce,
        "debtToEquity": de,
        "peg": peg,
        "pb": pb,
        "evEbitda": ev_ebitda,
        "priceToSales": price_to_sales,
        "revenueGrowth": revenue_growth,
        "earningsGrowth": earnings_growth,
        "interestCoverage": interest_coverage,
        "salesCagr": sales_cagr,
        "profitCagr": profit_cagr,
        "peMedian5y": pe_median_5y,
        "deliveryPct": delivery_pct,
        "macd": macd,
        "fcf": fcf,
        "hhhl": _optional_metric(raw, "hhhl"),
        "pledge": _optional_metric(raw, "pledge"),
        "cfo": _optional_metric(raw, "cfo", "operatingCashflow"),
        "pat": _optional_metric(raw, "pat", "netIncomeToCommon"),
        "eps": _optional_metric(raw, "eps", "trailingEps", "epsTrailingTwelveMonths"),
        "roa": normalize_roe_pct(_first_present(raw, "roa", "returnOnAssets")) if _first_present(raw, "roa", "returnOnAssets") not in (None, "") else None,
        "capitalAdequacy": _optional_metric(raw, "capitalAdequacy", "car"),
        "marginAvg3y": _optional_metric(raw, "marginAvg3y"),
        "rs63": _optional_metric(raw, "rs63"),
        "ret21": _optional_metric(raw, "ret21"),
        "vol21": _optional_metric(raw, "vol21"),
        "gsec10y": _optional_metric(raw, "gsec10y"),
        "supertrendAbove": raw.get("supertrendAbove"),
        "hhhlStructure": raw.get("hhhlStructure"),
        "upCloses": _safe_int(raw.get("upCloses")),
        "r122": _optional_metric(raw, "r122"),
        "r122Pct": _optional_metric(raw, "r122Pct"),
        "r1m": _optional_metric(raw, "r1m"),
        "r1mPct": _optional_metric(raw, "r1mPct"),
        "smooth": _optional_metric(raw, "smooth"),
        "h52": _optional_metric(raw, "h52"),
        "earnPct": _optional_metric(raw, "earnPct"),
        "epsRevision3m": _optional_metric(raw, "epsRevision3m"),
        "qmRank": _safe_int(raw.get("qmRank")),
        "listedSessions": _safe_int(raw.get("listedSessions")),
        "qmFlags": raw.get("qmFlags") if isinstance(raw.get("qmFlags"), list) else [],
        "asmGsm": raw.get("asmGsm"),
        "promoter": _optional_metric(raw, "promoter"),
        "nseSectorPe": _optional_metric(raw, "nseSectorPe"),
        "salesCagrYears": _safe_int(raw.get("salesCagrYears")),
        "profitCagrYears": _safe_int(raw.get("profitCagrYears")),
        "roceAvg": _optional_metric(raw, "roceAvg"),
        "roceAvgYears": _safe_int(raw.get("roceAvgYears")),
        "marginPct": opm if opm is not None else _optional_metric(raw, "marginPct", "margin"),
        "rsi": rsi,
        "fiftyTwoWeekHigh": _optional_metric(raw, "fiftyTwoWeekHigh"),
        "fiftyTwoWeekLow": _optional_metric(raw, "fiftyTwoWeekLow"),
        "sector": sector_for_symbol(symbol),
    }


def scanner_universe() -> List[str]:
    """NIFTY 50 + default watchlist catalog. Never walks the full NSE list."""
    symbols: List[str] = list(NIFTY50_UNIVERSE)
    seen = set(symbols)
    try:
        from .market_data import DEFAULT_SYMBOLS, INDIAN_STOCKS

        catalog = set(INDIAN_STOCKS.keys())
        symbols = [sym for sym in symbols if sym in catalog]
        seen = set(symbols)
        for sym in DEFAULT_SYMBOLS:
            key = str(sym or "").strip().upper()
            if key and key in catalog and key not in seen:
                symbols.append(key)
                seen.add(key)
    except Exception as exc:
        logger.warning("scanner.universe_catalog_unavailable reason=%s", exc)

    try:
        from .market_heatmap import _HEATMAP_CACHE

        payload = _HEATMAP_CACHE.get("data") or {}
        movers: List[Tuple[float, str]] = []
        for sector in payload.get("sectors") or []:
            for stock in sector.get("stocks") or []:
                sym = str(stock.get("symbol") or "").strip().upper()
                if not sym or sym in seen:
                    continue
                change = abs(_safe_float(stock.get("pctChange") or stock.get("changePercent")) or 0.0)
                movers.append((change, sym))
        movers.sort(reverse=True)
        for _, sym in movers[:8]:
            symbols.append(sym)
            seen.add(sym)
    except Exception:
        pass

    return symbols


def _screen_check(
    rule_id: str,
    label: str,
    *,
    applied: bool,
    value: Optional[float],
    passed: Optional[bool],
    note: str,
) -> Dict[str, Any]:
    if not applied:
        status = "skip"
    elif value is None or passed is None:
        status = "skip"
    elif passed:
        status = "pass"
    else:
        status = "fail"
    return {
        "id": rule_id,
        "label": label,
        "status": status,
        "applied": applied,
        "value": round(value, 2) if isinstance(value, (int, float)) else None,
        "note": note,
    }


def evaluate_quality_screen(
    row: Dict[str, Any],
    sector_pe: Optional[float] = None,
) -> Dict[str, Any]:
    """Popular textbook checklist. Missing Yahoo fields are skipped, never invented."""
    mcap = _safe_float(row.get("marketCap"))
    peg = _safe_float(row.get("peg"))
    pe = _safe_float(row.get("pe"))
    roe = _safe_float(row.get("roe"))
    sales_cagr = _safe_float(row.get("salesCagr"))
    sales_ttm = _safe_float(row.get("revenueGrowth"))
    sales = sales_cagr if sales_cagr is not None else sales_ttm
    profit_cagr = _safe_float(row.get("profitCagr"))
    profit_ttm = _safe_float(row.get("earningsGrowth"))
    profit = profit_cagr if profit_cagr is not None else profit_ttm
    opm = _safe_float(row.get("marginPct"))
    ps = _safe_float(row.get("priceToSales"))
    ev_ebitda = _safe_float(row.get("evEbitda"))
    roce_avg = _safe_float(row.get("roceAvg"))
    roce_latest = _safe_float(row.get("roce"))
    roce = roce_avg if roce_avg is not None else roce_latest
    roce_years = _safe_int(row.get("roceAvgYears"))
    promoter = _safe_float(row.get("promoter"))
    pledge = _safe_float(row.get("pledge"))
    nse_sector_pe = _safe_float(row.get("nseSectorPe"))
    industry_pe = nse_sector_pe if nse_sector_pe is not None else sector_pe
    sales_years = _safe_int(row.get("salesCagrYears"))
    profit_years = _safe_int(row.get("profitCagrYears"))

    checks = [
        _screen_check(
            "mcap",
            "MCap > ₹500 Cr",
            applied=True,
            value=(mcap / 10**7) if mcap is not None else None,
            passed=(mcap > MCAP_MIN_INR) if mcap is not None else None,
            note="Market cap in ₹",
        ),
        _screen_check(
            "peg",
            "PEG < 1",
            applied=True,
            value=peg,
            passed=(peg < 1) if peg is not None else None,
            note="PEG when present",
        ),
        _screen_check(
            "pe_vs_sector",
            "PE < sector PE",
            applied=True,
            value=pe,
            passed=(pe < industry_pe) if pe is not None and industry_pe is not None else None,
            note=(
                "NSE sector PE"
                if nse_sector_pe is not None
                else "Sector median in this batch, not official industry PE"
            ),
        ),
        _screen_check(
            "roe",
            "ROE > 20%",
            applied=True,
            value=roe,
            passed=(roe > 20) if roe is not None else None,
            note="Trailing ROE — not 5-year average",
        ),
        _screen_check(
            "roce",
            "ROCE (5Y avg) > 15%" if (roce_years or 0) >= 5 else "ROCE > 15%",
            applied=True,
            value=roce,
            passed=(roce > 15) if roce is not None else None,
            note=(
                f"Statements — {roce_years}-year average"
                if roce_avg is not None and roce_years
                else "Statements — latest year, not 5-year average"
                if roce_latest is not None
                else "Statements when enough years exist"
            ),
        ),
        _screen_check(
            "promoter",
            "Promoter holding > 50%",
            applied=True,
            value=promoter,
            passed=(promoter > 50) if promoter is not None else None,
            note="NSE shareholding when the filing is available",
        ),
        _screen_check(
            "sales",
            "Sales growth > 15%",
            applied=True,
            value=sales,
            passed=(sales > 15) if sales is not None else None,
            note=(
                f"{sales_years}Y sales CAGR"
                if sales_cagr is not None and sales_years
                else "TTM YoY — not 3-year CAGR"
            ),
        ),
        _screen_check(
            "profit",
            "Profit growth > 15%",
            applied=True,
            value=profit,
            passed=(profit > 15) if profit is not None else None,
            note=(
                f"{profit_years}Y profit CAGR"
                if profit_cagr is not None and profit_years
                else "TTM YoY — not 5-year CAGR"
            ),
        ),
        _screen_check(
            "pledge",
            "Pledged % < 1",
            applied=True,
            value=pledge,
            passed=(pledge < 1) if pledge is not None else None,
            note="NSE shareholding when the filing is available",
        ),
        _screen_check(
            "opm",
            "OPM > 15%",
            applied=True,
            value=opm,
            passed=(opm > 15) if opm is not None else None,
            note="Operating margin when present",
        ),
        _screen_check(
            "ps",
            "Price to Sales < 7",
            applied=True,
            value=ps,
            passed=(ps < 7) if ps is not None else None,
            note="Trailing P/S when present",
        ),
        _screen_check(
            "ev_ebitda",
            "EV/EBITDA < 20",
            applied=True,
            value=ev_ebitda,
            passed=(ev_ebitda < 20) if ev_ebitda is not None else None,
            note="EV/EBITDA when present",
        ),
    ]
    passed = sum(1 for item in checks if item["status"] == "pass")
    failed = sum(1 for item in checks if item["status"] == "fail")
    skipped = sum(1 for item in checks if item["status"] == "skip")
    matches = failed == 0 and passed >= QUALITY_SCREEN_MIN_PASSES
    return {
        "checks": checks,
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "matches": matches,
        "summary": f"{passed} passed · {failed} failed · {skipped} skipped",
    }


def _education(mode: str) -> Dict[str, Any]:
    if mode == "swing":
        title = "Swing — today's setups"
        summary = (
            "Prefers price above 50/200 DMA, RSI 40–65, and volume > 1.5× average "
            "when those fields exist. Entry / SL / Target are practice levels, not advice. "
            "Paper only — risk about 1–2% of practice capital per idea."
        )
        risk_note = "Paper practice. Size so one idea risks about 1–2% of the practice book."
    else:
        title = "Long-term — quality + fair value"
        summary = (
            "Ranks on BYSEL Score. Textbook screens (ROCE>15, ROE>15, D/E<1, PEG<1.5) "
            "are shown for education; we apply only filters we can compute from available fields."
        )
        risk_note = "Heuristic rank, not a buy list. Missing ROCE/pledge/delivery stay as —."
    if mode == "high_quality":
        title = "High Quality"
        summary = "Sorted by the Quality pillar from available ROE/ROCE/D/E only. Missing metrics are skipped."
    elif mode == "momentum":
        title = "QM Leaders"
        summary = (
            "Quality Momentum is sequential, not a fifth pillar: drop names that fail "
            "the quality bar (ROE ≥ 12%, D/E ≤ 1.5 and interest cover ≥ 2 for non-financials, "
            "pledge < 20%, no ASM/GSM), then rank survivors on 12-2 + smoothness + 52-week closeness. "
            "Quality and momentum stay separate numbers. "
            "Badges: Quality Momentum · Quality, trend mixed · Speculative momentum · Weak. "
            "Not official Nifty200 Quality 30 / Momentum 30 and not a buy list. "
            "Missing ROE fails the bar; missing pledge/D/E is skipped, not invented."
        )
    elif mode == "value":
        title = "Value"
        summary = "Sorted by the Valuation pillar (PE vs sector-ish / PEG when present)."
    elif mode == "custom":
        title = "Custom — chips we can actually apply"
        summary = (
            "Filter the scored universe with fields we already have: "
            "min BYSEL score, RSI, price vs 50/200 DMA, volume vs average, PE max, and day change. "
            "Missing RSI/DMA/PE skips that name instead of inventing a value. "
            "This is not a 40-filter builder."
        )
        risk_note = "Heuristic rank by BYSEL Score. Not a buy list. Unusual volume is flagged only at >2× average."
    elif mode == "quality_screen":
        title = "Quality screen — popular textbook checklist"
        summary = (
            "Often shared as a 'multibagger formula'. It is a filter, not a forecast — "
            "passing these checks does not mean a stock will multiply. "
            "Statement history fills 3Y sales CAGR, multi-year profit CAGR, and ROCE when enough years exist. "
            "NSE shareholding fills promoter % and pledge when the filing is available. "
            "Sector PE prefers NSE pdSectorPe, else the median PE in this batch. "
            "A missing year or filing stays —; we do not scrape Screener.in. "
            f"A name is listed first if it fails none of the available checks and passes at least {QUALITY_SCREEN_MIN_PASSES}. "
            "If none do, we show the closest names by checks passed — not a pass of the full checklist."
        )
        risk_note = (
            "Paper practice shortlist only. Not a buy list and not a multibagger prediction."
        )
    if mode == "quality_screen":
        filters = list(QUALITY_SCREEN_FILTERS)
    elif mode == "custom":
        filters = list(CUSTOM_EDUCATION_FILTERS)
    else:
        filters = list(EDUCATION_FILTERS)
    return {
        "title": title,
        "summary": summary,
        "filters": filters,
        "scoreGuide": FORMULA_NOTE + (
            " Labels: 80–100 Strong; 65–79 Good; 50–64 Mixed; 35–49 Weak; 0–34 Poor. "
            "Never Strong Buy / Buy / Hold / Avoid."
        ),
        "formulaChangedDate": FORMULA_CHANGED_DATE,
        "riskNote": risk_note,
        "disclaimer": DISCLAIMER,
        "dataLimits": (
            "Universe is NIFTY 50 plus the default watchlist catalog "
            "(and a few heatmap movers if that cache is already warm). "
            "ROCE, promoter pledge, or RSI are often missing on the quote snapshot."
        ),
    }


def build_scanner_payload(
    quotes: Sequence[Dict[str, Any]],
    mode: str = "long_term",
    limit: int = 30,
    *,
    universe_size: int = 0,
) -> Dict[str, Any]:
    mode_key = mode if mode in SCANNER_MODES else "long_term"
    limit = min(max(int(limit or 30), 5), 40)

    rows = []
    nifty_change = None
    for raw in quotes or []:
        if not isinstance(raw, dict):
            continue
        sym = str(raw.get("symbol") or "").strip().upper()
        if sym in {"NIFTY50", "NIFTY", "^NSEI"} and nifty_change is None:
            nifty_change = _safe_float(raw.get("pctChange"))
        normalized = normalize_quote_row(raw)
        if normalized:
            rows.append(normalized)

    from .quantitative_momentum import (
        apply_qm_to_rows,
        cached_closes,
        evaluate_quality_gates,
        nifty_absolute_regime,
        qm_style_badge,
        sequential_qm_sleeve,
    )

    qm_regime = nifty_absolute_regime(cached_closes("NIFTY50") or cached_closes("NIFTY") or cached_closes("^NSEI"))
    apply_qm_to_rows(rows, regime=qm_regime)

    sector_pe = _sector_pe_map(rows)
    scored: List[Dict[str, Any]] = []
    for row in rows:
        sector_median = sector_pe.get(str(row.get("sector") or "Other"))
        scores = score_row(
            row,
            mode_key,
            sector_median,
            nifty_change=nifty_change,
        )
        quality_screen = evaluate_quality_screen(row, sector_median)
        scored.append({
            "symbol": row["symbol"],
            "name": row["name"],
            "last": row["last"],
            "pctChange": row["pctChange"],
            "byselScore": scores["byselScore"],
            "quality": scores["quality"],
            "valuation": scores["valuation"],
            "value": scores["valuation"],
            "trend": scores["trend"],
            "momentum": scores["momentum"],
            "risk": scores["risk"],
            "riskLabel": scores["riskLabel"],
            "overall": scores["overall"],
            "colorBand": scores.get("colorBand") or color_band(scores.get("byselScore")),
            "convictionLabel": scores["convictionLabel"],
            "score_label": scores["score_label"],
            "scoreLabel": scores["scoreLabel"],
            "explanation": scores["explanation"],
            "ai_summary": scores["ai_summary"],
            "aiSummary": scores["aiSummary"],
            "bysel_score": scores["bysel_score"],
            "stance": scores["stance"],
            "pillars": scores["pillars"],
            "setup": scores["setup"],
            "why": scores["why"],
            "metrics": {
                "pe": row.get("pe"),
                "roe": row.get("roe"),
                "roce": row.get("roce"),
                "debtToEquity": row.get("debtToEquity"),
                "peg": row.get("peg"),
                "rsi": row.get("rsi"),
                "fiftyDayAverage": row.get("fiftyDayAverage"),
                "twoHundredDayAverage": row.get("twoHundredDayAverage"),
                "volumeRatio": row.get("volumeRatio"),
                "sector": row.get("sector"),
                "sectorPe": sector_median,
                "pledge": row.get("pledge"),
                "promoter": row.get("promoter"),
                "marginPct": row.get("marginPct"),
                "marketCap": row.get("marketCap"),
                "priceToSales": row.get("priceToSales"),
                "evEbitda": row.get("evEbitda"),
                "revenueGrowth": row.get("revenueGrowth"),
                "earningsGrowth": row.get("earningsGrowth"),
                "salesCagr": row.get("salesCagr"),
                "profitCagr": row.get("profitCagr"),
                "nseSectorPe": row.get("nseSectorPe"),
                "roceAvg": row.get("roceAvg"),
                "r122": row.get("r122"),
                "r122Pct": row.get("r122Pct"),
                "h52": row.get("h52"),
                "smooth": row.get("smooth"),
                "qmRank": row.get("qmRank"),
                "advInr": row.get("advInr"),
            },
            "qmRank": row.get("qmRank"),
            "qualityGate": evaluate_quality_gates(
                roe=row.get("roe"),
                debt_to_equity=row.get("debtToEquity"),
                interest_coverage=row.get("interestCoverage"),
                pledge=row.get("pledge"),
                asm_gsm=row.get("asmGsm"),
                sector=str(row.get("sector") or "Other"),
                eps_years=row.get("profitCagrYears") or row.get("epsYears"),
                loss_streak=row.get("lossStreak"),
            ),
            "qmBadge": qm_style_badge(scores.get("quality"), scores.get("momentum")) or "",
            "qualityMomentum": (
                qm_style_badge(scores.get("quality"), scores.get("momentum"))
                == "Quality Momentum"
            ),
            "qualityScreen": quality_screen,
            "missing": scores["missing"],
            "anomalies": scores.get("anomalies") or [],
            "incomplete": scores.get("incomplete") or False,
            "styleMode": scores.get("styleMode") or "balanced",
        })

    def _rank(item: Dict[str, Any]) -> Tuple[int, int, str]:
        screen = item.get("qualityScreen") or {}
        if mode_key == "high_quality":
            key = item.get("quality")
        elif mode_key == "momentum":
            key = item.get("momentum")
        elif mode_key == "value":
            key = item.get("valuation")
        elif mode_key == "custom":
            key = item.get("byselScore")
        elif mode_key == "quality_screen":
            key = item.get("byselScore")
        else:
            key = item.get("overall")
        return (
            -int(screen.get("passed") or 0) if mode_key == "quality_screen" else 0,
            -int(key or 0),
            str(item.get("symbol") or ""),
        )

    scored.sort(key=_rank)
    if mode_key == "swing":
        with_setup = [item for item in scored if item.get("setup")]
        cap = min(max(int(limit), 5), 15)
        shortlist = with_setup[:cap]
    elif mode_key == "momentum":
        sleeve = sequential_qm_sleeve(scored, limit=min(max(int(limit), 5), 30))
        shortlist = sleeve
    elif mode_key == "custom":
        shortlist = scored[: min(max(int(limit), 5), 40)]
    elif mode_key == "quality_screen":
        matched = [item for item in scored if (item.get("qualityScreen") or {}).get("matches")]
        if matched:
            shortlist = matched[:limit]
        else:
            closest = [
                item for item in scored
                if int((item.get("qualityScreen") or {}).get("passed") or 0) > 0
            ]
            shortlist = closest[:limit]
    else:
        shortlist = scored[:limit]

    return {
        "mode": mode_key,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheTtlSeconds": SCANNER_CACHE_TTL_SECONDS,
        "universe": "NIFTY50 + watchlist catalog",
        "universeSize": universe_size or len(rows),
        "quotedCount": len(rows),
        "disclaimer": DISCLAIMER,
        "formulaNote": FORMULA_NOTE,
        "formulaChangedDate": FORMULA_CHANGED_DATE,
        "qmRegime": qm_regime,
        "education": _education(mode_key),
        "rows": shortlist,
    }


def get_market_scanner(
    mode: str = "long_term",
    limit: int = 30,
    force_refresh: bool = False,
) -> Dict[str, Any]:
    mode_key = (mode or "long_term").strip().lower()
    if mode_key not in SCANNER_MODES:
        mode_key = "long_term"
    limit = min(max(int(limit or 30), 5), 40)
    cache_key = f"{mode_key}:{limit}"
    now = time.time()

    if not force_refresh:
        cached = _cached_scanner_payload(cache_key, now)
        if cached:
            stored: Dict[str, Dict[str, Any]] = {}
            with _CACHE_LOCK:
                for sibling in SCANNER_MODES:
                    item = _SCANNER_CACHE.get(sibling) or _SCANNER_CACHE.get(
                        f"{sibling}:{_limit_for_mode(sibling, limit)}"
                    )
                    if item:
                        stored[sibling] = item[1]
            return _with_by_mode(cached, stored)

    with _BUILD_LOCK:
        now = time.time()
        if not force_refresh:
            cached = _cached_scanner_payload(cache_key, now)
            if cached:
                stored = {}
                with _CACHE_LOCK:
                    for sibling in SCANNER_MODES:
                        item = _SCANNER_CACHE.get(sibling) or _SCANNER_CACHE.get(
                            f"{sibling}:{_limit_for_mode(sibling, limit)}"
                        )
                        if item:
                            stored[sibling] = item[1]
                return _with_by_mode(cached, stored)

        symbols = scanner_universe()
        quotes: List[Dict[str, Any]] = []
        try:
            from .market_data import fetch_quotes

            quotes = fetch_quotes(
                symbols,
                max_age_seconds=180,
                batch_size=max(50, len(symbols)),
                yf_threads=True,
                individual_fallback=False,
            ) or []
            try:
                from .quality_fundamentals import cached_nse_quality, schedule_nse_quality_fill

                merged_quotes: List[Dict[str, Any]] = []
                for quote in quotes:
                    if not isinstance(quote, dict):
                        continue
                    extra = cached_nse_quality(str(quote.get("symbol") or ""))
                    if extra:
                        row = dict(quote)
                        for key, value in extra.items():
                            if value not in (None, "") and row.get(key) in (None, "", 0, 0.0):
                                row[key] = value
                        merged_quotes.append(row)
                    else:
                        merged_quotes.append(quote)
                quotes = merged_quotes
                schedule_nse_quality_fill(symbols, limit=12)
                try:
                    from .quantitative_momentum import schedule_qm_history_fill

                    schedule_qm_history_fill(["NIFTY50", *symbols], limit=12)
                except Exception as exc:
                    logger.debug("scanner.qm_fill_schedule_failed reason=%s", exc)
            except Exception as exc:
                logger.debug("scanner.nse_quality_overlay_failed reason=%s", exc)
        except TypeError:
            try:
                from .market_data import fetch_quotes

                quotes = fetch_quotes(symbols) or []
            except Exception as exc:
                logger.warning("scanner.quotes_failed reason=%s", exc)
                quotes = []
        except Exception as exc:
            logger.warning("scanner.quotes_failed reason=%s", exc)
            quotes = []

        stored = _store_all_mode_payloads(
            quotes,
            requested_limit=limit,
            universe_size=len(symbols),
        )
        payload = dict(stored.get(mode_key) or build_scanner_payload(
            quotes,
            mode=mode_key,
            limit=limit,
            universe_size=len(symbols),
        ))
        try:
            persist_daily_score_snapshots(payload.get("rows") or [])
        except Exception as exc:
            logger.warning("scanner.snapshot_persist_failed reason=%s", exc)
        payload["cached"] = False
        return _with_by_mode(payload, stored)


def persist_daily_score_snapshots(rows: Sequence[Dict[str, Any]]) -> None:
    """Upsert today's BYSEL Score per symbol. create_all table; no Alembic."""
    if not rows:
        return
    try:
        from .database.db import ByselScoreSnapshotModel, SessionLocal
    except Exception as exc:
        logger.warning("scanner.snapshot_db_unavailable reason=%s", exc)
        return

    today = datetime.now(timezone.utc).date()
    db = SessionLocal()
    try:
        for row in rows:
            if not isinstance(row, dict):
                continue
            symbol = str(row.get("symbol") or "").strip().upper()
            score = row.get("byselScore")
            if not symbol or score is None:
                continue
            existing = (
                db.query(ByselScoreSnapshotModel)
                .filter(
                    ByselScoreSnapshotModel.symbol == symbol,
                    ByselScoreSnapshotModel.snapshot_date == today,
                )
                .first()
            )
            quality = row.get("quality")
            valuation = row.get("valuation") if row.get("valuation") is not None else row.get("value")
            trend = row.get("trend")
            momentum = row.get("momentum")
            if existing is None:
                db.add(
                    ByselScoreSnapshotModel(
                        symbol=symbol,
                        snapshot_date=today,
                        bysel_score=int(score),
                        quality=quality,
                        valuation=valuation,
                        trend=trend,
                        momentum=momentum,
                    )
                )
            else:
                existing.bysel_score = int(score)
                existing.quality = quality
                existing.valuation = valuation
                existing.trend = trend
                existing.momentum = momentum
        db.commit()
    except Exception as exc:
        db.rollback()
        logger.warning("scanner.snapshot_persist_failed reason=%s", exc)
    finally:
        db.close()


def _normalize_xray_symbol(symbol: str) -> str:
    key = (symbol or "").strip().upper()
    for prefix in ("NSE:", "BSE:"):
        if key.startswith(prefix):
            key = key[len(prefix):]
    if key.endswith(".NS") or key.endswith(".BO"):
        key = key[:-3]
    return key.strip()


def get_symbol_xray(symbol: str) -> Optional[Dict[str, Any]]:
    """Score a single symbol with the same BYSEL Score path as the scanner."""
    key = _normalize_xray_symbol(symbol)
    if not key:
        return None
    quotes: List[Dict[str, Any]] = []
    try:
        from .market_data import fetch_quotes

        quotes = fetch_quotes(
            [key],
            max_age_seconds=180,
            batch_size=1,
            yf_threads=False,
            individual_fallback=True,
        ) or []
    except Exception as exc:
        logger.warning("scanner.xray_quotes_failed symbol=%s reason=%s", key, exc)
        quotes = []
    payload = build_scanner_payload(quotes, mode="long_term", limit=1, universe_size=1)
    rows = payload.get("rows") or []
    row = next((item for item in rows if str(item.get("symbol") or "").upper() == key), None)
    if row is None and rows:
        row = rows[0]
    if row:
        try:
            persist_daily_score_snapshots([row])
        except Exception as exc:
            logger.warning("scanner.xray_snapshot_failed reason=%s", exc)
    return row


def get_score_history(symbol: str, days: int = 90) -> Dict[str, Any]:
    key = _normalize_xray_symbol(symbol)
    window = 30 if int(days or 90) <= 30 else 90
    empty = {
        "symbol": key,
        "days": window,
        "points": [],
        "pending": True,
        "note": "Score history fills after daily snapshots. 30/90-day view is pending until we have journal-free daily scores.",
    }
    if not key:
        return empty
    try:
        from .database.db import ByselScoreSnapshotModel, SessionLocal
    except Exception:
        return empty

    cutoff = datetime.now(timezone.utc).date()
    start = cutoff.fromordinal(cutoff.toordinal() - window + 1)
    db = SessionLocal()
    try:
        records = (
            db.query(ByselScoreSnapshotModel)
            .filter(
                ByselScoreSnapshotModel.symbol == key,
                ByselScoreSnapshotModel.snapshot_date >= start,
            )
            .order_by(ByselScoreSnapshotModel.snapshot_date.asc())
            .all()
        )
        points = [
            {
                "date": rec.snapshot_date.isoformat() if rec.snapshot_date else "",
                "byselScore": rec.bysel_score,
                "quality": rec.quality,
                "valuation": rec.valuation,
                "trend": rec.trend,
                "momentum": rec.momentum,
            }
            for rec in records
        ]
        pending = len(points) < 2
        return {
            "symbol": key,
            "days": window,
            "points": points,
            "pending": pending,
            "note": (
                "Score history starts after the first daily snapshot. "
                "30/90-day trend fills in over time."
                if pending
                else "Daily BYSEL Score snapshots (education only, not advice)."
            ),
        }
    except Exception as exc:
        logger.warning("scanner.history_failed symbol=%s reason=%s", key, exc)
        return empty
    finally:
        db.close()


def cached_bysel_score_map() -> Dict[str, int]:
    """In-memory scanner scores only — no Yahoo hop, no Postgres requirement."""
    out: Dict[str, int] = {}
    with _CACHE_LOCK:
        payloads = [payload for _ts, payload in _SCANNER_CACHE.values()]
    for payload in payloads:
        for row in payload.get("rows") or []:
            if not isinstance(row, dict):
                continue
            symbol = str(row.get("symbol") or "").strip().upper()
            raw = row.get("byselScore")
            if raw is None:
                raw = row.get("bysel_score")
            if not symbol or raw is None:
                continue
            try:
                out[symbol] = int(raw)
            except (TypeError, ValueError):
                continue
    return out


def clear_scanner_cache() -> None:
    with _CACHE_LOCK:
        _SCANNER_CACHE.clear()
