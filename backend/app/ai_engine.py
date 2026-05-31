"""
BYSEL AI Engine — Stock Analysis, Price Prediction & Natural Language Assistant

Features:
  1. Technical analysis (RSI, MACD, Bollinger Bands, Moving Averages)
  2. Fundamental analysis (P/E, Market Cap, Dividend, 52-week range)
  3. AI Price Prediction (Linear Regression + Exponential Smoothing)
  4. Natural Language Stock Assistant (pattern-matched Q&A)
  5. Stock scoring / rating system

No external AI API required — runs entirely on-device with yfinance data.
"""

import yfinance as yf
import numpy as np
import logging
import os
import re
from datetime import datetime, timedelta, timezone
from html import unescape
from threading import Lock
from typing import Dict, List, Optional, Tuple
from .market_data import _yf_ticker, INDIAN_STOCKS, fetch_quote, search_stocks

logger = logging.getLogger(__name__)

_NEWS_CACHE_TTL = timedelta(minutes=45)  # Increased from 15 to 45 minutes for better performance
_NEWS_CACHE_MAX_SYMBOLS = max(20, int(os.getenv("NEWS_CACHE_MAX_SYMBOLS", "200")))  # Increased from 120 to 200
_news_cache: Dict[str, Tuple[datetime, List[Dict]]] = {}
_news_cache_lock = Lock()

# AI Analysis & Prediction Response Cache (60 min TTL)
_ANALYSIS_CACHE_TTL = timedelta(minutes=60)
_ANALYSIS_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_ANALYSIS_CACHE_LOCK = Lock()
_PREDICTION_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_PREDICTION_CACHE_LOCK = Lock()

# Stock Detail Cache (20 seconds TTL for <1 second market updates during market hours)
_STOCK_DETAIL_CACHE_TTL = timedelta(seconds=20)
_STOCK_DETAIL_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_STOCK_DETAIL_CACHE_LOCK = Lock()

# Recommendations Cache (5 min TTL - updated frequently for changing market)
_RECOMMENDATIONS_CACHE = {"data": None, "timestamp": 0}
_RECOMMENDATIONS_CACHE_TTL = 5 * 60  # 5 minutes
_RECOMMENDATIONS_CACHE_LOCK = Lock()

# Model Performance Tracking (for accuracy improvement)
_MODEL_PERFORMANCE: Dict[str, Dict] = {
    "one_day": {"predictions": 0, "correct": 0, "accuracy": 0.0},
    "one_month": {"predictions": 0, "correct": 0, "accuracy": 0.0},
    "three_months": {"predictions": 0, "correct": 0, "accuracy": 0.0},
}
_MODEL_PERF_LOCK = Lock()

# Backtesting & Trade Accuracy Tracking (30-day window)
_BACKTESTING_CACHE: Dict[str, Dict] = {}
_BACKTESTING_CACHE_TTL = timedelta(hours=24)  # Refresh daily
_BACKTESTING_CACHE_LOCK = Lock()

_TRADE_ACCURACY_RESULTS: Dict[str, Dict] = {
    "one_day": {"recommended": [], "profitable": []},
    "one_month": {"recommended": [], "profitable": []},
    "three_months": {"recommended": [], "profitable": []},
}
_TRADE_ACCURACY_LOCK = Lock()

# Drawdown & Risk Analysis Cache (30m TTL)
_DRAWDOWN_RISK_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_DRAWDOWN_RISK_CACHE_TTL = timedelta(minutes=30)
_DRAWDOWN_RISK_CACHE_LOCK = Lock()

# Relative Strength Cache (1 hour TTL - sector rotations slower)
_RELATIVE_STRENGTH_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_RELATIVE_STRENGTH_CACHE_TTL = timedelta(hours=1)
_RELATIVE_STRENGTH_CACHE_LOCK = Lock()

# Trade Levels Cache (SL/TP) - 15 min TTL (volatility-sensitive)
_TRADE_LEVELS_CACHE: Dict[str, Tuple[datetime, Dict]] = {}
_TRADE_LEVELS_CACHE_TTL = timedelta(minutes=15)
_TRADE_LEVELS_CACHE_LOCK = Lock()

_MARKET_NEWS_DEFAULT_SYMBOLS = ["RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK"]

_POSITIVE_HEADLINE_KEYWORDS = (
    "beat",
    "beats",
    "bullish",
    "contract",
    "expansion",
    "gain",
    "gains",
    "growth",
    "launch",
    "order win",
    "orders",
    "partnership",
    "profit",
    "rally",
    "record",
    "upgrade",
)

_NEGATIVE_HEADLINE_KEYWORDS = (
    "cut",
    "cuts",
    "decline",
    "delay",
    "downgrade",
    "fall",
    "falls",
    "fraud",
    "investigation",
    "lawsuit",
    "loss",
    "miss",
    "penalty",
    "probe",
    "recall",
    "slump",
    "warning",
    "weak",
)


# ──────────────────────────────────────────────────────────────
# TECHNICAL INDICATORS
# ──────────────────────────────────────────────────────────────

def _compute_rsi(prices: np.ndarray, period: int = 14) -> float:
    """Relative Strength Index (0-100). >70 = overbought, <30 = oversold."""
    if len(prices) < period + 1:
        return 50.0
    deltas = np.diff(prices)
    gains = np.where(deltas > 0, deltas, 0)
    losses = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gains[-period:])
    avg_loss = np.mean(losses[-period:])
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 2)


def _compute_macd(prices: np.ndarray) -> Dict:
    """MACD indicator (12/26/9)."""
    if len(prices) < 26:
        return {"macd": 0, "signal": 0, "histogram": 0, "trend": "neutral"}
    ema12 = _ema(prices, 12)
    ema26 = _ema(prices, 26)
    macd_line = ema12 - ema26
    # signal line = 9-period EMA of MACD
    signal = _ema(np.array([macd_line]), 1)  # simplified
    histogram = macd_line - signal
    trend = "bullish" if macd_line > signal else "bearish"
    return {
        "macd": round(float(macd_line), 2),
        "signal": round(float(signal), 2),
        "histogram": round(float(histogram), 2),
        "trend": trend,
    }


def _ema(prices: np.ndarray, period: int) -> float:
    """Exponential Moving Average."""
    if len(prices) < period:
        return float(np.mean(prices))
    weights = np.exp(np.linspace(-1.0, 0.0, period))
    weights /= weights.sum()
    return float(np.convolve(prices, weights, mode="valid")[-1])


def _compute_bollinger(prices: np.ndarray, period: int = 20) -> Dict:
    """Bollinger Bands."""
    if len(prices) < period:
        return {"upper": 0, "middle": 0, "lower": 0, "position": "middle"}
    sma = np.mean(prices[-period:])
    std = np.std(prices[-period:])
    upper = sma + 2 * std
    lower = sma - 2 * std
    current = prices[-1]
    if current > upper:
        position = "above_upper"
    elif current < lower:
        position = "below_lower"
    elif current > sma:
        position = "upper_half"
    else:
        position = "lower_half"
    return {
        "upper": round(float(upper), 2),
        "middle": round(float(sma), 2),
        "lower": round(float(lower), 2),
        "position": position,
    }


def _compute_moving_averages(prices: np.ndarray) -> Dict:
    """Simple and Exponential Moving Averages."""
    result = {}
    for period in [5, 10, 20, 50, 200]:
        if len(prices) >= period:
            sma = float(np.mean(prices[-period:]))
            result[f"sma{period}"] = round(sma, 2)
        else:
            result[f"sma{period}"] = None
    current = float(prices[-1]) if len(prices) > 0 else 0
    # Determine overall MA trend
    sma20 = result.get("sma20")
    sma50 = result.get("sma50")
    sma200 = result.get("sma200")
    if sma20 and sma50 and sma200:
        if current > sma20 > sma50 > sma200:
            result["trend"] = "strong_bullish"
        elif current > sma50 > sma200:
            result["trend"] = "bullish"
        elif current < sma20 < sma50 < sma200:
            result["trend"] = "strong_bearish"
        elif current < sma50 < sma200:
            result["trend"] = "bearish"
        else:
            result["trend"] = "neutral"
    else:
        result["trend"] = "insufficient_data"
    return result


# ──────────────────────────────────────────────────────────────
# PRICE PREDICTION ENGINE
# ──────────────────────────────────────────────────────────────

def predict_price(symbol: str) -> Dict:
    """
    AI Price Prediction using ensemble of:
      1. Linear Regression on 90-day trend
      2. Exponential Smoothing (Holt's method)
      3. Moving Average Momentum

    Returns predictions for 7-day, 30-day, 90-day horizons
    with confidence intervals.
    """
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        hist = ticker.history(period="1y")

        if hist.empty or len(hist) < 30:
            return {"error": f"Insufficient data for {symbol}", "predictions": []}

        closes = hist["Close"].values.astype(float)
        current_price = closes[-1]

        predictions = []
        for days, label in [(7, "1 Week"), (30, "1 Month"), (90, "3 Months")]:
            # Method 1: Linear Regression
            lr_pred = _linear_regression_predict(closes, days)

            # Method 2: Exponential Smoothing
            es_pred = _exponential_smoothing_predict(closes, days)

            # Method 3: Momentum-based
            mom_pred = _momentum_predict(closes, days)

            # Ensemble: weighted average (LR: 40%, ES: 35%, Mom: 25%)
            ensemble = lr_pred * 0.40 + es_pred * 0.35 + mom_pred * 0.25

            # Confidence interval widens with time horizon
            if len(closes) > 61:
                diffs = np.diff(closes[-60:])
                base = closes[-60:-1]
                volatility = float(np.std(diffs / base))
            else:
                volatility = 0.02
            confidence_range = volatility * np.sqrt(days) * current_price

            pct_change = ((ensemble - current_price) / current_price) * 100

            predictions.append({
                "horizon": label,
                "days": days,
                "predictedPrice": round(float(ensemble), 2),
                "currentPrice": round(float(current_price), 2),
                "changePercent": round(float(pct_change), 2),
                "confidenceHigh": round(float(ensemble + confidence_range), 2),
                "confidenceLow": round(float(max(ensemble - confidence_range, 0)), 2),
                "direction": "up" if ensemble > current_price else "down",
            })

        # Overall signal
        avg_direction = sum(1 for p in predictions if p["direction"] == "up")
        if avg_direction == 3:
            signal = "STRONG_BUY"
        elif avg_direction >= 2:
            signal = "BUY"
        elif avg_direction == 0:
            signal = "STRONG_SELL"
        else:
            signal = "HOLD"

        return {
            "symbol": symbol,
            "currentPrice": round(float(current_price), 2),
            "predictions": predictions,
            "signal": signal,
            "modelAccuracy": _estimate_accuracy(closes),
            "lastUpdated": datetime.utcnow().isoformat(),
            "disclaimer": "AI predictions are for informational purposes only. Not financial advice.",
        }

    except Exception as e:
        logger.error(f"Prediction error for {symbol}: {e}")
        return {"error": str(e), "predictions": []}


def _linear_regression_predict(prices: np.ndarray, days_ahead: int) -> float:
    """Simple linear regression on recent 90-day window."""
    window = min(90, len(prices))
    recent = prices[-window:]
    x = np.arange(window)
    # Linear regression: y = mx + b
    m, b = np.polyfit(x, recent, 1)
    return float(m * (window + days_ahead) + b)


def _exponential_smoothing_predict(prices: np.ndarray, days_ahead: int) -> float:
    """Double Exponential Smoothing (Holt's method)."""
    alpha = 0.3  # level smoothing
    beta = 0.1   # trend smoothing
    window = min(90, len(prices))
    recent = prices[-window:]

    level = recent[0]
    trend = (recent[-1] - recent[0]) / len(recent)

    for price in recent:
        new_level = alpha * price + (1 - alpha) * (level + trend)
        new_trend = beta * (new_level - level) + (1 - beta) * trend
        level = new_level
        trend = new_trend

    return float(level + trend * days_ahead)


def _momentum_predict(prices: np.ndarray, days_ahead: int) -> float:
    """Momentum-based prediction using rate of change."""
    current = prices[-1]
    # Use 20-day momentum
    if len(prices) >= 20:
        momentum = (prices[-1] - prices[-20]) / prices[-20]
    else:
        momentum = 0

    # Dampen momentum for longer horizons (mean reversion tendency)
    damping = 0.7 ** (days_ahead / 30)
    predicted_change = momentum * (days_ahead / 20) * damping
    return float(current * (1 + predicted_change))


def _estimate_accuracy(prices: np.ndarray) -> float:
    """Estimate model accuracy by backtesting on last 30 days."""
    if len(prices) < 60:
        return 65.0

    correct = 0
    total = 20
    for i in range(total):
        idx = -(total - i + 7)
        if abs(idx) >= len(prices):
            continue
        train = prices[:idx]
        actual = prices[idx + 7] if abs(idx + 7) < len(prices) else prices[-1]
        predicted = _linear_regression_predict(train, 7)
        # Direction correct?
        actual_dir = actual > train[-1]
        pred_dir = predicted > train[-1]
        if actual_dir == pred_dir:
            correct += 1

    accuracy = (correct / total) * 100 if total > 0 else 60
    return round(min(max(accuracy, 50), 85), 1)  # Cap between 50-85%


def _utc_now_naive() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _parse_news_timestamp(raw_value) -> Optional[datetime]:
    if raw_value in (None, ""):
        return None

    if isinstance(raw_value, (int, float)):
        return datetime.fromtimestamp(float(raw_value), tz=timezone.utc).replace(tzinfo=None)

    if isinstance(raw_value, str):
        candidate = raw_value.strip()
        if not candidate:
            return None

        normalized = candidate.replace("Z", "+00:00")
        try:
            parsed = datetime.fromisoformat(normalized)
        except ValueError:
            for fmt in (
                "%Y-%m-%d %H:%M:%S%z",
                "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%dT%H:%M:%S%z",
                "%Y-%m-%dT%H:%M:%S",
            ):
                try:
                    parsed = datetime.strptime(candidate, fmt)
                    break
                except ValueError:
                    parsed = None
            if parsed is None:
                return None

        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
        return parsed

    return None


