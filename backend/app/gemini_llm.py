"""
Gemini LLM integration for BYSEL AI assistant.
Provides natural-language stock analysis powered by Google Gemini,
falling back to the rule-based ai_engine when the API key is not set.
"""

import os
import logging
import json
from typing import Dict, Optional

logger = logging.getLogger(__name__)

_model = None

# Enhanced system prompt with detailed structural requirements
SYSTEM_PROMPT = """You are BYSEL AI, an expert Indian stock market analyst.

CRITICAL: For EVERY stock query, provide STRUCTURED analysis with these MANDATORY sections:

1. SYMBOL & CONTEXT
   - Stock symbol, full name, sector, market cap

2. TECHNICAL ANALYSIS (MUST include):
   - RSI: [0-100] value and interpretation (>70=overbought, <30=oversold)
   - MACD: bullish/bearish/neutral with histogram direction
   - Bollinger Bands: price position (above/middle/below)
   - Moving Average Trend: 5/20/50/200 SMA status, trend direction
   - Overall Trend: strong_bullish / bullish / neutral / bearish / strong_bearish

3. FUNDAMENTAL DATA (MUST include):
   - P/E Ratio: [value] vs sector average
   - Market Cap: formatted in ₹ crores
   - Dividend Yield: [%] if applicable
   - 52-Week: ₹[low] - ₹[high] and current position (top/middle/bottom)
   - Earnings Next: [date if known]
   - Business Quality: moat, competitive position, growth prospects

4. TRADING LEVELS (MUST include):
   - Support 1: ₹[level] (recent support)
   - Resistance 1: ₹[level] (recent resistance)
   - Stop Loss (if buying): ₹[level]
   - Take Profit Target (1-month): ₹[target]
   - Risk/Reward Ratio: [calculated]

5. MARKET SENTIMENT (MUST include):
   - News Sentiment: [positive% / negative% / neutral%] breakdown
   - Recent Events: earnings, splits, FII/DII flows, regulatory news
   - Sector Trend: bullish/neutral/bearish
   - Sentiment Impact: how it affects price momentum

6. SIGNAL & RECOMMENDATION (MUST include):
   - PRIMARY SIGNAL: BUY / SELL / HOLD
   - Confidence: [0-100]% (never vague - be specific)
   - Why Confident: 2-3 key reasons with specific data points
   - Key Risks: 2-3 downside risks specific to this stock
   - Time Horizon: day trade / swing / 1-month / 3-month / long-term

7. DISCLAIMER: "Not financial advice. Do your own research. Consult a registered advisor."

FORMATTING RULES:
- Use Indian market terminology (NSE, BSE, NIFTY, SENSEX, etc.)
- Format prices in ₹ with Indian number system (₹1,23,456)
- Support Hinglish queries naturally
- If ANY required data is missing, EXPLICITLY STATE "Data not available: [field]"
- Never omit a section - even if data unavailable, mention it
- Confidence scores must be specific (78%, not "quite confident")
- Give probability-weighted ranges for predictions, never single-point forecasts

CRITICAL: If you notice conflicting signals (e.g., bullish technicals but bearish fundamentals),
explicitly highlight this conflict and explain the trade-off.

Indian Market Context:
- Consider FII/DII flows, RBI decisions, rupee strength
- Account for monsoon, agricultural cycles, festive patterns
- Reference NIFTY 50, NIFTY Next 50 comparisons
- Be aware of GST, regulatory changes specific to India
"""


def _get_model():
    """Lazy-initialize the Gemini model."""
    global _model
    if _model is not None:
        return _model

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        logger.info("GEMINI_API_KEY not set – LLM features disabled")
        return None

    try:
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        _model = genai.GenerativeModel(
            "gemini-2.0-flash",
            system_instruction=SYSTEM_PROMPT,
        )
        logger.info("Gemini model initialized successfully")
        return _model
    except Exception as e:
        logger.error("Failed to initialize Gemini: %s", e)
        return None


def gemini_available() -> bool:
    """Check if Gemini is configured and reachable."""
    return _get_model() is not None


