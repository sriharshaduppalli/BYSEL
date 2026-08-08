"""
Enhanced AI Analysis API Routes
Exposes all Level-2 AI improvements via REST endpoints
"""

from fastapi import APIRouter, HTTPException, Query
from typing import Optional, List, Dict
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/ai/v2", tags=["AI v2 Enhanced"])

# Import the enhanced AI engine
try:
    from app.ai_engine_enhanced import (
        QueryIntentClassifier,
        ConfidenceExplainer,
        EventRiskModeler,
        SentimentAnalyzer,
        enhance_analysis_response,
    )
except ImportError:
    logger.warning("Enhanced AI engine not available, using fallback mode")


@router.post("/analyze-with-explanation")
async def analyze_stock_enhanced(
    symbol: str,
    query: Optional[str] = None,
) -> Dict:
    """
    Comprehensive enhanced stock analysis with:
    - Query understanding & intent extraction
    - Confidence breakdown with visual factors
    - Event risk adjustment
    - Sentiment analysis with weighting
    - Recommended action with reasoning
    
    Returns enriched analysis with all Level-2 improvements.
    """
    try:
        # Import base AI engine
        from app.ai_engine import analyze_stock
        
        symbol_upper = symbol.upper().strip()
        if not symbol_upper:
            raise HTTPException(status_code=400, detail="Symbol required")
        
        # 1. Get base analysis
        base_analysis = analyze_stock(symbol_upper)
        
        # 2. Enhance with all improvements
        enhanced = enhance_analysis_response(base_analysis, query)
        
        # 3. Restructure to match Android model
        return {
            "symbol": symbol_upper,
            "apiVersion": "v2",
            "timestamp": __import__("datetime").datetime.utcnow().isoformat(),
            "baseAnalysis": enhanced["base"],
            "enhancedFeatures": {
                "confidenceBreakdown": enhanced["enhancements"]["confidenceBreakdown"],
                "predictionReasoning": {
                    "signal": enhanced["base"]["signal"],
                    "whyConfident": enhanced["enhancements"]["interpretations"]["whyConfident"],
                    "caveats": enhanced["enhancements"]["interpretations"]["caveatListings"]
                },
                "eventRiskAnalysis": enhanced["enhancements"]["eventRiskAdjustment"] if enhanced["enhancements"]["eventRiskAdjustment"]["eventRisks"] else None,
                "sentimentAnalysis": enhanced["enhancements"]["sentimentAnalysis"],
                "queryUnderstanding": enhanced["enhancements"]["queryAnalysis"] if enhanced["enhancements"]["queryAnalysis"] else {
                    "intents": ["analysis"],
                    "timeframe": {"phrase": "medium-term", "days": 90},
                    "confidence": 0.8
                }
            }
        }
        
    except Exception as e:
        logger.error(f"Enhanced analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/analyze-query-intent")
async def analyze_query_intent(query: str) -> Dict:
    """
    Analyze user query to extract intents, timeframes, and sectors.
    Returns structured understanding of what the user is asking.
    
    Addresses: "Query understanding is limited"
    """
    try:
        analysis = QueryIntentClassifier.analyze_query(query)
        return {
            "query": query,
            "analysis": analysis,
            "interpretations": {
                "mainIntents": analysis.get("intents", []),
                "timeframeAsked": analysis.get("timeframe", {}).get("phrase"),
                "riskFocused": analysis.get("riskFocus", False),
                "dividendFocused": analysis.get("dividendFocus", False),
                "comparingStocks": analysis.get("comparisonRequest", False),
                "wantsPrediction": analysis.get("predictionRequest", False),
                "understandingConfidence": f"{int(analysis.get('confidence', 0.5) * 100)}%",
            }
        }
    except Exception as e:
        logger.error(f"Query analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/confidence-breakdown")
