"""
Real-time stock context enricher.
Sources (all free, no API key):
  1. NSE India API  — P/E, sector, company name, 52-week range, live price
  2. Google News RSS — headlines from ET, MoneyControl, Business Standard
  3. yfinance fast_info — market cap, fallback price
  4. yf.download()   — OHLCV history for RSI, Bollinger, SMAs, S/R levels
"""
from __future__ import annotations

import asyncio
import logging
import re
from time import time
from typing import Optional
from urllib.parse import quote_plus
from xml.etree import ElementTree as ET

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

# Set of all valid NSE symbols we know — used to reject false ticker matches
_VALID_SYMBOLS: set[str] = set(_NAME_TO_SYMBOL.values())

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

_NSE_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/",
}

# Hardcoded fallback for sector (never shows "N/A" for major stocks)
_SYMBOL_SECTOR: dict[str, str] = {
    "RELIANCE": "Oil & Gas / Refineries",
    "TCS": "Information Technology",
    "INFY": "Information Technology",
    "HDFCBANK": "Banking & Financial Services",
    "ICICIBANK": "Banking & Financial Services",
    "WIPRO": "Information Technology",
    "HCLTECH": "Information Technology",
    "SBIN": "Banking & Financial Services",
    "BAJFINANCE": "Financial Services / NBFC",
    "KOTAKBANK": "Banking & Financial Services",
    "AXISBANK": "Banking & Financial Services",
    "MARUTI": "Automobiles",
    "ADANIENT": "Infrastructure / Diversified",
    "ADANIPORTS": "Infrastructure / Ports",
    "TITAN": "Consumer Goods / Jewellery",
    "ULTRACEMCO": "Cement",
    "NESTLEIND": "FMCG",
    "LTIM": "Information Technology",
    "TECHM": "Information Technology",
    "SUNPHARMA": "Pharmaceuticals",
    "ASIANPAINT": "Paints / Consumer Goods",
    "BHARTIARTL": "Telecom",
    "ONGC": "Oil & Gas",
    "POWERGRID": "Power / Utilities",
    "NTPC": "Power / Utilities",
    "COALINDIA": "Mining / Energy",
    "JIOFIN": "Financial Services",
    "TATAMOTORS": "Automobiles",
    "TATASTEEL": "Steel / Metals",
    "HINDALCO": "Metals & Mining",
    "JSWSTEEL": "Steel / Metals",
    "GRASIM": "Diversified / Chemicals",
    "BPCL": "Oil & Gas",
    "HEROMOTOCO": "Automobiles",
    "BAJAJ-AUTO": "Automobiles",
    "DIVISLAB": "Pharmaceuticals",
    "CIPLA": "Pharmaceuticals",
    "DRREDDY": "Pharmaceuticals",
    "EICHERMOT": "Automobiles",
    "SHRIRAMFIN": "Financial Services / NBFC",
    "UPL": "Agrochemicals",
    "BRITANNIA": "FMCG",
    "INDUSINDBK": "Banking & Financial Services",
}

