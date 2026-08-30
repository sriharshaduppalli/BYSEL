package com.bysel.trader.utils

/**
 * Parses natural-language trade intents from AI chat messages.
 * Detects patterns like "buy 10 shares of TCS" or "set alert on RELIANCE above 2800".
 */
object TradeIntentParser {

    data class TradeIntent(
        val action: Action,
        val symbol: String,
        val quantity: Int? = null,
        val price: Double? = null,
        val alertType: String? = null, // "ABOVE" or "BELOW"
        val displayText: String
    )

    enum class Action { BUY, SELL, ALERT, ANALYZE }

    /** Words that appear in AI prose / sentiment blocks but are not NSE tickers. */
    val KNOWN_FALSE_SYMBOLS = setOf(
        "ONLY", "AFTER", "BEFORE", "WITH", "FROM", "THIS", "THAT", "THE", "AND",
        "FOR", "NEAR", "AROUND", "ABOVE", "BELOW", "WHEN", "THEN", "NOW", "BUY",
        "SELL", "HOLD", "TRIM", "WAIT", "ACCUMULATE", "NEUTRAL", "STRONG",
        "SIGNAL", "SCORE", "PRICE", "ENTRY", "TARGET", "STOP", "LOSS", "ALERT",
        "TRADE", "DECISION", "BIAS", "ACTION", "NOTE", "CONFIDENCE", "CONVICTION",
        "SWING", "INTRADAY", "ZONE", "PAPER", "RISK", "REWARD", "QTY", "BUDGET",
        "INVALIDATION", "OVERALL", "DIRECT", "ANSWER", "WHY", "MEANING",
        "SENTIMENT", "ANALYSIS", "LEGEND", "PRACTICE", "MILDLY", "BULLISH",
        "BEARISH", "QUICK", "MATH", "FULL", "KEY", "LEVELS", "TAPE", "VIEW",
        "CHART", "STACK", "QUANTITATIVE", "HORIZON", "STRENGTH", "REDUCE",
        "LIGHTEN", "FRESH", "LONG", "SHORT", "STAGED", "ADDS", "DIPS", "CLEAR",
        "EDGE", "EXIT", "AVOID", "SKIP", "SETUP", "IMPROVES",
        "EARNINGS", "RESULTS", "RESULT", "DIVIDEND", "QUARTER", "QUARTERLY",
        "GUIDANCE", "REVENUE", "CALENDAR", "UPDATE", "HEADLINE", "CATALYST",
        "OUTLOOK", "FORECAST", "ESTIMATE", "PAYOUT", "YIELD", "REPORT",
        "SEASON", "YEAR", "YEARS", "MONTH", "WEEK", "TODAY", "TOMORROW",
        "DATE", "EXDATE", "SESSION", "MARKET", "HISTORY", "TREND",
        "PLAN", "PRINT", "CALL", "PUT", "OPTION", "PREMIUM", "INTO",
        "AHEAD", "STRENGTH", "TAPE", "COPY", "LINE", "EVENT", "CARD",
        "ASK", "OFF", "SIDE", "WING", "SPREAD",
        "ON", "IN", "AT", "TO", "OF", "IF", "OR", "AN", "AS", "BY", "UP",
        "IT", "NO", "SO", "MY", "WE", "BE",
        "MILLION", "BILLION", "TRILLION", "CRORE", "CRORES", "LAKH", "LAKHS",
        "LACS", "LAC", "THOUSAND", "THOUSANDS", "MN", "BN", "CR", "VOLUME",
        "SHARES", "SHARE", "UNITS", "UNIT", "LOTS", "LOT",
        "ALREADY", "HANDS", "CHANGED", "IDEA", "HEADLINE", "ABOUT", "WAS",
        "EQUIVALENT", "USD", "CAP", "DIPS", "STRUCTURE", "VOLUME",
    )

    private val SHARE_UNITS = Regex("""(?i)\b(?:shares?|lots?|qty|units?)\b""")
    private val SCALE_WORD = Regex(
        """(?i)^(?:million|billion|trillion|crore|crores|lakh|lakhs|lacs|lac|thousand|thousands|mn|bn|cr)$"""
    )

