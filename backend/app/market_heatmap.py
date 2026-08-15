"""
BYSEL Smart Sentiment Heatmap Engine

Sector-wise market visualization showing:
  - Real-time sector performance aggregation
  - Individual stock performance within sectors
  - Market breadth (advances vs declines)
  - Sector rotation signals
  - Market mood indicator
"""

import json
import logging
import os
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
from .market_data import INDIAN_STOCKS, fetch_quote, fetch_quotes

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────
# HEATMAP CACHE
# Open market: 2s TTL + stale-while-revalidate so clients polling
# every 2–5s always get a fast response while quotes refresh (~5s).
# Closed market: long TTL (snapshot does not change).
# ──────────────────────────────────────────────────────────────
_HEATMAP_CACHE = {"data": None, "timestamp": 0}
_HEATMAP_CACHE_TTL_OPEN = float(os.getenv("HEATMAP_CACHE_TTL_OPEN", "2"))
# After hours the tape is frozen — serve the same snapshot for the rest of the evening.
_HEATMAP_CACHE_TTL_CLOSED = float(os.getenv("HEATMAP_CACHE_TTL_CLOSED", "21600"))
# Full-universe quotes cannot refresh every 1–2s (Yahoo rate limits).
# Heatmap payload still caches ~2s; underlying quotes refresh on this TTL.
_HEATMAP_QUOTE_MAX_AGE_OPEN = float(os.getenv("HEATMAP_QUOTE_MAX_AGE_OPEN", "5"))
_HEATMAP_QUOTE_STALE_ACCEPT = float(os.getenv("HEATMAP_QUOTE_STALE_ACCEPT", "300"))
_HEATMAP_QUOTE_REFRESH_BUDGET = int(os.getenv("HEATMAP_QUOTE_REFRESH_BUDGET", "120"))
_HEATMAP_TILES_PER_SECTOR = int(os.getenv("HEATMAP_TILES_PER_SECTOR", "16"))
_HEATMAP_REFRESH_OFFSET = 0
# Backward-compatible alias used by older tests/patches.
_HEATMAP_CACHE_TTL = _HEATMAP_CACHE_TTL_OPEN
_HEATMAP_SNAPSHOT_PATH = Path(
    os.getenv(
        "HEATMAP_SNAPSHOT_PATH",
        str(Path(__file__).resolve().parent.parent / ".cache" / "market_heatmap_snapshot.json"),
    )
)
_HEATMAP_REFRESH_LOCK = threading.Lock()
_HEATMAP_REFRESH_IN_FLIGHT = False


# ──────────────────────────────────────────────────────────────
# SECTOR DEFINITIONS (curated for heatmap visualization)
# ──────────────────────────────────────────────────────────────

SECTOR_STOCKS = {
    "Banking": [
        "HDFCBANK", "ICICIBANK", "SBIN", "KOTAKBANK", "AXISBANK",
        "INDUSINDBK", "PNB", "BANKBARODA", "CANBK", "FEDERALBNK",
        "IDFCFIRSTB", "BANDHANBNK", "AUBANK",
    ],
    "IT": [
        "TCS", "INFY", "WIPRO", "HCLTECH", "TECHM",
        "LTIM", "MPHASIS", "COFORGE", "PERSISTENT", "LTTS",
    ],
    "Semiconductor": [
        "MOSCHIP", "KAYNES", "SYRMA", "DIXON", "AVALON",
        "CYIENTDLM", "CGPOWER", "TATAELXSI", "CYIENT", "RIR",
        "PGEL", "CENTUM", "SPELS",
    ],
    "Pharma": [
        "SUNPHARMA", "DRREDDY", "CIPLA", "DIVISLAB", "LUPIN",
        "AUROPHARMA", "BIOCON", "TORNTPHARM", "ALKEM",
    ],
    "Auto": [
        "TMPV", "TMCV", "MARUTI", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT",
        "TVSMOTOR", "ASHOKLEY", "MOTHERSON", "MRF",
    ],
    "FMCG": [
        "HINDUNILVR", "ITC", "NESTLEIND", "BRITANNIA", "DABUR",
        "MARICO", "COLPAL", "GODREJCP", "TATACONSUM",
    ],
    "Energy": [
        "RELIANCE", "ONGC", "BPCL", "IOC", "NTPC",
        "POWERGRID", "TATAPOWER", "ADANIGREEN", "GAIL", "COALINDIA",
    ],
    "Metals": [
        "TATASTEEL", "JSWSTEEL", "HINDALCO", "VEDL", "SAIL",
        "NATIONALUM", "JINDALSTEL", "NMDC",
    ],
    "Infra": [
        "LT", "ADANIPORTS", "ADANIENT", "IRCON", "RVNL",
        "NBCC", "NCC", "ULTRACEMCO", "AMBUJACEM",
    ],
    "Finance": [
        "BAJFINANCE", "BAJAJFINSV", "HDFCLIFE", "SBILIFE",
        "ICICIPRULI", "CHOLAFIN", "MUTHOOTFIN", "SHRIRAMFIN",
    ],
    "Realty": [
        "DLF", "GODREJPROP", "OBEROIRLTY", "PRESTIGE",
        "BRIGADE", "LODHA", "SOBHA",
    ],
    "Defence": [
        "HAL", "BEL", "BDL", "MAZDOCK", "COCHINSHIP",
    ],
    "Consumer": [
        "TITAN", "TRENT", "HAVELLS", "VOLTAS",
        "CROMPTON", "BATAINDIA", "PAGEIND",
    ],
    "Telecom": [
        "BHARTIARTL", "IDEA",
    ],
    "Chemicals": [
        "PIDILITIND", "ASIANPAINT", "SRF",
        "DEEPAKNTR", "NAVINFLUOR", "CLEAN",
    ],
}


