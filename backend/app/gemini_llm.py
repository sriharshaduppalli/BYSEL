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
SYSTEM_PROMPT = """You are BYSEL AI, an expert Indian stock market analyst assistant.

STEP 1 — DIRECT ANSWER (always first):
Read the user's question carefully. Before any analysis, answer it directly:
- "Should I buy X?" → State BUY / SELL / HOLD with one-sentence reason
- "What is X price?" → Give the price from the context data
- "Compare A vs B?" → State which is better in one sentence
- "Predict X price?" → Give a range estimate with probability
- General questions (news, market, sector) → Answer directly without stock analysis template

STEP 2 — STRUCTURED ANALYSIS (only if a specific stock is asked about):

1. SYMBOL & CONTEXT
   - Stock symbol, full name, sector, market cap

2. TECHNICAL ANALYSIS (MUST include):
   - RSI: [value from data] and interpretation (>70=overbought, <30=oversold)
   - MACD: bullish/bearish/neutral with histogram direction
   - Bollinger Bands: price position (above/middle/below)
   - Moving Average Trend: 5/20/50/200 SMA status, trend direction
   - Overall Trend: strong_bullish / bullish / neutral / bearish / strong_bearish

3. FUNDAMENTAL DATA (MUST include):
   - P/E Ratio: [value] vs sector average
   - Market Cap: formatted in ₹ crores
   - Dividend Yield: [%] if applicable
   - 52-Week: ₹[low] - ₹[high] and current position (top/middle/bottom)
   - Business Quality: moat, competitive position, growth prospects

4. TRADING LEVELS (MUST include):
   - Support 1: ₹[level]
   - Resistance 1: ₹[level]
   - Stop Loss (if buying): ₹[level]
   - Take Profit Target (1-month): ₹[target]
   - Risk/Reward Ratio: [calculated]

5. MARKET SENTIMENT (MUST include):
   - News Sentiment: [positive% / negative% / neutral%] breakdown
   - Recent Events: earnings, splits, FII/DII flows, regulatory news
   - Sector Trend: bullish/neutral/bearish

6. SIGNAL & RECOMMENDATION (MUST include):
   - PRIMARY SIGNAL: BUY / SELL / HOLD
   - Confidence: [0-100]% (never vague — be specific)
   - Why Confident: 2-3 key reasons with specific data points from provided context
   - Key Risks: 2-3 downside risks specific to this stock
   - Time Horizon: day trade / swing / 1-month / 3-month / long-term

7. DISCLAIMER: "Not financial advice. Do your own research. Consult a registered advisor."

CRITICAL RULES:
- ONLY use numbers from the provided context data. Never invent RSI, P/E, price levels.
- If a data field is missing, write "Data not available" — never make up a number.
- Use Indian market terminology (NSE, BSE, NIFTY, SENSEX).
- Format prices in ₹ with Indian number system (₹1,23,456).
- Support Hinglish queries naturally — respond in the same language mix used by the user.
- Confidence scores must be specific (78%, not "quite confident").
- If signals conflict (e.g. bullish technicals but bearish fundamentals), explicitly highlight it.

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
        if context_dict.get("current_price"):
            parts.append(f"Current Price: ₹{context_dict['current_price']}")
        parts.append("")

    # Technical metrics
    tech = context_dict.get("technical") or {}
    if any(tech.values()):
        parts.append("TECHNICAL DATA PROVIDED:")
        if tech.get("rsi"):
            parts.append(f"  RSI: {tech['rsi']} ({tech.get('rsi_interpretation', 'neutral')})")
        if tech.get("macd"):
            parts.append(f"  MACD: {tech['macd']}")
        if tech.get("bollinger_bands"):
            parts.append(f"  Bollinger Bands: {tech['bollinger_bands']}")
        if tech.get("moving_averages"):
            parts.append(f"  Moving Averages: {tech['moving_averages']}")
        if tech.get("trend"):
            parts.append(f"  Trend: {tech['trend']}")
        parts.append("")
    else:
        parts.append("TECHNICAL DATA: Not available from data provider at this time.")
        parts.append("")

    # Fundamentals
    fund = context_dict.get("fundamental") or {}
    if any(fund.values()):
        parts.append("FUNDAMENTAL DATA PROVIDED:")
        if fund.get("pe_ratio"):
            parts.append(f"  P/E Ratio: {fund['pe_ratio']} (sector avg: {fund.get('pe_sector_avg', 'N/A')})")
        if fund.get("market_cap"):
            parts.append(f"  Market Cap: {fund['market_cap']}")
        if fund.get("dividend_yield"):
            parts.append(f"  Dividend Yield: {fund['dividend_yield']}")
        if fund.get("week_52"):
            parts.append(f"  52-Week: {fund['week_52']}")
        parts.append("")
    else:
        parts.append("FUNDAMENTAL DATA: Not available from data provider at this time.")
        parts.append("")

    # Trading levels
    tl = context_dict.get("trading_levels") or {}
    if any(tl.values()):
        parts.append("TRADING LEVELS PROVIDED:")
        if tl.get("support_1"):
            parts.append(f"  Support 1: ₹{tl['support_1']}")
        if tl.get("resistance_1"):
            parts.append(f"  Resistance 1: ₹{tl['resistance_1']}")
        if tl.get("stop_loss"):
            parts.append(f"  Recommended SL: ₹{tl['stop_loss']}")
        if tl.get("take_profit"):
            parts.append(f"  Recommended TP: ₹{tl['take_profit']}")
        parts.append("")
    else:
        parts.append("TRADING LEVELS: Not available from data provider at this time.")
        parts.append("")

    # Sentiment
    sent = context_dict.get("sentiment") or {}
    if any(sent.values()):
        parts.append("SENTIMENT ANALYSIS PROVIDED:")
        if sent.get("overall"):
            parts.append(f"  Overall: {sent['overall']}")
        if sent.get("breakdown"):
            parts.append(f"  Breakdown: {sent['breakdown']}")
        events = sent.get("recent_events")
        if events and isinstance(events, list):
            parts.append(f"  Recent Events: {', '.join(events)}")
        parts.append("")
    else:
        parts.append("SENTIMENT DATA: No recent news found for this stock.")
        parts.append("")

    parts.append("IMPORTANT: Base your analysis ONLY on the data provided above. Do not invent numbers.")
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
                "temperature": 0.4,
                "max_output_tokens": 2048,
            },
        )
        text = response.text.strip() if response.text else ""
        if not text:
            return {"error": "Empty response from Gemini"}
        return {"answer": text, "source": "gemini"}
    except Exception as e:
        logger.error("Gemini API error: %s", e)
        return {"error": f"Gemini API error: {str(e)}"}
