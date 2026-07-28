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

STEP 0 — UNDERSTAND THE QUERY:
- Greetings / chitchat ("hi", "hello", "thanks") → reply briefly as BYSEL AI. Do NOT invent a stock analysis.
- Sector / theme queries ("defence stocks", "best pharma stocks") → answer with that sector's names.
  Prefer SCREENED RESULTS in context when present. Do NOT analyze a random unrelated ticker from wallet/context.
- Only run a single-stock technical/fundamental template when the user clearly asked about one company/symbol.

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

IMPORTANT — USER-FACING RESPONSE ONLY:
- Do NOT include any internal system metadata in your response
- Do NOT mention: "Intent detected", "category", "latency mode", "model backend", "confidence score", "alternatives", "data refresh", "data lineage", "stale feeds", "partial feeds", "resolved entity", or any other internal NLP/system information
- Do NOT start your response with any metadata or system fields
- Only provide analysis and investment advice to the user
- Begin directly with the analysis or answer — no preamble about how you processed the query
- Keep responses professional and user-focused
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

CRITICAL:
- If SCREENED RESULTS are present in context, use those symbols first.
- For Defence/Aerospace in India prefer HAL, BEL, BDL, MAZDOCK, COCHINSHIP, GRSE, DATAPATTNS.
- Do NOT invent unrelated Nifty names (like Reliance/TCS) for a defence query.
- Do NOT run a single-stock RSI/MACD template unless the user named one company.
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
The user wants to LEARN a stock-market concept, term, or equation. Structure as:
1. SIMPLE EXPLANATION (1-2 sentences, plain English)
2. DEFINITION / EQUATION: If the concept has a formula, show it clearly, e.g.
   - RSI = 100 - (100 / (1 + RS)) where RS = Avg Gain / Avg Loss
   - P/E = Price / EPS
   - CAGR = (Ending / Beginning)^(1/years) - 1
   Define every variable.
3. WORKED EXAMPLE with a real NSE name (e.g. RELIANCE, TCS) or sample numbers
4. HOW TO USE IT in trading/investing decisions
5. COMMON MISTAKES beginners make
Never invent a stock recommendation when the user only asked for a definition or formula.
""",

    "CALCULATION": """
--- CALCULATION FOCUS ---
The user is asking for a market calculation or formula-driven output.
1. Show the exact formula first (e.g., CAGR, P/E, stop-loss %, risk/reward, returns)
2. Define each variable with the user's values
3. Show step-by-step math clearly
4. Provide the final numeric result with units (% / ₹)
5. If any required input is missing, ask specifically for that input instead of guessing
If they only ask "what is the formula for X" without numbers, teach the equation (educational style) instead of refusing.
""",

    "SMALL_TALK": """
--- SMALL TALK FOCUS ---
Keep response short, warm, and direct. No stock analysis unless user asks for one.
""",

    "DERIVATIVES": """
--- DERIVATIVES FOCUS ---
The user is asking about futures/options or derivatives positioning.
1. Identify instrument type clearly (futures/call/put/spread)
2. Explain key drivers: IV, OI build-up, PCR, max pain, Greeks where relevant
3. Include risk framing: leverage risk, theta decay, stop-loss discipline
4. If strategy is requested, provide entry zone, invalidation, and target logic
5. If required inputs are missing (expiry/strike/premium), ask for them explicitly
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
""",

    "COMPARE_CONCEPTS": """
--- CONCEPT COMPARISON ---
The user is comparing investment concepts, strategies, or asset classes (not specific stocks).
1. DEFINITION: Clearly define each concept in simple language (1-2 sentences each)
2. COMPARISON TABLE: Feature | Concept A | Concept B | Better For
   Include: Risk level, Time horizon, Returns potential, Volatility, Effort required, Suitable for whom
3. PROS & CONS: List 3 pros and 3 cons for each concept
4. WHO SHOULD CHOOSE WHAT: Give specific recommendations based on investor type (beginner/experienced, aggressive/conservative, short/long-term)
5. VERDICT: Which concept suits different investor goals
Be educational and practical — help user make an informed decision based on their profile.
""",
}


# ---------------------------------------------------------------------------
# Acronym expansion — expand Indian financial terms before NLP processing
# ---------------------------------------------------------------------------
_ACRONYMS = {
    "FII": "Foreign Institutional Investors",
    "DII": "Domestic Institutional Investors",
    "RBI": "Reserve Bank of India",
    "NSE": "National Stock Exchange",
    "BSE": "Bombay Stock Exchange",
    "NIFTY": "NIFTY 50 index",
    "SENSEX": "BSE 30 index",
    "CAGR": "Compound Annual Growth Rate",
    "P/E": "Price-to-Earnings ratio",
    "PE": "Price-to-Earnings ratio",
    "ROE": "Return on Equity",
    "ROCE": "Return on Capital Employed",
    "EPS": "Earnings Per Share",
    "IPO": "Initial Public Offering",
    "FPO": "Follow-on Public Offering",
    "F&O": "Futures & Options",
    "SIP": "Systematic Investment Plan",
    "ELSS": "Equity Linked Savings Scheme",
    "PB": "Price-to-Book ratio",
    "PEG": "Price/Earnings to Growth ratio",
    "MACD": "Moving Average Convergence Divergence",
    "RSI": "Relative Strength Index",
    "BB": "Bollinger Bands",
    "SMA": "Simple Moving Average",
    "EMA": "Exponential Moving Average",
    "MNC": "Multi-National Corporation",
    "EV": "Enterprise Value",
    "EBITDA": "Earnings Before Interest Taxes Depreciation and Amortization",
    "PAT": "Profit After Tax",
    "PBT": "Profit Before Tax",
    "TTM": "Trailing Twelve Months",
    "YoY": "Year-over-Year",
    "QoQ": "Quarter-over-Quarter",
    "OI": "Open Interest",
    "PCR": "Put-Call Ratio",
    "IV": "Implied Volatility",
    "ATR": "Average True Range",
    "VWAP": "Volume Weighted Average Price",
    "CMP": "Current Market Price",
    "SL": "Stop Loss",
    "TP": "Take Profit",
    "CE": "Call Option",
    "PE2": "Put Option",
    "MTM": "Mark to Market",
    "MIS": "Margin Intraday Square-off",
    "CNC": "Cash and Carry delivery",
    "NRML": "Normal margin product",
    "GTT": "Good Till Triggered",
}


_MARKET_TERM_NORMALIZATION = {
    "ce": "call option",
    "pe": "put option",
    "oi": "open interest",
    "iv": "implied volatility",
    "atm": "at the money",
    "otm": "out of the money",
    "itm": "in the money",
    "sl": "stop loss",
    "tp": "take profit",
    "cmp": "current market price",
    "rr": "risk reward",
    "btst": "buy today sell tomorrow",
    "stbt": "sell today buy tomorrow",
    "fno": "futures and options",
    "fo": "futures and options",
    "cal spread": "calendar spread",
    "cal-spread": "calendar spread",
    "time spread": "calendar spread",
    "bfly": "butterfly spread",
    "butterfly": "butterfly spread",
    "ratio sprd": "ratio spread",
    "ratio-spread": "ratio spread",
    "cov call": "covered call",
    "prot put": "protective put",
    "hedge put": "protective put",
    "kitna": "how much",
    "kaisa": "how",
    "kaise": "how",
    "kya": "what",
    "matlab": "meaning",
}


_OPTIONS_STRATEGY_PATTERNS = {
    "calendar_spread": ["calendar spread", "time spread", "cal spread", "cal-spread"],
    "butterfly_spread": ["butterfly spread", "bfly", "butterfly"],
    "ratio_spread": ["ratio spread", "ratio sprd", "ratio-spread"],
    "covered_call": ["covered call", "cov call"],
    "protective_put": ["protective put", "prot put", "hedge put"],
}


_OPTIONS_STRATEGY_PROMPTS = {
    "calendar_spread": """