def get_market_heatmap() -> Dict:
    """
    Generate a complete market heatmap with sector-wise data.

    While the market is open, serve a 1–2s cache and refresh in the background
    so Android clients polling every 1–2s always get a fast response.

    When the market is closed the tape (and TQI) is frozen:
      1) Serve the in-memory / persisted last-session snapshot
      2) Rebuild from Yahoo only once if no snapshot exists yet
      Never keep walking a rotating quote window after hours.
    """
    now = time.time()
    market_open = _is_nse_market_open()
    ttl = _HEATMAP_CACHE_TTL_OPEN if market_open else _HEATMAP_CACHE_TTL_CLOSED
    cached = _HEATMAP_CACHE["data"]
    age = now - float(_HEATMAP_CACHE.get("timestamp") or 0)

    if cached and age < ttl:
        return cached

    # Open market: never block the request on a full Yahoo rebuild.
    # Return the previous snapshot immediately and refresh in the background.
    if market_open and cached:
        _schedule_heatmap_refresh(market_open=True)
        return cached

    if not market_open:
        frozen = _closed_session_snapshot()
        if frozen:
            stamped = _stamp_stale(
                frozen,
                market_open=False,
                reason="Market closed — TQI frozen at last session snapshot.",
            )
            if not _snapshot_covers_curated_sectors(frozen):
                _HEATMAP_CACHE["data"] = stamped
                _HEATMAP_CACHE["timestamp"] = now
                _schedule_heatmap_refresh(market_open=False)
            else:
                return _publish_heatmap(stamped, now=now)
            return stamped

        # Never block the HTTP request on Yahoo. Rebuild last-session quotes
        # once in the background, then freeze.
        empty = _empty_heatmap_payload(
            mood_desc="Market is closed — loading last-session heatmap."
        )
        empty["isStale"] = True
        empty["marketOpen"] = False
        empty["staleReason"] = empty["moodDescription"]
        _HEATMAP_CACHE["data"] = empty
        _HEATMAP_CACHE["timestamp"] = now
        _schedule_heatmap_refresh(market_open=False)
        return empty

    # Open market, no cache yet — never block the client on a full Yahoo rebuild.
    # Serve last disk snapshot (or a light empty shell) and refresh in background.
    persisted = _load_persisted_heatmap_snapshot()
    if persisted:
        stamped = _stamp_stale(
            persisted,
            market_open=True,
            reason="Warming live heatmap — showing last saved snapshot.",
        )
        _HEATMAP_CACHE["data"] = stamped
        _HEATMAP_CACHE["timestamp"] = now
        _schedule_heatmap_refresh(market_open=True)
        return stamped

    empty = _empty_heatmap_payload(
        mood_desc="Market data is warming up — heatmap tiles will fill in shortly."
    )
    empty["isStale"] = True
    empty["marketOpen"] = True
    empty["staleReason"] = empty["moodDescription"]
    _HEATMAP_CACHE["data"] = empty
    _HEATMAP_CACHE["timestamp"] = now
    _schedule_heatmap_refresh(market_open=True)
    return empty