    private val BUY_PATTERN = Regex(
        """(?i)\b(?:buy|purchase|accumulate|go long)\b[^.]*?\b(\d+)\s*(?:(?:million|billion|trillion|crore|crores|lakh|lakhs|lacs|thousand|mn|bn|cr)\s+)?(?:shares?|lots?|qty|units?)?\s*(?:of\s+)?([A-Z]{2,20})\b"""
    )
    private val BUY_SIMPLE = Regex(
        """(?i)\b(?:buy|purchase|go long)\b\s+([A-Z]{2,20})\b"""
    )
    private val SELL_PATTERN = Regex(
        """(?i)\b(?:sell|exit|book profits?|offload)\b[^.]*?\b(\d+)\s*(?:(?:million|billion|trillion|crore|crores|lakh|lakhs|lacs|thousand|mn|bn|cr)\s+)?(?:shares?|lots?|qty|units?)?\s*(?:of\s+)?([A-Z]{2,20})\b"""
    )
    private val SELL_SIMPLE = Regex(
        """(?i)\b(?:sell|book profits?(?:\s+on)?)\b\s+([A-Z]{2,20})\b"""
    )
    private val ALERT_PATTERN = Regex(
        """(?i)\b(?:set|create)\s+(?:an?\s+)?(?:price\s+)?alert\b[^.]*?([A-Z]{2,20})\s+(?:when|if|at)?\s*(?:it\s+)?(?:goes?\s+)?(above|below|crosses?)\s+(?:₹?\s*)?(\d+(?:\.\d+)?)"""
    )
    private val ALERT_SIMPLE = Regex(
        """(?i)\b(?:set|create)\s+(?:an?\s+)?(?:price\s+)?alert\b[^.]*?\b(?:for|on)\s+([A-Z]{2,20})\b(?:[^.]*?(?:at|above|below|@)\s*₹?\s*(\d+(?:\.\d+)?))?"""
    )
    private val ALERT_ME = Regex(
        """(?i)\balert\s+me\b[^.]*?\b([A-Z]{2,20})\b[^.]*?(above|below|crosses?|at)\s*₹?\s*(\d+(?:\.\d+)?)"""
    )
    // Avoid bare "analysis" — it false-matches "Sentiment analysis: … Overall …"
    private val ANALYZE_PATTERN = Regex(
        """(?i)\b(?:analyze|technical analysis of|fundamental analysis of|chart for|view chart(?:\s+for)?)\b[^.]*?\b([A-Z][A-Z0-9.&-]{1,15})\b"""
    )
    private val PAREN_SYMBOL = Regex("""\(([A-Z][A-Z0-9.&-]{1,19})\)""")
    private val DECISION_BIAS = Regex("""(?i)decision\s+bias\s*:\s*\**\s*(strong[\s_]?buy|buy|accumulate|strong[\s_]?sell|sell|hold)""")
    private val CURRENT_PRICE = Regex("""(?i)(?:current\s+price|price)\s*:\s*₹?\s*([\d,]+(?:\.\d+)?)""")

    // Recommendation patterns from AI responses: "We recommend buying TCS", "Consider buying 5 shares of INFY"
    private val AI_BUY_REC = Regex(
        """(?i)(?:recommend|consider|suggest)\s+(?:buying|purchasing|accumulating)\s+(?:(\d+)\s+(?:shares?\s+(?:of\s+)?)?)?([A-Z]{2,20})\b"""
    )
    private val AI_SELL_REC = Regex(
        """(?i)(?:recommend|consider|suggest)\s+(?:selling|exiting|booking profits? (?:on|in)?)\s+([A-Z]{2,20})\b"""
    )