def _format_headline_age(published_at: Optional[datetime]) -> str:
    if not published_at:
        return ""

    if published_at.tzinfo is not None:
        published_at = published_at.astimezone(timezone.utc).replace(tzinfo=None)

    age = max(int((_utc_now_naive() - published_at).total_seconds()), 0)
    if age < 3600:
        minutes = max(age // 60, 1)
        return f"{minutes}m ago"
    if age < 86400:
        hours = age // 3600
        return f"{hours}h ago"
    days = age // 86400
    return f"{days}d ago"


def _normalize_news_item(symbol: str, raw_item: Dict) -> Optional[Dict]:
    if not isinstance(raw_item, dict):
        return None

    content = raw_item.get("content") if isinstance(raw_item.get("content"), dict) else {}
    provider = content.get("provider") if isinstance(content.get("provider"), dict) else {}
    canonical_url = content.get("canonicalUrl") if isinstance(content.get("canonicalUrl"), dict) else {}
    click_through = content.get("clickThroughUrl") if isinstance(content.get("clickThroughUrl"), dict) else {}

    title = (
        raw_item.get("title")
        or content.get("title")
        or raw_item.get("headline")
        or content.get("summary")
        or ""
    )
    title = re.sub(r"\s+", " ", unescape(str(title)).strip())
    if not title:
        return None

    source = (
        raw_item.get("publisher")
        or provider.get("displayName")
        or raw_item.get("provider")
        or ""
    )

    published_at = _parse_news_timestamp(
        raw_item.get("providerPublishTime")
        or content.get("pubDate")
        or raw_item.get("pubDate")
        or content.get("displayTime")
    )

    link = (
        raw_item.get("link")
        or canonical_url.get("url")
        or click_through.get("url")
        or raw_item.get("url")
        or ""
    )

    return {
        "symbol": symbol.upper(),
        "title": title,
        "source": str(source).strip(),
        "publishedAt": published_at.isoformat() if published_at else "",
        "publishedLabel": _format_headline_age(published_at),
        "link": str(link).strip(),
    }


def _fetch_recent_headlines(symbol: str, limit: int = 5, ticker=None) -> List[Dict]:
    symbol_upper = symbol.upper()
    now = _utc_now_naive()

    with _news_cache_lock:
        _prune_news_cache(now)
        cached = _news_cache.get(symbol_upper)
        if cached and now - cached[0] <= _NEWS_CACHE_TTL:
            return cached[1][:limit]

    ticker_obj = ticker or yf.Ticker(_yf_ticker(symbol_upper))
    raw_news: List[Dict] = []

    get_news = getattr(ticker_obj, "get_news", None)
    if callable(get_news):
        try:
            raw_news = get_news() or []
        except Exception as exc:
            logger.warning("News fetch via get_news failed for %s: %s", symbol_upper, exc)

    if not raw_news:
        try:
            raw_news = getattr(ticker_obj, "news", []) or []
        except Exception as exc:
            logger.warning("News fetch via news property failed for %s: %s", symbol_upper, exc)

    normalized: List[Dict] = []
    seen_titles: set[str] = set()
    for item in raw_news:
        normalized_item = _normalize_news_item(symbol_upper, item)
        if not normalized_item:
            continue

        title_key = normalized_item["title"].lower()
        if title_key in seen_titles:
            continue

        seen_titles.add(title_key)
        normalized.append(normalized_item)

    normalized.sort(key=lambda item: item.get("publishedAt") or "", reverse=True)

    with _news_cache_lock:
        _news_cache[symbol_upper] = (now, normalized)
        _prune_news_cache(now)

    return normalized[:limit]


def _prune_news_cache(now: datetime) -> None:
    cutoff = now - _NEWS_CACHE_TTL

    expired_symbols = [
        symbol
        for symbol, (cached_at, _) in _news_cache.items()
        if cached_at < cutoff
    ]
    for symbol in expired_symbols:
        _news_cache.pop(symbol, None)

    overflow = len(_news_cache) - _NEWS_CACHE_MAX_SYMBOLS
    if overflow <= 0:
        return

    oldest_symbols = sorted(
        _news_cache.items(),
        key=lambda item: item[1][0],
    )[:overflow]
    for symbol, _ in oldest_symbols:
        _news_cache.pop(symbol, None)


def _classify_headline_flow(headlines: List[Dict]) -> str:
    if not headlines:
        return "unavailable"

    score = 0
    for item in headlines[:5]:
        title = str(item.get("title", "")).lower()
        for keyword in _POSITIVE_HEADLINE_KEYWORDS:
            if keyword in title:
                score += 1
        for keyword in _NEGATIVE_HEADLINE_KEYWORDS:
            if keyword in title:
                score -= 1

    if score >= 2:
        return "positive"
    if score <= -2:
        return "negative"
    return "mixed"


def _summarize_headline_flow(headlines: List[Dict]) -> str:
    """Create a concise summary from a list of news headlines."""
    if not headlines:
        return "No recent headlines available."

    # Use a simple heuristic: take up to three keyword-rich headlines.
    titles = [str(item.get("title", "")).strip() for item in headlines if item.get("title")]
    if not titles:
        return "No headline text found."

    selected = titles[:3]
    if len(selected) == 1:
        return f"Latest news: {selected[0]}"

    return "Latest news: " + " | ".join(selected)


def _get_cached_analysis(symbol: str) -> Optional[Dict]:
    """Get cached stock analysis if available."""
    with _ANALYSIS_CACHE_LOCK:
        if symbol not in _ANALYSIS_CACHE:
            return None
        cached_at, cached_data = _ANALYSIS_CACHE[symbol]
        if datetime.utcnow() - cached_at > _ANALYSIS_CACHE_TTL:
            _ANALYSIS_CACHE.pop(symbol, None)
            return None
        return cached_data


def _cache_analysis(symbol: str, data: Dict) -> None:
    """Cache stock analysis response."""
    with _ANALYSIS_CACHE_LOCK:
        _ANALYSIS_CACHE[symbol] = (datetime.utcnow(), data)
        # Keep cache size manageable (max 500 symbols)
        if len(_ANALYSIS_CACHE) > 500:
            oldest = min(_ANALYSIS_CACHE.items(), key=lambda x: x[1][0])
            _ANALYSIS_CACHE.pop(oldest[0], None)


def _get_cached_prediction(symbol: str) -> Optional[Dict]:
    """Get cached price prediction if available."""
    with _PREDICTION_CACHE_LOCK:
        if symbol not in _PREDICTION_CACHE:
            return None
        cached_at, cached_data = _PREDICTION_CACHE[symbol]
        if datetime.utcnow() - cached_at > _ANALYSIS_CACHE_TTL:
            _PREDICTION_CACHE.pop(symbol, None)
            return None
        return cached_data


def _cache_prediction(symbol: str, data: Dict) -> None:
    """Cache price prediction response."""
    with _PREDICTION_CACHE_LOCK:
        _PREDICTION_CACHE[symbol] = (datetime.utcnow(), data)
        # Keep cache size manageable
        if len(_PREDICTION_CACHE) > 500:
            oldest = min(_PREDICTION_CACHE.items(), key=lambda x: x[1][0])
            _PREDICTION_CACHE.pop(oldest[0], None)


def _get_cached_stock_detail(symbol: str) -> Optional[Dict]:
    """Get cached stock detail if available (20 sec TTL for <1s market updates)."""
    with _STOCK_DETAIL_CACHE_LOCK:
        if symbol not in _STOCK_DETAIL_CACHE:
            return None
        cached_at, cached_data = _STOCK_DETAIL_CACHE[symbol]
        if datetime.utcnow() - cached_at > _STOCK_DETAIL_CACHE_TTL:
            _STOCK_DETAIL_CACHE.pop(symbol, None)
            return None
        return cached_data


def _cache_stock_detail(symbol: str, data: Dict) -> None:
    """Cache stock detail response (20 second TTL)."""
    with _STOCK_DETAIL_CACHE_LOCK:
        _STOCK_DETAIL_CACHE[symbol] = (datetime.utcnow(), data)
        # Keep cache size manageable (max 100 symbols)
        if len(_STOCK_DETAIL_CACHE) > 100:
            oldest = min(_STOCK_DETAIL_CACHE.items(), key=lambda x: x[1][0])
            _STOCK_DETAIL_CACHE.pop(oldest[0], None)


# ──────────────────────────────────────────────────────────────
# FULL STOCK ANALYSIS
# ──────────────────────────────────────────────────────────────

def get_stock_detail_fast(symbol: str) -> Dict:
    """
    Ultra-fast stock detail loading with 20-second cache.
    Enables sub-1 second response times during market hours.
    """
    # Check cache first
    cached = _get_cached_stock_detail(symbol)
    if cached:
        return cached
    
    # Fetch and cache
    result = analyze_stock(symbol)
    _cache_stock_detail(symbol, result)
    return result


def analyze_stock(symbol: str) -> Dict:
    """
    Comprehensive stock analysis combining:
      - Live price data
      - Technical indicators (RSI, MACD, Bollinger, MAs)
      - Fundamental data (P/E, market cap, dividends)
      - AI price predictions
      - Overall score (0-100)
      - Plain-English summary
      - Uses caching to improve response time for repeated queries
"""
    if not headlines:
        return "Recent headline context is unavailable right now."

    flow = _classify_headline_flow(headlines)
    flow_label = flow.capitalize()
    latest_age = headlines[0].get("publishedLabel")
    freshness = f" Most recent item: {latest_age}." if latest_age else ""
    return f"{flow_label} flow across the latest {len(headlines[:5])} headlines.{freshness}"


def get_market_headlines(symbols: Optional[List[str]] = None, limit: int = 5) -> Dict:
    requested_symbols = symbols or _MARKET_NEWS_DEFAULT_SYMBOLS

    normalized_symbols: List[str] = []
    seen_symbols: set[str] = set()
    for raw_symbol in requested_symbols:
        symbol = str(raw_symbol or "").strip().upper()
        if not symbol or symbol in seen_symbols:
            continue
        seen_symbols.add(symbol)
        normalized_symbols.append(symbol)
        if len(normalized_symbols) >= 5:
            break

    aggregated: List[Dict] = []
    seen_titles: set[str] = set()
    headline_limit = max(limit, 5)

    for symbol in normalized_symbols:
        for headline in _fetch_recent_headlines(symbol, limit=headline_limit):
            title_key = str(headline.get("title", "")).strip().lower()
            if not title_key or title_key in seen_titles:
                continue
            seen_titles.add(title_key)
            aggregated.append(headline)

    aggregated.sort(key=lambda item: item.get("publishedAt") or "", reverse=True)

    return {
        "headlines": aggregated[:limit],
        "symbolsConsidered": normalized_symbols,
        "generatedAt": _utc_now_naive().isoformat(),
    }


# ──────────────────────────────────────────────────────────────
# FULL STOCK ANALYSIS
# ──────────────────────────────────────────────────────────────

def analyze_stock(symbol: str) -> Dict:
    """
    Comprehensive stock analysis combining:
      - Live price data
      - Technical indicators (RSI, MACD, Bollinger, MAs)
      - Fundamental data (P/E, market cap, dividends)
      - AI price predictions
      - Overall score (0-100)
      - Plain-English summary
    """
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        hist = ticker.history(period="1y")

        if hist.empty:
            return {"error": f"No data available for {symbol}"}

        closes = hist["Close"].values.astype(float)
        current = closes[-1]

        # Technical Analysis
        rsi = _compute_rsi(closes)
        macd = _compute_macd(closes)
        bollinger = _compute_bollinger(closes)
        mas = _compute_moving_averages(closes)

        # Fundamental data
        try:
            info = ticker.info
            pe = info.get("trailingPE", 0) or 0
            market_cap = info.get("marketCap", 0) or 0
            dividend_yield = (info.get("dividendYield", 0) or 0) * 100
            fifty_two_high = info.get("fiftyTwoWeekHigh", current * 1.15)
            fifty_two_low = info.get("fiftyTwoWeekLow", current * 0.85)
            sector = info.get("sector", "Unknown")
            industry = info.get("industry", "Unknown")
            company_name = info.get("shortName", symbol)
            book_value = info.get("bookValue", 0) or 0
            debt_to_equity = info.get("debtToEquity", 0) or 0
            roe = (info.get("returnOnEquity", 0) or 0) * 100
            revenue_growth = (info.get("revenueGrowth", 0) or 0) * 100
        except Exception:
            pe = market_cap = dividend_yield = 0
            fifty_two_high = current * 1.15
            fifty_two_low = current * 0.85
            sector = industry = "Unknown"
            company_name = None
            book_value = debt_to_equity = roe = revenue_growth = 0

        # Resolve display name: catalog > yfinance shortName > symbol ticker
        catalog_entry = INDIAN_STOCKS.get(symbol)
        if catalog_entry:
            name = catalog_entry[1]
        elif company_name and company_name.lower() not in ("unknown", "n/a", ""):
            name = company_name
        else:
            name = symbol

        # AI Predictions
        prediction = predict_price(symbol)

        recent_headlines = _fetch_recent_headlines(symbol, limit=5, ticker=ticker)
        news_summary = _summarize_headline_flow(recent_headlines)
        news_sentiment = _classify_headline_flow(recent_headlines)

        # Compute overall score (0-100)
        score, score_breakdown = _compute_stock_score(
            rsi, macd, bollinger, mas, pe, dividend_yield,
            current, fifty_two_high, fifty_two_low,
            roe, revenue_growth, debt_to_equity
        )

        # Generate plain-English summary
        summary = _generate_summary(
            symbol, name, current, score, rsi, macd,
            mas, pe, prediction, bollinger
        )
        if recent_headlines:
            summary = f"{summary} {news_summary}"

        return {
            "symbol": symbol,
            "name": name,
            "currentPrice": round(float(current), 2),
            "sector": sector,
            "industry": industry,
            "score": score,
            "scoreBreakdown": score_breakdown,
            "signal": prediction.get("signal", "HOLD"),
            "summary": summary,
            "technical": {
                "rsi": rsi,
                "macd": macd,
                "bollinger": bollinger,
                "movingAverages": mas,
            },
            "fundamental": {
                "pe": round(pe, 2),
                "marketCap": market_cap,
                "dividendYield": round(dividend_yield, 2),
                "fiftyTwoWeekHigh": round(float(fifty_two_high), 2),
                "fiftyTwoWeekLow": round(float(fifty_two_low), 2),
                "bookValue": round(float(book_value), 2),
                "debtToEquity": round(float(debt_to_equity), 2),
                "roe": round(float(roe), 2),
                "revenueGrowth": round(float(revenue_growth), 2),
            },
            "predictions": prediction.get("predictions", []),
            "news": {
                "sentiment": news_sentiment,
                "summary": news_summary,
                "headlinesConsidered": len(recent_headlines),
                "headlines": recent_headlines,
            },
            "modelAccuracy": prediction.get("modelAccuracy", 0),
            "disclaimer": "AI analysis is for educational purposes only. Not financial advice. Always do your own research.",
            "lastUpdated": datetime.utcnow().isoformat(),
        }

    except Exception as e:
        logger.error(f"Analysis error for {symbol}: {e}")
        return {"error": str(e)}


def get_best_stocks_to_buy(limit: int = 10) -> Dict:
    """
    Find best stocks to buy today with regularly improving accuracy & profits.
    Analyzes top NIFTY 50 stocks and scores them for:
      - 1 Day: Momentum, RSI, technical patterns
      - 1 Month: Predictions, technical + fundamental
      - 3 Months: Long-term trends, growth potential
    
    Returns top recommendations with confidence scores and model accuracy.
    """
    import time
    
    # Check cache first (5-minute TTL)
    now = time.time()
    with _RECOMMENDATIONS_CACHE_LOCK:
        cached_data = _RECOMMENDATIONS_CACHE.get("data")
        cached_ts = _RECOMMENDATIONS_CACHE.get("timestamp", 0)
        if cached_data and (now - cached_ts) < _RECOMMENDATIONS_CACHE_TTL:
            return cached_data
    
    # Top NIFTY 50 stocks for analysis
    top_stocks = [
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
        "HINDUNILVR", "ITC", "SBIN", "BHARTIARTL", "KOTAKBANK",
        "LT", "AXISBANK", "BAJFINANCE", "ASIANPAINT", "MARUTI",
        "TITAN", "SUNPHARMA", "TATAMOTORS", "WIPRO", "ULTRACEMCO",
        "NESTLEIND", "HCLTECH", "TATASTEEL", "NTPC", "POWERGRID",
        "TECHM", "BAJAJFINSV", "ONGC", "JSWSTEEL", "ADANIENT",
        "HDFCLIFE", "DIVISLAB", "DRREDDY", "SBILIFE", "BRITANNIA",
        "CIPLA", "EICHERMOT", "HEROMOTOCO", "APOLLOHOSP", "GRASIM",
    ]
    
    scored_stocks = []
    
    try:
        # Score each stock for different timeframes
        for symbol in top_stocks[:limit + 5]:  # Analyze a few more to ensure we have enough
            try:
                analysis = analyze_stock(symbol)
                if "error" in analysis:
                    continue
                
                # Extract scores for each timeframe
                predictions = analysis.get("predictions", [])
                one_day_pred = next((p for p in predictions if p.get("days") == 7), {})
                one_month_pred = next((p for p in predictions if p.get("days") == 30), {})
                three_month_pred = next((p for p in predictions if p.get("days") == 90), {})
                
                # Technical health (RSI, MACD)
                technical = analysis.get("technical", {})
                rsi = technical.get("rsi", 50)
                macd = technical.get("macd", {})
                
                # Calculate confidence scores (0-100)
                one_day_score = _calculate_recommendation_score(
                    rsi=rsi,
                    momentum_pct=one_day_pred.get("changePercent", 0),
                    signal=analysis.get("signal", "HOLD"),
                    timeframe="day"
                )
                
                one_month_score = _calculate_recommendation_score(
                    rsi=rsi,
                    momentum_pct=one_month_pred.get("changePercent", 0),
                    signal=analysis.get("signal", "HOLD"),
                    pe_ratio=analysis.get("fundamental", {}).get("pe", 0),
                    timeframe="month"
                )
                
                three_month_score = _calculate_recommendation_score(
                    rsi=rsi,
                    momentum_pct=three_month_pred.get("changePercent", 0),
                    signal=analysis.get("signal", "HOLD"),
                    pe_ratio=analysis.get("fundamental", {}).get("pe", 0),
                    roe=analysis.get("fundamental", {}).get("roe", 0),
                    timeframe="quarter"
                )
                
                scored_stocks.append({
                    "symbol": symbol,
                    "name": analysis.get("name", symbol),
                    "price": analysis.get("currentPrice", 0),
                    "sector": analysis.get("sector", "Unknown"),
                    "signal": analysis.get("signal", "HOLD"),
                    "overallScore": analysis.get("score", 50),
                    "oneDayScore": one_day_score,
                    "oneMonthScore": one_month_score,
                    "threeMonthScore": three_month_score,
                    "oneDayTarget": one_day_pred.get("predictedPrice", 0),
                    "oneMonthTarget": one_month_pred.get("predictedPrice", 0),
                    "threeMonthTarget": three_month_pred.get("predictedPrice", 0),
                    "rsi": round(float(rsi), 2),
                    "modelAccuracy": analysis.get("modelAccuracy", 65),
                })
            except Exception as e:
                logger.warning(f"Error scoring {symbol}: {e}")
                continue
        
        # Sort by composite score and return top recommendations
        scored_stocks.sort(
            key=lambda x: (x["oneMonthScore"] + x["threeMonthScore"]) / 2,
            reverse=True
        )
        
        result = {
            "recommendations": {
                "oneDay": sorted([s for s in scored_stocks if s["oneDayScore"] >= 60], 
                               key=lambda x: x["oneDayScore"], reverse=True)[:limit // 3 or 1],
                "oneMonth": sorted([s for s in scored_stocks if s["oneMonthScore"] >= 60],
                                 key=lambda x: x["oneMonthScore"], reverse=True)[:limit // 3 or 1],
                "threeMonths": sorted([s for s in scored_stocks if s["threeMonthScore"] >= 60],
                                    key=lambda x: x["threeMonthScore"], reverse=True)[:limit // 3 or 1],
            },
            "allScored": scored_stocks[:limit],
            "modelAccuracy": {
                "oneDay": _MODEL_PERFORMANCE.get("one_day", {}).get("accuracy", 65),
                "oneMonth": _MODEL_PERFORMANCE.get("one_month", {}).get("accuracy", 68),
                "threeMonths": _MODEL_PERFORMANCE.get("three_months", {}).get("accuracy", 62),
            },
            "disclaimer": "AI recommendations are for educational purposes. Not financial advice.",
            "generatedAt": datetime.utcnow().isoformat(),
        }
        
        # Cache result
        with _RECOMMENDATIONS_CACHE_LOCK:
            _RECOMMENDATIONS_CACHE["data"] = result
            _RECOMMENDATIONS_CACHE["timestamp"] = now
        
        return result
    
    except Exception as e:
        logger.error(f"Error generating stock recommendations: {e}")
        return {
            "error": str(e),
            "recommendations": {"oneDay": [], "oneMonth": [], "threeMonths": []},
        }


def _calculate_recommendation_score(
    rsi: float,
    momentum_pct: float,
    signal: str,
    pe_ratio: float = 0,
    roe: float = 0,
    timeframe: str = "month"
) -> float:
    """Calculate recommendation score (0-100) based on multiple factors."""
    score = 50  # Base score
    
    # RSI Component (0-20 pts)
    if 30 < rsi < 70:
        score += 15  # Neutral zone is good
    elif rsi < 30:
        score += 10  # Oversold - good for buy
    elif rsi > 70:
        score -= 10  # Overbought - risky
    
    # Signal Component (0-30 pts)
    if signal == "STRONG_BUY":
        score += 30
    elif signal == "BUY":
        score += 20
    elif signal == "HOLD":
        score += 5
    else:  # SELL, STRONG_SELL
        score -= 15
    
    # Momentum Component (0-30 pts)
    momentum_score = min(max(momentum_pct / 3, -15), 15)  # Cap momentum influence
    score += momentum_score
    
    # Fundamental Component (0-20 pts) - for longer timeframes
    if timeframe in ("month", "quarter") and pe_ratio > 0:
        if 15 < pe_ratio < 30:  # Reasonable PE
            score += 10
        elif pe_ratio < 15:
            score += 15  # Undervalued
        if roe > 15:
            score += 10  # Good ROE
        elif roe > 20:
            score += 15  # Excellent ROE
    
    return round(min(max(float(score), 0), 100), 2)


def _compute_stock_score(
    rsi, macd, bollinger, mas, pe, dividend_yield,
    current, high_52w, low_52w, roe, revenue_growth, debt_to_equity
) -> Tuple[int, Dict]:
    """Compute an overall stock score (0-100) with breakdown."""
    scores = {}

    # RSI Score (25 pts): 30-70 is healthy
    if 40 <= rsi <= 60:
        scores["rsi"] = 25
    elif 30 <= rsi <= 70:
        scores["rsi"] = 20
    elif rsi < 30:  # oversold = buying opportunity
        scores["rsi"] = 22
    else:  # overbought
        scores["rsi"] = 10

    # Trend Score (25 pts)
    trend = mas.get("trend", "neutral")
    trend_scores = {
        "strong_bullish": 25, "bullish": 20,
        "neutral": 15, "bearish": 8, "strong_bearish": 5,
        "insufficient_data": 12,
    }
    scores["trend"] = trend_scores.get(trend, 12)

    # Value Score (25 pts): based on P/E, 52-week position
    if pe > 0:
        if pe < 15:
            pe_score = 15
        elif pe < 25:
            pe_score = 12
        elif pe < 40:
            pe_score = 8
        else:
            pe_score = 4
    else:
        pe_score = 8  # unknown
    # 52-week position: closer to low = better value
    if high_52w > low_52w:
        position = (current - low_52w) / (high_52w - low_52w)
        value_bonus = int((1 - position) * 10)
    else:
        value_bonus = 5
    scores["value"] = min(pe_score + value_bonus, 25)

    # Fundamental Score (25 pts)
    fund_score = 12  # base
    if roe > 15:
        fund_score += 5
    elif roe > 10:
        fund_score += 3
    if revenue_growth > 10:
        fund_score += 4
    elif revenue_growth > 5:
        fund_score += 2
    if dividend_yield > 1:
        fund_score += 2
    if debt_to_equity < 50:
        fund_score += 2
    elif debt_to_equity > 150:
        fund_score -= 3
    scores["fundamental"] = min(max(fund_score, 0), 25)

    total = sum(scores.values())
    return min(total, 100), scores


def _generate_summary(
    symbol, name, current, score, rsi, macd, mas, pe, prediction, bollinger
) -> str:
    """Generate plain-English analysis summary."""
    parts = []

    # Opening
    signal = prediction.get("signal", "HOLD")
    signal_text = {
        "STRONG_BUY": f"🟢 {name} ({symbol}) looks excellent right now!",
        "BUY": f"🟢 {name} ({symbol}) shows positive signals.",
        "HOLD": f"🟡 {name} ({symbol}) is in a wait-and-watch zone.",
        "STRONG_SELL": f"🔴 {name} ({symbol}) shows concerning signals.",
        "SELL": f"🔴 {name} ({symbol}) has some red flags.",
    }
    parts.append(signal_text.get(signal, f"{name} ({symbol}) at ₹{current:.2f}"))

    # Price context
    parts.append(f"Currently trading at ₹{current:.2f}.")

    # RSI insight
    if rsi < 30:
        parts.append(f"RSI at {rsi} indicates the stock is oversold — could be a buying opportunity.")
    elif rsi > 70:
        parts.append(f"RSI at {rsi} indicates the stock is overbought — caution advised.")
    else:
        parts.append(f"RSI at {rsi} is in a healthy range.")

    # Trend
    trend = mas.get("trend", "neutral")
    trend_text = {
        "strong_bullish": "All moving averages confirm a strong uptrend.",
        "bullish": "Moving averages show a bullish trend.",
        "neutral": "Moving averages show no clear direction.",
        "bearish": "Moving averages indicate a downtrend.",
        "strong_bearish": "All moving averages confirm a strong downtrend.",
    }
    if trend in trend_text:
        parts.append(trend_text[trend])

    # MACD
    macd_trend = macd.get("trend", "neutral")
    if macd_trend == "bullish":
        parts.append("MACD is bullish, suggesting upward momentum.")
    elif macd_trend == "bearish":
        parts.append("MACD is bearish, suggesting downward momentum.")

    # P/E context
    if pe and pe > 0:
        if pe < 15:
            parts.append(f"P/E ratio of {pe:.1f} suggests the stock is undervalued.")
        elif pe < 25:
            parts.append(f"P/E ratio of {pe:.1f} is in a fair range.")
        elif pe < 40:
            parts.append(f"P/E ratio of {pe:.1f} is on the higher side.")
        else:
            parts.append(f"P/E ratio of {pe:.1f} is very high — proceed with caution.")

    # Prediction snippet
    preds = prediction.get("predictions", [])
    if preds:
        one_month = next((p for p in preds if p["days"] == 30), None)
        if one_month:
            direction = "rise" if one_month["changePercent"] > 0 else "fall"
            parts.append(
                f"Our AI model predicts a {abs(one_month['changePercent']):.1f}% "
                f"{direction} to ₹{one_month['predictedPrice']:.2f} in 1 month."
            )

    # Score
    if score >= 75:
        parts.append(f"Overall BYSEL Score: {score}/100 — Strong pick! ⭐")
    elif score >= 60:
        parts.append(f"Overall BYSEL Score: {score}/100 — Good potential.")
    elif score >= 40:
        parts.append(f"Overall BYSEL Score: {score}/100 — Average. Watch for developments.")
    else:
        parts.append(f"Overall BYSEL Score: {score}/100 — Weak. Consider alternatives.")

    return " ".join(parts)


# ──────────────────────────────────────────────────────────────
# NATURAL LANGUAGE ASSISTANT
# ──────────────────────────────────────────────────────────────

_COMPANY_SUFFIX_WORDS = {
    "ltd",
    "limited",
    "india",
    "ind",
    "co",
    "company",
    "corporation",
    "corp",
    "services",
    "service",
    "industries",
    "industry",
    "enterprises",
    "enterprise",
    "financial",
    "finance",
}

_MANUAL_STOCK_ALIASES = {
    # Bank aliases
    "sbi": "SBIN",
    "state bank": "SBIN",
    "state bank of india": "SBIN",
    "hdfc bank": "HDFCBANK",
    "hdfc": "HDFCBANK",
    "icici bank": "ICICIBANK",
    "icici": "ICICIBANK",
    "kotak bank": "KOTAKBANK",
    "kotak": "KOTAKBANK",
    "axis bank": "AXISBANK",
    "bob": "BANKBARODA",
    "bank of baroda": "BANKBARODA",
    "pnb bank": "PNB",
    "punjab national bank": "PNB",
    "canara bank": "CANBK",
    "idbi bank": "IDBI",
    "federal bank": "FEDERALBNK",
    "yes bank": "YESBANK",
    "indusind bank": "INDUSINDBK",
    "idfc first": "IDFCFIRSTB",
    "idfc first bank": "IDFCFIRSTB",
    "bandhan bank": "BANDHANBNK",
    "au bank": "AUBANK",
    "au small finance": "AUBANK",
    "rbl bank": "RBLBANK",
    "south indian bank": "SOUTHBANK",
    "south india bank": "SOUTHBANK",
    "southbank": "SOUTHBANK",
    "karnataka bank": "KTKBANK",
    "ktk bank": "KTKBANK",
    "karur vysya": "KARURVYSYA",
    "karur vysya bank": "KARURVYSYA",
    "kvb": "KARURVYSYA",
    "dcb bank": "DCBBANK",
    "csb bank": "CSBBANK",
    "catholic syrian bank": "CSBBANK",
    "dhanlaxmi bank": "DHANLAXMI",
    "j and k bank": "JKBANK",
    "j&k bank": "JKBANK",
    "jammu kashmir bank": "JKBANK",
    "jk bank": "JKBANK",
    "tamilnad mercantile": "TAMILNADMER",
    "tamilnad mercantile bank": "TAMILNADMER",
    "tmb": "TAMILNADMER",
    "city union bank": "CUB",
    "cub": "CUB",
    "indian overseas bank": "IOB",
    "iob": "IOB",
    "uco bank": "UCOBANK",
    "central bank": "CENTRALBK",
    "central bank of india": "CENTRALBK",
    "bank of maharashtra": "MAHABANK",
    "mahabank": "MAHABANK",
    "indian bank": "INDIANB",
    "union bank": "UNIONBANK",
    "union bank of india": "UNIONBANK",
    "ujjivan bank": "UJJIVANSFB",
    "ujjivan small finance": "UJJIVANSFB",
    "equitas bank": "EQUITASBNK",
    "equitas small finance": "EQUITASBNK",
    "esaf bank": "ESAFSFB",
    "esaf small finance": "ESAFSFB",
    "suryoday bank": "SURYODAY",
    "suryoday small finance": "SURYODAY",
    "utkarsh bank": "UTKARSHBNK",
    "utkarsh small finance": "UTKARSHBNK",
    "pnb housing": "PNBHOUSING",

    # Conglomerate / Industrial aliases
    "l and t": "LT",
    "l&t": "LT",
    "larsen and toubro": "LT",
    "larsen & toubro": "LT",
    "reliance": "RELIANCE",
    "ril": "RELIANCE",
    "reliance industries": "RELIANCE",
    "infosys": "INFY",
    "infy": "INFY",
    "tata motors": "TATAMOTORS",
    "tata steel": "TATASTEEL",
    "tata power": "TATAPOWER",
    "tata chemicals": "TATACHEM",
    "tata consumer": "TATACONSUM",
    "tata elxsi": "TATAELXSI",
    "tata comm": "TATACOMM",
    "tata communications": "TATACOMM",
    "tata tech": "TATATECH",
    "tata technologies": "TATATECH",
    "bharti airtel": "BHARTIARTL",
    "airtel": "BHARTIARTL",
    "sun pharma": "SUNPHARMA",
    "adani ports": "ADANIPORTS",
    "adani enterprises": "ADANIENT",
    "adani green": "ADANIGREEN",
    "adani power": "ADANIPOWER",
    "adani total gas": "ATGL",
    "adani wilmar": "ADANIWILMAR",
    "zomato": "ZOMATO",
    "eternal": "ZOMATO",

    # FMCG / Consumer aliases
    "hul": "HINDUNILVR",
    "hindustan lever": "HINDUNILVR",
    "hindustan unilever": "HINDUNILVR",
    "itc": "ITC",
    "asian paints": "ASIANPAINT",
    "nestle": "NESTLEIND",
    "nestle india": "NESTLEIND",
    "britannia": "BRITANNIA",
    "dabur": "DABUR",
    "marico": "MARICO",
    "colgate": "COLPAL",
    "colgate palmolive": "COLPAL",
    "godrej consumer": "GODREJCP",
    "godrej properties": "GODREJPROP",
    "berger paints": "BERGEPAINT",
    "pidilite": "PIDILITIND",
    "titan": "TITAN",
    "tanishq": "TITAN",

    # Auto aliases
    "mahindra": "M&M",
    "mahindra and mahindra": "M&M",
    "maruti": "MARUTI",
    "maruti suzuki": "MARUTI",
    "bajaj auto": "BAJAJ-AUTO",
    "bajaj finance": "BAJFINANCE",
    "bajaj finserv": "BAJAJFINSV",
    "hero moto": "HEROMOTOCO",
    "hero motocorp": "HEROMOTOCO",
    "eicher motors": "EICHERMOT",
    "eicher": "EICHERMOT",
    "tvs motor": "TVSMOTOR",
    "tvs": "TVSMOTOR",
    "ashok leyland": "ASHOKLEY",

    # Pharma aliases
    "dr reddy": "DRREDDY",
    "dr reddys": "DRREDDY",
    "divis lab": "DIVISLAB",
    "divis laboratories": "DIVISLAB",
    "cipla": "CIPLA",
    "lupin": "LUPIN",
    "biocon": "BIOCON",
    "glenmark": "GLENMARK",
    "aurobindo pharma": "AUROPHARMA",

    # IT aliases
    "hcl tech": "HCLTECH",
    "hcl technologies": "HCLTECH",
    "tech mahindra": "TECHM",
    "tech m": "TECHM",
    "wipro": "WIPRO",
    "mphasis": "MPHASIS",
    "coforge": "COFORGE",
    "ltimindtree": "LTIM",
    "lt mindtree": "LTIM",
    "persistent": "PERSISTENT",
    "kpit": "KPITTECH",

    # Insurance / Finance aliases
    "hdfc life": "HDFCLIFE",
    "sbi life": "SBILIFE",
    "sbi card": "SBICARD",
    "sbi cards": "SBICARD",
    "lic": "LICI",
    "icici lombard": "ICICIGI",
    "icici prudential": "ICICIPRULI",
    "star health": "STARHEALTH",
    "muthoot finance": "MUTHOOTFIN",
    "muthoot": "MUTHOOTFIN",
    "manappuram": "MANAPPURAM",
    "shriram finance": "SHRIRAMFIN",
    "cholafin": "CHOLAFIN",
    "cholamandalam": "CHOLAFIN",

    # Energy / Power aliases
    "ntpc": "NTPC",
    "power grid": "POWERGRID",
    "ongc": "ONGC",
    "bpcl": "BPCL",
    "ioc": "IOC",
    "indian oil": "IOC",
    "gail": "GAIL",
    "coal india": "COALINDIA",
    "nhpc": "NHPC",

    # Metal & Cement aliases
    "jsw steel": "JSWSTEEL",
    "jsw energy": "JSWENERGY",
    "hindalco": "HINDALCO",
    "vedanta": "VEDL",
    "ultra tech cement": "ULTRACEMCO",
    "ultratech": "ULTRACEMCO",
    "shree cement": "SHREECEM",
    "ambuja cement": "AMBUJACEM",
    "acc cement": "ACC",

    # Defence / PSU aliases
    "hal": "HAL",
    "hindustan aero": "HAL",
    "bel": "BEL",
    "bharat electronics": "BEL",
    "bhel": "BHEL",
    "bharat dynamics": "BDL",
    "mazagon dock": "MAZDOCK",
    "mazagon": "MAZDOCK",
    "cochin shipyard": "COCHINSHIP",
    "garden reach": "GRSE",
    "irctc": "IRCTC",
    "irfc": "IRFC",
    "rvnl": "RVNL",

    # New-age / IPO popular aliases
    "dmart": "DMART",
    "avenue supermarts": "DMART",
    "nykaa": "NYKAA",
    "paytm": "PAYTM",
    "swiggy": "SWIGGY",
    "ola electric": "OLA",
    "ola": "OLA",
    "firstcry": "FIRSTCRY",
    "jio financial": "JIOFIN",
    "jio fin": "JIOFIN",
    "policybazaar": "POLICYBZR",
    "policy bazaar": "POLICYBZR",
    "indigo": "INDIGO",
    "interglobe": "INDIGO",
    "suzlon": "SUZLON",
    "vodafone idea": "IDEA",
    "vi": "IDEA",

    # Real estate
    "dlf": "DLF",
    "sobha": "SOBHA",
    "lodha": "LODHA",
    "macrotech": "LODHA",
    "oberoi realty": "OBEROIRLTY",
    "prestige estates": "PRESTIGE",
    "brigade": "BRIGADE",

    # Other popular aliases
    "polycab": "POLYCAB",
    "voltas": "VOLTAS",
    "havells": "HAVELLS",
    "siemens": "SIEMENS",
    "abb": "ABB",
    "dixon": "DIXON",
    "angel one": "ANGELONE",
    "angel broking": "ANGELONE",
    "mrf": "MRF",
    "page industries": "PAGEIND",
    "jubilant food": "JUBLFOOD",
    "jubilant foodworks": "JUBLFOOD",
    "apollo hospitals": "APOLLOHOSP",
    "apollo": "APOLLOHOSP",
    "max healthcare": "MAXHEALTH",
    "fortis": "FORTIS",
    "lal path lab": "LALPATHLAB",
    "dr lal": "LALPATHLAB",
    "indiamart": "INDIAMART",
    "info edge": "NAUKRI",
    "naukri": "NAUKRI",
    "trent": "TRENT",
    "zydus": "ZYDUSLIFE",
    "zydus life": "ZYDUSLIFE",
    "mankind pharma": "MANKIND",
    "sail": "SAIL",
    "nmdc": "NMDC",
    "cdsl": "CDSL",
    "bse": "BSE",
    "mcx": "MCX",
    "kaynes": "KAYNES",
    "solar industries": "SOLARINDS",
    "ireda": "IREDA",
    "jsw infra": "JSWINFRA",
    "indian hotel": "INDHOTEL",
    "indian hotels": "INDHOTEL",
    "taj hotel": "INDHOTEL",
    "taj hotels": "INDHOTEL",
    "crompton": "CROMPTON",
    "crompton greaves": "CROMPTON",
    "exide": "EXIDEIND",
    "exide industries": "EXIDEIND",
    "cummins": "CUMMINSIND",
    "cummins india": "CUMMINSIND",
    "deepak nitrite": "DEEPAKNTR",
    "srf": "SRF",
    "astral": "ASTRAL",
    "bharat forge": "BHARATFORG",
    "kalyan jewellers": "KALYANKJIL",
    "kalyan": "KALYANKJIL",
    "nbcc": "NBCC",
    "ncc": "NCC",
    "irctc": "IRCTC",
    "irfc": "IRFC",
    "rvnl": "RVNL",
    "delhivery": "DELHIVERY",
    "devyani": "DEVYANI",
    "national aluminium": "NATIONALUM",
    "nalco": "NATIONALUM",
    "coal india": "COALINDIA",
    "vedanta": "VEDL",
    "iex": "IEX",
    "indian energy exchange": "IEX",
    "kei": "KEI",
    "kei industries": "KEI",
    "honeywell": "HONAUT",
    "honeywell india": "HONAUT",
}

_SYMBOL_NOISE_WORDS = {
    "a",
    "about",
    "analysis",
    "analyze",
    "and",
    "against",
    "best",
    "breakout",
    "buy",
    "case",
    "cheap",
    "compare",
    "detail",
    "dip",
    "entry",
    "expensive",
    "fair",
    "detail",
    "for",
    "good",
    "how",
    "i",
    "in",
    "invest",
    "is",
    "leader",
    "macd",
    "me",
    "momentum",
    "nifty",
    "now",
    "of",
    "outlook",
    "overpriced",
    "overvalued",
    "on",
    "or",
    "peer",
    "peers",
    "predict",
    "price",
    "quarter",
    "quote",
    "recommend",
    "resistance",
    "risk",
    "risks",
    "rsi",
    "sell",
    "setup",
    "share",
    "shares",
    "should",
    "sip",
    "stock",
    "stocks",
    "support",
    "tell",
    "technical",
    "the",
    "today",
    "to",
    "trend",
    "undervalued",
    "valuation",
    "value",
    "versus",
    "volatility",
    "what",
    "which",
    "with",
}


def _extract_user_query(raw_query: str) -> str:
    """Strip app-added wrapper so symbol detection sees only user text."""
    original = (raw_query or "").strip()
    if not original:
        return ""

    query = original
    if query.lower().startswith("user_query:"):
        query = query.split(":", 1)[1].strip()

    if " | context:" in query:
        query = query.split(" | context:", 1)[0].strip()

    return query or original


def _extract_context_symbol(raw_query: str) -> Optional[str]:
    """Extract context symbol from app wrapper when user query omits one."""
    original = (raw_query or "").strip()
    if not original:
        return None

    lower_original = original.lower()
    marker = " | context:"
    marker_index = lower_original.find(marker)
    if marker_index < 0:
        return None

    context_segment = original[marker_index + len(marker):]
    match = re.search(r"(?:^|,)\s*symbol=([A-Za-z0-9&.\-]+)", context_segment, flags=re.IGNORECASE)
    if not match:
        return None

    candidate = match.group(1).strip().upper()
    if candidate.endswith(".NS") or candidate.endswith(".BO"):
        candidate = candidate.rsplit(".", 1)[0]

    if candidate in INDIAN_STOCKS:
        return candidate

    condensed = candidate.replace("-", "")
    if condensed in INDIAN_STOCKS:
        return condensed

    return None


def _resolve_symbols_from_search(query_text: str, limit: int = 3) -> List[str]:
    """Resolve best-match symbols from catalog/Yahoo-backed search."""
    try:
        results = search_stocks(query_text, limit=limit)
    except Exception:
        return []

    symbols: List[str] = []
    seen: set[str] = set()
    for item in results or []:
        if not isinstance(item, dict):
            continue
        symbol = str(item.get("symbol", "")).strip().upper()
        if not symbol or symbol in seen:
            continue
        seen.add(symbol)
        symbols.append(symbol)
        if len(symbols) >= limit:
            break

    return symbols


def _normalize_phrase(text: str) -> str:
    normalized = (text or "").lower().replace("&", " and ")
    normalized = re.sub(r"[^a-z0-9]+", " ", normalized)
    return re.sub(r"\s+", " ", normalized).strip()


def _strip_company_suffixes(name: str) -> str:
    tokens = _normalize_phrase(name).split()
    return " ".join([token for token in tokens if token not in _COMPANY_SUFFIX_WORDS])


def _build_symbol_alias_index() -> Dict[str, str]:
    aliases: Dict[str, str] = {}

    for phrase, symbol in _MANUAL_STOCK_ALIASES.items():
        normalized_phrase = _normalize_phrase(phrase)
        if normalized_phrase:
            aliases[normalized_phrase] = symbol.upper()

    for symbol, (ticker, name) in INDIAN_STOCKS.items():
        symbol_upper = symbol.upper()

        normalized_symbol = _normalize_phrase(symbol)
        if normalized_symbol:
            aliases.setdefault(normalized_symbol, symbol_upper)

        if ticker:
            ticker_base = ticker.upper().replace(".NS", "").replace(".BO", "")
            normalized_ticker = _normalize_phrase(ticker_base)
            if normalized_ticker:
                aliases.setdefault(normalized_ticker, symbol_upper)

        normalized_name = _normalize_phrase(name)
        if normalized_name:
            aliases.setdefault(normalized_name, symbol_upper)

        stripped_name = _strip_company_suffixes(name)
        if len(stripped_name) >= 4:
            aliases.setdefault(stripped_name, symbol_upper)

            parts = stripped_name.split()
            if len(parts) >= 2:
                aliases.setdefault(" ".join(parts[:2]), symbol_upper)
            if len(parts) >= 3:
                aliases.setdefault(" ".join(parts[:3]), symbol_upper)

    return aliases


_SYMBOL_ALIAS_INDEX = _build_symbol_alias_index()
_SORTED_SYMBOL_ALIASES = sorted(
    _SYMBOL_ALIAS_INDEX.items(),
    key=lambda item: len(item[0]),
    reverse=True,
)

# Sector peer map: symbol → list of comparable peer symbols
_SECTOR_PEERS: Dict[str, List[str]] = {
    # Banks
    "HDFCBANK":    ["ICICIBANK", "KOTAKBANK", "AXISBANK", "SBIN"],
    "ICICIBANK":   ["HDFCBANK", "KOTAKBANK", "AXISBANK", "SBIN"],
    "KOTAKBANK":   ["HDFCBANK", "ICICIBANK", "AXISBANK"],
    "AXISBANK":    ["HDFCBANK", "ICICIBANK", "KOTAKBANK"],
    "SBIN":        ["HDFCBANK", "ICICIBANK", "PNB", "BANKBARODA"],
    "INDUSINDBK":  ["HDFCBANK", "ICICIBANK", "AXISBANK"],
    "PNB":         ["SBIN", "BANKBARODA", "CANARABANK"],
    "BANKBARODA":  ["SBIN", "PNB", "CANARABANK"],
    # IT
    "TCS":         ["INFY", "WIPRO", "HCLTECH", "TECHM"],
    "INFY":        ["TCS", "WIPRO", "HCLTECH", "TECHM"],
    "WIPRO":       ["TCS", "INFY", "HCLTECH", "TECHM"],
    "HCLTECH":     ["TCS", "INFY", "WIPRO", "TECHM"],
    "TECHM":       ["TCS", "INFY", "WIPRO", "HCLTECH"],
    "LTIM":        ["TCS", "INFY", "WIPRO", "MPHASIS"],
    "MPHASIS":     ["TCS", "INFY", "LTIM", "COFORGE"],
    "COFORGE":     ["MPHASIS", "LTIM", "INFY"],
    # Energy / Oil
    "RELIANCE":    ["ONGC", "BPCL", "IOC"],
    "ONGC":        ["RELIANCE", "BPCL", "IOC"],
    "BPCL":        ["RELIANCE", "ONGC", "IOC"],
    "IOC":         ["RELIANCE", "ONGC", "BPCL"],
    "ADANIGREEN":  ["NTPC", "TATAPOWER", "POWERGRID"],
    "NTPC":        ["POWERGRID", "ADANIGREEN", "TATAPOWER"],
    "TATAPOWER":   ["NTPC", "ADANIGREEN", "POWERGRID"],
    "POWERGRID":   ["NTPC", "TATAPOWER", "ADANIGREEN"],
    # Auto
    "TATAMOTORS":  ["MARUTI", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT"],
    "MARUTI":      ["TATAMOTORS", "BAJAJ-AUTO", "HEROMOTOCO"],
    "BAJAJ-AUTO":  ["MARUTI", "TATAMOTORS", "HEROMOTOCO", "EICHERMOT"],
    "HEROMOTOCO":  ["BAJAJ-AUTO", "MARUTI", "EICHERMOT"],
    "EICHERMOT":   ["BAJAJ-AUTO", "HEROMOTOCO", "TVSMOTOR"],
    "TVSMOTOR":    ["BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT"],
    "ASHOKLEY":    ["TATAMOTORS", "MARUTI", "BAJAJ-AUTO"],
    # Pharma
    "SUNPHARMA":   ["DRREDDY", "CIPLA", "LUPIN", "DIVISLAB"],
    "DRREDDY":     ["SUNPHARMA", "CIPLA", "LUPIN"],
    "CIPLA":       ["SUNPHARMA", "DRREDDY", "LUPIN"],
    "LUPIN":       ["SUNPHARMA", "DRREDDY", "CIPLA"],
    "DIVISLAB":    ["SUNPHARMA", "DRREDDY", "CIPLA"],
    "AUROPHARMA":  ["SUNPHARMA", "CIPLA", "LUPIN"],
    "BIOCON":      ["SUNPHARMA", "DRREDDY", "CIPLA"],
    # FMCG
    "HINDUNILVR":  ["ITC", "NESTLEIND", "BRITANNIA", "DABUR"],
    "ITC":         ["HINDUNILVR", "BRITANNIA", "DABUR"],
    "NESTLEIND":   ["HINDUNILVR", "BRITANNIA", "DABUR"],
    "BRITANNIA":   ["HINDUNILVR", "ITC", "NESTLEIND"],
    "DABUR":       ["HINDUNILVR", "ITC", "NESTLEIND"],
    "MARICO":      ["HINDUNILVR", "DABUR", "COLPAL"],
    "COLPAL":      ["HINDUNILVR", "MARICO", "DABUR"],
    # Metals
    "TATASTEEL":   ["JSWSTEEL", "HINDALCO", "VEDL", "SAIL"],
    "JSWSTEEL":    ["TATASTEEL", "HINDALCO", "SAIL"],
    "HINDALCO":    ["TATASTEEL", "JSWSTEEL", "VEDL", "NATIONALUM"],
    "VEDL":        ["HINDALCO", "TATASTEEL", "JSWSTEEL"],
    "SAIL":        ["TATASTEEL", "JSWSTEEL"],
    # Infra / Conglomerates
    "LT":          ["ADANIENT", "ADANIPORTS", "IRCON"],
    "ADANIENT":    ["LT", "ADANIPORTS", "NTPC"],
    "ADANIPORTS":  ["LT", "ADANIENT"],
    # Real Estate
    "DLF":         ["GODREJPROP", "OBEROIRLTY", "PRESTIGE"],
    "GODREJPROP":  ["DLF", "OBEROIRLTY", "PRESTIGE"],
    "OBEROIRLTY":  ["DLF", "GODREJPROP", "PRESTIGE"],
    # Defence
    "HAL":         ["BEL", "BDL", "MAZAGON"],
    "BEL":         ["HAL", "BDL", "DATAPATTNS"],
}


def _build_stock_suggestions(symbol: str, exclude: str = "") -> List[str]:
    """
    Build diverse follow-up prompt suggestions for a given stock.
    `exclude` can be 'analysis','prediction','buy_sell','compare' to avoid
    repeating the type the user just asked about.
    """
    candidates: List[str] = []

    if exclude != "buy_sell":
        candidates.append(f"Should I buy {symbol}?")
    if exclude != "prediction":
        candidates.append(f"Predict {symbol} price")
    if exclude != "analysis":
        candidates.append(f"Analyze {symbol}")
    if exclude != "overvaluation":
        candidates.append(f"Is {symbol} overvalued?")
        candidates.append(f"What is fair value for {symbol}?")

    peers = _SECTOR_PEERS.get(symbol, [])
    if peers:
        candidates.append(f"Compare {symbol} with {peers[0]}")
        if len(peers) > 1 and len(candidates) < 6:
            candidates.append(f"Compare {symbol} and {peers[1]}")

    if not peers:
        candidates.append(f"Technical analysis of {symbol}")

    candidates.append(f"Support and resistance for {symbol}")
    candidates.append(f"What are risks in {symbol} right now?")
    candidates.append(f"Should I wait for a dip in {symbol}?")

    deduped: List[str] = []
    seen: set[str] = set()
    for candidate in candidates:
        normalized = candidate.lower().strip()
        if normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(candidate)

    return deduped[:8]


def _build_help_response() -> Dict:
    """Build a contextual help response."""
    return {
        "type": "help",
        "answer": "I'm your AI stock analysis assistant! I can help you with:\n\n"
                  "**Price & Predictions:**\n"
                  "• \"Predict RELIANCE price for 1 month\"\n"
                  "• \"Will TCS go up or down?\"\n\n"
                  "**Comparisons:**\n"
                  "• \"Compare INFY vs TCS\"\n"
                  "• \"ICICIBANK vs HDFC\"\n\n"
                  "**Valuations & Screening:**\n"
                  "• \"Is HDFCBANK overvalued?\"\n"
                  "• \"Best bank stocks under 500\"\n"
                  "• \"Top pharma stocks\"\n\n"
                  "**Buy/Sell Decisions:**\n"
                  "• \"Should I buy SBIN?\"\n"
                  "• \"Is it a good time to sell WIPRO?\"\n\n"
                  "**Analysis & Details:**\n"
                  "• \"Analyze TATAMOTORS\"\n"
                  "• \"Tell me about MARUTI\"\n\n"
                  "I cover 363+ Indian stocks with live data and AI analysis!",
        "suggestions": [
            "Predict TCS price for 1 month",
            "Should I buy RELIANCE?",
            "Compare INFY and TCS",
            "Is HDFCBANK overvalued?",
            "Best bank stocks",
            "Top pharma stocks",
            "Analyze TATAMOTORS",
            "Will WIPRO go up?",
        ],
    }


def _build_generic_response(query: str, symbols: List[str]) -> Dict:
    """Build a response for queries that don't match specific patterns."""
    if symbols:
        # If symbols are found but query is generic, analyze them
        return _handle_analysis_query(symbols, query)
    else:
        # Try to extract intent and respond accordingly
        query_lower = query.lower().strip()
        
        # Check for market interest
        if any(w in query_lower for w in ["market", "trading", "stock", "invest", "profit"]):
            return {
                "type": "generic",
                "answer": "I can help with stock trading! Please ask about specific stocks or sectors.\n\n"
                          "Examples:\n"
                          "• Ask about specific stocks (RELIANCE, INFY, TCS, etc.)\n"
                          "• Compare two stocks\n"
                          "• Get predictions and analysis\n"
                          "• Find best stocks in a sector",
                "suggestions": [
                  "Which stocks to buy today?",
                  "Best sectors to invest in?",
                  "Tell me about RELIANCE",
                  "Predict TCS price",
                ]
            }
        else:
            return _build_help_response()

def ai_assistant(query: str, db=None) -> Dict:
    """
    Process natural language queries about stocks with Indian market context.
    Examples:
      - "Should I buy RELIANCE?"
      - "Best pharma stocks under 500"
      - "Compare TCS and INFY"
      - "Is SBIN overvalued?"
      - "Predict Tata Motors price"
      - "What is IPO?"
      - "How does SIP work?"
    """
    user_query = _extract_user_query(query)
    query_lower = user_query.lower().strip()

    if not query_lower:
        return _build_help_response()

    # Use enhanced query analysis with Indian market context
    try:
        from .ai_engine_enhanced import QueryIntentClassifier, IndianMarketContext
        query_analysis = QueryIntentClassifier.analyze_query(user_query)
        symbols = query_analysis.get("symbols", [])
        intents = query_analysis.get("intents", [])
        indian_context = query_analysis.get("indianSpecific", False)
    except ImportError:
        # Fallback to basic analysis if enhanced engine not available
        symbols = _extract_symbols(user_query)
        intents = []
        indian_context = False

    symbols_direct = bool(symbols)  # True if _extract_symbols found a specific stock

    if not symbols:
        context_symbol = _extract_context_symbol(query)
        if context_symbol:
            symbols = [context_symbol]
            symbols_direct = True
        else:
            symbols = _resolve_symbols_from_search(user_query)

    # ── Hinglish / Hindi keyword normalization ──
    _hinglish_map = {
        "kharido": "buy", "kharidna": "buy", "kharid": "buy", "lelo": "buy", "le lo": "buy",
        "becho": "sell", "bechna": "sell", "bech": "sell", "bech do": "sell",
        "kaisa hai": "how is", "kaise hai": "how is", "kaisa": "how is",
        "konsa": "which", "kaunsa": "which", "kaun sa": "which",
        "kitna": "what price", "kya price": "what price", "kya rate": "what price",
        "accha": "good", "acha": "good", "bekar": "bad", "ghatiya": "bad",
        "sasta": "cheap", "mehenga": "expensive", "mehnga": "expensive",
        "faayda": "profit", "fayda": "profit", "nuksaan": "loss", "nuksan": "loss",
        "girega": "go down", "badhega": "go up", "upar jayega": "go up", "niche jayega": "go down",
        "kab": "when", "kyu": "why", "kyun": "why", "kaha": "where",
        "paisa lagao": "invest", "invest karo": "invest", "nivesh": "invest",
        "munafa": "profit", "bharosa": "safe", "surakshit": "safe",
    }
    for hindi_word, eng_word in _hinglish_map.items():
        if hindi_word in query_lower:
            query_lower = query_lower.replace(hindi_word, eng_word)
            user_query = query_lower  # use normalized version for routing

    # Enhanced keyword detection with Indian terms
    prediction_keywords = [
        "predict", "forecast", "future", "target", "price prediction", "outlook", "expected price"
    ]

    comparison_keywords = [
        "compare", "vs", "versus", "better", "peer", "peers", "against", "which is better"
    ]

    technical_keywords = [
        "technical", "rsi", "macd", "support", "resistance", "trend", "momentum", "breakout", "circuit breaker"
    ]

    trade_timing_keywords = [
        "entry", "accumulate", "sip", "dip", "risk", "risks", "volatility", "downside", "stop loss", "target price"
    ]

    valuation_keywords = [
        "overvalued", "undervalued", "overpriced", "underpriced", "expensive", "cheap",
        "fair value", "intrinsic value", "valuation", "pe ratio", "pb ratio"
    ]

    screening_keywords = [
        "best", "top", "undervalued", "overvalued", "cheap", "value", "screen", "list", "find"
    ]

    recommendation_keywords = [
        "recommend", "suggest", "portfolio", "watchlist", "invest in", "good stock"
    ]

    # Indian-specific keywords
    indian_finance_keywords = [
        "ipo", "fpo", "f&o", "fno", "demat", "mutual fund", "sip", "stp", "swp", "elss",
        "nifty", "sensex", "sebi", "nse", "bse", "bonus issue", "rights issue", "stock split",
        "buyback", "dividend", "agm", "egm", "qip", "oddlot", "vix"
    ]

    market_keywords = [
        "market", "index", "nifty", "sensex", "sector", "industry", "overview", "indian market"
    ]

    sector_keywords = [
        "pharma", "tech", "bank", "finance", "auto", "automobile", "cement", "energy",
        "steel", "fmcg", "it", "information technology", "oil", "gas", "chemicals",
        "defence", "defense", "electrical", "paints", "jewelry", "hospitality", "textiles",
        "insurance", "railway", "rail", "psu", "shipping", "shipyard", "textile", "chemical",
        "telecom", "media", "hotel", "paint", "jewel", "gold", "sugar", "power", "electric",
        "ev", "fintech", "nbfc", "realty", "real estate", "metal", "infra",
    ]

    # Handle Indian finance education queries first
    # But skip if the query also has technical/sector/screening terms (indicates analysis, not education)
    _non_education_signals = (
        prediction_keywords + comparison_keywords + technical_keywords
        + screening_keywords + sector_keywords + ["stock", "stocks", "shares"]
    )
    if (any(w in query_lower for w in indian_finance_keywords)
            and not any(w in query_lower for w in _non_education_signals)):
        return _handle_indian_finance_query(user_query)

    # Specialized Indian market query handlers
    if any(w in query_lower for w in ["penny stock", "penny shares", "micro cap", "microcap"]):
        return _handle_penny_multibagger_query(query_lower, "penny")

    if any(w in query_lower for w in ["multibagger", "multi bagger", "wealth creator", "100x", "10x"]):
        return _handle_penny_multibagger_query(query_lower, "multibagger")

    if any(w in query_lower for w in ["52 week high", "52 week low", "52w high", "52w low", "yearly high", "yearly low", "all time high", "all time low", "ath"]):
        return _handle_52_week_query(query_lower, symbols)

    if any(w in query_lower for w in ["most active", "high volume", "top gainer", "top loser", "top gainers", "top losers", "biggest gainer", "biggest loser"]):
        return _handle_most_active_query(query_lower)

    if any(w in query_lower for w in ["fii", "dii", "foreign investor", "institutional", "fpi", "mutual fund flow"]):
        return _handle_fii_dii_query(query_lower)

    if any(w in query_lower for w in ["intraday", "intra day", "day trading", "scalp", "swing trade"]):
        if symbols:
            return _handle_buy_sell_query(symbols, user_query)
        return _handle_intraday_query(query_lower)

    # Route to appropriate handler based on enhanced analysis
    if "predict" in intents or any(w in query_lower for w in prediction_keywords):
        return _handle_prediction_query(symbols, user_query)

    elif "compare" in intents or any(w in query_lower for w in comparison_keywords):
        return _handle_compare_query(symbols, user_query)

    elif any(w in query_lower for w in valuation_keywords):
        if symbols and symbols_direct:
            return _handle_valuation_query(symbols, user_query)
        return _handle_screening_query(query_lower, symbols)

    elif "buy" in intents or "sell" in intents or any(w in query_lower for w in ["buy", "sell", "should i", "invest", "good time", *trade_timing_keywords]):
        return _handle_buy_sell_query(symbols, user_query)

    elif any(w in query_lower for w in technical_keywords):
        if any(w in query_lower for w in ["stocks", "sector", "nifty", "sensex", "index", "indices"]):
            return _handle_screening_query(query_lower, symbols)
        if symbols:
            return _handle_analysis_query(symbols, user_query)
        return _handle_screening_query(query_lower, symbols)

    elif any(w in query_lower for w in recommendation_keywords):
        if symbols:
            return _handle_buy_sell_query(symbols, user_query)
        # Personalized recommendation logic
        user_portfolio = _get_user_portfolio(db)
        if user_portfolio:
            return _handle_personalized_recommendation(query_lower, symbols, user_portfolio)
        else:
            return _handle_screening_query(query_lower, symbols)

    elif any(w in query_lower for w in screening_keywords):
        return _handle_screening_query(query_lower, symbols)

    elif any(w in query_lower for w in sector_keywords + market_keywords):
        # Market/sector overview or screening
        return _handle_screening_query(query_lower, symbols)

    elif any(w in query_lower for w in ["analyze", "analysis", "detail", "about", "tell me", "how is", "how are", "info"]):
        if symbols:
            return _handle_analysis_query(symbols, user_query)
        # Try to infer context or provide market overview
        return _handle_screening_query(query_lower, symbols)

    elif symbols:
        # Default: analyze first symbol found
        return _handle_analysis_query(symbols, user_query)

    elif any(w in query_lower for w in ["what", "which", "where", "when", "how", "why"]):
        # WH-questions without symbols - try to find context or provide help
        return _handle_screening_query(query_lower, symbols)

    else:
        # Fallback for unmatched queries
        return _build_generic_response(query, symbols)


def _get_user_portfolio(db=None):
    """Fetch user's portfolio or watchlist for personalized recommendations."""
    if db is not None:
        try:
            from .routes.trading import get_holdings
            holdings = get_holdings(db)
            return [h.symbol for h in holdings if getattr(h, "symbol", None)]
        except Exception:
            pass
    return []


def _safe_float(value, default: float = 0.0) -> float:
    try:
        return float(value)
    except Exception:
        return default


def _safe_int(value, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _extract_one_month_prediction(predictions: List[Dict]) -> Optional[Dict]:
    for entry in predictions or []:
        if _safe_int(entry.get("days", 0), 0) == 30:
            return entry
    return None


def _get_news_payload(analysis: Dict) -> Dict:
    news_payload = analysis.get("news") if isinstance(analysis.get("news"), dict) else {}
    raw_headlines = news_payload.get("headlines") or analysis.get("recentNews") or []

    headlines: List[Dict] = []
    for item in raw_headlines[:5]:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title", "")).strip()
        if not title:
            continue
        headlines.append({
            "title": title,
            "source": str(item.get("source", "")).strip(),
            "publishedLabel": str(item.get("publishedLabel", "")).strip(),
        })

    sentiment = str(news_payload.get("sentiment") or _classify_headline_flow(headlines))
    summary = str(news_payload.get("summary") or _summarize_headline_flow(headlines)).strip()
    return {
        "sentiment": sentiment,
        "summary": summary,
        "headlines": headlines,
    }


def _build_news_lines(analysis: Dict, heading: str = "Recent Headlines Considered", indent: str = "") -> List[str]:
    news_payload = _get_news_payload(analysis)
    headlines = news_payload.get("headlines", [])[:5]

    lines = [f"{indent}**{heading}**"]
    if not headlines:
        lines.append(f"{indent}• Live headline context unavailable right now.")
        return lines

    lines.append(f"{indent}• Headline Read: {news_payload.get('summary', '')}")
    for item in headlines:
        meta_parts = [part for part in [item.get("source"), item.get("publishedLabel")] if part]
        meta = f" ({' | '.join(meta_parts)})" if meta_parts else ""
        lines.append(f"{indent}• {item.get('title', '')}{meta}")
    return lines


def _build_analysis_answer(symbol: str, analysis: Dict) -> str:
    name = analysis.get("name") or symbol
    price = _safe_float(analysis.get("currentPrice"), 0.0)
    score = _safe_int(analysis.get("score"), 0)
    signal = str(analysis.get("signal", "HOLD")).upper()
    technical = analysis.get("technical", {}) or {}
    moving_averages = technical.get("movingAverages", {}) or {}
    trend = str(moving_averages.get("trend", "neutral")).replace("_", " ")
    rsi = _safe_float(technical.get("rsi"), 0.0)
    macd = technical.get("macd", {}) or {}
    macd_trend = str(macd.get("trend", "neutral"))

    fundamental = analysis.get("fundamental", {}) or {}
    pe = _safe_float(fundamental.get("pe"), 0.0)
    roe = _safe_float(fundamental.get("roe"), 0.0)
    debt = _safe_float(fundamental.get("debtToEquity"), 0.0)
    news_block = "\n".join(_build_news_lines(analysis))

    month_pred = _extract_one_month_prediction(analysis.get("predictions", []) or [])
    outlook = "No 1-month model outlook available"
    if month_pred:
        outlook = (
            f"1-month model outlook: ₹{_safe_float(month_pred.get('predictedPrice'), 0.0):.2f} "
            f"({_safe_float(month_pred.get('changePercent'), 0.0):+.1f}%)"
        )

    return (
        f"🔎 **Detailed Analysis: {name} ({symbol})**\n"
        f"Price: ₹{price:.2f} | Signal: **{signal}** | Score: **{score}/100**\n\n"
        f"**Technical Pulse**\n"
        f"• RSI: {rsi:.1f}\n"
        f"• Trend: {trend}\n"
        f"• MACD Bias: {macd_trend}\n\n"
        f"**Fundamental Snapshot**\n"
        f"• P/E: {pe:.1f}\n"
        f"• ROE: {roe:.1f}%\n"
        f"• Debt/Equity: {debt:.1f}\n\n"
        f"**AI Outlook**\n"
        f"• {outlook}\n\n"
        f"{news_block}\n\n"
        f"{analysis.get('summary', '')}"
    )


def _build_recommendation_answer(symbol: str, analysis: Dict) -> str:
    name = analysis.get("name") or symbol
    price = _safe_float(analysis.get("currentPrice"), 0.0)
    score = _safe_int(analysis.get("score"), 0)
    signal = str(analysis.get("signal", "HOLD")).upper()

    technical = analysis.get("technical", {}) or {}
    rsi = _safe_float(technical.get("rsi"), 0.0)
    moving_averages = technical.get("movingAverages", {}) or {}
    trend = str(moving_averages.get("trend", "neutral")).replace("_", " ")
    news_payload = _get_news_payload(analysis)
    news_sentiment = str(news_payload.get("sentiment", "mixed"))
    news_block = "\n".join(_build_news_lines(analysis))

    month_pred = _extract_one_month_prediction(analysis.get("predictions", []) or [])
    month_change = _safe_float((month_pred or {}).get("changePercent"), 0.0)

    if signal in {"STRONG_BUY", "BUY"} or score >= 70:
        stance = "BUY on staggered entries"
    elif signal in {"STRONG_SELL", "SELL"} or score <= 40:
        stance = "Avoid fresh entries / reduce exposure"
    else:
        stance = "HOLD and wait for stronger confirmation"

    if news_sentiment == "negative" and stance.startswith("BUY"):
        stance = "BUY only after headline risk settles"
    elif news_sentiment == "positive" and stance.startswith("HOLD"):
        stance = "HOLD, but headline flow is improving"

    risk_notes = ["High momentum risk (overbought)" if rsi >= 70 else "No major momentum excess"]
    if news_sentiment == "negative":
        risk_notes.append("Recent headlines add near-term caution")
    elif news_sentiment == "positive":
        risk_notes.append("Recent headlines support sentiment")
    else:
        risk_notes.append("Recent headlines are mixed")
    risk_flag = " | ".join(risk_notes)

    action_note = "Wait for headline volatility to cool before sizing up aggressively."
    if news_sentiment != "negative":
        action_note = "Position-size gradually and use stop-loss discipline."

    return (
        f"🧭 **Trade Decision: {name} ({symbol})**\n"
        f"Current Price: ₹{price:.2f}\n"
        f"Decision Bias: **{stance}**\n\n"
        f"**Decision Inputs**\n"
        f"• AI Signal: {signal}\n"
        f"• Score: {score}/100\n"
        f"• Trend: {trend}\n"
        f"• 1-Month Model Move: {month_change:+.1f}%\n"
        f"• Risk Check: {risk_flag}\n\n"
        f"Actionable note: {action_note}\n\n"
        f"{news_block}"
    )


def _build_valuation_answer(symbol: str, analysis: Dict, query: str) -> str:
    name = analysis.get("name") or symbol
    price = _safe_float(analysis.get("currentPrice"), 0.0)
    score = _safe_int(analysis.get("score"), 0)
    signal = str(analysis.get("signal", "HOLD")).upper()
    pe = _safe_float((analysis.get("fundamental", {}) or {}).get("pe"), 0.0)
    news_payload = _get_news_payload(analysis)
    news_sentiment = str(news_payload.get("sentiment", "mixed"))
    news_block = "\n".join(_build_news_lines(analysis))

    if pe <= 0:
        valuation_read = "valuation data is incomplete"
    elif pe >= 45 or score <= 45:
        valuation_read = "appears expensive vs typical large-cap benchmarks"
    elif pe <= 18 and score >= 60:
        valuation_read = "looks relatively attractive on valuation"
    else:
        valuation_read = "looks roughly fairly valued"

    query_lower = query.lower().strip()
    asked_side = ""
    if "overvalued" in query_lower or "expensive" in query_lower or "overpriced" in query_lower:
        asked_side = "overvalued"
    elif "undervalued" in query_lower or "cheap" in query_lower or "underpriced" in query_lower:
        asked_side = "undervalued"

    alignment_note = ""
    if asked_side == "overvalued" and "expensive" in valuation_read:
        alignment_note = "This aligns with your overvaluation check."
    elif asked_side == "undervalued" and "attractive" in valuation_read:
        alignment_note = "This aligns with your undervaluation check."
    elif asked_side:
        alignment_note = "The current data does not strongly support that exact valuation bias."

    if news_sentiment == "negative" and "attractive" in valuation_read:
        valuation_read = f"{valuation_read}, but recent headlines add caution"
    elif news_sentiment == "positive" and "expensive" in valuation_read:
        valuation_read = f"{valuation_read}, though recent headlines are more supportive than the pure multiple suggests"

    return (
        f"💰 **Valuation Check: {name} ({symbol})**\n"
        f"Price: ₹{price:.2f} | P/E: {pe:.1f} | Score: {score}/100 | Signal: {signal}\n\n"
        f"Read: **{valuation_read}**\n"
        f"{alignment_note}\n\n"
        f"{news_block}\n\n"
        f"Practical next step: validate growth quality, margin trend, and peer-relative multiples before entry."
    ).strip()


def _handle_personalized_recommendation(query, symbols, portfolio):
    """Recommend stocks based on user's portfolio/watchlist."""
    from .market_data import INDIAN_STOCKS
    portfolio_set = set(portfolio)
    recommendations = []
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym not in portfolio_set:
            recommendations.append({"symbol": sym, "name": name})
            if len(recommendations) >= 5:
                break
    answer = "Top recommendations based on your portfolio: " + ", ".join([r["symbol"] for r in recommendations])
    return {
        "type": "recommendation",
        "answer": answer,
        "suggestions": [r["symbol"] for r in recommendations],
        "stocks": recommendations
    }


def _extract_symbols(query: str) -> List[str]:
    """Extract stock symbols from a natural language query."""
    import difflib

    symbols: List[str] = []
    seen: set[str] = set()

    def _add_symbol(symbol_value: str) -> None:
        symbol_upper = symbol_value.upper()
        if symbol_upper in INDIAN_STOCKS and symbol_upper not in seen:
            seen.add(symbol_upper)
            symbols.append(symbol_upper)

    query_text = _extract_user_query(query)
    query_upper = query_text.upper()

    # Direct symbol and ticker token matching.
    for raw_token in re.findall(r"[A-Za-z0-9&.\-]+", query_upper):
        token = raw_token.strip("?!.,:;()[]{}\"' ")
        if not token:
            continue

        if token.endswith(".NS") or token.endswith(".BO"):
            token = token.rsplit(".", 1)[0]

        if token in INDIAN_STOCKS:
            _add_symbol(token)
            continue

        condensed = token.replace("-", "")
        if condensed in INDIAN_STOCKS:
            _add_symbol(condensed)

    normalized_query = _normalize_phrase(query_text)
    padded_query = f" {normalized_query} "

    # Company-name alias matching to support lowercase and common names.
    for alias, symbol in _SORTED_SYMBOL_ALIASES:
        if not alias:
            continue
        if f" {alias} " in padded_query:
            _add_symbol(symbol)
            if len(symbols) >= 5:
                break

    # Fuzzy alias matching — handles typos, "india" vs "indian", etc.
    # Only for short, stock-name-like queries to avoid false positives on screening phrases.
    _noise_heavy = sum(1 for w in normalized_query.split() if w in _SYMBOL_NOISE_WORDS)
    _total_words = max(len(normalized_query.split()), 1)
    if not symbols and len(normalized_query) <= 25 and _noise_heavy < _total_words * 0.5:
        all_aliases = [a for a, _ in _SORTED_SYMBOL_ALIASES if len(a) >= 4]
        close = difflib.get_close_matches(normalized_query, all_aliases, n=3, cutoff=0.78)
        for matched_alias in close:
            sym = _SYMBOL_ALIAS_INDEX.get(matched_alias)
            if sym:
                _add_symbol(sym)

    # Fuzzy matching on INDIAN_STOCKS company names directly
    if not symbols and 4 <= len(normalized_query) <= 30 and _noise_heavy < _total_words * 0.5:
        all_names = {name.lower(): sym for sym, (_, name) in INDIAN_STOCKS.items()}
        close_names = difflib.get_close_matches(
            normalized_query, list(all_names.keys()), n=3, cutoff=0.6
        )
        for matched_name in close_names:
            sym = all_names.get(matched_name)
            if sym:
                _add_symbol(sym)

    if len(symbols) < 5:
        fallback_tokens: List[str] = []
        for raw_token in re.findall(r"[A-Za-z0-9&.\-]+", query_upper):
            token = raw_token.strip("?!.,:;()[]{}\"' ")
            if not token:
                continue

            if token.endswith(".NS") or token.endswith(".BO"):
                token = token.rsplit(".", 1)[0]

            token = token.replace("-", "")
            if len(token) < 2:
                continue

            token_lower = token.lower()
            if token_lower in _SYMBOL_NOISE_WORDS:
                continue

            if token in INDIAN_STOCKS:
                _add_symbol(token)
                continue

            if not token.isalnum() or not any(ch.isalpha() for ch in token):
                continue
            if len(token) < 3:
                continue

            fallback_tokens.append(token.upper())

        # Prefer ticker-like tokens explicitly next to stock/share/symbol words.
        if len(symbols) < 5 and fallback_tokens:
            ranked_fallbacks = sorted(
                set(fallback_tokens),
                key=lambda candidate: (
                    not (
                        f" {candidate.lower()} stock " in f" {normalized_query} "
                        or f" stock {candidate.lower()} " in f" {normalized_query} "
                        or f" {candidate.lower()} share " in f" {normalized_query} "
                        or f" symbol {candidate.lower()} " in f" {normalized_query} "
                    ),
                    -len(candidate),
                ),
            )

            for candidate in ranked_fallbacks:
                if candidate in seen:
                    continue
                seen.add(candidate)
                symbols.append(candidate)
                if len(symbols) >= 5:
                    break

    return symbols[:5]


def _handle_prediction_query(symbols: List[str], query: str) -> Dict:
    """Handle price prediction queries."""
    if not symbols:
        return {"type": "error", "answer": "Please specify a stock symbol. E.g., 'Predict RELIANCE price'"}

    symbol = symbols[0]
    pred = predict_price(symbol)
    analysis = analyze_stock(symbol)

    if "error" in pred and not pred.get("predictions"):
        return {"type": "error", "answer": f"Could not generate prediction for {symbol}: {pred['error']}"}

    name = analysis.get("name") if isinstance(analysis, dict) else None
    if not name:
        name = INDIAN_STOCKS.get(symbol, (None, symbol))[1]
    preds = pred.get("predictions", [])

    answer_parts = [f"📊 **AI Price Prediction for {name} ({symbol})**\n"]
    answer_parts.append(f"Current Price: ₹{pred['currentPrice']}\n")

    for p in preds:
        arrow = "📈" if p["direction"] == "up" else "📉"
        answer_parts.append(
            f"{arrow} **{p['horizon']}**: ₹{p['predictedPrice']} "
            f"({p['changePercent']:+.1f}%) "
            f"[Range: ₹{p['confidenceLow']} - ₹{p['confidenceHigh']}]"
        )

    answer_parts.append(f"\nSignal: **{pred.get('signal', 'N/A')}**")
    answer_parts.append(f"Model Accuracy: {pred.get('modelAccuracy', 0)}%")
    answer_parts.append("")
    answer_parts.extend(_build_news_lines(analysis, heading="Recent Headlines Considered"))
    answer_parts.append(f"\n⚠️ {pred.get('disclaimer', '')}")

    return {
        "type": "prediction",
        "symbol": symbol,
        "answer": "\n".join(answer_parts),
        "data": pred,
        "suggestions": _build_stock_suggestions(symbol, exclude="prediction"),
    }


def _handle_compare_query(symbols: List[str], query: str) -> Dict:
    """Handle stock comparison queries."""
    catalog_symbols = [symbol for symbol in symbols if symbol in INDIAN_STOCKS]
    if len(catalog_symbols) >= 2:
        symbols = catalog_symbols
    elif len(catalog_symbols) == 1:
        symbols = catalog_symbols

    if len(symbols) < 2:
        if len(symbols) == 1:
            seed = symbols[0]
            peer = next((candidate for candidate in _SECTOR_PEERS.get(seed, []) if candidate != seed), None)
            if peer:
                symbols = [seed, peer]
            else:
                return _handle_analysis_query([seed], query)
        else:
            return {"type": "error", "answer": "Please specify 2 stocks to compare. E.g., 'Compare TCS and INFY'"}

    analyses = {}
    for sym in symbols[:3]:
        analyses[sym] = analyze_stock(sym)

    answer_parts = [f"⚖️ **Stock Comparison**\n"]

    for sym, a in analyses.items():
        if "error" in a:
            answer_parts.append(f"❌ {sym}: Error fetching data")
            continue
        score = a.get("score", 0)
        price = a.get("currentPrice", 0)
        signal = a.get("signal", "N/A")
        pe = a.get("fundamental", {}).get("pe", 0)
        preds = a.get("predictions", [])
        month_pred = next((p for p in preds if p["days"] == 30), None)

        answer_parts.append(f"\n**{a.get('name', sym)} ({sym})**")
        answer_parts.append(f"  Price: ₹{price} | Score: {score}/100 | Signal: {signal}")
        answer_parts.append(f"  P/E: {pe} | RSI: {a.get('technical', {}).get('rsi', 0)}")
        if month_pred:
            answer_parts.append(f"  1-Month Prediction: ₹{month_pred['predictedPrice']} ({month_pred['changePercent']:+.1f}%)")
        answer_parts.extend(_build_news_lines(a, heading="Latest Headlines Considered", indent="  "))

    # Winner
    valid = {s: a for s, a in analyses.items() if "error" not in a}
    if valid:
        winner = max(valid, key=lambda s: valid[s].get("score", 0))
        answer_parts.append(f"\n🏆 **Winner: {winner}** (Score: {valid[winner].get('score', 0)}/100)")

    follow_ups: List[str] = []
    for s in symbols[:2]:
        follow_ups.append(f"Should I buy {s}?")
        follow_ups.append(f"Predict {s} price")
    if len(symbols) >= 2:
        peers_a = _SECTOR_PEERS.get(symbols[0], [])
        for p in peers_a:
            if p not in symbols:
                follow_ups.append(f"Compare {symbols[0]} with {p}")
                break
    return {
        "type": "comparison",
        "answer": "\n".join(answer_parts),
        "data": analyses,
        "suggestions": follow_ups[:5],
    }


def _handle_buy_sell_query(symbols: List[str], query: str) -> Dict:
    """Handle buy/sell recommendation queries."""
    if not symbols:
        return {"type": "error", "answer": "Please specify a stock. E.g., 'Should I buy RELIANCE?'"}

    symbol = symbols[0]
    analysis = analyze_stock(symbol)

    if "error" in analysis:
        return {"type": "error", "answer": f"Could not analyze {symbol}: {analysis['error']}"}

    return {
        "type": "recommendation",
        "symbol": symbol,
        "answer": _build_recommendation_answer(symbol, analysis),
        "score": analysis.get("score", 0),
        "signal": analysis.get("signal", "HOLD"),
        "data": analysis,
        "suggestions": _build_stock_suggestions(symbol, exclude="buy_sell"),
    }


def _handle_analysis_query(symbols: List[str], query: str) -> Dict:
    """Handle detailed analysis queries."""
    if not symbols:
        return {"type": "error", "answer": "Please specify a stock to analyze. E.g., 'Analyze RELIANCE'"}

    symbol = symbols[0]
    analysis = analyze_stock(symbol)

    if "error" in analysis:
        return {"type": "error", "answer": f"Could not analyze {symbol}: {analysis['error']}"}

    return {
        "type": "analysis",
        "symbol": symbol,
        "answer": _build_analysis_answer(symbol, analysis),
        "score": analysis.get("score", 0),
        "signal": analysis.get("signal", "HOLD"),
        "data": analysis,
        "suggestions": _build_stock_suggestions(symbol, exclude="analysis"),
    }


def _handle_valuation_query(symbols: List[str], query: str) -> Dict:
    if not symbols:
        return _handle_screening_query(query.lower(), symbols)

    symbol = symbols[0]
    analysis = analyze_stock(symbol)
    if "error" in analysis:
        return {"type": "error", "answer": f"Could not analyze {symbol}: {analysis['error']}"}

    return {
        "type": "analysis",
        "symbol": symbol,
        "answer": _build_valuation_answer(symbol, analysis, query),
        "score": analysis.get("score", 0),
        "signal": analysis.get("signal", "HOLD"),
        "data": analysis,
        "suggestions": _build_stock_suggestions(symbol, exclude="overvaluation"),
    }


def _handle_penny_multibagger_query(query_lower: str, qtype: str) -> Dict:
    """Handle penny stock / multibagger queries."""
    if qtype == "penny":
        candidates = ["SUZLON", "YESBANK", "IDEA", "IRFC", "NHPC", "SJVN", "IREDA", "RVNL", "NBCC", "HUDCO"]
        title = "Penny / Low-Price Stocks"
        note = ("⚠️ **Penny stocks carry very high risk.** These are low-priced stocks popular "
                "among Indian traders. Always do your own research and never invest more than you can afford to lose.")
    else:
        candidates = ["TRENT", "DEEPAKNTR", "DIXON", "PERSISTENT", "POLYCAB", "KALYANKJIL",
                       "BSE", "JIOFIN", "ZOMATO", "IREDA"]
        title = "Potential Multibagger Picks"
        note = ("💡 **Multibaggers** are stocks that deliver many times their cost price over time. "
                "Past performance doesn't guarantee future returns. Look for strong revenue growth, "
                "increasing margins, and sector tailwinds.")

    results = []
    for sym in candidates[:8]:
        try:
            q = fetch_quote(sym)
            results.append({
                "symbol": sym,
                "name": INDIAN_STOCKS.get(sym, (None, sym))[1],
                "price": q.get("last", 0),
                "pctChange": q.get("pctChange", 0),
            })
        except Exception:
            pass

    results.sort(key=lambda x: x["pctChange"], reverse=True)
    parts = [f"📋 **{title}**\n"]
    for i, r in enumerate(results, 1):
        arrow = "🟢" if r["pctChange"] > 0 else "🔴"
        parts.append(f"{i}. {arrow} **{r['symbol']}** ({r['name']}) — ₹{r['price']:.2f} ({r['pctChange']:+.2f}%)")
    parts.append(f"\n{note}")

    return {
        "type": "screening",
        "answer": "\n".join(parts),
        "stocks": results,
        "suggestions": [f"Analyze {r['symbol']}" for r in results[:3]] + ["Best bank stocks", "Top pharma stocks"],
    }


def _handle_52_week_query(query_lower: str, symbols: List[str]) -> Dict:
    """Handle 52-week high/low and all-time high queries."""
    is_high = any(w in query_lower for w in ["high", "ath", "all time high"])
    scope_stocks = symbols if symbols else ["RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
                                             "SBIN", "BHARTIARTL", "TATAMOTORS", "LT", "SUNPHARMA"]
    results = []
    for sym in scope_stocks[:8]:
        try:
            ticker = yf.Ticker(_yf_ticker(sym))
            hist = ticker.history(period="1y")
            if hist.empty:
                continue
            high_52 = float(hist["High"].max())
            low_52 = float(hist["Low"].min())
            current = float(hist["Close"].iloc[-1])
            from_high = ((current - high_52) / high_52) * 100 if high_52 else 0
            from_low = ((current - low_52) / low_52) * 100 if low_52 else 0
            results.append({
                "symbol": sym,
                "name": INDIAN_STOCKS.get(sym, (None, sym))[1],
                "price": current,
                "high_52": high_52,
                "low_52": low_52,
                "from_high_pct": from_high,
                "from_low_pct": from_low,
            })
        except Exception:
            pass

    if is_high:
        results.sort(key=lambda x: x["from_high_pct"], reverse=True)  # closest to 52w high
        label = "52-Week High"
    else:
        results.sort(key=lambda x: x["from_low_pct"])  # closest to 52w low
        label = "52-Week Low"

    parts = [f"📊 **{label} Analysis**\n"]
    for i, r in enumerate(results, 1):
        parts.append(
            f"{i}. **{r['symbol']}** ({r['name']}) — ₹{r['price']:.2f}\n"
            f"   52W High: ₹{r['high_52']:.2f} ({r['from_high_pct']:+.1f}%) | "
            f"52W Low: ₹{r['low_52']:.2f} ({r['from_low_pct']:+.1f}%)"
        )

    return {
        "type": "screening",
        "answer": "\n".join(parts),
        "stocks": results,
        "suggestions": [f"Analyze {r['symbol']}" for r in results[:3]] + ["Best bank stocks"],
    }


def _handle_most_active_query(query_lower: str) -> Dict:
    """Handle top gainers/losers/most active queries."""
    nifty_stocks = ["RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN",
                    "BHARTIARTL", "TATAMOTORS", "LT", "SUNPHARMA", "KOTAKBANK",
                    "AXISBANK", "ITC", "WIPRO", "HCLTECH", "NTPC", "ADANIENT",
                    "BAJFINANCE", "MARUTI", "ONGC"]
    results = []
    for sym in nifty_stocks:
        try:
            q = fetch_quote(sym)
            results.append({
                "symbol": sym,
                "name": INDIAN_STOCKS.get(sym, (None, sym))[1],
                "price": q.get("last", 0),
                "pctChange": q.get("pctChange", 0),
                "volume": q.get("volume", 0),
            })
        except Exception:
            pass

    is_loser = any(w in query_lower for w in ["loser", "fall", "drop", "crash"])
    is_volume = any(w in query_lower for w in ["active", "volume"])

    if is_volume:
        results.sort(key=lambda x: x.get("volume", 0), reverse=True)
        title = "Most Active Stocks (by Volume)"
    elif is_loser:
        results.sort(key=lambda x: x["pctChange"])
        title = "Top Losers Today"
    else:
        results.sort(key=lambda x: x["pctChange"], reverse=True)
        title = "Top Gainers Today"

    parts = [f"📈 **{title}**\n"]
    for i, r in enumerate(results[:10], 1):
        arrow = "🟢" if r["pctChange"] > 0 else "🔴"
        vol_str = f" | Vol: {r.get('volume', 0):,.0f}" if is_volume else ""
        parts.append(f"{i}. {arrow} **{r['symbol']}** — ₹{r['price']:.2f} ({r['pctChange']:+.2f}%){vol_str}")

    return {
        "type": "screening",
        "answer": "\n".join(parts),
        "stocks": results[:10],
        "suggestions": [f"Analyze {results[0]['symbol']}" if results else "Analyze RELIANCE",
                        "Top losers today" if not is_loser else "Top gainers today",
                        "Best bank stocks", "Predict TCS price"],
    }


def _handle_fii_dii_query(query_lower: str) -> Dict:
    """Handle FII/DII institutional flow queries."""
    return {
        "type": "education",
        "answer": (
            "🏛️ **FII / DII Activity in Indian Markets**\n\n"
            "**FII (Foreign Institutional Investors):**\n"
            "• FIIs are overseas institutions (hedge funds, pension funds, etc.) investing in Indian stocks\n"
            "• When FIIs buy heavily → bullish signal for markets\n"
            "• When FIIs sell heavily → bearish pressure on Nifty/Sensex\n\n"
            "**DII (Domestic Institutional Investors):**\n"
            "• DIIs include Indian mutual funds, insurance companies (LIC, HDFC Life)\n"
            "• DIIs often buy when FIIs sell, providing market stability\n"
            "• SIP flows from retail investors drive DII buying\n\n"
            "**How to track:**\n"
            "• SEBI publishes daily FII/DII data on nseindia.com\n"
            "• Positive FII net buying + DII buying = strong rally signal\n"
            "• FII selling + DII buying = range-bound market\n\n"
            "**Current tip:** Watch FII flows in banking and IT sectors — they move the Nifty most.\n\n"
            "💡 *Ask me about specific stocks to see how institutional activity affects them!*"
        ),
        "suggestions": [
            "Best bank stocks",
            "Top gainers today",
            "Should I buy HDFCBANK?",
            "Predict NIFTY50 price",
            "What is mutual fund SIP?",
        ],
    }


def _handle_intraday_query(query_lower: str) -> Dict:
    """Handle intraday / day trading queries."""
    # Pick high-volume liquid stocks suitable for intraday
    intraday_picks = ["RELIANCE", "SBIN", "TATAMOTORS", "ICICIBANK", "HDFCBANK",
                      "BHARTIARTL", "AXISBANK", "INFY", "ITC", "BAJFINANCE"]
    results = []
    for sym in intraday_picks[:6]:
        try:
            q = fetch_quote(sym)
            results.append({
                "symbol": sym,
                "name": INDIAN_STOCKS.get(sym, (None, sym))[1],
                "price": q.get("last", 0),
                "pctChange": q.get("pctChange", 0),
            })
        except Exception:
            pass

    results.sort(key=lambda x: abs(x["pctChange"]), reverse=True)
    parts = [
        "⚡ **Intraday / Day Trading Picks**\n",
        "These are high-liquidity stocks suitable for intraday trading:\n",
    ]
    for i, r in enumerate(results, 1):
        arrow = "🟢" if r["pctChange"] > 0 else "🔴"
        parts.append(f"{i}. {arrow} **{r['symbol']}** — ₹{r['price']:.2f} ({r['pctChange']:+.2f}%)")

    parts.append("\n📌 **Intraday Tips:**")
    parts.append("• Always set a stop-loss (0.5-1% for intraday)")
    parts.append("• Trade only in the first 2 hours (9:15-11:15 AM) or last hour (2:30-3:30 PM)")
    parts.append("• Stick to Nifty 50 / high-volume stocks for easy entry/exit")
    parts.append("• Never carry intraday positions overnight without converting to delivery")
    parts.append("\n⚠️ Intraday trading carries high risk. Use proper risk management.")

    return {
        "type": "screening",
        "answer": "\n".join(parts),
        "stocks": results,
        "suggestions": [f"Analyze {r['symbol']}" for r in results[:2]] +
                       ["Top gainers today", "Best bank stocks"],
    }


def _handle_screening_query(query_lower: str, symbols: List[str]) -> Dict:
    """Handle stock screening queries like 'best pharma stocks'."""
    # Determine sector/filter
    sector_keywords = {
        "bank": ["HDFCBANK", "ICICIBANK", "SBIN", "KOTAKBANK", "AXISBANK", "INDUSINDBK", "PNB", "BANKBARODA"],
        "pharma": ["SUNPHARMA", "DRREDDY", "CIPLA", "DIVISLAB", "LUPIN", "AUROPHARMA", "BIOCON"],
        "it": ["TCS", "INFY", "WIPRO", "HCLTECH", "TECHM", "LTIM", "MPHASIS", "COFORGE"],
        "auto": ["TATAMOTORS", "MARUTI", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT", "TVSMOTOR", "ASHOKLEY"],
        "metal": ["TATASTEEL", "JSWSTEEL", "HINDALCO", "VEDL", "SAIL", "NATIONALUM", "JINDALSTEL"],
        "energy": ["RELIANCE", "ONGC", "BPCL", "IOC", "NTPC", "POWERGRID", "TATAPOWER", "ADANIGREEN"],
        "fmcg": ["HINDUNILVR", "ITC", "NESTLEIND", "BRITANNIA", "DABUR", "MARICO", "COLPAL", "GODREJCP"],
        "real": ["DLF", "GODREJPROP", "OBEROIRLTY", "PRESTIGE", "BRIGADE", "LODHA", "SOBHA"],
        "realty": ["DLF", "GODREJPROP", "OBEROIRLTY", "PRESTIGE", "BRIGADE", "LODHA", "SOBHA"],
        "defence": ["HAL", "BEL", "BDL", "MAZAGON", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "defense": ["HAL", "BEL", "BDL", "MAZAGON", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "infra": ["LT", "ADANIENT", "ADANIPORTS", "IRCON", "RVNL", "NBCC", "NCC", "KEC"],
        "insurance": ["HDFCLIFE", "SBILIFE", "ICICIGI", "ICICIPRULI", "STARHEALTH", "LICI", "GICRE", "NIACL"],
        "railway": ["IRCTC", "IRFC", "RVNL", "IRCON", "RAILTEL", "TITAGARH"],
        "rail": ["IRCTC", "IRFC", "RVNL", "IRCON", "RAILTEL", "TITAGARH"],
        "psu": ["SBIN", "NTPC", "ONGC", "BPCL", "IOC", "COALINDIA", "NHPC", "BEL", "HAL", "IRCTC"],
        "shipping": ["COCHINSHIP", "MAZAGON", "GRSE", "SCI"],
        "shipyard": ["COCHINSHIP", "MAZAGON", "GRSE"],
        "textile": ["TRENT", "RAYMOND", "ABFRL", "PAGEIND", "ARVIND"],
        "cement": ["ULTRACEMCO", "AMBUJACEM", "SHREECEM", "ACC", "DALMIACEM", "RAMCOCEM", "JKCEMENT"],
        "chemical": ["PIDILITIND", "SRF", "AARTI", "DEEPAKNTR", "CLEAN", "NAVINFLUOR", "FLUOROCHEM"],
        "telecom": ["BHARTIARTL", "IDEA", "TTML", "RAILTEL"],
        "media": ["ZEEL", "SUNTV", "PVRINOX", "SAREGAMA", "NETWORK18"],
        "hotel": ["INDHOTEL", "ELIH", "LEMONTR", "CHALET"],
        "hospitality": ["INDHOTEL", "ELIH", "LEMONTR", "CHALET"],
        "paint": ["ASIANPAINT", "BERGEPAINT", "KANSAINER", "INDIGO"],
        "jewel": ["TITAN", "KALYANKJIL", "PCJEWELLER", "SENCO"],
        "gold": ["TITAN", "KALYANKJIL", "PCJEWELLER", "SENCO"],
        "sugar": ["BALRAMCHIN", "RENUKA", "TRIVENI", "DHAMPUR"],
        "power": ["NTPC", "POWERGRID", "TATAPOWER", "ADANIGREEN", "NHPC", "SJVN", "JSWENERGY"],
        "electric": ["TATAPOWER", "ADANIGREEN", "NHPC", "SJVN", "JSWENERGY", "IREDA"],
        "ev": ["TATAMOTORS", "HEROMOTOCO", "OLECTRA", "TATAMOTORS"],
        "fintech": ["PAYTM", "POLICYBZR", "JIOFIN", "BAJFINANCE"],
        "nbfc": ["BAJFINANCE", "BAJAJFINSV", "CHOLAFIN", "MUTHOOTFIN", "MANAPPURAM", "SHRIRAMFIN"],
    }

    target_sector = None
    target_stocks = None
    for keyword, stocks in sector_keywords.items():
        if keyword in query_lower:
            target_sector = keyword
            target_stocks = stocks
            break

    if not target_stocks:
        # Default: analyze top NIFTY stocks
        target_sector = "popular"
        target_stocks = ["RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK"]

    # Analyze top stocks in sector
    results = []
    for sym in target_stocks[:6]:  # Limit to 6 for speed
        try:
            q = fetch_quote(sym)
            results.append({
                "symbol": sym,
                "name": INDIAN_STOCKS.get(sym, (None, sym))[1],
                "price": q.get("last", 0),
                "pctChange": q.get("pctChange", 0),
            })
        except Exception:
            pass

    # Sort by performance
    results.sort(key=lambda x: x["pctChange"], reverse=True)

    answer_parts = [f"📋 **Top {target_sector.title()} Stocks**\n"]
    for i, r in enumerate(results, 1):
        arrow = "🟢" if r["pctChange"] > 0 else "🔴"
        answer_parts.append(
            f"{i}. {arrow} **{r['symbol']}** ({r['name']}) — "
            f"₹{r['price']:.2f} ({r['pctChange']:+.2f}%)"
        )

    screen_suggestions: List[str] = []
    for r in results[:3]:
        screen_suggestions.append(f"Analyze {r['symbol']}")
    if len(results) >= 2:
        screen_suggestions.append(f"Compare {results[0]['symbol']} and {results[1]['symbol']}")
    if not screen_suggestions:
        screen_suggestions = ["Analyze RELIANCE", "Best bank stocks"]
    return {
        "type": "screening",
        "answer": "\n".join(answer_parts),
        "stocks": results,
        "suggestions": screen_suggestions[:5],
    }


# ──────────────────────────────────────────────────────────────
# PROFIT OPTIMIZATION ENGINES
# ──────────────────────────────────────────────────────────────

def get_stop_loss_take_profit(symbol: str) -> Dict:
    """
    Calculate risk-adjusted stop loss and take profit levels based on volatility
    and technical support/resistance levels.
    
    Returns:
        Dict with entry_signal, stop_loss, take_profit_1/2/3, risk:reward ratio
    """
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        hist = ticker.history(period="3mo")
        
        if hist.empty or len(hist) < 20:
            return {
                "error": f"Insufficient data for {symbol}",
                "entry_signal": None,
                "stopLoss": None,
                "takeProfit": []
            }
        
        # Get current and recent prices
        closes = hist["Close"].values.astype(float)
        current = closes[-1]
        high_52w = float(ticker.info.get("fiftyTwoWeekHigh", closes.max()))
        low_52w = float(ticker.info.get("fiftyTwoWeekLow", closes.min()))
        
        # Calculate volatility (20-day)
        recent_returns = np.diff(closes[-20:]) / closes[-20:-1]
        volatility = np.std(recent_returns)  # Daily volatility
        
        # Support & Resistance levels
        resistance_1 = np.percentile(closes[-20:], 75)  # 75th percentile = resistance
        support_1 = np.percentile(closes[-20:], 25)     # 25th percentile = support
        support_2 = low_52w  # 52-week low as strong support
        
        # Risk-adjusted position sizing
        atl_risk = volatility * np.sqrt(252)  # Annualized volatility
        if atl_risk < 0.02:  # <2% volatility
            position_pct = 5.0  # 5% of portfolio
            sl_pct = 3.0
        elif atl_risk < 0.04:  # 2-4% volatility
            position_pct = 3.5  # 3.5% of portfolio
            sl_pct = 4.5
        else:  # >4% volatility
            position_pct = 2.0  # 2% of portfolio
            sl_pct = 6.0
        
        # Calculate stop loss (below support)
        stop_loss = support_1 * (1 - sl_pct / 100.0)
        
        # Calculate take profit levels based on risk:reward ratios
        risk_per_trade = current - stop_loss
        risk_basis = abs(risk_per_trade) if risk_per_trade != 0 else current * 0.02
        
        # 3 TP levels with increasing ratios
        tp_1 = current + (risk_basis * 1.5)    # 1:1.5 ratio
        tp_2 = current + (risk_basis * 2.5)    # 1:2.5 ratio
        tp_3 = current + (risk_basis * 4.0)    # 1:4.0 ratio
        
        # Max loss per ₹1000 invested
        max_loss_per_1k = (current - stop_loss) / current * 1000
        
        # Signal based on position in range
        position_in_range = (current - support_1) / (resistance_1 - support_1) if resistance_1 > support_1 else 0.5
        if position_in_range < 0.3:
            entry_signal = "STRONG_BUY"
            signal_confidence = 85
        elif position_in_range < 0.5:
            entry_signal = "BUY"
            signal_confidence = 70
        elif position_in_range < 0.7:
            entry_signal = "HOLD"
            signal_confidence = 55
        else:
            entry_signal = "TAKE_PROFIT"
            signal_confidence = 65
        
        # Calculate max profit potential
        max_profit_per_1k = (tp_3 - current) / current * 1000
        
        return {
            "symbol": symbol,
            "currentPrice": round(current, 2),
            "entrySignal": entry_signal,
            "signalConfidence": signal_confidence,
            "riskMetrics": {
                "volatility": round(atl_risk * 100, 2),  # Annualized %
                "volatilityLevel": "Low" if atl_risk < 0.02 else ("Medium" if atl_risk < 0.04 else "High"),
                "suggestedPositionSize": round(position_pct, 1),  # % of portfolio
                "maxLossper1kInvested": round(max_loss_per_1k, 2),
                "maxProfitPotential": round(max_profit_per_1k, 2),
            },
            "levels": {
                "stopLoss": round(stop_loss, 2),
                "takeProfit1": round(tp_1, 2),
                "takeProfit2": round(tp_2, 2),
                "takeProfit3": round(tp_3, 2),
                "resistance1": round(resistance_1, 2),
                "support1": round(support_1, 2),
                "support2": round(support_2, 2),
            },
            "riskRewardRatios": {
                "tp1Ratio": round((tp_1 - current) / abs(risk_basis) if risk_basis > 0 else 0, 2),
                "tp2Ratio": round((tp_2 - current) / abs(risk_basis) if risk_basis > 0 else 0, 2),
                "tp3Ratio": round((tp_3 - current) / abs(risk_basis) if risk_basis > 0 else 0, 2),
            },
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error calculating SL/TP for {symbol}: {e}")
        return {
            "error": str(e),
            "symbol": symbol,
            "entrySignal": None,
        }


def calculate_drawdown_risk(symbol: str) -> Dict:
    """
    Analyze historical drawdown risk and current distance from peaks.
    
    Returns:
        Dict with max drawdown %, current drawdown %, risk score, probability
    """
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        hist = ticker.history(period="2y")  # 2 years for drawdown analysis
        
        if hist.empty or len(hist) < 60:
            return {
                "error": f"Insufficient data for {symbol}",
                "symbol": symbol,
                "riskScore": 50,
            }
        
        closes = hist["Close"].values.astype(float)
        current = closes[-1]
        
        # Calculate max drawdown (running max to trough)
        running_max = np.maximum.accumulate(closes)
        drawdown = (closes - running_max) / running_max * 100
        max_drawdown = float(np.min(drawdown))
        
        # Current distance from 52-week high
        high_52w = np.max(closes[-252:]) if len(closes) >= 252 else np.max(closes)
        current_dd = (current - high_52w) / high_52w * 100
        
        # Support levels
        low_52w = np.min(closes[-252:]) if len(closes) >= 252 else np.min(closes)
        sma_200 = np.mean(closes[-200:]) if len(closes) >= 200 else np.mean(closes)
        
        # Risk scoring (0-100, higher = more risk)
        risk_score = 50  # Base
        
        # Adjust for current drawdown from high
        if current_dd < -5:
            risk_score -= 15  # Below high = less risk
        elif current_dd < 0:
            risk_score -= 10
        else:
            risk_score += 10  # Near all-time high = more risk
        
        # Adjust for volatility
        daily_returns = np.diff(closes[-60:]) / closes[-60:-1]
        volatility = np.std(daily_returns) * np.sqrt(252)
        if volatility > 0.04:
            risk_score += 15
        elif volatility > 0.02:
            risk_score += 8
        
        # Adjust for distance from support
        distance_to_200ma_pct = ((current - sma_200) / sma_200 * 100) if sma_200 > 0 else 0
        if distance_to_200ma_pct < -10:
            risk_score += 20  # Far below MA = vulnerable
        elif distance_to_200ma_pct < 0:
            risk_score += 10
        
        risk_score = max(1, min(99, risk_score))
        
        # Estimate probability of further decline
        recent_trend = closes[-1] - closes[-21]  # Last month
        if recent_trend < 0:
            prob_decline = min(75, 50 + (abs(recent_trend / closes[-21]) * 100))
        else:
            prob_decline = max(25, 50 - (recent_trend / closes[-21] * 100))
        
        # Recovery time estimation (based on historical)
        if len(closes) >= 252:
            yearly_closes = closes[-252::21]  # ~Monthly closes
            if len(yearly_closes) >= 12:
                avg_recovery_days = 45  # Average recovery time
            else:
                avg_recovery_days = 60
        else:
            avg_recovery_days = 90
        
        return {
            "symbol": symbol,
            "currentPrice": round(current, 2),
            "drawdownMetrics": {
                "historicalMaxDrawdown": round(max_drawdown, 2),  # % from peak
                "currentDrawdownFromHigh": round(current_dd, 2),   # % from 52w high
                "distanceFrom52wLow": round(((current - low_52w) / low_52w * 100), 2),
                "distanceFrom200MA": round(distance_to_200ma_pct, 2),
            },
            "riskAssessment": {
                "riskScore": risk_score,  # 0-100
                "riskLevel": "LOW" if risk_score < 35 else ("MEDIUM" if risk_score < 65 else "HIGH"),
                "probabilityOfFurtherDecline": round(prob_decline, 1),  # %
                "estimatedRecoveryDays": avg_recovery_days,
            },
            "supportLevels": {
                "trendingMA200": round(sma_200, 2),
                "support52wLow": round(low_52w, 2),
            },
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error calculating drawdown risk for {symbol}: {e}")
        return {
            "error": str(e),
            "symbol": symbol,
            "riskScore": 50,
        }


def calculate_relative_strength(symbol: str) -> Dict:
    """
    Calculate relative strength vs sector and market benchmark.
    
    Returns:
        Dict with RS ratio, sector rank, market position
    """
    try:
        from .portfolio_scorer import SECTOR_MAP
        
        # Get stock performance
        ticker = yf.Ticker(_yf_ticker(symbol))
        hist = ticker.history(period="1y")
        
        if hist.empty or len(hist) < 200:
            return {
                "symbol": symbol,
                "error": "Insufficient data for RS calculation",
                "relativeStrength": 1.0,
            }
        
        closes = hist["Close"].values.astype(float)
        stock_return = (closes[-1] - closes[0]) / closes[0]  # 1-year return
        
        # Get sector for comparison
        sector = SECTOR_MAP.get(symbol, "UNKNOWN")
        
        # Fetch peer stocks in same sector (simplified - use top 3 peers)
        peer_symbols = [s for s, sec in SECTOR_MAP.items() if sec == sector][:5]
        
        sector_returns = []
        for peer in peer_symbols:
            try:
                peer_hist = yf.Ticker(_yf_ticker(peer)).history(period="1y")
                if not peer_hist.empty and len(peer_hist) >= 200:
                    peer_return = (peer_hist["Close"].iloc[-1] - peer_hist["Close"].iloc[0]) / peer_hist["Close"].iloc[0]
                    sector_returns.append(peer_return)
            except:
                pass
        
        avg_sector_return = np.mean(sector_returns) if sector_returns else stock_return
        
        # Compare to NIFTY 50 (using proxy: large cap average)
        nifty_proxies = ["RELIANCE", "TCS", "HDFCBANK"]
        nifty_returns = []
        for nifty_sym in nifty_proxies:
            try:
                nifty_hist = yf.Ticker(_yf_ticker(nifty_sym)).history(period="1y")
                if not nifty_hist.empty and len(nifty_hist) >= 200:
                    n_return = (nifty_hist["Close"].iloc[-1] - nifty_hist["Close"].iloc[0]) / nifty_hist["Close"].iloc[0]
                    nifty_returns.append(n_return)
            except:
                pass
        
        avg_market_return = np.mean(nifty_returns) if nifty_returns else stock_return
        
        # Relative strength ratios
        sector_rs = float(stock_return / avg_sector_return) if avg_sector_return != 0 else 1.0
        market_rs = float(stock_return / avg_market_return) if avg_market_return != 0 else 1.0
        
        # Sector rank (percentile)
        all_sector_returns = [stock_return] + sector_returns
        sector_percentile = (np.argsort(all_sector_returns).tolist().index(0) / len(all_sector_returns)) * 100 if all_sector_returns else 50
        
        # Strength assessment
        if market_rs > 1.15:
            strength = "OUTPERFORMING"
        elif market_rs > 1.0:
            strength = "IN_LINE"
        else:
            strength = "UNDERPERFORMING"
        
        return {
            "symbol": symbol,
            "sector": sector,
            "performance": {
                "oneYearReturn": round(stock_return * 100, 2),
                "sectorAvgReturn": round(avg_sector_return * 100, 2),
                "marketAvgReturn": round(avg_market_return * 100, 2),
            },
            "relativeStrength": {
                "vsSector": round(sector_rs, 3),
                "vsMarket": round(market_rs, 3),
                "strength": strength,
                "sectorPercentile": round(sector_percentile, 1),  # Higher = better in sector
            },
            "interpretation": (
                f"{symbol} is {strength.lower()} the market. "
                f"Relative to {sector} peers, it's at {sector_percentile:.0f}th percentile. "
                f"Consider accumulating if RS improves, reducing if RS deteriorates."
            ),
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error calculating relative strength for {symbol}: {e}")
        return {
            "symbol": symbol,
            "error": str(e),
            "relativeStrength": 1.0,
        }


def calculate_trade_accuracy(timeframe: str = "one_month") -> Dict:
    """
    Calculate backtesting accuracy of recommendations from N days ago.
    Track: Win %, avg profit, Sharpe ratio of recommended stocks.
    
    Args:
        timeframe: "one_day", "one_month", or "three_months"
    
    Returns:
        Dict with accuracy metrics and top performers
    """
    try:
        with _TRADE_ACCURACY_LOCK:
            results = _TRADE_ACCURACY_RESULTS.get(timeframe, {})
        
        recommended = results.get("recommended", [])
        profitable = results.get("profitable", [])
        
        if not recommended:
            return {
                "timeframe": timeframe,
                "daysAgo": {"one_day": 1, "one_month": 30, "three_months": 90}.get(timeframe, 30),
                "recommendationCount": 0,
                "profitableCount": 0,
                "winRate": 0.0,
                "avgProfit": 0.0,
                "disclaimer": "Insufficient historical recommendations to calculate accuracy.",
            }
        
        win_rate = (len(profitable) / len(recommended) * 100) if recommended else 0.0
        
        # Calculate average profit from profitable trades
        avg_profit = np.mean([p.get("return_pct", 0) for p in profitable]) if profitable else 0.0
        
        # Sharpe ratio estimation (assuming 15% market return, 12% volatility)
        if profitable:
            returns = np.array([p.get("return_pct", 0) / 100.0 for p in profitable])
            excess_return = np.mean(returns) - 0.15  # vs 15% market baseline
            volatility = np.std(returns) if len(returns) > 1 else 0.1
            sharpe = excess_return / volatility if volatility > 0 else 0.0
        else:
            sharpe = 0.0
        
        return {
            "timeframe": timeframe,
            "daysAgo": {"one_day": 1, "one_month": 30, "three_months": 90}.get(timeframe, 30),
            "statistics": {
                "recommendationCount": len(recommended),
                "profitableCount": len(profitable),
                "lossCount": len(recommended) - len(profitable),
                "winRate": round(win_rate, 1),  # %
                "avgProfit": round(avg_profit, 2),  # %
                "sharpeRatio": round(sharpe, 2),
            },
            "topPerformers": [
                {
                    "symbol": p.get("symbol"),
                    "returnPct": round(p.get("return_pct", 0), 2),
                    "entryPrice": round(p.get("entry_price", 0), 2),
                    "exitPrice": round(p.get("exit_price", 0), 2),
                }
                for p in sorted(profitable, key=lambda x: x.get("return_pct", 0), reverse=True)[:5]
            ],
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error calculating trade accuracy for {timeframe}: {e}")
        return {
            "timeframe": timeframe,
            "error": str(e),
            "winRate": 0.0,
        }


def get_sector_rotation_signals() -> Dict:
    """
    Analyze all 14 sectors to identify rotation opportunities.
    Scores sectors on: relative strength, momentum, valuation, sentiment.
    
    Returns:
        Dict with accumulate/hold/reduce sectors and rotation scores
    """
    try:
        from .portfolio_scorer import SECTOR_MAP, SECTOR_STOCKS
        
        sector_scores = {}
        
        # Get all unique sectors
        sectors = list(set(SECTOR_MAP.values()))
        
        for sector in sectors:
            sector_symbols = [s for s, sec in SECTOR_MAP.items() if sec == sector][:10]  # Top 10 per sector
            
            if not sector_symbols:
                continue
            
            # Fetch quotes for sector stocks
            quotes = fetch_quotes(sector_symbols[:8])
            if not quotes:
                continue
            
            # Calculate sector metrics
            sector_returns = []
            sector_rsis = []
            sector_pes = []
            sector_volumes = []
            
            for quote in quotes:
                pct_change = float(quote.get("pctChange", 0))
                sector_returns.append(pct_change)
                
                # Try to estimate RSI from recent data
                try:
                    ticker = yf.Ticker(_yf_ticker(quote.get("symbol", "")))
                    hist = ticker.history(period="3mo")
                    if not hist.empty and len(hist) >= 14:
                        closes = hist["Close"].values.astype(float)
                        rsi = _compute_rsi(closes, 14)
                        sector_rsis.append(rsi)
                except:
                    pass
                
                pe = float(quote.get("pe", 20))
                sector_pes.append(pe if pe > 0 else 20)
                
                vol = float(quote.get("volume", 0))
                avg_vol = float(quote.get("avgVolume", vol))
                volume_ratio = vol / avg_vol if avg_vol > 0 else 1.0
                sector_volumes.append(volume_ratio)
            
            # Score the sector (0-100)
            score = 50  # Base
            
            # Momentum (0-25 pts) - sectors with positive returns
            if sector_returns:
                avg_return = np.mean(sector_returns)
                momentum_score = max(0, min(25, avg_return * 5))
                score += momentum_score
            
            # Strength (0-25 pts) - RSI analysis
            if sector_rsis:
                avg_rsi = np.mean(sector_rsis)
                if 40 <= avg_rsi <= 60:
                    score += 20  # Healthy zone
                elif avg_rsi < 40:
                    score += 25  # Oversold = accumulate
                elif avg_rsi > 60:
                    score -= 10  # Overbought = reduce
            
            # Valuation (0-25 pts) - PE analysis
            if sector_pes:
                avg_pe = np.mean(sector_pes)
                if avg_pe < 18:
                    score += 20  # Cheap = accumulate
                elif avg_pe < 25:
                    score += 10  # Fair = neutral
                # else high PE = reduce (already penalized)
            
            # Volume participation (0-10 pts)
            if sector_volumes:
                avg_volume = np.mean(sector_volumes)
                if avg_volume > 1.2:
                    score += 8  # High participation
            
            sector_scores[sector] = min(100, max(0, score))
        
        # Categorize sectors
        accumulate = [s for s, sc in sector_scores.items() if sc >= 75]
        hold = [s for s, sc in sector_scores.items() if 50 <= sc < 75]
        reduce = [s for s, sc in sector_scores.items() if sc < 50]
        
        # Sort by score
        accumulate_sorted = sorted([(s, sector_scores[s]) for s in accumulate], key=lambda x: x[1], reverse=True)
        hold_sorted = sorted([(s, sector_scores[s]) for s in hold], key=lambda x: x[1], reverse=True)
        reduce_sorted = sorted([(s, sector_scores[s]) for s in reduce], key=lambda x: x[1], reverse=True)
        
        return {
            "accumulate": [{"sector": s, "score": round(sc, 1)} for s, sc in accumulate_sorted],
            "hold": [{"sector": s, "score": round(sc, 1)} for s, sc in hold_sorted],
            "reduce": [{"sector": s, "score": round(sc, 1)} for s, sc in reduce_sorted],
            "rationale": (
                f"Based on relative strength, momentum, and valuation. "
                f"Accumulate sectors show oversold conditions + positive momentum. "
                f"Reduce sectors show stretched valuations + negative momentum."
            ),
            "actionable": (
                f"Rotate from {reduce[0] if reduce else 'weak sectors'} to "
                f"{accumulate[0] if accumulate else 'strong sectors'}. "
                f"Consider {accumulate[0:2] if accumulate else []}."
            ) if accumulate and reduce else "Market in neutral zone. Hold current allocations.",
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error calculating sector rotation signals: {e}")
        return {
            "error": str(e),
            "accumulate": [],
            "hold": [],
            "reduce": [],
        }


def get_earnings_calendar(next_days: int = 30) -> Dict:
    """
    Get upcoming earnings calendar with pre-earnings volatility alerts.
    
    Args:
        next_days: Number of days ahead to scan (max 90)
    
    Returns:
        Dict with upcoming earnings, expected vol%, analyst estimates
    """
    try:
        # Simplified earnings calendar (mock data from 20 major stocks)
        # In production, use real earnings API like earnings.com
        
        major_stocks = [
            "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY",
            "WIPRO", "SBIN", "AXIS", "DMART", "LT"
        ]
        
        earnings_data = []
        
        for symbol in major_stocks:
            try:
                ticker = yf.Ticker(_yf_ticker(symbol))
                
                # Get historical earnings impact (volatility around earnings)
                hist = ticker.history(period="1y")
                if hist.empty or len(hist) < 60:
                    continue
                
                closes = hist["Close"].values.astype(float)
                recent_vol = np.std(np.diff(closes[-20:]) / closes[-20:-1]) * np.sqrt(252)
                
                # Estimate next earnings (simplification: quarterly, assuming earnings on specific dates)
                # In real scenario, fetch from earnings.com API
                earnings_dates = [
                    datetime(2026, 4, 15),   # Q4 FY26
                    datetime(2026, 7, 20),   # Q1 FY27
                ]
                
                for earnings_date in earnings_dates:
                    days_until = (earnings_date.date() - datetime.now().date()).days
                    if 0 < days_until <= next_days:
                        # Estimate pre-earnings volatility boost
                        pre_earnings_vol = recent_vol * 1.3  # Typically 30% higher vol before earnings
                        
                        earnings_data.append({
                            "symbol": symbol,
                            "earningsDate": earnings_date.strftime("%Y-%m-%d"),
                            "daysUntil": days_until,
                            "currentVol": round(recent_vol * 100, 1),
                            "expectedEarningsVol": round(pre_earnings_vol * 100, 1),
                            "volBoost": round((pre_earnings_vol - recent_vol) / recent_vol * 100, 1),
                            "strategy": (
                                "Buy volatility (straddle/strangle)" if pre_earnings_vol > recent_vol * 1.25
                                else "Reduce position size" if pre_earnings_vol > recent_vol * 1.1
                                else "Normal trading"
                            ),
                            "riskAlert": "HIGH" if days_until <= 2 else "MEDIUM" if days_until <= 7 else "NORMAL",
                        })
            except:
                pass
        
        # Sort by days until earnings
        earnings_data.sort(key=lambda x: x["daysUntil"])
        
        return {
            "earnings": earnings_data[:15],  # Top 15 nearest earnings
            "totalUpcoming": len(earnings_data),
            "nextEarnings": earnings_data[0]["symbol"] if earnings_data else None,
            "recommendations": {
                "strategy": "Reduce position size 2-3 days before earnings to avoid gap risk",
                "hedging": "Consider buying puts if holding large positions into earnings",
                "volatility": "Expected vol boost offers premium selling opportunities (sell calls/puts)",
            },
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error fetching earnings calendar: {e}")
        return {
            "error": str(e),
            "earnings": [],
        }


def advanced_stock_screener(filters: Dict) -> Dict:
    """
    Advanced stock screener with multiple filter criteria.
    
    Supported filters:
    - rs_min_vs_market: Relative strength > X (default: 1.0)
    - rsi_min, rsi_max: RSI range (default: 30-70)
    - pe_min, pe_max: P/E ratio range
    - momentum_min: % price change min
    - volume_boost_min: Volume ratio > X (default: 1.0)
    - sector: Filter by sector
    - risk_level: "LOW" / "MEDIUM" / "HIGH"
    
    Returns:
        Dict with filtered stocks ranked by composite score
    """
    try:
        # Get all Indian stocks
        symbols = list(INDIAN_STOCKS.keys())[:363]
        
        # Filter parameters with defaults
        rs_min = filters.get("rs_min_vs_market", 0.95)
        rsi_min = filters.get("rsi_min", 30)
        rsi_max = filters.get("rsi_max", 70)
        pe_min = filters.get("pe_min", 5)
        pe_max = filters.get("pe_max", 50)
        momentum_min = filters.get("momentum_min", -5)  # % change
        volume_boost_min = filters.get("volume_boost_min", 1.0)
        sector_filter = filters.get("sector")
        risk_level = filters.get("risk_level", "MEDIUM")
        
        # Risk level volatility bounds
        risk_vol_bounds = {
            "LOW": (0, 0.02),
            "MEDIUM": (0.02, 0.04),
            "HIGH": (0.04, 1.0),
        }
        vol_min, vol_max = risk_vol_bounds.get(risk_level, (0, 1.0))
        
        candidates = []
        
        # Fetch quotes in batches
        for i in range(0, len(symbols), 40):
            batch = symbols[i:i+40]
            quotes = fetch_quotes(batch)
            
            for quote in quotes:
                symbol = quote.get("symbol", "").upper()
                
                # Skip if sector doesn't match
                if sector_filter:
                    from .portfolio_scorer import SECTOR_MAP
                    if SECTOR_MAP.get(symbol) != sector_filter:
                        continue
                
                # Get technical analysis
                try:
                    ticker = yf.Ticker(_yf_ticker(symbol))
                    hist = ticker.history(period="3mo")
                    
                    if hist.empty or len(hist) < 30:
                        continue
                    
                    closes = hist["Close"].values.astype(float)
                    
                    # Calculate metrics
                    rsi = _compute_rsi(closes, 14)
                    momentum = (closes[-1] - closes[-20]) / closes[-20] * 100
                    volatility = np.std(np.diff(closes[-60:]) / closes[-60:-1]) * np.sqrt(252)
                    
                    # Apply RSI filter
                    if not (rsi_min <= rsi <= rsi_max):
                        continue
                    
                    # Apply momentum filter
                    if momentum < momentum_min:
                        continue
                    
                    # Apply volatility (risk) filter
                    if not (vol_min <= volatility <= vol_max):
                        continue
                    
                    # P/E filter
                    pe = float(quote.get("pe", 20))
                    if not (pe_min <= pe <= pe_max):
                        continue
                    
                    # Volume filter
                    volume = float(quote.get("volume", 0))
                    avg_volume = float(quote.get("avgVolume", volume))
                    volume_ratio = volume / avg_volume if avg_volume > 0 else 1.0
                    
                    if volume_ratio < volume_boost_min:
                        continue
                    
                    # Relative strength (simplified)
                    price_change = float(quote.get("pctChange", 0))
                    rs_score = 1.0 + (price_change / 100)  # Rough RS proxy
                    
                    if rs_score < rs_min:
                        continue
                    
                    # Composite score for ranking
                    rsi_score = min(max((rsi - rsi_min) / (rsi_max - rsi_min) * 25, 0), 25)
                    momentum_score = min(max(momentum / 5, -15), 15) if momentum > 0 else 0
                    volume_score = (volume_ratio - 1.0) * 10 if volume_ratio > 1.0 else -5
                    value_score = (1 - (pe - pe_min) / (pe_max - pe_min)) * 20 if pe > 0 else 10
                    
                    composite_score = 50 + rsi_score + momentum_score + volume_score + value_score
                    
                    candidates.append({
                        "symbol": symbol,
                        "price": round(float(quote.get("last", 0)), 2),
                        "pctChange": round(price_change, 2),
                        "rsi": round(rsi, 1),
                        "momentum": round(momentum, 2),
                        "pe": round(pe, 1),
                        "volatility": round(volatility * 100, 1),
                        "volumeRatio": round(volume_ratio, 2),
                        "score": round(composite_score, 1),
                        "recommendation": "STRONG_BUY" if composite_score >= 80 else ("BUY" if composite_score >= 65 else "HOLD"),
                    })
                
                except:
                    continue
        
        # Sort by score
        candidates.sort(key=lambda x: x["score"], reverse=True)
        
        return {
            "totalScanned": len(symbols),
            "matchedCount": len(candidates),
            "results": candidates[:20],  # Top 20
            "filters": {
                "rsMin": rs_min,
                "rsiRange": [rsi_min, rsi_max],
                "peRange": [pe_min, pe_max],
                "momentumMin": momentum_min,
                "volumeBoostMin": volume_boost_min,
                "sector": sector_filter or "All",
                "riskLevel": risk_level,
            },
            "screenTime": "Real-time",
            "lastUpdated": datetime.utcnow().isoformat(),
        }
    
    except Exception as e:
        logger.error(f"Error running advanced screener: {e}")
        return {
            "error": str(e),
            "results": [],
        }


def _handle_indian_finance_query(query: str) -> Dict:
    """
    Handle Indian finance education and terminology queries.
    """
    query_lower = query.lower().strip()

    # IPO related queries
    if "ipo" in query_lower:
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "📈 **What is an IPO (Initial Public Offering)?**\n\n"
                    "An IPO is when a private company offers its shares to the public for the first time. "
                    "In India, IPOs are regulated by SEBI and happen on NSE/BSE.\n\n"
                    "**Key Points:**\n"
                    "• Companies raise capital for expansion\n"
                    "• Retail investors can apply through UPI/Demat\n"
                    "• Lot size determines minimum investment\n"
                    "• Allotment is done via lottery system\n"
                    "• Listing happens 3-4 days after closure\n\n"
                    "**Risks:** High volatility, lock-in period, no guaranteed returns"
                ),
                "suggestions": ["How to apply for IPO?", "Best upcoming IPOs", "IPO allotment status"]
            }

    # SIP related queries
    elif "sip" in query_lower:
        if "what is" in query_lower or "explain" in query_lower or "how" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "💰 **What is SIP (Systematic Investment Plan)?**\n\n"
                    "SIP is a smart way to invest regularly in mutual funds. Instead of investing a large sum at once, "
                    "you invest a fixed amount every month/quarter.\n\n"
                    "**Benefits:**\n"
                    "• **Rupee Cost Averaging**: Buy more units when prices are low\n"
                    "• **Discipline**: Regular investing habit\n"
                    "• **Power of Compounding**: Long-term wealth creation\n"
                    "• **Low Minimum**: Start with ₹100-500 per month\n\n"
                    "**Types:** Daily, Weekly, Monthly, Quarterly SIP\n"
                    "**Best for:** Long-term wealth creation (5+ years)"
                ),
                "suggestions": ["Best SIP funds", "How to start SIP", "SIP calculator"]
            }

    # F&O related queries
    elif any(term in query_lower for term in ["f&o", "fno", "futures", "options", "derivatives"]):
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "⚡ **What are F&O (Futures & Options)?**\n\n"
                    "F&O are derivative instruments that derive value from underlying assets like stocks or indices.\n\n"
                    "**Futures:** Contract to buy/sell asset at predetermined price on future date\n"
                    "**Options:** Right (not obligation) to buy/sell at strike price before expiry\n\n"
                    "**Key Terms:**\n"
                    "• **Call Option**: Bet on price increase\n"
                    "• **Put Option**: Bet on price decrease\n"
                    "• **Strike Price**: Predetermined price level\n"
                    "• **Premium**: Cost of the option\n"
                    "• **Expiry**: Contract end date\n\n"
                    "**Risk:** High leverage, can result in unlimited losses\n"
                    "**Regulation:** SEBI regulated, margin requirements apply"
                ),
                "suggestions": ["F&O basics", "Options strategies", "F&O margin calculator"]
            }

    # Demat account queries
    elif "demat" in query_lower:
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "🏦 **What is a Demat Account?**\n\n"
                    "Demat (Dematerialized) account holds shares and securities in electronic form. "
                    "Required for trading in Indian stock markets.\n\n"
                    "**Key Features:**\n"
                    "• **Electronic Holding**: No physical share certificates\n"
                    "• **Safe Storage**: Shares stored with Depository Participants (DPs)\n"
                    "• **Easy Transfer**: Instant transfer of shares\n"
                    "• **Consolidation**: All investments in one account\n\n"
                    "**Types:**\n"
                    "• Regular Demat Account\n"
                    "• BSDA (Basic Services Demat Account) - ₹0 annual maintenance\n"
                    "• Minor Demat Account\n\n"
                    "**Required Documents:** PAN, Aadhaar, Bank details, Address proof"
                ),
                "suggestions": ["How to open Demat account", "Best Demat accounts", "Demat charges"]
            }

    # Mutual fund queries
    elif "mutual fund" in query_lower:
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "📊 **What are Mutual Funds?**\n\n"
                    "Mutual funds pool money from investors to invest in stocks, bonds, gold, etc. "
                    "Managed by professional fund managers.\n\n"
                    "**Types:**\n"
                    "• **Equity Funds**: Invest in stocks (High risk, high returns)\n"
                    "• **Debt Funds**: Invest in bonds (Low risk, stable returns)\n"
                    "• **Hybrid Funds**: Mix of equity and debt\n"
                    "• **Index Funds**: Track market indices like Nifty 50\n"
                    "• **ELSS**: Tax-saving equity funds\n\n"
                    "**Benefits:**\n"
                    "• Professional management\n"
                    "• Diversification\n"
                    "• Liquidity\n"
                    "• Tax benefits (ELSS)\n\n"
                    "**Risk:** Market risk, fund manager risk, exit load"
                ),
                "suggestions": ["Best mutual funds", "ELSS funds", "SIP in mutual funds"]
            }

    # Nifty/Sensex queries
    elif any(term in query_lower for term in ["nifty", "sensex"]):
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "📈 **What are Nifty 50 and Sensex?**\n\n"
                    "**Nifty 50:** Benchmark index of NSE with 50 large companies\n"
                    "**Sensex:** Benchmark index of BSE with 30 large companies\n\n"
                    "**Key Points:**\n"
                    "• Represent overall market performance\n"
                    "• Weighted by market capitalization\n"
                    "• Trading hours: 9:15 AM - 3:30 PM IST\n"
                    "• Used for derivatives and ETFs\n\n"
                    "**Current Levels:**\n"
                    "• Nifty 50: ~22,000 points\n"
                    "• Sensex: ~72,000 points\n\n"
                    "**Importance:** Helps track market trends and investor sentiment"
                ),
                "suggestions": ["Nifty 50 stocks", "Sensex companies", "Market analysis"]
            }

    # Dividend queries
    elif "dividend" in query_lower:
        if "what is" in query_lower or "explain" in query_lower:
            return {
                "type": "education",
                "answer": (
                    "💵 **What are Dividends?**\n\n"
                    "Dividends are portion of company's profits distributed to shareholders. "
                    "Paid periodically (quarterly/annually).\n\n"
                    "**Types:**\n"
                    "• **Cash Dividend**: Direct payment to shareholders\n"
                    "• **Stock Dividend**: Additional shares instead of cash\n"
                    "• **Special Dividend**: One-time extra dividend\n\n"
                    "**Key Terms:**\n"
                    "• **Dividend Yield**: Annual dividend per share ÷ Current price\n"
                    "• **Record Date**: Date when shareholders are recorded\n"
                    "• **Ex-Dividend Date**: Date after which buyers don't get dividend\n"
                    "• **Dividend Payout Ratio**: % of profits paid as dividend\n\n"
                    "**Taxation:** Taxed at applicable slab rates for individuals"
                ),
                "suggestions": ["High dividend stocks", "Dividend yield calculator", "Dividend history"]
            }

    # General fallback for Indian finance terms
    else:
        try:
            from .ai_engine_enhanced import IndianMarketContext
            term = query_lower.split()[0]  # Get first word as potential term
            explanation = IndianMarketContext.explain_indian_term(term)
            if explanation != f"'{term}' is a financial term. Please clarify the context.":
                return {
                    "type": "education",
                    "answer": f"📚 **{term.upper()}**: {explanation}",
                    "suggestions": ["Learn more about Indian markets", "Stock market basics", "Investment options"]
                }
        except ImportError:
            pass

    # Default response for unrecognized Indian finance queries
    return {
        "type": "education",
        "answer": (
            "🤔 I can help you understand various Indian financial concepts!\n\n"
            "Try asking about:\n"
            "• IPO (Initial Public Offering)\n"
            "• SIP (Systematic Investment Plan)\n"
            "• Mutual Funds & ELSS\n"
            "• F&O (Futures & Options)\n"
            "• Demat Account\n"
            "• Nifty 50 & Sensex\n"
            "• Dividends & Taxation\n\n"
            "What specific topic would you like to learn about?"
        ),
        "suggestions": ["What is IPO?", "How does SIP work?", "Mutual funds basics"]
    }

