"""
Long-term + Swing hybrid scanner.

BYSEL Score = Quality×0.35 + Valuation×0.25 + Trend×0.20 + Momentum×0.20.
Missing pillars and missing sub-metrics are skipped; remaining weights
are renormalized. ROCE / pledge / delivery / MACD / 5yr PE median / HH-HL
are never invented. Risk is a separate readout.

Conviction labels are educational (not Buy / Hold / Avoid).
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
SCANNER_MODES = ("long_term", "swing", "high_quality", "momentum", "value", "custom")
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
        "status": "Yahoo quotes do not include ROCE — shown as —",
    },
    {
        "id": "roe",
        "label": "ROE > 15%",
        "applied": True,
        "status": "Used when Yahoo has returnOnEquity; otherwise —",
    },
    {
        "id": "de",
        "label": "D/E < 1",
        "applied": True,
        "status": "Used when Yahoo has debtToEquity (non-banks); otherwise —",
    },
    {
        "id": "peg",
        "label": "PEG < 1.5",
        "applied": True,
        "status": "Used when Yahoo has pegRatio; otherwise —",
    },
    {
        "id": "pledge",
        "label": "Low promoter pledge",
        "applied": False,
        "status": "Not in Yahoo quotes — shown as —",
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

PILLAR_WEIGHTS = {
    "quality": 0.35,
    "valuation": 0.25,
    "trend": 0.20,
    "momentum": 0.20,
}
QUALITY_WEIGHTS = {
    "roce": 0.25,
    "roe": 0.20,
    "de": 0.15,
    "interestCoverage": 0.10,
    "salesCagr": 0.15,
    "profitCagr": 0.10,
    "promoterPledge": 0.05,
}
VALUATION_WEIGHTS = {
    "pe": 0.35,
    "peg": 0.25,
    "pb": 0.20,
    "evEbitda": 0.20,
}
TREND_WEIGHTS = {
    "vs200": 0.30,
    "vs50": 0.25,
    "cross": 0.20,
    "hhhl": 0.15,
    "week52": 0.10,
}
MOMENTUM_WEIGHTS = {
    "rsi": 0.30,
    "macd": 0.25,
    "rsNifty": 0.20,
    "volume": 0.15,
    "roc": 0.10,
}
FORMULA_NOTE = (
    "BYSEL Score = Quality×0.35 + Valuation×0.25 + Trend×0.20 + Momentum×0.20. "
    "Missing pillars and missing sub-metrics are skipped and remaining weights "
    "are renormalized. ROCE, pledge, delivery, MACD, 5yr PE median, and HH/HL "
    "are not invented from Yahoo quotes."
)
METRIC_LABELS = {
    "roce": "ROCE",
    "roe": "ROE",
    "de": "D/E",
    "debtToEquity": "D/E",
    "interestCoverage": "Interest cover",
    "salesCagr": "Sales CAGR",
    "profitCagr": "Profit CAGR",
    "promoterPledge": "Pledge",
    "pe": "PE vs baseline",
    "peg": "PEG",
    "pb": "P/B",
    "evEbitda": "EV/EBITDA",
    "vs200": "vs 200 DMA",
    "vs50": "vs 50 DMA",
    "cross": "50 vs 200 DMA",
    "hhhl": "HH/HL",
    "week52": "52-week range",
    "fiftyDayAverage": "50 DMA",
    "twoHundredDayAverage": "200 DMA",
    "rsi": "RSI",
    "macd": "MACD",
    "rsNifty": "RS vs Nifty",
    "volume": "Volume",
    "volumeRatio": "Volume",
    "roc": "ROC",
}
QUALITY_METRIC_WEIGHTS = {
    "roce": QUALITY_WEIGHTS["roce"],
    "roe": QUALITY_WEIGHTS["roe"],
    "debtToEquity": QUALITY_WEIGHTS["de"],
    "interestCoverage": QUALITY_WEIGHTS["interestCoverage"],
    "salesCagr": QUALITY_WEIGHTS["salesCagr"],
    "profitCagr": QUALITY_WEIGHTS["profitCagr"],
    "promoterPledge": QUALITY_WEIGHTS["promoterPledge"],
}
VALUATION_METRIC_WEIGHTS = dict(VALUATION_WEIGHTS)
TREND_METRIC_WEIGHTS = dict(TREND_WEIGHTS)
MOMENTUM_METRIC_WEIGHTS = dict(MOMENTUM_WEIGHTS)

_CACHE_LOCK = threading.Lock()
_SCANNER_CACHE: Dict[str, Tuple[float, Dict[str, Any]]] = {}


def _safe_float(value: Any) -> Optional[float]:
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number != number:  # NaN
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


def color_band(score: Optional[int]) -> str:
    """80+ green, 65–79 light green, 50–64 yellow, <50 orange/red."""
    if score is None:
        return "none"
    if score >= 80:
        return "green"
    if score >= 65:
        return "light_green"
    if score >= 50:
        return "yellow"
    return "orange_red"


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
) -> Dict[str, Any]:
    return {
        "score": score,
        "colorBand": color_band(score),
        "metrics": metrics,
        "topMetrics": top_contributing_metrics(metrics, weights, limit=3),
    }


def band_roce(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if value >= 25:
        return 100
    if value >= 20:
        return 85
    if value >= 15:
        return 65
    if value >= 10:
        return 40
    return 15


def band_roe(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if value >= 20:
        return 100
    if value >= 15:
        return 85
    if value >= 12:
        return 65
    if value >= 8:
        return 40
    return 15


def band_de(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if value <= 0.5:
        return 100
    if value <= 1.0:
        return 85
    if value <= 1.5:
        return 65
    if value <= 2.0:
        return 40
    return 15


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


def band_pe_vs_median(ratio: Optional[float]) -> Optional[int]:
    if ratio is None:
        return None
    if ratio <= 0.7:
        return 100
    if ratio <= 0.9:
        return 80
    if ratio <= 1.1:
        return 55
    if ratio <= 1.4:
        return 30
    return 15


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


def band_rsi(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    if 50 <= value <= 65:
        return int(round(90 + (value - 50) / 15.0 * 10))
    if 40 <= value < 50 or 65 < value <= 70:
        return 70
    if 30 <= value < 40 or 70 < value <= 75:
        return 40
    return 20


def score_label_token(score: Optional[int]) -> str:
    if score is None:
        return "insufficient"
    if score >= 80:
        return "high_conviction"
    if score >= 65:
        return "attractive"
    if score >= 50:
        return "neutral"
    if score >= 35:
        return "caution"
    return "weak"


def conviction_label(score: Optional[int]) -> str:
    token = score_label_token(score)
    return {
        "high_conviction": "High conviction setup (education)",
        "attractive": "Attractive on these factors",
        "neutral": "Neutral",
        "caution": "Caution",
        "weak": "Weak on these factors",
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
) -> Tuple[Optional[int], List[str]]:
    """Quality from available fundamentals only. No large-cap invention."""
    _ = symbol, market_cap
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    roce_score = band_roce(roce)
    parts["roce"] = roce_score
    notes.append(f"ROCE {roce:.0f}%" if roce is not None else "ROCE —")

    roe_score = band_roe(roe)
    parts["roe"] = roe_score
    notes.append(f"ROE {roe:.0f}%" if roe is not None else "ROE —")

    if sector in BANK_LIKE_SECTORS:
        parts["de"] = None
        notes.append("D/E n/a (bank)")
    else:
        parts["de"] = band_de(debt_to_equity)
        notes.append(f"D/E {debt_to_equity:.2f}" if debt_to_equity is not None else "D/E —")

    parts["interestCoverage"] = band_interest_coverage(interest_coverage)
    notes.append(
        f"Interest cover {interest_coverage:.1f}" if interest_coverage is not None else "Interest cover —"
    )
    parts["salesCagr"] = band_cagr(sales_cagr)
    notes.append(f"Sales CAGR {sales_cagr:.0f}%" if sales_cagr is not None else "Sales CAGR —")
    parts["profitCagr"] = band_cagr(profit_cagr)
    notes.append(f"Profit CAGR {profit_cagr:.0f}%" if profit_cagr is not None else "Profit CAGR —")

    if pledge is None and promoter is None:
        parts["promoterPledge"] = None
        notes.append("Pledge —")
    elif pledge is not None:
        if pledge <= 5:
            parts["promoterPledge"] = 100
        elif pledge <= 15:
            parts["promoterPledge"] = 65
        else:
            parts["promoterPledge"] = 20
        notes.append(f"Pledge {pledge:.0f}%")
    else:
        parts["promoterPledge"] = None
        notes.append("Pledge —")

    blended = renormalized_score(parts, QUALITY_WEIGHTS)
    if blended is None:
        return None, notes, parts
    if fcf is not None and fcf > 0:
        notes.append("FCF bonus")
        blended = min(100.0, blended + 8)
    return int(round(blended)), notes, parts


def score_value(
    *,
    pe: Optional[float],
    sector_pe: Optional[float] = None,
    pe_median_5y: Optional[float] = None,
    peg: Optional[float] = None,
    pb: Optional[float] = None,
    ev_ebitda: Optional[float] = None,
) -> Tuple[Optional[int], List[str]]:
    """Valuation: PE vs 5yr median (else sector-ish), PEG, PB, EV/EBITDA."""
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    baseline = pe_median_5y if pe_median_5y and pe_median_5y > 0 else None
    baseline_label = "5yr median"
    if baseline is None and sector_pe and sector_pe > 0:
        baseline = sector_pe
        baseline_label = "sector"
    if pe is not None and pe > 0 and baseline:
        ratio = pe / baseline
        parts["pe"] = band_pe_vs_median(ratio)
        notes.append(f"PE {pe:.0f} vs {baseline_label} ~{baseline:.0f}")
    else:
        parts["pe"] = None
        notes.append("PE vs median —" if pe is None else f"PE {pe:.0f} (no median)")

    if peg is None:
        parts["peg"] = None
    elif peg <= 1.0:
        parts["peg"] = 100
    elif peg <= 1.5:
        parts["peg"] = 75
    elif peg <= 2.0:
        parts["peg"] = 45
    else:
        parts["peg"] = 20
    notes.append(f"PEG {peg:.1f}" if peg is not None else "PEG —")
    parts["pb"] = None
    notes.append("P/B —" if pb is None else f"P/B {pb:.1f}")
    ev_sector = None
    if ev_ebitda is not None and ev_sector:
        parts["evEbitda"] = band_pe_vs_median(ev_ebitda / ev_sector)
        notes.append(f"EV/EBITDA {ev_ebitda:.1f} vs sector")
    else:
        parts["evEbitda"] = None
        notes.append("EV/EBITDA —")

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
) -> Tuple[Optional[int], List[str]]:
    """Trend: vs 200 DMA, vs 50 DMA, 50 vs 200, HH/HL, 52w distance."""
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {
        "vs200": band_dma_pct(last, two_hundred),
        "vs50": band_dma_pct(last, fifty_day),
        "cross": None,
        "hhhl": None,
        "week52": None,
    }
    if two_hundred:
        notes.append("Above 200 DMA" if parts["vs200"] and parts["vs200"] >= 80 else (
            "Below 200 DMA" if two_hundred and last and last < two_hundred else "200 DMA —"
        ))
    else:
        notes.append("200 DMA —")
    if fifty_day:
        notes.append("Above 50 DMA" if parts["vs50"] and parts["vs50"] >= 80 else (
            "Below 50 DMA" if fifty_day and last and last < fifty_day else "50 DMA —"
        ))
    else:
        notes.append("50 DMA —")

    if fifty_day and two_hundred and two_hundred > 0:
        parts["cross"] = 100 if fifty_day >= two_hundred else 20
        notes.append("Golden cross 50>200" if fifty_day >= two_hundred else "Death cross 50<200")
    else:
        notes.append("50 vs 200 —")

    if hhhl is None:
        notes.append("HH/HL —")
    else:
        notes.append("HH/HL present")

    if last and week52_high and week52_low and week52_high > week52_low:
        pos = (last - week52_low) / (week52_high - week52_low)
        if 0.40 <= pos <= 0.70:
            parts["week52"] = 80
        elif 0.20 <= pos < 0.40 or 0.70 < pos <= 0.85:
            parts["week52"] = 60
        elif pos > 0.85:
            parts["week52"] = 40
        else:
            parts["week52"] = 35
        notes.append(f"52w {pos * 100:.0f}%")
    else:
        notes.append("52w —")

    blended = renormalized_score(parts, TREND_WEIGHTS)
    if blended is None:
        return None, notes, parts
    if hhhl is not None and hhhl > 0:
        notes.append("HH/HL bonus")
        blended = min(100.0, blended + 8)
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
) -> Tuple[Optional[int], List[str]]:
    """Momentum: RSI 50–65 ideal, MACD, RS vs Nifty, volume/delivery, ROC."""
    _ = last, fifty_day, two_hundred
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {}

    parts["rsi"] = band_rsi(rsi)
    notes.append(f"RSI {rsi:.0f}" if rsi is not None else "RSI —")

    if macd is None:
        parts["macd"] = None
        notes.append("MACD —")
    else:
        parts["macd"] = 80 if macd > 0 else 30
        notes.append("MACD +") if macd > 0 else notes.append("MACD −")

    if rs_vs_nifty is None:
        parts["rsNifty"] = None
        notes.append("RS vs Nifty —")
    elif rs_vs_nifty > 1.5:
        parts["rsNifty"] = 100
        notes.append(f"RS vs Nifty {rs_vs_nifty:+.1f}%")
    elif rs_vs_nifty >= 0:
        parts["rsNifty"] = 80
        notes.append(f"RS vs Nifty {rs_vs_nifty:+.1f}%")
    elif rs_vs_nifty >= -1.5:
        parts["rsNifty"] = 50
        notes.append(f"RS vs Nifty {rs_vs_nifty:+.1f}%")
    else:
        parts["rsNifty"] = 20
        notes.append(f"RS vs Nifty {rs_vs_nifty:+.1f}%")

    vol_score = None
    if vol_ratio is not None:
        if vol_ratio >= 2.0:
            vol_score = 100
        elif vol_ratio >= 1.5:
            vol_score = 80
        elif vol_ratio >= 1.0:
            vol_score = 50
        else:
            vol_score = 30
        notes.append(f"vol {vol_ratio:.1f}x")
    else:
        notes.append("vol —")
    if delivery_pct is not None:
        if delivery_pct >= 50:
            delivery_score = 100
        elif delivery_pct >= 40:
            delivery_score = 80
        elif delivery_pct >= 30:
            delivery_score = 50
        else:
            delivery_score = 20
        notes.append(f"delivery {delivery_pct:.0f}%")
        vol_score = (vol_score + delivery_score) / 2.0 if vol_score is not None else float(delivery_score)
    else:
        notes.append("delivery —")
    parts["volume"] = vol_score

    roc_value = roc if roc is not None else pct_change
    if roc_value is None:
        parts["roc"] = None
        notes.append("ROC —")
    elif 0.5 <= roc_value <= 3.0:
        parts["roc"] = 80
        notes.append(f"ROC {roc_value:+.1f}%")
    elif 3.0 < roc_value <= 6.0:
        parts["roc"] = 60
        notes.append(f"ROC {roc_value:+.1f}%")
    elif roc_value > 6.0:
        parts["roc"] = 40
        notes.append(f"ROC {roc_value:+.1f}%")
    elif -2.0 <= roc_value < 0.5:
        parts["roc"] = 55
        notes.append(f"ROC {roc_value:+.1f}%")
    else:
        parts["roc"] = 30
        notes.append(f"ROC {roc_value:+.1f}%")

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
) -> Optional[int]:
    blended = renormalized_score(
        {
            "quality": float(quality) if quality is not None else None,
            "valuation": float(valuation) if valuation is not None else None,
            "trend": float(trend) if trend is not None else None,
            "momentum": float(momentum) if momentum is not None else None,
        },
        PILLAR_WEIGHTS,
    )
    if blended is None:
        return None
    return int(round(blended))


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
        ("interestCoverage", "interestCoverage"),
        ("salesCagr", "salesCagr"),
        ("profitCagr", "profitCagr"),
        ("peg", "peg"),
        ("pb", "pb"),
        ("evEbitda", "evEbitda"),
        ("peMedian5y", "peMedian5y"),
        ("rsi", "rsi"),
        ("macd", "macd"),
        ("deliveryPct", "delivery"),
        ("hhhl", "hhhl"),
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
            "Insufficient Yahoo fields to compute a BYSEL Score — pillars stay as —. "
            "Missing metrics are skipped rather than invented. "
            "This is an educational readout, not investment advice."
        )
    q_bit = next((n for n in q_notes if not n.endswith("—")), "only available quality metrics")
    v_bit = next((n for n in v_notes if not n.endswith("—")), "valuation incomplete")
    t_bit = next((n for n in t_notes if not n.endswith("—")), "trend incomplete")
    m_bit = next((n for n in m_notes if not n.endswith("—")), "momentum incomplete")
    sentences = [
        (
            f"BYSEL Score is {bysel}/100 from available Yahoo fields: "
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
    skip_note = "Missing metrics are skipped and remaining weights are renormalized"
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
    )
    valuation, v_notes, v_parts = score_value(
        pe=row.get("pe"),
        sector_pe=sector_pe,
        pe_median_5y=row.get("peMedian5y"),
        peg=row.get("peg"),
        pb=row.get("pb"),
        ev_ebitda=row.get("evEbitda"),
    )
    trend, t_notes, t_parts = score_trend(
        last=row.get("last"),
        fifty_day=row.get("fiftyDayAverage"),
        two_hundred=row.get("twoHundredDayAverage"),
        week52_high=row.get("fiftyTwoWeekHigh"),
        week52_low=row.get("fiftyTwoWeekLow"),
        hhhl=row.get("hhhl"),
    )
    rs_vs_nifty = None
    if nifty_change is not None and row.get("pctChange") is not None:
        rs_vs_nifty = float(row["pctChange"]) - float(nifty_change)
    momentum, m_notes, m_parts = score_momentum(
        last=row.get("last"),
        rsi=row.get("rsi"),
        vol_ratio=row.get("volumeRatio"),
        pct_change=row.get("pctChange"),
        macd=row.get("macd"),
        rs_vs_nifty=rs_vs_nifty,
        delivery_pct=row.get("deliveryPct"),
        roc=row.get("roc"),
    )
    risk_score, risk_label, risk_notes = score_risk(
        debt_to_equity=row.get("debtToEquity"),
        pledge=row.get("pledge"),
        vol_ratio=row.get("volumeRatio"),
        sector=str(row.get("sector") or "Other"),
    )
    missing = missing_fields(row)
    bysel = _weighted_bysel_score(quality, valuation, trend, momentum)
    rank_score = bysel if bysel is not None else 0
    rank_score = min(max(int(round(rank_score * _soft_filter_multiplier(mode, row))), 0), 100)
    label = conviction_label(bysel)
    quality_metrics = {
        "roce": _metric(row.get("roce"), _score_int(q_parts.get("roce"))),
        "roe": _metric(row.get("roe"), _score_int(q_parts.get("roe"))),
        "debtToEquity": _metric(row.get("debtToEquity"), _score_int(q_parts.get("de"))),
        "interestCoverage": _metric(row.get("interestCoverage"), _score_int(q_parts.get("interestCoverage"))),
        "salesCagr": _metric(row.get("salesCagr"), _score_int(q_parts.get("salesCagr"))),
        "profitCagr": _metric(row.get("profitCagr"), _score_int(q_parts.get("profitCagr"))),
        "promoterPledge": _metric(row.get("pledge"), _score_int(q_parts.get("promoterPledge"))),
    }
    valuation_metrics = {
        "pe": _metric(row.get("pe"), _score_int(v_parts.get("pe"))),
        "peg": _metric(row.get("peg"), _score_int(v_parts.get("peg"))),
        "pb": _metric(row.get("pb"), _score_int(v_parts.get("pb"))),
        "evEbitda": _metric(row.get("evEbitda"), _score_int(v_parts.get("evEbitda"))),
    }
    trend_metrics = {
        "vs200": _metric(row.get("twoHundredDayAverage"), _score_int(t_parts.get("vs200"))),
        "vs50": _metric(row.get("fiftyDayAverage"), _score_int(t_parts.get("vs50"))),
        "cross": _metric(None, _score_int(t_parts.get("cross"))),
        "hhhl": _metric(row.get("hhhl"), _score_int(t_parts.get("hhhl"))),
        "week52": _metric(None, _score_int(t_parts.get("week52"))),
        "fiftyDayAverage": _metric(row.get("fiftyDayAverage"), _score_int(t_parts.get("vs50"))),
        "twoHundredDayAverage": _metric(row.get("twoHundredDayAverage"), _score_int(t_parts.get("vs200"))),
    }
    momentum_metrics = {
        "rsi": _metric(row.get("rsi"), _score_int(m_parts.get("rsi"))),
        "macd": _metric(row.get("macd"), _score_int(m_parts.get("macd"))),
        "rsNifty": _metric(rs_vs_nifty, _score_int(m_parts.get("rsNifty"))),
        "volume": _metric(row.get("volumeRatio"), _score_int(m_parts.get("volume"))),
        "volumeRatio": _metric(row.get("volumeRatio"), _score_int(m_parts.get("volume"))),
        "roc": _metric(row.get("roc") if row.get("roc") is not None else row.get("pctChange"), _score_int(m_parts.get("roc"))),
    }
    pillars = {
        "quality": _pillar_payload(quality, quality_metrics, QUALITY_METRIC_WEIGHTS),
        "valuation": _pillar_payload(valuation, valuation_metrics, VALUATION_METRIC_WEIGHTS),
        "trend": _pillar_payload(trend, trend_metrics, TREND_METRIC_WEIGHTS),
        "momentum": _pillar_payload(momentum, momentum_metrics, MOMENTUM_METRIC_WEIGHTS),
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
        "interestCoverage": interest_coverage,
        "salesCagr": sales_cagr,
        "profitCagr": profit_cagr,
        "peMedian5y": pe_median_5y,
        "deliveryPct": delivery_pct,
        "macd": macd,
        "fcf": fcf,
        "hhhl": _optional_metric(raw, "hhhl"),
        "pledge": _optional_metric(raw, "pledge"),
        "marginPct": _optional_metric(raw, "marginPct", "margin"),
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
            "are shown for education; we apply only filters we can compute from Yahoo."
        )
        risk_note = "Heuristic rank, not a buy list. Missing ROCE/pledge/delivery stay as —."
    if mode == "high_quality":
        title = "High Quality"
        summary = "Sorted by the Quality pillar from available ROE/ROCE/D/E only. Missing metrics are skipped."
    elif mode == "momentum":
        title = "Momentum"
        summary = "Sorted by the Momentum pillar (RSI/volume/ROC when present). MACD stays — on Yahoo quotes."
    elif mode == "value":
        title = "Value"
        summary = "Sorted by the Valuation pillar (PE vs sector-ish / PEG when present)."
    elif mode == "custom":
        title = "Custom — chips we can actually apply"
        summary = (
            "Filter the scored universe with fields Yahoo already gives us: "
            "min BYSEL score, RSI, price vs 50/200 DMA, volume vs average, PE max, and day change. "
            "Missing RSI/DMA/PE skips that name instead of inventing a value. "
            "This is not a 40-filter builder."
        )
        risk_note = "Heuristic rank by BYSEL Score. Not a buy list. Unusual volume is flagged only at >2× average."
    return {
        "title": title,
        "summary": summary,
        "filters": list(CUSTOM_EDUCATION_FILTERS if mode == "custom" else EDUCATION_FILTERS),
        "scoreGuide": FORMULA_NOTE + (
            " Labels: 80–100 High conviction setup (education); 65–79 Attractive on these factors; "
            "50–64 Neutral; 35–49 Caution; <35 Weak on these factors. "
            "Never Strong Buy / Buy / Hold / Avoid."
        ),
        "riskNote": risk_note,
        "disclaimer": DISCLAIMER,
        "dataLimits": (
            "Universe is NIFTY 50 plus the default watchlist catalog "
            "(and a few heatmap movers if that cache is already warm). "
            "Yahoo rarely has ROCE, promoter pledge, or RSI on the quote snapshot."
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

    sector_pe = _sector_pe_map(rows)
    scored: List[Dict[str, Any]] = []
    for row in rows:
        scores = score_row(
            row,
            mode_key,
            sector_pe.get(str(row.get("sector") or "Other")),
            nifty_change=nifty_change,
        )
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
                "sectorPe": sector_pe.get(str(row.get("sector") or "Other")),
                "pledge": row.get("pledge"),
                "marginPct": row.get("marginPct"),
            },
            "missing": scores["missing"],
            "anomalies": scores.get("anomalies") or [],
        })

    def _rank(item: Dict[str, Any]) -> Tuple[int, str]:
        if mode_key == "high_quality":
            key = item.get("quality")
        elif mode_key == "momentum":
            key = item.get("momentum")
        elif mode_key == "value":
            key = item.get("valuation")
        elif mode_key == "custom":
            key = item.get("byselScore")
        else:
            key = item.get("overall")
        return (-int(key or 0), str(item.get("symbol") or ""))

    scored.sort(key=_rank)
    if mode_key == "swing":
        with_setup = [item for item in scored if item.get("setup")]
        cap = min(max(int(limit), 5), 15)
        shortlist = with_setup[:cap]
    elif mode_key == "custom":
        shortlist = scored[: min(max(int(limit), 5), 40)]
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
        with _CACHE_LOCK:
            cached = _SCANNER_CACHE.get(cache_key)
        if cached and (now - cached[0]) < SCANNER_CACHE_TTL_SECONDS:
            payload = dict(cached[1])
            payload["cached"] = True
            return payload

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

    payload = build_scanner_payload(
        quotes,
        mode=mode_key,
        limit=limit,
        universe_size=len(symbols),
    )
    try:
        persist_daily_score_snapshots(payload.get("rows") or [])
    except Exception as exc:
        logger.warning("scanner.snapshot_persist_failed reason=%s", exc)
    with _CACHE_LOCK:
        _SCANNER_CACHE[cache_key] = (time.time(), dict(payload))
    payload["cached"] = False
    return payload


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


def clear_scanner_cache() -> None:
    with _CACHE_LOCK:
        _SCANNER_CACHE.clear()
