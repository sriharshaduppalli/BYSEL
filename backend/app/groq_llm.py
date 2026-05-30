"""
Groq LLM integration for BYSEL AI assistant.
Runs open-source Llama / Gemma models on Groq's free inference API.
No credit card required — sign up at console.groq.com, set GROQ_API_KEY.
Falls back gracefully when the key is absent.
"""

import os
import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)

# Model to use — override via GROQ_MODEL env var.
# llama-3.1-8b-instant : 6 000 RPD, very fast (~0.5 s)
# llama-3.3-70b-versatile: 1 000 RPD, higher quality
_DEFAULT_MODEL = "llama-3.1-8b-instant"

_client = None

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

4. TRADING LEVELS (MUST include):
   - Support 1: ₹[level]
   - Resistance 1: ₹[level]
   - Stop Loss (if buying): ₹[level]
   - Take Profit Target (1-month): ₹[target]
   - Risk/Reward Ratio: [calculated]

5. MARKET SENTIMENT (MUST include):
   - News Sentiment: breakdown
   - Recent Events: earnings, splits, FII/DII flows
   - Sector Trend: bullish/neutral/bearish

6. SIGNAL & RECOMMENDATION (MUST include):
   - PRIMARY SIGNAL: BUY / SELL / HOLD
   - Confidence: [0-100]% (be specific)
   - Why Confident: 2-3 key reasons with specific data points
   - Key Risks: 2-3 downside risks
   - Time Horizon: day trade / swing / 1-month / 3-month / long-term

7. DISCLAIMER: "Not financial advice. Do your own research. Consult a registered advisor."

CRITICAL RULES:
- ONLY use numbers from the provided context data. Never invent RSI, P/E, price levels.
- If a data field is missing, write "Data not available" — never make up a number.
- Use Indian market terminology (NSE, BSE, NIFTY, SENSEX).
- Format prices in ₹ with Indian number system (₹1,23,456).
- Support Hinglish queries naturally — respond in the same language mix used by the user.
- Confidence scores must be specific (78%, not "quite confident").
- If signals conflict, explicitly highlight it.
Indian Market Context:
- Consider FII/DII flows, RBI decisions, rupee strength
- Account for monsoon, agricultural cycles, festive patterns
- Reference NIFTY 50, NIFTY Next 50 comparisons
"""


def _get_client():
    global _client
    if _client is not None:
        return _client

    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        logger.info("GROQ_API_KEY not set — Groq LLM disabled")
        return None

    try:
        from groq import AsyncGroq
        _client = AsyncGroq(api_key=api_key)
        model = os.environ.get("GROQ_MODEL", _DEFAULT_MODEL)
        logger.info("Groq client initialised (model=%s)", model)
        return _client
    except Exception as e:
        logger.error("Failed to init Groq client: %s", e)
        return None


def groq_available() -> bool:
    return _get_client() is not None


def _format_context(context: Optional[Dict]) -> str:
    """Format structured rule-engine data into a Groq prompt context block."""
    if not context or not isinstance(context, dict):
        return ""

    parts = []

    if context.get("symbol"):
        parts.append(f"STOCK: {context['symbol']}")
        if context.get("company_name"):
            parts.append(f"Company: {context['company_name']}")
        if context.get("sector"):
            parts.append(f"Sector: {context['sector']}")
        if context.get("current_price"):
            parts.append(f"Current Price: ₹{context['current_price']}")
        parts.append("")

    tech = context.get("technical") or {}
    if any(tech.values()):
        parts.append("TECHNICAL DATA:")
        for key, label in [("rsi", "RSI"), ("macd", "MACD"), ("bollinger_bands", "Bollinger Bands"),
                           ("moving_averages", "Moving Averages"), ("trend", "Trend")]:
            if tech.get(key):
                parts.append(f"  {label}: {tech[key]}")
        parts.append("")
    else:
        parts.append("TECHNICAL DATA: Not available.")
        parts.append("")

    fund = context.get("fundamental") or {}
    if any(fund.values()):
        parts.append("FUNDAMENTAL DATA:")
        if fund.get("pe_ratio"):
            parts.append(f"  P/E Ratio: {fund['pe_ratio']} (sector avg: {fund.get('pe_sector_avg', 'N/A')})")
        if fund.get("market_cap"):
            parts.append(f"  Market Cap: {fund['market_cap']}")
        if fund.get("week_52"):
            parts.append(f"  52-Week: {fund['week_52']}")
        parts.append("")
    else:
        parts.append("FUNDAMENTAL DATA: Not available.")
        parts.append("")

    tl = context.get("trading_levels") or {}
    if any(tl.values()):
        parts.append("TRADING LEVELS:")
        for key, label in [("support_1", "Support 1"), ("resistance_1", "Resistance 1"),
                           ("stop_loss", "Stop Loss"), ("take_profit", "Take Profit")]:
            if tl.get(key):
                parts.append(f"  {label}: ₹{tl[key]}")
        parts.append("")

    sent = context.get("sentiment") or {}
    if any(sent.values()):
        parts.append("SENTIMENT:")
        if sent.get("overall"):
            parts.append(f"  Overall: {sent['overall']}")
        if sent.get("breakdown"):
            parts.append(f"  Breakdown: {sent['breakdown']}")
        parts.append("")

    parts.append("IMPORTANT: Base your analysis ONLY on the data above. Do not invent numbers.")
    return "\n".join(parts)


async def ask_groq(query: str, context: Optional[Dict] = None) -> Dict:
    """
    Send query to Groq with optional structured market context.
    Returns {"answer": str, "source": "groq"} or {"error": str}.
    """
    client = _get_client()
    if client is None:
        return {"error": "Groq not configured"}

    model = os.environ.get("GROQ_MODEL", _DEFAULT_MODEL)

    user_content = ""
    if context and isinstance(context, dict):
        formatted = _format_context(context)
        if formatted:
            user_content = f"MARKET CONTEXT:\n{formatted}\n\n"
    user_content += f"User query: {query}"

    try:
        response = await client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_content},
            ],
            temperature=0.4,
            max_tokens=2048,
        )
        text = response.choices[0].message.content or ""
        text = text.strip()
        if not text:
            return {"error": "Empty response from Groq"}
        return {"answer": text, "source": "groq"}
    except Exception as e:
        logger.error("Groq API error: %s", e)
        return {"error": f"Groq error: {str(e)}"}
