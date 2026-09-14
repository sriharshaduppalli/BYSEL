package com.bysel.trader.utils

/**
 * Buy/sell CTAs are valid only when this user turn named a stock
 * (or is a short pronoun follow-up). Literacy and general how-to asks
 * must not inherit the open quote or the last ticker.
 */
object TradeCtaPolicy {
    private val INDEX_SYMBOLS = setOf(
        "NIFTY", "NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYBANK", "NIFTYIT", "INDIAVIX",
    )

    private val LITERACY_WORDS = setOf(
        "RSI", "MACD", "SMA", "EMA", "ATR", "VWAP", "SIP", "PE", "PB", "EPS",
        "ROE", "CAGR", "ETF", "IPO", "SEBI", "NSE", "BSE", "FII", "DII",
    )

    private val COMPANY_NAMES = mapOf(
        "reliance" to "RELIANCE", "ril" to "RELIANCE",
        "tcs" to "TCS", "infosys" to "INFY", "infy" to "INFY",
        "hdfc bank" to "HDFCBANK", "icici bank" to "ICICIBANK",
        "sbi" to "SBIN", "wipro" to "WIPRO", "tata motors" to "TMPV",
        "maruti" to "MARUTI", "lupin" to "LUPIN", "cipla" to "CIPLA",
        "airtel" to "BHARTIARTL", "zomato" to "ETERNAL",
    )

    private val KNOWN_TICKERS = setOf(
        "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "WIPRO", "HCLTECH",
        "SBIN", "BAJFINANCE", "KOTAKBANK", "AXISBANK", "MARUTI", "TITAN",
        "SUNPHARMA", "LUPIN", "CIPLA", "DRREDDY", "TMPV", "TMCV", "TATASTEEL",
        "ETERNAL", "BHARTIARTL", "ONGC", "NTPC", "ITC", "LT", "HAL", "BEL",
        "KAYNES", "TRENT", "DMART", "IRCTC",
    )

    private val DEFINITION = Regex(
        """\b(what is|what are|define|definition|meaning of|explain|how to|how does|how do i|difference between|formula|equation|teach)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val FOLLOW_UP = Regex(
        """^(what about|how about|and |also |same |yes\b|ok\b|why\?|simplify|tell me more|what next)|(\b(it|that stock|this stock|the same stock|its)\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val TRADE_ASK = Regex(
        """\b(should i (buy|sell)|buy or sell|hold or (exit|sell)|kharid|bech|entry zone|trade plan|accumulate|trim)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TRADE_PLAN_ANSWER = Regex(
        """(paper (buy|sell) plan|\*\*action:\*\*\s*(buy|sell|hold|trim|wait)|\*\*direct answer:\*\*\s*(buy|sell|hold|trim|accumulate))""",
        RegexOption.IGNORE_CASE,
    )
    private val BUY_SELL_CHIP = Regex(
        """(should i (buy|sell)|wait for a dip|good (buy|time to (buy|sell))|is it a good (buy|investment)|practice (buy|sell)|risk vs reward for buying|best entry price)""",
        RegexOption.IGNORE_CASE,
    )

    fun isDefinitional(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return false
        if (DEFINITION.containsMatchIn(q)) return true
        return q.contains("\u0c05\u0c02\u0c1f\u0c47") || q.contains("\u0c0f\u0c2e\u0c3f\u0c1f\u0c3f")
    }

    fun isFollowUp(query: String): Boolean = FOLLOW_UP.containsMatchIn(query.trim())

    fun isTradeAsk(query: String): Boolean {
        val q = query.trim()
        if (TRADE_ASK.containsMatchIn(q)) return true
        return q.contains("\u0c15\u0c4a\u0c28\u0c3e\u0c32\u0c3e") ||
            q.contains("\u0c05\u0c2e\u0c4d\u0c2e\u0c3e\u0c32\u0c3e")
    }

    fun isGeneralTopic(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        if (isDefinitional(q) && namedSymbol(q) == null) return true
        return false
    }

    fun namedSymbol(query: String): String? {
        val q = query.trim()
        if (q.isBlank()) return null
        val upper = q.uppercase()
        for (sym in KNOWN_TICKERS) {
            if (Regex("""\b${Regex.escape(sym)}\b""").containsMatchIn(upper) && sym !in LITERACY_WORDS) {
                return sym
            }
        }
        val lower = q.lowercase()
        for ((name, sym) in COMPANY_NAMES.entries.sortedByDescending { it.key.length }) {
            if (lower.contains(name)) return sym
        }
        return null
    }

    fun isIndexSymbol(symbol: String?): Boolean {
        val sym = symbol?.trim()?.uppercase().orEmpty()
        return sym.isNotBlank() && sym in INDEX_SYMBOLS
    }

    fun allowsAttachedSymbol(query: String): Boolean {
        if (isGeneralTopic(query)) return false
        if (namedSymbol(query) != null) return true
        return isFollowUp(query)
    }

    fun allowsPracticeTrade(query: String, answer: String = "", symbol: String? = null): Boolean {
        if (isIndexSymbol(symbol)) return false
        if (!allowsAttachedSymbol(query)) return false
        if (isGeneralTopic(query)) return false
        return isTradeAsk(query) || TRADE_PLAN_ANSWER.containsMatchIn(answer)
    }

    fun isBuySellChip(text: String): Boolean {
        val tip = text.trim()
        if (tip.isBlank()) return false
        if (BUY_SELL_CHIP.containsMatchIn(tip)) return true
        return tip.contains("\u0c15\u0c4a\u0c28\u0c3e\u0c32\u0c3e") ||
            tip.contains("\u0c05\u0c2e\u0c4d\u0c2e\u0c3e\u0c32\u0c3e")
    }

    fun filterSuggestions(query: String, suggestions: List<String>): List<String> {
        if (allowsAttachedSymbol(query) && !isGeneralTopic(query)) return suggestions
        return suggestions.filterNot { isBuySellChip(it) }
    }
}