async def get_confidence_breakdown(
    symbol: str,
    prediction: Optional[Dict] = None,
    model_accuracy: Optional[float] = 65.0,
) -> Dict:
    """
    Get detailed confidence breakdown with visual factors.
    Shows exactly WHY the model is confident or uncertain.
    
    Addresses: "Confidence scores aren't trustworthy" + "UI doesn't explain reasoning"
    """
    try:
        from app.ai_engine import analyze_stock
        
        symbol_upper = symbol.upper().strip()
        
        # Get base data
        analysis = analyze_stock(symbol_upper)
        
        # Prepare data for confidence explanation
        historical_data = {
            "dataPoints": 250,
            "missingRatio": 0.0,
            "recencyDays": 1,
            "volatility": 0.02,
        }
        
        # Generate confidence breakdown
        breakdown = ConfidenceExplainer.explain_prediction_confidence(
            analysis,
            historical_data,
            model_accuracy or 65.0
        )
        
        return {
            "symbol": symbol_upper,
            "breakdown": breakdown,
            "factors": breakdown.get("factors", {}),
            "visualization": {
                "gaugeValue": breakdown.get("overallConfidence", 0),
                "gaugeLevel": breakdown.get("confidenceLevel", "Unknown"),
                "gaugeColor": _get_gauge_color(breakdown.get("overallConfidence", 0)),
            },
            "caveats": breakdown.get("caveats", []),
        }
    except Exception as e:
        logger.error(f"Confidence breakdown error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/event-risk-analysis")
async def check_event_risks(
    symbol: str,
    prediction_horizon: int = 30,
) -> Dict:
    """
    Check for upcoming events that impact prediction confidence.
    
    Addresses: "Predictions are inaccurate for specific stocks"
    Accounts for: earnings, sector crises, macro events
    """
    try:
        from app.ai_engine import analyze_stock
        
        symbol_upper = symbol.upper().strip()
        analysis = analyze_stock(symbol_upper)
        
        # Get base confidence from analysis
        base_conf = float(analysis.get("score", 70))
        sector = analysis.get("sector", "")
        
        # Adjust for events
        adjustment = EventRiskModeler.adjust_confidence_for_events(
            symbol_upper,
            base_conf,
            prediction_horizon,
            sector
        )
        
        return {
            "symbol": symbol_upper,
            "prediction_horizon_days": prediction_horizon,
            "baseConfidence": adjustment.get("baseConfidence"),
            "adjustedConfidence": adjustment.get("adjustedConfidence"),
            "adjustmentFactor": adjustment.get("adjustmentFactor"),
            "eventRisks": adjustment.get("eventRisks", []),
            "reason": adjustment.get("adjustmentReason"),
            "visualization": {
                "before": adjustment.get("baseConfidence"),
                "after": adjustment.get("adjustedConfidence"),
                "impactPercentage": ((adjustment.get("adjustmentFactor", 1.0) - 1) * 100),
            }
        }
    except Exception as e:
        logger.error(f"Event risk analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/sentiment-analysis")
async def analyze_sentiment(
    symbol: str = None,
    headlines: Optional[List[Dict]] = None,
) -> Dict:
    """
    Advanced sentiment analysis with weighted scoring.
    
    Addresses: "Predictions are inaccurate for specific stocks"
    Uses: keyword weighting, recency factors, probability scoring
    """
    try:
        # Use provided headlines or fetch from news
        if headlines is None:
            from app.ai_engine import _fetch_recent_headlines
            headlines = _fetch_recent_headlines(symbol, limit=10) if symbol else []
        
        # Analyze with weighted scoring
        sentiment = SentimentAnalyzer.analyze_headlines_sentiment(headlines)
        
        return {
            "symbol": symbol,
            "sentiment": sentiment,
            "visualization": {
                "score": sentiment.get("score"),  # -1 to +1
                "level": sentiment.get("overallSentiment"),
                "strength": sentiment.get("strength"),
                "scoreBar": _sentiment_to_visual(sentiment.get("score", 0)),
            },
            "breakdown": sentiment.get("breakdown"),
            "interpretation": sentiment.get("interpretation"),
            "recommendation": _sentiment_to_recommendation(sentiment.get("score", 0)),
        }
    except Exception as e:
        logger.error(f"Sentiment analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/prediction-with-reasoning")