OPTIONS STRATEGY TEMPLATE — CALENDAR SPREAD:
1. MARKET VIEW: neutral to mildly directional with expected IV/range behavior
2. STRUCTURE: near-expiry short option + farther-expiry long option at/near same strike
3. ENTRY CHECKLIST: ATM/near-ATM strike choice, event risk, liquidity, spread cost
4. RISK PLAN: max loss limited to net debit; specify invalidation condition
5. MANAGEMENT: adjustments if price moves sharply before front expiry
6. EXIT FRAMEWORK: profit-taking zone and time-based exit before major decay risk
""",
    "butterfly_spread": """
OPTIONS STRATEGY TEMPLATE — BUTTERFLY SPREAD:
1. MARKET VIEW: range-bound around target strike into expiry
2. STRUCTURE: long wing + short 2 middle strikes + long opposite wing
3. ENTRY CHECKLIST: center strike selection, cost vs max reward, breakeven bands
4. RISK PLAN: capped risk and capped reward; highlight pin risk near expiry
5. MANAGEMENT: what to do if spot trends away from center strike
6. EXIT FRAMEWORK: take profit when reward-to-remaining-risk deteriorates
""",
    "ratio_spread": """
OPTIONS STRATEGY TEMPLATE — RATIO SPREAD:
1. MARKET VIEW: directional bias with controlled premium objective
2. STRUCTURE: unequal long/short option quantities (e.g., 1x2) with defined strikes
3. ENTRY CHECKLIST: margin impact, assignment risk, tail-risk scenario
4. RISK PLAN: specify worst-case zone and hard adjustment trigger
5. MANAGEMENT: delta control and hedge action if move accelerates
6. EXIT FRAMEWORK: profit target, risk cut, and expiry management
""",
    "covered_call": """
OPTIONS STRATEGY TEMPLATE — COVERED CALL:
1. MARKET VIEW: mildly bullish to neutral on owned stock
2. STRUCTURE: long stock + short OTM call
3. ENTRY CHECKLIST: strike above desired sell price, annualized yield from premium
4. RISK PLAN: downside remains stock risk; premium only partially buffers losses
5. MANAGEMENT: roll-up/roll-out criteria if stock rallies
6. EXIT FRAMEWORK: assignment decision, buyback threshold, dividend/event handling
""",
    "protective_put": """
