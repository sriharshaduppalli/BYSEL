"""
Real-time market data provider using Yahoo Finance.
Fetches live NSE stock prices with caching to avoid rate limits.
"""

import yfinance as yf
import logging
import time
from datetime import datetime
from typing import Dict, Optional, List

logger = logging.getLogger(__name__)

# NSE symbols mapped to Yahoo Finance tickers (append .NS for NSE)
NSE_SYMBOLS = {
    "RELIANCE": "RELIANCE.NS",
    "TCS": "TCS.NS",
    "INFY": "INFY.NS",
    "HDFCBANK": "HDFCBANK.NS",
    "SBIN": "SBIN.NS",
    "WIPRO": "WIPRO.NS",
    "BAJFINANCE": "BAJFINANCE.NS",
    "LT": "LT.NS",
    "MARUTI": "MARUTI.NS",
    "ICICIBANK": "ICICIBANK.NS",
    "KOTAKBANK": "KOTAKBANK.NS",
    "HINDUNILVR": "HINDUNILVR.NS",
    "ITC": "ITC.NS",
    "BHARTIARTL": "BHARTIARTL.NS",
    "ASIANPAINT": "ASIANPAINT.NS",
    "AXISBANK": "AXISBANK.NS",
    "TATAMOTORS": "TATAMOTORS.NS",
    "SUNPHARMA": "SUNPHARMA.NS",
    "ULTRACEMCO": "ULTRACEMCO.NS",
    "TITAN": "TITAN.NS",
    "NESTLEIND": "NESTLEIND.NS",
    "TECHM": "TECHM.NS",
    "HCLTECH": "HCLTECH.NS",
    "POWERGRID": "POWERGRID.NS",
    "NTPC": "NTPC.NS",
    "TATASTEEL": "TATASTEEL.NS",
    "ADANIENT": "ADANIENT.NS",
    "BAJAJFINSV": "BAJAJFINSV.NS",
    "JSWSTEEL": "JSWSTEEL.NS",
    "ONGC": "ONGC.NS",
}

# Default symbols shown to users
DEFAULT_SYMBOLS = [
    "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN",
    "WIPRO", "ICICIBANK", "KOTAKBANK", "HINDUNILVR", "ITC"
]


class QuoteCache:
    """In-memory cache for stock quotes with TTL."""

    def __init__(self, ttl_seconds: int = 60):
        self._cache: Dict[str, dict] = {}
        self._timestamps: Dict[str, float] = {}
        self._ttl = ttl_seconds

    def get(self, symbol: str) -> Optional[dict]:
        if symbol in self._cache:
            if time.time() - self._timestamps[symbol] < self._ttl:
                return self._cache[symbol]
            else:
                del self._cache[symbol]
                del self._timestamps[symbol]
        return None

    def put(self, symbol: str, data: dict):
        self._cache[symbol] = data
        self._timestamps[symbol] = time.time()

    def clear(self):
        self._cache.clear()
        self._timestamps.clear()


# Global cache: quotes refresh every 60 seconds
_quote_cache = QuoteCache(ttl_seconds=60)


def _yf_ticker(symbol: str) -> str:
    """Convert our symbol to Yahoo Finance NSE ticker."""
    return NSE_SYMBOLS.get(symbol, f"{symbol}.NS")