def kick_heatmap_refresh() -> None:
    """Non-blocking warmup hook so keepalive can fill tiles before the user opens Heatmap.

    After hours, a valid snapshot is already the close print — do not rebuild,
    or TQI/breadth will keep drifting as Yahoo's rotating window fills.
    """
    market_open = _is_nse_market_open()
    if not market_open and _snapshot_covers_curated_sectors(_closed_session_snapshot()):
        return
    _schedule_heatmap_refresh(market_open=market_open)


def _schedule_heatmap_refresh(*, market_open: bool) -> None:
    global _HEATMAP_REFRESH_IN_FLIGHT
    with _HEATMAP_REFRESH_LOCK:
        if _HEATMAP_REFRESH_IN_FLIGHT:
            return
        _HEATMAP_REFRESH_IN_FLIGHT = True

    def _runner():
        global _HEATMAP_REFRESH_IN_FLIGHT
        try:
            _refresh_heatmap_sync(market_open=market_open)
        finally:
            with _HEATMAP_REFRESH_LOCK:
                _HEATMAP_REFRESH_IN_FLIGHT = False

    threading.Thread(target=_runner, name="heatmap-refresh", daemon=True).start()


def _warm_heatmap_universe_async() -> None:
    def _warm():
        try:
            from .heatmap_universe import build_heatmap_universe

            build_heatmap_universe()
        except Exception as exc:
            logger.warning("heatmap.universe_warm_failed reason=%s", exc)

    threading.Thread(target=_warm, name="heatmap-universe", daemon=True).start()


def _quoted_count(payload: Optional[Dict]) -> int:
    if not isinstance(payload, dict):
        return 0
    if payload.get("quotedCount"):
        return int(payload.get("quotedCount") or 0)
    breadth = payload.get("marketBreadth") or {}
    return int(breadth.get("total") or 0)


def _snapshot_sector_names(payload: Optional[Dict]) -> set:
    if not isinstance(payload, dict):
        return set()
    return {
        str(sector.get("name") or "")
        for sector in (payload.get("sectors") or [])
        if isinstance(sector, dict) and sector.get("name")
    }


def _snapshot_covers_curated_sectors(payload: Optional[Dict]) -> bool:
    return bool(payload) and set(SECTOR_STOCKS).issubset(_snapshot_sector_names(payload))


def _merge_missing_curated_sectors(base: Dict, fresh: Dict) -> Dict:
    """Keep the frozen close print, but splice in newly added heatmap buckets."""
    merged = dict(base)
    existing = _snapshot_sector_names(merged)
    extras = [
        dict(sector)
        for sector in (fresh.get("sectors") or [])
        if isinstance(sector, dict)
        and sector.get("name") in SECTOR_STOCKS
        and sector.get("name") not in existing
        and sector.get("stocks")
    ]
    if extras:
        merged["sectors"] = list(merged.get("sectors") or []) + extras
    return merged


def _closed_session_snapshot() -> Optional[Dict]:
    cached = _HEATMAP_CACHE.get("data")
    if _is_valid_heatmap_snapshot(cached):
        return cached
    return _load_persisted_heatmap_snapshot()


def _publish_heatmap(payload: Dict, *, now: float) -> Dict:
    current = _HEATMAP_CACHE.get("data")
    if (
        _is_valid_heatmap_snapshot(current)
        and _is_valid_heatmap_snapshot(payload)
        and _quoted_count(current) > 0
        and _quoted_count(payload) < _quoted_count(current) * 0.6
    ):
        # Never replace a fuller tape with a thinner leaders-only pass — that
        # is what made TQI jump on every refresh interval.
        return current
    _HEATMAP_CACHE["data"] = payload
    _HEATMAP_CACHE["timestamp"] = now
    return payload


