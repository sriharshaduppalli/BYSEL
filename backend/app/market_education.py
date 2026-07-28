"""Deterministic market education / equation answers for BYSEL AI.

Used by /ai/ask so definition and formula prompts get accurate structured
answers even when LLM tiers are cold or unavailable.
"""

from __future__ import annotations

import re
from typing import Optional

_TERM_ANSWERS: dict[str, str] = {
    "rsi": (
        "**RSI (Relative Strength Index)**\n\n"
        "RSI measures recent price strength on a 0–100 scale.\n\n"
        "**Equation:**\n"
        "`RSI = 100 − (100 / (1 + RS))`\n"
        "`RS = Average Gain / Average Loss` over N periods (commonly 14)\n\n"
        "**How to read:**\n"
        "• Above 70 → often overbought\n"
        "• Below 30 → often oversold\n"
        "• Around 50 → neutral momentum\n\n"
        "**NSE tip:** Use RSI with support/resistance — alone it can stay overbought in strong trends "
        "(common in momentum names like RELIANCE during rallies).\n\n"
        "**Common mistake:** Buying only because RSI < 30 without checking trend and volume."
    ),
    "macd": (
        "**MACD (Moving Average Convergence Divergence)**\n\n"
        "MACD tracks trend and momentum using EMAs.\n\n"
        "**Equations:**\n"
        "`MACD Line = EMA(12) − EMA(26)`\n"
        "`Signal Line = EMA(9) of MACD Line`\n"
        "`Histogram = MACD Line − Signal Line`\n\n"
        "**How to use:**\n"
        "• MACD crossing above Signal → bullish momentum shift\n"
        "• Crossing below → bearish shift\n"
        "• Histogram expanding → momentum strengthening\n\n"
        "**Common mistake:** Trading every tiny cross in a sideways market."
    ),
    "pe": (
        "**P/E Ratio (Price-to-Earnings)**\n\n"
        "**Equation:**\n"
        "`P/E = Market Price per Share / Earnings Per Share (EPS)`\n\n"
        "Trailing P/E uses last 12 months EPS; Forward P/E uses estimated EPS.\n\n"
        "**How to use:** Compare vs sector peers (e.g. TCS vs INFY), not vs unrelated industries.\n\n"
        "**Common mistake:** Calling a stock cheap only because P/E is low — it may have weak growth or high debt."
    ),
    "p/e": None,  # alias filled below
    "cagr": (
        "**CAGR (Compound Annual Growth Rate)**\n\n"
        "**Equation:**\n"
        "`CAGR = (Ending Value / Beginning Value)^(1 / Years) − 1`\n\n"
        "**Example:** ₹1,00,000 → ₹1,61,051 in 5 years\n"
        "`CAGR = (1.61051)^(1/5) − 1 ≈ 10%`\n\n"
        "Useful for SIPs/mutual funds and multi-year stock returns.\n\n"
        "**Common mistake:** Using CAGR for very short periods (noisy)."
    ),
    "roe": (
        "**ROE (Return on Equity)**\n\n"
        "**Equation:**\n"
        "`ROE = Net Profit / Shareholders' Equity`\n\n"
        "Shows how efficiently a company uses equity capital.\n"
        "High ROE with reasonable debt is often preferred by long-term investors.\n\n"
        "**Common mistake:** High ROE driven only by heavy leverage."
    ),
    "roce": (
        "**ROCE (Return on Capital Employed)**\n\n"
        "**Equation:**\n"
        "`ROCE = EBIT / Capital Employed`\n"
        "`Capital Employed ≈ Equity + Debt − Cash` (definitions vary slightly)\n\n"
        "Better than ROE alone when comparing capital-intensive businesses.\n\n"
        "**Common mistake:** Mixing ROCE with ROE without adjusting for debt."
    ),
    "eps": (
        "**EPS (Earnings Per Share)**\n\n"
        "**Equation:**\n"
        "`EPS = Net Profit attributable to equity / Weighted average shares outstanding`\n\n"
        "Used in P/E and in tracking profit growth over quarters.\n\n"
        "**Common mistake:** Ignoring diluted EPS when convertibles/ESOPs exist."
    ),
    "vwap": (
        "**VWAP (Volume Weighted Average Price)**\n\n"
        "**Equation:**\n"
        "`VWAP = Σ(Price × Volume) / Σ(Volume)` for the session\n\n"
        "Intraday benchmark — institutions often buy below VWAP and sell above it.\n\n"
        "**Common mistake:** Using previous-day VWAP for today's intraday decisions."
    ),
    "atr": (
        "**ATR (Average True Range)**\n\n"
        "Measures volatility (not direction).\n\n"
        "**True Range** = max of:\n"
        "• High − Low\n"
        "• |High − Previous Close|\n"
        "• |Low − Previous Close|\n\n"
        "`ATR = Average of True Range over N periods` (often 14)\n\n"
        "Used for stop-loss distance and position sizing.\n\n"
        "**Common mistake:** Treating rising ATR as bullish — it only means bigger swings."
    ),
    "bollinger": (
        "**Bollinger Bands**\n\n"
        "**Equations:**\n"
        "`Middle Band = SMA(N)` (often 20)\n"
        "`Upper Band = Middle + k × StdDev`\n"
        "`Lower Band = Middle − k × StdDev` (k often 2)\n\n"
        "Bands widen in high volatility and contract in quiet markets.\n\n"
        "**Common mistake:** Fading every touch of the upper band in a strong uptrend."
    ),
    "sharpe": (
        "**Sharpe Ratio**\n\n"
        "**Equation:**\n"
        "`Sharpe = (Portfolio Return − Risk-Free Rate) / Standard Deviation of Returns`\n\n"
        "Higher Sharpe means better return per unit of volatility.\n\n"
        "**Common mistake:** Comparing Sharpes across very different time periods."
    ),
    "drawdown": (
        "**Drawdown**\n\n"
        "**Equation:**\n"
        "`Drawdown = (Trough Value − Peak Value) / Peak Value`\n"
        "Max Drawdown = worst peak-to-trough decline in the period\n\n"
        "Critical for risk management and position sizing.\n\n"
        "**Common mistake:** Ignoring drawdown while chasing high returns."
    ),
    "risk reward": (
        "**Risk/Reward Ratio**\n\n"
        "**Equation:**\n"
        "`Risk/Reward = Potential Reward / Potential Risk`\n"
        "`= (Target − Entry) / (Entry − Stop Loss)`\n\n"
        "Many traders prefer setups ≥ 1:2 (risk ₹1 to aim ₹2).\n\n"
        "**Common mistake:** Wide targets without a realistic probability of being hit."
    ),
    "stop loss": (
        "**Stop Loss**\n\n"
        "An order/level that exits a trade to limit loss.\n\n"
        "**Simple % formula:**\n"
        "`Stop = Entry × (1 − Stop%)` for longs\n"
        "`Stop = Entry × (1 + Stop%)` for shorts\n\n"
        "**ATR-based:** `Stop = Entry − (k × ATR)` for longs\n\n"
        "**Common mistake:** Moving stop farther after being wrong instead of cutting risk."
    ),
    "sip": (
        "**SIP (Systematic Investment Plan)**\n\n"
        "Invest a fixed amount at regular intervals (usually monthly) into a mutual fund.\n\n"
        "Benefit comes from rupee-cost averaging and compounding — not from timing one perfect entry.\n\n"
        "**Common mistake:** Stopping SIPs only because markets fell (often the best averaging period)."
    ),
    "ipo": (
        "**IPO (Initial Public Offering)**\n\n"
        "When a private company lists shares for the public on NSE/BSE, regulated by SEBI.\n\n"
        "Retail applies via UPI/ASBA; allotment can be lottery-based for oversubscribed issues.\n\n"
        "**Common mistake:** Treating listing gains as guaranteed."
    ),
    "beta": (
        "**Beta**\n\n"
        "**Equation (concept):** sensitivity of a stock’s returns to market (Nifty) returns.\n"
        "`β ≈ Cov(stock, market) / Var(market)`\n\n"
        "• β > 1 → amplifies Nifty moves (more volatile)\n"
        "• β < 1 → usually more defensive\n\n"
        "**Common mistake:** Assuming low beta means zero risk — stock-specific events still matter."
    ),
    "peg": (
        "**PEG Ratio**\n\n"
        "**Equation:**\n"
        "`PEG = P/E / Expected earnings growth rate (%)`\n\n"
        "A PEG near 1 is often treated as roughly fair for growth, but growth forecasts can be wrong.\n\n"
        "**Common mistake:** Using an unrealistically high growth rate to make PEG look cheap."
    ),
    "pb": (
        "**P/B (Price-to-Book)**\n\n"
        "**Equation:**\n"
        "`P/B = Market Price / Book Value per Share`\n\n"
        "Common for banks/NBFCs (HDFCBANK, SBIN). Pair with ROE and asset quality.\n\n"
        "**Common mistake:** Buying low P/B without checking NPAs or capital adequacy."
    ),
    "p/b": None,
    "delta": (
        "**Delta (Options Greek)**\n\n"
        "Approximate change in option premium for a ₹1 move in the underlying.\n\n"
        "• Call delta ≈ 0 to 1; Put delta ≈ −1 to 0\n"
        "• ATM options often have |delta| near 0.5\n\n"
        "**Common mistake:** Treating delta as a guarantee of directional profit — theta/vega still apply."
    ),
    "theta": (
        "**Theta (Time Decay)**\n\n"
        "Daily erosion of option value as expiry approaches (all else equal).\n\n"
        "Long options usually lose theta; short options earn theta but carry large risk.\n\n"
        "**Common mistake:** Buying far OTM weekly options and ignoring theta burn."
    ),
    "iv": (
        "**Implied Volatility (IV)**\n\n"
        "The volatility the options market is pricing in. Higher IV → more expensive options.\n\n"
        "IV often rises before RBI decisions, elections, and big earnings.\n\n"
        "**Common mistake:** Buying calls/puts only because a big move is expected — you may be buying peak IV."
    ),
    "implied volatility": None,
    "circuit": (
        "**Circuit Filter / Price Band**\n\n"
        "NSE/BSE halt trading in a stock beyond a ±% band from the reference price "
        "(bands often 5/10/20% depending on the stock).\n\n"
        "Index circuit breakers can pause the broader market on extreme moves.\n\n"
        "**Common mistake:** Assuming you can always exit at the last traded price during a freeze."
    ),
    "circuit breaker": None,
    "fii": (
        "**FII (Foreign Institutional Investor)**\n\n"
        "Overseas institutions investing in Indian markets. Persistent FII selling can pressure "
        "Nifty, banks, and IT; buying often supports rallies.\n\n"
        "**Common mistake:** Trading a single stock only because of one day of FII data."
    ),
    "dii": (
        "**DII (Domestic Institutional Investor)**\n\n"
        "Indian mutual funds, insurers, and other domestic institutions. SIP flows often create "
        "steady DII buying that can cushion FII outflows.\n\n"
        "**Common mistake:** Ignoring valuation and earnings while following flow headlines alone."
    ),
    "delivery": (
        "**Delivery Percentage**\n\n"
        "**Equation:**\n"
        "`Delivery % = Deliverable volume / Total traded volume`\n\n"
        "Higher delivery can indicate more genuine investment interest vs pure intraday churn.\n\n"
        "**Common mistake:** Treating one high-delivery day as proof of a long-term bottom."
    ),
    "nifty": (
        "**NIFTY 50**\n\n"
        "NSE’s flagship index of 50 large liquid companies. Used as the main market benchmark "
        "and as an F&O underlier (lot size set by exchange).\n\n"
        "In BYSEL, the index symbol is often referenced as NIFTY50."
    ),
    "sensex": (
        "**SENSEX**\n\n"
        "BSE’s 30-stock benchmark index. Moves closely with Nifty over time but constituents differ.\n\n"
        "In BYSEL, the symbol is SENSEX."
    ),
    "banknifty": (
        "**BANK NIFTY**\n\n"
        "NSE index of liquid banking stocks — highly traded in F&O. Sensitive to RBI policy, "
        "credit growth, and asset quality news.\n\n"
        "In BYSEL, the symbol is BANKNIFTY."
    ),
    "sma": (
        "**SMA (Simple Moving Average)**\n\n"
        "**Equation:**\n"
        "`SMA(N) = (P1 + P2 + … + PN) / N`\n\n"
        "Common lengths: 20, 50, 200. Price above rising 200-SMA is often treated as a long-term uptrend.\n\n"
        "**Common mistake:** Acting on every tiny SMA cross without volume/trend context."
    ),
    "ema": (
        "**EMA (Exponential Moving Average)**\n\n"
        "Like SMA but weights recent prices more heavily. Used in MACD (12/26/9).\n\n"
        "**Common mistake:** Assuming EMA is always better than SMA — both lag in choppy markets."
    ),
    "support": (
        "**Support**\n\n"
        "A price zone where demand historically appears and declines may pause/reverse.\n\n"
        "Often prior swing lows, consolidation bases, or high-volume nodes.\n\n"
        "**Common mistake:** Placing stops exactly on an obvious round-number support where stops cluster."
    ),
    "resistance": (
        "**Resistance**\n\n"
        "A price zone where supply historically appears and rallies may stall.\n\n"
        "Breakouts are more reliable with rising volume; failed breakouts often reverse fast.\n\n"
        "**Common mistake:** Shorting every first touch of resistance in a strong uptrend."
    ),
    "futures": (
        "**Stock / Index Futures (NSE)**\n\n"
        "Leveraged contracts to buy/sell an underlier at a future date. Marked to market daily; "
        "require SPAN/exposure margins. Lot sizes are exchange-defined.\n\n"
        "**Common mistake:** Ignoring overnight gap risk and margin calls."
    ),
    "options": (
        "**Options (NSE F&O)**\n\n"
        "Calls give the right to buy; puts the right to sell, at a strike before/on expiry. "
        "Premium is driven by spot, strike, time, and implied volatility (Greeks).\n\n"
        "**Common mistake:** Buying cheap far-OTM options as ‘lottery tickets’ without a probability plan."
    ),
}