OPTIONS STRATEGY TEMPLATE — PROTECTIVE PUT:
1. MARKET VIEW: bullish long stock but near-term downside protection needed
2. STRUCTURE: long stock + long put (insurance)
3. ENTRY CHECKLIST: hedge horizon, strike distance, premium budget
4. RISK PLAN: effective floor price after premium cost
5. MANAGEMENT: roll put when time/value decays or volatility regime shifts
6. EXIT FRAMEWORK: unwind criteria when risk event passes
""",
}


_OPTIONS_STRATEGY_EXAMPLES = {
    "calendar_spread": {
        "concise": (
            "CONCISE EXAMPLE:\n"
            "View: Neutral to mildly bullish into front expiry.\n"
            "Setup: Sell near-expiry ATM call, buy next-expiry same strike call.\n"
            "Risk: Max loss = net debit paid; manage if spot breaks range strongly."
        ),
        "detailed": (
            "DETAILED EXAMPLE:\n"
            "Market View: Spot likely to stay near strike this week, IV can stay supported near event.\n"
            "Structure: +1 next-expiry ATM call, -1 near-expiry ATM call (same strike).\n"
            "Entry Logic: Prefer liquid strike, avoid wide bid-ask, enter when debit is acceptable vs expected front-theta decay.\n"
            "Risk Management: Hard invalidation if spot trends sharply away; cap loss at planned debit threshold.\n"
            "Management: If front option decays well and spot remains near strike, consider rolling short leg.\n"
            "Exit: Partial/Full book when spread reaches target return or before event volatility crush risk."
        ),
    },
    "butterfly_spread": {
        "concise": (
            "CONCISE EXAMPLE:\n"
            "View: Range-bound near target strike.\n"
            "Setup: Buy lower strike, sell 2 middle strikes, buy upper strike (same expiry).\n"
            "Risk: Limited loss and limited reward; avoid over-holding near expiry pin risk."
        ),
        "detailed": (
            "DETAILED EXAMPLE:\n"
            "Market View: Expect price to gravitate near center strike into expiry.\n"
            "Structure: +1 lower strike call, -2 center strike calls, +1 upper strike call.\n"
            "Entry Logic: Choose wing width by expected move; ensure payoff peak aligns with likely settlement zone.\n"
            "Risk Management: Max loss is net debit; monitor if price escapes breakeven band early.\n"
            "Management: If trend starts, cut/adjust before theta decay works against center pin thesis.\n"
            "Exit: Book profits once reward-to-risk compresses near the peak zone."
        ),
    },
    "ratio_spread": {
        "concise": (
            "CONCISE EXAMPLE:\n"
            "View: Directional but controlled premium objective.\n"
            "Setup: Example 1x2 ratio spread with defined strikes.\n"
            "Risk: Watch tail-risk zone and margin expansion closely."
        ),
        "detailed": (
            "DETAILED EXAMPLE:\n"
            "Market View: Moderate directional move expected, not extreme runaway.\n"
            "Structure: Long 1 option, short 2 farther strike options (same expiry) with clear ratio.\n"
            "Entry Logic: Select strikes where expected move stays in profit zone while premium collected is meaningful.\n"
            "Risk Management: Define worst-case move threshold; pre-plan hedge if spot accelerates beyond comfort.\n"
            "Management: Control net delta and adjust hedge as gamma risk rises near expiry.\n"
            "Exit: Close on target payoff zone or if tail-risk probability rises materially."
        ),
    },
    "covered_call": {
        "concise": (
            "CONCISE EXAMPLE:\n"
            "View: Mildly bullish/neutral on existing holdings.\n"
            "Setup: Hold stock and sell OTM call above desired exit price.\n"
            "Risk: Downside still follows stock; premium is only partial cushion."
        ),
        "detailed": (
            "DETAILED EXAMPLE:\n"
            "Market View: Hold stock, monetize sideways-to-moderate upside via premium income.\n"
            "Structure: Long underlying + short OTM call at a strike where assignment is acceptable.\n"
            "Entry Logic: Choose strike by target exit level and annualized premium yield.\n"
            "Risk Management: If stock drops, treat premium as limited buffer; if stock rallies, prepare assignment decision.\n"
            "Management: Roll up/out when trend strengthens and you want to continue holding shares.\n"
            "Exit: Let assignment happen at planned level or buy back call at predefined profit threshold."
        ),
    },
    "protective_put": {
        "concise": (
            "CONCISE EXAMPLE:\n"
            "View: Bullish long stock but want downside insurance.\n"
            "Setup: Hold stock and buy put near risk floor.\n"
            "Risk: Protection costs premium; choose hedge horizon carefully."
        ),
        "detailed": (
            "DETAILED EXAMPLE:\n"
            "Market View: Keep upside ownership while capping downside through a put hedge.\n"
            "Structure: Long stock + long put at chosen strike and expiry.\n"
            "Entry Logic: Select put strike by maximum acceptable drawdown and event horizon.\n"
            "Risk Management: Effective floor = put strike minus premium impact; monitor hedge decay over time.\n"
            "Management: Roll put if protection window is still needed and time value decays.\n"
            "Exit: Unwind hedge after risk event or when downside probability normalizes."
        ),
    },
}


def detect_options_strategy(query: str) -> Optional[str]:
    """Detect named options strategy from user query."""
    q = normalize_market_terms_in_query((query or "").lower())
    for strategy_name, aliases in _OPTIONS_STRATEGY_PATTERNS.items():
        for alias in aliases:
            if re.search(r"\b" + re.escape(alias) + r"\b", q):
                return strategy_name
    return None


def get_options_strategy_example(strategy_name: str, response_style: str = "concise") -> str:
    """Get a concise/detailed example block for a detected options strategy."""
    if strategy_name not in _OPTIONS_STRATEGY_EXAMPLES:
        return ""
    style_key = "concise" if response_style == "concise" else "detailed"
    return _OPTIONS_STRATEGY_EXAMPLES[strategy_name].get(style_key, "")


def expand_acronyms_in_query(query: str) -> str:
    """Expand common Indian financial acronyms to full forms."""
    expanded = query
    for acronym, expansion in _ACRONYMS.items():
        # Replace acronym with parenthetical expansion (preserve original)
        import re
        pattern_acronym = acronym
        display_acronym = acronym
        if acronym == "PE2":
            pattern_acronym = "PE"
            display_acronym = "PE"
        pattern = r'\b' + re.escape(pattern_acronym) + r'\b'
        expanded = re.sub(pattern, f"{display_acronym} ({expansion})", expanded, flags=re.IGNORECASE)
    return normalize_market_terms_in_query(expanded)


def normalize_market_terms_in_query(query: str) -> str:
    """Normalize common market shorthand/Hinglish terms for better intent detection."""
    normalized = query or ""
    for short_term, long_term in _MARKET_TERM_NORMALIZATION.items():
        pattern = r'\b' + re.escape(short_term) + r'\b'
        normalized = re.sub(pattern, long_term, normalized, flags=re.IGNORECASE)
    return normalized


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
        "reasoning": "Detected intent based on..."  # explanation
    }
    """
    q = normalize_market_terms_in_query((query or "").lower())
    detected_strategy = detect_options_strategy(q)

    small_talk_type = _detect_small_talk_type(q)
    if small_talk_type:
        return {
            "intent": "SMALL_TALK",
            "confidence": 98,
            "alternatives": [("GENERAL", 35)],
            "reasoning": f"Detected small-talk intent: {small_talk_type}",
            "multi_intent": False,
            "intents": ["SMALL_TALK"],
            "small_talk_type": small_talk_type,
        }

    # Check for concept comparisons first (non-stock comparisons)
    _CONCEPT_PAIRS = {
        "growth vs value": "COMPARE_CONCEPTS",
        "safe vs aggressive": "COMPARE_CONCEPTS",
        "dividend vs growth": "COMPARE_CONCEPTS",
        "large-cap vs small-cap": "COMPARE_CONCEPTS",
        "large cap vs small cap": "COMPARE_CONCEPTS",
        "debt vs equity": "COMPARE_CONCEPTS",
        "long term vs short term": "COMPARE_CONCEPTS",
        "active vs passive": "COMPARE_CONCEPTS",
        "mutual fund vs stock": "COMPARE_CONCEPTS",
        "etf vs mutual fund": "COMPARE_CONCEPTS",
    }

    for concept_pair, intent in _CONCEPT_PAIRS.items():
        if concept_pair in q:
            return {
                "intent": intent,
                "confidence": 95,
                "alternatives": [("EDUCATIONAL", 50)],
                "reasoning": f"Detected concept comparison: {concept_pair.title()}",
            }

    scores: Dict[str, int] = {
        "PREDICT": 0, "COMPARE": 0, "BUY_SELL": 0, "TECHNICAL": 0,
        "FUNDAMENTAL": 0, "SECTOR_SCREEN": 0, "PORTFOLIO": 0, "EDUCATIONAL": 0,
        "CALCULATION": 0, "DERIVATIVES": 0,
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
             "death cross", "trend reversal", "price action", "vwap", "atr", "adx",
             "supertrend", "stochastic", "ichimoku", "volume profile"]:
        if kw in q: scores["TECHNICAL"] += 2

    # FUNDAMENTAL
    for kw in ["pe ratio", "p/e", "earnings", " eps", "revenue", "quarterly results",
               "fundamentals", "valuation", "roe", "roce", "debt", "balance sheet",
             "promoter", "pledging", "dividend yield", "payout", "ev to ebitda", "ev/ebitda",
             "price to book", "pb ratio", "cash flow", "free cash flow", "ebitda margin", "book value"]:
        if kw in q: scores["FUNDAMENTAL"] += 2

    # SECTOR_SCREEN
    for kw in [
        "sector", "banking stocks", "pharma stocks", "it stocks", "fmcg stocks",
        "auto stocks", "defence stocks", "defense stocks", "energy stocks",
        "nse listed", "screener", "psu stocks", "realty stocks", "metal stocks",
        "infra stocks", "railway stocks", "cement stocks",
    ]:
        if kw in q:
            scores["SECTOR_SCREEN"] += 2
    # Bare sector themes ("defence", "psu", "pharma") without requiring "stocks"
    for kw in [
        "defence", "defense", "psu", "realty", "pharma", "fmcg", "infra",
        "railway", "shipyard", "fintech", "nbfc", "cement", "telecom",
    ]:
        if re.search(r"\b" + re.escape(kw) + r"\b", q):
            scores["SECTOR_SCREEN"] += 3
    if re.search(r'\b(top|best|good)\b.{0,20}\bstocks?\b', q):
        scores["SECTOR_SCREEN"] += 3

    # PORTFOLIO
    for kw in ["portfolio", "my holdings", "sip", "diversify", "allocation",
               "rebalance", "asset allocation", "my stocks", "long term investment"]:
        if kw in q: scores["PORTFOLIO"] += 3

    # EDUCATIONAL — definitions, meanings, equations (boost strongly so
    # "what is RSI" does not fall into TECHNICAL clarifier asking for a symbol).
    for kw in [
        "what is", "what are", "explain", "how does", "what does",
        "meaning of", "define", "definition", "understand", "how to calculate",
        "why is", "formula", "equation", "difference between",
        "what is rsi", "what is macd", "what is pe", "what is p/e",
        "rsi formula", "macd formula", "cagr formula", "pe formula",
    ]:
        if kw in q:
            scores["EDUCATIONAL"] += 3
    if re.search(r'\bwhat (is|are)\b', q):
        scores["EDUCATIONAL"] += 3
    if re.search(r'\b(formula|equation|definition|meaning)\b', q):
        scores["EDUCATIONAL"] += 4
    if re.search(
        r'\b(rsi|macd|bollinger|cagr|roe|roce|eps|vwap|atr|pe ratio|p/e|peg|sharpe|drawdown)\b',
        q,
    ) and re.search(r'\b(what|explain|define|formula|equation|mean|meaning)\b', q):
        scores["EDUCATIONAL"] += 5

    # CALCULATION
    for kw in [
        "calculate", "calculation", "formula", "compute", "how much return", "returns in",
        "cagr", "risk reward", "risk/reward", "position size", "stop loss percent",
        "target price from", "intrinsic value", "fair value", "valuation model", "discounted cash flow",
        "dcf", "pe based target", "peg ratio", "drawdown", "compounded return",
        "margin required", "break even", "breakeven", "lot size", "position sizing",
        "theta decay", "premium decay", "roi", "risk per trade",
    ]:
        if kw in q:
            scores["CALCULATION"] += 2
    if re.search(r'\b(calculate|compute|formula)\b', q):
        scores["CALCULATION"] += 3
    if re.search(r'\b\d+(\.\d+)?\s*(%|percent|x|times|days|months|years|yrs|₹|rs)\b', q):
        scores["CALCULATION"] += 1
    if re.search(r'\b(cagr|pe|p/e|roe|roce|eps|stop\s*loss|risk\s*/?\s*reward)\b', q):
        scores["CALCULATION"] += 1

    # DERIVATIVES
    for kw in [
        "futures", "options", "call option", "put option", "straddle", "strangle", "spread",
        "calendar spread", "butterfly spread", "ratio spread", "covered call", "protective put",
        "iron condor", "expiry", "strike", "premium", "open interest", "implied volatility",
        "pcr", "max pain", "greeks", "delta", "gamma", "theta", "vega", "mtm", "hedge",
    ]:
        if kw in q:
            scores["DERIVATIVES"] += 2
    if re.search(r'\b(call option|put option|futures?)\b', q):
        scores["DERIVATIVES"] += 2
    if detected_strategy:
        scores["DERIVATIVES"] += 4

    # Get top 3
    sorted_intents = sorted(scores.items(), key=lambda x: -x[1])
    best = sorted_intents[0][0]
    best_score = sorted_intents[0][1]

    # Calculate confidence (0-100)
    if best_score == 0:
        confidence = 0
        best = "GENERAL"
        reasoning = "No specific intent detected; treating as general market question"
    else:
        second_score = sorted_intents[1][1] if len(sorted_intents) > 1 else 0
        # Confidence based on gap between top and runner-up
        gap = best_score - second_score
        confidence = min(100, 50 + gap * 5)  # 50-100 scale

        # Build reasoning message
        alt_intent = sorted_intents[1][0] if len(sorted_intents) > 1 else None
        alt_confidence = min(100, 50 + second_score * 5) if alt_intent else 0

        if alt_intent and confidence < 65:
            reasoning = f"Detected {best} (confidence {confidence}%) with secondary interpretation: {alt_intent} ({alt_confidence}%); query may be ambiguous"
        elif alt_intent:
            reasoning = f"Detected {best} (confidence {confidence}%) based on primary keywords; {alt_intent} also possible ({alt_confidence}%)"
        else:
            reasoning = f"Detected {best} with high confidence ({confidence}%)"

    alternatives = [(name, min(100, 50 + score * 5)) for name, score in sorted_intents[1:3]]

    # Multi-intent detection: if top 2 intents both have good scores, flag as multi-intent
    multi_intent = False
    detected_intents = [best]
    if len(sorted_intents) > 1:
        second_intent, second_score = sorted_intents[1]
        # Multi-intent if:
        # 1. Confidence < 55% (ambiguous), OR
        # 2. Second intent has >70% of top intent's score (strong secondary intent)
        if confidence < 55 or (second_score > 0 and second_score >= best_score * 0.7):
            multi_intent = True
            # Include top 2-3 intents that have meaningful scores
            for name, score in sorted_intents[:3]:
                if score > 0 and name not in detected_intents:
                    detected_intents.append(name)

    result = {
        "intent": best,
        "confidence": confidence,
        "alternatives": alternatives,
        "reasoning": reasoning,
        "multi_intent": multi_intent,
        "intents": detected_intents,  # List of detected intents to process
    }
    if detected_strategy:
        result["detected_strategy"] = detected_strategy
    return result