# Hardcoded full company names (fallback when all APIs return nothing)
_SYMBOL_COMPANY: dict[str, str] = {
    "RELIANCE": "Reliance Industries Ltd",
    "TCS": "Tata Consultancy Services Ltd",
    "INFY": "Infosys Ltd",
    "HDFCBANK": "HDFC Bank Ltd",
    "ICICIBANK": "ICICI Bank Ltd",
    "WIPRO": "Wipro Ltd",
    "HCLTECH": "HCL Technologies Ltd",
    "SBIN": "State Bank of India",
    "BAJFINANCE": "Bajaj Finance Ltd",
    "KOTAKBANK": "Kotak Mahindra Bank Ltd",
    "AXISBANK": "Axis Bank Ltd",
    "MARUTI": "Maruti Suzuki India Ltd",
    "ADANIENT": "Adani Enterprises Ltd",
    "ADANIPORTS": "Adani Ports and SEZ Ltd",
    "TITAN": "Titan Company Ltd",
    "ULTRACEMCO": "UltraTech Cement Ltd",
    "NESTLEIND": "Nestle India Ltd",
    "LTIM": "LTIMindtree Ltd",
    "TECHM": "Tech Mahindra Ltd",
    "SUNPHARMA": "Sun Pharmaceutical Industries Ltd",
    "ASIANPAINT": "Asian Paints Ltd",
    "BHARTIARTL": "Bharti Airtel Ltd",
    "ONGC": "Oil and Natural Gas Corporation Ltd",
    "POWERGRID": "Power Grid Corporation of India Ltd",
    "NTPC": "NTPC Ltd",
    "COALINDIA": "Coal India Ltd",
    "JIOFIN": "Jio Financial Services Ltd",
    "TATAMOTORS": "Tata Motors Ltd",
    "TATASTEEL": "Tata Steel Ltd",
    "HINDALCO": "Hindalco Industries Ltd",
    "JSWSTEEL": "JSW Steel Ltd",
    "GRASIM": "Grasim Industries Ltd",
    "BPCL": "Bharat Petroleum Corporation Ltd",
    "HEROMOTOCO": "Hero MotoCorp Ltd",
    "BAJAJ-AUTO": "Bajaj Auto Ltd",
    "DIVISLAB": "Divi's Laboratories Ltd",
    "CIPLA": "Cipla Ltd",
    "DRREDDY": "Dr. Reddy's Laboratories Ltd",
    "EICHERMOT": "Eicher Motors Ltd",
    "SHRIRAMFIN": "Shriram Finance Ltd",
    "UPL": "UPL Ltd",
    "BRITANNIA": "Britannia Industries Ltd",
    "INDUSINDBK": "IndusInd Bank Ltd",
}


def extract_symbol_from_query(query: str) -> Optional[str]:
    # Step 1: name-based lookup first — most reliable for natural language
    q_lower = query.lower()
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower:
            return sym

    # Step 2: regex for explicit uppercase tickers — only accept known NSE symbols
    # to avoid false positives like BEST, BANK, TOP, GOOD, etc.
    _SKIP = {
        # articles / prepositions / conjunctions
        "A", "AN", "BY", "TO", "OF", "ON", "AS",
        "IS", "IN", "AT", "OR", "AND", "THE", "FOR",
        # question words
        "HOW", "WHY", "WHAT", "WHEN", "WHERE", "WHICH", "WHO",
        # pronouns
        "IT", "ITS", "THIS", "THAT", "THEY", "THEM",
        # common verbs
        "BUY", "SELL", "GET", "CAN", "WILL", "HAS", "HIT", "ARE", "WAS",
        "GIVE", "SHOW", "TELL", "LIST", "FIND", "SHOULD", "WANT",
        # directional / status
        "UP", "DOWN", "NOW", "NEW", "TOP", "BEST", "HIGH", "LOW", "BIG",
        "MID", "MOST", "LESS", "MORE", "ALL", "ANY",
        # market/finance terms that look like tickers
        "STOCK", "STOCKS", "SHARE", "SHARES", "MARKET", "SECTOR", "SECTORS",
        "BANK", "BANKS", "FUND", "FUNDS", "INDEX", "NIFTY", "NSE", "BSE",
        "SENSEX", "IPO", "FII", "DII",
        # descriptive
        "GOOD", "SAFE", "RISK", "RISKY", "SMALL", "LARGE", "CAP",
        "VALUE", "INDIA", "INDIAN",
        # time
        "YEAR", "MONTH", "WEEK", "TERM", "LONG", "SHORT", "NEXT", "LAST",
        "TODAY",
    }
    q_upper = query.upper().strip()
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', q_upper)
    for tok in tokens:
        if tok not in _SKIP and tok in _VALID_SYMBOLS:
            return tok

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


