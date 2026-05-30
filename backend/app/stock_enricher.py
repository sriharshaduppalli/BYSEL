"""
Real-time stock context enricher.
Fetches live price, fundamentals, technicals, trading levels,
news headlines, and sentiment from yfinance — all free, no API key.
"""
from __future__ import annotations

import asyncio
import logging
import re
from time import time
from typing import Optional

logger = logging.getLogger(__name__)

_cache: dict[str, tuple[float, dict]] = {}
_CACHE_TTL = 60  # seconds

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

_POS_WORDS = {
    "surge", "rally", "growth", "profit", "strong", "beat", "record",
    "gain", "rise", "bull", "upgrade", "positive", "high", "wins",
    "boost", "outperform", "buy", "overweight", "robust", "better",
    "recovery", "rebound", "momentum", "expansion", "milestone",
}
_NEG_WORDS = {
    "fall", "decline", "loss", "weak", "miss", "cut", "drop", "bear",
    "downgrade", "risk", "concern", "warning", "down", "low", "crash",
    "slump", "worry", "underperform", "sell", "underweight", "poor",
    "disappointing", "slowdown", "contraction", "pressure", "debt",
}


def extract_symbol_from_query(query: str) -> Optional[str]:
    q = query.upper().strip()
    _SKIP = {
        "IS", "IN", "AT", "OR", "AND", "THE", "FOR", "BUY", "SELL",
        "NOW", "UP", "DOWN", "GET", "CAN", "HOW", "WHY", "WHAT",
        "ARE", "ITS", "THIS", "THAT", "WILL", "HAS", "HIT", "NEW",
    }
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', q)
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 2:
            return tok
    q_lower = query.lower()
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower:
            return sym
    return None


def _fmt_price(val) -> Optional[str]:
    try:
        return f"{float(val):,.2f}"
    except (TypeError, ValueError):
        return None


def _fmt_inr_cr(val) -> Optional[str]:
    try:
        v = float(val)
        cr = v / 1e7
        if cr >= 1_00_000:
            return f"₹{cr/1_00_000:.1f} lakh crore"
        if cr >= 1_000:
            return f"₹{cr:,.0f} crore"
        return f"₹{cr:.1f} crore"
    except (TypeError, ValueError):
        return None


def _rolling_mean(arr, n):
    if len(arr) < n:
        return None
    return sum(arr[-n:]) / n


def _rolling_std(arr, n):
    if len(arr) < n:
        return None
    mean = _rolling_mean(arr, n)
    variance = sum((x - mean) ** 2 for x in arr[-n:]) / n
    return variance ** 0.5


def _calc_rsi(closes, period=14):
    if len(closes) < period + 1:
        return None
    gains, losses = [], []
    for i in range(1, len(closes)):
        diff = closes[i] - closes[i - 1]
        gains.append(max(diff, 0))
        losses.append(max(-diff, 0))
    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 1)


def _news_sentiment(headlines: list[str]) -> dict:
    pos, neg, neu = 0, 0, 0
    for h in headlines:
        words = set(h.lower().split())
        p = len(words & _POS_WORDS)
        n = len(words & _NEG_WORDS)
        if p > n:
            pos += 1
        elif n > p:
            neg += 1
        else:
            neu += 1
    total = pos + neg + neu or 1
    if pos > neg:
        overall = "Positive"
    elif neg > pos:
        overall = "Negative"
    else:
        overall = "Neutral"
    return {
        "overall": overall,
        "breakdown": f"Positive {pos*100//total}% / Neutral {neu*100//total}% / Negative {neg*100//total}%",
    }