def _detect_small_talk_type(query_lower: str) -> Optional[str]:
    """Detect light conversational small-talk categories."""
    q = (query_lower or "").strip()
    if not q:
        return None

    # Normalize punctuation to spaces for safer token checks.
    q_norm = re.sub(r"[^a-z0-9\s]", " ", q)
    q_norm = re.sub(r"\s+", " ", q_norm).strip()

    def _contains_phrase(phrase: str) -> bool:
        return re.search(r"\b" + re.escape(phrase) + r"\b", q_norm) is not None

    thanks_patterns = ["thanks", "thank you", "thx", "ty", "appreciate it", "great thanks"]
    bye_patterns = ["bye", "goodbye", "see you", "cya", "talk later", "good night", "gn"]
    how_are_you_patterns = ["how are you", "how r u", "howre you", "how you doing", "kaise ho", "kya haal"]
    greeting_patterns = ["hi", "hello", "hey", "namaste", "namaskar", "good morning", "good afternoon", "good evening"]

    if any(_contains_phrase(pat) for pat in how_are_you_patterns):
        return "how_are_you"
    if any(_contains_phrase(pat) for pat in thanks_patterns):
        return "thanks"
    if any(_contains_phrase(pat) for pat in bye_patterns):
        return "bye"

    # Restrict greeting to short greeting-like utterances.
    greeting_tokens = {
        "hi", "hii", "hiii", "hello", "hey", "namaste", "namaskar", "gm",
        "good", "morning", "afternoon", "evening",
    }
    allowed_context_tokens = {"there", "team", "bot", "ai", "assistant", "bysel"}
    tokens = q_norm.split()
    if (
        len(tokens) <= 5
        and any(_contains_phrase(pat) for pat in greeting_patterns)
        and all(token in greeting_tokens or token in allowed_context_tokens for token in tokens)
    ):
        return "greeting"

    return None