# ---------------------------------------------------------------------------
# Source 1: NSE India API — P/E, sector, company name, 52-week, live price
# ---------------------------------------------------------------------------
def _fetch_nse_fundamentals(symbol: str) -> dict:
    """
    NSE India official API. Returns P/E, sector P/E avg, company name,
    industry/sector, 52-week high/low, and live price. No API key needed.
    """
    try:
        import requests
        session = requests.Session()
        session.headers.update(_NSE_HEADERS)
        # Step 1: visit home page to get cookies (NSE checks for session)
        session.get("https://www.nseindia.com/", timeout=8)
        resp = session.get(
            f"https://www.nseindia.com/api/quote-equity?symbol={symbol}",
            timeout=8,
        )
        if resp.status_code != 200:
            logger.warning("NSE API returned %s for %s", resp.status_code, symbol)
            return {}
        d = resp.json()
        meta = d.get("metadata", {})
        price_info = d.get("priceInfo", {})
        info = d.get("info", {})
        whl = price_info.get("weekHighLow", {})
        pe_val = meta.get("pdSymbolPe")
        pe_sector_val = meta.get("pdSectorPe")
        return {
            "company_name": info.get("companyName"),
            "sector": info.get("industry") or meta.get("industry"),
            "price": price_info.get("lastPrice") or price_info.get("close"),
            "pe": float(pe_val) if pe_val else None,
            "pe_sector": float(pe_sector_val) if pe_sector_val else None,
            "week52_high": float(whl["max"]) if whl.get("max") else None,
            "week52_low": float(whl["min"]) if whl.get("min") else None,
        }
    except Exception as e:
        logger.warning("NSE fetch failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 1b: Yahoo Finance direct API — P/E, sector, dividend (cloud-reliable)
# ---------------------------------------------------------------------------
_YF_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}