def fetch_quote(symbol: str) -> dict:
    """
    Fetch a single real-time quote for an NSE stock.
    Returns dict with: symbol, last, pctChange, open, high, low,
    volume, marketCap, previousClose, fiftyTwoWeekHigh, fiftyTwoWeekLow, pe, dividendYield
    """
    cached = _quote_cache.get(symbol)
    if cached:
        return cached

    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        info = ticker.fast_info
        hist = ticker.history(period="2d")

        if hist.empty:
            logger.warning(f"No history data for {symbol}")
            return _empty_quote(symbol)

        last_price = float(info.last_price) if hasattr(info, 'last_price') and info.last_price else float(hist['Close'].iloc[-1])
        prev_close = float(info.previous_close) if hasattr(info, 'previous_close') and info.previous_close else (
            float(hist['Close'].iloc[-2]) if len(hist) > 1 else last_price
        )

        pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0

        # Get extended info (may fail for some stocks)
        try:
            full_info = ticker.info
            market_cap = full_info.get('marketCap', 0)
            pe_ratio = full_info.get('trailingPE', 0)
            dividend_yield = full_info.get('dividendYield', 0)
            fifty_two_high = full_info.get('fiftyTwoWeekHigh', last_price * 1.15)
            fifty_two_low = full_info.get('fiftyTwoWeekLow', last_price * 0.85)
            day_high = full_info.get('dayHigh', float(hist['High'].iloc[-1]))
            day_low = full_info.get('dayLow', float(hist['Low'].iloc[-1]))
            open_price = full_info.get('open', float(hist['Open'].iloc[-1]))
            volume = full_info.get('volume', int(hist['Volume'].iloc[-1]))
        except Exception:
            day_high = float(hist['High'].iloc[-1])
            day_low = float(hist['Low'].iloc[-1])
            open_price = float(hist['Open'].iloc[-1])
            volume = int(hist['Volume'].iloc[-1])
            market_cap = 0
            pe_ratio = 0
            dividend_yield = 0
            fifty_two_high = last_price * 1.15
            fifty_two_low = last_price * 0.85

        quote = {
            "symbol": symbol,
            "last": round(last_price, 2),
            "pctChange": pct_change,
            "open": round(open_price, 2),
            "high": round(day_high, 2),
            "low": round(day_low, 2),
            "previousClose": round(prev_close, 2),
            "volume": volume,
            "marketCap": market_cap,
            "pe": round(pe_ratio, 2) if pe_ratio else 0,
            "dividendYield": round((dividend_yield or 0) * 100, 2),
            "fiftyTwoWeekHigh": round(fifty_two_high, 2),
            "fiftyTwoWeekLow": round(fifty_two_low, 2),
            "timestamp": int(datetime.utcnow().timestamp() * 1000),
        }

        _quote_cache.put(symbol, quote)
        logger.info(f"Fetched live quote: {symbol} = ₹{last_price:.2f} ({pct_change:+.2f}%)")
        return quote

    except Exception as e:
        logger.error(f"Error fetching quote for {symbol}: {e}")
        return _empty_quote(symbol)


def fetch_quotes(symbols: List[str]) -> List[dict]:
    """Fetch quotes for multiple symbols. Uses batch download for uncached symbols."""
    results = []
    uncached = []

    for s in symbols:
        cached = _quote_cache.get(s)
        if cached:
            results.append(cached)
        else:
            uncached.append(s)

    if uncached:
        try:
            yf_tickers = " ".join([_yf_ticker(s) for s in uncached])
            data = yf.download(yf_tickers, period="2d", group_by="ticker", progress=False, threads=True)

            for symbol in uncached:
                try:
                    yf_sym = _yf_ticker(symbol)
                    if len(uncached) == 1:
                        ticker_data = data
                    else:
                        ticker_data = data[yf_sym] if yf_sym in data.columns.get_level_values(0) else None

                    if ticker_data is not None and not ticker_data.empty:
                        last_price = float(ticker_data['Close'].iloc[-1])
                        prev_close = float(ticker_data['Close'].iloc[-2]) if len(ticker_data) > 1 else last_price
                        pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0

                        quote = {
                            "symbol": symbol,
                            "last": round(last_price, 2),
                            "pctChange": pct_change,
                            "open": round(float(ticker_data['Open'].iloc[-1]), 2),
                            "high": round(float(ticker_data['High'].iloc[-1]), 2),
                            "low": round(float(ticker_data['Low'].iloc[-1]), 2),
                            "previousClose": round(prev_close, 2),
                            "volume": int(ticker_data['Volume'].iloc[-1]),
                            "marketCap": 0,
                            "pe": 0,
                            "dividendYield": 0,
                            "fiftyTwoWeekHigh": round(last_price * 1.15, 2),
                            "fiftyTwoWeekLow": round(last_price * 0.85, 2),
                            "timestamp": int(datetime.utcnow().timestamp() * 1000),
                        }
                        _quote_cache.put(symbol, quote)
                        results.append(quote)
                    else:
                        # Fallback to individual fetch
                        results.append(fetch_quote(symbol))
                except Exception as e:
                    logger.warning(f"Batch parse failed for {symbol}, falling back: {e}")
                    results.append(fetch_quote(symbol))

        except Exception as e:
            logger.error(f"Batch download failed: {e}, falling back to individual fetches")
            for symbol in uncached:
                results.append(fetch_quote(symbol))

    return results


def _empty_quote(symbol: str) -> dict:
    """Return a zero-value quote when data is unavailable."""
    return {
        "symbol": symbol,
        "last": 0.0,
        "pctChange": 0.0,
        "open": 0.0,
        "high": 0.0,
        "low": 0.0,
        "previousClose": 0.0,
        "volume": 0,
        "marketCap": 0,
        "pe": 0,
        "dividendYield": 0,
        "fiftyTwoWeekHigh": 0,
        "fiftyTwoWeekLow": 0,
        "timestamp": int(datetime.utcnow().timestamp() * 1000),
    }


def get_all_symbols() -> List[str]:
    """Return all supported symbols."""
    return list(NSE_SYMBOLS.keys())


def get_default_symbols() -> List[str]:
    """Return default symbols shown to users."""
    return DEFAULT_SYMBOLS