def get_small_talk_response(query: str, response_style: str = "concise") -> Optional[str]:
    """Return deterministic responses for small-talk queries, else None."""
    small_talk_type = _detect_small_talk_type((query or "").lower())
    if not small_talk_type:
        return None

    concise_map = {
        "greeting": "Hi! I am BYSEL AI. Ask me about stock prices, buy/sell signals, comparisons, or valuation.",
        "thanks": "You are welcome. I am here whenever you need market help.",
        "bye": "Bye! See you soon. Happy investing.",
        "how_are_you": "I am doing great and ready to help with markets. What stock question do you have?",
    }

    detailed_map = {
        "greeting": (
            "Hi! I am BYSEL AI. I can help with live stock context, technical/fundamental analysis, "
            "comparisons, and risk-aware buy/sell planning."
        ),
        "thanks": "You are welcome. If you want, I can also help with your next stock query right away.",
        "bye": "Bye! Have a great day. Reach out anytime for stock analysis or market calculations.",
        "how_are_you": (
            "I am doing great and fully ready to help. Tell me a symbol and objective "
            "(buy/sell, technicals, fundamentals, comparison, or valuation)."
        ),
    }

    if response_style == "detailed":
        return detailed_map[small_talk_type]
    return concise_map[small_talk_type]


def infer_response_style(query: str, conversation_history: Optional[List[Dict]] = None) -> str:
    """Infer concise vs detailed response style from query length and conversation depth."""
    q = (query or "").lower()
    words = re.findall(r"\w+", q)
    word_count = len(words)
    history_turns = len([turn for turn in (conversation_history or []) if turn.get("content")])

    concise_cues = ["quick", "brief", "short", "tldr", "one line", "one-liner", "concise"]
    detailed_cues = ["detailed", "in detail", "deep dive", "explain", "breakdown", "step by step", "why"]

    if any(cue in q for cue in concise_cues):
        return "concise"
    if any(cue in q for cue in detailed_cues):
        return "detailed"

    if word_count <= 8 and history_turns < 2:
        return "concise"
    if word_count >= 18 or history_turns >= 4:
        return "detailed"

    return "detailed"


def resolve_pronouns(query: str, conversation_history: Optional[List[Dict]] = None) -> str:
    """
    Resolve pronouns (it, that, this, those) to actual entities from conversation history.
    Example: "RELIANCE rallied. Why?" → "Why did RELIANCE rally?"
    """
    if not conversation_history or len(conversation_history) < 2:
        return query  # Not enough context to resolve

    # Extract entities from recent conversation
    recent_symbols = []
    for turn in conversation_history[-4:]:  # Last 4 turns
        # Look for stock symbols (uppercase patterns)
        symbols = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', turn.get("content", ""))
        recent_symbols.extend(symbols)

    if not recent_symbols:
        return query

    # Get the most recent symbol
    last_symbol = recent_symbols[-1] if recent_symbols else None
    if not last_symbol:
        return query

    # Replace pronouns with last mentioned symbol
    pronouns = {
        r'\bit\b': last_symbol.lower(),
        r'\bthat\b': last_symbol.lower(),
        r'\bthis\b': last_symbol.lower(),
        r'\bthose\b': last_symbol.lower(),
        r'\bthem\b': last_symbol.lower(),
        r'\bits\b': f"{last_symbol.lower()}'s",
    }

    resolved = query
    for pronoun, replacement in pronouns.items():
        resolved = re.sub(pronoun, replacement, resolved, flags=re.IGNORECASE)

    return resolved


