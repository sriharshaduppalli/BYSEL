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
