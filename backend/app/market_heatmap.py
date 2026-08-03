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
# every 1–2s always get a fast response while quotes refresh.
# Closed market: long TTL (snapshot does not change).
# ──────────────────────────────────────────────────────────────
_HEATMAP_CACHE = {"data": None, "timestamp": 0}
_HEATMAP_CACHE_TTL_OPEN = float(os.getenv("HEATMAP_CACHE_TTL_OPEN", "2"))
_HEATMAP_CACHE_TTL_CLOSED = float(os.getenv("HEATMAP_CACHE_TTL_CLOSED", "120"))
# Full-universe quotes cannot refresh every 1–2s (Yahoo rate limits).
# Heatmap payload still caches ~2s; underlying quotes refresh on this TTL.
_HEATMAP_QUOTE_MAX_AGE_OPEN = float(os.getenv("HEATMAP_QUOTE_MAX_AGE_OPEN", "45"))
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
    "Pharma": [
        "SUNPHARMA", "DRREDDY", "CIPLA", "DIVISLAB", "LUPIN",
        "AUROPHARMA", "BIOCON", "TORNTPHARM", "ALKEM",
    ],
    "Auto": [
        "TATAMOTORS", "MARUTI", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT",
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

    When the market is closed:
      1) Prefer the last persisted in-session snapshot
      2) Otherwise rebuild from provider last-session quotes (Yahoo still
         returns the prior close session after hours) and persist it
      Never return an all-zero empty shell when quotes are available.
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
        persisted = _load_persisted_heatmap_snapshot()
        if persisted:
            stamped = _stamp_stale(
                persisted,
                market_open=False,
                reason="Market closed — showing last saved session snapshot.",
            )
            _HEATMAP_CACHE["data"] = stamped
            _HEATMAP_CACHE["timestamp"] = now
            return stamped

        # Render disks are ephemeral — after redeploy there is often no snapshot.
        # Build from last-session quotes so closed-market heatmap is never blank zeros.
        try:
            rebuilt = _build_heatmap_from_quotes(market_open=False)
            if _is_valid_heatmap_snapshot(rebuilt):
                rebuilt["isStale"] = True
                rebuilt["marketOpen"] = False
                rebuilt["staleReason"] = (
                    "Market closed — showing last session quotes (fresh snapshot rebuilt)."
                )
                rebuilt["moodDescription"] = (
                    f"{rebuilt.get('moodDescription', '')} "
                    "(Last session data — market is closed.)"
                ).strip()
                _persist_heatmap_snapshot(rebuilt)
                _HEATMAP_CACHE["data"] = rebuilt
                _HEATMAP_CACHE["timestamp"] = now
                return rebuilt
        except Exception as exc:
            logger.error("heatmap.closed_rebuild_failed reason=%s", exc)

        empty = _empty_heatmap_payload(
            mood_desc="Market is closed and last-session heatmap data is temporarily unavailable."
        )
        empty["isStale"] = True
        empty["marketOpen"] = False
        empty["staleReason"] = empty["moodDescription"]
        _HEATMAP_CACHE["data"] = empty
        _HEATMAP_CACHE["timestamp"] = now
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


def _refresh_heatmap_sync(*, market_open: bool) -> Dict:
    now = time.time()
    try:
        result = _build_heatmap_from_quotes(market_open=market_open)
        if _is_valid_heatmap_snapshot(result):
            _persist_heatmap_snapshot(result)
        else:
            persisted = _load_persisted_heatmap_snapshot()
            if persisted:
                stamped = _stamp_stale(
                    persisted,
                    market_open=market_open,
                    reason="Live heatmap incomplete — showing last saved snapshot.",
                )
                _HEATMAP_CACHE["data"] = stamped
                _HEATMAP_CACHE["timestamp"] = now
                return stamped

        _HEATMAP_CACHE["data"] = result
        _HEATMAP_CACHE["timestamp"] = now
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
            _HEATMAP_CACHE["data"] = stamped
            _HEATMAP_CACHE["timestamp"] = now
            return stamped
        empty = _empty_heatmap_payload(mood_desc=f"Heatmap temporarily unavailable ({exc}).")
        empty["staleReason"] = empty["moodDescription"]
        return empty


def _stamp_stale(payload: Dict, *, market_open: bool, reason: str) -> Dict:
    stamped = dict(payload)
    stamped["isStale"] = True
    stamped["marketOpen"] = market_open
    stamped["staleReason"] = reason
    return stamped


def _build_heatmap_from_quotes(*, market_open: bool) -> Dict:
    """Assemble heatmap from the full active equity universe.

    Quotes refresh on a rotating budget so Yahoo rate limits are respected while
    Market Breath / TQI still cover every listed symbol that already has a quote.
    """
    global _HEATMAP_REFRESH_OFFSET
    from .heatmap_universe import get_heatmap_sector_symbols, universe_size
    from .market_data import _quote_cache

    sector_symbols = get_heatmap_sector_symbols()
    all_symbols = sorted({sym for symbols in sector_symbols.values() for sym in symbols})
    fresh_age = _HEATMAP_QUOTE_MAX_AGE_OPEN if market_open else _HEATMAP_QUOTE_STALE_ACCEPT

    quotes_dict: Dict[str, dict] = {}
    missing: List[str] = []
    for sym in all_symbols:
        quote = _quote_cache.get(sym, max_age_seconds=fresh_age)
        if quote is None:
            quote = _quote_cache.get_allow_stale(sym, _HEATMAP_QUOTE_STALE_ACCEPT)
        if quote:
            quotes_dict[sym] = quote
        else:
            missing.append(sym)

    if missing:
        start = _HEATMAP_REFRESH_OFFSET % len(missing)
        budget = max(1, _HEATMAP_QUOTE_REFRESH_BUDGET)
        window = [missing[(start + i) % len(missing)] for i in range(min(budget, len(missing)))]
        _HEATMAP_REFRESH_OFFSET = start + len(window)
        # Force refresh this window (ignore cache age).
        fetched = fetch_quotes(window, max_age_seconds=0)
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
    catalog_size = universe_size()

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
    from .heatmap_universe import HEATMAP_SECTOR_ORDER, universe_size

    try:
        catalog_size = universe_size()
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
    ist = datetime.now().astimezone()
    try:
        from zoneinfo import ZoneInfo
        ist = datetime.now(ZoneInfo("Asia/Kolkata"))
    except Exception:
        pass

    if ist.weekday() >= 5:
        return False

    current_minutes = ist.hour * 60 + ist.minute
    return (9 * 60 + 15) <= current_minutes <= (15 * 60 + 30)


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
        if name == sym:
            try:
                from .market_data import get_stock_catalog

                catalog_name = get_stock_catalog().get(sym, (None, sym))[1]
                name = catalog_name or sym
            except Exception:
                pass

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
