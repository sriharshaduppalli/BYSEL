"""
Groq LLM integration for BYSEL AI assistant.
Runs open-source Llama / Gemma models on Groq's free inference API.
No credit card required — sign up at console.groq.com, set GROQ_API_KEY.
Falls back gracefully when the key is absent.
"""

import os
import re
import logging
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)

# Model to use — override via GROQ_MODEL env var.
# llama-3.1-8b-instant : 6 000 RPD, very fast (~0.5 s)
# llama-3.3-70b-versatile: 1 000 RPD, higher quality
_DEFAULT_MODEL = "llama-3.1-8b-instant"

_client = None

# ---------------------------------------------------------------------------
# Base system prompt (always included)
# ---------------------------------------------------------------------------
_BASE_SYSTEM_PROMPT = """You are BYSEL AI, an expert Indian stock market analyst assistant.

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
   - Confidence: [0-100]% (be specific — e.g. 72%, not "high confidence")
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
- USE the PRE-COMPUTED SIGNALS block (if provided) — these are already analyzed conclusions, weave them directly into your response.
Indian Market Context:
- Consider FII/DII flows, RBI decisions, rupee strength
- Account for monsoon, agricultural cycles, festive patterns
- Reference NIFTY 50, NIFTY Next 50 comparisons
"""