def _refresh_heatmap_sync(*, market_open: bool) -> Dict:
    now = time.time()
    try:
        if not market_open:
            frozen = _closed_session_snapshot()
            if frozen and _snapshot_covers_curated_sectors(frozen):
                stamped = _stamp_stale(
                    frozen,
                    market_open=False,
                    reason="Market closed — TQI frozen at last session snapshot.",
                )
                return _publish_heatmap(stamped, now=now)

            payload = _build_heatmap_from_quotes(market_open=False, leaders_only=True)
            if frozen and _is_valid_heatmap_snapshot(payload):
                merged = _merge_missing_curated_sectors(frozen, payload)
                stamped = _stamp_stale(
                    merged,
                    market_open=False,
                    reason="Market closed — TQI frozen at last session snapshot.",
                )
                _persist_heatmap_snapshot(stamped)
                return _publish_heatmap(stamped, now=time.time())

            # One-shot last-session rebuild. Leaders only so the universe (and
            # TQI) is stable — do not walk the rotating full-catalog window.
            if _is_valid_heatmap_snapshot(payload):
                stamped = _stamp_stale(
                    payload,
                    market_open=False,
                    reason="Market closed — TQI frozen at last session snapshot.",
                )
                _persist_heatmap_snapshot(stamped)
                return _publish_heatmap(stamped, now=time.time())
            empty = _empty_heatmap_payload(
                mood_desc="Market closed — last-session heatmap is unavailable."
            )
            empty["staleReason"] = empty["moodDescription"]
            return empty

        # Overlap NSE/BSE catalog I/O with the fast curated-leader Yahoo fetch.
        _warm_heatmap_universe_async()
        current = _HEATMAP_CACHE.get("data")
        if not _is_valid_heatmap_snapshot(current):
            leaders = _build_heatmap_from_quotes(market_open=True, leaders_only=True)
            if _is_valid_heatmap_snapshot(leaders):
                _persist_heatmap_snapshot(leaders)
                _publish_heatmap(leaders, now=time.time())

        result = _build_heatmap_from_quotes(market_open=True, leaders_only=False)
        if _is_valid_heatmap_snapshot(result):
            _persist_heatmap_snapshot(result)
            return _publish_heatmap(result, now=time.time())

        persisted = _load_persisted_heatmap_snapshot()
        if persisted:
            stamped = _stamp_stale(
                persisted,
                market_open=market_open,
                reason="Live heatmap incomplete — showing last saved snapshot.",
            )
            return _publish_heatmap(stamped, now=time.time())

        cached = _HEATMAP_CACHE.get("data")
        if _is_valid_heatmap_snapshot(cached):
            return cached
        return result
    except Exception as exc:
        logger.error("heatmap.live_build_failed reason=%s", exc)
        persisted = _load_persisted_heatmap_snapshot()
        if persisted:
            stamped = _stamp_stale(
                persisted,
                market_open=market_open,
                reason="Live heatmap failed — showing last saved snapshot.",
            )
            return _publish_heatmap(stamped, now=now)
        cached = _HEATMAP_CACHE.get("data")
        if _is_valid_heatmap_snapshot(cached):
            return cached
        empty = _empty_heatmap_payload(mood_desc=f"Heatmap temporarily unavailable ({exc}).")
        empty["staleReason"] = empty["moodDescription"]
        return empty


def _stamp_stale(payload: Dict, *, market_open: bool, reason: str) -> Dict:
    stamped = dict(payload)
    stamped["isStale"] = True
    stamped["marketOpen"] = market_open
    stamped["staleReason"] = reason
    return stamped


def _curated_leader_symbols() -> List[str]:
    """HDFCBANK / TCS / RELIANCE-class names the heatmap tiles should paint first."""
    seen: List[str] = []
    seen_set = set()
    for symbols in SECTOR_STOCKS.values():
        for sym in symbols:
            key = str(sym or "").strip().upper()
            if not key or key in seen_set:
                continue
            seen_set.add(key)
            seen.append(key)
    return seen


def _curated_sector_symbols() -> Dict[str, List[str]]:
    return {name: list(symbols) for name, symbols in SECTOR_STOCKS.items() if symbols}


def _fetch_heatmap_quotes(symbols: List[str]) -> List[dict]:
    """Yahoo pull tuned for heatmap: larger batches, threads on, no per-symbol fallback."""
    if not symbols:
        return []
    try:
        return fetch_quotes(
            symbols,
            max_age_seconds=0,
            batch_size=max(80, int(os.getenv("QUOTE_BATCH_SIZE", "40") or 40)),
            yf_threads=True,
            individual_fallback=False,
        )
    except TypeError:
        return fetch_quotes(symbols, max_age_seconds=0)


