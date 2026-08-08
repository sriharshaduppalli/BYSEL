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

    private val KNOWN_FALSE_SYMBOLS = setOf(
        "ONLY", "AFTER", "BEFORE", "WITH", "FROM", "THIS", "THAT", "THE", "AND",
        "FOR", "NEAR", "AROUND", "ABOVE", "BELOW", "WHEN", "THEN", "NOW", "BUY",
        "SELL", "HOLD", "STRONG", "SIGNAL", "SCORE", "PRICE", "ENTRY", "TARGET",
        "STOP", "LOSS", "ALERT", "TRADE", "DECISION", "BIAS", "ACTION", "NOTE",
        "CONFIDENCE", "CONVICTION", "SWING", "INTRADAY", "ZONE", "SCORE", "PAPER",
        "RISK", "REWARD", "QTY", "BUDGET", "INVALIDATION"
    )

    private val BUY_PATTERN = Regex(
        """(?i)\b(?:buy|purchase|accumulate|go long)\b[^.]*?\b(\d+)\s*(?:shares?|lots?|qty|units?)?\s*(?:of\s+)?([A-Z]{2,20})\b"""
    )
    private val BUY_SIMPLE = Regex(
        """(?i)\b(?:buy|purchase|go long)\b\s+([A-Z]{2,20})\b"""
    )
    private val SELL_PATTERN = Regex(
        """(?i)\b(?:sell|exit|book profits?|offload)\b[^.]*?\b(\d+)\s*(?:shares?|lots?|qty|units?)?\s*(?:of\s+)?([A-Z]{2,20})\b"""
    )
    private val SELL_SIMPLE = Regex(
        """(?i)\b(?:sell|exit|book profits?)\b\s+([A-Z]{2,20})\b"""
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
    private val ANALYZE_PATTERN = Regex(
        """(?i)\b(?:analyze|analysis|technical analysis|fundamental analysis)\b[^.]*?([A-Z]{2,20})\b"""
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
            val qty = match.groupValues[1].toIntOrNull()
            val symbol = sanitizeSymbol(match.groupValues[2]) ?: return@let
            intents.add(TradeIntent(Action.BUY, symbol, qty, displayText = "Buy ${qty ?: ""} $symbol".trim()))
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
            val qty = match.groupValues[1].toIntOrNull()
            val symbol = sanitizeSymbol(match.groupValues[2]) ?: return@let
            intents.add(TradeIntent(Action.SELL, symbol, qty, displayText = "Sell ${qty ?: ""} $symbol".trim()))
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
                intents.add(TradeIntent(Action.ANALYZE, fallbackSymbol, displayText = "View $fallbackSymbol"))
            }
        }

        ANALYZE_PATTERN.find(message)?.let { match ->
            val symbol = sanitizeSymbol(match.groupValues[1]) ?: return@let
            if (intents.none { it.action == Action.ANALYZE && it.symbol == symbol }) {
                intents.add(TradeIntent(Action.ANALYZE, symbol, displayText = "View $symbol"))
            }
        }

        return intents.distinctBy { "${it.action}:${it.symbol}" }
    }

    private fun sanitizeSymbol(raw: String?): String? {
        val symbol = raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        if (symbol in KNOWN_FALSE_SYMBOLS) return null
        if (symbol.length < 2) return null
        return symbol
    }
}