def _fetch_yfinance(symbol: str) -> dict:
    try:
        import yfinance as yf

        nse_sym = symbol if symbol.endswith(".NS") else f"{symbol}.NS"
        ticker = yf.Ticker(nse_sym)

        # --- fast_info: reliable on all yfinance versions including Docker/Render ---
        price = None
        week52_high = None
        week52_low = None
        market_cap = None
        try:
            fi = ticker.fast_info
            price = getattr(fi, "last_price", None) or getattr(fi, "regularMarketPrice", None)
            week52_high = getattr(fi, "fifty_two_week_high", None) or getattr(fi, "yearHigh", None)
            week52_low = getattr(fi, "fifty_two_week_low", None) or getattr(fi, "yearLow", None)
            market_cap = getattr(fi, "market_cap", None) or getattr(fi, "marketCap", None)
        except Exception as e:
            logger.warning("fast_info failed for %s: %s", symbol, e)

        # --- ticker.info: fundamentals only (P/E, sector, dividend) ---
        info: dict = {}
        try:
            info = ticker.info or {}
        except Exception as e:
            logger.warning("ticker.info failed for %s: %s", symbol, e)

        pe = info.get("trailingPE") or info.get("forwardPE")
        div_yield = info.get("dividendYield")
        sector = info.get("sector") or info.get("industry") or "N/A"
        company_name = info.get("longName") or info.get("shortName") or symbol
        pe_sector_avg = info.get("industryPe") or info.get("fiveYearAvgDivYield")

        # Fallback price from info if fast_info gave nothing
        if not price:
            price = (info.get("currentPrice")
                     or info.get("regularMarketPrice")
                     or info.get("previousClose"))
        if not week52_high:
            week52_high = info.get("fiftyTwoWeekHigh")
        if not week52_low:
            week52_low = info.get("fiftyTwoWeekLow")
        if not market_cap:
            market_cap = info.get("marketCap")

        pos_52w = None
        if price and week52_high and week52_low and week52_high > week52_low:
            pct = (price - week52_low) / (week52_high - week52_low) * 100
            if pct >= 80:
                position = "near 52-week high"
            elif pct <= 20:
                position = "near 52-week low"
            else:
                position = "middle of range"
            pos_52w = f"₹{week52_low:,.2f} – ₹{week52_high:,.2f} | Current at {pct:.0f}% ({position})"

        # --- yf.download(): reliable OHLCV history on Docker/Render ---
        closes, highs, lows = [], [], []
        try:
            hist = yf.download(nse_sym, period="1y", progress=False, auto_adjust=True)
            if not hist.empty:
                # Newer yfinance returns MultiIndex columns (field, ticker) — flatten
                if hasattr(hist.columns, "levels"):
                    hist.columns = hist.columns.get_level_values(0)
                if "Close" in hist.columns:
                    closes = [float(v) for v in hist["Close"].dropna()]
                if "High" in hist.columns:
                    highs = [float(v) for v in hist["High"].dropna()]
                if "Low" in hist.columns:
                    lows = [float(v) for v in hist["Low"].dropna()]
        except Exception as e:
            logger.warning("yf.download failed for %s: %s", symbol, e)

        current = closes[-1] if closes else price

        # Moving Averages
        sma5   = _rolling_mean(closes, 5)
        sma20  = _rolling_mean(closes, 20)
        sma50  = _rolling_mean(closes, 50)
        sma200 = _rolling_mean(closes, 200)

        ma_lines = []
        if sma5:   ma_lines.append(f"5-SMA ₹{sma5:,.2f}")
        if sma20:  ma_lines.append(f"20-SMA ₹{sma20:,.2f}")
        if sma50:  ma_lines.append(f"50-SMA ₹{sma50:,.2f}")
        if sma200: ma_lines.append(f"200-SMA ₹{sma200:,.2f}")

        if current and sma50 and sma200:
            if current > sma50 > sma200:
                ma_trend = "Bullish — price above 50 & 200 SMA. " + ", ".join(ma_lines)
            elif current < sma50 < sma200:
                ma_trend = "Bearish — price below 50 & 200 SMA. " + ", ".join(ma_lines)
            else:
                ma_trend = "Mixed. " + ", ".join(ma_lines)
        elif ma_lines:
            ma_trend = ", ".join(ma_lines)
        else:
            ma_trend = None

        # Bollinger Bands (20-day)
        bb_band = None
        if len(closes) >= 20:
            bb_mid = _rolling_mean(closes, 20)
            bb_std = _rolling_std(closes, 20)
            if bb_mid and bb_std:
                bb_upper = bb_mid + 2 * bb_std
                bb_lower = bb_mid - 2 * bb_std
                if current:
                    if current > bb_upper:
                        bb_pos = "above upper band (overbought signal)"
                    elif current < bb_lower:
                        bb_pos = "below lower band (oversold signal)"
                    else:
                        pct_in = (current - bb_lower) / (bb_upper - bb_lower) * 100
                        bb_pos = f"{pct_in:.0f}% within bands"
                    bb_band = (f"Upper ₹{bb_upper:,.2f} | Mid ₹{bb_mid:,.2f} | "
                               f"Lower ₹{bb_lower:,.2f} — price {bb_pos}")

        # RSI
        rsi_val = _calc_rsi(closes)
        if rsi_val is not None:
            if rsi_val > 70:
                rsi_interp = "overbought"
            elif rsi_val < 30:
                rsi_interp = "oversold"
            else:
                rsi_interp = "neutral"
            rsi_str = f"{rsi_val} ({rsi_interp})"
        else:
            rsi_str = None

        # Support & Resistance (20-day range)
        support1 = round(min(lows[-20:]), 2) if len(lows) >= 20 else None
        resistance1 = round(max(highs[-20:]), 2) if len(highs) >= 20 else None

        # Wider support/resistance (52-week)
        support2 = round(min(lows[-52:]), 2) if len(lows) >= 52 else None
        resistance2 = round(max(highs[-52:]), 2) if len(highs) >= 52 else None

        # Stop Loss & Take Profit
        stop_loss = round(support1 * 0.98, 2) if support1 else None
        take_profit = resistance1
        rr_ratio = None
        if current and stop_loss and take_profit and current > stop_loss:
            risk = current - stop_loss
            reward = take_profit - current
            if risk > 0:
                rr_ratio = f"1 : {reward/risk:.1f}"

        # Sector trend (derived from stock's own 1-month momentum vs 3-month)
        sector_trend = "Neutral"
        if len(closes) >= 63:
            ret_1m = (closes[-1] - closes[-21]) / closes[-21] * 100 if len(closes) >= 21 else None
            ret_3m = (closes[-1] - closes[-63]) / closes[-63] * 100
            if ret_1m is not None:
                if ret_1m > 3:
                    sector_trend = f"Bullish (stock +{ret_1m:.1f}% in 1 month)"
                elif ret_1m < -3:
                    sector_trend = f"Bearish (stock {ret_1m:.1f}% in 1 month)"
                else:
                    sector_trend = f"Neutral (stock {ret_1m:+.1f}% in 1 month)"

        # --- News ---
        headlines: list[str] = []
        try:
            raw_news = ticker.news or []
            for item in raw_news[:8]:
                title = (item.get("title")
                         or (item.get("content") or {}).get("title", ""))
                if title:
                    headlines.append(title)
        except Exception:
            pass

        sentiment = _news_sentiment(headlines) if headlines else {}

        return {
            "symbol": symbol,
            "company_name": company_name,
            "sector": sector,
            "current_price": f"{current:,.2f}" if current else None,
            "fundamental": {
                "pe_ratio": f"{pe:.1f}" if pe else None,
                "pe_sector_avg": f"{pe_sector_avg:.1f}" if pe_sector_avg else "~25 (market avg)",
                "market_cap": _fmt_inr_cr(market_cap),
                "dividend_yield": f"{div_yield*100:.2f}%" if div_yield else "Not declared",
                "week_52": pos_52w,
            },
            "technical": {
                "rsi": rsi_str,
                "rsi_interpretation": rsi_interp if rsi_val else None,
                "bollinger_bands": bb_band,
                "moving_averages": ma_trend,
                "trend": ("strong_bullish" if current and sma50 and sma200 and current > sma50 > sma200
                          else "bearish" if current and sma50 and sma200 and current < sma50 < sma200
                          else "neutral"),
            },
            "trading_levels": {
                "support_1": f"{support1:,.2f}" if support1 else None,
                "support_2": f"{support2:,.2f}" if support2 else None,
                "resistance_1": f"{resistance1:,.2f}" if resistance1 else None,
                "resistance_2": f"{resistance2:,.2f}" if resistance2 else None,
                "stop_loss": f"{stop_loss:,.2f}" if stop_loss else None,
                "take_profit": f"{take_profit:,.2f}" if take_profit else None,
                "risk_reward": rr_ratio,
            },
            "sentiment": {
                "overall": sentiment.get("overall"),
                "breakdown": sentiment.get("breakdown"),
                "recent_events": headlines[:5],
                "sector_trend": sector_trend,
            },
            "news_headlines": headlines,
        }

    except Exception as exc:
        logger.error("yfinance fetch failed for %s: %s", symbol, exc, exc_info=True)
        raise  # re-raise so enricher-test endpoint can catch it


async def enrich(symbol: str) -> dict:
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
