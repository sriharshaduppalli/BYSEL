"""
Quantitative momentum (Jegadeesh–Titman / Gray-style 12-2).

This is a cross-sectional rank of intermediate-term winners, not RSI.
Short-horizon oscillators stay on the swing / F&O Momentum pillar.

Quality Momentum is sequential: hard quality gates, then 12-2 rank among
survivors. Quality is never averaged into the 12-2 number.

Missing bars, revisions, VIX, and official Nifty 500 membership are never invented.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

logger = logging.getLogger(__name__)

QM_WEIGHTS = {
    "r122": 0.40,
    "smooth": 0.25,
    "h52": 0.20,
    "earn": 0.15,
}
QM_LOOKBACK_SESSIONS = 252
QM_SKIP_SESSIONS = 21
QM_MIN_BARS = QM_LOOKBACK_SESSIONS
QM_ADV_FLOOR_INR = 8 * 10**7  # ₹8 Cr
QM_SQUEEZE_PERCENTILE = 95.0
QM_HISTORY_TTL_SECONDS = 6 * 60 * 60
QM_MODES = frozenset(
    {"long_term", "high_quality", "value", "quality_screen", "custom", "momentum"}
)
QUALITY_GATE_MIN_ROE = 12.0
QUALITY_GATE_MAX_DE = 1.5
QUALITY_GATE_MIN_INTEREST_COVER = 2.0
QUALITY_GATE_MAX_PLEDGE = 20.0
QUALITY_GATE_MIN_EPS_YEARS = 3
QM_SECTOR_CAP = 0.30
QM_SLEEVE_SIZE = 30
BANK_LIKE_SECTORS = {"Banking", "NBFC", "Finance", "Insurance"}
QM_BADGE_QUALITY_MOMENTUM = "Quality Momentum"
QM_BADGE_QUALITY_MIXED = "Quality, trend mixed"
QM_BADGE_SPECULATIVE = "Speculative momentum"
QM_BADGE_WEAK = "Weak"

_HISTORY_LOCK = threading.Lock()
_HISTORY_CACHE: Dict[str, Tuple[float, List[float]]] = {}
_FILL_LOCK = threading.Lock()
_FILL_RUNNING = False

METRIC_LABELS = {
    "r122": "12-2 rank",
    "smooth": "Path smoothness",
    "h52": "52-week closeness",
    "earn": "EPS revision 3m",
}


def uses_qm_momentum(mode: str) -> bool:
    return (mode or "").strip().lower() in QM_MODES


def _safe_float(value: Any) -> Optional[float]:
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number != number:
        return None
    return number


def closes_from_candles(candles: Sequence[Dict[str, Any]]) -> List[float]:
    closes: List[float] = []
    for item in candles or []:
        if not isinstance(item, dict):
            continue
        price = _safe_float(item.get("close") if item.get("close") is not None else item.get("adjClose"))
        if price is not None and price > 0:
            closes.append(price)
    return closes


def generic_momentum_12_2(closes: Sequence[float]) -> Optional[float]:
    """R_12-2 = P[t-21] / P[t-252] - 1. Needs 252 sessions."""
    if len(closes) < QM_MIN_BARS:
        return None
    start = closes[-QM_LOOKBACK_SESSIONS]
    end = closes[-QM_SKIP_SESSIONS]
    if start <= 0 or end <= 0:
        return None
    return (end / start) - 1.0


def one_month_return(closes: Sequence[float]) -> Optional[float]:
    if len(closes) < QM_SKIP_SESSIONS + 1:
        return None
    start = closes[-QM_SKIP_SESSIONS - 1]
    end = closes[-1]
    if start <= 0 or end <= 0:
        return None
    return (end / start) - 1.0


def trailing_return(closes: Sequence[float], sessions: int) -> Optional[float]:
    if sessions <= 0 or len(closes) < sessions + 1:
        return None
    start = closes[-(sessions + 1)]
    end = closes[-1]
    if start <= 0 or end <= 0:
        return None
    return (end / start) - 1.0


def smoothness_up_week_fraction(closes: Sequence[float]) -> Optional[float]:
    """Share of up-weeks in the 12-2 formation window (skip last ~21 sessions)."""
    if len(closes) < QM_MIN_BARS:
        return None
    window = list(closes[:-QM_SKIP_SESSIONS])
    flags: List[bool] = []
    for index in range(0, len(window) - 4, 5):
        chunk = window[index : index + 5]
        if len(chunk) < 2 or chunk[0] <= 0:
            continue
        flags.append(chunk[-1] > chunk[0])
    if len(flags) < 8:
        return None
    return sum(1 for flag in flags if flag) / float(len(flags))


def h52_ratio(last: Optional[float], week52_high: Optional[float]) -> Optional[float]:
    if last is None or week52_high is None or week52_high <= 0 or last <= 0:
        return None
    return last / week52_high


def band_h52(ratio: Optional[float]) -> Optional[float]:
    if ratio is None:
        return None
    if ratio <= 0.70:
        return 0.0
    if ratio >= 1.0:
        return 100.0
    return 100.0 * (ratio - 0.70) / 0.30


def percentile_rank(value: Optional[float], universe: Sequence[float]) -> Optional[float]:
    if value is None or not universe:
        return None
    below = sum(1 for item in universe if item < value)
    if len(universe) == 1:
        return 100.0
    return 100.0 * below / float(len(universe) - 1)


def adv_inr(last: Optional[float], avg_volume: Optional[float]) -> Optional[float]:
    if last is None or avg_volume is None or last <= 0 or avg_volume <= 0:
        return None
    return last * avg_volume


def _renormalize(parts: Dict[str, Optional[float]], weights: Dict[str, float]) -> Optional[float]:
    used = {
        key: float(score)
        for key, score in parts.items()
        if score is not None and key in weights
    }
    if not used:
        return None
    weight_sum = sum(weights[key] for key in used)
    if weight_sum <= 0:
        return None
    blended = sum(used[key] * weights[key] for key in used) / weight_sum
    return min(max(blended, 0.0), 100.0)


def score_momentum_qm(
    *,
    r122_percentile: Optional[float] = None,
    smoothness: Optional[float] = None,
    h52: Optional[float] = None,
    earn_percentile: Optional[float] = None,
) -> Tuple[Optional[int], List[str], Dict[str, Optional[float]]]:
    notes: List[str] = []
    parts: Dict[str, Optional[float]] = {
        "r122": None if r122_percentile is None else min(max(float(r122_percentile), 0.0), 100.0),
        "smooth": None if smoothness is None else min(max(float(smoothness) * 100.0, 0.0), 100.0),
        "h52": band_h52(h52),
        "earn": None if earn_percentile is None else min(max(float(earn_percentile), 0.0), 100.0),
    }
    notes.append(f"12-2 pctl {r122_percentile:.0f}" if r122_percentile is not None else "12-2 —")
    notes.append(
        f"Up-weeks {smoothness:.0%}" if smoothness is not None else "Path smoothness —"
    )
    notes.append(f"52w {h52:.2f}" if h52 is not None else "52w closeness —")
    notes.append(
        f"EPS rev pctl {earn_percentile:.0f}" if earn_percentile is not None else "EPS revision —"
    )
    blended = _renormalize(parts, QM_WEIGHTS)
    if blended is None:
        return None, notes, parts
    return int(round(blended)), notes, parts


def qm_flags(
    *,
    r1m_percentile: Optional[float] = None,
    adv: Optional[float] = None,
    listed_sessions: Optional[int] = None,
    asm_gsm: Optional[bool] = None,
    regime_off: Optional[bool] = None,
) -> List[Dict[str, str]]:
    flags: List[Dict[str, str]] = []
    if r1m_percentile is not None and r1m_percentile >= QM_SQUEEZE_PERCENTILE:
        flags.append({
            "id": "qm_squeeze",
            "label": "Skip-month warning",
            "detail": "1-month return is in the top 5% of this universe — possible squeeze",
        })
    if adv is not None and adv < QM_ADV_FLOOR_INR:
        flags.append({
            "id": "qm_low_adv",
            "label": "Low ADV",
            "detail": f"ADV about ₹{adv / 10**7:.1f} Cr — below the ₹8 Cr liquidity floor",
        })
    if listed_sessions is not None and listed_sessions < QM_MIN_BARS:
        flags.append({
            "id": "qm_short_history",
            "label": "Short history",
            "detail": "Fewer than 252 sessions — 12-2 not computed",
        })
    if asm_gsm:
        flags.append({
            "id": "qm_asm_gsm",
            "label": "ASM/GSM",
            "detail": "Surveillance list when the flag is present",
        })
    if regime_off:
        flags.append({
            "id": "qm_late_cycle",
            "label": "QM late-cycle",
            "detail": "Nifty 12-month return is not positive in this snapshot",
        })
    return flags


def attach_qm_cross_section(rows: Sequence[Dict[str, Any]]) -> None:
    r122_universe = [
        float(row["r122"])
        for row in rows
        if _safe_float(row.get("r122")) is not None
    ]
    r1m_universe = [
        float(row["r1m"])
        for row in rows
        if _safe_float(row.get("r1m")) is not None
    ]
    earn_universe = [
        float(row["epsRevision3m"])
        for row in rows
        if _safe_float(row.get("epsRevision3m")) is not None
    ]
    ranked = sorted(
        (
            row
            for row in rows
            if _safe_float(row.get("r122")) is not None
        ),
        key=lambda item: float(item["r122"]),
        reverse=True,
    )
    rank_map = {id(row): index + 1 for index, row in enumerate(ranked)}
    for row in rows:
        row["r122Pct"] = percentile_rank(_safe_float(row.get("r122")), r122_universe)
        row["r1mPct"] = percentile_rank(_safe_float(row.get("r1m")), r1m_universe)
        row["earnPct"] = percentile_rank(_safe_float(row.get("epsRevision3m")), earn_universe)
        row["qmRank"] = rank_map.get(id(row))
        row["qmUniverse"] = len(ranked)


def features_from_closes(
    closes: Sequence[float],
    *,
    last: Optional[float] = None,
    week52_high: Optional[float] = None,
) -> Dict[str, Any]:
    price = last if last is not None else (closes[-1] if closes else None)
    high = week52_high
    if high is None and closes:
        high = max(closes[-QM_LOOKBACK_SESSIONS:]) if len(closes) >= 2 else max(closes)
    return {
        "r122": generic_momentum_12_2(closes),
        "r1m": one_month_return(closes),
        "smooth": smoothness_up_week_fraction(closes),
        "h52": h52_ratio(price, high),
        "listedSessions": len(closes),
        "nifty12m": None,
    }


def cached_closes(symbol: str) -> List[float]:
    key = (symbol or "").strip().upper()
    if not key:
        return []
    with _HISTORY_LOCK:
        cached = _HISTORY_CACHE.get(key)
    if not cached:
        return []
    ts, closes = cached
    if (time.time() - ts) > QM_HISTORY_TTL_SECONDS:
        return []
    return list(closes)


def store_closes(symbol: str, closes: Sequence[float]) -> None:
    key = (symbol or "").strip().upper()
    if not key or not closes:
        return
    with _HISTORY_LOCK:
        _HISTORY_CACHE[key] = (time.time(), list(closes))


def cached_qm_features(
    symbol: str,
    *,
    last: Optional[float] = None,
    week52_high: Optional[float] = None,
) -> Dict[str, Any]:
    closes = cached_closes(symbol)
    if not closes:
        return {
            "r122": None,
            "r1m": None,
            "smooth": None,
            "h52": h52_ratio(last, week52_high),
            "listedSessions": None,
        }
    return features_from_closes(closes, last=last, week52_high=week52_high)


def _fetch_symbol_closes(symbol: str) -> List[float]:
    try:
        from .market_data import fetch_quote_history

        candles = fetch_quote_history(symbol, period="1y", interval="1d") or []
    except Exception as exc:
        logger.debug("qm.history_failed symbol=%s reason=%s", symbol, exc)
        return []
    closes = closes_from_candles(candles)
    if closes:
        store_closes(symbol, closes)
    return closes


def fill_qm_history_batch(symbols: Sequence[str], limit: int = 15) -> None:
    seen = set()
    filled = 0
    for raw in symbols:
        key = str(raw or "").strip().upper()
        if not key or key in seen:
            continue
        seen.add(key)
        if cached_closes(key):
            continue
        _fetch_symbol_closes(key)
        filled += 1
        if filled >= max(1, int(limit)):
            break


def schedule_qm_history_fill(symbols: Sequence[str], limit: int = 15) -> None:
    global _FILL_RUNNING
    pending = [str(sym or "").strip().upper() for sym in symbols if str(sym or "").strip()]
    if not pending:
        return
    with _FILL_LOCK:
        if _FILL_RUNNING:
            return
        _FILL_RUNNING = True

    def _run() -> None:
        global _FILL_RUNNING
        try:
            fill_qm_history_batch(pending, limit=limit)
        finally:
            with _FILL_LOCK:
                _FILL_RUNNING = False

    threading.Thread(target=_run, daemon=True, name="qm-history-fill").start()


def nifty_absolute_regime(closes: Sequence[float]) -> Optional[str]:
    """On if 12-month Nifty return > 0. Off if computed and ≤ 0. None if missing."""
    ret = trailing_return(closes, 252)
    if ret is None:
        return None
    return "on" if ret > 0 else "off"


def apply_qm_to_rows(rows: Sequence[Dict[str, Any]], *, regime: Optional[str] = None) -> None:
    for row in rows:
        if row.get("r122") is None or row.get("smooth") is None or row.get("h52") is None:
            features = cached_qm_features(
                str(row.get("symbol") or ""),
                last=_safe_float(row.get("last")),
                week52_high=_safe_float(row.get("fiftyTwoWeekHigh")),
            )
            for key, value in features.items():
                if row.get(key) is None and value is not None:
                    row[key] = value
        if row.get("h52") is None:
            row["h52"] = h52_ratio(
                _safe_float(row.get("last")),
                _safe_float(row.get("fiftyTwoWeekHigh")),
            )
        row["advInr"] = adv_inr(
            _safe_float(row.get("last")),
            _safe_float(row.get("avgVolume")),
        )
    attach_qm_cross_section(rows)
    regime_off = regime == "off"
    for row in rows:
        flags = qm_flags(
            r1m_percentile=_safe_float(row.get("r1mPct")),
            adv=_safe_float(row.get("advInr")),
            listed_sessions=row.get("listedSessions"),
            asm_gsm=bool(row.get("asmGsm")),
            regime_off=regime_off,
        )
        row["qmFlags"] = flags


def evaluate_quality_gates(
    *,
    roe: Optional[float] = None,
    debt_to_equity: Optional[float] = None,
    interest_coverage: Optional[float] = None,
    pledge: Optional[float] = None,
    asm_gsm: Optional[bool] = None,
    sector: str = "Other",
    eps_years: Optional[int] = None,
    loss_streak: Optional[int] = None,
) -> Dict[str, Any]:
    """Binary quality bar. Missing fields are skipped, never invented."""
    checks: List[Dict[str, Any]] = []
    bank_like = (sector or "Other") in BANK_LIKE_SECTORS

    if roe is None:
        checks.append({"id": "roe", "status": "skip", "note": "ROE —"})
    elif roe >= QUALITY_GATE_MIN_ROE:
        checks.append({"id": "roe", "status": "pass", "note": f"ROE {roe:.0f}%"})
    else:
        checks.append({"id": "roe", "status": "fail", "note": f"ROE {roe:.0f}% < 12%"})

    if bank_like:
        checks.append({"id": "de", "status": "skip", "note": "D/E n/a (financial)"})
        checks.append({"id": "interestCover", "status": "skip", "note": "Interest cover n/a (financial)"})
    else:
        if debt_to_equity is None:
            checks.append({"id": "de", "status": "skip", "note": "D/E —"})
        elif debt_to_equity <= QUALITY_GATE_MAX_DE:
            checks.append({"id": "de", "status": "pass", "note": f"D/E {debt_to_equity:.2f}"})
        else:
            checks.append({"id": "de", "status": "fail", "note": f"D/E {debt_to_equity:.2f} > 1.5"})
        if interest_coverage is None:
            checks.append({"id": "interestCover", "status": "skip", "note": "Interest cover —"})
        elif interest_coverage >= QUALITY_GATE_MIN_INTEREST_COVER:
            checks.append({"id": "interestCover", "status": "pass", "note": f"Cover {interest_coverage:.1f}x"})
        else:
            checks.append({"id": "interestCover", "status": "fail", "note": f"Cover {interest_coverage:.1f}x < 2"})

    if pledge is None:
        checks.append({"id": "pledge", "status": "skip", "note": "Pledge —"})
    elif pledge < QUALITY_GATE_MAX_PLEDGE:
        checks.append({"id": "pledge", "status": "pass", "note": f"Pledge {pledge:.0f}%"})
    else:
        checks.append({"id": "pledge", "status": "fail", "note": f"Pledge {pledge:.0f}% ≥ 20%"})

    if asm_gsm:
        checks.append({"id": "asmGsm", "status": "fail", "note": "ASM/GSM"})
    else:
        checks.append({"id": "asmGsm", "status": "skip" if asm_gsm is None else "pass", "note": "ASM/GSM —" if asm_gsm is None else "Not on ASM/GSM"})

    if eps_years is None:
        checks.append({"id": "epsHistory", "status": "skip", "note": "EPS years —"})
    elif int(eps_years) >= QUALITY_GATE_MIN_EPS_YEARS:
        checks.append({"id": "epsHistory", "status": "pass", "note": f"{int(eps_years)}Y EPS"})
    else:
        checks.append({"id": "epsHistory", "status": "fail", "note": f"{int(eps_years)}Y EPS < 3"})

    if loss_streak is None:
        checks.append({"id": "losses", "status": "skip", "note": "Loss streak —"})
    elif int(loss_streak) <= 0:
        checks.append({"id": "losses", "status": "pass", "note": "No loss streak"})
    else:
        checks.append({"id": "losses", "status": "fail", "note": f"Loss streak {int(loss_streak)}"})

    failed = [item for item in checks if item["status"] == "fail"]
    passed = [item for item in checks if item["status"] == "pass"]
    roe_ok = next((item["status"] == "pass" for item in checks if item["id"] == "roe"), False)
    return {
        "passed": (not failed) and roe_ok,
        "failed": len(failed),
        "skipped": sum(1 for item in checks if item["status"] == "skip"),
        "checks": checks,
        "failures": [item["id"] for item in failed],
        "note": (
            "Quality bar passed"
            if (not failed) and roe_ok
            else ("Quality bar failed: " + ", ".join(item["id"] for item in failed) if failed else "ROE required and missing")
        ),
    }


def qm_style_badge(quality: Optional[int], momentum: Optional[int]) -> Optional[str]:
    """Analysis label from Q and M bands. Does not mix the two numbers."""
    if quality is None or momentum is None:
        return None
    if quality >= 70 and momentum >= 70:
        return QM_BADGE_QUALITY_MOMENTUM
    if quality >= 70 and momentum < 70:
        return QM_BADGE_QUALITY_MIXED
    if quality < 50 and momentum >= 70:
        return QM_BADGE_SPECULATIVE
    if quality < 50 and momentum < 50:
        return QM_BADGE_WEAK
    return None


def sequential_qm_sleeve(
    rows: Sequence[Dict[str, Any]],
    *,
    limit: int = QM_SLEEVE_SIZE,
    sector_cap: float = QM_SECTOR_CAP,
) -> List[Dict[str, Any]]:
    """Quality survivors only, then 12-2 / M rank, sector-capped."""
    survivors = [row for row in rows if (row.get("qualityGate") or {}).get("passed")]
    attach_qm_cross_section(survivors)
    for index, row in enumerate(
        sorted(
            survivors,
            key=lambda item: (
                _safe_float(item.get("r122")) is not None,
                _safe_float(item.get("r122")) or -999,
                int(item.get("momentum") or 0),
            ),
            reverse=True,
        )
    ):
        row["qmSleeveRank"] = index + 1
    ranked = sorted(
        survivors,
        key=lambda item: (int(item.get("qmSleeveRank") or 9999), str(item.get("symbol") or "")),
    )
    cap = max(1, int(round(max(limit, 1) * sector_cap)))
    picked: List[Dict[str, Any]] = []
    sector_counts: Dict[str, int] = {}
    for row in ranked:
        sector = str((row.get("metrics") or {}).get("sector") or row.get("sector") or "Other")
        if sector_counts.get(sector, 0) >= cap:
            continue
        picked.append(row)
        sector_counts[sector] = sector_counts.get(sector, 0) + 1
        if len(picked) >= limit:
            break
    return picked