def detect_sentiment_from_query(query: str) -> dict:
    """
    Detect sentiment: urgency, risk appetite, emotional tone.
    Returns: {
        "urgency": "low" | "medium" | "high",
        "risk_appetite": "conservative" | "moderate" | "aggressive",
        "emotion": "frustrated" | "neutral" | "excited",
        "user_profile": "investor" | "trader" | "unknown"
    }
    """
    q = query.lower()
    sentiment = {
        "urgency": "low",
        "risk_appetite": "moderate",
        "emotion": "neutral",
        "user_profile": "unknown",
    }

    # Urgency detection — multiple !, ?, CAPS
    exclamation_count = query.count("!") + query.count("?")
    caps_ratio = sum(1 for c in query if c.isupper()) / max(len(query), 1)

    if exclamation_count >= 2 or caps_ratio > 0.4 or "urgent" in q or "asap" in q:
        sentiment["urgency"] = "high"
    elif exclamation_count == 1 or caps_ratio > 0.2 or "soon" in q:
        sentiment["urgency"] = "medium"

    # Risk appetite detection
    conservative_kws = ["safe", "conservative", "low risk", "dividend", "stable", "blue chip"]
    aggressive_kws = ["aggressive", "leverage", "swing", "day trade", "momentum", "breakout", "leveraged"]

    cons_count = sum(1 for kw in conservative_kws if kw in q)
    agg_count = sum(1 for kw in aggressive_kws if kw in q)

    if agg_count > cons_count:
        sentiment["risk_appetite"] = "aggressive"
    elif cons_count > agg_count:
        sentiment["risk_appetite"] = "conservative"

    # Emotion/tone detection
    frustrated_kws = ["killing me", "frustrated", "loss", "down", "crash", "panic", "worried", "stressed"]
    excited_kws = ["excited", "bullish", "boom", "rocket", "moon", "thrilled", "huge gain", "doubled"]

    frust_count = sum(1 for kw in frustrated_kws if kw in q)
    excit_count = sum(1 for kw in excited_kws if kw in q)

    if frust_count > excit_count:
        sentiment["emotion"] = "frustrated"
    elif excit_count > frust_count:
        sentiment["emotion"] = "excited"

    # User profile detection
    investor_kws = ["long term", "sip", "holding", "portfolio", "diversify", "retirement"]
    trader_kws = ["swing", "day trade", "intraday", "scalp", "technical", "momentum", "breakout"]

    inv_count = sum(1 for kw in investor_kws if kw in q)
    trad_count = sum(1 for kw in trader_kws if kw in q)

    if trad_count > inv_count:
        sentiment["user_profile"] = "trader"
    elif inv_count > trad_count:
        sentiment["user_profile"] = "investor"

    return sentiment


def build_user_profile_from_history(conversation_history: Optional[List[Dict]]) -> dict:
    """
    Build investor profile by accumulating signals from conversation history.
    Accounts for investor type, risk appetite, time horizon, experience level.
    Returns: {
        "investor_type": "investor" | "trader" | "hybrid" | "unknown",
        "risk_appetite": "conservative" | "moderate" | "aggressive",
        "time_horizon": "day" | "swing" | "medium" | "long" | "unknown",
        "experience": "beginner" | "intermediate" | "advanced" | "unknown",
        "profile_strength": 0-100  (confidence in profile detection, based on signal count)
    }
    """
    if not conversation_history or len(conversation_history) < 2:
        return {
            "investor_type": "unknown",
            "risk_appetite": "moderate",
            "time_horizon": "unknown",
            "experience": "unknown",
            "profile_strength": 0,
        }

    # Accumulate signals from last 10 turns (broader context)
    recent_turns = conversation_history[-10:]
    combined_text = " ".join([turn.get("content", "") for turn in recent_turns]).lower()

    investor_signals = 0
    trader_signals = 0
    hybrid_signals = 0
    conservative_signals = 0
    aggressive_signals = 0
    day_signals = 0
    swing_signals = 0
    medium_signals = 0
    long_signals = 0
    beginner_signals = 0
    advanced_signals = 0

    # Investor vs Trader vs Hybrid signals
    investor_keywords = ["sip", "long term", "holding", "portfolio", "diversify", "retirement", "build wealth", "passive"]
    trader_keywords = ["swing", "day trade", "intraday", "scalp", "technical", "momentum", "breakout", "entry zone"]
    hybrid_keywords = ["mix", "both", "sometimes", "depends on", "flexible", "balance"]

    for kw in investor_keywords:
        investor_signals += combined_text.count(kw)
    for kw in trader_keywords:
        trader_signals += combined_text.count(kw)
    for kw in hybrid_keywords:
        hybrid_signals += combined_text.count(kw)

    # Risk appetite signals
    conservative_keywords = ["safe", "dividend", "stable", "blue chip", "large cap", "low volatility", "risk averse"]
    aggressive_keywords = ["aggressive", "leverage", "high risk", "penny stock", "volatile", "growth", "momentum", "2x", "10x"]

    for kw in conservative_keywords:
        conservative_signals += combined_text.count(kw)
    for kw in aggressive_keywords:
        aggressive_signals += combined_text.count(kw)

    # Time horizon signals
    day_keywords = ["intraday", "day trading", "today", "today's", "scalp"]
    swing_keywords = ["swing", "swing trade", "1-2 weeks", "few days", "short term"]
    medium_keywords = ["3 months", "quarter", "medium term", "6 months", "half year"]
    long_keywords = ["1 year", "long term", "years", "multi-year", "annual", "sip"]

    for kw in day_keywords:
        day_signals += combined_text.count(kw)
    for kw in swing_keywords:
        swing_signals += combined_text.count(kw)
    for kw in medium_keywords:
        medium_signals += combined_text.count(kw)
    for kw in long_keywords:
        long_signals += combined_text.count(kw)

    # Experience level signals
    beginner_keywords = ["new to", "beginner", "never traded", "first time", "how do i", "what is", "explain"]
    advanced_keywords = ["technicals", "rsi", "macd", "bollinger", "support resistance", "derivatives", "options", "f&o", "sector rotation"]

    for kw in beginner_keywords:
        beginner_signals += combined_text.count(kw)
    for kw in advanced_keywords:
        advanced_signals += combined_text.count(kw)

    # Determine dominant profile traits
    profile = {
        "investor_type": "unknown",
        "risk_appetite": "moderate",
        "time_horizon": "unknown",
        "experience": "unknown",
        "profile_strength": 0,
    }

    total_signals = (
        investor_signals + trader_signals + hybrid_signals +
        conservative_signals + aggressive_signals +
        day_signals + swing_signals + medium_signals + long_signals +
        beginner_signals + advanced_signals
    )

    if total_signals > 0:
        # Investor type
        if hybrid_signals > investor_signals and hybrid_signals > trader_signals:
            profile["investor_type"] = "hybrid"
        elif trader_signals > investor_signals:
            profile["investor_type"] = "trader"
        elif investor_signals > trader_signals:
            profile["investor_type"] = "investor"

        # Risk appetite
        if aggressive_signals > conservative_signals * 1.5:
            profile["risk_appetite"] = "aggressive"
        elif conservative_signals > aggressive_signals * 1.5:
            profile["risk_appetite"] = "conservative"
        else:
            profile["risk_appetite"] = "moderate"

        # Time horizon — prioritize most frequent signal
        time_signals = {
            "day": day_signals,
            "swing": swing_signals,
            "medium": medium_signals,
            "long": long_signals,
        }
        if max(time_signals.values()) > 0:
            profile["time_horizon"] = max(time_signals, key=time_signals.get)

        # Experience level
        if advanced_signals > beginner_signals * 2:
            profile["experience"] = "advanced"
        elif advanced_signals > 0 and beginner_signals > 0:
            profile["experience"] = "intermediate"
        elif beginner_signals > 0:
            profile["experience"] = "beginner"

        # Strength: normalize signal count (0-100)
        profile["profile_strength"] = min(100, int((total_signals / len(recent_turns)) * 10))

    return profile


