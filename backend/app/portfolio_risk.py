"""
Phase 1.3 paper Portfolio Risk snapshot.

Honest book metrics from current holdings + quotes. Does not invent
1Y max drawdown, realized volatility, beta, or a correlation matrix.
Nifty shocks use a conservative beta=1 illustration on equity value only.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

logger = logging.getLogger(__name__)

DISCLAIMER = (
    "Educational paper metrics — not investment advice and not a forecast. "
    "BYSEL Score is value-weighted from names that already have a score; missing names are skipped."
)
IMPORT_NOTE = (
    "Import a broker CSV or CAS extract on Portfolio — read-only. "
    "Marks use live quotes when the session is open."
)
WHAT_IF_LABEL = (
    "Illustration, not a forecast. Conservative beta = 1 on equity value "
    "(cash ignored). Actual names move differently from Nifty."
)
HISTORY_NOTE = "Needs more history — volatility and 1Y max drawdown are omitted until we have real series."
CONCENTRATION_HINT = "Higher = more of the book in one name"
SECTOR_SPREAD_HINT = "Higher = more spread across sectors (1 − HHI)"


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (TypeError, ValueError):
        return default


def _symbol_key(value: Any) -> str:
    return str(value or "").strip().upper()


def holding_mark(holding: Mapping[str, Any], quote: Optional[Mapping[str, Any]]) -> float:
    """Prefer live quote last, then stored last, then cost. Never invent a mark."""
    if quote is not None:
        last = _safe_float(quote.get("last") or quote.get("ltp") or quote.get("price"), 0.0)
        if last > 0:
            return last
    stored = _safe_float(holding.get("last") or holding.get("lastPrice") or holding.get("last_price"), 0.0)
    if stored > 0:
        return stored
    return max(_safe_float(holding.get("avgPrice") or holding.get("avg_price"), 0.0), 0.0)


def day_pnl_rupees(
    qty: int,
    last: float,
    pct_change: Optional[float] = None,
    prev_close: Optional[float] = None,
) -> Tuple[float, bool]:
    """Session P&L from prevClose when present, else implied from pctChange.

    Returns (rupees, available). available is False when we have no session move input.
    """
    if qty <= 0 or last <= 0:
        return 0.0, False
    if prev_close is not None and prev_close > 0:
        return round(qty * (last - prev_close), 2), True
    if pct_change is None:
        return 0.0, False
    denom = 1.0 + (pct_change / 100.0)
    if denom <= 0:
        return 0.0, False
    prev = last / denom
    return round(qty * (last - prev), 2), True


def position_weights(values: Sequence[float]) -> List[float]:
    total = sum(max(v, 0.0) for v in values)
    if total <= 0:
        return [0.0] * len(values)
    return [max(v, 0.0) / total * 100.0 for v in values]


def concentration_from_weights(
    weights_pct: Sequence[float],
    symbols: Sequence[str],
) -> Dict[str, Any]:
    """Top-1 / top-5 weight %. Gauge is top-1 itself (honest 0–100)."""
    paired = sorted(
        (
            (_safe_float(w), _symbol_key(sym))
            for w, sym in zip(weights_pct, symbols)
        ),
        key=lambda item: (-item[0], item[1]),
    )
    top1 = paired[0][0] if paired else 0.0
    top1_symbol = paired[0][1] if paired else ""
    top5 = sum(item[0] for item in paired[:5])
    gauge = int(round(min(max(top1, 0.0), 100.0)))
    return {
        "top1Pct": round(top1, 2),
        "top1Symbol": top1_symbol,
        "top5Pct": round(min(top5, 100.0), 2),
        "gauge": gauge,
        "gaugeLabel": "Largest name as % of book",
        "gaugeHint": CONCENTRATION_HINT,
    }


def sector_mix(
    weights_pct: Sequence[float],
    sectors: Sequence[str],
) -> List[Dict[str, Any]]:
    buckets: Dict[str, float] = {}
    for weight, sector in zip(weights_pct, sectors):
        name = str(sector or "Other").strip() or "Other"
        buckets[name] = buckets.get(name, 0.0) + max(_safe_float(weight), 0.0)
    ranked = sorted(buckets.items(), key=lambda item: (-item[1], item[0]))
    return [{"name": name, "weightPct": round(weight, 2)} for name, weight in ranked if weight > 0]


def herfindahl_hhi(weights_pct: Sequence[float]) -> float:
    """HHI on fractions. 1.0 = one bucket owns the book."""
    total = sum(max(_safe_float(w), 0.0) for w in weights_pct)
    if total <= 0:
        return 0.0
    return sum((max(_safe_float(w), 0.0) / total) ** 2 for w in weights_pct)


def sector_spread_from_mix(sectors: Sequence[Mapping[str, Any]]) -> Dict[str, Any]:
    weights = [_safe_float(item.get("weightPct")) for item in sectors]
    hhi = herfindahl_hhi(weights)
    gauge = int(round(min(max((1.0 - hhi) * 100.0, 0.0), 100.0)))
    return {
        "sectorCount": len([w for w in weights if w > 0]),
        "hhi": round(hhi, 4),
        "gauge": gauge,
        "gaugeLabel": "Sector spread",
        "gaugeHint": SECTOR_SPREAD_HINT,
    }


def nifty_what_if(equity_value: float, shock_pct: float, beta: float = 1.0) -> float:
    """Illustrated rupee move: equity × shock × beta. Not a forecast."""
    if equity_value <= 0:
        return 0.0
    return round(equity_value * (shock_pct / 100.0) * beta, 2)


def value_weighted_score(
    values: Sequence[float],
    scores: Sequence[Optional[float]],
) -> Dict[str, Any]:
    """Value-weighted BYSEL Score. Missing scores are skipped, not filled with 0."""
    covered = 0.0
    weighted = 0.0
    scored_count = 0
    missing_count = 0
    total = sum(max(v, 0.0) for v in values)
    for value, score in zip(values, scores):
        v = max(_safe_float(value), 0.0)
        if score is None:
            if v > 0:
                missing_count += 1
            continue
        try:
            numeric = float(score)
        except (TypeError, ValueError):
            if v > 0:
                missing_count += 1
            continue
        covered += v
        weighted += v * numeric
        scored_count += 1
    if covered <= 0 or scored_count == 0:
        return {
            "valueWeighted": None,
            "scoredCount": 0,
            "missingCount": missing_count,
            "coveredValuePct": 0.0,
            "note": "No BYSEL Score on these holdings yet — skipped rather than guessed.",
        }
    return {
        "valueWeighted": int(round(weighted / covered)),
        "scoredCount": scored_count,
        "missingCount": missing_count,
        "coveredValuePct": round((covered / total * 100.0) if total > 0 else 0.0, 2),
        "note": "Value-weighted from holdings that already have a BYSEL Score. Missing names skipped.",
    }


def empty_portfolio_risk() -> Dict[str, Any]:
    return {
        "empty": True,
        "totalValue": 0.0,
        "totalInvested": 0.0,
        "totalPnl": 0.0,
        "dayPnl": 0.0,
        "dayPnlPercent": 0.0,
        "dayPnlAvailable": False,
        "byselScore": {
            "valueWeighted": None,
            "scoredCount": 0,
            "missingCount": 0,
            "coveredValuePct": 0.0,
            "note": "Scores appear after paper holdings exist.",
        },
        "concentration": {
            "top1Pct": 0.0,
            "top1Symbol": "",
            "top5Pct": 0.0,
            "gauge": 0,
            "gaugeLabel": "Largest name as % of book",
            "gaugeHint": CONCENTRATION_HINT,
        },
        "sectors": [],
        "sectorSpread": {
            "sectorCount": 0,
            "hhi": 0.0,
            "gauge": 0,
            "gaugeLabel": "Sector spread",
            "gaugeHint": SECTOR_SPREAD_HINT,
        },
        "whatIf": {
            "beta": 1.0,
            "equityValue": 0.0,
            "niftyDown5": 0.0,
            "niftyDown10": 0.0,
            "label": WHAT_IF_LABEL,
        },
        "volatility": {"available": False, "note": HISTORY_NOTE},
        "maxDrawdown": {"available": False, "note": HISTORY_NOTE},
        "disclaimer": DISCLAIMER,
        "importNote": IMPORT_NOTE,
        "message": "No paper holdings yet. Risk gauges appear after your first practice buy.",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
    }


def _quote_index(quotes: Iterable[Mapping[str, Any]]) -> Dict[str, Mapping[str, Any]]:
    out: Dict[str, Mapping[str, Any]] = {}
    for quote in quotes or []:
        key = _symbol_key(quote.get("symbol"))
        if key:
            out[key] = quote
    return out


def _prev_close(quote: Optional[Mapping[str, Any]]) -> Optional[float]:
    if not quote:
        return None
    for key in ("prevClose", "previousClose", "previous_close"):
        value = _safe_float(quote.get(key), 0.0)
        if value > 0:
            return value
    return None


def _pct_change(quote: Optional[Mapping[str, Any]]) -> Optional[float]:
    if not quote:
        return None
    raw = quote.get("pctChange")
    if raw is None:
        raw = quote.get("pct_change")
    if raw is None:
        return None
    return _safe_float(raw, 0.0)


def _sector_for(symbol: str, sector_map: Mapping[str, str]) -> str:
    return sector_map.get(symbol, "Other")


def build_portfolio_risk(
    holdings: Sequence[Mapping[str, Any]],
    quotes: Sequence[Mapping[str, Any]] = (),
    scores: Optional[Mapping[str, Any]] = None,
    sector_map: Optional[Mapping[str, str]] = None,
) -> Dict[str, Any]:
    """Build the Phase 1.3 risk snapshot. Pure: no network, no DB."""
    if sector_map is None:
        from .portfolio_scorer import SECTOR_MAP

        sector_map = SECTOR_MAP
    score_map = { _symbol_key(k): v for k, v in (scores or {}).items() if _symbol_key(k) }
    quote_by = _quote_index(quotes)

    rows: List[Dict[str, Any]] = []
    for holding in holdings or []:
        symbol = _symbol_key(holding.get("symbol"))
        qty = _safe_int(holding.get("qty") if holding.get("qty") is not None else holding.get("quantity"), 0)
        if not symbol or qty <= 0:
            continue
        quote = quote_by.get(symbol)
        last = holding_mark(holding, quote)
        avg = _safe_float(holding.get("avgPrice") or holding.get("avg_price"), 0.0)
        value = last * qty
        invested = avg * qty
        day_pnl, day_ok = day_pnl_rupees(qty, last, _pct_change(quote), _prev_close(quote))
        raw_score = score_map.get(symbol)
        parsed_score: Optional[float]
        try:
            parsed_score = float(raw_score) if raw_score is not None else None
        except (TypeError, ValueError):
            parsed_score = None
        rows.append({
            "symbol": symbol,
            "qty": qty,
            "last": last,
            "value": value,
            "invested": invested,
            "dayPnl": day_pnl,
            "dayPnlAvailable": day_ok,
            "sector": _sector_for(symbol, sector_map),
            "score": parsed_score,
        })

    if not rows:
        return empty_portfolio_risk()

    values = [row["value"] for row in rows]
    total_value = sum(values)
    total_invested = sum(row["invested"] for row in rows)
    weights = position_weights(values)
    for row, weight in zip(rows, weights):
        row["weightPct"] = round(weight, 2)

    day_available = any(row["dayPnlAvailable"] for row in rows)
    day_pnl = round(sum(row["dayPnl"] for row in rows if row["dayPnlAvailable"]), 2) if day_available else 0.0
    day_pct = round((day_pnl / total_value * 100.0), 2) if day_available and total_value > 0 else 0.0

    sectors = sector_mix(weights, [row["sector"] for row in rows])
    what_if_5 = nifty_what_if(total_value, -5.0, beta=1.0)
    what_if_10 = nifty_what_if(total_value, -10.0, beta=1.0)

    payload = empty_portfolio_risk()
    payload.update({
        "empty": False,
        "totalValue": round(total_value, 2),
        "totalInvested": round(total_invested, 2),
        "totalPnl": round(total_value - total_invested, 2),
        "dayPnl": day_pnl,
        "dayPnlPercent": day_pct,
        "dayPnlAvailable": day_available,
        "byselScore": value_weighted_score(values, [row["score"] for row in rows]),
        "concentration": concentration_from_weights(weights, [row["symbol"] for row in rows]),
        "sectors": sectors,
        "sectorSpread": sector_spread_from_mix(sectors),
        "whatIf": {
            "beta": 1.0,
            "equityValue": round(total_value, 2),
            "niftyDown5": what_if_5,
            "niftyDown10": what_if_10,
            "label": WHAT_IF_LABEL,
        },
        "message": "",
        "holdingsCount": len(rows),
    })
    return payload


def cached_scanner_scores(symbols: Sequence[str]) -> Dict[str, int]:
    wanted = { _symbol_key(s) for s in symbols if _symbol_key(s) }
    if not wanted:
        return {}
    try:
        from .market_scanner import cached_bysel_score_map

        cached = cached_bysel_score_map()
    except Exception as exc:
        logger.debug("portfolio_risk.scanner_cache_unavailable reason=%s", exc)
        cached = {}
    return { sym: score for sym, score in cached.items() if sym in wanted }


def latest_snapshot_scores(symbols: Sequence[str]) -> Dict[str, int]:
    """Optional SQLite/SQLAlchemy snapshots. Must not require Postgres."""
    wanted = [ _symbol_key(s) for s in symbols if _symbol_key(s) ]
    if not wanted:
        return {}
    try:
        from .database.db import ByselScoreSnapshotModel, SessionLocal
        from sqlalchemy import desc
    except Exception:
        return {}
    db = None
    try:
        db = SessionLocal()
        rows = (
            db.query(ByselScoreSnapshotModel)
            .filter(ByselScoreSnapshotModel.symbol.in_(wanted))
            .order_by(ByselScoreSnapshotModel.symbol.asc(), desc(ByselScoreSnapshotModel.snapshot_date))
            .all()
        )
        out: Dict[str, int] = {}
        for row in rows:
            key = _symbol_key(row.symbol)
            if key in out or row.bysel_score is None:
                continue
            out[key] = int(row.bysel_score)
        return out
    except Exception as exc:
        logger.debug("portfolio_risk.snapshot_scores_unavailable reason=%s", exc)
        return {}
    finally:
        if db is not None:
            db.close()


def scores_from_quotes(quotes: Sequence[Mapping[str, Any]]) -> Dict[str, int]:
    """Score from already-fetched quote fields. Never extra Yahoo hops."""
    if not quotes:
        return {}
    try:
        from .market_scanner import score_row
    except Exception:
        return {}
    out: Dict[str, int] = {}
    for quote in quotes:
        symbol = _symbol_key(quote.get("symbol"))
        if not symbol:
            continue
        try:
            scored = score_row(dict(quote), "long_term", sector_pe=None)
            raw = scored.get("byselScore")
            if raw is None:
                continue
            out[symbol] = int(raw)
        except Exception:
            continue
    return out


def resolve_bysel_scores(
    symbols: Sequence[str],
    quotes: Sequence[Mapping[str, Any]] = (),
) -> Dict[str, int]:
    """Cache first, then snapshots, then score_row on existing quotes. Skip gaps."""
    merged: Dict[str, int] = {}
    merged.update(latest_snapshot_scores(symbols))
    merged.update(cached_scanner_scores(symbols))
    quote_scores = scores_from_quotes(quotes)
    for symbol, score in quote_scores.items():
        merged.setdefault(symbol, score)
    wanted = { _symbol_key(s) for s in symbols if _symbol_key(s) }
    return { sym: score for sym, score in merged.items() if sym in wanted }


def compute_portfolio_risk_from_holdings(
    holdings: Sequence[Mapping[str, Any]],
    quotes: Sequence[Mapping[str, Any]] = (),
) -> Dict[str, Any]:
    symbols = [_symbol_key(h.get("symbol")) for h in holdings]
    scores = resolve_bysel_scores(symbols, quotes)
    return build_portfolio_risk(holdings, quotes, scores)