    /** Extract trade intents from a message. Returns empty list if no intents found. */
    fun parse(message: String, contextSymbol: String? = null): List<TradeIntent> {
        val intents = mutableListOf<TradeIntent>()
        val fallbackSymbol = contextSymbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: PAREN_SYMBOL.find(message)?.groupValues?.get(1)?.uppercase()

        // Buy with quantity
        BUY_PATTERN.find(message)?.let { match ->
            val parsed = parseQtyAndSymbol(match.groupValues[1], match.groupValues[2], match.value)
                ?: return@let
            val (qty, symbol) = parsed
            intents.add(TradeIntent(Action.BUY, symbol, qty, displayText = "Buy $qty $symbol"))
        }

        // Simple buy (no quantity)
        if (intents.none { it.action == Action.BUY }) {
            BUY_SIMPLE.find(message)?.let { match ->
                val symbol = sanitizeSymbol(match.groupValues[1])
                if (symbol != null) {
                    intents.add(TradeIntent(Action.BUY, symbol, displayText = "Buy $symbol"))
                }
            }
        }

        // Sell with quantity
        SELL_PATTERN.find(message)?.let { match ->
            val parsed = parseQtyAndSymbol(match.groupValues[1], match.groupValues[2], match.value)
                ?: return@let
            val (qty, symbol) = parsed
            intents.add(TradeIntent(Action.SELL, symbol, qty, displayText = "Sell $qty $symbol"))
        }

        if (intents.none { it.action == Action.SELL }) {
            SELL_SIMPLE.find(message)?.let { match ->
                val symbol = sanitizeSymbol(match.groupValues[1])
                if (symbol != null) {
                    intents.add(TradeIntent(Action.SELL, symbol, displayText = "Sell $symbol"))
                }
            }
        }

        // Alert patterns
        ALERT_PATTERN.find(message)?.let { match ->
            val symbol = sanitizeSymbol(match.groupValues[1]) ?: fallbackSymbol ?: return@let
            val direction = match.groupValues[2].lowercase()
            val price = match.groupValues[3].toDoubleOrNull()
            val alertType = if (direction.contains("below")) "BELOW" else "ABOVE"
            intents.add(
                TradeIntent(
                    Action.ALERT,
                    symbol,
                    price = price,
                    alertType = alertType,
                    displayText = "Alert: $symbol $alertType ₹${price ?: ""}".trim()
                )
            )
        }

        if (intents.none { it.action == Action.ALERT }) {
            ALERT_ME.find(message)?.let { match ->
                val symbol = sanitizeSymbol(match.groupValues[1]) ?: fallbackSymbol ?: return@let
                val direction = match.groupValues[2].lowercase()
                val price = match.groupValues[3].toDoubleOrNull()
                val alertType = if (direction.contains("below")) "BELOW" else "ABOVE"
                intents.add(
                    TradeIntent(
                        Action.ALERT,
                        symbol,
                        price = price,
                        alertType = alertType,
                        displayText = "Alert: $symbol $alertType ₹${price ?: ""}".trim()
                    )
                )
            }
        }

        if (intents.none { it.action == Action.ALERT }) {
            ALERT_SIMPLE.find(message)?.let { match ->
                val symbol = sanitizeSymbol(match.groupValues[1]) ?: fallbackSymbol ?: return@let
                val price = match.groupValues.getOrNull(2)?.toDoubleOrNull()
                intents.add(
                    TradeIntent(
                        Action.ALERT,
                        symbol,
                        price = price,
                        alertType = "ABOVE",
                        displayText = if (price != null) "Alert: $symbol ABOVE ₹$price" else "Set Alert $symbol"
                    )
                )
            }
        }

        // AI recommendations
        AI_BUY_REC.find(message)?.let { match ->
            if (intents.none { it.action == Action.BUY }) {
                val qty = match.groupValues[1].toIntOrNull()
                val symbol = sanitizeSymbol(match.groupValues[2]) ?: return@let
                intents.add(TradeIntent(Action.BUY, symbol, qty, displayText = "Buy ${qty ?: ""} $symbol".trim()))
            }
        }

        AI_SELL_REC.find(message)?.let { match ->
            if (intents.none { it.action == Action.SELL }) {
                val symbol = sanitizeSymbol(match.groupValues[1]) ?: return@let
                intents.add(TradeIntent(Action.SELL, symbol, displayText = "Sell $symbol"))
            }
        }

        // Structured BYSEL trade-decision replies: "Trade Decision: Name (TCS)" + Decision Bias BUY/SELL
        if (fallbackSymbol != null) {
            val bias = DECISION_BIAS.find(message)?.groupValues?.get(1)?.uppercase()?.replace(" ", "_")
            if (bias != null) {
                when {
                    bias.contains("BUY") || bias.contains("ACCUMULATE") -> {
                        if (intents.none { it.action == Action.BUY }) {
                            intents.add(TradeIntent(Action.BUY, fallbackSymbol, displayText = "Buy $fallbackSymbol"))
                        }
                    }
                    bias.contains("SELL") -> {
                        if (intents.none { it.action == Action.SELL }) {
                            intents.add(TradeIntent(Action.SELL, fallbackSymbol, displayText = "Sell $fallbackSymbol"))
                        }
                    }
                }
            }
            if (intents.none { it.action == Action.ALERT }) {
                val price = CURRENT_PRICE.find(message)
                    ?.groupValues?.get(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
                intents.add(
                    TradeIntent(
                        Action.ALERT,
                        fallbackSymbol,
                        price = price?.let { it * 1.02 },
                        alertType = "ABOVE",
                        displayText = "Set Alert $fallbackSymbol"
                    )
                )
            }
            if (intents.none { it.action == Action.ANALYZE }) {
                intents.add(
                    TradeIntent(
                        Action.ANALYZE,
                        fallbackSymbol,
                        displayText = "View chart $fallbackSymbol",
                    )
                )
            }
        }

        ANALYZE_PATTERN.find(message)?.let { match ->
            val symbol = sanitizeSymbol(match.groupValues[1]) ?: return@let
            if (intents.none { it.action == Action.ANALYZE && it.symbol == symbol }) {
                intents.add(
                    TradeIntent(
                        Action.ANALYZE,
                        symbol,
                        displayText = "View chart $symbol",
                    )
                )
            }
        }

        return intents.distinctBy { "${it.action}:${it.symbol}" }
    }

    private fun sanitizeSymbol(raw: String?): String? {
        val symbol = raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        if (symbol in KNOWN_FALSE_SYMBOLS) return null
        if (SCALE_WORD.matches(symbol)) return null
        if (symbol.length < 2) return null
        if (symbol.any { it.isDigit() }) return null
        return symbol
    }

    /**
     * "exit … 2026 earnings" is a calendar phrase, not "sell 2026 shares of EARNINGS".
     * Years only count as qty when the user wrote shares/lots/qty/units.
     */
    private fun parseQtyAndSymbol(qtyRaw: String, symbolRaw: String, matched: String): Pair<Int, String>? {
        val symbol = sanitizeSymbol(symbolRaw) ?: return null
        val qty = qtyRaw.toIntOrNull() ?: return null
        if (qty <= 0 || qty > 100_000) return null
        val yearLike = qty in 1900..2100
        if (yearLike && !SHARE_UNITS.containsMatchIn(matched)) return null
        return qty to symbol
    }
}