def _build_heatmap_from_quotes(*, market_open: bool, leaders_only: bool = False) -> Dict:
    """Assemble heatmap from quotes.

    First pass (`leaders_only=True`) uses the curated ~130 names so tiles paint
    without waiting on the full NSE/BSE catalog or an alphabetical Yahoo walk.
    Later passes cover the rest of the universe on a rotating budget.
    """
    global _HEATMAP_REFRESH_OFFSET
    from .heatmap_universe import get_heatmap_sector_symbols, cached_universe_size
    from .market_data import _quote_cache

    if leaders_only:
        sector_symbols = _curated_sector_symbols()
    else:
        try:
            sector_symbols = get_heatmap_sector_symbols()
        except Exception as exc:
            logger.warning("heatmap.universe_unavailable reason=%s", exc)
            sector_symbols = _curated_sector_symbols()

    all_symbols = sorted({sym for symbols in sector_symbols.values() for sym in symbols})
    fresh_age = _HEATMAP_QUOTE_MAX_AGE_OPEN if market_open else _HEATMAP_QUOTE_STALE_ACCEPT

    quotes_dict: Dict[str, dict] = {}
    missing: List[str] = []
    for sym in all_symbols:
        quote = _quote_cache.get(sym, max_age_seconds=fresh_age)
        if quote:
            quotes_dict[sym] = quote
            continue
        # Paint last print immediately, but always enqueue a Yahoo refresh
        # when the cache is older than the live TTL (was skipping refresh
        # whenever a 5-minute-old quote still existed).
        stale = _quote_cache.get_allow_stale(sym, _HEATMAP_QUOTE_STALE_ACCEPT)
        if stale:
            quotes_dict[sym] = stale
        missing.append(sym)

    if missing:
        missing_set = set(missing)
        leaders = [sym for sym in _curated_leader_symbols() if sym in missing_set]
        rest = [sym for sym in missing if sym not in set(leaders)]
        window: List[str] = list(leaders)
        if rest and not leaders_only:
            start = _HEATMAP_REFRESH_OFFSET % len(rest)
            budget = max(1, _HEATMAP_QUOTE_REFRESH_BUDGET)
            tail = [rest[(start + i) % len(rest)] for i in range(min(budget, len(rest)))]
            _HEATMAP_REFRESH_OFFSET = start + len(tail)
            window.extend(tail)
        fetched = _fetch_heatmap_quotes(window)
        for quote in fetched or []:
            if isinstance(quote, dict) and quote.get("symbol"):
                quotes_dict[quote["symbol"]] = quote

    sectors_data = []
    all_advances = 0
    all_declines = 0
    all_unchanged = 0
    total_stocks = 0

    for sector_name, symbols in sector_symbols.items():
        sector_result = _analyze_sector(
            sector_name,
            symbols,
            quotes_dict,
            tile_limit=_HEATMAP_TILES_PER_SECTOR,
        )
        if sector_result["totalStocks"] <= 0 and not sector_result.get("stocks"):
            continue
        sectors_data.append(sector_result)
        all_advances += sector_result["advances"]
        all_declines += sector_result["declines"]
        all_unchanged += sector_result["unchanged"]
        total_stocks += sector_result["totalStocks"]

    sectors_data.sort(key=lambda x: x["avgChange"], reverse=True)

    if total_stocks > 0:
        advance_ratio = all_advances / total_stocks
    else:
        advance_ratio = 0.5

    if advance_ratio >= 0.7:
        mood, mood_emoji = "EUPHORIC", "🚀"
        mood_desc = "Markets are on fire! Strong buying across sectors."
    elif advance_ratio >= 0.55:
        mood, mood_emoji = "BULLISH", "🟢"
        mood_desc = "Positive sentiment with broad-based buying."
    elif advance_ratio >= 0.45:
        mood, mood_emoji = "NEUTRAL", "🟡"
        mood_desc = "Mixed signals. Markets are indecisive."
    elif advance_ratio >= 0.3:
        mood, mood_emoji = "BEARISH", "🔴"
        mood_desc = "Selling pressure across multiple sectors."
    else:
        mood, mood_emoji = "FEARFUL", "⚫"
        mood_desc = "Heavy selling! Markets in panic mode."

    best_sector = sectors_data[0] if sectors_data else None
    worst_sector = sectors_data[-1] if sectors_data else None
    catalog_size = cached_universe_size() or len(all_symbols)

    return {
        "sectors": sectors_data,
        "marketBreadth": {
            "advances": all_advances,
            "declines": all_declines,
            "unchanged": all_unchanged,
            "total": total_stocks,
            "advanceRatio": round(advance_ratio, 3),
        },
        "mood": mood,
        "moodEmoji": mood_emoji,
        "moodDescription": mood_desc,
        "bestSector": {
            "name": best_sector["name"] if best_sector else "N/A",
            "change": best_sector["avgChange"] if best_sector else 0,
        },
        "worstSector": {
            "name": worst_sector["name"] if worst_sector else "N/A",
            "change": worst_sector["avgChange"] if worst_sector else 0,
        },
        "lastUpdated": datetime.utcnow().isoformat(),
        "marketOpen": market_open,
        "isStale": not market_open,
        "universeSize": catalog_size,
        "quotedCount": total_stocks,
        "tilesPerSector": _HEATMAP_TILES_PER_SECTOR,
        "pendingQuotes": len(missing),
    }