def _strip_internal_metadata(text: str) -> str:
    """Remove ONLY the most obvious template/fallback LLM metadata markers."""
    if not text:
        return text

    lines = text.split("\n")
    filtered_lines = []

    # ONLY filter these EXACT line starters (very conservative)
    skip_prefixes = [
        "intent detected:",
        "category:",
        "latency mode:",
        "model backend:",
        "query focus:",
        "intent-category mapping:",
        "data readiness snapshot:",
        "deterministic checks:",
        "analysis guidance:",
        "compliance note:",
    ]

    for line in lines:
        stripped = line.strip().lower()
        # Only skip if line STARTS with these exact prefixes
        should_skip = any(stripped.startswith(prefix) for prefix in skip_prefixes)
        if not should_skip:
            filtered_lines.append(line)

    result = "\n".join(filtered_lines).strip()
    # If everything was stripped, return original (safety)
    return result if result else text


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

    # User context block (sentiment, urgency, profile)
    user_sentiment = context.get("user_sentiment") or {}
    if user_sentiment:
        parts.append("USER CONTEXT (adjust tone & recommendations accordingly):")
        if user_sentiment.get("urgency") and user_sentiment["urgency"] != "low":
            parts.append(f"  Urgency: {user_sentiment['urgency'].upper()}")
        if user_sentiment.get("risk_appetite"):
            parts.append(f"  Risk Profile: {user_sentiment['risk_appetite'].title()}")
        if user_sentiment.get("emotion") and user_sentiment["emotion"] != "neutral":
            parts.append(f"  Tone: {user_sentiment['emotion'].title()}")
        if user_sentiment.get("user_profile") and user_sentiment["user_profile"] != "unknown":
            parts.append(f"  Profile: {user_sentiment['user_profile'].title()}")
        parts.append("")

    # Portfolio context block
    portfolio = context.get("portfolio_context") or {}
    if portfolio and portfolio.get("total_holdings", 0) > 0:
        parts.append("YOUR CURRENT PORTFOLIO:")
        parts.append(f"  Total Holdings: {portfolio['total_holdings']} stocks")
        if portfolio.get("symbols"):
            parts.append(f"  Stocks: {', '.join(portfolio['symbols'][:10])}")  # First 10
            if len(portfolio['symbols']) > 10:
                parts.append(f"  (+{len(portfolio['symbols']) - 10} more)")
        parts.append("")

    # Historical data block
    hist = context.get("historical_data") or {}
    if hist:
        parts.append(f"HISTORICAL DATA ({hist['period']} lookback):")
        parts.append(f"  Data Points: {hist['data_points']} candles")
        parts.append(f"  Start Price: ₹{hist['start_price']:,}")
        parts.append(f"  End Price: ₹{hist['end_price']:,}")
        parts.append(f"  Change: {hist['change_percent']:+.2f}%")
        parts.append(f"  High: ₹{hist['high']:,} | Low: ₹{hist['low']:,}")
        parts.append("")

    # Catalyst info block (news-to-price linkage)
    catalyst = context.get("catalyst_info") or {}
    if catalyst:
        parts.append("LIKELY CATALYST (news event linked to price move):")
        parts.append(f"  Event: {catalyst.get('event_type', '').title()}")
        parts.append(f"  Headline: {catalyst.get('likely_catalyst', '')[:100]}")
        parts.append(f"  Sentiment: {catalyst.get('sentiment', 'neutral').title()}")
        parts.append(f"  Confidence: {catalyst.get('confidence', 'low').title()}")
        parts.append("")

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

    # Screened stocks summary (from sector screener)
    screened = context.get("screened_stocks_summary", "")
    if screened:
        parts.append(screened)
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
    intent_result: Optional[Dict] = None,
    response_style: Optional[str] = None,
) -> Dict:
    """Send query to Groq with optional structured market context."""
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

    # DEBUG: Log what we're sending to Groq
    logger.info(f"GROQ DEBUG: intent={intent}, confidence={confidence}")
    logger.info(f"GROQ DEBUG: query={query}")
    if context:
        logger.info(f"GROQ DEBUG: context keys={list(context.keys())}")
        logger.info(f"GROQ DEBUG: symbol={context.get('symbol')}, has_technical={bool(context.get('technical'))}")

    # Resolve pronouns if conversation history is provided
    resolved_query = query
    if conversation_history:
        resolved_query = resolve_pronouns(query, conversation_history)

    effective_style = response_style or infer_response_style(query, conversation_history)

    # Handle multi-intent detection
    multi_intent = intent_result.get("multi_intent", False)
    detected_intents = intent_result.get("intents", [intent])

    if multi_intent and len(detected_intents) > 1:
        # Combine prompts for multiple detected intents
        intent_addendum_parts = []
        for detected_intent in detected_intents[:3]:  # Max 3 intents
            addendum = _INTENT_PROMPTS.get(detected_intent, "")
            if addendum:
                intent_addendum_parts.append(f"## INTENT: {detected_intent}\n{addendum}")

        intent_addendum = "\n\n".join(intent_addendum_parts)
        if intent_addendum:
            intent_addendum += "\n\n⚠️ IMPORTANT: This query contains multiple intents above. Please address ALL of them in your response."
    else:
        # Single intent or low confidence — use original logic
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

    if intent == "DERIVATIVES":
        strategy_name = intent_result.get("detected_strategy") or detect_options_strategy(query)
        if strategy_name and strategy_name in _OPTIONS_STRATEGY_PROMPTS:
            intent_addendum += "\n\n" + _OPTIONS_STRATEGY_PROMPTS[strategy_name]
            strategy_example = get_options_strategy_example(strategy_name, effective_style)
            if strategy_example:
                intent_addendum += "\n\n" + strategy_example

    # Sector screening: if SECTOR_SCREEN intent, run screener and inject results
    if intent == "SECTOR_SCREEN" and context:
        try:
            from .stock_enricher import screen_stocks
            # Extract sector from context or entities
            entities = context.get("entities", {})
            query_lower = query.lower()

            # Detect sector with word boundaries; prefer longest matches first so
            # "it" does not match inside "circuit" / "liquidity".
            sector_keywords = [
                "defence", "defense", "banking", "pharma", "energy", "infra",
                "fmcg", "cement", "metal", "realty", "psu", "railway", "auto", "it",
            ]
            detected_sector = None
            for sector_kw in sector_keywords:
                if re.search(r"\b" + re.escape(sector_kw) + r"\b", query_lower):
                    detected_sector = "DEFENCE" if sector_kw in ("defence", "defense") else sector_kw.upper()
                    break

            screening_criteria = {}
            if detected_sector:
                screening_criteria["sector"] = detected_sector

            # Extract P/E filter if mentioned ("P/E < 20")
            pe_match = re.search(r'pe\s*[<>]\s*(\d+)', query_lower, re.IGNORECASE)
            if pe_match:
                screening_criteria["pe_max"] = int(pe_match.group(1))

            # Extract dividend filter if mentioned ("dividend > 3")
            div_match = re.search(r'dividend.*?[>]\s*(\d+)', query_lower, re.IGNORECASE)
            if div_match:
                screening_criteria["dividend_min"] = int(div_match.group(1))

            screened_results = screen_stocks(screening_criteria) if screening_criteria else []
            if screened_results:
                # Build screener summary for context
                screener_summary = f"\n\nSCREENED RESULTS ({detected_sector or 'All sectors'}):\n"
                for i, stock in enumerate(screened_results[:5], 1):
                    screener_summary += f"{i}. {stock.get('symbol', 'N/A')}: P/E {stock.get('pe', 'N/A')}, Div Yield {stock.get('dividendYield', 'N/A')}%\n"

                context["screened_stocks_summary"] = screener_summary
        except Exception as e:
            logger.debug(f"Sector screening failed: {e}")

    # Build system prompt: base + intent-specific addendum
    system_prompt = _BASE_SYSTEM_PROMPT + intent_addendum

    if effective_style == "concise":
        system_prompt += "\n\nRESPONSE STYLE: concise. Keep it to the key answer and essential rationale only."
    else:
        system_prompt += "\n\nRESPONSE STYLE: detailed. Include structured reasoning and calculation steps where relevant."

    # Build user profile from conversation history for tone/recommendation tailoring
    user_profile = build_user_profile_from_history(conversation_history)
    if user_profile["profile_strength"] > 20:  # Only inject if confident (20+ signal strength)
        profile_instruction = f"""
INVESTOR PROFILE (detected from conversation):
- Type: {user_profile['investor_type'].upper()}
- Risk Appetite: {user_profile['risk_appetite'].upper()}
- Time Horizon: {user_profile['time_horizon'].upper()}
- Experience: {user_profile['experience'].upper()}

TONE ADJUSTMENTS:
"""
        # Customize tone based on profile
        if user_profile["investor_type"] == "investor":
            profile_instruction += "- Long-term wealth building focus; emphasize fundamentals, consistency, SIPs\n"
            profile_instruction += "- Avoid aggressive language; use 'opportunity' rather than 'trade'\n"
        elif user_profile["investor_type"] == "trader":
            profile_instruction += "- Short-term tactical focus; emphasize entry/exit zones, technicals, momentum\n"
            profile_instruction += "- Use 'breakout', 'resistance', 'setup' terminology\n"
        elif user_profile["investor_type"] == "hybrid":
            profile_instruction += "- Balanced approach; cover both fundamentals and technicals\n"
            profile_instruction += "- Suggest different strategies for different time horizons\n"

        if user_profile["risk_appetite"] == "conservative":
            profile_instruction += "- Emphasize stability, dividend yield, large-caps, downside protection\n"
            profile_instruction += "- Include risk caveats; flag any speculative elements\n"
        elif user_profile["risk_appetite"] == "aggressive":
            profile_instruction += "- Highlight growth potential, volatility opportunities, higher-beta opportunities\n"
            profile_instruction += "- Discuss risk management rather than avoidance\n"

        if user_profile["experience"] == "beginner":
            profile_instruction += "- Use simple language; define technical terms\n"
            profile_instruction += "- Include 'why' explanations for recommendations\n"
        elif user_profile["experience"] == "advanced":
            profile_instruction += "- Use technical jargon; skip basic definitions\n"
            profile_instruction += "- Focus on nuances and edge cases\n"

        system_prompt += "\n" + profile_instruction

    # Build user content with market context
    user_content = ""
    if context and isinstance(context, dict):
        formatted = _format_context(context)
        if formatted:
            user_content = f"MARKET CONTEXT:\n{formatted}\n\n"
    user_content += f"User query: {resolved_query}"

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
        # Don't strip Groq responses — only strip fallback LLM responses
        # Groq should never include metadata due to system prompt
        return {
            "answer": text,
        }
    except Exception as e:
        logger.error("Groq API error: %s", e)
        return {"error": f"Groq error: {str(e)}"}
