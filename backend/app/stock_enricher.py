"""
Real-time stock context enricher.
Fetches live price, fundamentals, and news from yfinance and formats
them into a structured dict that is injected into the Groq/LLM prompt.
"""
from __future__ import annotations

import asyncio
import logging
import re
from functools import lru_cache
from time import time
from typing import Optional

logger = logging.getLogger(__name__)

# Simple TTL cache: {symbol: (timestamp, data)}
_cache: dict[str, tuple[float, dict]] = {}
_CACHE_TTL = 60  # seconds


# Common Indian stock name → NSE symbol mapping for query extraction
_NAME_TO_SYMBOL: dict[str, str] = {
    "reliance": "RELIANCE", "ril": "RELIANCE",
    "tcs": "TCS", "tata consultancy": "TCS",
    "infosys": "INFY", "infy": "INFY",
    "hdfc bank": "HDFCBANK", "hdfc": "HDFCBANK",
    "icici bank": "ICICIBANK", "icici": "ICICIBANK",
    "wipro": "WIPRO",
    "hcl": "HCLTECH", "hcltech": "HCLTECH",
    "sbi": "SBIN", "state bank": "SBIN",
    "bajaj finance": "BAJFINANCE", "bajaj fin": "BAJFINANCE",
    "kotak": "KOTAKBANK", "kotak bank": "KOTAKBANK",
    "axis bank": "AXISBANK", "axis": "AXISBANK",
    "maruti": "MARUTI", "maruti suzuki": "MARUTI",
    "adani": "ADANIENT", "adani enterprises": "ADANIENT",
    "adani ports": "ADANIPORTS",
    "titan": "TITAN",
    "ultratech": "ULTRACEMCO", "ultratech cement": "ULTRACEMCO",
    "nestle": "NESTLEIND",
    "ltimindtree": "LTIM", "lti": "LTIM",
    "tech mahindra": "TECHM",
    "sun pharma": "SUNPHARMA", "sun pharmaceutical": "SUNPHARMA",
    "asian paints": "ASIANPAINT",
    "bharti airtel": "BHARTIARTL", "airtel": "BHARTIARTL",
    "ongc": "ONGC",
    "power grid": "POWERGRID",
    "ntpc": "NTPC",
    "coal india": "COALINDIA",
    "jio": "JIOFIN", "jio financial": "JIOFIN",
    "tata motors": "TATAMOTORS",
    "tata steel": "TATASTEEL",
    "hindalco": "HINDALCO",
    "jsw steel": "JSWSTEEL",
    "grasim": "GRASIM",
    "bpcl": "BPCL",
    "hero motocorp": "HEROMOTOCO", "hero": "HEROMOTOCO",
    "bajaj auto": "BAJAJ-AUTO",
    "divis": "DIVISLAB", "divis lab": "DIVISLAB",
    "cipla": "CIPLA",
    "dr reddy": "DRREDDY",
    "eicher": "EICHERMOT",
    "shriram": "SHRIRAMFIN",
    "upl": "UPL",
    "britannia": "BRITANNIA",
    "indusind": "INDUSINDBK", "indusind bank": "INDUSINDBK",
}


def extract_symbol_from_query(query: str) -> Optional[str]:
    """Pull a stock symbol out of a free-text query."""
    q = query.upper().strip()

    # Direct all-caps ticker (2-10 chars, not a common English word)
    _SKIP = {"IS", "IN", "AT", "OR", "AND", "THE", "FOR", "BUY", "SELL",
              "NOW", "UP", "DOWN", "GET", "CAN", "HOW", "WHY", "WHAT"}
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', q)
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 2:
            return tok

    # Name-to-symbol lookup (case-insensitive)
    q_lower = query.lower()
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower:
            return sym

    return None


def _fmt_inr(val) -> Optional[str]:
    """Format a number as ₹ with Indian crore notation."""
    try:
        v = float(val)
    except (TypeError, ValueError):
        return None
    if v >= 1e12:
        return f"₹{v/1e7:.0f} crore (large cap)"
    if v >= 1e9:
        return f"₹{v/1e7:.1f} crore"
    return f"₹{v:,.0f}"


def _fetch_yfinance(symbol: str) -> dict:
    """Synchronous yfinance fetch — run in executor from async context."""
    try:
        import yfinance as yf
        nse_sym = symbol if symbol.endswith(".NS") else f"{symbol}.NS"
        ticker = yf.Ticker(nse_sym)
        info = ticker.info or {}

        price = (info.get("currentPrice")
                 or info.get("regularMarketPrice")
                 or info.get("previousClose"))
        pe = info.get("trailingPE") or info.get("forwardPE")
        week52_high = info.get("fiftyTwoWeekHigh")
        week52_low = info.get("fiftyTwoWeekLow")
        market_cap = info.get("marketCap")
        div_yield = info.get("dividendYield")
        sector = info.get("sector") or info.get("industry")
        company_name = info.get("longName") or info.get("shortName") or symbol

        # position in 52-week range
        pos_52w = None
        if price and week52_high and week52_low and week52_high > week52_low:
            pct = (price - week52_low) / (week52_high - week52_low) * 100
            pos_52w = f"{pct:.0f}% of 52-week range"

        # News headlines (latest 6)
        headlines: list[str] = []
        try:
            raw_news = ticker.news or []
            for item in raw_news[:6]:
                title = (item.get("title")
                         or (item.get("content", {}) or {}).get("title", ""))
                if title:
                    headlines.append(title)
        except Exception:
            pass

        return {
            "symbol": symbol,
            "company_name": company_name,
            "sector": sector,
            "current_price": f"{price:.2f}" if price else None,
            "fundamental": {
                "pe_ratio": f"{pe:.1f}" if pe else None,
                "market_cap": _fmt_inr(market_cap),
                "dividend_yield": f"{div_yield*100:.2f}%" if div_yield else None,
                "week_52": (f"₹{week52_low:.2f} – ₹{week52_high:.2f} ({pos_52w})"
                            if week52_low and week52_high else None),
            },
            "news_headlines": headlines,
        }
    except Exception as exc:
        logger.warning("yfinance fetch failed for %s: %s", symbol, exc)
        return {}


async def enrich(symbol: str) -> dict:
    """Async wrapper: returns enriched context dict with TTL caching."""
    now = time()
    cached = _cache.get(symbol)
    if cached and (now - cached[0]) < _CACHE_TTL:
        return cached[1]

    loop = asyncio.get_event_loop()
    data = await loop.run_in_executor(None, _fetch_yfinance, symbol)
    if data:
        _cache[symbol] = (now, data)
    return data


def format_news_for_prompt(headlines: list[str]) -> str:
    if not headlines:
        return ""
    lines = "\n".join(f"  • {h}" for h in headlines)
    return f"RECENT NEWS HEADLINES:\n{lines}"