def _fetch_yahoo_fundamentals(symbol: str) -> dict:
    """
    Direct Yahoo Finance quoteSummary API call using requests (not yfinance lib).
    More reliable on cloud/Docker than ticker.info because we control the session.
    Returns P/E, dividend yield, sector, company name.
    """
    try:
        import requests
        yf_sym = f"{symbol}.NS"
        url = (
            "https://query1.finance.yahoo.com/v10/finance/quoteSummary/"
            f"{yf_sym}?modules=summaryDetail,summaryProfile,defaultKeyStatistics"
        )
        resp = requests.get(url, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            # try query2 mirror
            url2 = url.replace("query1", "query2")
            resp = requests.get(url2, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            logger.warning("Yahoo Finance direct API %s for %s", resp.status_code, symbol)
            return {}
        result = (resp.json()
                  .get("quoteSummary", {})
                  .get("result") or [{}])[0]

        def _raw(d: dict, key: str):
            v = d.get(key, {})
            return v.get("raw") if isinstance(v, dict) else v

        summary = result.get("summaryDetail", {})
        profile = result.get("summaryProfile", {})
        stats = result.get("defaultKeyStatistics", {})

        pe = _raw(summary, "trailingPE") or _raw(stats, "forwardPE")
        div = _raw(summary, "dividendYield") or _raw(summary, "trailingAnnualDividendYield")
        sector = profile.get("sector") or profile.get("industry")
        company = profile.get("longBusinessSummary")  # not the name, but we skip it

        return {
            "pe": float(pe) if pe else None,
            "div_yield": float(div) if div else None,
            "sector": sector,
        }
    except Exception as e:
        logger.warning("Yahoo Finance direct API failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 2: Google News RSS — aggregates ET, MoneyControl, Business Standard
# ---------------------------------------------------------------------------
def _fetch_google_news_headlines(symbol: str, company_name: str = "") -> list[str]:
    """
    Google News RSS feed for the stock. Aggregates headlines from
    Economic Times, MoneyControl, Business Standard etc. — no scraping needed.
    """
    try:
        import requests
        query = quote_plus(f"{company_name or symbol} NSE stock India")
        url = (
            f"https://news.google.com/rss/search"
            f"?q={query}&hl=en-IN&gl=IN&ceid=IN:en"
        )
        resp = requests.get(url, timeout=8, headers={"User-Agent": "Mozilla/5.0"})
        if resp.status_code != 200:
            return []
        root = ET.fromstring(resp.content)
        headlines: list[str] = []
        for item in root.findall(".//item")[:10]:
            title = item.findtext("title", "").strip()
            if title:
                # Google News appends " - Source Name"; strip it
                title = title.rsplit(" - ", 1)[0].strip()
                headlines.append(title)
        return headlines
    except Exception as e:
        logger.warning("Google News RSS failed for %s: %s", symbol, e)
        return []


# ---------------------------------------------------------------------------
# Main enricher: merges all four sources
# ---------------------------------------------------------------------------
def _fetch_yfinance(symbol: str) -> dict:
    try:
        import yfinance as yf

        nse_sym = symbol if symbol.endswith(".NS") else f"{symbol}.NS"
        ticker = yf.Ticker(nse_sym)

        # --- Source 1: NSE India API (primary for fundamentals) ---
        nse = _fetch_nse_fundamentals(symbol)
        company_name: str = nse.get("company_name") or _SYMBOL_COMPANY.get(symbol) or symbol
        sector: str = nse.get("sector") or ""
        price = nse.get("price")
        pe = nse.get("pe")
        pe_sector_avg = nse.get("pe_sector")
        week52_high = nse.get("week52_high")
        week52_low = nse.get("week52_low")

        # --- Source 1b: Yahoo Finance direct API (fills gaps when NSE is blocked) ---
        yf_fund = _fetch_yahoo_fundamentals(symbol)
        if not pe:
            pe = yf_fund.get("pe")
        div_yield = yf_fund.get("div_yield")
        if not sector:
            sector = yf_fund.get("sector") or ""

        # --- Hardcoded fallbacks (100% reliable for NIFTY 50) ---
        if not sector:
            sector = _SYMBOL_SECTOR.get(symbol, "N/A")
        if not company_name or company_name == symbol:
            company_name = _SYMBOL_COMPANY.get(symbol, symbol)

        # --- Source 3: yfinance fast_info (market cap + fallback price) ---
        market_cap = None
        try:
            fi = ticker.fast_info
            market_cap = getattr(fi, "market_cap", None) or getattr(fi, "marketCap", None)
            if not price:
                price = getattr(fi, "last_price", None)
            if not week52_high:
                week52_high = getattr(fi, "fifty_two_week_high", None)
            if not week52_low:
                week52_low = getattr(fi, "fifty_two_week_low", None)
        except Exception as e:
            logger.warning("fast_info failed for %s: %s", symbol, e)

        # yfinance ticker.info: last-resort fill for anything still missing
        try:
            info = ticker.info or {}
            if not div_yield:
                div_yield = info.get("dividendYield")
            if not pe:
                pe = info.get("trailingPE") or info.get("forwardPE")
            if not sector or sector == "N/A":
                sector = info.get("sector") or info.get("industry") or _SYMBOL_SECTOR.get(symbol, "N/A")
            if not company_name or company_name == symbol:
                company_name = info.get("longName") or info.get("shortName") or _SYMBOL_COMPANY.get(symbol, symbol)
            if not market_cap:
                market_cap = info.get("marketCap")
            if not week52_high:
                week52_high = info.get("fiftyTwoWeekHigh")
            if not week52_low:
                week52_low = info.get("fiftyTwoWeekLow")
            if not price:
                price = (info.get("currentPrice")
                         or info.get("regularMarketPrice")
                         or info.get("previousClose"))
        except Exception as e:
            logger.warning("ticker.info failed for %s: %s", symbol, e)

        # 52-week position string
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

        # --- Source 4: yf.download() for OHLCV (technicals) ---
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
        rsi_interp = None
        rsi_str = None
        if rsi_val is not None:
            if rsi_val > 70:
                rsi_interp = "overbought"
            elif rsi_val < 30:
                rsi_interp = "oversold"
            else:
                rsi_interp = "neutral"
            rsi_str = f"{rsi_val} ({rsi_interp})"

        # Support & Resistance
        support1 = round(min(lows[-20:]), 2) if len(lows) >= 20 else None
        resistance1 = round(max(highs[-20:]), 2) if len(highs) >= 20 else None
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

        # Sector trend from 1-month price momentum
        sector_trend = "Neutral"
        if len(closes) >= 21:
            ret_1m = (closes[-1] - closes[-21]) / closes[-21] * 100
            if ret_1m > 3:
                sector_trend = f"Bullish (stock +{ret_1m:.1f}% in 1 month)"
            elif ret_1m < -3:
                sector_trend = f"Bearish (stock {ret_1m:.1f}% in 1 month)"
            else:
                sector_trend = f"Neutral (stock {ret_1m:+.1f}% in 1 month)"

        # --- Source 2: Google News RSS (primary for headlines) ---
        headlines = _fetch_google_news_headlines(symbol, company_name)

        # Fallback: yfinance news if Google News returned nothing
        if not headlines:
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
                "rsi_interpretation": rsi_interp,
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
        logger.error("Stock enricher failed for %s: %s", symbol, exc, exc_info=True)
        raise


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
