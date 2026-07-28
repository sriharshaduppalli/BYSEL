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
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
from .market_data import INDIAN_STOCKS, fetch_quote, fetch_quotes

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────
# HEATMAP CACHE (30-second TTL for fast sub-1s market updates)
# ──────────────────────────────────────────────────────────────
_HEATMAP_CACHE = {"data": None, "timestamp": 0}
_HEATMAP_CACHE_TTL = 30  # 30 seconds
_HEATMAP_SNAPSHOT_PATH = Path(
    os.getenv(
        "HEATMAP_SNAPSHOT_PATH",
        str(Path(__file__).resolve().parent.parent / ".cache" / "market_heatmap_snapshot.json"),
    )
)


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
    Uses caching for fast sub-1 second market updates.

    Returns:
        Dict with sectors, stocks, market breadth, and mood.
    """
    # Check cache first
    now = time.time()
    if _HEATMAP_CACHE["data"] and (now - _HEATMAP_CACHE["timestamp"]) < _HEATMAP_CACHE_TTL:
        return _HEATMAP_CACHE["data"]

    market_open = _is_nse_market_open()
    if not market_open:
        persisted = _load_persisted_heatmap_snapshot()
        if persisted:
            stamped = dict(persisted)
            stamped["isStale"] = True
            stamped["marketOpen"] = False
            stamped["staleReason"] = "Market closed — showing last saved session snapshot."
            _HEATMAP_CACHE["data"] = stamped
            _HEATMAP_CACHE["timestamp"] = now
            return stamped
        # No snapshot yet: return an empty-but-valid payload (never 500 / blank crash).
        empty = _empty_heatmap_payload(
            mood_desc="Market is closed and no prior session snapshot is available yet."
        )
        empty["isStale"] = True
        empty["marketOpen"] = False
        _HEATMAP_CACHE["data"] = empty
        _HEATMAP_CACHE["timestamp"] = now
        return empty

    try:
        # Collect ALL unique symbols across all sectors (avoid duplicate fetches)
        all_symbols = set()
        for symbols in SECTOR_STOCKS.values():
            all_symbols.update(symbols)
        all_symbols = list(all_symbols)

        # Fetch quotes ONCE for all symbols
        quotes_list = fetch_quotes(all_symbols)
        quotes_dict = {q["symbol"]: q for q in quotes_list if isinstance(q, dict) and "symbol" in q}

        # Now analyze each sector using the pre-fetched quotes
        sectors_data = []
        all_advances = 0
        all_declines = 0
        all_unchanged = 0
        total_stocks = 0

        for sector_name, symbols in SECTOR_STOCKS.items():
            sector_result = _analyze_sector(sector_name, symbols, quotes_dict)
            sectors_data.append(sector_result)

            all_advances += sector_result["advances"]
            all_declines += sector_result["declines"]
            all_unchanged += sector_result["unchanged"]
            total_stocks += sector_result["totalStocks"]

        # Sort sectors by performance (best to worst)
        sectors_data.sort(key=lambda x: x["avgChange"], reverse=True)

        # Market mood
        if total_stocks > 0:
            advance_ratio = all_advances / total_stocks
        else:
            advance_ratio = 0.5

        if advance_ratio >= 0.7:
            mood = "EUPHORIC"
            mood_emoji = "🚀"
            mood_desc = "Markets are on fire! Strong buying across sectors."
        elif advance_ratio >= 0.55:
            mood = "BULLISH"
            mood_emoji = "🟢"
            mood_desc = "Positive sentiment with broad-based buying."
        elif advance_ratio >= 0.45:
            mood = "NEUTRAL"
            mood_emoji = "🟡"
            mood_desc = "Mixed signals. Markets are indecisive."
        elif advance_ratio >= 0.3:
            mood = "BEARISH"
            mood_emoji = "🔴"
            mood_desc = "Selling pressure across multiple sectors."
        else:
            mood = "FEARFUL"
            mood_emoji = "⚫"
            mood_desc = "Heavy selling! Markets in panic mode."

        # Best and worst sectors
        best_sector = sectors_data[0] if sectors_data else None
        worst_sector = sectors_data[-1] if sectors_data else None

        result = {
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
            "marketOpen": True,
            "isStale": False,
        }

        if _is_valid_heatmap_snapshot(result):
            _persist_heatmap_snapshot(result)
        else:
            persisted = _load_persisted_heatmap_snapshot()
            if persisted:
                stamped = dict(persisted)
                stamped["isStale"] = True
                stamped["marketOpen"] = market_open
                stamped["staleReason"] = "Live heatmap incomplete — showing last saved snapshot."
                _HEATMAP_CACHE["data"] = stamped
                _HEATMAP_CACHE["timestamp"] = now
                return stamped

        # Cache result for 30 seconds
        _HEATMAP_CACHE["data"] = result
        _HEATMAP_CACHE["timestamp"] = now
        return result
    except Exception as exc:
        logger.error("heatmap.live_build_failed reason=%s", exc)
        persisted = _load_persisted_heatmap_snapshot()
        if persisted:
            stamped = dict(persisted)
            stamped["isStale"] = True
            stamped["staleReason"] = "Live heatmap failed — showing last saved snapshot."
            _HEATMAP_CACHE["data"] = stamped
            _HEATMAP_CACHE["timestamp"] = now
            return stamped
        return _empty_heatmap_payload(mood_desc=f"Heatmap temporarily unavailable ({exc}).")


def _empty_heatmap_payload(mood_desc: str = "No heatmap data available.") -> Dict:
    return {
        "sectors": [
            {
                "name": name,
                "avgChange": 0.0,
                "advances": 0,
                "declines": 0,
                "unchanged": 0,
                "totalStocks": 0,
                "stocks": [],
            }
            for name in SECTOR_STOCKS.keys()
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
        "marketOpen": _is_nse_market_open(),
        "isStale": True,
    }

def _is_nse_market_open() -> bool:
    ist = datetime.now().astimezone().astimezone()
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
    return any(isinstance(sector.get("stocks"), list) and sector.get("stocks") for sector in sectors if isinstance(sector, dict))


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


def _analyze_sector(sector_name: str, symbols: List[str], quotes_dict: Dict) -> Dict:
    """Analyze a single sector's stocks using pre-fetched quotes (no redundant fetches)."""
    stocks_data = []
    total_change = 0
    advances = 0
    declines = 0
    unchanged = 0
    valid_count = 0

    # Use pre-fetched quotes (passed from get_market_heatmap)
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

        # Determine intensity for heatmap coloring
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

    # Sort stocks by performance
    stocks_data.sort(key=lambda x: x["pctChange"], reverse=True)

    avg_change = (total_change / valid_count) if valid_count > 0 else 0

    # Sector intensity
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

    return {
        "name": sector_name,
        "stocks": stocks_data,
        "avgChange": round(avg_change, 2),
        "advances": advances,
        "declines": declines,
        "unchanged": unchanged,
        "totalStocks": valid_count,
        "intensity": sector_intensity,
        "topGainer": stocks_data[0] if stocks_data else None,
        "topLoser": stocks_data[-1] if stocks_data else None,
    }


def get_sector_detail(sector_name: str) -> Optional[Dict]:
    """Get detailed data for a specific sector."""
    sector_key = sector_name.strip().title()
    if sector_key not in SECTOR_STOCKS:
        # Try fuzzy match
        for key in SECTOR_STOCKS:
            if sector_name.lower() in key.lower():
                sector_key = key
                break
        else:
            return None

    symbols = SECTOR_STOCKS[sector_key]
    # Fetch quotes for this sector
    quotes_list = fetch_quotes(symbols)
    quotes_dict = {q["symbol"]: q for q in quotes_list if isinstance(q, dict) and "symbol" in q}
    return _analyze_sector(sector_key, symbols, quotes_dict)
