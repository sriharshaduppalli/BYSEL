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
        "RSI is a **momentum oscillator** (0–100) — best as a *leading* cue in ranging markets; "
        "less reliable alone in strong trends.\n\n"
        "**Equation:**\n"
        "`RSI = 100 − (100 / (1 + RS))`\n"
        "`RS = Average Gain / Average Loss` over N periods (commonly 14)\n\n"
        "**Classic reads:**\n"
        "• Cross back below ~70 after overbought → short/fade cue (with structure)\n"
        "• Cross back above ~30 after oversold → long cue\n"
        "• **Divergence:** price new high + RSI lower high → bearish warning; "
        "price new low + RSI higher low → bullish warning\n\n"
        "**Advanced zone idea (literacy):** in bull phases some watch ~70 / ~40; "
        "in bear phases ~60 / ~30 — RSI can stay “overbought” for long stretches in trends.\n\n"
        "**NSE tip:** Pair with S/R and volume; use ADX/trend filter before fading extremes.\n\n"
        "**Common mistake:** Shorting every RSI > 70 in a strong Nifty bull leg."
    ),
    "macd": (
        "**MACD (Moving Average Convergence Divergence)**\n\n"
        "Hybrid **trend + momentum** tool (Gerald Appel). Uses EMAs so it lags less than "
        "simple two-MA systems, but still lags sharp turns.\n\n"
        "**Equations:**\n"
        "`MACD Line = EMA(12) − EMA(26)`\n"
        "`Signal Line = EMA(9) of MACD Line`\n"
        "`Histogram = MACD Line − Signal Line`\n\n"
        "**Signal families:**\n"
        "• Fast/slow **crossover** (best when away from the zero line)\n"
        "• **Centerline** cross — above 0 bullish momentum bias; below 0 bearish\n"
        "• **Divergence** vs price — often rarer but more meaningful for turns\n\n"
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
        "**Literacy cues:** squeeze (bands contract) often precedes a larger move; "
        "walks outside the band can continue a breakout; a top/bottom *outside* the band "
        "followed by a top/bottom *inside* can warn of reversal. Upper/lower act as "
        "dynamic resistance/support zones — not hard walls.\n\n"
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
    "risk management": (
        "**Risk Management & Trading Psychology — map**\n\n"
        "Risk is more than a stop-loss: single-trade risk, multi-position risk, and **portfolio** risk "
        "(variance, correlation, VaR) differ.\n\n"
        "**Core toolkit BYSEL covers:** systematic vs unsystematic risk · expected portfolio return · "
        "variance/covariance/correlation · equity curve / drawdowns · VaR · position sizing "
        "(% risk, % volatility, equity models) · **3-5-7 rule** · Kelly · trading biases.\n\n"
        "Stay solvent first — recovery after large losses is nonlinear.\n\n"
        "**Common mistake:** Focusing on entries while risking too much capital per trade.\n"
        "_Educational — Varsity-style Risk Management literacy, paraphrased._"
    ),
    "3-5-7 rule": (
        "**3-5-7 Rule (risk management framework) — educational**\n\n"
        "A simple capital-preservation checklist used by many active traders:\n\n"
        "• **3% — single-trade risk:** risk no more than **~3% of capital** on one trade "
        "(loss if the stop is hit, not full notional). "
        "`Risk ₹ ≈ Equity × 0.03` → `Qty ≈ Risk ₹ / |Entry − Stop|`\n"
        "• **5% — total open risk:** keep **combined open risk** across all positions "
        "at or below **~5% of capital** (sum of per-trade stop risks)\n"
        "• **7% — win payoff target:** aim for winners that deliver at least "
        "**~7% favorable move / return** (or an edge that compounds enough to outrun "
        "the smaller, controlled losses) so expectancy stays positive over many trades\n\n"
        "**BYSEL paper-practice tip:** size with the stop distance first (3%), refuse "
        "new entries that push total open risk past 5%, and prefer setups with room "
        "toward ~7% upside vs the entry (or solid R:R).\n\n"
        "**Common mistake:** Risking 3% of *notional position value* instead of 3% of "
        "*account equity if stopped out*.\n"
        "_Educational framework — not a guarantee; adapt to your risk profile._"
    ),
    "systematic risk": (
        "**Systematic vs Unsystematic Risk**\n\n"
        "• **Unsystematic (specific):** company/sector issues — reduced by **diversification**.\n"
        "• **Systematic (market):** rates, inflation, geopolitics, GDP shocks — shared by most stocks; "
        "**hedged** (e.g. index futures), not fully diversified away.\n\n"
        "**Common mistake:** Owning 5 stocks in the same theme and calling it diversified."
    ),
    "expected return": (
        "**Expected Return (Portfolio)**\n\n"
        "`E(Rp) = w1·R1 + w2·R2 + … + wn·Rn`\n"
        "Weights are portfolio fractions; Ri are expected returns (not guarantees).\n\n"
        "Example: 50% @ 20% + 50% @ 15% → **E(Rp) = 17.5%**.\n\n"
        "**Common mistake:** Treating expected return as a promised return."
    ),
    "portfolio variance": (
        "**Variance, Covariance & Portfolio Risk**\n\n"
        "• **Variance (σ²):** how returns spread around their mean; **σ = √variance**.\n"
        "• **Covariance:** whether two assets move together (+/−); magnitude via **correlation**:\n"
        "  `Corr(x,y) = Cov(x,y) / (σx·σy)`\n"
        "• Two-asset portfolio variance uses both vols and correlation — low/negative corr can "
        "cut portfolio risk vs single-name vol.\n\n"
        "**Common mistake:** Adding many highly correlated stocks and expecting risk to vanish."
    ),
    "equity curve": (
        "**Equity Curve**\n\n"
        "A plot of account equity over time (trade-by-trade or mark-to-market). Use it to see "
        "drawdowns, streakiness, and whether position sizing is destabilising the curve.\n\n"
        "A “good” system with poor sizing can still produce ugly equity curves.\n\n"
        "**Common mistake:** Judging a strategy on a few wins without looking at the full curve/DD."
    ),
    "portfolio optimization": (
        "**Portfolio Optimization (intuition)**\n\n"
        "Choose weights to improve the risk/return trade-off (e.g. target return with lower vol, "
        "or higher return for a risk budget) using expected returns + covariance structure.\n\n"
        "Solver tools (Excel Solver etc.) search weight combinations; constraints matter "
        "(no shorting, max weight, etc.). Outputs are model-dependent — garbage in, garbage out.\n\n"
        "**Common mistake:** Optimising on a short noisy sample and over-fitting weights."
    ),
    "value at risk": (
        "**Value at Risk (VaR)**\n\n"
        "Educational parametric form (normal approx):\n"
        "`VaR ≈ Portfolio value × z × σ_daily × √t`\n"
        "Common teaching shortcut: z ≈ **1.65** for ~95% one-tail.\n\n"
        "VaR is a loss **threshold** estimate for a horizon — not the maximum possible loss "
        "(tails/gaps can be worse). BYSEL can show 1d/10d VaR proxies from HV.\n\n"
        "**Common mistake:** Treating VaR as a hard worst-case stop."
    ),
    "position sizing": (
        "**Position Sizing (Active Trader)**\n\n"
        "Answers: **how much** capital/risk for this trade — not where to enter.\n\n"
        "**Ideas:**\n"
        "• Cap risk per trade (often ~0.5–2% of equity as *loss if stopped*, not full notional; "
        "the **3-5-7 rule** uses a **3%** single-trade cap as a stricter beginner-friendly ceiling)\n"
        "• Cap **total open risk** (e.g. **5%** across all open stops — see 3-5-7 rule)\n"
        "• Equity models: **core equity**, **total equity**, **reduced total equity** "
        "(locks only *locked-in* profits)\n"
        "• Van Tharp-style methods: units per fixed amount · % margin · **% volatility** (ATR)\n"
        "• Recovery trauma: after a 50% loss you need **+100%** to get back — size to stay in the game\n\n"
        "`Qty ≈ (Equity × Risk%) / |Entry − Stop|`\n\n"
        "**Common mistake:** Confusing margin blocked with money you can afford to lose."
    ),
    "kelly criterion": (
        "**Kelly Criterion**\n\n"
        "For a binary edge with win probability p and win/loss payoff ratio b (= R:R):\n"
        "`f* = p − (1−p)/b`\n\n"
        "Full Kelly is aggressive; many traders use **half/quarter Kelly** and hard caps "
        "(BYSEL often shows quarter-Kelly capped).\n\n"
        "Needs honest p and b — overestimating edge → overbetting.\n\n"
        "**Common mistake:** Using Kelly with fantasy win-rates from a tiny sample."
    ),
    "trading biases": (
        "**Trading Biases (Psychology)**\n\n"
        "Common traps:\n"
        "• **Gambler’s fallacy** — believing a streak must reverse (or continue)\n"
        "• **Anchoring** — stuck on an old price/entry as “truth”\n"
        "• **Recency** — overweighting the last few trades/news\n"
        "• **Confirmation** — seeking only agreeing evidence\n"
        "• **Hindsight** — “I knew it” after the move\n"
        "• Overconfidence / loss aversion / revenge trading after DD\n\n"
        "Antidote: written rules, position sizing, journal, pre-committed stops.\n\n"
        "**Common mistake:** Changing size based on the last streak instead of the plan."
    ),
    "recovery trauma": (
        "**Recovery Trauma (Loss Math)**\n\n"
        "Return needed to recover a loss:\n"
        "`Needed % = Loss% / (100% − Loss%)`\n\n"
        "Examples: −10% needs ~**+11.1%**; −20% needs **+25%**; −50% needs **+100%**.\n\n"
        "Small accounts that over-bet dig holes that are hard to climb — position size to survive.\n\n"
        "**Common mistake:** Doubling size after a loss to ‘make it back quickly’."
    ),
    "trading system": (
        "**Trading Systems — what to expect**\n\n"
        "A **trading system** is a quantified process: inputs → rules → output → trade/no-trade. "
        "Gut tips and TV opinions are not systems because they can’t be defined or backtested cleanly.\n\n"
        "A system is **not** a holy grail — it can lose streaks; risk/position sizing still matter.\n\n"
        "**Varsity-style systems BYSEL covers:** pair trading (correlation + regression/ADF cues) · "
        "calendar spreads · momentum portfolios.\n\n"
        "**Common mistake:** Expecting every system trade to win from day one.\n"
        "_Educational — Varsity Trading Systems literacy, paraphrased._"
    ),
    "pair trading": (
        "**Pair Trading Logic**\n\n"
        "Trade the **relationship** between two related stocks (often sector peers): go long one / "
        "short the other so you’re closer to **market-neutral** on the pair’s relative move.\n\n"
        "Track a variable such as:\n"
        "• **Ratio** = Price1 / Price2 (versatile)\n"
        "• **Spread** / differential of prices or returns\n\n"
        "**Convergence:** expect the variable to return toward its mean → trade mean reversion.\n"
        "**Divergence:** expect it to move further apart (less common as a default pair style).\n\n"
        "Glue: high **correlation** helps Method 1; Method 2 uses regression + cointegration checks.\n\n"
        "**Common mistake:** Pairing two random stocks because they ‘look similar on a chart’."
    ),
    "density curve": (
        "**Density Curve & Pair Triggers**\n\n"
        "The pair **ratio** wanders around its mean (mean reversion intuition). "
        "Normal-distribution bands help filter noise:\n"
        "• ~68% of observations within 1σ · ~95% within 2σ · ~99.7% within 3σ\n\n"
        "A practical trigger is the **z-score** of the ratio (or residual):\n"
        "`z = (value − mean) / σ`\n"
        "Many educational setups wait for |z| ≳ 2 before fading (short rich / long cheap), "
        "then exit near mean — always with stops and costs.\n\n"
        "Excel’s `NORM.DIST` can build a density/CDF view of the same idea.\n\n"
        "**Common mistake:** Fading every tiny |z|<1 wiggle and dying in costs."
    ),
    "linear regression pairs": (
        "**Pair Trading Method 2 — Regression & Errors**\n\n"
        "Model `StockA ≈ a + b·StockB` (OLS). Slope **b** is an educational hedge-ratio cue "
        "(shares of B per share of A).\n\n"
        "Residuals (errors) = actual A − fitted A. If residuals are mean-reverting, fade extremes.\n"
        "**Error ratio / R²** describe fit quality — weak R² → noisy pair.\n\n"
        "**Common mistake:** Using regression hedge ratios without checking residual stationarity."
    ),
    "adf test": (
        "**ADF Test (Cointegration Cue)**\n\n"
        "Correlation can be high even when the spread **wanders** (no reliable mean reversion). "
        "The **Augmented Dickey–Fuller (ADF)** test checks whether a series (often regression "
        "residuals) is stationary — a cointegration-style gate for pair trades.\n\n"
        "BYSEL may show a lightweight DF **proxy** (not a full p-value). Confirm with proper "
        "stats tooling before live size.\n\n"
        "**Common mistake:** Trading pairs on correlation alone and calling it ‘stat arb’."
    ),
    "momentum portfolio": (
        "**Momentum Portfolios**\n\n"
        "Momentum ≈ **rate of change of returns** (not just ‘price went up’). Prefer "
        "**percentage** ROC over absolute ₹ moves so expensive stocks aren’t falsely ‘stronger’.\n\n"
        "Portfolio approach: rank a universe by momentum (e.g. 20d/60d ROC), hold top sleeve, "
        "rebalance on a schedule, control risk with sizing/diversification.\n\n"
        "Consistency of trend can matter as much as a single spike day.\n\n"
        "**Common mistake:** Chasing one gap-up day and calling it a momentum process."
    ),
    "sip": (
        "**SIP (Systematic Investment Plan)**\n\n"
        "Invest a fixed amount at regular intervals (usually monthly) into a mutual fund.\n\n"
        "Benefit comes from rupee-cost averaging and compounding — not from timing one perfect entry.\n\n"
        "FV of SIP (ordinary annuity, educational):\n"
        "`FV ≈ PMT × [((1+r)^n − 1) / r] × (1+r)` where r = monthly rate, n = months.\n\n"
        "Popular teaching shortcut: the **15-15-15 rule** "
        "(₹15,000/month × 15 years × ~15% p.a. ≈ ~₹1 crore) — illustrative, not a promise.\n\n"
        "**Common mistake:** Stopping SIPs only because markets fell (often the best averaging period)."
    ),
    "15-15-15 rule": (
        "**15-15-15 Rule (SIP wealth illustration) — educational**\n\n"
        "A popular mutual-fund planning thumb rule for compounding via SIP:\n\n"
        "• **₹15,000** invested every month through a **SIP**\n"
        "• For **15 years** (n = 180 months)\n"
        "• At an assumed average **~15% annualised** return\n"
        "• Illustrative corpus ≈ **~₹1 crore**\n\n"
        "**Rough math (educational SIP FV):**\n"
        "Monthly rate `r ≈ 0.15/12`, months `n = 180`, `PMT = 15000`:\n"
        "`FV ≈ PMT × [((1+r)^n − 1) / r] × (1+r)` → about **₹1 crore** under a steady 15% path.\n\n"
        "**What to remember:**\n"
        "• 15% every year is an **assumption**, not a guarantee (equity returns vary by fund/era)\n"
        "• Inflation, taxes, exit loads, and TER reduce real take-home wealth\n"
        "• Consistency (not pausing SIPs in drawdowns) matters as much as the headline rate\n"
        "• Scale the same idea: change PMT / years / expected return for your goal\n\n"
        "**Common mistake:** Treating ₹1 crore as assured if you simply start any SIP.\n"
        "_Illustrative financial-planning concept — not a return promise or SEBI RA advice._"
    ),
    "personal finance": (
        "**Personal Finance (Mutual Funds) — map**\n\n"
        "Varsity-style path: **background** → **time value of money** → **retirement** → "
        "**mutual funds** (NAV, factsheet, equity/debt) → **returns & risk metrics** → "
        "**costs (TER / direct vs regular)** → **asset allocation & ETFs** → "
        "**goals / debt / insurance / emergency fund** review.\n\n"
        "Goal: grow purchasing power after inflation and taxes, with liquidity buffers — "
        "not chase the hottest fund.\n\n"
        "**Common mistake:** Buying products before writing goals, horizon, and emergency fund.\n"
        "_Educational — paraphrased Varsity Personal Finance literacy; not SEBI RA advice._"
    ),
    "time value of money": (
        "**Time Value of Money (TVM)**\n\n"
        "A rupee today beats a rupee later because it can earn a return (and inflation erodes cash).\n\n"
        "• **Future value (lump sum):** `FV = PV × (1+r)^n`\n"
        "• **Present value:** `PV = FV / (1+r)^n`\n"
        "• **Rule of 72:** years to double ≈ `72 / rate%`\n"
        "• **Real return:** `(1+nominal)/(1+inflation) − 1`\n\n"
        "SIPs are repeated deposits; use the annuity FV form (BYSEL SIP calculator).\n\n"
        "**Common mistake:** Comparing raw future ₹ goals without inflation-adjusting."
    ),
    "retirement planning": (
        "**Retirement Planning (education)**\n\n"
        "Estimate required **corpus**, then back-solve monthly savings/SIP.\n\n"
        "Teaching shortcut (4% rule style):\n"
        "`Corpus ≈ Annual expense / 0.04` (or your chosen withdrawal rate).\n\n"
        "Also model: years to retire · expected real return · longevity · healthcare buffer · "
        "pension/EPF/NPS sleeves separately from equity SIP.\n\n"
        "**Common mistake:** Using today’s expenses without inflation for a 20–30 year horizon."
    ),
    "mutual fund": (
        "**What are Mutual Funds?**\n\n"
        "A mutual fund **pools money** from many investors and invests in a diversified basket — "
        "equities, debt (bonds/debentures), money-market instruments, or a mix — under a stated "
        "scheme objective. An **AMC** (Asset Management Company) manages the portfolio within "
        "SEBI rules.\n\n"
        "You don’t ‘buy the fund company’ as the product — you buy **units** of a scheme that "
        "gives exposure to securities that can be hard to assemble yourself. Example: instead of "
        "buying all Nifty 50 stocks in the right weights, a **Nifty 50 index fund** holds them "
        "for you in index proportions.\n\n"
        "**How it works (simple loop)**\n"
        "1. Investors contribute (SIP or lump sum)\n"
        "2. Units are allotted at the applicable **NAV**\n"
        "3. Returns come from **NAV appreciation** and/or **dividends/IDCW** "
        "(growth plans typically reinvest)\n\n"
        "**Before you invest:** write goal · horizon · risk you can sit through without stopping "
        "the SIP · then pick category (equity/debt/hybrid/index) and compare costs.\n\n"
        "**Common mistake:** Treating last year’s star fund as next year’s forecast.\n"
        "_Educational primer — not SEBI RA advice or a product recommendation._"
    ),
    "how mutual funds work": (
        "**How Mutual Funds Work**\n\n"
        "• **Pool:** many investors → one scheme corpus\n"
        "• **Units @ NAV:** amount ÷ applicable NAV ≈ units allotted\n"
        "• **Earn:** (1) NAV rise when underlying assets rise "
        "(realised when you redeem) (2) dividends/IDCW if the plan pays them\n"
        "• **AMC role:** design schemes (including NFOs), invest per mandate, ops & compliance\n\n"
        "Routes: AMC app/site (often **direct**), or distributor/bank/platform (**regular** — "
        "usually higher TER).\n\n"
        "**Common mistake:** Confusing unit count with wealth — rupee value = units × NAV."
    ),
    "nav": (
        "**NAV (Net Asset Value)**\n\n"
        "`NAV per unit = (Total assets − Total liabilities) / Outstanding units`\n\n"
        "Assets ≈ market value of holdings + cash + receivables (accrued dividends/interest). "
        "Liabilities ≈ fees/expenses owed. NAV is typically computed each business day.\n\n"
        "**Toy example:** stocks ₹50cr + bonds ₹10cr + cash ₹2cr + receivables ₹1cr − "
        "liabilities ₹3cr = ₹60cr net; ÷ 6cr units → **NAV ₹10**.\n\n"
        "NAV rises when holdings/income rise; falls when markets drop or expenses rise.\n\n"
        "A ‘high NAV’ fund is not automatically expensive vs a ‘low NAV’ fund — "
        "compare **returns, risk, and TER**, not NAV level.\n\n"
        "**Common mistake:** Preferring ₹10 NAV schemes because they ‘look cheap’."
    ),
    "exit load": (
        "**Exit Load**\n\n"
        "A fee some schemes charge if you redeem before a stated holding period "
        "(e.g. ~1% if sold within 1 year — check the SID/KIM). It reduces redemption proceeds.\n\n"
        "Plan liquidity needs so you don’t pay exit load to fund an emergency.\n\n"
        "**Common mistake:** Ignoring exit load when comparing ‘similar’ funds."
    ),
    "amc": (
        "**AMC (Asset Management Company)**\n\n"
        "The intermediary that creates schemes (including NFOs), invests pooled money per the "
        "scheme objective, and runs day-to-day fund operations under regulation.\n\n"
        "Examples of fund houses: HDFC MF, ICICI Prudential MF, etc. You evaluate **schemes**, "
        "not just brand familiarity.\n\n"
        "**Common mistake:** Picking a fund only because you recognise the AMC logo."
    ),
    "types of mutual funds": (
        "**Types of Mutual Funds (education)**\n\n"
        "• **Equity** — stocks / equity-related; typically highest long-horizon volatility\n"
        "• **Debt** — bonds, G-Secs, corporate debt, T-bills; generally lower risk than equity "
        "(still rate/credit risk)\n"
        "• **Hybrid / balanced** — mix of equity + debt; moderate risk profile\n"
        "• **Index** — passively tracks an index (e.g. Nifty 50); cost & tracking matter\n"
        "• **ETF** — exchange-traded; live price vs NAV; needs demat/broker\n"
        "• **FoF** — invests in other schemes; risk depends on underlying funds\n\n"
        "Also: ELSS (tax-saving equity with lock-in), liquid/ultra-short, gilt, sector/thematic "
        "(less diversified).\n\n"
        "**Common mistake:** Buying five equity funds that all hold the same large-caps."
    ),
    "mutual funds vs fd": (
        "**Mutual Funds vs Fixed Deposits**\n\n"
        "| | Mutual funds | Bank FD |\n"
        "|---|---|---|\n"
        "| Nature | Market-linked portfolio | Predetermined interest |\n"
        "| Returns | Variable | Fixed (known at booking) |\n"
        "| Risk | Market / credit / rate risk | Typically low (issuer risk remains) |\n"
        "| Liquidity | Redeem to NAV (exit load/cut-offs may apply) | Premature exit often penalised |\n"
        "| Diversification | Across many securities | None |\n\n"
        "Use FDs for near-term safety sleeves; use MFs when horizon and risk capacity fit the "
        "category.\n\n"
        "**Common mistake:** Expecting FD-like certainty from equity mutual funds."
    ),
    "mutual funds vs stocks": (
        "**Mutual Funds vs Direct Stocks**\n\n"
        "• **MF:** pooled, diversified, professionally managed; redemption timelines depend on "
        "scheme (equity often T+1–T+3 business days; debt can be faster)\n"
        "• **Stocks:** direct ownership; you pick/monitor; higher concentration risk; sell in "
        "market hours\n\n"
        "Tax rules differ by instrument and holding period — verify current equity/debt MF and "
        "stock STCG/LTCG rules before deciding.\n\n"
        "**Common mistake:** Assuming ‘MF = no risk’ because a manager is involved."
    ),
    "mutual fund factsheet": (
        "**Reading a Mutual Fund Factsheet**\n\n"
        "Scan: investment objective · category · AUM · expense ratio · portfolio holdings & sector "
        "weights · portfolio turnover · exit load · benchmark · riskometer · manager tenure · "
        "rolling/trailing returns vs peers.\n\n"
        "Style drift and concentrated top holdings matter more than glossy marketing.\n\n"
        "**Common mistake:** Skipping TER, exit load, and benchmark when chasing past returns."
    ),
    "equity mutual fund": (
        "**Equity Mutual Funds (categories — education)**\n\n"
        "SEBI-style sleeves include large/mid/small/flexi/multi/focused/sectoral/thematic, "
        "ELSS (tax-saving with lock-in), and international equity funds.\n\n"
        "Higher equity share → higher long-horizon growth potential **and** drawdowns. "
        "Match category risk to horizon (e.g. short goals → not pure small-cap).\n\n"
        "**Common mistake:** Stacking five ‘different’ funds that all hold the same large-caps."
    ),
    "debt mutual fund": (
        "**Debt Mutual Funds & Bonds (intuition)**\n\n"
        "Debt funds hold money-market / bonds. Key drivers: **interest-rate risk** (duration) "
        "and **credit risk** (issuer quality). Shorter duration / higher quality → usually "
        "lower return and lower rate sensitivity.\n\n"
        "Bond price falls when yields rise (and vice versa), all else equal.\n\n"
        "**Common mistake:** Treating all debt funds as ‘safe like a savings account’."
    ),
    "index fund": (
        "**Index Funds**\n\n"
        "Passively track a published index (e.g. Nifty 50) with low active stock-picking. "
        "Aim is **tracking** the index, not beating it after costs.\n\n"
        "Judge via tracking error/difference and **TER** — not short-term outperformance claims.\n\n"
        "**Common mistake:** Paying active-fund fees for near-index portfolios."
    ),
    "arbitrage fund": (
        "**Arbitrage Funds (education)**\n\n"
        "Seek cash–futures (or similar) price gaps with hedged legs — equity-oriented for tax "
        "treatment in many cases, but return profile is often closer to low-volatility strategies "
        "than to pure equity beta. Read the scheme document; rules and tax labels can change.\n\n"
        "**Common mistake:** Expecting equity-bull returns from arbitrage sleeves."
    ),
    "etf": (
        "**ETF (Exchange Traded Fund)**\n\n"
        "Index (or theme) basket that trades on the exchange like a stock, with live market price "
        "vs indicative NAV. Useful for low-cost index exposure; watch liquidity, spreads, and "
        "tracking.\n\n"
        "Vs index mutual fund: ETF needs demat/broker; MF SIP UX is often simpler.\n\n"
        "**Common mistake:** Ignoring bid–ask spreads on thinly traded ETFs."
    ),
    "expense ratio": (
        "**Expense Ratio (TER) & Direct vs Regular**\n\n"
        "**TER** is the annualised cost charged inside the fund (management, ops, etc.). "
        "Higher TER compounds into large drag over decades.\n\n"
        "• **Direct** plans: you invest without distributor commission → usually **lower TER**.\n"
        "• **Regular** plans: include distributor trail → higher TER for the same portfolio.\n\n"
        "BYSEL can illustrate TER drag on a lump-sum growth path.\n\n"
        "**Common mistake:** Ignoring 0.5–1% TER differences because they ‘look small’ yearly."
    ),
    "rolling returns": (
        "**Rolling Returns**\n\n"
        "Instead of one fixed start/end date, compute returns over many overlapping windows "
        "(e.g. every possible 3y/5y period). Shows **consistency** and range of outcomes — "
        "better than cherry-picked trailing returns.\n\n"
        "**Common mistake:** Judging a fund only on the single best 5-year stretch."
    ),
    "fund risk metrics": (
        "**MF Risk / Return Metrics (education)**\n\n"
        "• **Standard deviation** — total volatility of returns\n"
        "• **Beta** — sensitivity vs fund benchmark/market\n"
        "• **Sharpe** — excess return per unit total vol\n"
        "• **Sortino** — excess return per unit downside vol\n"
        "• **Upside/downside capture** — how much of benchmark rallies/selloffs the fund captured\n\n"
        "Use with **rolling returns** and costs; metrics are backward-looking.\n\n"
        "**Common mistake:** Maximising Sharpe on a tiny sample and ignoring liquidity/credit risk."
    ),
    "asset allocation": (
        "**Asset Allocation**\n\n"
        "Split capital across equity, debt, gold, cash, etc. by **goal horizon and risk capacity**. "
        "Allocation usually drives long-term outcomes more than fund-picking within a sleeve.\n\n"
        "Rebalance periodically; glide paths often raise debt share as a goal nears.\n\n"
        "**Common mistake:** 100% equity for a 2-year house down-payment goal."
    ),
    "smart beta": (
        "**Smart Beta (factor / strategic beta)**\n\n"
        "Rules-based portfolios that tilt indexes by factors (value, quality, low vol, momentum, "
        "etc.) instead of pure market-cap weights. Still systematic — not discretionary stock tips.\n\n"
        "Factors can underperform for long stretches; costs and concentration still matter.\n\n"
        "**Common mistake:** Treating smart-beta as guaranteed outperformance vs plain index."
    ),
    "emergency fund": (
        "**Emergency Fund**\n\n"
        "Liquid buffer for job loss, medical shocks, repairs — typically **~3–12 months** of "
        "essential expenses in safe, accessible instruments (savings/liquid funds), **before** "
        "aggressive equity SIPs.\n\n"
        "A common sizing shortcut is the **3-6-9 rule of money**: hold **3, 6, or 9 months** "
        "of essential living expenses based on job stability / financial risk.\n\n"
        "**Common mistake:** Fully invested in equity with zero liquid buffer."
    ),
    "3-6-9 rule": (
        "**3-6-9 Rule of Money (emergency fund) — educational**\n\n"
        "A personal-finance guideline for sizing your **emergency fund** from essential "
        "monthly living costs (rent/EMI essentials, food, utilities, school fees, insurance "
        "premiums — not discretionary lifestyle spend):\n\n"
        "• **3 months** — more stable income (e.g. dual income, secure job, low fixed costs, "
        "strong family/backup support)\n"
        "• **6 months** — typical default for many salaried households (moderate job risk, "
        "single primary earner, normal fixed obligations)\n"
        "• **9 months** — higher uncertainty (self-employed / commission income, industry "
        "layoff risk, single income, dependents, variable cashflows)\n\n"
        "**How to use it:**\n"
        "1. List **essential** monthly expenses only\n"
        "2. Multiply by 3 / 6 / 9 for your risk band\n"
        "3. Keep the corpus in **liquid, low-volatility** options (savings, liquid/overnight "
        "funds — not locked equity or speculative bets)\n"
        "4. Build this **before** stretching aggressive equity SIPs\n\n"
        "**Common mistake:** Counting mutual-fund equity corpus as an emergency fund "
        "(it can fall when you need cash most).\n"
        "_Educational guideline — not a one-size guarantee; adapt to your household._"
    ),
    "personal finance review": (
        "**Personal Finance Review Checklist**\n\n"
        "1. **Goals** — amount, date, priority\n"
        "2. **Cashflow** — income vs expenses; SIP capacity\n"
        "3. **Debt** — high-interest debt before fancy products\n"
        "4. **Insurance** — term + health adequacy (separate from investing)\n"
        "5. **Emergency fund** — 3–12 months essentials\n"
        "6. **Asset allocation** — match horizons\n"
        "7. **Know your funds** — mandate, TER, overlap, rebalance\n\n"
        "**Common mistake:** Buying ULIPs/‘guaranteed’ marketing products before covering basics.\n"
        "_Insurance detail is a separate Varsity module — here we only flag adequacy._"
    ),
    "ipo": (
        "**IPO (Initial Public Offering)**\n\n"
        "A private company offers shares to the public and lists on NSE/BSE under SEBI rules.\n\n"
        "**Retail process (educational):**\n"
        "1. Read RHP / prospectus — business, risks, use of proceeds, valuations\n"
        "2. Apply via broker app using **ASBA / UPI** (funds blocked, not paid upfront)\n"
        "3. Choose category (retail / HNI / employee as eligible) and lots\n"
        "4. **Allotment** — if oversubscribed, retail often gets lottery / proportionate allotment\n"
        "5. Listing day — price can gap up or down; not a guaranteed listing gain\n\n"
        "**GMP** (grey-market premium) is unofficial chatter — not exchange data.\n\n"
        "**Common mistake:** Applying only because GMP looks high, without reading risks."
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
    "option theory": (
        "**Option Theory (NSE) — beginner map**\n\n"
        "Options = right (buyer) / obligation (seller) at a **strike** before/on **expiry**, "
        "for a **premium**.\n\n"
        "**Building blocks:** call & put buy/sell payoffs · intrinsic vs time value · "
        "moneyness (ITM/ATM/OTM) · Greeks (Δ Γ Θ Vega) · historical vs implied volatility · "
        "expiry P&L / M2M · physical settlement note for stock F&O.\n\n"
        "BYSEL can show educational ATM Black–Scholes Greeks using HV as σ "
        "(not a live IV surface).\n\n"
        "**Common mistake:** Memorising Greeks without writing max loss and breakeven first.\n"
        "_Educational — Varsity-style Option Theory literacy, paraphrased._"
    ),
    "call option": (
        "**Call Option Basics**\n\n"
        "A **call** gives the **buyer** the right (not obligation) to **buy** the underlier at the "
        "strike by expiry. The **seller/writer** is obligated if exercised and keeps the premium.\n\n"
        "Buyer wants the underlier to rise; seller is typically neutral-to-bearish / premium seller.\n"
        "On NSE many contracts are cash-settled by rules; stock F&O can involve physical settlement "
        "near expiry — confirm current NSE rules.\n\n"
        "**Common mistake:** Confusing ‘I bought a call’ with already owning the shares."
    ),
    "put option": (
        "**Put Option Basics**\n\n"
        "A **put** gives the **buyer** the right to **sell** at the strike. Profits if spot falls "
        "enough. The put seller keeps premium but faces losses if spot crashes.\n\n"
        "**Common mistake:** Using puts only as ‘lottery tickets’ without a size/expiry plan."
    ),
    "option premium": (
        "**Option Premium**\n\n"
        "Price of the option = what the buyer pays / seller receives.\n"
        "`Premium ≈ Intrinsic value + Time value`\n\n"
        "Premium moves with spot, strike distance, time to expiry, volatility (and rates). "
        "It is not fixed — it changes continuously in the market.\n\n"
        "**Common mistake:** Comparing two premiums without matching strike, expiry, and IV."
    ),
    "intrinsic value": (
        "**Intrinsic Value & Time Value**\n\n"
        "`Call intrinsic = max(0, Spot − Strike)`\n"
        "`Put intrinsic = max(0, Strike − Spot)`\n"
        "`Time value = Premium − Intrinsic` (≥ 0 in normal markets)\n\n"
        "At expiry, time value → 0 and premium → intrinsic. Before expiry, OTM options are "
        "pure time value.\n\n"
        "**Common mistake:** Paying a large time premium on far OTM weeklies into quiet markets."
    ),
    "moneyness": (
        "**Moneyness (ITM / ATM / OTM)**\n\n"
        "• **ATM** — strike ≈ spot\n"
        "• **Call ITM** — spot > strike; **Call OTM** — spot < strike\n"
        "• **Put ITM** — spot < strike; **Put OTM** — spot > strike\n\n"
        "ITM has intrinsic value; OTM does not. Moneyness drives delta and how fast theta/gamma bite.\n\n"
        "**Common mistake:** Buying deep OTM because ‘it’s cheap’ without probability context."
    ),
    "buying a call": (
        "**Buying a Call (Long Call)**\n\n"
        "Bullish, defined risk.\n"
        "`P&L at expiry ≈ max(0, Spot − Strike) − Premium`\n"
        "`Max loss = Premium` · `Breakeven = Strike + Premium` · upside theoretically large.\n\n"
        "**Common mistake:** Needing a huge move just to recover a rich premium."
    ),
    "selling a call": (
        "**Selling / Writing a Call (Short Call)**\n\n"
        "Mirror of the long call: `P&L ≈ Premium − max(0, Spot − Strike)`.\n"
        "`Max profit = Premium` · losses grow as spot rallies (theoretically large for naked shorts). "
        "Needs margin. Prefer spreads if you want defined risk.\n\n"
        "**Common mistake:** Naked short calls into a short-squeeze / gap-up event."
    ),
    "buying a put": (
        "**Buying a Put (Long Put)**\n\n"
        "Bearish / hedge, defined risk.\n"
        "`P&L at expiry ≈ max(0, Strike − Spot) − Premium`\n"
        "`Max loss = Premium` · `Breakeven = Strike − Premium`.\n\n"
        "**Common mistake:** Holding puts through IV crush after the feared event passes quietly."
    ),
    "selling a put": (
        "**Selling a Put (Short Put)**\n\n"
        "`P&L ≈ Premium − max(0, Strike − Spot)`.\n"
        "`Max profit = Premium` · large loss if spot gaps down. Often used when willing to buy "
        "stock at strike — still requires margin and risk rules.\n\n"
        "**Common mistake:** Treating short puts as ‘safe income’ in a crash."
    ),
    "delta": (
        "**Delta (Options Greek)**\n\n"
        "Approx change in premium for a ₹1 underlier move.\n\n"
        "• Call Δ ≈ 0 → 1; Put Δ ≈ −1 → 0\n"
        "• ATM |Δ| often near ~0.5\n"
        "• Also a rough probability-of-finishing-ITM heuristic (not exact)\n"
        "• Position delta ≈ sum(qty × Δ × multiplier)\n\n"
        "**Common mistake:** Hedging only with delta and ignoring gamma near expiry."
    ),
    "theta": (
        "**Theta (Time Decay)**\n\n"
        "Approx daily change in premium as time passes (other inputs fixed).\n\n"
        "Long options usually have **negative** theta (bleed); short options earn theta but "
        "carry tail risk. Theta accelerates near expiry for ATM options.\n\n"
        "**Common mistake:** Buying far OTM weeklies and ignoring theta burn."
    ),
    "vega": (
        "**Vega**\n\n"
        "Approx change in premium for a **1 percentage-point** change in implied volatility.\n\n"
        "Long options are usually long vega (help when IV rises); short options are short vega "
        "(hurt when IV spikes). Events often lift IV before the print.\n\n"
        "**Common mistake:** Buying options into peak IV and blaming ‘direction’ when IV collapses."
    ),
    "iv": (
        "**Implied Volatility (IV)**\n\n"
        "The volatility **implied by** the option’s market price (what the market is pricing in). "
        "Higher IV → richer premiums.\n\n"
        "Contrast with **historical volatility (HV)** from past returns. IV often rises before "
        "RBI, elections, and big earnings.\n\n"
        "**Common mistake:** Buying calls/puts only because a big move is expected — you may "
        "be buying peak IV."
    ),
    "historical volatility": (
        "**Historical Volatility (HV)**\n\n"
        "Realised volatility from past prices. Common approach:\n"
        "1. Daily log returns `ln(P_t / P_{t−1})`\n"
        "2. Stdev of returns\n"
        "3. Annualise ≈ `stdev × √252` (→ %)\n\n"
        "BYSEL reports HV20/HV60 and can use HV as σ in educational Black–Scholes Greeks "
        "when live IV is unavailable.\n\n"
        "**Common mistake:** Treating HV as a forecast — it describes the past."
    ),
    "greek interactions": (
        "**Greek Interactions**\n\n"
        "Greeks move together: a spot rally changes Δ (via Γ), time passage changes Θ, "
        "and IV shocks hit Vega — often at once around events.\n\n"
        "Near expiry, ATM options show high Γ and fast Θ. Deep ITM behave more like the "
        "underlier (high |Δ|); deep OTM have small Δ but can be lottery-priced.\n\n"
        "**Common mistake:** Optimising one Greek while blowing up another (e.g. short Θ, long disaster Γ)."
    ),
    "options m2m": (
        "**Options M2M & P&L**\n\n"
        "Option positions are marked as premiums change (and at expiry vs intrinsic). "
        "Buyer P&L ≈ change in premium (until exit) or expiry intrinsic − premium paid. "
        "Seller P&L is the mirror, with margin for adverse MTM.\n\n"
        "Square-off before expiry if you do not want exercise/assignment/physical obligations.\n\n"
        "**Common mistake:** Ignoring that premium can go to nearly zero before expiry even if "
        "your eventual direction was ‘eventually right’."
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
        "Equal weight on each bar — a classic **lagging / trend-following** tool. "
        "Common lengths: 21, 50, 89, 150, 200 (and weekly analogues).\n\n"
        "**Uses:** trend direction (MA rising/falling), price vs MA location, "
        "price↔MA or dual-MA **crossovers**, dynamic S/R. Longer MAs (e.g. 200-day) "
        "are watched on Nifty/Sensex as major pivots.\n\n"
        "**Common mistake:** Using MAs in tight trading ranges (many whipsaws)."
    ),
    "ema": (
        "**EMA (Exponential Moving Average)**\n\n"
        "Like SMA but weights recent prices more (`Multiplier ≈ 2/(N+1)`). "
        "Reacts faster — useful for shorter horizons; used in MACD (12/26/9).\n\n"
        "Trade-off: more sensitivity → earlier signals **and** more false breaks. "
        "Pick length/type per stock behaviour; always pair with risk rules.\n\n"
        "**Common mistake:** Assuming EMA is always better than SMA — both lag in choppy markets."
    ),
    "support": (
        "**Support & Resistance**\n\n"
        "**Support:** horizontal (or angled) floor where demand can halt a decline.\n"
        "**Resistance:** ceiling where supply can halt a rally.\n"
        "Think **zones**, not single ticks — especially on weekly charts. "
        "Break of support often turns that zone into resistance (and vice versa).\n\n"
        "Construction: swing highs/lows, congestions, round numbers, prior gaps. "
        "Markets “remember” levels traded with time and volume. Breakouts prefer "
        "participation (volume); failed breaks reverse fast.\n\n"
        "**Common mistake:** Stops exactly on round-number support, or shorting first touch "
        "of resistance in a strong uptrend."
    ),
    "resistance": (
        "**Resistance**\n\n"
        "A price zone where supply historically appears and rallies may stall.\n\n"
        "Breakouts are more reliable with rising volume; failed breakouts often reverse fast.\n"
        "See also **Support & Resistance** for construction and target ideas.\n\n"
        "**Common mistake:** Shorting every first touch of resistance in a strong uptrend."
    ),
    "futures and options": (
        "**What is Futures & Options (F&O)?**\n\n"
        "F&O are **derivatives** — contracts whose price is derived from an **underlying** "
        "(stock, index, commodity, ETF, etc.). Two parties agree on terms for a later date; "
        "they are used to **hedge** price risk or to express a view with **leverage**.\n\n"
        "• **Futures:** obligation to buy/sell (or cash-settle) at the contract price by expiry; "
        "daily **M2M**, margins, linear P&L.\n"
        "• **Options:** **buyer** has the **right** (not duty) to buy (call) or sell (put) at a "
        "**strike**; pays a **premium**. Seller/writer is obligated if exercised and keeps premium.\n\n"
        "Wrong direction + leverage can mean large losses; F&O suits people who understand "
        "underliers, margins, and expiry — not a casual ‘small tip’ product.\n\n"
        "**Go deeper:** futures · option theory · option strategies · hedgers/speculators/arbitrageurs.\n"
        "**Common mistake:** Treating F&O like cash stocks with ‘small’ size.\n"
        "_Educational — Groww/Varsity-style F&O basics, paraphrased. Not SEBI RA advice._"
    ),
    "futures vs options": (
        "**Futures vs Options — key difference**\n\n"
        "| | **Futures** | **Options (buyer)** |\n"
        "|---|---|---|\n"
        "| Obligation | Must follow through (or settle) | Right to exercise / exit; can walk away |\n"
        "| Upfront | Margin (not full notional) | Premium paid |\n"
        "| Risk shape | Symmetric / linear | Buyer max loss ≈ premium; seller risk can be large |\n"
        "| M2M | Daily on futures | Premium marked; sellers face margin |\n\n"
        "Futures lock price with **obligation** on both sides. Options let the **buyer** choose "
        "whether exercise is worthwhile; the **seller** takes the opposite obligation for premium.\n\n"
        "**Common mistake:** Buying options ‘because safer than futures’ without checking "
        "that time decay and IV can still wipe the premium."
    ),
    "f&o participants": (
        "**Who uses Futures & Options?**\n\n"
        "Three classic roles (same person can wear different hats):\n\n"
        "**1. Hedgers** — reduce volatility of an existing exposure. Example: a producer locks a "
        "sell price with futures; if spot falls, futures gains offset. With a **put**, they keep "
        "upside if prices rise (pay premium). Often linked to physical commodity/stock needs.\n\n"
        "**2. Speculators** — take a directional view for profit (usually **cash-settled** mindset).\n"
        "• Expect price **up** → **long** futures / buy calls (or bullish spreads)\n"
        "• Expect price **down** → **short** futures / buy puts (or bearish spreads)\n"
        "(Do **not** reverse this — a bullish view is long, not short.)\n\n"
        "**3. Arbitrageurs** — hunt mispricing (e.g. futures vs spot + **cost of carry**). "
        "Their trades push prices toward fair value / equilibrium.\n\n"
        "Retail F&O is usually leveraged: you post **margin**, not full contract value — "
        "profits and losses both scale up.\n\n"
        "**Common mistake:** Speculating with hedge-sized notions of ‘safety’."
    ),
    "hedgers": (
        "**Hedgers in F&O**\n\n"
        "Hedgers already have (or will have) exposure and use derivatives to **lock** a price "
        "or cap downside. Classic teaching example: a farmer sells futures to lock a crop price; "
        "if spot falls, futures P&L cushions the cash market; if spot rises, the futures side "
        "offsets the extra cash gain. A **put option** hedges downside while keeping upside "
        "(cost = premium).\n\n"
        "Equity investors may short index/stock futures against a long portfolio (β-hedge).\n\n"
        "**Common mistake:** Calling a naked directional F&O bet a ‘hedge’."
    ),
    "speculators": (
        "**Speculators in F&O**\n\n"
        "Speculators take risk to profit from expected price moves — often preferring "
        "**cash settlement** (P&L in money, no physical delivery intent).\n\n"
        "Direction (correct convention):\n"
        "• Bullish → **long** futures / long calls / bullish spreads\n"
        "• Bearish → **short** futures / long puts / bearish spreads\n\n"
        "Leverage amplifies both wins and losses; wrong calls + M2M can force exits early.\n\n"
        "**Common mistake:** Confusing ‘short’ with ‘buy later cheaper’ without understanding "
        "that short futures profit when prices **fall**."
    ),
    "arbitrageurs": (
        "**Arbitrageurs in F&O**\n\n"
        "Arbitrageurs exploit **price gaps** between related markets (e.g. futures vs spot, "
        "or across venues) after costs. Futures fair value embeds **cost of carry** "
        "(`F ≈ S×(1+Rf×T/365) − D`); large basis vs fair value can invite cash-and-carry style "
        "trades that push prices back toward equilibrium.\n\n"
        "True arb needs speed, capital, and low costs — retail ‘arb’ labels are often just "
        "relative-value speculation.\n\n"
        "**Common mistake:** Ignoring dividends, borrow, and brokerage so the ‘arb’ is negative."
    ),
    "cash settlement": (
        "**Cash Settlement vs Physical**\n\n"
        "• **Cash settlement:** no delivery of the asset — parties settle the difference between "
        "contract/settlement price and reference spot in **cash** (typical for index F&O).\n"
        "• **Physical settlement:** shares (or commodity) may be delivered around expiry "
        "(stock F&O in India can involve this — confirm current NSE rules).\n\n"
        "Speculators usually square off before expiry to avoid delivery obligations; "
        "hedgers sometimes intend the physical outcome.\n\n"
        "**Common mistake:** Holding stock F&O into expiry without knowing delivery/margin rules."
    ),
    "futures": (
        "**Futures Trading (NSE) — beginner map**\n\n"
        "A **futures** contract is a standardised, exchange-traded agreement to buy/sell an underlying "
        "(stock/index) at a future date. Unlike OTC **forwards**, futures are cleared, margined, and "
        "marked-to-market (M2M) daily — SEBI/exchange rules cut counterparty default risk.\n\n"
        "**Core ideas BYSEL covers:** lot size & contract value · leverage = notional/margin · "
        "SPAN+exposure margins & margin calls · M2M P&L · shorting overnight in futures "
        "(spot short is typically intraday) · Nifty futures liquidity / impact cost · "
        "pricing `F ≈ S·(1+Rf·T/365) − D` · beta hedging · open interest vs volume · "
        "physical settlement note for stock F&O.\n\n"
        "**Common mistake:** Treating futures like cash equity with ‘small’ size — leverage can wipe "
        "margin on a small adverse move (~1/leverage).\n"
        "_Educational — Varsity/Groww-style Futures literacy, paraphrased. Not SPAN / not tips._"
    ),
    "forwards": (
        "**Forwards vs Futures**\n\n"
        "**Forward:** private (OTC) agreement between two parties to exchange an asset later — "
        "customisable but has **counterparty risk**.\n"
        "**Futures:** exchange-standardised forward with daily **M2M**, margins, and clearing — "
        "the default for retail/traders on NSE.\n\n"
        "**Common mistake:** Assuming a forward ‘promise’ has the same safety as a cleared futures trade."
    ),
    "mark to market": (
        "**Mark to Market (M2M) on Futures**\n\n"
        "Each day, futures P&L is settled in cash vs the day’s reference/settlement price:\n"
        "`Daily MTM ≈ (Today settle − Prior ref) × Lot size` (sign flips for shorts).\n\n"
        "Profits credit / losses debit your ledger **before** expiry. Sum of daily MTMs ≈ "
        "`(Exit − Entry) × Lot` for a completed trade.\n\n"
        "**Common mistake:** Ignoring that a winning thesis can still force a **margin call** on a bad day."
    ),
    "margin call": (
        "**Margins & Margin Call (Futures)**\n\n"
        "Exchanges charge **SPAN + exposure** (and brokers may add buffers) so daily M2M losses "
        "can be covered. If losses eat available margin, you get a **margin call** — add funds or "
        "reduce/square off, or the broker may force-close.\n\n"
        "Product types (MIS/NRML/etc.) change how much margin is blocked — always check the "
        "live margin calculator; BYSEL’s % is only an educational ballpark.\n\n"
        "**Common mistake:** Using full buying power on MIS and getting squared off into a gap."
    ),
    "leverage": (
        "**Leverage in Futures**\n\n"
        "`Leverage ≈ Contract value / Margin blocked`\n"
        "`Rough wipeout % ≈ 1 / Leverage` (adverse move that can erase the margin).\n\n"
        "Example intuition: ~7× leverage → ~14% adverse move can wipe the posted margin; "
        "~40× leverage → ~2.5% move can. Futures P&L is **linear** and roughly a **zero-sum** "
        "transfer between longs and shorts (before costs).\n\n"
        "**Common mistake:** Chasing max leverage because ‘margin looks cheap’."
    ),
    "futures pricing": (
        "**Futures Pricing (Cost of Carry)**\n\n"
        "Theoretical fair value (Varsity-style):\n"
        "`F ≈ S × (1 + Rf × T/365) − D`\n"
        "where S = spot, Rf = funding rate, T = days to expiry, D = dividends over the period.\n\n"
        "**Basis** = Futures − Spot. Futures above fair/spot → **premium** (contango-like); "
        "below → **discount** (backwardation-like). Large gaps can invite cash-and-carry / "
        "calendar ideas — but costs, dividends, and margins matter.\n\n"
        "**Common mistake:** Trading ‘cheap futures’ without checking dividends and carry."
    ),
    "open interest": (
        "**Open Interest (OI)**\n\n"
        "OI = number of **open** futures/options contracts (one long + one short = OI 1). "
        "New positions raise OI; transfers between parties may leave OI unchanged; "
        "closing both sides lowers OI. **Volume** is day’s traded contracts — different from OI.\n\n"
        "Classic read (use with volume, not alone):\n"
        "• Price↑ + OI↑ → fresh long build\n"
        "• Price↑ + OI↓ → short covering\n"
        "• Price↓ + OI↑ → fresh short build\n"
        "• Price↓ + OI↓ → long liquidation\n\n"
        "**Common mistake:** Treating rising OI alone as bullish without price direction."
    ),
    "hedging with futures": (
        "**Hedging with Futures**\n\n"
        "Hedge = take an offsetting futures position so adverse moves hurt less "
        "(garden fence analogy). Diversify to cut **unsystematic** (company) risk; "
        "hedge with index futures to cut **systematic** (market) risk.\n\n"
        "**Single stock:** long spot → short same stock futures (sizes must match for a near-perfect hedge).\n"
        "**Portfolio:** `Hedge value ≈ Portfolio β × Portfolio value`; "
        "`Lots ≈ Hedge value / (Nifty futures × lot size)` — usually round to whole lots "
        "(under/over-hedge). Small notionals below one Nifty lot are hard to hedge with futures "
        "(options can help — separate module).\n\n"
        "**Common mistake:** Hedging with the wrong beta or wrong lot count and calling it ‘safe’."
    ),
    "shorting futures": (
        "**Shorting — Spot vs Futures**\n\n"
        "**Short** = sell first, buy later; profits if price falls.\n"
        "• **Spot short** in India is typically **intraday** — overnight short delivery can mean "
        "auction/penalty.\n"
        "• **Futures short** can be **carried overnight** with margin + daily M2M "
        "(same margin framework as longs).\n\n"
        "**Common mistake:** Carrying an equity short overnight without shares/borrow."
    ),
    "impact cost": (
        "**Impact Cost & Liquidity**\n\n"
        "Impact cost ≈ loss on an instant round-trip vs mid of bid/ask:\n"
        "`(Best ask − Best bid) / Mid × 100`\n\n"
        "Lower impact cost ⇒ better liquidity. Index futures (e.g. Nifty) usually have far lower "
        "impact cost than illiquid single stocks — important for market orders.\n\n"
        "**Common mistake:** Using market orders in thin names and blaming ‘broker slippage’ only."
    ),
    "calendar spread": (
        "**Calendar Spread (Futures / Trading Systems)**\n\n"
        "Simultaneously trade two expiries of the **same** underlying (e.g. buy near / sell far). "
        "Directional risk is largely cancelled; P&L comes from the **spread** between contracts "
        "(often small, cost-sensitive). Expiry-week dynamics can create signals.\n\n"
        "Classic approach uses fair-value/carry; systems approaches backtest spread entry/exit "
        "rules. Exchanges often give **margin benefit** vs two naked legs.\n\n"
        "**Common mistake:** Ignoring brokerage/STT so tiny spread edges vanish."
    ),
    "physical settlement": (
        "**Physical Settlement (Stock F&O — note)**\n\n"
        "Many stock F&O contracts in India can involve **physical settlement** of shares around "
        "expiry (rules evolved over time). Index futures/options are cash-settled. "
        "If you hold stock futures into expiry without intent to take/deliver stock, "
        "square off or roll earlier and know margin/delivery obligations.\n\n"
        "**Common mistake:** Ignoring expiry week physical-delivery obligations on stock F&O.\n"
        "_Confirm current NSE settlement rules before any live trade._"
    ),
    "nifty futures": (
        "**Nifty Futures**\n\n"
        "Index futures on the Nifty 50 — usually among the most liquid Indian derivatives. "
        "Available in consecutive month expiries (near / next / far). Diversified underlier "
        "reduces single-stock (unsystematic) noise vs stock futures; still carries market risk.\n\n"
        "Contract value = futures price × lot size (lot size changes over time — check NSE).\n\n"
        "**Common mistake:** Using outdated lot sizes from old tutorials."
    ),
    "options": (
        "**Options (NSE F&O)**\n\n"
        "Calls give the right to buy; puts the right to sell, at a strike before/on expiry. "
        "Premium is driven by spot, strike, time, and implied volatility (Greeks).\n\n"
        "Multi-leg **option strategies** (spreads, straddles, condors) define max profit/loss "
        "and breakevens — ask BYSEL for a named strategy or see **option strategies**.\n\n"
        "**Common mistake:** Buying cheap far-OTM options as ‘lottery tickets’ without a probability plan."
    ),
    "option strategies": (
        "**Option Strategies (NSE) — map**\n\n"
        "Build multi-leg structures to match a **view** (moderate vs strong move, range, vol):\n\n"
        "**Bullish:** Bull call / bull put spread (moderate) · Call ratio back spread / "
        "bear-call-ladder style (strong) · Synthetic long\n"
        "**Bearish:** Bear put / bear call spread (moderate) · Put ratio back spread (strong)\n"
        "**Volatile / unsure direction:** Long straddle · Long strangle\n"
        "**Range / premium sell:** Short straddle · Short strangle · Iron condor\n"
        "**Context tools:** Max pain · Put-Call Ratio (PCR)\n\n"
        "Always write: view → structure → max loss/profit → breakevens → margin → exit.\n"
        "_Educational — Varsity-style Option Strategies literacy, paraphrased. Not tips / not SPAN._"
    ),
    "bull call spread": (
        "**Bull Call Spread**\n\n"
        "Moderately bullish **debit** spread: **buy lower-strike call + sell higher-strike call** "
        "(same expiry).\n\n"
        "`Max loss = Net debit`\n"
        "`Max profit = Strike width − Net debit`\n"
        "`Breakeven = Lower strike + Net debit`\n\n"
        "Classic combo: ATM buy + OTM sell. Wider width → more potential profit but harder BE.\n\n"
        "**Common mistake:** Using this when you need unlimited upside (use naked/ratio instead)."
    ),
    "bull put spread": (
        "**Bull Put Spread**\n\n"
        "Moderately bullish **credit** spread: **sell higher-strike put + buy lower-strike put**.\n\n"
        "`Max profit = Net credit`\n"
        "`Max loss = Strike width − Net credit`\n"
        "`Breakeven = Higher strike − Net credit`\n\n"
        "Similar payoff shape to bull call spread; often preferred when put premiums/margin fit better.\n\n"
        "**Common mistake:** Ignoring assignment/margin just because it is a ‘credit’ trade."
    ),
    "bear put spread": (
        "**Bear Put Spread**\n\n"
        "Moderately bearish **debit** spread: **buy higher-strike put + sell lower-strike put**.\n\n"
        "`Max loss = Net debit`\n"
        "`Max profit = Strike width − Net debit`\n"
        "`Breakeven = Higher strike − Net debit`\n\n"
        "Caps risk vs naked long puts; also caps profit if the crash is huge.\n\n"
        "**Common mistake:** Strike width too tight so R:R is poor after costs."
    ),
    "bear call spread": (
        "**Bear Call Spread**\n\n"
        "Moderately bearish **credit** spread: **sell lower-strike call + buy higher-strike call**.\n\n"
        "`Max profit = Net credit`\n"
        "`Max loss = Strike width − Net credit`\n"
        "`Breakeven = Lower strike + Net credit`\n\n"
        "Payoff similar to bear put spread; choose calls vs puts using liquidity/IV/margin.\n\n"
        "**Common mistake:** Shorting calls into a squeeze without a defined long hedge wing."
    ),
    "call ratio back spread": (
        "**Call Ratio Back Spread**\n\n"
        "Strongly bullish (classic **1×2**): **sell 1 lower call + buy 2 higher calls**, often for "
        "**net credit**.\n\n"
        "`Max loss ≈ Strike width − Net credit` (worst near the higher strike)\n"
        "`Lower BE ≈ Lower strike + Net credit`\n"
        "`Upper BE ≈ Higher strike + Max loss`\n"
        "Large upside if a strong rally clears the upper BE; keep credit if market falls a lot.\n\n"
        "**Common mistake:** Treating it like a ‘safe credit’ — max loss zone sits mid-structure."
    ),
    "put ratio back spread": (
        "**Put Ratio Back Spread**\n\n"
        "Strongly bearish mirror of call ratio: **sell 1 higher put + buy 2 lower puts** "
        "(often net credit).\n\n"
        "Max-loss zone near the lower long strikes; large profit if a hard selloff clears the "
        "lower breakeven. Upper side can keep the credit if market rallies away.\n\n"
        "**Common mistake:** Undersizing margin for gap-down tail scenarios."
    ),
    "bear call ladder": (
        "**Bear Call Ladder**\n\n"
        "Despite the name, used when **outright bullish** — an improvisation on call ratio "
        "back spreads: finance long calls by selling an ITM call (often better credit than plain "
        "ratio). Payoff similar to ratio back spreads with a slightly different risk pocket; "
        "study Greeks/strikes before live use.\n\n"
        "**Common mistake:** Assuming ‘bear’ in the name means a bearish outlook."
    ),
    "synthetic long": (
        "**Synthetic Long & Options Arbitrage**\n\n"
        "**Synthetic long** ≈ **long call + short put** (same strike/expiry) — behaves like long "
        "futures/stock directionally.\n"
        "`Approx BE ≈ Strike + (Call prem − Put prem)`\n\n"
        "Put-call parity / mispricing can create brief arb vs futures after costs — usually "
        "thin for retail after margins and slippage.\n\n"
        "**Common mistake:** Ignoring that the short put carries futures-like downside risk."
    ),
    "long straddle": (
        "**Long Straddle**\n\n"
        "Buy **ATM call + ATM put** (same strike/expiry) when you expect a **large move** but "
        "are unsure of direction (events).\n\n"
        "`Max loss = Total premium`\n"
        "`Upper BE = Strike + Total premium`\n"
        "`Lower BE = Strike − Total premium`\n\n"
        "Needs realised move (or IV expansion) bigger than the premium paid; IV crush after "
        "events can hurt even if direction was ‘right but small’.\n\n"
        "**Common mistake:** Buying straddles when IV is already extremely elevated into the event."
    ),
    "short straddle": (
        "**Short Straddle**\n\n"
        "Sell ATM call + ATM put — profit if price stays near strike / IV falls.\n\n"
        "`Max profit = Total premium received`\n"
        "Breakevens same formula as long straddle; **loss theoretically large** outside the band. "
        "High margin. Prefer defined-risk alternatives (iron condor) unless experienced.\n\n"
        "**Common mistake:** Naked short straddles into binary events."
    ),
    "long strangle": (
        "**Long Strangle**\n\n"
        "Buy **OTM put + OTM call** (different strikes) — cheaper than straddle, needs a "
        "**bigger** move to win.\n\n"
        "`Max loss = Total premium`\n"
        "`Upper BE = Call strike + Net debit`\n"
        "`Lower BE = Put strike − Net debit`\n\n"
        "**Common mistake:** Expecting straddle-like win rates with far OTM wings."
    ),
    "short strangle": (
        "**Short Strangle**\n\n"
        "Sell OTM put + OTM call — collect premium if spot stays **between** strikes. "
        "Max profit = credit; losses grow outside breakevens; margin intensive.\n\n"
        "**Common mistake:** Selling thin-wing strangles for ‘extra premium’ without a hedge plan."
    ),
    "iron condor": (
        "**Iron Condor**\n\n"
        "Range strategy: **short put spread + short call spread** (four legs) — defined risk.\n\n"
        "`Max profit ≈ Net credit`\n"
        "`Max loss ≈ Wing width − Net credit` (equal wings)\n"
        "`Lower BE ≈ Short put strike − Credit`\n"
        "`Upper BE ≈ Short call strike + Credit`\n\n"
        "Prefer liquid index options; manage early if spot threatens a wing.\n\n"
        "**Common mistake:** Holding to expiry for the last tick when a wing is under attack."
    ),
    "max pain": (
        "**Max Pain Theory**\n\n"
        "Max pain is the expiry strike where **option writers** (as a group) would lose the "
        "least / option buyers suffer most — computed from call+put OI across strikes.\n\n"
        "It is a **heuristic**, not a magnet law. Useful as context with PCR/spot, never alone.\n\n"
        "**Common mistake:** Assuming spot ‘must’ pin max pain every expiry."
    ),
    "put call ratio": (
        "**Put-Call Ratio (PCR)**\n\n"
        "`PCR ≈ Put OI (or volume) ÷ Call OI (or volume)`\n\n"
        "Very high PCR is sometimes read as fear / put-heavy; very low as complacency — "
        "but levels mean different things for index vs stock and OI vs volume PCR. "
        "Use as sentiment context with price structure, not a standalone buy/sell.\n\n"
        "**Common mistake:** Comparing stock PCR to Nifty PCR as if scales match."
    ),
    "dividend yield": (
        "**Dividend Yield**\n\n"
        "**Equation:**\n"
        "`Dividend Yield = (Annual Dividend per Share / Price) × 100`\n\n"
        "Useful for income comparison within a sector. A very high yield can signal price stress "
        "or an unsustainable payout.\n\n"
        "**Common mistake:** Chasing yield without checking payout ratio and cash flows."
    ),
    "stt": (
        "**STT (Securities Transaction Tax)**\n\n"
        "Tax levied on Indian equity/F&O trades. Delivery equity buy/sell and F&O have different STT rates. "
        "It is part of your real trading cost along with brokerage, GST, exchange fees, and stamp duty.\n\n"
        "**Common mistake:** Ignoring STT when comparing intraday vs delivery profitability."
    ),
    "t+1": (
        "**T+1 Settlement**\n\n"
        "Indian equity cash currently settles on **T+1** (trade day + 1 business day). "
        "Delivery buys credit shares to demat after settlement; sells credit funds on settlement.\n\n"
        "**Common mistake:** Expecting instant withdrawable cash the same day as a delivery sell."
    ),
    "lot size": (
        "**Lot Size (F&O)**\n\n"
        "Exchange-defined contract size for futures/options (e.g. Nifty, Bank Nifty, stock F&O). "
        "Position value ≈ lot size × price × number of lots; margins are required.\n\n"
        "**Common mistake:** Trading multiple lots without checking SPAN/exposure margin."
    ),
    "stcg": (
        "**STCG vs listed equity (educational overview)**\n\n"
        "For **listed equity** (delivery), short-term capital gains generally apply when the "
        "holding period is **≤ 12 months**. Equity STCG has often been taxed at a special rate "
        "(historically discussed around **15%** plus surcharge/cess — **verify the latest Budget / IT rules**).\n\n"
        "**Also note:**\n"
        "• Intraday / F&O P&L is usually treated under **business income** rules, not equity LTCG/STCG delivery slabs\n"
        "• Mutual-fund tax depends on fund type and holding period\n"
        "• Broker contract notes + CA advice beat chat summaries\n\n"
        "**Common mistake:** Planning trades only for tax without considering risk and liquidity.\n"
        "_Not tax advice — rates and exemptions change._"
    ),
    "ltcg": (
        "**LTCG vs listed equity (educational overview)**\n\n"
        "For **listed equity** (delivery), long-term capital gains generally apply when holding "
        "period is **> 12 months**. Equity LTCG has often used a special rate with an annual "
        "exemption threshold (historically discussed around **10%** above a yearly exemption — "
        "**verify current Budget / IT rules**; thresholds have changed over years).\n\n"
        "**Common mistake:** Holding a weak thesis only to ‘wait for LTCG’.\n"
        "_Not tax advice — confirm with a CA for your case._"
    ),
    "demat": (
        "**Demat Account (how it works in India)**\n\n"
        "Electronic account that holds shares in dematerialised form via **NSDL/CDSL** through a "
        "**Depository Participant (DP)** — usually your broker.\n\n"
        "**How to open (typical retail path):**\n"
        "1. Pick a SEBI-registered broker / DP\n"
        "2. Complete KYC (PAN, Aadhaar, bank proof, photo/signature)\n"
        "3. Open linked **trading + demat** (and bank for payouts)\n"
        "4. E-sign / wet-ink as required; enable UPI/ASBA for IPOs if needed\n\n"
        "Delivery buys credit shares to demat after **T+1** settlement.\n\n"
        "**Common mistake:** Confusing trading ledger cash with settled withdrawable balance."
    ),
    "gamma": (
        "**Gamma (Options Greek)**\n\n"
        "Rate of change of **delta** as the underlier moves. High gamma near ATM/expiry means "
        "delta (and P&L) can change quickly — painful for naked short options on sharp moves.\n\n"
        "Long options are typically long gamma; short options are short gamma.\n\n"
        "**Common mistake:** Ignoring gamma risk when shorting options into event days."
    ),
    "rho": (
        "**Rho (Options Greek)**\n\n"
        "Sensitivity of premium to interest-rate changes. Usually a smaller driver for short-dated "
        "equity/index options in India vs Δ/Θ/Vega, but matters more for longer-dated options.\n\n"
        "**Common mistake:** Over-weighting rho on weekly Nifty options."
    ),
    "currency trading": (
        "**Currency, Commodity & G-Sec (India) — map**\n\n"
        "Alternate markets beyond equity F&O:\n"
        "1. **Currency derivatives** (NSE) — pairs like USDINR, EURINR, GBPINR, JPYINR + crosses\n"
        "2. **Commodities** (MCX / NCDEX) — gold, silver, crude, gas, base metals, agri\n"
        "3. **Government securities** — T-bills, dated G-Secs, SDLs (retail access via RBI/NSE rails)\n\n"
        "Prereqs: futures/options basics + event/macro awareness. Less retail liquidity than Nifty "
        "options — size and roll carefully.\n\n"
        "**Common mistake:** Trading gold/crude/FX like equities without contract specs & delivery rules.\n"
        "_Educational — Varsity-style CCG literacy, paraphrased._"
    ),
    "currency pair": (
        "**Currency Pairs & Quotes**\n\n"
        "FX trades as a **pair**: `Base / Quote = value`.\n"
        "Example: **USD/INR = 83** means 1 USD costs 83 INR (USD is base, INR is quote).\n\n"
        "International FX is huge and near-24×5; India offers exchange-traded currency futures/options "
        "on select pairs. Dual view: rising USDINR = USD strength / INR weakness (from INR lens).\n\n"
        "**Common mistake:** Mixing up which currency strengthens when the pair rises."
    ),
    "usdinr": (
        "**USDINR Pair (India)**\n\n"
        "Most liquid INR currency contract. Tracked vs RBI **reference rate**, US/India rate "
        "differentials, FII flows, crude (India import bill), risk sentiment, and RBI intervention talk.\n\n"
        "Futures usually trade near **interest-rate parity / cost of carry** vs spot; calendar spreads "
        "trade relative expiries. Confirm lot size/tick on NSE before sizing.\n\n"
        "**Common mistake:** Ignoring event risk (FOMC, RBI, geopolitics) overnight — FX gaps."
    ),
    "interest rate parity": (
        "**Interest Rate Parity (FX carry intuition)**\n\n"
        "Educational forward link:\n"
        "`F ≈ S × (1 + r_quote × T) / (1 + r_base × T)`\n"
        "For USDINR: S = spot, r_quote ≈ INR rate, r_base ≈ USD rate, T = days/365.\n\n"
        "If INR rates are higher than USD, USDINR forwards often sit at a premium to spot "
        "(carry). Real markets add risk premium, RBI actions, and liquidity.\n\n"
        "**Common mistake:** Treating IRP as a free arb after costs and margins."
    ),
    "cross currency": (
        "**Cross Currency Pairs**\n\n"
        "Pairs that do **not** include your home currency as quote — e.g. EURUSD, GBPUSD, USDJPY "
        "internationally. In India, some cross/INR combinations are available on exchange; "
        "global ‘majors’ dominate OTC FX.\n\n"
        "Crosses inherit drivers of both legs (rates, growth, risk).\n\n"
        "**Common mistake:** Trading a cross with only one-country news in mind."
    ),
    "commodity trading": (
        "**Commodity Trading (MCX / NCDEX)**\n\n"
        "**MCX** — metals & energy heavy (gold, silver, crude, natural gas, copper, …).\n"
        "**NCDEX** — agri focus (with notes that agri activity also exists on MCX).\n\n"
        "Know: quote unit, lot size, tick, expiry, **delivery logic** (often compulsory physical "
        "for many commodity futures). Prefer near-month liquidity; square off before delivery "
        "windows if you are a pure price trader.\n\n"
        "`P&L per tick ≈ (Lot size / Quote unit) × Tick size`\n"
        "`Contract value ≈ (Price × Lot size) / Quote unit`\n\n"
        "**Common mistake:** Holding into delivery without warehouse/intent processes."
    ),
    "gold": (
        "**Gold (MCX)**\n\n"
        "Bullion staple. Variants (names evolve): larger ‘Gold’, Gold Mini, smaller Guinea/Petal-style "
        "contracts — liquidity usually best in larger near-month contracts.\n\n"
        "Quote often **₹ per 10 grams** (all-in style on MCX); lot may be 1 kg for the big contract → "
        "P&L/tick commonly ₹100 when tick=₹1 on that spec (verify live specs).\n\n"
        "Domestic price tracks international $/oz × USDINR × duties/taxes. Factors: real rates, "
        "USD, geopolitics, jewellery demand, ETF flows.\n\n"
        "**Common mistake:** Trading illiquid micro contracts for ‘cheap margin’."
    ),
    "silver": (
        "**Silver (MCX)**\n\n"
        "Bullion twin to gold — often more volatile; industrial demand (solar/electronics) matters "
        "alongside monetary demand. Same futures discipline: specs, near-month liquidity, "
        "delivery awareness.\n\n"
        "**Common mistake:** Assuming silver always moves 1:1 with gold."
    ),
    "crude oil": (
        "**Crude Oil (MCX)**\n\n"
        "Typically among the most active energy contracts on MCX. Globally linked to benchmarks "
        "(e.g. WTI/Brent ecosystem), OPEC+/supply, demand, USD, inventories, geopolitics.\n\n"
        "India is a large importer — crude swings feed inflation and INR narratives. "
        "Contract units/ticks change over time — read the live MCX spec sheet.\n\n"
        "**Common mistake:** Oversizing crude because ‘margin % looks small’."
    ),
    "natural gas": (
        "**Natural Gas**\n\n"
        "Energy commodity with weather, storage, and regional supply drivers; can be very volatile. "
        "Trade only with clear contract specs and strict risk limits.\n\n"
        "**Common mistake:** Treating gas like a slow-moving bullion market."
    ),
    "base metals": (
        "**Base Metals (Copper, Aluminium, Lead, Nickel)**\n\n"
        "Industrial metals on MCX — sensitive to China/global growth, inventories, USD, and "
        "smelter/mine disruptions. Copper is often watched as a growth barometer.\n\n"
        "**Common mistake:** Ignoring inventory/warehouse news that can gap prices."
    ),
    "commodity options": (
        "**Commodity Options**\n\n"
        "Options on commodity futures (where available) — same Greeks intuition as equity options, "
        "but underlier is the commodity futures curve. Liquidity can be thinner than Nifty options; "
        "check open interest and spreads.\n\n"
        "**Common mistake:** Copying equity option size rules onto illiquid commodity options."
    ),
    "government securities": (
        "**Government Securities (G-Sec)**\n\n"
        "Loans to the government with **sovereign** backing.\n"
        "• **T-bills** — ≤1y, issued at discount to par, redeemed at par (no coupon)\n"
        "• **Dated G-Secs** — longer tenor, usually **semi-annual** coupon; symbols like "
        "`740GS2035` ≈ 7.40% coupon maturing 2035\n"
        "• **SDLs** — State Development Loans (state borrowing; similar coupon mechanics)\n\n"
        "Retail can access via RBI/NSE retail pathways (auction + secondary). Price moves "
        "inversely with yields.\n\n"
        "**Common mistake:** Treating G-Sec price as ‘always stable’ — yields move with RBI/inflation."
    ),
    "sovereign gold bond": (
        "**Sovereign Gold Bonds (SGB)**\n\n"
        "RBI-issued **government securities denominated in grams of gold**. You hold demat/certificate "
        "units — no physical bars to store or assay.\n\n"
        "**Return intuition**\n"
        "• Fixed interest (historically around **2.5% p.a.** on issue price, paid semi-annually — "
        "confirm the live tranche)\n"
        "• Principal linked to gold price at redemption / sale\n\n"
        "**Tenure & exit**\n"
        "Common tenor is **8 years**, with an early-exit window often after ~**5 years** on interest "
        "payment dates (series-specific). Secondary-market liquidity on exchange can be thin.\n\n"
        "**SGB vs gold ETF vs physical vs MCX**\n"
        "• **SGB** — sovereign issuer + interest; longer hold / exit rules\n"
        "• **Gold ETF** — tradeable units, expense ratio, no coupon\n"
        "• **Physical** — making charges, purity/storage risk, different tax path\n"
        "• **MCX futures** — leveraged price bet with margin & expiry risk\n\n"
        "**Tax sketch (verify yourself):** interest is usually taxable as income; maturity capital-gains "
        "treatment for individuals has often been more favourable than physical gold — rules change.\n\n"
        "**Common mistake:** Treating SGB like a day-trade gold vehicle or funding emergencies with it."
    ),
    "sgb vs gold etf": (
        "**SGB vs gold ETF**\n\n"
        "| | **SGB** | **Gold ETF** |\n"
        "|---|---|---|\n"
        "| Issuer / structure | RBI sovereign gold bond | AMC ETF tracking gold |\n"
        "| Income | Periodic interest (tranche-specific) | Usually none |\n"
        "| Costs | No TER; watch spreads if selling early | Expense ratio + brokerage |\n"
        "| Liquidity | Secondary can be thin | Typically easier intraday |\n"
        "| Horizon | Multi-year by design | Flexible trading horizon |\n\n"
        "Pick from **liquidity need + tax situation + whether you value the interest sleeve** — "
        "not from ‘both are gold so they’re identical’.\n\n"
        "**Common mistake:** Ignoring exit friction on SGB when you may need cash in 1–2 years."
    ),
    "treasury bill": (
        "**Treasury Bills (T-Bills)**\n\n"
        "Short-term G-Sec (commonly 91 / 182 / 364 days). Issued at a **discount**, mature at **par**.\n\n"
        "Educational yield form:\n"
        "`Yield ≈ (Discount / Price) × (365 / Days) × 100`\n"
        "Example intuition: buy 97, redeem 100 in 91 days → annualised yield illustration "
        "(live auction yields differ).\n\n"
        "**Common mistake:** Comparing T-bill discount % to a bond coupon without annualising."
    ),
    "bond yield": (
        "**Bond Yield & Coupons (G-Sec)**\n\n"
        "Coupon is the stated interest (often paid semi-annually on face value). "
        "Market price can be at discount/par/premium via auction/secondary trading. "
        "**YTM** assumes reinvestment of coupons — institutions compare bonds on YTM.\n\n"
        "When yields rise, existing bond **prices** fall (and vice versa).\n\n"
        "**Common mistake:** Buying only on coupon % while ignoring purchase price / YTM."
    ),
    "electricity derivatives": (
        "**Electricity Derivatives**\n\n"
        "Newer power-market derivatives on Indian exchanges — specialised contracts tied to "
        "electricity price benchmarks. Treat as advanced: understand delivery/settlement design "
        "and liquidity before any paper size.\n\n"
        "**Common mistake:** Assuming electricity contracts behave like gold or crude."
    ),
    "nifty pe": (
        "**Nifty P/E (Index Valuation)**\n\n"
        "Aggregate price-to-earnings for Nifty constituents — a broad valuation thermometer, "
        "not a timing signal by itself. Compare with history and earnings growth backdrop.\n\n"
        "**Common mistake:** Going all-cash solely because Nifty P/E looks ‘high’."
    ),
    "stock market": (
        "**Stock Market Meaning (India)**\n\n"
        "A regulated marketplace where shares of publicly listed companies are bought and sold. "
        "Companies issue shares to raise capital; buyers become shareholders.\n\n"
        "**In India:** trading mainly happens on **NSE** and **BSE**, overseen by **SEBI**.\n\n"
        "**How it works (short):** IPO/listing → trade via broker app → exchange matches orders → "
        "T+1 settlement into demat.\n\n"
        "**Common mistake:** Treating tips/rumours as research."
    ),
    "how does the stock market work": (
        "**How the Indian Stock Market Works (beginner guide)**\n\n"
        "1. **IPO (primary market):** Company offers shares to the public to raise capital (SEBI rules).\n"
        "2. **Listing:** Shares list on NSE and/or BSE for everyday trading (secondary market).\n"
        "3. **Broker / app:** You place buy/sell orders through a registered broker (trading + demat).\n"
        "4. **Order matching:** The exchange matching engine pairs compatible buy and sell prices in real time.\n"
        "5. **Settlement (T+1):** Next trading day, shares credit/debit demat and funds settle.\n\n"
        "**Core idea:** different opinions make a market — buyers and sellers meet electronically.\n"
        "**What moves prices:** news/events (company, sector, macro) and demand–supply "
        "(liquid names move even on quiet days).\n"
        "**After you own shares:** demat holding + possible dividends/bonus/split/rights/voting.\n"
        "**Returns:** absolute % for ≤1y holds; CAGR for multi-year compares.\n"
        "**Styles:** day / scalp / swing traders vs growth / value investors — pick by holding period & risk.\n\n"
        "**Participants:** retail, traders, institutions (MF/FII/DII), NSE/BSE, NSDL/CDSL, DPs, SEBI.\n\n"
        "Educational overview for paper practice — not investment advice."
    ),
    "share price": (
        "**How Share Prices Are Determined**\n\n"
        "**Primary factor:** demand and supply on the exchange order book.\n"
        "• More buyers than sellers at a price → price tends to rise\n"
        "• More sellers than buyers → price tends to fall\n\n"
        "**Also matters:** earnings/EPS & margins, interest rates & inflation, sector trends, "
        "company/policy/global news, bullish/bearish sentiment, FII/DII/MF activity, and liquidity.\n\n"
        "**Common mistake:** Assuming short-term spikes always equal fundamental value."
    ),
    "nsdl": (
        "**NSDL (National Securities Depository Limited)**\n\n"
        "One of India’s two securities depositories. Holds shares in electronic (demat) form.\n\n"
        "Retail investors typically access NSDL via a **Depository Participant (DP)** — "
        "usually their broker or bank — not by opening an account directly with NSDL.\n\n"
        "**Common mistake:** Confusing the depository with the stock exchange (NSE/BSE)."
    ),
    "cdsl": (
        "**CDSL (Central Depository Services Limited)**\n\n"
        "India’s other major securities depository alongside NSDL. Holds dematerialised securities.\n\n"
        "Your broker/bank as DP connects your demat to CDSL or NSDL for credits, debits, and transfers.\n\n"
        "**Common mistake:** Thinking demat and trading account are the same thing."
    ),
    "depository participant": (
        "**Depository Participant (DP)**\n\n"
        "The link between you and depositories (NSDL/CDSL). Usually a stockbroker or bank.\n\n"
        "**DP helps you:** open/maintain demat, hold & transfer securities, and receive corporate actions.\n"
        "**Trading account** places orders on NSE/BSE; **demat** stores settled delivery shares.\n\n"
        "**Common mistake:** Ignoring DP/AMC charges when choosing a broker."
    ),
    "start investing": (
        "**How to Start Investing in the Share Market (India — educational)**\n\n"
        "1. Open **trading + demat** with a registered broker/DP\n"
        "2. Complete **KYC** (typically PAN, Aadhaar, bank details — verify current requirements)\n"
        "3. Add funds from your bank to the trading ledger\n"
        "4. Research company fundamentals & industry (not tips)\n"
        "5. Place an order (quantity + price you understand)\n"
        "6. Start small; size risk first\n"
        "7. Diversify across sectors/names\n"
        "8. Monitor vs your goal — adjust with a plan\n\n"
        "BYSEL is ideal for paper-practicing research and process before live money.\n\n"
        "**Common mistake:** Skipping research and copying WhatsApp tips."
    ),
    "common mistakes": (
        "**Common Share-Market Mistakes to Avoid**\n\n"
        "• Investing without basic knowledge\n"
        "• Following tips or rumours blindly\n"
        "• Overtrading / revenge trading\n"
        "• Ignoring diversification\n"
        "• Trying to time every market tick perfectly\n"
        "• Letting fear or greed override a written plan\n"
        "• No clear goal or risk budget\n"
        "• Chasing big green candles after the move\n\n"
        "**Process fix:** thesis → entry → stop → invalidation → journal.\n"
        "Educational checklist — not personalized advice."
    ),
    "primary market": (
        "**Primary vs Secondary Market**\n\n"
        "**Primary:** company issues new shares (IPO/FPO/rights) and receives the capital.\n"
        "**Secondary:** investors trade already-listed shares with each other on NSE/BSE; "
        "the company usually does not get that trade cash.\n\n"
        "**Common mistake:** Thinking every stock purchase pays money to the company."
    ),
    "what moves the stock": (
        "**What Moves Stock Prices?**\n\n"
        "Market participants react to **news and events** — company-specific, industry-wide, or "
        "macro/political — and that reaction becomes buying or selling.\n\n"
        "• **Bullish tape:** buyers often pay up through rising ask prices (prices climb quickly)\n"
        "• **Sector news:** can hit many stocks in the same industry, not just one name\n"
        "• **No news:** liquid large-caps still move on demand/supply; illiquid unknowns may stay flat\n"
        "• **Expectations** of future news also move prices before the event\n\n"
        "**Common mistake:** Assuming a quiet news day means zero price movement.\n"
        "_Educational — inspired by standard India market literacy (Varsity-style Module 1)._"
    ),
    "absolute return": (
        "**Absolute Return vs CAGR**\n\n"
        "**Absolute return** = `(End / Start − 1) × 100`\n"
        "Use when the hold is roughly **one year or less**.\n"
        "Example: buy ₹3,030 → sell ₹3,550 → **17.16%** absolute.\n\n"
        "**CAGR** = `(End / Start)^(1/years) − 1`\n"
        "Use to compare **multi-year** growth rates fairly.\n"
        "Same prices over **2 years** → about **8.2% CAGR**.\n\n"
        "For short wins, don’t blindly annualize (17% in 6 months ≠ guaranteed 34% year).\n"
        "Try: `CAGR of 3030 to 3550 in 2 years` or `return from 3030 to 3550`.\n\n"
        "**Common mistake:** Comparing a 3-year absolute % to a 6-month absolute % without CAGR."
    ),
    "trader vs investor": (
        "**Where Do You Fit — Trader or Investor?**\n\n"
        "**Traders** (shorter horizon, active risk management):\n"
        "• **Day trader** — open & close same day; no overnight hold\n"
        "• **Scalper** — many quick trades for small ticks (often large size)\n"
        "• **Swing trader** — holds days to a few weeks\n\n"
        "**Investors** (longer horizon):\n"
        "• **Growth** — companies expected to grow with industry/macro shifts\n"
        "• **Value** — good businesses temporarily beaten down by sentiment\n\n"
        "Style follows **holding period + risk tolerance**. Use BYSEL paper trades to discover fit.\n"
        "_Educational — not a recommendation to trade or invest any style._"
    ),
    "holding period": (
        "**Holding Period**\n\n"
        "How long you intend to keep a position — minutes, days, months, or years.\n\n"
        "• Minutes–hours → scalp / intraday\n"
        "• Days–weeks → swing\n"
        "• Years → investing\n\n"
        "There is no universally “best” period (some legends prefer “forever”). "
        "Match period to your written process and risk budget.\n\n"
        "**Common mistake:** Calling yourself a long-term investor while panic-selling every dip."
    ),
    "after you own stock": (
        "**What Happens After You Own a Stock?**\n\n"
        "Delivery shares credit your **demat** — you become a **fractional owner** "
        "(even 200 shares of a large cap is a tiny %).\n\n"
        "Possible corporate privileges (when announced/eligible):\n"
        "• Dividends · Bonuses · Stock splits · Rights issues · Buybacks · Voting rights\n\n"
        "**Common mistake:** Expecting dividends every quarter from every company."
    ),
    "technical analysis": (
        "**Technical Analysis (TA) — beginner (NCFM-style literacy)**\n\n"
        "TA forecasts probable price behaviour from **past price, volume, and (for F&O) open interest** "
        "on charts — across intraday to multi-year frames. FA asks *why* a price should be fair; "
        "TA focuses on *what* price is doing (supply vs demand).\n\n"
        "**Three classic assumptions:**\n"
        "1. **Market discounts everything** — known news, psychology, and fundamentals are in the price\n"
        "2. **Prices move in trends** — once a trend is established, continuation is more likely than random flips\n"
        "3. **History tends to rhyme** — crowd reactions create repeating chart patterns\n\n"
        "**Strengths:** universal across instruments; focuses on timing/entry; maps S/R and risk; "
        "charts are a fast pictorial history. **Weaknesses:** subjective, open to bias, signals can "
        "arrive late, “always another level,” and not every pattern works the same on every stock.\n\n"
        "**Top-down idea:** index → promising sectors → shortlist stocks → manage risk.\n"
        "Many practitioners treat markets as heavily **psychological** — capital preservation "
        "beats maximising every profit.\n\n"
        "**Building blocks BYSEL covers:** candles, chart patterns, S/R, volume, MAs, RSI, MACD, "
        "Stochastic, Bollinger, MFI, Fib, Dow / Elliott literacy, pivots/CPR, ATR / 3-5-7 risk.\n\n"
        "**Common mistake:** Using every indicator at once with no written risk plan.\n"
        "_Educational paraphrase of classic / NCFM TA frameworks — not NSE copyrighted text "
        "and not investment advice._"
    ),
    "ncfm technical analysis": (
        "**NCFM Technical Analysis Module (NSE education path) — educational**\n\n"
        "NSE’s **NCFM** programme includes a **Technical Analysis** certification module "
        "(curriculum on nseindia.com → Education → Certifications). Typical exam style: "
        "objective questions, timed test, pass marks and certificate validity as published by NSE "
        "(verify live fee/duration/pass % on the exchange site — they change).\n\n"
        "**Curriculum weight (classic outline):** Intro to TA · Candle charts · Pattern study · "
        "Indicators & oscillators · Trading strategies · Dow & Elliott · Psychology & risk.\n\n"
        "Related paths often mentioned alongside: other NCFM modules, **NISM** exams "
        "(e.g. equity derivatives for sales/approved users), FPSB/CFP tracks — each has its own "
        "rules. BYSEL teaches the *concepts* for paper practice; a certificate is separate.\n\n"
        "**Common mistake:** Treating a certificate as a guaranteed trading edge without a risk plan.\n"
        "_Check www.nseindia.com for current modules, fees, and updates._"
    ),
    "market timings": (
        "**Indian market timings (from 3 Aug 2026)**\n\n"
        "• **Open:** 9:15 AM IST (pre-open ~9:00–9:15)\n"
        "• **F&O stocks (cash):** continuous till **3:15 PM**, then **Closing Auction Session (CAS)** "
        "till **3:35 PM** (closing price from the auction)\n"
        "• **Non-F&O cash:** continuous till **3:30 PM**\n"
        "• **Equity derivatives:** till **3:40 PM**\n"
        "• **Post-close:** short window around **3:50–4:00 PM** (exchange rules)\n\n"
        "There is no longer one universal 3:30 close for every segment. Broker MIS square-off times "
        "for CAS stocks may differ — check your broker.\n"
        "_Educational summary — verify live NSE/BSE/SEBI circulars._"
    ),
    "sentiment analysis": (
        "**Sentiment analysis (stocks / market) — beginner**\n\n"
        "Sentiment is the **crowd mood** around a stock or the market — optimistic, fearful, or mixed. "
        "BYSEL scores it as a **multi-factor educational stack**, not a crystal ball:\n\n"
        "1. **News tone** — recent headlines (upgrade/deal/profit vs probe/miss/downgrade)\n"
        "2. **Momentum** — RSI / short-term ROC (overbought can mean crowded bullishness)\n"
        "3. **Trend** — Supertrend / MA alignment\n"
        "4. **MACD** — histogram direction\n"
        "5. **Volume** — whether the move is confirmed or thin\n"
        "6. **Relative strength** — stock vs Nifty (stock asks)\n\n"
        "Bullish sentiment ≠ buy tip. Extreme readings often mean **crowding** — confirm with "
        "structure, levels, and risk. For a named stock, ask: *sentiment of RELIANCE* / "
        "*TCS market sentiment*.\n\n"
        "**Common mistake:** Trading headlines alone while ignoring trend and volume.\n"
        "_Educational — not investment advice._"
    ),
    "candlestick": (
        "**Candlesticks & Chart Types**\n\n"
        "Each candle summarizes **OHLC**: Open, High, Low, Close.\n"
        "• Body = open→close · Wicks = extremes beyond the body\n"
        "• Traders prefer candles over plain bars because body vs wick shows conviction vs indecision fast\n\n"
        "**Single patterns (literacy):** Marubozu (strong body), Doji (indecision), "
        "Spinning top, Hammer / Hanging man, Shooting star / Inverted hammer.\n"
        "**Multi patterns:** Engulfing, Harami, Morning/Evening star, Dark cloud / Piercing; "
        "gaps around news.\n"
        "For multi-bar structures (H&S, triangles, flags), ask about **chart patterns**.\n\n"
        "**Common mistake:** Trading a pattern in the middle of nowhere without S/R or trend context."
    ),
    "marubozu": (
        "**Marubozu Candlestick**\n\n"
        "• **Bullish Marubozu:** long green body, little/no wicks → aggressive buying\n"
        "• **Bearish Marubozu:** long red body → aggressive selling\n\n"
        "Paper setup idea: trade in the direction of the Marubozu with a stop beyond the opposite end "
        "of the candle; target next S/R. Confirm with volume.\n\n"
        "**Common mistake:** Buying every green Marubozu into major resistance."
    ),
    "doji": (
        "**Doji & Spinning Top**\n\n"
        "**Doji:** open ≈ close (or nearly) → indecision / tug-of-war. Meaningful mainly when "
        "dojis are *not* common on that chart (very short intraday frames print many near-dojis). "
        "After a long white candle in an uptrend, a doji is a stronger warning that buyers are "
        "hesitant — still wait for confirmation. Bottoms often need more confirmation than tops.\n"
        "**Spinning top:** small body, longer wicks → balance / pause.\n\n"
        "**Common mistake:** Exiting a healthy trend on a single Doji with no follow-through."
    ),
    "hammer": (
        "**Hammer & Hanging Man**\n\n"
        "Same shape: small body near the high, **long lower wick**.\n"
        "• After a decline → **Hammer** (potential bullish reversal cue)\n"
        "• After a rally → **Hanging man** (potential bearish warning)\n\n"
        "Thought process: long lower wick = sellers pushed price down but buyers recovered.\n"
        "Confirm next candle/volume; stop often beyond the wick extreme.\n\n"
        "**Common mistake:** Treating every hammer mid-trend as a must-buy."
    ),
    "engulfing": (
        "**Engulfing Candlestick**\n\n"
        "**Bullish engulfing:** tall green body fully covers prior red body → buyers take over.\n"
        "**Bearish engulfing:** tall red body covers prior green → sellers take over.\n\n"
        "Best near S/R after a clear prior trend, with volume expansion on the engulfing day. "
        "Wait for the pattern to complete (close) before acting.\n\n"
        "**Common mistake:** Taking engulfing signals against a strong higher-timeframe trend blindly."
    ),
    "harami": (
        "**Harami Candlestick**\n\n"
        "Two-candle pattern: a **large body** followed by a **small body** completely inside "
        "the first body (a “spinning top” inside). Colour often opposite.\n\n"
        "Often treated as a pause / possible reversal cue, but breakouts can go either way — "
        "confirm with the next break of the large candle’s range. A doji as the second candle "
        "is a common variation.\n\n"
        "**Common mistake:** Shorting/buying on harami alone without waiting for confirmation."
    ),
    "shooting star": (
        "**Shooting Star & Inverted Hammer**\n\n"
        "Small body near the low with a **long upper wick** (inverted hammer/hanging-man shape).\n"
        "• After a rally → **Shooting star** (potential bearish warning)\n"
        "• After a decline → **Inverted hammer** (potential bullish cue)\n\n"
        "Confirm with next candle; stop beyond the wick extreme.\n\n"
        "**Common mistake:** Fading every long upper wick in a strong trend."
    ),
    "dark cloud cover": (
        "**Dark Cloud Cover & Piercing Line**\n\n"
        "**Dark cloud cover:** after an up move, a bearish candle opens above prior close/high "
        "zone and closes well into the prior bullish body — potential downward reversal cue.\n"
        "**Piercing line:** mirror image after a decline — bullish candle closes well into "
        "prior bearish body — potential upward reversal cue.\n\n"
        "Confirm with follow-through; manage risk with a stop beyond the pattern extreme.\n\n"
        "**Common mistake:** Acting before the second candle closes."
    ),
    "morning star": (
        "**Morning Star & Evening Star**\n\n"
        "**Morning star:** down move → small indecision candle → strong up candle "
        "(potential bullish reversal).\n"
        "**Evening star:** up move → indecision → strong down (potential bearish reversal).\n"
        "Gaps into the middle candle are common but not required.\n\n"
        "**Common mistake:** Forcing a “star” label on three random candles without a prior trend."
    ),
    "volume": (
        "**Volume in Technical Analysis**\n\n"
        "Volume shows participation behind a price move.\n"
        "• Uptrend + rising volume → healthier advance\n"
        "• Breakout + expanding volume → more trustworthy\n"
        "• Rally + shrinking volume → caution\n\n"
        "In India, pair with **delivery %** when available (conviction vs intraday churn).\n"
        "BYSEL also reports volume z-score vs recent average.\n\n"
        "**Common mistake:** Ignoring volume on breakouts."
    ),
    "fibonacci": (
        "**Fibonacci Retracements**\n\n"
        "Common pullback zones from a swing: **23.6%, 38.2%, 50%, 61.8%** "
        "(and extensions like **161.8%**).\n"
        "Map swing high↔low; watch reactions where Fib meets S/R or MA.\n"
        "BYSEL computes Fib levels in the live quant pack for a symbol.\n\n"
        "**Common mistake:** Treating 61.8% as destiny without confluence or a stop."
    ),
    "dow theory": (
        "**Dow Theory (Charles Dow — trend framework)**\n\n"
        "Six classic principles (literacy):\n"
        "1. **Price discounts information** (ex-natural disasters)\n"
        "2. Market has **three trends** — primary (main tide, often >1 year), secondary "
        "(weeks–months corrections/pullbacks), minor (days — noise)\n"
        "3. Primary trend has **three phases** — accumulation → public participation → distribution\n"
        "4. **Averages should confirm** each other (Dow used Industrials + Rails/Transports)\n"
        "5. **Volume confirms** the primary trend (expanding with the trend’s direction)\n"
        "6. Trend stays until a **clear reversal** (higher highs/lows vs lower highs/lows)\n\n"
        "Bullish primary: successive higher peaks and higher troughs. "
        "Bearish primary: lower peaks and lower troughs.\n\n"
        "**Limits:** confirmation can be late; today’s economy is more than industrials+transports "
        "(tech/banks matter for Nifty). Still the root of HH/HL trend language.\n\n"
        "**Common mistake:** Fighting the primary trend with every minor dip."
    ),
    "cpr": (
        "**Central Pivot Range (CPR)**\n\n"
        "From prior day H/L/C:\n"
        "`P = (H + L + C) / 3`\n"
        "`BC = (H + L) / 2`\n"
        "`TC = 2P − BC`\n\n"
        "Bias cues (educational): above TC bullish session bias; below BC bearish; "
        "inside = balance. Narrow CPR → some watch for expansion.\n"
        "BYSEL computes CPR in the quant pack with classic/Camarilla pivots.\n\n"
        "**Common mistake:** Trading CPR alone without trend or news context."
    ),
    "adx": (
        "**ADX (Average Directional Index)**\n\n"
        "ADX measures **trend strength** (not direction). Often with +DI / −DI for direction.\n"
        "• ADX rising above ~20–25 → trend strengthening\n"
        "• Low ADX → chop / range — breakout systems suffer\n\n"
        "BYSEL includes an ADX approximation in the quant score stack.\n\n"
        "**Common mistake:** Buying solely because ADX is high without checking direction."
    ),
    "chart patterns": (
        "**Identifying Chart Patterns (TA literacy) — educational**\n\n"
        "A **pattern** is price structure bounded by at least two trend lines (straight or curved), "
        "with entry/exit ideas. Patterns are **continuation** or **reversal**, and **fractal** "
        "(visible on weekly, daily, or intraday charts).\n\n"
        "**Activation rule:** a pattern is not complete until a real **breakout** occurs "
        "(violation of trend line / S/R / key swing).\n\n"
        "**Multi-bar families:**\n"
        "• Horizontal congestion — double/triple tops & bottoms, rectangles\n"
        "• Triangles — symmetrical, ascending, descending; wedges\n"
        "• Other — head & shoulders, cup & handle\n"
        "• Short-term — flags/pennants, gaps, pipe bottoms, narrow-range / inside bars\n\n"
        "**Candles** (doji, engulfing, hammer, harami, etc.) are shorter patterns — still wait "
        "for completion/confirmation.\n\n"
        "**Limits:** humans see patterns that aren’t there, cling to old targets after conditions "
        "change, and trust “market lore” without evidence. Use filters + stops.\n\n"
        "Ask BYSEL about a named pattern (e.g. *what is a head and shoulders?*, *double top*, "
        "*how to trade breakouts*).\n\n"
        "**Common mistake:** Entering before the breakout “because it looks almost done.”\n"
        "_Educational paraphrase of classic TA pattern literacy (CMT / Kirkpatrick-style frameworks) "
        "— not a broker recommendation._"
    ),
    "trading breakouts": (
        "**Breakouts, Filters & Stops (pattern trading) — educational**\n\n"
        "A **breakout** is a violation of a trend line, support/resistance, or prior reversal "
        "point — it signals a possible shift in buyer/seller behaviour.\n\n"
        "**Confirmation filters** (reduce noise):\n"
        "• Intrabar vs close beyond level · multiple closes · time beyond level · "
        "% / points / rupee buffer\n\n"
        "**False breakout:** price breaks out then quickly returns through the breakout level.\n"
        "**Failed breakout (trap):** false break, then breaks the other way.\n\n"
        "**Trading toolkit:**\n"
        "• **Entry stops** — buy/sell stop beyond breakout to enter only if level is taken\n"
        "• **Protective stops** — define capital risk *before* entry (filter, or beyond "
        "opposite side of breakout bar / S/R)\n"
        "• **Stop-and-reverse** idea — protective stop that also flips if the breakout fails\n\n"
        "**Retracements after breakout:**\n"
        "• **Throwback** — pullback toward breakout after upside break\n"
        "• **Pullback** — bounce toward breakout after downside break\n"
        "They don’t always occur; waiting can improve location but may miss moves.\n\n"
        "**Common mistake:** Chasing every wick beyond resistance with no filter or stop.\n"
        "_Educational — stop orders do not guarantee fill price in fast markets._"
    ),
    "double top": (
        "**Double Top & Double Bottom**\n\n"
        "**Double top (“M”):** after an uptrend, two peaks near the same area (within ~3% is often "
        "“close enough”), trough between them. Completes when price **breaks the reaction low** "
        "(ideally with expanding volume). Target cue ≈ peak-to-trough height projected down. "
        "Peaks usually weeks–months apart; a shallow dip may just be ordinary resistance.\n\n"
        "**Double bottom (“W”):** after a downtrend, two lows near the same area; completes on "
        "**break of the reaction high** with volume. Target cue ≈ height projected up. "
        "Bottoms often take longer to form than tops.\n\n"
        "**Rounded top/bottom:** gradual U-turn; volume typically dries in the base then expands "
        "on the turn — ask *rounded top* for more.\n\n"
        "**Common mistake:** Jumping the gun before the support/resistance break is convincing."
    ),
    "triple top": (
        "**Triple Top & Triple Bottom**\n\n"
        "**Triple top:** three peaks near resistance with two intervening troughs; "
        "breakout below the troughs / connecting support. "
        "Target cue ≈ pattern height subtracted from the breakout area.\n\n"
        "**Triple bottom:** three troughs near support; breakout above intervening peaks. "
        "Target cue ≈ height added above the breakout area.\n\n"
        "**Common mistake:** Forcing a triple label on noisy sideways chop without clear levels."
    ),
    "rectangle pattern": (
        "**Rectangle (trading range) Pattern**\n\n"
        "Price oscillates between roughly horizontal **support** and **resistance** "
        "(a channel-like box). Many false breaks — use confirmation filters.\n\n"
        "Target cue (educational): box height added above resistance (up break) or "
        "subtracted below support (down break).\n\n"
        "**Common mistake:** Buying every touch of support in a rectangle without a breakout plan."
    ),
    "triangle pattern": (
        "**Triangle Patterns (symmetrical / ascending / descending)**\n\n"
        "• **Symmetrical:** falling upper + rising lower bounds; touches each side ≥2×; "
        "break either way — confirm\n"
        "• **Ascending:** flat resistance + rising support — often resolves up, but either way possible\n"
        "• **Descending:** flat support + falling resistance — often resolves down, either way possible\n\n"
        "Educational target: pattern height (highest peak − lowest trough) added/subtracted "
        "from the breakout price.\n\n"
        "**Common mistake:** Entering mid-triangle before a breakout filter triggers."
    ),
    "wedge pattern": (
        "**Rising & Falling Wedges**\n\n"
        "Both bounds slope the **same direction**; price should touch the lines multiple times "
        "(often ~5 touches across both sides) before a breakout.\n\n"
        "Rising wedges after climactic rallies often break down; falling wedges after panics "
        "often break up — but confirm. Retracements after breakout are common.\n\n"
        "**Common mistake:** Shorting every rising wedge without waiting for the break."
    ),
    "head and shoulders": (
        "**Head and Shoulders (Top & Inverse)**\n\n"
        "Needs a prior trend to reverse. **Top:** left shoulder → higher **head** → right shoulder "
        "≈ left; **neckline** joins the two troughs. Completes only on **neckline break** "
        "(ideally with volume). Target cue ≈ head-to-neckline height projected from the break; "
        "broken neckline often acts as resistance on a retest.\n\n"
        "**Volume literacy (top):** ideally heavier on left-shoulder advance than the head; "
        "rising volume on declines from head/right shoulder supports the reverse.\n\n"
        "**Inverse (bottom):** same geometry flipped; **volume expansion on the upside neckline "
        "break is more critical** than for tops. Throwbacks to new support are common.\n\n"
        "**Common mistake:** Calling every three peaks H&S when shoulders aren’t aligned / "
        "neckline unbroken."
    ),
    "cup and handle": (
        "**Cup and Handle (Saucer)**\n\n"
        "Rounded **cup** (not a sharp V), two “lips,” then a smaller consolidating **handle** "
        "(flag-like). Completes on breakout above the lips/handle resistance. "
        "Throwbacks are common.\n\n"
        "Educational target: depth of cup (lip to bottom) added to the breakout price.\n\n"
        "**Common mistake:** Treating a V-spike bottom with no handle as a completed cup."
    ),
    "flag pattern": (
        "**Flag & Pennant**\n\n"
        "After a steep “flagpole” move, a short consolidation slopes slightly against the trend "
        "(flag = parallel channel; pennant = small triangle).\n\n"
        "Breakout often continues in the pole direction; educational target ≈ pole height "
        "projected from the breakout/consolidation.\n\n"
        "**Common mistake:** Holding a flag that morphs into a full reversal without a stop."
    ),
    "price gaps": (
        "**Price Gaps (gap theory) — educational**\n\n"
        "A **gap** is a chart zone with no trades between one close and the next open "
        "(results, news, global cues on NSE).\n\n"
        "**Types (literacy):**\n"
        "• **Common / area gap** — inside congestion; often fills quickly; low opportunity\n"
        "• **Breakaway gap** — leaves a range/pattern with enthusiasm + volume; new S/R at the gap; "
        "may take long to fill — don’t assume instant fill\n"
        "• **Runaway / measuring / continuation gap** — mid-trend acceleration (late joiners / "
        "liquidation); sometimes used to roughly measure remaining move\n"
        "• **Exhaustion gap** — near trend end; large gap + very high volume; often fills as trend turns\n"
        "• **Island cluster** — exhaustion gap then opposite breakaway gap — strong reversal cue\n\n"
        "One trading idea after a valid gap-up: wait for a throwback that does **not** fully cover "
        "the gap (“pivot”), then plan entries with stops under the pivot/gap low.\n\n"
        "**Common mistake:** Assuming every gap “must fill” immediately — especially breakaway/runaway."
    ),
    "narrow range": (
        "**Narrow Range, Inside Bar & Pipe Bottom**\n\n"
        "• **Narrow range (e.g. NR4):** a bar with smaller high−low than the prior few bars → "
        "volatility compression; breakout above/below the NR bar’s range is the cue\n"
        "• **Inside bar:** entirely within prior bar’s range — often traded on break of that range\n"
        "• **Pipe / two-bar reversal bottom:** two wide-range bars at a decline’s end "
        "(more studied on weekly); action on break of the second bar\n\n"
        "Low volatility often precedes new trends — still define risk before entry.\n\n"
        "**Common mistake:** Buying every inside bar in a chop without a directional filter."
    ),
    "rounded top": (
        "**Rounded Top & Rounded Bottom (saucer)**\n\n"
        "Gradual shift from bullish to bearish (**rounded top**) or bearish to bullish "
        "(**rounded bottom**) — bowl-shaped, not a sharp V. Volume typically shrinks into "
        "the quiet middle, then expands as the new direction asserts.\n\n"
        "Harder to time than H&S or double patterns; use volume + break of the rim as confirmation.\n\n"
        "**Common mistake:** Forcing a “saucer” label on every slow sideways grind."
    ),
    "leading indicators": (
        "**Leading vs Lagging Indicators — educational**\n\n"
        "A technical indicator is a formula on price/volume/OI used to **alert, confirm, or predict**.\n\n"
        "• **Leading** (momentum family): RSI, Stochastic, Williams %R, CCI, ROC — earlier signals, "
        "more trades, more false alarms; shine in ranges\n"
        "• **Lagging / trend-following**: moving averages, MACD — stay with trends longer; "
        "late at turns; suffer in chop\n\n"
        "**Tips:** don’t ignore price for the indicator; use 2–3 complementary tools "
        "(not two overbought oscillators); always with structure and stops.\n\n"
        "**Common mistake:** Stacking RSI + Stochastic + Williams and treating every extreme as a must-fade."
    ),
    "stochastic": (
        "**Stochastic Oscillator (%K / %D)**\n\n"
        "Compares close to the recent high–low range (often 14 periods). "
        "`%K` is the raw reading; `%D` is a short MA of `%K` (often 3).\n\n"
        "**Literacy uses:**\n"
        "• Above ~80 overbought / below ~20 oversold — book/avoid adds, don’t blindly reverse\n"
        "• `%K` cross of `%D` in those zones as timing cue\n"
        "• Bullish/bearish **divergences** vs price\n\n"
        "Like RSI, can stay extreme in strong trends — filter with trend/ADX.\n\n"
        "**Common mistake:** Shorting every %K > 80 on a momentum breakout day."
    ),
    "williams %r": (
        "**Williams %R**\n\n"
        "Larry Williams’ oscillator — conceptually close to Stochastic but scaled **0 to −100**. "
        "Roughly −20 to 0 = overbought zone; −80 to −100 = oversold zone.\n\n"
        "Uses: fade extremes with structure, watch **divergences**, avoid fighting a strong trend.\n\n"
        "**Common mistake:** Treating %R alone as a buy/sell system without S/R or risk rules."
    ),
    "money flow index": (
        "**Money Flow Index (MFI)**\n\n"
        "Volume-weighted RSI-style oscillator (“always watch the smart money” literacy). "
        "Uses typical price × volume to build positive vs negative money flow, then:\n"
        "`MFI = 100 − 100 / (1 + money ratio)`\n\n"
        "Common reads: >80 overbought / <20 oversold; positive/negative **divergences** "
        "when price makes new extremes but MFI does not.\n\n"
        "**Common mistake:** Ignoring MFI divergence that confirms an RSI/MACD warning."
    ),
    "elliott wave": (
        "**Elliott Wave Theory — educational**\n\n"
        "Ralph Nelson Elliott’s idea: prices swing with crowd psychology in **fractal** waves.\n\n"
        "• **Impulse (dominant):** 5 waves in trend direction (1–2–3–4–5); 1/3/5 motive, 2/4 corrective\n"
        "• **Correction:** 3 waves (A–B–C) against the prior impulse\n\n"
        "**Hard rules (literacy):** Wave 2 does not retrace beyond start of Wave 1; "
        "Wave 3 is never the shortest impulse; Wave 4 should not overlap Wave 1 in cash "
        "(futures/FX often allow small overlap).\n\n"
        "Fibonacci ratios guide typical retracements/extensions (e.g. Wave 2 ~50–61.8% of Wave 1; "
        "Wave 3 often 1.618× Wave 1). Counting is subjective — prefer clear patterns, "
        "trade with R:R ≥ ~1.5, and use stops (e.g. beyond invalidation of the count).\n\n"
        "**Common mistake:** Forcing a perfect 1–5 count on every Nifty wiggle.\n"
        "_Educational — not a prediction engine._"
    ),
    "day trading": (
        "**Day Trading Strategies — educational**\n\n"
        "Day trading = open and close within the same session (no overnight gap risk on that position). "
        "Uses leverage/margins; profits and losses can both be large. Discipline > excitement.\n\n"
        "**Style literacy:**\n"
        "• **Scalping** — many tiny gains; ruthless exits; costs matter on NSE\n"
        "• **Fading** — fade climactic spikes (overbought + early profit-taking); high risk\n"
        "• **Daily pivots** — trade LOD/HOD / pivot S/R in range days; break levels for trend days\n"
        "• **Momentum** — ride news/volume thrusts; exit when volume fades / opposing candles appear\n\n"
        "**Risks:** large losses, screen time/stress, overtrading, margin debt, tech failures. "
        "Never risk money you can’t afford to lose. BYSEL is paper-first for practice.\n\n"
        "**Common mistake:** Averaging losers intraday because “it has to come back.”\n"
        "_Educational — not a recommendation to day-trade._"
    ),
    "momentum trading": (
        "**Momentum Trading (incl. Elder impulse idea) — educational**\n\n"
        "Trade stocks already moving on **news + volume**, not quiet fundamentals debates.\n\n"
        "**Impulse-system literacy (Elder-style):** combine a short **EMA** (trend inertia) with "
        "**MACD histogram** (momentum). Enter when both point the same way; exit when they diverge. "
        "Many focus on the first and last hour of the cash session when liquidity is richer; "
        "midday can be choppier.\n\n"
        "Exits must be planned before entry — momentum reverses violently.\n\n"
        "**Common mistake:** Chasing mid-move without a stop because “it’s in the news.”"
    ),
    "trading psychology": (
        "**Trading Psychology & Risk Management — educational**\n\n"
        "Even a decent system may only win ~60% of the time — risk control keeps you alive "
        "through the other 40%. Seed capital is scarce; opportunities are plentiful.\n\n"
        "**Core toolkit:**\n"
        "• **Stop-loss** on every trade · size so a stop is a small % of equity (often ~1–2% risk)\n"
        "• Check **reward:risk** before entry (many require ≥ ~1.5)\n"
        "• **Trail** stops as trades work · book profits at planned targets\n"
        "• Prefer **3-5-7** / position-sizing rules over “all-in” conviction\n\n"
        "**Behaviour traps:** revenge trading, no plan, counting chickens before exit, "
        "complexity addiction, overtrading for excitement, fighting the trend, ignoring warnings.\n\n"
        "**Discipline ideas:** trade with the trend until evidence flips; wait for the market "
        "(flat is a position); keep size small enough to think clearly; choose a few markets "
        "you actually follow.\n\n"
        "**Common mistake:** Risking the account to “get even” after a loss streak.\n"
        "_Educational — paper trade until rules are automatic._"
    ),
    "gtt": (
        "**GTT (Good Till Triggered) — educational**\n\n"
        "A broker facility: you set a **trigger price**; when LTP crosses it, an order "
        "(limit/market as configured) is placed automatically. Useful for target exits or "
        "buy-on-dip plans without watching the screen all day.\n\n"
        "• Validity is broker-defined (often months) — check your broker’s GTT rules\n"
        "• Trigger ≠ guaranteed fill at that exact price in fast markets\n"
        "• Cancel/replace if your thesis changes\n\n"
        "**Common mistake:** Setting GTT and forgetting risk events (results, circuits)."
    ),
    "brokerage": (
        "**Brokerage & trading charges (India equity — educational)**\n\n"
        "On NSE/BSE cash trades, total cost is more than brokerage:\n"
        "• **Brokerage** — broker fee (flat or %; many discount brokers are low/zero on delivery)\n"
        "• **STT** — Securities Transaction Tax (govt)\n"
        "• **Exchange / clearing / SEBI** turnover fees\n"
        "• **Stamp duty** — state levy (often on buy side)\n"
        "• **GST** — on brokerage + some fees\n\n"
        "Intraday / F&O usually have different STT and brokerage schedules than delivery.\n"
        "BYSEL paper-trade estimates show an educational charges stack — live broker bills can differ.\n\n"
        "**Common mistake:** Ignoring charges when scalping tiny moves."
    ),
    "delivery vs intraday": (
        "**Delivery vs Intraday (CNC / MIS / NRML)**\n\n"
        "• **Delivery (CNC)** — you take/give shares; settles **T+1**; needs full funds "
        "(or margin as per broker product); shares hit demat\n"
        "• **Intraday (MIS / similar)** — square-off same day; broker may give leverage; "
        "auto square-off near close if open\n"
        "• **NRML** — often used for overnight F&O / carry-forward positions (product names vary by broker)\n\n"
        "Delivery % on exchanges is a **volume statistic** (how much was delivery vs traded) — "
        "not the same as choosing CNC vs MIS on your order ticket.\n\n"
        "**Common mistake:** Holding an MIS position overnight by accident."
    ),
    "pledge": (
        "**Pledge for margin (shares with broker) — educational**\n\n"
        "You can **pledge** demat holdings with your broker to get **margin** for trading "
        "(as per broker/exchange haircuts). Shares stay beneficially yours but are locked "
        "until unpledged.\n\n"
        "Different from **promoter pledge** news (promoters pledging shares to lenders) — "
        "that is a company/ownership risk signal, not your trading margin product.\n\n"
        "**Common mistake:** Over-leveraging against pledged shares into a sharp drawdown."
    ),
    "auction": (
        "**Auction / short delivery (educational)**\n\n"
        "If a seller fails to deliver shares for a delivery trade, the exchange runs a "
        "**auction / close-out** process so the buyer can still receive shares (or cash "
        "close-out as per rules). Short-delivery can mean penalty costs for the defaulting side.\n\n"
        "Separate from the **closing auction (CAS)** that helps discover the official closing price.\n\n"
        "**Common mistake:** Confusing closing auction (CAS) with short-delivery auction."
    ),
    "bonus": (
        "**Bonus Issue**\n\n"
        "Company issues free additional shares to existing shareholders from reserves "
        "(e.g. 1:1 bonus → you get 1 new share per share held). Price adjusts downward "
        "roughly with the ratio; your **economic value** is similar before/after ignoring "
        "market reaction.\n\n"
        "**Common mistake:** Treating bonus as ‘free money’ without price adjustment."
    ),
    "stock split": (
        "**Stock Split**\n\n"
        "Company reduces face value and increases share count (e.g. ₹10 → ₹2 face, 1 share "
        "becomes 5). Price adjusts; ownership % unchanged. Often done to improve retail "
        "lot affordability.\n\n"
        "**Common mistake:** Expecting wealth to multiply solely because share count rose."
    ),
    "rights issue": (
        "**Rights Issue**\n\n"
        "Company offers new shares to existing shareholders at a stated ratio/price "
        "(often at a discount to market) to raise capital. You may subscribe, renounce, "
        "or let rights lapse (as per offer rules).\n\n"
        "**Common mistake:** Ignoring dilution / use-of-proceeds when deciding to subscribe."
    ),
    "asba": (
        "**ASBA / UPI for IPOs**\n\n"
        "**ASBA** (Applications Supported by Blocked Amount): IPO application money is "
        "**blocked** in your bank/UPI mandate and debited only if shares are allotted.\n\n"
        "Retail IPO bids commonly use UPI mandates via broker apps. If not allotted, the "
        "block is released.\n\n"
        "**Common mistake:** Applying in multiple demat accounts beyond SEBI rules."
    ),
    "corporate actions": (
        "**Corporate Actions (India — map)**\n\n"
        "Events that change shares/cash for holders:\n"
        "• **Dividend** — cash (or sometimes stock) to shareholders\n"
        "• **Bonus / split** — share count & price adjust\n"
        "• **Rights** — offer to buy more shares\n"
        "• **Buyback / delisting / merger** — special processes under SEBI/company law\n\n"
        "Watch **ex-date / record date** — you generally need to hold before ex-date for entitlement.\n\n"
        "**Common mistake:** Buying on ex-date expecting the dividend still."
    ),
    "fii dii flows": (
        "**FII / DII Flows (educational)**\n\n"
        "**FIIs/FPIs** (foreign) and **DIIs** (mutual funds, insurers, etc.) publish "
        "daily/periodic buy-sell activity. Persistent FII selling with DII buying is a "
        "common tape narrative — useful context, not a standalone buy/sell signal.\n\n"
        "Flows interact with rupee, global risk, valuations, and earnings season.\n\n"
        "**Common mistake:** Blindly fading FII selling without checking levels and news."
    ),
    "sebi investor protection": (
        "**SEBI investor basics (educational)**\n\n"
        "SEBI regulates securities markets in India. Practical retail points:\n"
        "• Use **SEBI-registered** brokers / advisors\n"
        "• Beware tips on social media promising guaranteed returns\n"
        "• IPO, mutual funds, and listed equity have disclosure rules — read them\n"
        "• Complaints: SCORES / exchange grievance mechanisms\n\n"
        "**Common mistake:** Sharing OTP/password or trading via unregistered ‘tips’ channels.\n"
        "_Educational summary — not a legal opinion._"
    ),
    "nseindia": (
        "**NSE official site (https://www.nseindia.com/)**\n\n"
        "NSE is the official source for Indian listed-equity and most index F&O **rules and "
        "reference data**: quotes, option chain, lot sizes, expiries, holidays, price bands, "
        "and circulars.\n\n"
        "**How to use it (literacy, not a scrape):**\n"
        "• **Get Quote** — last / OHLC / volume / corporate actions for a symbol\n"
        "• **Option chain** — strikes, OI, IV (index or stock F&O)\n"
        "• **Holidays / timings** — confirm the session calendar (cash vs F&O can differ)\n"
        "• **Circulars** — lot-size or settlement changes; do not invent a circular number\n"
        "• **Learn / NCFM** — investor education and certifications — **not** buy/sell tips\n\n"
        "BYSEL paper practice is educational. We do **not** crawl or train weights on "
        "nseindia.com. For any live lot, holiday, or rule, open the NSE page and verify.\n\n"
        "**If you asked for ‘NSE strategies’:** official education is **process and risk** "
        "(know the product, size the book, journal the plan). It is not a secret system "
        "and not a SEBI research call.\n\n"
        "_Educational map — confirm current figures on nseindia.com._"
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
_TERM_ANSWERS["circuit limit"] = _TERM_ANSWERS["circuit"]
_TERM_ANSWERS["price band"] = _TERM_ANSWERS["circuit"]
_TERM_ANSWERS["simple moving average"] = _TERM_ANSWERS["sma"]
_TERM_ANSWERS["exponential moving average"] = _TERM_ANSWERS["ema"]
_TERM_ANSWERS["bank nifty"] = _TERM_ANSWERS["banknifty"]
_TERM_ANSWERS["nifty 50"] = _TERM_ANSWERS["nifty"]
_TERM_ANSWERS["dividend"] = _TERM_ANSWERS["dividend yield"]
_TERM_ANSWERS["securities transaction tax"] = _TERM_ANSWERS["stt"]
_TERM_ANSWERS["t+1 settlement"] = _TERM_ANSWERS["t+1"]
_TERM_ANSWERS["settlement cycle"] = _TERM_ANSWERS["t+1"]
_TERM_ANSWERS["f&o lot"] = _TERM_ANSWERS["lot size"]
_TERM_ANSWERS["short term capital gains"] = _TERM_ANSWERS["stcg"]
_TERM_ANSWERS["long term capital gains"] = _TERM_ANSWERS["ltcg"]
_TERM_ANSWERS["demat account"] = _TERM_ANSWERS["demat"]
_TERM_ANSWERS["how to open demat"] = _TERM_ANSWERS["demat"]
_TERM_ANSWERS["how to open a demat account"] = _TERM_ANSWERS["demat"]
_TERM_ANSWERS["open demat account"] = _TERM_ANSWERS["demat"]
_TERM_ANSWERS["fii dii"] = _TERM_ANSWERS["fii"]
_TERM_ANSWERS["fii vs dii"] = _TERM_ANSWERS["fii dii flows"]
_TERM_ANSWERS["fii and dii"] = _TERM_ANSWERS["fii dii flows"]
_TERM_ANSWERS["dii fii"] = _TERM_ANSWERS["fii dii flows"]
_TERM_ANSWERS["good till triggered"] = _TERM_ANSWERS["gtt"]
_TERM_ANSWERS["gtt order"] = _TERM_ANSWERS["gtt"]
_TERM_ANSWERS["what is gtt"] = _TERM_ANSWERS["gtt"]
_TERM_ANSWERS["brokerage charges"] = _TERM_ANSWERS["brokerage"]
_TERM_ANSWERS["trading charges"] = _TERM_ANSWERS["brokerage"]
_TERM_ANSWERS["transaction charges"] = _TERM_ANSWERS["brokerage"]
_TERM_ANSWERS["cnc vs mis"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["mis vs cnc"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["delivery vs mis"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["intraday vs delivery"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["cnc"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["mis"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["nrml"] = _TERM_ANSWERS["delivery vs intraday"]
_TERM_ANSWERS["pledge for margin"] = _TERM_ANSWERS["pledge"]
_TERM_ANSWERS["pledging shares"] = _TERM_ANSWERS["pledge"]
_TERM_ANSWERS["share pledge"] = _TERM_ANSWERS["pledge"]
_TERM_ANSWERS["short delivery"] = _TERM_ANSWERS["auction"]
_TERM_ANSWERS["auction market"] = _TERM_ANSWERS["auction"]
_TERM_ANSWERS["bonus issue"] = _TERM_ANSWERS["bonus"]
_TERM_ANSWERS["bonus share"] = _TERM_ANSWERS["bonus"]
_TERM_ANSWERS["bonus shares"] = _TERM_ANSWERS["bonus"]
_TERM_ANSWERS["split"] = _TERM_ANSWERS["stock split"]
_TERM_ANSWERS["share split"] = _TERM_ANSWERS["stock split"]
_TERM_ANSWERS["rights"] = _TERM_ANSWERS["rights issue"]
_TERM_ANSWERS["right issue"] = _TERM_ANSWERS["rights issue"]
_TERM_ANSWERS["ipo allotment"] = _TERM_ANSWERS["ipo"]
_TERM_ANSWERS["ipo process"] = _TERM_ANSWERS["ipo"]
_TERM_ANSWERS["upi ipo"] = _TERM_ANSWERS["asba"]
_TERM_ANSWERS["asba ipo"] = _TERM_ANSWERS["asba"]
_TERM_ANSWERS["corporate action"] = _TERM_ANSWERS["corporate actions"]
_TERM_ANSWERS["stcg vs ltcg"] = _TERM_ANSWERS["stcg"]
_TERM_ANSWERS["ltcg vs stcg"] = _TERM_ANSWERS["ltcg"]
_TERM_ANSWERS["capital gains tax"] = _TERM_ANSWERS["stcg"]
_TERM_ANSWERS["tax on shares"] = _TERM_ANSWERS["stcg"]
_TERM_ANSWERS["tax on equity"] = _TERM_ANSWERS["stcg"]
_TERM_ANSWERS["sebi"] = _TERM_ANSWERS["sebi investor protection"]
_TERM_ANSWERS["investor protection"] = _TERM_ANSWERS["sebi investor protection"]
_TERM_ANSWERS["delta gamma"] = _TERM_ANSWERS["gamma"]
_TERM_ANSWERS["nifty p/e"] = _TERM_ANSWERS["nifty pe"]
_TERM_ANSWERS["index pe"] = _TERM_ANSWERS["nifty pe"]
_TERM_ANSWERS["stock market meaning"] = _TERM_ANSWERS["stock market"]
_TERM_ANSWERS["share market"] = _TERM_ANSWERS["stock market"]
_TERM_ANSWERS["how stock market works"] = _TERM_ANSWERS["how does the stock market work"]
_TERM_ANSWERS["how the stock market works"] = _TERM_ANSWERS["how does the stock market work"]
_TERM_ANSWERS["how does share market work"] = _TERM_ANSWERS["how does the stock market work"]
_TERM_ANSWERS["how share prices are determined"] = _TERM_ANSWERS["share price"]
_TERM_ANSWERS["how are share prices determined"] = _TERM_ANSWERS["share price"]
_TERM_ANSWERS["price discovery"] = _TERM_ANSWERS["share price"]
_TERM_ANSWERS["depository participants"] = _TERM_ANSWERS["depository participant"]
_TERM_ANSWERS["what is a dp"] = _TERM_ANSWERS["depository participant"]
_TERM_ANSWERS["how to start investing"] = _TERM_ANSWERS["start investing"]
_TERM_ANSWERS["how to start investing in share market"] = _TERM_ANSWERS["start investing"]
_TERM_ANSWERS["how to invest in stock market"] = _TERM_ANSWERS["start investing"]
_TERM_ANSWERS["mistakes to avoid"] = _TERM_ANSWERS["common mistakes"]
_TERM_ANSWERS["secondary market"] = _TERM_ANSWERS["primary market"]
_TERM_ANSWERS["ipo vs secondary"] = _TERM_ANSWERS["primary market"]
_TERM_ANSWERS["what moves stock"] = _TERM_ANSWERS["what moves the stock"]
_TERM_ANSWERS["what moves stocks"] = _TERM_ANSWERS["what moves the stock"]
_TERM_ANSWERS["why do stock prices move"] = _TERM_ANSWERS["what moves the stock"]
_TERM_ANSWERS["why do stock prices fluctuate"] = _TERM_ANSWERS["what moves the stock"]
_TERM_ANSWERS["cagr vs absolute"] = _TERM_ANSWERS["absolute return"]
_TERM_ANSWERS["absolute vs cagr"] = _TERM_ANSWERS["absolute return"]
_TERM_ANSWERS["how to calculate returns"] = _TERM_ANSWERS["absolute return"]
_TERM_ANSWERS["day trader"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["scalper"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["swing trader"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["growth investor"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["value investor"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["where do you fit"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["trader or investor"] = _TERM_ANSWERS["trader vs investor"]
_TERM_ANSWERS["what happens after you buy"] = _TERM_ANSWERS["after you own stock"]
_TERM_ANSWERS["what happens after you own"] = _TERM_ANSWERS["after you own stock"]
_TERM_ANSWERS["after you own a stock"] = _TERM_ANSWERS["after you own stock"]
_TERM_ANSWERS["owning a stock"] = _TERM_ANSWERS["after you own stock"]
_TERM_ANSWERS["shareholder privileges"] = _TERM_ANSWERS["after you own stock"]
_TERM_ANSWERS["ta"] = _TERM_ANSWERS["technical analysis"]
_TERM_ANSWERS["what is technical analysis"] = _TERM_ANSWERS["technical analysis"]
_TERM_ANSWERS["technical vs fundamental"] = _TERM_ANSWERS["technical analysis"]
_TERM_ANSWERS["fundamental vs technical"] = _TERM_ANSWERS["technical analysis"]
_TERM_ANSWERS["ncfm ta"] = _TERM_ANSWERS["ncfm technical analysis"]
_TERM_ANSWERS["ncfm technical analysis module"] = _TERM_ANSWERS["ncfm technical analysis"]
_TERM_ANSWERS["nse technical analysis module"] = _TERM_ANSWERS["ncfm technical analysis"]
_TERM_ANSWERS["ncfm technical analysis certification"] = _TERM_ANSWERS["ncfm technical analysis"]
_TERM_ANSWERS["elliot wave"] = _TERM_ANSWERS["elliott wave"]
_TERM_ANSWERS["elliott wave theory"] = _TERM_ANSWERS["elliott wave"]
_TERM_ANSWERS["elliot wave theory"] = _TERM_ANSWERS["elliott wave"]
_TERM_ANSWERS["stochastic oscillator"] = _TERM_ANSWERS["stochastic"]
_TERM_ANSWERS["stochastics"] = _TERM_ANSWERS["stochastic"]
_TERM_ANSWERS["williams percent r"] = _TERM_ANSWERS["williams %r"]
_TERM_ANSWERS["william %r"] = _TERM_ANSWERS["williams %r"]
_TERM_ANSWERS["williams r"] = _TERM_ANSWERS["williams %r"]
_TERM_ANSWERS["mfi"] = _TERM_ANSWERS["money flow index"]
_TERM_ANSWERS["lagging indicators"] = _TERM_ANSWERS["leading indicators"]
_TERM_ANSWERS["leading vs lagging"] = _TERM_ANSWERS["leading indicators"]
_TERM_ANSWERS["types of indicators"] = _TERM_ANSWERS["leading indicators"]
_TERM_ANSWERS["rounded bottom"] = _TERM_ANSWERS["rounded top"]
_TERM_ANSWERS["saucer top"] = _TERM_ANSWERS["rounded top"]
_TERM_ANSWERS["saucer bottom"] = _TERM_ANSWERS["rounded top"]
_TERM_ANSWERS["breakaway gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["exhaustion gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["runaway gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["measuring gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["common gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["island reversal"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["island cluster"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["gap theory"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["day trade"] = _TERM_ANSWERS["day trading"]
_TERM_ANSWERS["intraday trading strategies"] = _TERM_ANSWERS["day trading"]
_TERM_ANSWERS["scalping"] = _TERM_ANSWERS["day trading"]
_TERM_ANSWERS["fading"] = _TERM_ANSWERS["day trading"]
_TERM_ANSWERS["momentum trade"] = _TERM_ANSWERS["momentum trading"]
_TERM_ANSWERS["elder impulse"] = _TERM_ANSWERS["momentum trading"]
_TERM_ANSWERS["trading psychology and risk"] = _TERM_ANSWERS["trading psychology"]
_TERM_ANSWERS["trader psychology"] = _TERM_ANSWERS["trading psychology"]
_TERM_ANSWERS["risk management in trading"] = _TERM_ANSWERS["trading psychology"]
_TERM_ANSWERS["golden rules for traders"] = _TERM_ANSWERS["trading psychology"]
_TERM_ANSWERS["principles of dow theory"] = _TERM_ANSWERS["dow theory"]
_TERM_ANSWERS["what is dow theory"] = _TERM_ANSWERS["dow theory"]
_TERM_ANSWERS["market hours"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["trading hours"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["nse timings"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["bse timings"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["closing auction"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["closing auction session"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["what is cas"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["cas"] = _TERM_ANSWERS["market timings"]
_TERM_ANSWERS["market sentiment"] = _TERM_ANSWERS["sentiment analysis"]
_TERM_ANSWERS["news sentiment"] = _TERM_ANSWERS["sentiment analysis"]
_TERM_ANSWERS["stock sentiment"] = _TERM_ANSWERS["sentiment analysis"]
_TERM_ANSWERS["investor sentiment"] = _TERM_ANSWERS["sentiment analysis"]
_TERM_ANSWERS["what is sentiment analysis"] = _TERM_ANSWERS["sentiment analysis"]
_TERM_ANSWERS["candlesticks"] = _TERM_ANSWERS["candlestick"]
_TERM_ANSWERS["candle stick"] = _TERM_ANSWERS["candlestick"]
_TERM_ANSWERS["chart types"] = _TERM_ANSWERS["candlestick"]
_TERM_ANSWERS["hanging man"] = _TERM_ANSWERS["hammer"]
_TERM_ANSWERS["spinning top"] = _TERM_ANSWERS["doji"]
_TERM_ANSWERS["bullish engulfing"] = _TERM_ANSWERS["engulfing"]
_TERM_ANSWERS["bearish engulfing"] = _TERM_ANSWERS["engulfing"]
_TERM_ANSWERS["evening star"] = _TERM_ANSWERS["morning star"]
_TERM_ANSWERS["inverted hammer"] = _TERM_ANSWERS["shooting star"]
_TERM_ANSWERS["piercing line"] = _TERM_ANSWERS["dark cloud cover"]
_TERM_ANSWERS["piercing pattern"] = _TERM_ANSWERS["dark cloud cover"]
_TERM_ANSWERS["dark cloud"] = _TERM_ANSWERS["dark cloud cover"]
_TERM_ANSWERS["chart pattern"] = _TERM_ANSWERS["chart patterns"]
_TERM_ANSWERS["identifying chart patterns"] = _TERM_ANSWERS["chart patterns"]
_TERM_ANSWERS["what is a chart pattern"] = _TERM_ANSWERS["chart patterns"]
_TERM_ANSWERS["common chart patterns"] = _TERM_ANSWERS["chart patterns"]
_TERM_ANSWERS["trading patterns"] = _TERM_ANSWERS["chart patterns"]
_TERM_ANSWERS["false breakout"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["failed breakout"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["confirmation filter"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["confirmation filters"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["entry stops"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["protective stops"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["throwback"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["pullback after breakout"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["how to trade breakouts"] = _TERM_ANSWERS["trading breakouts"]
_TERM_ANSWERS["double bottom"] = _TERM_ANSWERS["double top"]
_TERM_ANSWERS["triple bottom"] = _TERM_ANSWERS["triple top"]
_TERM_ANSWERS["trading range pattern"] = _TERM_ANSWERS["rectangle pattern"]
_TERM_ANSWERS["rectangle chart pattern"] = _TERM_ANSWERS["rectangle pattern"]
_TERM_ANSWERS["symmetrical triangle"] = _TERM_ANSWERS["triangle pattern"]
_TERM_ANSWERS["ascending triangle"] = _TERM_ANSWERS["triangle pattern"]
_TERM_ANSWERS["descending triangle"] = _TERM_ANSWERS["triangle pattern"]
_TERM_ANSWERS["triangle chart pattern"] = _TERM_ANSWERS["triangle pattern"]
_TERM_ANSWERS["rising wedge"] = _TERM_ANSWERS["wedge pattern"]
_TERM_ANSWERS["falling wedge"] = _TERM_ANSWERS["wedge pattern"]
_TERM_ANSWERS["wedge chart pattern"] = _TERM_ANSWERS["wedge pattern"]
_TERM_ANSWERS["inverse head and shoulders"] = _TERM_ANSWERS["head and shoulders"]
_TERM_ANSWERS["head & shoulders"] = _TERM_ANSWERS["head and shoulders"]
_TERM_ANSWERS["cup & handle"] = _TERM_ANSWERS["cup and handle"]
_TERM_ANSWERS["saucer pattern"] = _TERM_ANSWERS["cup and handle"]
_TERM_ANSWERS["pennant"] = _TERM_ANSWERS["flag pattern"]
_TERM_ANSWERS["flag and pennant"] = _TERM_ANSWERS["flag pattern"]
_TERM_ANSWERS["pennant pattern"] = _TERM_ANSWERS["flag pattern"]
_TERM_ANSWERS["price gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["explosion gap"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["gap trading"] = _TERM_ANSWERS["price gaps"]
_TERM_ANSWERS["inside bar"] = _TERM_ANSWERS["narrow range"]
_TERM_ANSWERS["nr4"] = _TERM_ANSWERS["narrow range"]
_TERM_ANSWERS["narrow range bar"] = _TERM_ANSWERS["narrow range"]
_TERM_ANSWERS["pipe bottom"] = _TERM_ANSWERS["narrow range"]
_TERM_ANSWERS["two bar reversal"] = _TERM_ANSWERS["narrow range"]
_TERM_ANSWERS["fibonacci retracement"] = _TERM_ANSWERS["fibonacci"]
_TERM_ANSWERS["fib retracement"] = _TERM_ANSWERS["fibonacci"]
_TERM_ANSWERS["central pivot range"] = _TERM_ANSWERS["cpr"]
_TERM_ANSWERS["pivot range"] = _TERM_ANSWERS["cpr"]
_TERM_ANSWERS["volumes"] = _TERM_ANSWERS["volume"]
_TERM_ANSWERS["volume analysis"] = _TERM_ANSWERS["volume"]
_TERM_ANSWERS["support and resistance"] = _TERM_ANSWERS["support"]
_TERM_ANSWERS["moving averages"] = _TERM_ANSWERS["sma"]
_TERM_ANSWERS["f&o"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["f and o"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["fno"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["what is f&o"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["what is futures and options"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["futures & options"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["future and options"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["futures and option"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["derivatives trading"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["stock derivatives"] = _TERM_ANSWERS["futures and options"]
_TERM_ANSWERS["difference between futures and options"] = _TERM_ANSWERS["futures vs options"]
_TERM_ANSWERS["futures or options"] = _TERM_ANSWERS["futures vs options"]
_TERM_ANSWERS["option vs future"] = _TERM_ANSWERS["futures vs options"]
_TERM_ANSWERS["options vs futures"] = _TERM_ANSWERS["futures vs options"]
_TERM_ANSWERS["who should trade f&o"] = _TERM_ANSWERS["f&o participants"]
_TERM_ANSWERS["who uses f&o"] = _TERM_ANSWERS["f&o participants"]
_TERM_ANSWERS["hedger"] = _TERM_ANSWERS["hedgers"]
_TERM_ANSWERS["speculator"] = _TERM_ANSWERS["speculators"]
_TERM_ANSWERS["arbitrageur"] = _TERM_ANSWERS["arbitrageurs"]
_TERM_ANSWERS["f&o arbitrage"] = _TERM_ANSWERS["arbitrageurs"]
_TERM_ANSWERS["cash settled"] = _TERM_ANSWERS["cash settlement"]
_TERM_ANSWERS["cash settle"] = _TERM_ANSWERS["cash settlement"]
_TERM_ANSWERS["futures trading"] = _TERM_ANSWERS["futures"]
_TERM_ANSWERS["futures contract"] = _TERM_ANSWERS["futures"]
_TERM_ANSWERS["futures market"] = _TERM_ANSWERS["futures"]
_TERM_ANSWERS["forward contract"] = _TERM_ANSWERS["forwards"]
_TERM_ANSWERS["forwards market"] = _TERM_ANSWERS["forwards"]
_TERM_ANSWERS["m2m"] = _TERM_ANSWERS["mark to market"]
_TERM_ANSWERS["mark-to-market"] = _TERM_ANSWERS["mark to market"]
_TERM_ANSWERS["marking to market"] = _TERM_ANSWERS["mark to market"]
_TERM_ANSWERS["margins"] = _TERM_ANSWERS["margin call"]
_TERM_ANSWERS["span margin"] = _TERM_ANSWERS["margin call"]
_TERM_ANSWERS["exposure margin"] = _TERM_ANSWERS["margin call"]
_TERM_ANSWERS["futures leverage"] = _TERM_ANSWERS["leverage"]
_TERM_ANSWERS["cost of carry"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["fair value of futures"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["futures fair value"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["basis"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["contango"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["backwardation"] = _TERM_ANSWERS["futures pricing"]
_TERM_ANSWERS["oi"] = _TERM_ANSWERS["open interest"]
_TERM_ANSWERS["open interest vs volume"] = _TERM_ANSWERS["open interest"]
_TERM_ANSWERS["hedging"] = _TERM_ANSWERS["hedging with futures"]
_TERM_ANSWERS["hedge with futures"] = _TERM_ANSWERS["hedging with futures"]
_TERM_ANSWERS["portfolio hedge"] = _TERM_ANSWERS["hedging with futures"]
_TERM_ANSWERS["beta hedge"] = _TERM_ANSWERS["hedging with futures"]
_TERM_ANSWERS["short selling"] = _TERM_ANSWERS["shorting futures"]
_TERM_ANSWERS["shorting"] = _TERM_ANSWERS["shorting futures"]
# Keep auction primer for "short delivery" (do not overwrite with shorting futures).
_TERM_ANSWERS["short delivery"] = _TERM_ANSWERS["auction"]
_TERM_ANSWERS["short-delivery"] = _TERM_ANSWERS["auction"]
_TERM_ANSWERS["bid ask spread"] = _TERM_ANSWERS["impact cost"]
_TERM_ANSWERS["bid ask"] = _TERM_ANSWERS["impact cost"]
_TERM_ANSWERS["calendar spreads"] = _TERM_ANSWERS["calendar spread"]
_TERM_ANSWERS["physical delivery"] = _TERM_ANSWERS["physical settlement"]
_TERM_ANSWERS["index futures"] = _TERM_ANSWERS["nifty futures"]
_TERM_ANSWERS["nifty future"] = _TERM_ANSWERS["nifty futures"]
_TERM_ANSWERS["option strategy"] = _TERM_ANSWERS["option strategies"]
_TERM_ANSWERS["options strategies"] = _TERM_ANSWERS["option strategies"]
_TERM_ANSWERS["options strategy"] = _TERM_ANSWERS["option strategies"]
_TERM_ANSWERS["debit spread"] = _TERM_ANSWERS["bull call spread"]
_TERM_ANSWERS["credit spread"] = _TERM_ANSWERS["bull put spread"]
_TERM_ANSWERS["bullish call spread"] = _TERM_ANSWERS["bull call spread"]
_TERM_ANSWERS["bullish put spread"] = _TERM_ANSWERS["bull put spread"]
_TERM_ANSWERS["bearish put spread"] = _TERM_ANSWERS["bear put spread"]
_TERM_ANSWERS["bearish call spread"] = _TERM_ANSWERS["bear call spread"]
_TERM_ANSWERS["ratio back spread"] = _TERM_ANSWERS["call ratio back spread"]
_TERM_ANSWERS["call ratio"] = _TERM_ANSWERS["call ratio back spread"]
_TERM_ANSWERS["put ratio"] = _TERM_ANSWERS["put ratio back spread"]
_TERM_ANSWERS["put ratio backspread"] = _TERM_ANSWERS["put ratio back spread"]
_TERM_ANSWERS["call ratio backspread"] = _TERM_ANSWERS["call ratio back spread"]
_TERM_ANSWERS["synthetic futures"] = _TERM_ANSWERS["synthetic long"]
_TERM_ANSWERS["synthetic stock"] = _TERM_ANSWERS["synthetic long"]
_TERM_ANSWERS["straddle"] = _TERM_ANSWERS["long straddle"]
_TERM_ANSWERS["strangle"] = _TERM_ANSWERS["long strangle"]
_TERM_ANSWERS["long & short strangle"] = _TERM_ANSWERS["long strangle"]
_TERM_ANSWERS["pcr"] = _TERM_ANSWERS["put call ratio"]
_TERM_ANSWERS["put-call ratio"] = _TERM_ANSWERS["put call ratio"]
_TERM_ANSWERS["put/call ratio"] = _TERM_ANSWERS["put call ratio"]
_TERM_ANSWERS["maxpain"] = _TERM_ANSWERS["max pain"]
_TERM_ANSWERS["iron butterfly"] = _TERM_ANSWERS["iron condor"]
_TERM_ANSWERS["options theory"] = _TERM_ANSWERS["option theory"]
_TERM_ANSWERS["option basics"] = _TERM_ANSWERS["option theory"]
_TERM_ANSWERS["call options"] = _TERM_ANSWERS["call option"]
_TERM_ANSWERS["put options"] = _TERM_ANSWERS["put option"]
_TERM_ANSWERS["what is a call"] = _TERM_ANSWERS["call option"]
_TERM_ANSWERS["what is a put"] = _TERM_ANSWERS["put option"]
_TERM_ANSWERS["option price"] = _TERM_ANSWERS["option premium"]
_TERM_ANSWERS["time value"] = _TERM_ANSWERS["intrinsic value"]
_TERM_ANSWERS["intrinsic"] = _TERM_ANSWERS["intrinsic value"]
_TERM_ANSWERS["itm"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["atm"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["otm"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["in the money"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["out of the money"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["at the money"] = _TERM_ANSWERS["moneyness"]
_TERM_ANSWERS["long call"] = _TERM_ANSWERS["buying a call"]
_TERM_ANSWERS["buy call"] = _TERM_ANSWERS["buying a call"]
_TERM_ANSWERS["short call"] = _TERM_ANSWERS["selling a call"]
_TERM_ANSWERS["write call"] = _TERM_ANSWERS["selling a call"]
_TERM_ANSWERS["call writing"] = _TERM_ANSWERS["selling a call"]
_TERM_ANSWERS["long put"] = _TERM_ANSWERS["buying a put"]
_TERM_ANSWERS["buy put"] = _TERM_ANSWERS["buying a put"]
_TERM_ANSWERS["short put"] = _TERM_ANSWERS["selling a put"]
_TERM_ANSWERS["put writing"] = _TERM_ANSWERS["selling a put"]
_TERM_ANSWERS["option greeks"] = _TERM_ANSWERS["greek interactions"]
_TERM_ANSWERS["greeks"] = _TERM_ANSWERS["greek interactions"]
_TERM_ANSWERS["greek calculator"] = _TERM_ANSWERS["greek interactions"]
_TERM_ANSWERS["hv"] = _TERM_ANSWERS["historical volatility"]
_TERM_ANSWERS["realized volatility"] = _TERM_ANSWERS["historical volatility"]
_TERM_ANSWERS["realised volatility"] = _TERM_ANSWERS["historical volatility"]
_TERM_ANSWERS["option m2m"] = _TERM_ANSWERS["options m2m"]
_TERM_ANSWERS["options pnl"] = _TERM_ANSWERS["options m2m"]
_TERM_ANSWERS["option pnl"] = _TERM_ANSWERS["options m2m"]
_TERM_ANSWERS["commodities currency"] = _TERM_ANSWERS["currency trading"]
_TERM_ANSWERS["currency commodity"] = _TERM_ANSWERS["currency trading"]
_TERM_ANSWERS["commodity and currency"] = _TERM_ANSWERS["currency trading"]
_TERM_ANSWERS["forex"] = _TERM_ANSWERS["currency pair"]
_TERM_ANSWERS["fx"] = _TERM_ANSWERS["currency pair"]
_TERM_ANSWERS["currency pairs"] = _TERM_ANSWERS["currency pair"]
_TERM_ANSWERS["usd inr"] = _TERM_ANSWERS["usdinr"]
_TERM_ANSWERS["usd-inr"] = _TERM_ANSWERS["usdinr"]
_TERM_ANSWERS["dollar rupee"] = _TERM_ANSWERS["usdinr"]
_TERM_ANSWERS["irp"] = _TERM_ANSWERS["interest rate parity"]
_TERM_ANSWERS["cross currency pairs"] = _TERM_ANSWERS["cross currency"]
_TERM_ANSWERS["commodities"] = _TERM_ANSWERS["commodity trading"]
_TERM_ANSWERS["mcx"] = _TERM_ANSWERS["commodity trading"]
_TERM_ANSWERS["ncdex"] = _TERM_ANSWERS["commodity trading"]
_TERM_ANSWERS["bullion"] = _TERM_ANSWERS["gold"]
_TERM_ANSWERS["sgb"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["sovereign gold bonds"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["what are sovereign gold bonds"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["what is a sovereign gold bond"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["what is sgb"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["what are sgbs"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["gold bond"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["gold bonds"] = _TERM_ANSWERS["sovereign gold bond"]
_TERM_ANSWERS["sgb vs gold etf vs physical gold"] = _TERM_ANSWERS["sgb vs gold etf"]
_TERM_ANSWERS["sgb vs etf"] = _TERM_ANSWERS["sgb vs gold etf"]
_TERM_ANSWERS["sovereign gold bond vs gold etf"] = _TERM_ANSWERS["sgb vs gold etf"]
_TERM_ANSWERS["crude"] = _TERM_ANSWERS["crude oil"]
_TERM_ANSWERS["brent"] = _TERM_ANSWERS["crude oil"]
_TERM_ANSWERS["wti"] = _TERM_ANSWERS["crude oil"]
_TERM_ANSWERS["copper"] = _TERM_ANSWERS["base metals"]
_TERM_ANSWERS["aluminium"] = _TERM_ANSWERS["base metals"]
_TERM_ANSWERS["aluminum"] = _TERM_ANSWERS["base metals"]
_TERM_ANSWERS["nickel"] = _TERM_ANSWERS["base metals"]
_TERM_ANSWERS["g-sec"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["gsec"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["g secs"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["government security"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["t-bill"] = _TERM_ANSWERS["treasury bill"]
_TERM_ANSWERS["t-bills"] = _TERM_ANSWERS["treasury bill"]
_TERM_ANSWERS["tbills"] = _TERM_ANSWERS["treasury bill"]
_TERM_ANSWERS["treasury bills"] = _TERM_ANSWERS["treasury bill"]
_TERM_ANSWERS["sdl"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["state development loan"] = _TERM_ANSWERS["government securities"]
_TERM_ANSWERS["ytm"] = _TERM_ANSWERS["bond yield"]
_TERM_ANSWERS["yield to maturity"] = _TERM_ANSWERS["bond yield"]
_TERM_ANSWERS["electricity derivative"] = _TERM_ANSWERS["electricity derivatives"]
_TERM_ANSWERS["risk management map"] = _TERM_ANSWERS["risk management"]
_TERM_ANSWERS["portfolio risk management"] = _TERM_ANSWERS["risk management"]
_TERM_ANSWERS["unsystematic risk"] = _TERM_ANSWERS["systematic risk"]
_TERM_ANSWERS["idiosyncratic risk"] = _TERM_ANSWERS["systematic risk"]
_TERM_ANSWERS["market risk"] = _TERM_ANSWERS["systematic risk"]
_TERM_ANSWERS["portfolio expected return"] = _TERM_ANSWERS["expected return"]
_TERM_ANSWERS["variance"] = _TERM_ANSWERS["portfolio variance"]
_TERM_ANSWERS["covariance"] = _TERM_ANSWERS["portfolio variance"]
_TERM_ANSWERS["correlation matrix"] = _TERM_ANSWERS["portfolio variance"]
_TERM_ANSWERS["correlation"] = _TERM_ANSWERS["portfolio variance"]
_TERM_ANSWERS["var"] = _TERM_ANSWERS["value at risk"]
_TERM_ANSWERS["value-at-risk"] = _TERM_ANSWERS["value at risk"]
_TERM_ANSWERS["position size"] = _TERM_ANSWERS["position sizing"]
_TERM_ANSWERS["kelly"] = _TERM_ANSWERS["kelly criterion"]
_TERM_ANSWERS["kellys criterion"] = _TERM_ANSWERS["kelly criterion"]
_TERM_ANSWERS["kelly's criterion"] = _TERM_ANSWERS["kelly criterion"]
_TERM_ANSWERS["3 5 7 rule"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["3-5-7"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["357 rule"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["3–5–7 rule"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["three five seven rule"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["3% 5% 7% rule"] = _TERM_ANSWERS["3-5-7 rule"]
_TERM_ANSWERS["15 15 15 rule"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["15-15-15"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["151515 rule"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["15–15–15 rule"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["fifteen fifteen fifteen"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["1 crore sip"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["1 crore sip rule"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["crorepati sip"] = _TERM_ANSWERS["15-15-15 rule"]
_TERM_ANSWERS["3 6 9 rule"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["3-6-9"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["369 rule"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["3–6–9 rule"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["3-6-9 rule of money"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["three six nine rule"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["3 6 9 emergency"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["months of expenses"] = _TERM_ANSWERS["3-6-9 rule"]
_TERM_ANSWERS["cognitive bias"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["anchoring bias"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["confirmation bias"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["gamblers fallacy"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["gambler's fallacy"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["hindsight bias"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["recency bias"] = _TERM_ANSWERS["trading biases"]
_TERM_ANSWERS["portfolio optimisation"] = _TERM_ANSWERS["portfolio optimization"]
_TERM_ANSWERS["trading systems"] = _TERM_ANSWERS["trading system"]
_TERM_ANSWERS["what is a trading system"] = _TERM_ANSWERS["trading system"]
_TERM_ANSWERS["pair trade"] = _TERM_ANSWERS["pair trading"]
_TERM_ANSWERS["pairs trading"] = _TERM_ANSWERS["pair trading"]
_TERM_ANSWERS["statistical arbitrage"] = _TERM_ANSWERS["pair trading"]
_TERM_ANSWERS["stat arb"] = _TERM_ANSWERS["pair trading"]
_TERM_ANSWERS["mean reversion pairs"] = _TERM_ANSWERS["density curve"]
_TERM_ANSWERS["z score"] = _TERM_ANSWERS["density curve"]
_TERM_ANSWERS["z-score"] = _TERM_ANSWERS["density curve"]
_TERM_ANSWERS["pair regression"] = _TERM_ANSWERS["linear regression pairs"]
_TERM_ANSWERS["hedge ratio"] = _TERM_ANSWERS["linear regression pairs"]
_TERM_ANSWERS["cointegration"] = _TERM_ANSWERS["adf test"]
_TERM_ANSWERS["augmented dickey fuller"] = _TERM_ANSWERS["adf test"]
_TERM_ANSWERS["dickey fuller"] = _TERM_ANSWERS["adf test"]
_TERM_ANSWERS["momentum"] = _TERM_ANSWERS["momentum portfolio"]
_TERM_ANSWERS["momentum strategy"] = _TERM_ANSWERS["momentum portfolio"]
_TERM_ANSWERS["momentum investing"] = _TERM_ANSWERS["momentum portfolio"]
_TERM_ANSWERS["personal finance mutual funds"] = _TERM_ANSWERS["personal finance"]
_TERM_ANSWERS["varsity personal finance"] = _TERM_ANSWERS["personal finance"]
_TERM_ANSWERS["tvm"] = _TERM_ANSWERS["time value of money"]
_TERM_ANSWERS["future value"] = _TERM_ANSWERS["time value of money"]
_TERM_ANSWERS["present value"] = _TERM_ANSWERS["time value of money"]
_TERM_ANSWERS["rule of 72"] = _TERM_ANSWERS["time value of money"]
_TERM_ANSWERS["real return"] = _TERM_ANSWERS["time value of money"]
_TERM_ANSWERS["retirement"] = _TERM_ANSWERS["retirement planning"]
_TERM_ANSWERS["retirement corpus"] = _TERM_ANSWERS["retirement planning"]
_TERM_ANSWERS["mutual funds"] = _TERM_ANSWERS["mutual fund"]
_TERM_ANSWERS["what is a mutual fund"] = _TERM_ANSWERS["mutual fund"]
_TERM_ANSWERS["what are mutual funds"] = _TERM_ANSWERS["mutual fund"]
_TERM_ANSWERS["how do mutual funds work"] = _TERM_ANSWERS["how mutual funds work"]
_TERM_ANSWERS["how does a mutual fund work"] = _TERM_ANSWERS["how mutual funds work"]
_TERM_ANSWERS["mutual fund types"] = _TERM_ANSWERS["types of mutual funds"]
_TERM_ANSWERS["types of mf"] = _TERM_ANSWERS["types of mutual funds"]
_TERM_ANSWERS["mf vs fd"] = _TERM_ANSWERS["mutual funds vs fd"]
_TERM_ANSWERS["mutual fund vs fd"] = _TERM_ANSWERS["mutual funds vs fd"]
_TERM_ANSWERS["mutual funds vs fds"] = _TERM_ANSWERS["mutual funds vs fd"]
_TERM_ANSWERS["mf vs stocks"] = _TERM_ANSWERS["mutual funds vs stocks"]
_TERM_ANSWERS["mutual fund vs stocks"] = _TERM_ANSWERS["mutual funds vs stocks"]
_TERM_ANSWERS["mutual funds vs shares"] = _TERM_ANSWERS["mutual funds vs stocks"]
_TERM_ANSWERS["asset management company"] = _TERM_ANSWERS["amc"]
_TERM_ANSWERS["exit loads"] = _TERM_ANSWERS["exit load"]
_TERM_ANSWERS["net asset value"] = _TERM_ANSWERS["nav"]
_TERM_ANSWERS["fund factsheet"] = _TERM_ANSWERS["mutual fund factsheet"]
_TERM_ANSWERS["factsheet"] = _TERM_ANSWERS["mutual fund factsheet"]
_TERM_ANSWERS["equity fund"] = _TERM_ANSWERS["equity mutual fund"]
_TERM_ANSWERS["equity funds"] = _TERM_ANSWERS["equity mutual fund"]
_TERM_ANSWERS["debt fund"] = _TERM_ANSWERS["debt mutual fund"]
_TERM_ANSWERS["debt funds"] = _TERM_ANSWERS["debt mutual fund"]
_TERM_ANSWERS["bond fund"] = _TERM_ANSWERS["debt mutual fund"]
_TERM_ANSWERS["index funds"] = _TERM_ANSWERS["index fund"]
_TERM_ANSWERS["passive fund"] = _TERM_ANSWERS["index fund"]
_TERM_ANSWERS["arbitrage funds"] = _TERM_ANSWERS["arbitrage fund"]
_TERM_ANSWERS["etfs"] = _TERM_ANSWERS["etf"]
_TERM_ANSWERS["exchange traded fund"] = _TERM_ANSWERS["etf"]
_TERM_ANSWERS["ter"] = _TERM_ANSWERS["expense ratio"]
_TERM_ANSWERS["total expense ratio"] = _TERM_ANSWERS["expense ratio"]
_TERM_ANSWERS["direct plan"] = _TERM_ANSWERS["expense ratio"]
_TERM_ANSWERS["regular plan"] = _TERM_ANSWERS["expense ratio"]
_TERM_ANSWERS["direct vs regular"] = _TERM_ANSWERS["expense ratio"]
_TERM_ANSWERS["rolling return"] = _TERM_ANSWERS["rolling returns"]
_TERM_ANSWERS["sharpe ratio"] = _TERM_ANSWERS.get("sharpe") or _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["sortino ratio"] = _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["sortino"] = _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["upside capture"] = _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["downside capture"] = _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["capture ratio"] = _TERM_ANSWERS["fund risk metrics"]
_TERM_ANSWERS["asset allocation plan"] = _TERM_ANSWERS["asset allocation"]
_TERM_ANSWERS["strategic beta"] = _TERM_ANSWERS["smart beta"]
_TERM_ANSWERS["factor investing"] = _TERM_ANSWERS["smart beta"]
_TERM_ANSWERS["emergency corpus"] = _TERM_ANSWERS["emergency fund"]
_TERM_ANSWERS["know your fund"] = _TERM_ANSWERS["personal finance review"]
_TERM_ANSWERS["financial planning"] = _TERM_ANSWERS["personal finance"]
_TERM_ANSWERS["financial plan"] = _TERM_ANSWERS["personal finance review"]
_TERM_ANSWERS["nseindia.com"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["www.nseindia.com"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse website"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse official"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["official nse"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse learn"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse education"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse circulars"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse strategies"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse strategy"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse option chain"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse get quote"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse holidays"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse circular"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["nse lot size"] = _TERM_ANSWERS["nseindia"]
_TERM_ANSWERS["official option chain"] = _TERM_ANSWERS["nseindia"]


def get_education_answer(query: str) -> Optional[str]:
    """Return a structured education answer if the query is definitional/formulaic."""
    try:
        from .habit_lessons import get_habit_lesson

        habit = get_habit_lesson(query)
        if habit:
            return habit
    except Exception:
        pass

    q = (query or "").strip().lower()
    if not q:
        return None

    educational_cue = bool(
        re.search(
            r"\b(what is|what are|what happens|explain|define|definition|meaning of|formula|equation|"
            r"how to calculate|tell me about|how does|how do|how are|how to start|how to open|"
            r"complete guide|beginner|difference between|vs\.?|versus)\b",
            q,
        )
    )
    formula_cue = bool(re.search(r"\b(formula|equation)\b", q))
    # Broad market-literacy asks should hit the primer even without "what is".
    literacy_cue = bool(
        re.search(
            r"\b(how does the stock market work|how the stock market works|"
            r"how does the share market work|how share prices|price discovery|"
            r"start investing|common mistakes|key participants|"
            r"depository participant|primary market|secondary market|"
            r"what moves (the )?stock|why do stock prices|absolute return|cagr vs|"
            r"trader vs investor|day trader|scalper|swing trader|holding period|"
            r"where do you fit|after you (own|buy)|how to calculate returns|"
            r"technical analysis|\bncfm\b|candlestick|marubozu|doji|hammer|engulfing|harami|"
            r"shooting star|inverted hammer|dark cloud|piercing line|"
            r"morning star|evening star|fibonacci|dow theory|elliott wave|elliot wave|"
            r"central pivot|\bcpr\b|stochastic|williams\s*%?r|money flow|\bmfi\b|"
            r"leading indicators?|lagging indicators?|rounded top|rounded bottom|"
            r"day trading|momentum trading|scalping|trading psychology|"
            r"chart patterns?|trading breakouts?|false breakout|failed breakout|"
            r"double tops?|double bottoms?|triple tops?|triple bottoms?|"
            r"head and shoulders|cup and handle|flag and pennant|pennant|"
            r"symmetrical triangle|ascending triangle|descending triangle|"
            r"rising wedge|falling wedge|rectangle pattern|pipe bottom|"
            r"narrow range|\bnr4\b|inside bar|price gaps?|gap theory|breakaway gap|"
            r"exhaustion gap|throwback|"
            r"support and resistance|sentiment analysis|market sentiment|news sentiment|"
            r"stock sentiment|investor sentiment|market timings|market hours|trading hours|"
            r"closing auction|\bcas\b|nse timings|bse timings|"
            r"futures and options|\bf&o\b|\bfno\b|futures vs options|"
            r"f&o participants|hedgers|speculators|arbitrageurs|cash settlement|"
            r"futures trading|futures pricing|cost of carry|mark to market|\bm2m\b|"
            r"open interest|\bcontango\b|backwardation|calendar spread|"
            r"physical settlement|impact cost|hedging with futures|shorting futures|"
            r"nifty futures|forwards?|"
            r"option strateg|bull call spread|bull put spread|bear put spread|"
            r"bear call spread|straddle|strangle|iron condor|max pain|put call ratio|"
            r"\bpcr\b|ratio back spread|synthetic long|"
            r"option theory|call option|put option|moneyness|intrinsic value|"
            r"time value|option premium|option greeks|\bgreeks\b|historical volatility|"
            r"buying a call|selling a call|buying a put|selling a put|options m2m|"
            r"currency trading|currency pair|usdinr|usd inr|interest rate parity|"
            r"commodity trading|\bmcx\b|\bncdex\b|\bgold\b|\bsilver\b|crude oil|"
            r"natural gas|government securities|\bg-?sec\b|treasury bill|t-bills?|"
            r"sovereign gold bond|\bsgb\b|gold bonds?|"
            r"bond yield|\bsdl\b|cross currency|"
            r"risk management|position sizing|value at risk|\bvar\b|kelly criterion|"
            r"3-5-7|3–5–7|357 rule|three five seven|"
            r"trading biases|equity curve|portfolio variance|expected return|"
            r"systematic risk|recovery trauma|portfolio optimization|"
            r"trading system|pair trading|pairs trading|density curve|adf test|"
            r"cointegration|momentum portfolio|\bmomentum\b|"
            r"personal finance|time value of money|\btvm\b|retirement planning|"
            r"mutual fund|\bnav\b|expense ratio|\bter\b|direct vs regular|"
            r"rolling returns|asset allocation|smart beta|emergency fund|"
            r"index fund|\betf\b|arbitrage fund|debt fund|equity fund|"
            r"fund factsheet|financial planning|"
            r"15-15-15|15–15–15|151515|1 crore sip|crorepati sip|"
            r"3-6-9|3–6–9|369 rule|three six nine|rule of money|"
            # Retail mechanics — allow without forcing "what is"
            r"nseindia|nse website|official nse|nse learn|nse circular|"
            r"nse holidays|nse get quote|nse option chain|nse lot size|"
            r"\bdemat\b|\bipo\b|\basba\b|\bgtt\b|brokerage|trading charges|"
            r"\bstcg\b|\bltcg\b|capital gains|tax on (equity|shares|profit)|"
            r"\bfii\b|\bdii\b|fii/?dii|"
            r"\bbonus\b|stock split|share split|rights issue|"
            r"\bcnc\b|\bmis\b|\bnrml\b|delivery vs|intraday vs|"
            r"pledge|short delivery|auction market|corporate actions?|"
            r"lot size|circuit limit|investor protection|\bsebi\b)\b",
            q,
        )
    )
    # Bare well-known terms also allowed (short educational prompts).
    bare_term = any(
        re.fullmatch(re.escape(term), q.strip(" ?!."))
        for term in _TERM_ANSWERS
        if term and _TERM_ANSWERS.get(term)
    )
    # Multi-word / known glossary hits count as cues (fixes "how to open demat account").
    glossary_hit = any(
        (term in q) if (" " in term) else bool(re.search(r"\b" + re.escape(term) + r"\b", q))
        for term, answer in _TERM_ANSWERS.items()
        if answer and term and len(term) >= 3
    )
    if not educational_cue and not formula_cue and not bare_term and not literacy_cue and not glossary_hit:
        return None

    # Prefer specific TA terms over the broad "technical analysis" umbrella.
    if re.search(r"\bvolume\b", q) and not re.search(r"\bvwap\b", q):
        vol = _TERM_ANSWERS.get("volume")
        if vol and (literacy_cue or educational_cue or "volume" in q):
            return vol

    if re.search(
        r"\b(sentiment analysis|market sentiment|news sentiment|investor sentiment|"
        r"stock sentiment)\b",
        q,
    ) and not re.search(
        r"\b(of|for)\s+[a-z0-9]|\b(reliance|tcs|infy|hdfc|nifty|sensex|today|now|current)\b",
        q,
    ):
        return _TERM_ANSWERS["sentiment analysis"]

    # Live S/R for a named symbol must not collapse to the glossary primer.
    # e.g. "Support and resistance of BSE:500325" / "S/R of RELIANCE".
    stock_specific_levels = bool(
        re.search(r"\b(support|resistance|s/?r|trading levels?|pivots?)\b", q)
        and re.search(
            r"\b(of|for)\s+[a-z0-9]|bse:|nse:|\b\d{6}\b|"
            r"\b(reliance|tcs|infy|hdfc|wipro|sbin|nifty|sensex)\b",
            q,
        )
    )
    if stock_specific_levels and not formula_cue:
        return None

    # "Stop loss for INFY swing trade" must return a stock plan, not the glossary.
    named_symbol = None
    try:
        from .stock_enricher import extract_symbol_from_query

        named_symbol = extract_symbol_from_query(query)
    except Exception:
        named_symbol = None
    stock_specific_stop_or_plan = bool(named_symbol) and bool(
        re.search(
            r"\b(stop[\s-]?loss|take[\s-]?profit|entry|target price|trade plan|"
            r"should i (buy|sell)|buy or sell|swing trade)\b",
            q,
        )
    )
    if stock_specific_stop_or_plan and not formula_cue and not re.search(
        r"\b(what is|define|definition|meaning of|explain)\b", q
    ):
        return None

    # "Dividend date of INFY" / "corporate actions of TCS" → dated pack, not yield glossary.
    dated_event_ask = bool(
        re.search(
            r"\b(ex[- ]?date|record date|dividend date|bonus date|"
            r"rights date|corporate actions?)\b",
            q,
        )
    )
    stock_specific_event = bool(
        dated_event_ask
        and (
            named_symbol
            or re.search(
                r"\b(of|for)\s+[a-z0-9]|bse:|nse:|\b\d{6}\b|"
                r"\b(reliance|tcs|infy|hdfc|wipro|sbin)\b",
                q,
            )
        )
        and not formula_cue
        and not re.search(r"\b(what is|define|definition|meaning of|explain)\b", q)
    )
    if stock_specific_event:
        return None

    # "Technical analysis of KAYNES" → live stock TA, not the TA literacy primer.
    stock_specific_ta = bool(named_symbol) and bool(
        re.search(
            r"\b(technical analysis|chart analysis|price action|"
            r"technically (analyse|analyze))\b",
            q,
        )
    ) and (
        re.search(r"\b(of|for|on)\s+[a-z0-9]", q)
        or bool(re.search(rf"\b{re.escape(str(named_symbol).lower())}\b", q))
    )
    if stock_specific_ta and not formula_cue:
        return None

    # Live pattern asks for a named symbol → skip glossary (detector / analysis path).
    stock_specific_pattern = bool(
        re.search(
            r"\b(double tops?|double bottoms?|triple tops?|triple bottoms?|"
            r"head and shoulders|cup and handle|chart patterns?|"
            r"ascending triangle|descending triangle|symmetrical triangle|"
            r"flag pattern|pennant|rising wedge|falling wedge)\b",
            q,
        )
        and re.search(
            r"\b(of|for)\s+[a-z0-9]|bse:|nse:|\b\d{6}\b|"
            r"\b(reliance|tcs|infy|hdfc|wipro|sbin|nifty|sensex)\b",
            q,
        )
    )
    if stock_specific_pattern and not formula_cue:
        return None

    # Prefer NCFM module / specific TA topics over the broad "technical analysis" umbrella.
    if re.search(
        r"\b(ncfm\s*technical|ncfm\s*ta|nse\s*technical\s*analysis\s*module|"
        r"technical\s*analysis\s*module)\b",
        q,
    ):
        return _TERM_ANSWERS["ncfm technical analysis"]
    if re.search(r"\b(elliott\s*wave|elliot\s*wave)\b", q):
        return _TERM_ANSWERS["elliott wave"]
    if re.search(r"\b(dow\s*theory|principles of dow)\b", q):
        return _TERM_ANSWERS["dow theory"]
    if re.search(
        r"\b(day\s*trading|scalping|intraday\s*trading\s*strateg)",
        q,
    ) and not re.search(r"\b(of|for)\s+[a-z0-9]", q):
        return _TERM_ANSWERS["day trading"]
    if re.search(r"\b(momentum\s*trading|elder\s*impulse)\b", q):
        return _TERM_ANSWERS["momentum trading"]
    if re.search(
        r"\b(trading\s*psychology|trader\s*psychology|golden\s*rules\s*for\s*traders|"
        r"risk\s*management\s*in\s*trading|dos?\s*and\s*don'?ts\s*in\s*trading)\b",
        q,
    ):
        return _TERM_ANSWERS["trading psychology"]
    if re.search(r"\b(trading\s*biases|cognitive\s*bias|confirmation\s*bias|gamblers?\s*fallacy)\b", q):
        return _TERM_ANSWERS["trading biases"]
    if re.search(r"\b(stochastic|stochastics)\b", q):
        return _TERM_ANSWERS["stochastic"]
    if re.search(r"\b(williams?\s*%?r|williams\s*percent)\b", q):
        return _TERM_ANSWERS["williams %r"]
    if re.search(r"\b(money\s*flow\s*index|\bmfi\b)\b", q):
        return _TERM_ANSWERS["money flow index"]
    if re.search(
        r"\b(leading\s*(vs\.?\s*)?lagging|lagging\s*indicators?|leading\s*indicators?|"
        r"types of (technical )?indicators)\b",
        q,
    ):
        return _TERM_ANSWERS["leading indicators"]
    if re.search(r"\b(rounded\s*tops?|rounded\s*bottoms?|saucer\s*tops?|saucer\s*bottoms?)\b", q):
        return _TERM_ANSWERS["rounded top"]
    if re.search(
        r"\b(gap\s*theory|breakaway\s*gap|exhaustion\s*gap|runaway\s*gap|"
        r"measuring\s*gap|common\s*gap|island\s*(cluster|reversal))\b",
        q,
    ):
        return _TERM_ANSWERS["price gaps"]

    # Prefer chart-pattern family over the broad "technical analysis" umbrella.
    if re.search(
        r"\b(chart patterns?|identifying chart patterns|common chart patterns|"
        r"trading patterns)\b",
        q,
    ):
        return _TERM_ANSWERS["chart patterns"]
    if re.search(
        r"\b(false breakout|failed breakout|confirmation filters?|"
        r"trading breakouts?|how to trade breakouts?|entry stops?|"
        r"protective stops?|throwback)\b",
        q,
    ):
        return _TERM_ANSWERS["trading breakouts"]
    if re.search(r"\b(head and shoulders|inverse head and shoulders|head\s*&\s*shoulders)\b", q):
        return _TERM_ANSWERS["head and shoulders"]
    if re.search(r"\b(cup and handle|cup\s*&\s*handle|saucer pattern)\b", q):
        return _TERM_ANSWERS["cup and handle"]
    if re.search(r"\b(double tops?|double bottoms?)\b", q):
        return _TERM_ANSWERS["double top"]
    if re.search(r"\b(triple tops?|triple bottoms?)\b", q):
        return _TERM_ANSWERS["triple top"]
    if re.search(
        r"\b(symmetrical triangle|ascending triangle|descending triangle|"
        r"triangle (chart )?pattern)\b",
        q,
    ):
        return _TERM_ANSWERS["triangle pattern"]
    if re.search(r"\b(rising wedge|falling wedge|wedge (chart )?pattern)\b", q):
        return _TERM_ANSWERS["wedge pattern"]
    if re.search(r"\b(flag and pennant|flag pattern|pennant pattern|\bpennant\b)\b", q):
        return _TERM_ANSWERS["flag pattern"]
    if re.search(r"\b(rectangle (chart )?pattern|trading range pattern)\b", q):
        return _TERM_ANSWERS["rectangle pattern"]
    if re.search(r"\b(price gaps?|gap trading|explosion gap)\b", q):
        return _TERM_ANSWERS["price gaps"]
    if re.search(
        r"\b(narrow range|\bnr4\b|inside bar|pipe bottom|two[- ]bar reversal)\b",
        q,
    ):
        return _TERM_ANSWERS["narrow range"]
    if re.search(r"\b(harami)\b", q):
        return _TERM_ANSWERS["harami"]
    if re.search(r"\b(shooting star|inverted hammer)\b", q):
        return _TERM_ANSWERS["shooting star"]
    if re.search(r"\b(dark cloud|piercing line|piercing pattern)\b", q):
        return _TERM_ANSWERS["dark cloud cover"]

    # Prefer specific MF primers over the broad mutual-fund umbrella.
    if re.search(r"\b(net asset value|\bnav\b)\b", q):
        return _TERM_ANSWERS["nav"]
    if re.search(r"\b(expense ratio|\bter\b|direct vs regular|direct plan|regular plan)\b", q):
        return _TERM_ANSWERS["expense ratio"]
    if re.search(r"\bexit load\b", q):
        return _TERM_ANSWERS["exit load"]
    if re.search(r"\b(how (do|does) (a )?mutual funds? work|how mutual funds work)\b", q):
        return _TERM_ANSWERS["how mutual funds work"]
    if re.search(r"\b(types? of mutual funds|mutual fund types)\b", q):
        return _TERM_ANSWERS["types of mutual funds"]
    if re.search(r"\b(mutual funds? vs\.? (fd|fds|fixed deposit)|mf vs fd)\b", q):
        return _TERM_ANSWERS["mutual funds vs fd"]
    if re.search(r"\b(mutual funds? vs\.? (stocks?|shares?)|mf vs stocks?)\b", q):
        return _TERM_ANSWERS["mutual funds vs stocks"]
    if re.search(r"\b(asset management company|\bamc\b)\b", q) and re.search(
        r"\b(mutual fund|fund|amc|what)\b", q
    ):
        return _TERM_ANSWERS["amc"]

    # Prefer unified F&O / vs-options primers over bare "futures" or "options".
    if re.search(
        r"\b(futures\s+vs\.?\s+options|options\s+vs\.?\s+futures|"
        r"difference between futures and options)\b",
        q,
    ):
        return _TERM_ANSWERS["futures vs options"]
    if re.search(r"\b(hedgers?|speculators?|arbitrageurs?)\b", q) or re.search(
        r"\bwho\b.{0,24}\b(f&o|fno|futures and options|derivatives)\b",
        q,
    ):
        if re.search(r"\bhedgers?\b", q) and not re.search(r"\bspeculat|arbitrage", q):
            return _TERM_ANSWERS["hedgers"]
        if re.search(r"\bspeculators?\b", q) and not re.search(r"\bhedg|arbitrage", q):
            return _TERM_ANSWERS["speculators"]
        if re.search(r"\barbitrageurs?\b", q):
            return _TERM_ANSWERS["arbitrageurs"]
        return _TERM_ANSWERS["f&o participants"]
    if re.search(
        r"\b(futures and options|futures & options|future and options|"
        r"\bf\s*&\s*o\b|\bfno\b|what is f&o|stock derivatives)\b",
        q,
    ) and not re.search(r"\b(strategy|strategies|greek|greeks|straddle|spread)\b", q):
        return _TERM_ANSWERS["futures and options"]

    # Prefer short/long-specific straddle/strangle over generic aliases.
    if re.search(r"\bshort straddle\b", q):
        return _TERM_ANSWERS["short straddle"]
    if re.search(r"\blong straddle\b", q):
        return _TERM_ANSWERS["long straddle"]
    if re.search(r"\bshort strangle\b", q):
        return _TERM_ANSWERS["short strangle"]
    if re.search(r"\blong strangle\b", q):
        return _TERM_ANSWERS["long strangle"]

    # Prefer VaR over bare "var" colliding with variance alias only when phrased as VaR.
    if re.search(r"\b(value at risk|value-at-risk|\bvaar\b)\b", q) or re.search(
        r"\bvar\b", q
    ):
        if re.search(r"\b(value at risk|value-at-risk|var\b)", q) and not re.search(
            r"\b(variance|covariance)\b", q
        ):
            # "var" alone is ambiguous — only if risk/portfolio cues present or exact.
            if re.search(r"\b(value at risk|value-at-risk)\b", q) or re.fullmatch(
                r"\s*var\s*", q
            ) or re.search(r"\b(var|value at risk).{0,20}(95|99|portfolio|risk)\b", q):
                return _TERM_ANSWERS["value at risk"]

    # Prefer specific 3-5-7 framework over broad "risk management" umbrella.
    if re.search(r"\b(3\s*[-–—]?\s*5\s*[-–—]?\s*7|357\s*rule|three\s+five\s+seven)\b", q):
        return _TERM_ANSWERS["3-5-7 rule"]

    # Prefer 15-15-15 SIP corpus rule over bare SIP / mutual-fund umbrella.
    if re.search(
        r"\b(15\s*[-–—]?\s*15\s*[-–—]?\s*15|151515\s*rule|fifteen\s+fifteen\s+fifteen|"
        r"1\s*crore\s*sip|crorepati\s*sip)\b",
        q,
    ) or (
        re.search(r"\b(1\s*crore|₹?\s*1\s*crore|one\s*crore)\b", q)
        and re.search(r"\b(sip|mutual\s*fund)\b", q)
    ):
        return _TERM_ANSWERS["15-15-15 rule"]

    # Prefer 3-6-9 emergency-fund rule over bare "emergency fund" umbrella.
    if re.search(
        r"\b(3\s*[-–—]?\s*6\s*[-–—]?\s*9|369\s*rule|three\s+six\s+nine|"
        r"3-6-9\s*rule\s*of\s*money|rule\s*of\s*money)\b",
        q,
    ) or (
        re.search(r"\b(3|6|9)\s*months?\b", q)
        and re.search(r"\b(emergency|expenses?|job\s*stability|living\s*cost)\b", q)
    ) or re.search(
        r"\bhow many months\b.{0,40}\bemergency\b|\bemergency\b.{0,40}\bhow many months\b",
        q,
    ):
        return _TERM_ANSWERS["3-6-9 rule"]

    for term in sorted(_TERM_ANSWERS.keys(), key=len, reverse=True):
        answer = _TERM_ANSWERS.get(term)
        if not answer:
            continue
        # Multi-word primers: substring match; short terms: word boundary.
        if " " in term:
            if term in q:
                return answer
        elif re.search(r"\b" + re.escape(term) + r"\b", q):
            return answer

    return None