def _empty_heatmap_payload(mood_desc: str = "No heatmap data available.") -> Dict:
    from .heatmap_universe import HEATMAP_SECTOR_ORDER, cached_universe_size

    try:
        catalog_size = cached_universe_size()
    except Exception:
        catalog_size = 0

    return {
        "sectors": [
            {
                "name": name,
                "avgChange": 0.0,
                "advances": 0,
                "declines": 0,
                "unchanged": 0,
                "totalStocks": 0,
                "listedStocks": 0,
                "stocks": [],
                "tilesTruncated": False,
            }
            for name in HEATMAP_SECTOR_ORDER
        ],
        "marketBreadth": {
            "advances": 0,
            "declines": 0,
            "unchanged": 0,
            "total": 0,
            "advanceRatio": 0.0,
        },
        "mood": "NEUTRAL",
        "moodEmoji": "🟡",
        "moodDescription": mood_desc,
        "bestSector": {"name": "N/A", "change": 0},
        "worstSector": {"name": "N/A", "change": 0},
        "lastUpdated": datetime.utcnow().isoformat(),
        "marketOpen": False,
        "isStale": True,
        "universeSize": catalog_size,
        "quotedCount": 0,
        "tilesPerSector": _HEATMAP_TILES_PER_SECTOR,
    }


def _is_nse_market_open() -> bool:
    try:
        from .market_session import is_within_equity_session

        return is_within_equity_session()
    except Exception:
        ist = datetime.now().astimezone()
        try:
            from zoneinfo import ZoneInfo
            ist = datetime.now(ZoneInfo("Asia/Kolkata"))
        except Exception:
            pass
        if ist.weekday() >= 5:
            return False
        current_minutes = ist.hour * 60 + ist.minute
        return (9 * 60 + 15) <= current_minutes <= (15 * 60 + 40)


def _is_valid_heatmap_snapshot(payload: Optional[Dict]) -> bool:
    if not isinstance(payload, dict):
        return False

    sectors = payload.get("sectors")
    breadth = payload.get("marketBreadth")
    if not isinstance(sectors, list) or not sectors:
        return False
    if not isinstance(breadth, dict):
        return False
    if int(breadth.get("total", 0) or 0) <= 0:
        return False
    return any(
        isinstance(sector.get("stocks"), list) and sector.get("stocks")
        for sector in sectors
        if isinstance(sector, dict)
    )


def _load_persisted_heatmap_snapshot() -> Optional[Dict]:
    try:
        if not _HEATMAP_SNAPSHOT_PATH.exists():
            return None
        payload = json.loads(_HEATMAP_SNAPSHOT_PATH.read_text(encoding="utf-8"))
        if _is_valid_heatmap_snapshot(payload):
            return payload
    except Exception as exc:
        logger.warning("heatmap.snapshot_load_failed reason=%s", exc)
    return None


def _persist_heatmap_snapshot(payload: Dict) -> None:
    if not _is_valid_heatmap_snapshot(payload):
        return
    existing = _load_persisted_heatmap_snapshot()
    if existing and _quoted_count(payload) < _quoted_count(existing):
        return
    try:
        _HEATMAP_SNAPSHOT_PATH.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = _HEATMAP_SNAPSHOT_PATH.with_suffix(".tmp")
        tmp_path.write_text(json.dumps(payload), encoding="utf-8")
        tmp_path.replace(_HEATMAP_SNAPSHOT_PATH)
    except Exception as exc:
        logger.warning("heatmap.snapshot_persist_failed reason=%s", exc)