# Aliases
_TERM_ANSWERS["p/e"] = _TERM_ANSWERS["pe"]
_TERM_ANSWERS["pe ratio"] = _TERM_ANSWERS["pe"]
_TERM_ANSWERS["p/e ratio"] = _TERM_ANSWERS["pe"]
_TERM_ANSWERS["p/b"] = _TERM_ANSWERS["pb"]
_TERM_ANSWERS["price to book"] = _TERM_ANSWERS["pb"]
_TERM_ANSWERS["risk/reward"] = _TERM_ANSWERS["risk reward"]
_TERM_ANSWERS["risk-reward"] = _TERM_ANSWERS["risk reward"]
_TERM_ANSWERS["bollinger bands"] = _TERM_ANSWERS["bollinger"]
_TERM_ANSWERS["relative strength index"] = _TERM_ANSWERS["rsi"]
_TERM_ANSWERS["moving average convergence divergence"] = _TERM_ANSWERS["macd"]
_TERM_ANSWERS["implied volatility"] = _TERM_ANSWERS["iv"]
_TERM_ANSWERS["circuit breaker"] = _TERM_ANSWERS["circuit"]
_TERM_ANSWERS["circuit filter"] = _TERM_ANSWERS["circuit"]
_TERM_ANSWERS["price band"] = _TERM_ANSWERS["circuit"]
_TERM_ANSWERS["simple moving average"] = _TERM_ANSWERS["sma"]
_TERM_ANSWERS["exponential moving average"] = _TERM_ANSWERS["ema"]
_TERM_ANSWERS["bank nifty"] = _TERM_ANSWERS["banknifty"]
_TERM_ANSWERS["nifty 50"] = _TERM_ANSWERS["nifty"]


def get_education_answer(query: str) -> Optional[str]:
    """Return a structured education answer if the query is definitional/formulaic."""
    q = (query or "").strip().lower()
    if not q:
        return None

    educational_cue = bool(
        re.search(
            r"\b(what is|what are|explain|define|definition|meaning of|formula|equation|how to calculate|tell me about)\b",
            q,
        )
    )
    formula_cue = bool(re.search(r"\b(formula|equation)\b", q))
    # Bare well-known terms also allowed (short educational prompts).
    bare_term = any(
        re.fullmatch(re.escape(term), q.strip(" ?!."))
        for term in _TERM_ANSWERS
        if term and _TERM_ANSWERS.get(term)
    )
    if not educational_cue and not formula_cue and not bare_term:
        return None

    for term in sorted(_TERM_ANSWERS.keys(), key=len, reverse=True):
        answer = _TERM_ANSWERS.get(term)
        if not answer:
            continue
        if re.search(r"\b" + re.escape(term) + r"\b", q):
            return answer

    return None