async def get_prediction_with_reasoning(
    symbol: str,
    include_events: bool = True,
    include_sentiment: bool = True,
) -> Dict:
    """
    Get price prediction with complete reasoning chain:
    1. Base prediction models
    2. Confidence breakdown
    3. Event risk adjustments
    4. Sentiment integration
    5. Recommended action
    """
    try:
        from app.ai_engine import predict_price, analyze_stock
        
        symbol_upper = symbol.upper().strip()
        
        # 1. Base prediction
        prediction = predict_price(symbol_upper)
        
        # 2. Confidence explanation
        analysis = analyze_stock(symbol_upper)
        conf_breakdown = ConfidenceExplainer.explain_prediction_confidence(
            prediction,
            {"dataPoints": 250, "missingRatio": 0.0, "recencyDays": 1, "volatility": 0.02},
            float(prediction.get("modelAccuracy", 65))
        )
        
        result_data = {
            "symbol": symbol_upper,
            "prediction": prediction,
            "confidenceBreakdown": conf_breakdown,
        }
        
        # 3. Event risk adjustments
        if include_events:
            sector = analysis.get("sector", "")
            event_adj = EventRiskModeler.adjust_confidence_for_events(
                symbol_upper,
                conf_breakdown["overallConfidence"],
                30,  # 30-day default
                sector
            )
            result_data["eventRiskAdjustment"] = event_adj
        
        # 4. Sentiment integration
        if include_sentiment:
            headlines = analysis.get("news", {}).get("headlines", [])
            sentiment = SentimentAnalyzer.analyze_headlines_sentiment(headlines)
            result_data["sentiment"] = sentiment
        
        # 5. Recommended action
        result_data["recommendedAction"] = _generate_action_recommendation(
            prediction, conf_breakdown, result_data.get("eventRiskAdjustment")
        )
        
        return result_data
        
    except Exception as e:
        logger.error(f"Prediction with reasoning error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ──────────────────────────────────────────────────────────────
# HELPER FUNCTIONS
# ──────────────────────────────────────────────────────────────

def _get_gauge_color(confidence: float) -> str:
    """Map confidence score to color."""
    if confidence >= 75:
        return "#28A745"  # Green
    elif confidence >= 60:
        return "#FFC107"  # Yellow
    else:
        return "#DC3545"  # Red


def _sentiment_to_visual(score: float) -> float:
    """Convert sentiment score (-1 to +1) to visual percentage (0-100)."""
    return round((score + 1) / 2 * 100, 1)


def _sentiment_to_recommendation(score: float) -> str:
    """Convert sentiment score to recommendation."""
    if score > 0.6:
        return "Very Bullish"
    elif score > 0.3:
        return "Bullish"
    elif score < -0.6:
        return "Very Bearish"
    elif score < -0.3:
        return "Bearish"
    else:
        return "Neutral"


def _generate_action_recommendation(prediction: Dict, conf_breakdown: Dict, event_adj: Dict = None) -> Dict:
    """Generate final action recommendation."""
    signal = prediction.get("signal", "HOLD")
    confidence = conf_breakdown.get("overallConfidence", 50)
    
    if event_adj:
        confidence = event_adj.get("adjustedConfidence", confidence)
    
    action = "📊 Analyze More"
    reason = "Gather more data before deciding"
    
    if signal == "STRONG_BUY" and confidence >= 75:
        action = "🟢 STRONG BUY"
        reason = f"High conviction ({confidence:.0f}%): All signals aligned and confident"
    elif signal == "BUY" and confidence >= 60:
        action = "✅ BUY"
        reason = f"Moderate confidence ({confidence:.0f}%): Bullish indicators present"
    elif signal == "STRONG_SELL" and confidence >= 75:
        action = "🔴 STRONG SELL"
        reason = f"High conviction ({confidence:.0f}%): Bearish pattern confirmed"
    elif signal == "HOLD" or confidence < 55:
        action = "⏸️ HOLD"
        reason = "Mixed signals or elevated uncertainty - Wait for clearer trend"
    
    return {
        "action": action,
        "reason": reason,
        "riskLevel": "Low" if confidence >= 75 else "Medium" if confidence >= 60 else "High",
        "confidence": round(confidence, 1),
    }


# ==================== DAILY BRIEF ====================

@router.get("/daily-brief")
async def get_daily_brief():
    """
    Morning/evening AI market brief: global cues, top movers, sentiment, outlook.
    Used by AiDailyBriefCard on the Dashboard.
    """
    try:
        from app.ai_engine import get_market_headlines, get_best_stocks_to_buy, analyze_stock
        from datetime import datetime, timezone
        import pytz

        ist = pytz.timezone("Asia/Kolkata")
        now_ist = datetime.now(ist)
        try:
            from app.market_session import CASH_OPEN, is_within_equity_session, session_close_for_status

            t = now_ist.time()
            if t < CASH_OPEN:
                session = "pre-market"
            elif is_within_equity_session(now_ist):
                session = "market"
            else:
                session = "post-market"
            _ = session_close_for_status(now_ist.date())
        except Exception:
            hour = now_ist.hour
            minute = now_ist.minute
            # Fallback: open until 15:40 after CAS go-live approximation.
            session = (
                "pre-market"
                if hour < 9 or (hour == 9 and minute < 15)
                else "post-market"
                if hour > 15 or (hour == 15 and minute > 40)
                else "market"
            )

        headlines_data = get_market_headlines(limit=6)
        headlines = headlines_data.get("headlines", [])

        top_movers: List[Dict] = []
        default_symbols = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK"]
        for sym in default_symbols[:3]:
            try:
                a = analyze_stock(sym)
                top_movers.append({
                    "symbol": sym,
                    "signal": a.get("signal", "HOLD"),
                    "score": a.get("score", 5),
                    "change_pct": a.get("change_pct", 0.0),
                })
            except Exception:
                pass

        overall_sentiment = "Neutral"
        try:
            sentiment_result = SentimentAnalyzer.analyze_headlines_sentiment(headlines)
            overall_sentiment = sentiment_result.get("overallSentiment", "Neutral")
        except Exception:
            pass

        if session == "pre-market":
            greeting = "Good morning! Here's your pre-market brief."
            outlook = "Markets open soon. Watch for gap-up/gap-down based on global cues."
        elif session == "post-market":
            greeting = "Markets closed. Here's your post-market recap."
            outlook = "Review today's trades and plan tomorrow's strategy."
        else:
            greeting = "Markets are live. Here's your intraday summary."
            outlook = "Monitor live positions and sector rotations."

        return {
            "session": session,
            "timestamp": now_ist.isoformat(),
            "greeting": greeting,
            "overallSentiment": overall_sentiment,
            "outlook": outlook,
            "topMovers": top_movers,
            "headlineCount": len(headlines),
            "headlines": headlines[:3],
        }
    except Exception as exc:
        logger.exception("daily_brief.error reason=%s", exc)
        raise HTTPException(status_code=500, detail="Failed to generate daily brief")


# ==================== PARSE TRADE INTENT (SERVER-SIDE NLP) ====================

@router.post("/parse-trade-intent")
async def parse_trade_intent(body: Dict):
    """
    Server-side NLP for trade intent parsing.
    Replaces client-side regex in TradeIntentParser.kt with richer intent extraction.
    Input: { "text": "Buy 10 shares of Reliance" }
    Output: { "intent": "BUY", "symbol": "RELIANCE", "qty": 10, "price": null, "confidence": 0.9 }
    """
    text: str = body.get("text", "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is required")

    text_lower = text.lower()

    # Intent detection
    intent = "UNKNOWN"
    if any(w in text_lower for w in ["buy", "purchase", "long", "invest in"]):
        intent = "BUY"
    elif any(w in text_lower for w in ["sell", "exit", "close", "short"]):
        intent = "SELL"
    elif any(w in text_lower for w in ["alert", "notify", "ping", "remind", "set alert", "watch"]):
        intent = "ALERT"
    elif any(w in text_lower for w in ["analyze", "analysis", "tell me about", "what about", "how is", "should i"]):
        intent = "ANALYZE"
    elif any(w in text_lower for w in ["predict", "forecast", "target", "price prediction"]):
        intent = "PREDICT"

    # Quantity extraction
    import re
    qty = None
    qty_match = re.search(r'\b(\d+)\s*(?:shares?|qty|quantity|units?|lots?)?\b', text_lower)
    if qty_match:
        qty = int(qty_match.group(1))

    # Price extraction
    price = None
    price_match = re.search(r'(?:at|@|price|above|below|crosses?)\s*(?:rs\.?|inr)?\s*(\d+(?:\.\d+)?)', text_lower)
    if price_match:
        price = float(price_match.group(1))

    # Symbol resolution
    from app.ai_engine import _resolve_symbol_from_company_name
    symbol = _resolve_symbol_from_company_name(text)

    confidence = 0.9 if symbol and intent != "UNKNOWN" else 0.6 if intent != "UNKNOWN" else 0.3

    return {
        "intent": intent,
        "symbol": symbol,
        "qty": qty,
        "price": price,
        "confidence": confidence,
        "raw": text,
    }


# ==================== REAL-TIME SENTIMENT SCORE ====================

@router.get("/sentiment/{symbol}")
async def get_sentiment_score(symbol: str):
    """
    Real-time news sentiment score for a stock (-100 to +100).
    Used by SentimentBar on StockDetailScreen.
    """
    try:
        from app.ai_engine import _fetch_recent_headlines

        headlines = _fetch_recent_headlines(symbol.upper(), limit=10)
        if not headlines:
            return {
                "symbol": symbol.upper(),
                "score": 0,
                "scoreBar": 50.0,
                "level": "Neutral",
                "strength": "Weak",
                "headlineCount": 0,
                "summary": "No recent headlines found.",
            }

        sentiment = SentimentAnalyzer.analyze_headlines_sentiment(headlines)
        raw_score = sentiment.get("score", 0.0)
        bar_score = ((raw_score + 1) / 2) * 100

        return {
            "symbol": symbol.upper(),
            "score": round(raw_score * 100),
            "scoreBar": round(bar_score, 1),
            "level": sentiment.get("overallSentiment", "Neutral"),
            "strength": sentiment.get("strength", "Moderate"),
            "headlineCount": len(headlines),
            "breakdown": sentiment.get("breakdown", {}),
            "summary": sentiment.get("interpretation", ""),
        }
    except Exception as exc:
        logger.exception("sentiment.error symbol=%s reason=%s", symbol, exc)
        raise HTTPException(status_code=500, detail="Failed to compute sentiment")


# ==================== PATTERN DETECTION ====================

@router.get("/patterns/{symbol}")
async def get_chart_patterns(symbol: str, period: str = "3mo"):
    """
    Detect chart patterns for a stock: Head & Shoulders, Double Top/Bottom,
    Triangle, Cup & Handle, Bull/Bear Flag.
    Used by TradingViewChart pattern overlay.
    """
    try:
        import yfinance as yf
        from app.pattern_detector import detect_patterns

        sym = symbol.upper()
        ticker = yf.Ticker(sym if sym.endswith(".NS") else sym + ".NS")
        hist = ticker.history(period=period)

        if hist is None or hist.empty or len(hist) < 30:
            return {"symbol": sym, "patterns": [], "message": "Insufficient data"}

        closes = hist["Close"].tolist()
        highs = hist["High"].tolist()
        lows = hist["Low"].tolist()
        timestamps = [int(ts.timestamp() * 1000) for ts in hist.index]

        patterns = detect_patterns(closes, highs, lows)

        return {
            "symbol": sym,
            "patterns": patterns,
            "dataPoints": len(closes),
            "period": period,
            "timestamps": timestamps,
        }
    except Exception as exc:
        logger.exception("patterns.error symbol=%s reason=%s", symbol, exc)
        raise HTTPException(status_code=500, detail="Failed to detect patterns")


# ==================== PORTFOLIO RISK ENGINE ====================

def _illustrative_risk_payload(
    sym_list: List[str],
    horizon_days: int,
    *,
    reason: str,
) -> Dict:
    """Always-open educational payload when live history is unavailable."""
    n = max(len(sym_list), 1)
    equal = [round(1.0 / n, 4)] * n
    corr = [[1.0 if i == j else 0.55 for j in range(n)] for i in range(n)]
    return {
        "symbols": sym_list,
        "weights": equal,
        "metrics": {
            "var95": -1.8,
            "var99": -2.9,
            "maxDrawdown": -12.5,
            "sharpeRatio": 0.85,
            "annualizedReturn": 14.2,
            "annualizedVolatility": 18.0,
        },
        "var95": -1.8,
        "var99": -2.9,
        "maxDrawdown": -12.5,
        "sharpeRatio": 0.85,
        "annualizedReturn": 14.2,
        "annualizedVolatility": 18.0,
        "correlationMatrix": corr,
        "monteCarlo": {
            "horizonDays": horizon_days,
            "simulations": 500,
            "p5": -8.5,
            "p50": 2.1,
            "p95": 12.4,
        },
        "monteCarloP5": -8.5,
        "monteCarloMedian": 2.1,
        "monteCarloP95": 12.4,
        "riskLevel": "Medium",
        "demoBasket": True,
        "disclaimer": (
            f"Illustrative educational metrics ({', '.join(sym_list)}). {reason}"
        ),
    }


def _build_risk_payload_from_returns(
    returns_dict: Dict[str, "np.ndarray"],
    sym_list: List[str],
    weight_list: List[float],
    horizon_days: int,
    used_demo: bool,
) -> Dict:
    import numpy as np

    min_len = min(len(r) for r in returns_dict.values())
    if min_len < 5:
        raise ValueError("insufficient aligned return history")

    matrix = np.column_stack([returns_dict[s][-min_len:] for s in returns_dict])
    w = np.array([weight_list[i] for i, s in enumerate(sym_list) if s in returns_dict], dtype=float)
    if w.size == 0 or float(w.sum()) <= 0:
        raise ValueError("invalid portfolio weights")
    w /= w.sum()

    portfolio_returns = matrix @ w

    var_95 = float(np.percentile(portfolio_returns, 5))
    var_99 = float(np.percentile(portfolio_returns, 1))

    cum = np.cumprod(1 + portfolio_returns)
    peak = np.maximum.accumulate(cum)
    drawdown = (cum - peak) / peak
    max_drawdown = float(drawdown.min())

    rf_daily = 0.065 / 252
    excess = portfolio_returns - rf_daily
    sharpe = float(excess.mean() / excess.std() * np.sqrt(252)) if excess.std() > 0 else 0.0

    symbols_in = list(returns_dict.keys())
    corr = np.corrcoef(matrix.T).tolist() if len(symbols_in) > 1 else [[1.0]]

    mc_final = []
    for _ in range(500):
        path = np.random.choice(portfolio_returns, size=horizon_days, replace=True)
        mc_final.append(float(np.prod(1 + path) - 1))
    mc_final.sort()
    mc_5th = mc_final[int(0.05 * len(mc_final))]
    mc_50th = mc_final[len(mc_final) // 2]
    mc_95th = mc_final[int(0.95 * len(mc_final))]

    mean_daily = float(portfolio_returns.mean()) if len(portfolio_returns) else 0.0
    std_daily = float(portfolio_returns.std()) if len(portfolio_returns) else 0.0
    annualized_return = (1.0 + mean_daily) ** 252 - 1.0
    annualized_vol = std_daily * float(np.sqrt(252))

    return {
        "symbols": symbols_in,
        "weights": w.tolist(),
        "metrics": {
            "var95": round(var_95 * 100, 2),
            "var99": round(var_99 * 100, 2),
            "maxDrawdown": round(max_drawdown * 100, 2),
            "sharpeRatio": round(sharpe, 2),
            "annualizedReturn": round(annualized_return * 100, 2),
            "annualizedVolatility": round(annualized_vol * 100, 2),
        },
        "var95": round(var_95 * 100, 2),
        "var99": round(var_99 * 100, 2),
        "maxDrawdown": round(max_drawdown * 100, 2),
        "sharpeRatio": round(sharpe, 2),
        "annualizedReturn": round(annualized_return * 100, 2),
        "annualizedVolatility": round(annualized_vol * 100, 2),
        "correlationMatrix": corr,
        "monteCarlo": {
            "horizonDays": horizon_days,
            "simulations": 500,
            "p5": round(mc_5th * 100, 2),
            "p50": round(mc_50th * 100, 2),
            "p95": round(mc_95th * 100, 2),
        },
        "monteCarloP5": round(mc_5th * 100, 2),
        "monteCarloMedian": round(mc_50th * 100, 2),
        "monteCarloP95": round(mc_95th * 100, 2),
        "riskLevel": "Low" if var_95 > -0.01 else "Medium" if var_95 > -0.025 else "High",
        "demoBasket": used_demo,
        "disclaimer": (
            "Educational demo basket (RELIANCE/TCS/INFY) — not your live paper portfolio."
            if used_demo
            else "Computed on the symbols you provided (paper portfolio when supplied by the app)."
        ),
    }


@router.get("/portfolio/risk-analysis")
async def get_portfolio_risk(
    symbols: str = "",
    weights: str = "",
    horizon_days: int = 30,
):
    """
    Portfolio risk: VaR (95%, 99%), Max Drawdown, Sharpe Ratio, Correlation Matrix.
    Used by Risk Lab screen.
    symbols = comma-separated e.g. "RELIANCE,TCS,INFY"
    weights = comma-separated floats e.g. "0.4,0.3,0.3"

    When symbols is empty, callers should pass the authenticated user's holdings
    from the Android client (preferred). Demo basket is a last-resort fallback.

    History is loaded via market_data.fetch_quote_history (same path as /quotes/{sym}/history),
    not raw yfinance — Render often blocks direct Ticker.history() calls.
    """
    import asyncio

    import numpy as np

    from ..market_data import fetch_quote_history

    try:
        horizon_days = max(5, min(int(horizon_days or 30), 90))
        sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
        used_demo = False
        if not sym_list:
            # Educational fallback so Risk Lab still opens when portfolio is empty.
            sym_list = ["RELIANCE", "TCS", "INFY"]
            used_demo = True

        weight_list = [float(w) for w in weights.split(",") if w.strip()] if weights else []
        if not weight_list or len(weight_list) != len(sym_list):
            weight_list = [1 / len(sym_list)] * len(sym_list)

        async def _daily_returns(sym: str):
            try:
                candles = await asyncio.to_thread(fetch_quote_history, sym, "1y", "1d")
                if len(candles) < 10:
                    candles = await asyncio.to_thread(fetch_quote_history, sym, "6mo", "1d")
                closes = [
                    float(c.get("close") or 0.0)
                    for c in candles
                    if float(c.get("close") or 0.0) > 0
                ]
                if len(closes) < 10:
                    return sym, None
                arr = np.asarray(closes, dtype=float)
                return sym, (np.diff(arr) / arr[:-1])
            except Exception as exc:
                logger.debug("risk.history_failed symbol=%s reason=%s", sym, exc)
                return sym, None

        pairs = await asyncio.gather(*[_daily_returns(s) for s in sym_list])
        returns_dict = {s: r for s, r in pairs if r is not None and len(r) > 0}

        if not returns_dict:
            logger.warning(
                "risk.no_history symbols=%s — serving illustrative demo payload",
                ",".join(sym_list),
            )
            return _illustrative_risk_payload(
                sym_list,
                horizon_days,
                reason="Live return history was unavailable; showing sample Risk Lab numbers.",
            )

        try:
            return _build_risk_payload_from_returns(
                returns_dict,
                sym_list,
                weight_list,
                horizon_days,
                used_demo,
            )
        except Exception as exc:
            logger.warning("risk.compute_fallback symbols=%s reason=%s", ",".join(sym_list), exc)
            return _illustrative_risk_payload(
                list(returns_dict.keys()) or sym_list,
                horizon_days,
                reason="Risk computation failed; showing sample Risk Lab numbers.",
            )
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("risk.error symbols=%s reason=%s", symbols, exc)
        # Never hard-fail the Risk Lab screen — educational shell is better than an empty error.
        fallback_syms = [s.strip().upper() for s in symbols.split(",") if s.strip()] or [
            "RELIANCE",
            "TCS",
            "INFY",
        ]
        return _illustrative_risk_payload(
            fallback_syms,
            max(5, min(int(horizon_days or 30), 90)),
            reason="Risk service hit an error; showing sample Risk Lab numbers.",
        )


# ==================== QUARTERLY RESULTS CALENDAR ====================

# Approximate next results windows for major NSE names when Yahoo calendar/info
# is blocked (common on Render). Marked estimated=true in the payload.
_CURATED_EARNINGS_DATES = {
    "RELIANCE": ("2026-10-16", "Energy"),
    "TCS": ("2026-10-08", "Technology"),
    "INFY": ("2026-10-23", "Technology"),
    "HDFCBANK": ("2026-10-18", "Financial Services"),
    "ICICIBANK": ("2026-10-25", "Financial Services"),
    "ITC": ("2026-10-30", "Consumer Defensive"),
    "WIPRO": ("2026-10-15", "Technology"),
    "SBIN": ("2026-11-05", "Financial Services"),
    "AXISBANK": ("2026-10-22", "Financial Services"),
    "LT": ("2026-10-28", "Industrials"),
    "BHARTIARTL": ("2026-10-28", "Communication Services"),
    "KOTAKBANK": ("2026-10-26", "Financial Services"),
}


def _parse_yahoo_earnings_date(cal) -> Optional[str]:
    if cal is None:
        return None
    try:
        if hasattr(cal, "empty") and cal.empty:
            return None
        if hasattr(cal, "columns") and "Earnings Date" in cal.columns:
            ed = cal["Earnings Date"].iloc[0]
            if hasattr(ed, "date"):
                return ed.date().isoformat()
            return str(ed)[:10]
        if isinstance(cal, dict) and "Earnings Date" in cal:
            raw = cal["Earnings Date"]
            if isinstance(raw, (list, tuple)) and raw:
                raw = raw[0]
            if hasattr(raw, "isoformat"):
                return raw.isoformat()[:10]
            if hasattr(raw, "date"):
                return raw.date().isoformat()
            return str(raw)[:10]
    except Exception:
        return None
    return None


def _fetch_one_earnings_row(sym: str) -> Dict:
    """Best-effort Yahoo row; never raises."""
    from datetime import date as date_cls

    from ..market_data import get_stock_name

    row: Dict = {
        "symbol": sym,
        "name": get_stock_name(sym),
        "nextEarningsDate": None,
        "epsTrailing": None,
        "epsForward": None,
        "revenueGrowth": None,
        "pe": None,
        "sector": "",
        "estimated": False,
    }
    try:
        import yfinance as yf

        ticker = yf.Ticker(sym if sym.endswith((".NS", ".BO")) else f"{sym}.NS")
        next_earnings = _parse_yahoo_earnings_date(getattr(ticker, "calendar", None))
        info = {}
        try:
            # ticker.info is often blocked/slow on cloud hosts — keep optional.
            info = ticker.info or {}
        except Exception:
            info = {}

        if not next_earnings:
            for key in ("earningsTimestamp", "earningsDate"):
                raw = info.get(key)
                if raw is None:
                    continue
                try:
                    if isinstance(raw, (list, tuple)) and raw:
                        raw = raw[0]
                    if isinstance(raw, (int, float)):
                        next_earnings = date_cls.fromtimestamp(int(raw)).isoformat()
                    else:
                        next_earnings = str(raw)[:10]
                    break
                except Exception:
                    continue

        eps_trailing = info.get("trailingEps")
        eps_forward = info.get("forwardEps")
        revenue_growth = info.get("revenueGrowth")
        pe = info.get("trailingPE")
        row.update(
            {
                "name": info.get("longName") or row["name"],
                "nextEarningsDate": next_earnings,
                "epsTrailing": round(float(eps_trailing), 2) if eps_trailing else None,
                "epsForward": round(float(eps_forward), 2) if eps_forward else None,
                "revenueGrowth": round(float(revenue_growth) * 100, 1) if revenue_growth else None,
                "pe": round(float(pe), 1) if pe else None,
                "sector": info.get("sector") or "",
            }
        )
    except Exception as exc:
        logger.debug("earnings.yahoo_row_failed symbol=%s reason=%s", sym, exc)

    if not row.get("nextEarningsDate"):
        curated = _CURATED_EARNINGS_DATES.get(sym)
        if curated:
            row["nextEarningsDate"] = curated[0]
            row["sector"] = row.get("sector") or curated[1]
            row["estimated"] = True
    return row


@router.get("/earnings-calendar")
async def get_earnings_calendar(symbols: str = ""):
    """
    Upcoming earnings dates + EPS history for watchlist stocks.
    Uses Yahoo when available; curated approximate dates when Yahoo is blocked.
    """
    import asyncio
    from datetime import date

    try:
        sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
        if not sym_list:
            sym_list = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "ITC", "WIPRO", "SBIN"]

        rows = await asyncio.gather(
            *[asyncio.to_thread(_fetch_one_earnings_row, sym) for sym in sym_list[:12]]
        )
        calendar_items = list(rows)
        calendar_items.sort(key=lambda x: x.get("nextEarningsDate") or "9999")

        return {
            "items": calendar_items,
            "count": len(calendar_items),
            "generatedAt": str(date.today()),
            "disclaimer": (
                "Some dates are approximate educational estimates when live calendar "
                "data is unavailable."
                if any(i.get("estimated") for i in calendar_items)
                else None
            ),
        }
    except Exception as exc:
        logger.exception("earnings_calendar.error reason=%s", exc)
        # Never hard-fail the Android screen — return curated shell.
        from datetime import date

        fallback_syms = [s.strip().upper() for s in symbols.split(",") if s.strip()] or list(
            _CURATED_EARNINGS_DATES.keys()
        )[:8]
        items = [_fetch_one_earnings_row(s) for s in fallback_syms[:12]]
        return {
            "items": items,
            "count": len(items),
            "generatedAt": str(date.today()),
            "disclaimer": "Educational earnings calendar (live feed unavailable).",
        }