def _format_context_for_gemini(context_dict: Optional[Dict]) -> str:
    """Format structured context dictionary into clear Gemini prompt."""
    if not context_dict or not isinstance(context_dict, dict):
        return ""

    parts = []

    # Symbol and company info
    if context_dict.get("symbol"):
        parts.append(f"STOCK: {context_dict['symbol']}")
        if context_dict.get("company_name"):
            parts.append(f"Company: {context_dict['company_name']}")
        if context_dict.get("sector"):
            parts.append(f"Sector: {context_dict['sector']}")
        parts.append("")

    # Technical metrics
    if context_dict.get("technical"):
        tech = context_dict["technical"]
        parts.append("TECHNICAL DATA PROVIDED:")
        if "rsi" in tech:
            parts.append(f"  RSI: {tech['rsi']} (interpretation: {tech.get('rsi_interpretation', 'neutral')})")
        if "macd" in tech:
            parts.append(f"  MACD: {tech['macd']}")
        if "bollinger_bands" in tech:
            parts.append(f"  Bollinger Bands: {tech['bollinger_bands']}")
        if "moving_averages" in tech:
            parts.append(f"  Moving Averages: {tech['moving_averages']}")
        if "trend" in tech:
            parts.append(f"  Trend: {tech['trend']}")
        parts.append("")

    # Fundamentals
    if context_dict.get("fundamental"):
        fund = context_dict["fundamental"]
        parts.append("FUNDAMENTAL DATA PROVIDED:")
        if "pe_ratio" in fund:
            parts.append(f"  P/E Ratio: {fund['pe_ratio']} (sector avg: {fund.get('pe_sector_avg', 'N/A')})")
        if "market_cap" in fund:
            parts.append(f"  Market Cap: {fund['market_cap']}")
        if "dividend_yield" in fund:
            parts.append(f"  Dividend Yield: {fund['dividend_yield']}")
        if "week_52" in fund:
            parts.append(f"  52-Week: {fund['week_52']}")
        parts.append("")

    # Trading levels
    if context_dict.get("trading_levels"):
        tl = context_dict["trading_levels"]
        parts.append("TRADING LEVELS PROVIDED:")
        if "support_1" in tl:
            parts.append(f"  Support 1: ₹{tl['support_1']}")
        if "resistance_1" in tl:
            parts.append(f"  Resistance 1: ₹{tl['resistance_1']}")
        if "stop_loss" in tl:
            parts.append(f"  Recommended SL: ₹{tl['stop_loss']}")
        if "take_profit" in tl:
            parts.append(f"  Recommended TP: ₹{tl['take_profit']}")
        parts.append("")

    # Sentiment
    if context_dict.get("sentiment"):
        sent = context_dict["sentiment"]
        parts.append("SENTIMENT ANALYSIS PROVIDED:")
        if "overall" in sent:
            parts.append(f"  Overall: {sent['overall']}")
        if "breakdown" in sent:
            parts.append(f"  Breakdown: {sent['breakdown']}")
        if "recent_events" in sent:
            parts.append(f"  Recent Events: {', '.join(sent['recent_events'])}")
        parts.append("")

    parts.append("Based on the provided data above, structure your response exactly as specified in your instructions.")
    return "\n".join(parts)


async def ask_gemini(query: str, context: Optional[Dict] = None) -> Dict:
    """
    Send a query to Gemini with structured context and return the response.

    Args:
        query: User's natural language query
        context: Optional Dict with 'technical', 'fundamental', 'trading_levels', 'sentiment' keys
                 (replaces old string-based context)

    Returns:
        {"answer": str, "source": "gemini"} on success, or {"error": str} on failure.
    """
    model = _get_model()
    if model is None:
        return {"error": "Gemini not configured"}

    prompt_parts = []

    # Format structured context if provided
    if context and isinstance(context, dict):
        formatted_context = _format_context_for_gemini(context)
        if formatted_context:
            prompt_parts.append(f"MARKET CONTEXT:\n{formatted_context}\n\n")
    # Fallback: support legacy string-based context
    elif context and isinstance(context, str):
        prompt_parts.append(f"Market context:\n{context}\n\n")

    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    try:
        response = await model.generate_content_async(
            full_prompt,
            generation_config={
                "temperature": 0.7,
                "max_output_tokens": 2048,  # Increased for structured analysis
            },
        )
        text = response.text.strip() if response.text else ""
        if not text:
            return {"error": "Empty response from Gemini"}
        return {"answer": text, "source": "gemini"}
    except Exception as e:
        logger.error("Gemini API error: %s", e)
        return {"error": f"Gemini API error: {str(e)}"}
