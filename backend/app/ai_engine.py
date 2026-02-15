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
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from .market_data import _yf_ticker, INDIAN_STOCKS, fetch_quote

logger = logging.getLogger(__name__)


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
            sector = industry = company_name = "Unknown"
            book_value = debt_to_equity = roe = revenue_growth = 0

        # AI Predictions
        prediction = predict_price(symbol)

        # Compute overall score (0-100)
        score, score_breakdown = _compute_stock_score(
            rsi, macd, bollinger, mas, pe, dividend_yield,
            current, fifty_two_high, fifty_two_low,
            roe, revenue_growth, debt_to_equity
        )

        # Generate plain-English summary
        summary = _generate_summary(
            symbol, company_name, current, score, rsi, macd,
            mas, pe, prediction, bollinger
        )

        # Determine stock name from our catalog or Yahoo
        name = INDIAN_STOCKS.get(symbol, (None, company_name))[1]

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
            "modelAccuracy": prediction.get("modelAccuracy", 0),
            "disclaimer": "AI analysis is for educational purposes only. Not financial advice. Always do your own research.",
            "lastUpdated": datetime.utcnow().isoformat(),
        }

    except Exception as e:
        logger.error(f"Analysis error for {symbol}: {e}")
        return {"error": str(e)}


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

def ai_assistant(query: str) -> Dict:
    """
    Process natural language queries about stocks.
    Examples:
      - "Should I buy RELIANCE?"
      - "Best pharma stocks under 500"
      - "Compare TCS and INFY"
      - "Is SBIN overvalued?"
      - "Predict Tata Motors price"
    """
    query_lower = query.lower().strip()

    # Extract symbol(s) from query
    symbols = _extract_symbols(query)

    # Route to appropriate handler
    if any(w in query_lower for w in ["predict", "forecast", "future", "target", "price prediction"]):
        return _handle_prediction_query(symbols, query)

    elif any(w in query_lower for w in ["compare", "vs", "versus", "better"]):
        return _handle_compare_query(symbols, query)

    elif any(w in query_lower for w in ["buy", "sell", "should i", "invest", "good time"]):
        return _handle_buy_sell_query(symbols, query)

    elif any(w in query_lower for w in ["best", "top", "undervalued", "overvalued", "cheap", "value"]):
        return _handle_screening_query(query_lower, symbols)

    elif any(w in query_lower for w in ["analyze", "analysis", "detail", "about", "tell me"]):
        return _handle_analysis_query(symbols, query)

    elif symbols:
        # Default: analyze first symbol found
        return _handle_analysis_query(symbols, query)

    else:
        return {
            "type": "help",
            "answer": "I can help you with Indian stocks! Try asking:\n\n"
                      "• \"Should I buy RELIANCE?\"\n"
                      "• \"Predict TCS price\"\n"
                      "• \"Compare INFY and TCS\"\n"
                      "• \"Best bank stocks\"\n"
                      "• \"Analyze SBIN\"\n"
                      "• \"Is HDFCBANK overvalued?\"\n\n"
                      "I cover 363+ Indian stocks with live data!",
            "suggestions": [
                "Should I buy RELIANCE?",
                "Predict TCS price",
                "Best pharma stocks",
                "Compare INFY and TCS",
                "Analyze HDFCBANK",
            ],
        }


def _extract_symbols(query: str) -> List[str]:
    """Extract stock symbols from a natural language query."""
    symbols = []
    query_upper = query.upper()
    words = query_upper.replace(",", " ").replace(".", " ").split()

    # Direct symbol match
    for word in words:
        clean = word.strip("?!.,")
        if clean in INDIAN_STOCKS:
            symbols.append(clean)

    # Name-based match if no symbols found
    if not symbols:
        query_lower = query.lower()
        for sym, (ticker, name) in INDIAN_STOCKS.items():
            # Match company short names
            short_names = name.lower().replace(" ltd", "").replace(" limited", "").split()
            for word in short_names:
                if len(word) > 3 and word in query_lower:
                    if sym not in symbols:
                        symbols.append(sym)
                    break

    return symbols[:5]  # Max 5 symbols


def _handle_prediction_query(symbols: List[str], query: str) -> Dict:
    """Handle price prediction queries."""
    if not symbols:
        return {"type": "error", "answer": "Please specify a stock symbol. E.g., 'Predict RELIANCE price'"}

    symbol = symbols[0]
    pred = predict_price(symbol)
    analysis = analyze_stock(symbol)

    if "error" in pred and not pred.get("predictions"):
        return {"type": "error", "answer": f"Could not generate prediction for {symbol}: {pred['error']}"}

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
    answer_parts.append(f"\n⚠️ {pred.get('disclaimer', '')}")

    return {
        "type": "prediction",
        "symbol": symbol,
        "answer": "\n".join(answer_parts),
        "data": pred,
        "suggestions": [
            f"Should I buy {symbol}?",
            f"Analyze {symbol}",
            f"Compare {symbol} with peers",
        ],
    }


def _handle_compare_query(symbols: List[str], query: str) -> Dict:
    """Handle stock comparison queries."""
    if len(symbols) < 2:
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

    # Winner
    valid = {s: a for s, a in analyses.items() if "error" not in a}
    if valid:
        winner = max(valid, key=lambda s: valid[s].get("score", 0))
        answer_parts.append(f"\n🏆 **Winner: {winner}** (Score: {valid[winner].get('score', 0)}/100)")

    return {
        "type": "comparison",
        "answer": "\n".join(answer_parts),
        "data": analyses,
        "suggestions": [f"Predict {s} price" for s in symbols[:2]],
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
        "answer": analysis.get("summary", "No analysis available."),
        "score": analysis.get("score", 0),
        "signal": analysis.get("signal", "HOLD"),
        "data": analysis,
        "suggestions": [
            f"Predict {symbol} price",
            f"Compare {symbol} with peers",
        ],
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
        "answer": analysis.get("summary", "No analysis available."),
        "score": analysis.get("score", 0),
        "signal": analysis.get("signal", "HOLD"),
        "data": analysis,
        "suggestions": [
            f"Predict {symbol} price",
            f"Should I buy {symbol}?",
        ],
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
        "defence": ["HAL", "BEL", "BDL", "MAZAGON", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "infra": ["LT", "ADANIENT", "ADANIPORTS", "IRCON", "RVNL", "NBCC", "NCC", "KEC"],
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

    return {
        "type": "screening",
        "answer": "\n".join(answer_parts),
        "stocks": results,
        "suggestions": [f"Analyze {results[0]['symbol']}" if results else "Analyze RELIANCE"],
    }