def _analyze_sector(
    sector_name: str,
    symbols: List[str],
    quotes_dict: Dict,
    tile_limit: int | None = None,
) -> Dict:
    """Analyze a single sector's stocks using pre-fetched quotes (no redundant fetches).

    Breadth counts use every quoted symbol in the sector. UI tiles are capped to
    the strongest absolute movers when tile_limit is set (full-universe mode).
    """
    stocks_data = []
    total_change = 0
    advances = 0
    declines = 0
    unchanged = 0
    valid_count = 0

    for sym in symbols:
        quote = quotes_dict.get(sym, {})
        if not quote or quote.get("last", 0) == 0:
            continue

        price = quote.get("last", 0)
        change = quote.get("change", 0)
        pct_change = quote.get("pctChange", 0)
        name = INDIAN_STOCKS.get(sym, (None, sym))[1]

        if pct_change > 0.05:
            advances += 1
        elif pct_change < -0.05:
            declines += 1
        else:
            unchanged += 1

        total_change += pct_change
        valid_count += 1

        if pct_change >= 3:
            intensity = "strong_positive"
        elif pct_change >= 1:
            intensity = "positive"
        elif pct_change >= 0:
            intensity = "slight_positive"
        elif pct_change >= -1:
            intensity = "slight_negative"
        elif pct_change >= -3:
            intensity = "negative"
        else:
            intensity = "strong_negative"

        stocks_data.append({
            "symbol": sym,
            "name": name,
            "price": round(price, 2),
            "change": round(change, 2),
            "pctChange": round(pct_change, 2),
            "intensity": intensity,
        })

    stocks_data.sort(key=lambda x: x["pctChange"], reverse=True)
    avg_change = (total_change / valid_count) if valid_count > 0 else 0

    if avg_change >= 2:
        sector_intensity = "strong_positive"
    elif avg_change >= 0.5:
        sector_intensity = "positive"
    elif avg_change >= -0.5:
        sector_intensity = "neutral"
    elif avg_change >= -2:
        sector_intensity = "negative"
    else:
        sector_intensity = "strong_negative"

    tile_stocks = stocks_data
    if tile_limit is not None and tile_limit > 0 and len(stocks_data) > tile_limit:
        # Prefer largest absolute moves for the visual grid.
        tile_stocks = sorted(stocks_data, key=lambda x: abs(x["pctChange"]), reverse=True)[:tile_limit]
        tile_stocks.sort(key=lambda x: x["pctChange"], reverse=True)

    return {
        "name": sector_name,
        "stocks": tile_stocks,
        "avgChange": round(avg_change, 2),
        "advances": advances,
        "declines": declines,
        "unchanged": unchanged,
        "totalStocks": valid_count,
        "listedStocks": len(symbols),
        "intensity": sector_intensity,
        "topGainer": stocks_data[0] if stocks_data else None,
        "topLoser": stocks_data[-1] if stocks_data else None,
        "tilesTruncated": bool(tile_limit and len(stocks_data) > tile_limit),
    }


def get_sector_detail(sector_name: str) -> Optional[Dict]:
    """Get detailed data for a specific sector (full listed symbols in that bucket)."""
    from .heatmap_universe import get_heatmap_sector_symbols

    sector_symbols = get_heatmap_sector_symbols()
    sector_key = sector_name.strip()
    if sector_key not in sector_symbols:
        lowered = sector_name.lower()
        for key in sector_symbols:
            if lowered in key.lower():
                sector_key = key
                break
        else:
            # Fall back to curated SECTOR_STOCKS for older clients/tests.
            sector_key = sector_name.strip().title()
            if sector_key not in SECTOR_STOCKS:
                for key in SECTOR_STOCKS:
                    if sector_name.lower() in key.lower():
                        sector_key = key
                        break
                else:
                    return None
                symbols = SECTOR_STOCKS[sector_key]
            else:
                symbols = SECTOR_STOCKS[sector_key]
            quotes_list = fetch_quotes(symbols)
            quotes_dict = {q["symbol"]: q for q in quotes_list if isinstance(q, dict) and "symbol" in q}
            return _analyze_sector(sector_key, symbols, quotes_dict, tile_limit=None)

    symbols = sector_symbols[sector_key]
    quotes_list = fetch_quotes(symbols)
    quotes_dict = {q["symbol"]: q for q in quotes_list if isinstance(q, dict) and "symbol" in q}
    return _analyze_sector(sector_key, symbols, quotes_dict, tile_limit=None)