# ---------------------------------------------------------------------------
# Intent-specific system prompt addendums
# ---------------------------------------------------------------------------
_INTENT_PROMPTS: Dict[str, str] = {
    "PREDICT": """
--- PREDICTION FOCUS ---
The user wants a PRICE PREDICTION. Structure your response around:
1. SHORT-TERM (2-4 weeks): Price range with probability % (e.g. "65% chance of ₹1200–1350")
2. MEDIUM-TERM (3 months): Bull case / Base case / Bear case with specific price targets
3. KEY CATALYSTS: What triggers each scenario (earnings date, RBI policy, sector tailwind)
4. CONFIDENCE LEVEL: Specific % with reasoning (use technical + fundamental signals)
DO NOT say "it depends" — give a directional view with numbers. Users need actionable targets.
""",

    "COMPARE": """
--- COMPARISON FOCUS ---
The user wants to COMPARE two or more stocks. Structure your response as:
1. SIDE-BY-SIDE TABLE: Metric | Stock A | Stock B | Winner
   Include: Current Price, P/E, Market Cap, 52-week performance, RSI trend, Dividend
2. TECHNICAL winner (short-term momentum)
3. FUNDAMENTAL winner (long-term value)
4. VERDICT: Better for swing traders? For long-term SIP? Risk-averse investors?
Be decisive — name a clear winner for each category, not just "both have merits".
""",

    "BUY_SELL": """
--- BUY/SELL/HOLD FOCUS ---
The user wants a clear BUY / SELL / HOLD decision. Structure as:
1. VERDICT (first line): BUY at ₹[price] / SELL at ₹[price] / HOLD — one sentence reason
2. FOR (if BUY/HOLD): 3 specific data-backed reasons from the context
3. AGAINST (if SELL/HOLD): 3 specific risks
4. EXACT ENTRY ZONE: ₹[low] – ₹[high] (never skip)
5. STOP LOSS: ₹[level] — mandatory, calculated from support
6. TARGET: ₹[level] in [timeframe]
7. RISK/REWARD: [ratio]
8. WHO SHOULD BUY: Long-term investor / Swing trader / Avoid if [condition]
""",

    "TECHNICAL": """
--- TECHNICAL ANALYSIS FOCUS ---
Deep technical breakdown:
1. RSI [value]: Is it overbought/oversold? Any divergence visible?
2. MACD: Histogram direction, signal line crossover, bullish/bearish confirmation
3. BOLLINGER BANDS: Band position, squeeze (low volatility before move) or expansion
4. MOVING AVERAGES: All SMA crossovers (golden cross / death cross), MA as support/resistance
5. SUPPORT & RESISTANCE: Exact levels, how many times tested, strength
6. PATTERN: Any forming chart pattern (flag, triangle, H&S, double bottom)
7. SHORT-TERM OUTLOOK: Next 5-10 trading sessions — likely direction with probability
Refer to the PRE-COMPUTED SIGNALS for ready conclusions on each indicator.
""",

    "FUNDAMENTAL": """
--- FUNDAMENTAL ANALYSIS FOCUS ---
Deep fundamental breakdown:
1. VALUATION: P/E vs sector average and 5-year historical average — cheap/fair/expensive?
2. QUALITY: ROE, ROCE, Debt/Equity interpretation — strong/moderate/weak business?
3. GROWTH: Revenue and profit trend — accelerating/steady/declining?
4. PROMOTER & INSTITUTIONAL: Promoter holding %, pledging concern, FII/DII trend
5. DIVIDEND: History, payout ratio, sustainability
6. LONG-TERM VERDICT: 5-year investment thesis — accumulate/avoid/watch
Compare P/E signal from PRE-COMPUTED SIGNALS if available.
""",

    "SECTOR_SCREEN": """
--- SECTOR SCREENER FOCUS ---
The user wants stock recommendations from a sector. Provide:
1. SECTOR OUTLOOK: Current tailwinds and headwinds (RBI, monsoon, global factors)
2. TOP PICKS (3–5 stocks with brief reason for each):
   - Best for VALUE investors (low P/E, strong fundamentals)
   - Best for GROWTH investors (earnings momentum, expansion)
   - Best for SWING TRADERS (technical breakout setup)
3. AVOID (with specific reason — not just "risky")
4. SECTOR CATALYST to watch in next 30–60 days
""",

    "PORTFOLIO": """
--- PORTFOLIO ADVISORY FOCUS ---
The user is asking about portfolio strategy. Cover:
1. CURRENT ALLOCATION: Based on mentioned stocks/sectors, identify concentration risk
2. DIVERSIFICATION GAPS: Which sectors/market caps are missing?
3. REBALANCING SUGGESTION: What to trim (overvalued/overbought) and what to add
4. SIP RECOMMENDATION: Best SIP candidate from NIFTY 50 / quality mid-cap given current market
5. RISK PROFILE: Conservative / Moderate / Aggressive — tailor advice accordingly
""",

    "EDUCATIONAL": """
--- EDUCATIONAL FOCUS ---
The user wants to LEARN or UNDERSTAND a concept. Structure as:
1. SIMPLE EXPLANATION (1-2 sentences, no jargon)
2. TECHNICAL DEFINITION (precise, complete)
3. INDIAN MARKET EXAMPLE: Use a real NSE-listed stock to illustrate the concept
4. HOW TO USE IT: Practical application in daily trading/investing decisions
5. COMMON MISTAKES: What beginners get wrong about this concept
Keep it clear and actionable — the goal is understanding, not showing off terminology.
""",

    "GENERAL": "",

    "MULTI_STOCK": """
--- MULTI-STOCK COMPARISON ---
The user is comparing 2+ stocks. Structure your response:
1. SCORECARD TABLE: Stock | P/E | Market Cap | 52-week | RSI | Trend | Winner (per metric)
2. VERDICT PER CATEGORY:
   - Best for VALUE investors (lowest P/E, strong fundamentals)
   - Best for GROWTH investors (revenue growth, momentum)
   - Best for CONSERVATIVE (dividend yield, stability)
3. FINAL RECOMMENDATION: Which stock to pick given current market conditions
4. RISKS: Specific risk for each stock, not generic
Be comparative and specific — don't give generic analysis for each stock separately.
"""


# ---------------------------------------------------------------------------
# Intent classifier — pure Python, ~0ms, no extra LLM call
# ---------------------------------------------------------------------------
def classify_intent(query: str) -> dict:
    """
    Classify user query into one of 8 intents using keyword scoring.
    Returns: {
        "intent": "PREDICT|COMPARE|...|GENERAL",
        "confidence": 85,  # 0-100 confidence score
        "alternatives": [("TECHNICAL", 62), ("EDUCATIONAL", 45)]  # next 2 runner-ups
    }
    """
    q = query.lower()
    scores: Dict[str, int] = {
        "PREDICT": 0, "COMPARE": 0, "BUY_SELL": 0, "TECHNICAL": 0,
        "FUNDAMENTAL": 0, "SECTOR_SCREEN": 0, "PORTFOLIO": 0, "EDUCATIONAL": 0,
    }

    # PREDICT
    for kw in ["predict", "forecast", "will reach", "price target", "target price",
               "expected price", "future price", "next month", "next year", "bull case",
               "bear case", "upside potential", "downside potential", "will it go"]:
        if kw in q: scores["PREDICT"] += 2
    if re.search(r'\bwill\b.{0,30}\b(price|reach|go|touch|hit|cross)\b', q):
        scores["PREDICT"] += 3
    if re.search(r'\b(price\s+)?target\b', q):
        scores["PREDICT"] += 2

    # COMPARE
    for kw in ["vs ", "versus", "compare", "better than", "which is better",
               "difference between", "or between"]:
        if kw in q: scores["COMPARE"] += 2
    if re.search(r'\b\w+\s+(vs|versus)\s+\w+\b', q):
        scores["COMPARE"] += 4

    # BUY_SELL
    for kw in ["should i buy", "should i sell", "buy or sell", "good time to buy",
               "good to buy", "should i invest", "worth buying", "good investment",
               "is it safe to buy", "add more"]:
        if kw in q: scores["BUY_SELL"] += 3
    if re.search(r'\b(buy|sell|invest|entry|exit)\b', q):
        scores["BUY_SELL"] += 1

    # TECHNICAL
    for kw in ["rsi", "macd", "bollinger", "moving average", "sma", "ema",
               "support level", "resistance level", "chart", "technical analysis",
               "oversold", "overbought", "candlestick", "breakout setup", "golden cross",
               "death cross", "trend reversal", "price action"]:
        if kw in q: scores["TECHNICAL"] += 2

    # FUNDAMENTAL
    for kw in ["pe ratio", "p/e", "earnings", " eps", "revenue", "quarterly results",
               "fundamentals", "valuation", "roe", "roce", "debt", "balance sheet",
               "promoter", "pledging", "dividend yield", "payout"]:
        if kw in q: scores["FUNDAMENTAL"] += 2

    # SECTOR_SCREEN
    for kw in ["sector", "banking stocks", "pharma stocks", "it stocks", "fmcg stocks",
               "auto stocks", "defence stocks", "energy stocks", "nse listed", "screener"]:
        if kw in q: scores["SECTOR_SCREEN"] += 2
    if re.search(r'\b(top|best|good)\b.{0,20}\bstocks?\b', q):
        scores["SECTOR_SCREEN"] += 3

    # PORTFOLIO
    for kw in ["portfolio", "my holdings", "sip", "diversify", "allocation",
               "rebalance", "asset allocation", "my stocks", "long term investment"]:
        if kw in q: scores["PORTFOLIO"] += 3

    # EDUCATIONAL
    for kw in ["what is", "what are", "explain", "how does", "what does",
               "meaning of", "define", "understand", "how to calculate", "why is",
               "difference between rsi", "what is macd", "what is pe"]:
        if kw in q: scores["EDUCATIONAL"] += 2
    if re.search(r'\bwhat (is|are)\b', q):
        scores["EDUCATIONAL"] += 2

    # Get top 3
    sorted_intents = sorted(scores.items(), key=lambda x: -x[1])
    best = sorted_intents[0][0]
    best_score = sorted_intents[0][1]

    # Calculate confidence (0-100)
    if best_score == 0:
        confidence = 0
        best = "GENERAL"
    else:
        second_score = sorted_intents[1][1] if len(sorted_intents) > 1 else 0
        # Confidence based on gap between top and runner-up
        gap = best_score - second_score
        confidence = min(100, 50 + gap * 5)  # 50-100 scale

    alternatives = [(name, min(100, 50 + score * 5)) for name, score in sorted_intents[1:3]]

    return {
        "intent": best,
        "confidence": confidence,
        "alternatives": alternatives,
    }


# ---------------------------------------------------------------------------
# Groq client
# ---------------------------------------------------------------------------
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


# ---------------------------------------------------------------------------
# Context formatter — injects pre-computed signals prominently
# ---------------------------------------------------------------------------
def _format_context(context: Optional[Dict]) -> str:
    """Format structured rule-engine + enricher data into a Groq prompt context block."""
    if not context or not isinstance(context, dict):
        return ""

    parts = []

    # Extracted entities block (price targets, time horizons, etc.)
    entities = context.get("entities") or {}
    if entities:
        parts.append("EXTRACTED PARAMETERS (from your query):")
        if entities.get("price_target"):
            parts.append(f"  Price Target: ₹{entities['price_target']:,.0f}")
        if entities.get("price_level"):
            parts.append(f"  Price Level: {entities['price_level']}")
        if entities.get("duration"):
            parts.append(f"  Time Horizon: {entities['duration']}")
        if entities.get("next_period"):
            parts.append(f"  Next Period: {entities['next_period']}")
        if entities.get("percentage_target"):
            parts.append(f"  Expected Return: {entities['percentage_target']}")
        parts.append("")

    # Multi-stock comparison flag
    all_symbols = context.get("all_symbols") or []
    if len(all_symbols) > 1:
        parts.append(f"MULTI-STOCK COMPARISON: {' vs '.join(all_symbols)}")
        parts.append("")

    if context.get("symbol"):
        parts.append(f"STOCK: {context['symbol']}")
        if context.get("company_name"):
            parts.append(f"Company: {context['company_name']}")
        if context.get("sector"):
            parts.append(f"Sector: {context['sector']}")
        if context.get("current_price"):
            parts.append(f"Current Price: ₹{context['current_price']}")
        parts.append("")

    # Pre-computed signals block
    pre = context.get("pre_signals") or {}
    if pre:
        parts.append("PRE-COMPUTED SIGNALS (use these conclusions directly):")
        signal_keys = ["rsi_signal", "ma_signal", "week52_signal", "level_signal", "pe_signal"]
        for key in signal_keys:
            if pre.get(key):
                parts.append(f"  ► {pre[key]}")
        parts.append("")

    tech = context.get("technical") or {}
    if any(tech.values()):
        parts.append("TECHNICAL DATA (raw numbers):")
        for key, label in [
            ("rsi", "RSI"),
            ("macd", "MACD"),
            ("bollinger_bands", "Bollinger Bands"),
            ("moving_averages", "Moving Average Trend"),
            ("trend", "Overall Trend"),
        ]:
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
            parts.append(f"  P/E Ratio: {fund['pe_ratio']} (sector avg: {fund.get('pe_sector_avg', '~25')})")
        if fund.get("market_cap"):
            parts.append(f"  Market Cap: {fund['market_cap']}")
        if fund.get("dividend_yield"):
            parts.append(f"  Dividend Yield: {fund['dividend_yield']}")
        if fund.get("week_52"):
            parts.append(f"  52-Week Range: {fund['week_52']}")
        parts.append("")
    else:
        parts.append("FUNDAMENTAL DATA: Not available.")
        parts.append("")

    tl = context.get("trading_levels") or {}
    if any(tl.values()):
        parts.append("TRADING LEVELS:")
        if tl.get("support_1"):
            parts.append(f"  Support 1 (20-day): ₹{tl['support_1']}")
        if tl.get("support_2"):
            parts.append(f"  Support 2 (52-week): ₹{tl['support_2']}")
        if tl.get("resistance_1"):
            parts.append(f"  Resistance 1 (20-day): ₹{tl['resistance_1']}")
        if tl.get("resistance_2"):
            parts.append(f"  Resistance 2 (52-week): ₹{tl['resistance_2']}")
        if tl.get("stop_loss"):
            parts.append(f"  Stop Loss (if buying): ₹{tl['stop_loss']}")
        if tl.get("take_profit"):
            parts.append(f"  Take Profit Target: ₹{tl['take_profit']}")
        if tl.get("risk_reward"):
            parts.append(f"  Risk/Reward Ratio: {tl['risk_reward']}")
        parts.append("")
    else:
        parts.append("TRADING LEVELS: Not available.")
        parts.append("")

    sent = context.get("sentiment") or {}
    if any(sent.values()):
        parts.append("MARKET SENTIMENT:")
        if sent.get("overall"):
            parts.append(f"  News Sentiment: {sent['overall']}")
        if sent.get("breakdown"):
            parts.append(f"  Breakdown: {sent['breakdown']}")
        events = sent.get("recent_events") or []
        if events:
            parts.append("  Recent Events:")
            for e in events[:4]:
                parts.append(f"    • {e}")
        if sent.get("sector_trend"):
            parts.append(f"  Sector Trend: {sent['sector_trend']}")
        parts.append("")
    else:
        parts.append("SENTIMENT: Not available.")
        parts.append("")

    news = context.get("news_summary", "")
    if news:
        parts.append(news)
        parts.append("")

    parts.append("IMPORTANT: Base your analysis ONLY on the data above. Do not invent numbers.")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# Main ask function — intent-aware, conversation-aware
# ---------------------------------------------------------------------------
async def ask_groq(
    query: str,
    context: Optional[Dict] = None,
    conversation_history: Optional[List[Dict]] = None,
    intent_result: Optional[Dict] = None,  # {"intent": "X", "confidence": 85, "alternatives": [...]}
) -> Dict:
    """
    Send query to Groq with optional structured market context.
    - intent_result: from classify_intent() with confidence and alternatives
    - conversation_history: list of {"role": "user"|"assistant", "content": str}
    Returns {"answer": str, "source": "groq", "intent": str, "confidence": int} or {"error": str}.
    """
    client = _get_client()
    if client is None:
        return {"error": "Groq not configured"}

    model = os.environ.get("GROQ_MODEL", _DEFAULT_MODEL)

    # Parse intent result
    if not intent_result:
        intent_result = classify_intent(query)

    intent = intent_result.get("intent", "GENERAL")
    confidence = intent_result.get("confidence", 0)
    alternatives = intent_result.get("alternatives", [])

    # If confidence is low (<55%), combine top 2 intents in prompt
    if confidence < 55 and alternatives:
        alt_intent = alternatives[0][0]
        intent_addendum = _INTENT_PROMPTS.get(intent, "") + "\n\nALTERNATIVELY: " + _INTENT_PROMPTS.get(alt_intent, "")
    else:
        intent_addendum = _INTENT_PROMPTS.get(intent, "")

    # Multi-stock flag: if >1 symbol in context, use MULTI_STOCK prompt
    all_symbols = context.get("all_symbols", []) if context else []
    if len(all_symbols) > 1:
        intent_addendum = _INTENT_PROMPTS.get("MULTI_STOCK", "")
        intent = "MULTI_STOCK"

    # Build system prompt: base + intent-specific addendum
    system_prompt = _BASE_SYSTEM_PROMPT + intent_addendum

    # Build user content with market context
    user_content = ""
    if context and isinstance(context, dict):
        formatted = _format_context(context)
        if formatted:
            user_content = f"MARKET CONTEXT:\n{formatted}\n\n"
    user_content += f"User query: {query}"

    # Build messages array
    messages = [{"role": "system", "content": system_prompt}]
    if conversation_history:
        for turn in conversation_history[-4:]:  # last 4 turns (2 exchanges)
            role = turn.get("role", "")
            content = turn.get("content", "")
            if role in ("user", "assistant") and content:
                messages.append({"role": role, "content": str(content)[:800]})

    messages.append({"role": "user", "content": user_content})

    # Tune temperature per intent
    temperature = 0.5 if intent in ("PREDICT", "SECTOR_SCREEN", "MULTI_STOCK") else 0.35

    try:
        response = await client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=temperature,
            max_tokens=2048,
        )
        text = response.choices[0].message.content or ""
        text = text.strip()
        if not text:
            return {"error": "Empty response from Groq"}
        return {
            "answer": text,
            "source": "groq",
            "intent": intent,
            "confidence": confidence,
        }
    except Exception as e:
        logger.error("Groq API error: %s", e)
        return {"error": f"Groq error: {str(e)}"}
