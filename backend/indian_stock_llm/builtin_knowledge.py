"""Production Indian-market knowledge pack for BYSEL's local LLM tier.

These items are always merged into the RAG index so educational, equation,
market-mechanics, and analysis queries stay grounded without a remote model.
"""

from __future__ import annotations

from .knowledge_base import KnowledgeItem


def builtin_knowledge_items() -> list[KnowledgeItem]:
    raw = [
        # ── Equations / calculations ─────────────────────────────────
        {
            "id": "eq_rsi",
            "title": "RSI equation and reading",
            "content": (
                "RSI (Relative Strength Index): RSI = 100 - (100 / (1 + RS)), "
                "where RS = Average Gain / Average Loss over N periods (usually 14). "
                "Above 70 often overbought; below 30 often oversold. On NSE, pair RSI with "
                "trend and volume — strong momentum names can stay overbought for weeks."
            ),
            "tags": ["rsi", "equation", "technical", "calculation", "indicator", "analysis"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_macd",
            "title": "MACD equations",
            "content": (
                "MACD Line = EMA(12) - EMA(26); Signal = EMA(9) of MACD; Histogram = MACD - Signal. "
                "Bullish when MACD crosses above Signal; bearish when below. Avoid trading every "
                "tiny cross in sideways Nifty ranges."
            ),
            "tags": ["macd", "equation", "technical", "calculation", "ema", "analysis"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_pe",
            "title": "P/E valuation equation",
            "content": (
                "P/E = Price / EPS. Trailing uses last-12-month EPS; forward uses estimates. "
                "Compare within sector (TCS vs INFY, HDFCBANK vs ICICIBANK), not across banks vs IT. "
                "Low P/E alone is not undervaluation — check growth, debt, and earnings quality."
            ),
            "tags": ["pe", "valuation", "fundamentals", "equation", "eps", "analysis"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_pb",
            "title": "P/B equation",
            "content": (
                "Price-to-Book = Market Price / Book Value per Share. Useful for banks and NBFCs "
                "(HDFCBANK, SBIN, BAJFINANCE). High P/B can be justified by high ROE; low P/B may "
                "signal asset quality or profitability stress."
            ),
            "tags": ["pb", "price to book", "valuation", "fundamentals", "banking", "equation"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_cagr",
            "title": "CAGR equation",
            "content": (
                "CAGR = (Ending / Beginning) ^ (1 / Years) - 1. Example: 1,00,000 to 1,61,051 in 5 years "
                "is about 10% CAGR. Use for multi-year stock or SIP returns; avoid applying CAGR to "
                "noisy 1–2 week moves."
            ),
            "tags": ["cagr", "return", "calculation", "equation", "sip"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_roe_roce",
            "title": "ROE and ROCE equations",
            "content": (
                "ROE = Net Profit / Equity. ROCE = EBIT / Capital Employed (Equity + Debt - Cash approx). "
                "Prefer businesses with durable ROE/ROCE and controlled leverage. High ROE from debt alone "
                "is a red flag."
            ),
            "tags": ["roe", "roce", "fundamentals", "equation", "quality", "analysis"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_eps",
            "title": "EPS equation",
            "content": (
                "EPS = Net profit attributable to equity / weighted average shares. Diluted EPS includes "
                "ESOPs and convertibles. EPS growth drives long-term equity compounding on NSE/BSE."
            ),
            "tags": ["eps", "earnings", "fundamentals", "equation"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_vwap_atr",
            "title": "VWAP and ATR equations",
            "content": (
                "VWAP = sum(Price × Volume) / sum(Volume) for the session — institutional intraday benchmark. "
                "ATR averages True Range (max of high-low, |high-prev close|, |low-prev close|). Use ATR for "
                "stop distance and position sizing, not direction."
            ),
            "tags": ["vwap", "atr", "volatility", "technical", "equation", "intraday"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_bollinger_sharpe",
            "title": "Bollinger Bands and Sharpe",
            "content": (
                "Bollinger: Middle = SMA(N); Upper/Lower = Middle ± k×StdDev (often N=20, k=2). "
                "Sharpe = (Return - Risk-free rate) / StdDev of returns. Higher Sharpe = better "
                "risk-adjusted performance."
            ),
            "tags": ["bollinger", "sharpe", "equation", "volatility", "risk", "calculation"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_rr_drawdown",
            "title": "Risk-reward and drawdown",
            "content": (
                "Risk/Reward = (Target - Entry) / (Entry - Stop). Many traders prefer ≥ 1:2. "
                "Drawdown = (Trough - Peak) / Peak; max drawdown is the worst peak-to-trough fall. "
                "Size positions so a full stop does not ruin the portfolio."
            ),
            "tags": ["risk", "reward", "drawdown", "stop loss", "portfolio", "equation"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_beta_peg",
            "title": "Beta and PEG",
            "content": (
                "Beta measures sensitivity to Nifty moves (beta > 1 amplifies market swings). "
                "PEG = P/E / expected earnings growth rate. PEG near 1 can imply fair growth-adjusted "
                "valuation, but growth estimates are uncertain."
            ),
            "tags": ["beta", "peg", "valuation", "risk", "equation", "nifty"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "eq_dividend_yield",
            "title": "Dividend yield and payout",
            "content": (
                "Dividend Yield = Annual Dividend per Share / Price. Payout Ratio = Dividends / Net Profit. "
                "PSU and mature cash businesses (COALINDIA, ITC, ONGC) often lead yield screens; growth IT "
                "usually pays less."
            ),
            "tags": ["dividend", "yield", "payout", "fundamentals", "equation", "income"],
            "source": "bysel_builtin_v1",
        },
        # ── Market mechanics / NSE-BSE-SEBI ──────────────────────────
        {
            "id": "mx_nse_bse",
            "title": "NSE and BSE basics",
            "content": (
                "NSE and BSE are India's primary equity exchanges. Most liquid stocks trade on NSE with "
                "symbols like RELIANCE, TCS, INFY. BSE uses scrip codes; both map to the same ISIN. "
                "BYSEL quotes typically use NSE Yahoo tickers (SYMBOL.NS)."
            ),
            "tags": ["nse", "bse", "exchange", "isin", "symbols", "stocks"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_sebi",
            "title": "SEBI investor protection",
            "content": (
                "SEBI regulates securities markets, disclosures, insider trading, brokers, and mutual funds. "
                "Listed companies must publish quarterly results and material events. Treat tip lines and "
                "guaranteed-return claims as compliance red flags."
            ),
            "tags": ["sebi", "regulation", "compliance", "disclosure", "events"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_circuit",
            "title": "Circuit filters and freeze",
            "content": (
                "NSE/BSE apply price bands (circuits) that halt trading beyond ±X% from the previous close "
                "or reference price. Bands vary by stock (often 5/10/20%). Index circuit breakers can pause "
                "the whole market on extreme moves. Never assume you can exit at any price during a freeze."
            ),
            "tags": ["circuit", "price band", "nse", "risk", "trading", "volatility"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_session",
            "title": "Indian cash market session",
            "content": (
                "Regular NSE/BSE cash session is roughly 9:15 AM to 3:30 PM IST, Monday–Friday "
                "(excluding holidays). Pre-open and post-close windows exist for orders/settlement. "
                "After-hours charts may reflect global cues but cash liquidity is session-bound."
            ),
            "tags": ["market hours", "nse", "session", "trading", "ist"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_settlement",
            "title": "T+1 settlement and delivery",
            "content": (
                "Indian equity cash currently settles on T+1. Delivery trades give you shares in demat; "
                "intraday/MIS square-off before close. Delivery % = delivery volume / total volume — "
                "high delivery can support genuine accumulation narratives."
            ),
            "tags": ["settlement", "delivery", "t+1", "demat", "trading"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_fo",
            "title": "F&O basics India",
            "content": (
                "Futures & Options on NSE cover indices (NIFTY, BANKNIFTY) and many stocks with lot sizes. "
                "Weekly/monthly expiries, mark-to-market on futures, and option Greeks (delta, gamma, theta, vega) "
                "drive risk. F&O is leveraged — margin calls and expiry decay can wipe capital quickly."
            ),
            "tags": ["fno", "futures", "options", "nifty", "banknifty", "derivatives", "greeks"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_iv_greeks",
            "title": "Implied volatility and Greeks",
            "content": (
                "Implied Volatility (IV) is the market's expected move priced into options. "
                "Delta ≈ directional sensitivity; Theta = time decay; Vega = sensitivity to IV. "
                "High IV around events (RBI, results) makes options expensive — selling premium has "
                "different risk than buying lottery calls."
            ),
            "tags": ["iv", "implied volatility", "delta", "theta", "vega", "options", "equation"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_fii_dii",
            "title": "FII and DII flows",
            "content": (
                "FIIs are foreign institutions; DIIs are domestic mutual funds/insurers. Persistent FII selling "
                "often pressures Nifty/banks/IT; DII SIP buying can cushion drawdowns. Flows are context, "
                "not a standalone buy/sell signal for one stock."
            ),
            "tags": ["fii", "dii", "flows", "nifty", "institutional", "macro"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "mx_indices",
            "title": "Key Indian indices and symbols",
            "content": (
                "NIFTY50 tracks 50 large NSE names; SENSEX is BSE's 30-stock index; BANKNIFTY tracks banks. "
                "Sector indices (IT, Pharma, Auto, FMCG, Energy, Realty, PSU Bank) help relative strength. "
                "In BYSEL, index symbols include NIFTY50, SENSEX, BANKNIFTY."
            ),
            "tags": ["nifty", "sensex", "banknifty", "indices", "symbols", "stocks"],
            "source": "bysel_builtin_v1",
        },
        # ── Analysis frameworks ──────────────────────────────────────
        {
            "id": "an_checklist",
            "title": "Stock analysis checklist",
            "content": (
                "Practical analysis stack: (1) Business quality — ROE/ROCE, margins, cash flow. "
                "(2) Valuation — P/E, P/B, PEG vs peers/history. (3) Technical — trend, RSI/MACD, support/resistance. "
                "(4) Events — results, guidance, promoter pledge, regulatory news. (5) Risk — debt, circuit, liquidity. "
                "Score conviction only after at least three pillars agree."
            ),
            "tags": ["analysis", "fundamental", "technical", "checklist", "stocks"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "an_support_resistance",
            "title": "Support and resistance",
            "content": (
                "Support is a price zone where buying historically appears; resistance where selling appears. "
                "Use prior swing highs/lows, round numbers, and volume nodes. Breakouts need volume confirmation; "
                "failed breakouts often reverse quickly on NSE momentum names."
            ),
            "tags": ["support", "resistance", "technical", "analysis", "price action"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "an_prediction_guard",
            "title": "Prediction guardrails",
            "content": (
                "Price forecasts must be probabilistic with ranges, not guarantees. Combine trend, valuation, "
                "and catalysts; state invalidation (stop) clearly. Never promise returns. Prefer scenario "
                "analysis: bull / base / bear with catalysts for each."
            ),
            "tags": ["prediction", "forecast", "risk", "uncertainty", "guidance"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "an_portfolio",
            "title": "Portfolio construction India",
            "content": (
                "Diversify across sectors (banking, IT, consumer, energy, pharma) and market caps. "
                "Avoid single-stock concentration above a risk budget. Rebalance periodically; use SIP for "
                "core equity and satellites for high-conviction ideas with stops."
            ),
            "tags": ["portfolio", "diversification", "risk", "sip", "allocation"],
            "source": "bysel_builtin_v1",
        },
        # ── Sector primers ───────────────────────────────────────────
        {
            "id": "sec_banking",
            "title": "Banking sector primer",
            "content": (
                "Private banks (HDFCBANK, ICICIBANK, AXISBANK, KOTAKBANK) and PSU banks (SBIN) are sensitive to "
                "RBI rates, credit growth, NIM, GNPA/NNPA, and CASA. Rate cuts can help margins with lag; "
                "asset-quality shocks re-rate P/B quickly."
            ),
            "tags": ["banking", "rbi", "hdfcbank", "sbin", "nim", "fundamentals", "sector"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "sec_it",
            "title": "IT sector primer",
            "content": (
                "IT majors (TCS, INFY, WIPRO, HCLTECH, TECHM) depend on US/EU demand, deal wins, attrition, "
                "and currency. A weak rupee can aid reported revenue; discretionary tech spend slowdowns "
                "hurt midcaps first. Watch guidance and large-deal pipelines."
            ),
            "tags": ["it", "tcs", "infy", "wipro", "earnings", "sector", "guidance"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "sec_pharma",
            "title": "Pharma sector primer",
            "content": (
                "Pharma names (SUNPHARMA, DRREDDY, CIPLA, DIVISLAB, LUPIN) mix domestic formulations, US generics, "
                "and API/CDMO. FDA observations, price erosion, and product approvals drive swings. "
                "Export mix and currency matter for margins."
            ),
            "tags": ["pharma", "sunpharma", "cipla", "sector", "earnings"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "sec_auto_energy",
            "title": "Auto and energy primers",
            "content": (
                "Auto (TATAMOTORS, MARUTI, M&M) tracks volumes, rural demand, EV transition, and commodity costs. "
                "Energy/PSU (RELIANCE, ONGC, NTPC, POWERGRID, COALINDIA) mixes refining/marketing, upstream, "
                "and regulated utilities — sensitive to crude, tarifs, and government policy."
            ),
            "tags": ["auto", "energy", "reliance", "maruti", "ntpc", "sector"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "sec_fmcg_defence",
            "title": "FMCG and defence primers",
            "content": (
                "FMCG (HINDUNILVR, ITC, NESTLEIND, BRITANNIA) focuses on volume growth, rural recovery, and margins. "
                "Defence (HAL, BEL, BDL) is order-book and execution driven with government capex cycles — "
                "valuations can stay elevated on multi-year visibility."
            ),
            "tags": ["fmcg", "defence", "hal", "bel", "itc", "sector"],
            "source": "bysel_builtin_v1",
        },
        # ── Symbol / listing literacy ────────────────────────────────
        {
            "id": "sym_literacy",
            "title": "Reading Indian stock symbols",
            "content": (
                "NSE equity symbols are usually uppercase tickers (RELIANCE, HDFCBANK). Prefer exact symbols in "
                "queries for BYSEL. Yahoo mapping appends .NS for NSE and .BO for many BSE codes. "
                "Indices use special tickers (NIFTY50, SENSEX, BANKNIFTY). Always verify company name when "
                "symbols look similar (e.g. HDFCBANK vs HDFCLIFE)."
            ),
            "tags": ["symbols", "ticker", "nse", "bse", "stocks", "search"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "sym_bluechips",
            "title": "Common liquid large-cap symbols",
            "content": (
                "Highly traded large caps include RELIANCE, TCS, INFY, HDFCBANK, ICICIBANK, SBIN, ITC, LT, "
                "BHARTIARTL, AXISBANK, KOTAKBANK, BAJFINANCE, HINDUNILVR, SUNPHARMA, MARUTI, TITAN, NTPC, "
                "POWERGRID, ULTRACEMCO, TATAMOTORS, TATASTEEL, WIPRO, HCLTECH. These dominate Nifty weight "
                "and BYSEL default watchlists."
            ),
            "tags": ["symbols", "nifty", "largecap", "stocks", "watchlist"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "term_sip_ipo",
            "title": "SIP and IPO terms",
            "content": (
                "SIP invests a fixed amount regularly into mutual funds for rupee-cost averaging. "
                "IPO lists a private company on NSE/BSE under SEBI rules; retail often uses UPI/ASBA and "
                "may face lottery allotment when oversubscribed. Listing gains are never guaranteed."
            ),
            "tags": ["sip", "ipo", "mutual fund", "sebi", "terms"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "term_order_types",
            "title": "Order types and charges context",
            "content": (
                "Market orders fill at available price; limit orders set your price. SL/SL-M help risk control. "
                "Indian trades incur brokerage, STT, exchange fees, GST, and stamp duty — always check "
                "pre-trade estimates before sizing."
            ),
            "tags": ["orders", "stop loss", "charges", "stt", "trading", "terms"],
            "source": "bysel_builtin_v1",
        },
        {
            "id": "term_promoter_pledge",
            "title": "Promoter holding and pledging",
            "content": (
                "Promoter holding shows skin in the game. High promoter pledging can be a stress signal if "
                "prices fall and lenders force sales. Track shareholding pattern changes and pledged "
                "percentage in quarterly disclosures."
            ),
            "tags": ["promoter", "pledge", "shareholding", "risk", "fundamentals"],
            "source": "bysel_builtin_v1",
        },
    ]
    return [KnowledgeItem(**item) for item in raw]
