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
    private val ANALYZE_PATTERN = Regex(
        """(?i)\b(?:analyze|analysis|technical analysis|fundamental analysis)\b[^.]*?([A-Z]{2,20})\b"""
    )

    // Recommendation patterns from AI responses: "We recommend buying TCS", "Consider buying 5 shares of INFY"
    private val AI_BUY_REC = Regex(
        """(?i)(?:recommend|consider|suggest)\s+(?:buying|purchasing|accumulating)\s+(?:(\d+)\s+(?:shares?\s+(?:of\s+)?)?)?([A-Z]{2,20})\b"""
    )
    private val AI_SELL_REC = Regex(
        """(?i)(?:recommend|consider|suggest)\s+(?:selling|exiting|booking profits? (?:on|in)?)\s+([A-Z]{2,20})\b"""
    )

    /** Extract trade intents from a message. Returns empty list if no intents found. */
    fun parse(message: String): List<TradeIntent> {
        val intents = mutableListOf<TradeIntent>()

        // Buy with quantity
        BUY_PATTERN.find(message)?.let { match ->
            val qty = match.groupValues[1].toIntOrNull()
            val symbol = match.groupValues[2].uppercase()
            intents.add(TradeIntent(Action.BUY, symbol, qty, displayText = "Buy ${qty ?: ""} $symbol"))
        }

        // Simple buy (no quantity)
        if (intents.none { it.action == Action.BUY }) {
            BUY_SIMPLE.find(message)?.let { match ->
                val symbol = match.groupValues[1].uppercase()
                intents.add(TradeIntent(Action.BUY, symbol, displayText = "Buy $symbol"))
            }
        }

        // Sell with quantity
        SELL_PATTERN.find(message)?.let { match ->
            val qty = match.groupValues[1].toIntOrNull()
            val symbol = match.groupValues[2].uppercase()
            intents.add(TradeIntent(Action.SELL, symbol, qty, displayText = "Sell ${qty ?: ""} $symbol"))
        }

        if (intents.none { it.action == Action.SELL }) {
            SELL_SIMPLE.find(message)?.let { match ->
                val symbol = match.groupValues[1].uppercase()
                intents.add(TradeIntent(Action.SELL, symbol, displayText = "Sell $symbol"))
            }
        }

        // Alert
        ALERT_PATTERN.find(message)?.let { match ->
            val symbol = match.groupValues[1].uppercase()
            val direction = match.groupValues[2].lowercase()
            val price = match.groupValues[3].toDoubleOrNull()
            val alertType = if (direction.contains("below")) "BELOW" else "ABOVE"
            intents.add(TradeIntent(Action.ALERT, symbol, price = price, alertType = alertType, displayText = "Alert: $symbol $alertType ₹${price ?: ""}"))
        }

        // AI recommendations
        AI_BUY_REC.find(message)?.let { match ->
            if (intents.none { it.action == Action.BUY }) {
                val qty = match.groupValues[1].toIntOrNull()
                val symbol = match.groupValues[2].uppercase()
                intents.add(TradeIntent(Action.BUY, symbol, qty, displayText = "Buy ${qty ?: ""} $symbol"))
            }
        }

        AI_SELL_REC.find(message)?.let { match ->
            if (intents.none { it.action == Action.SELL }) {
                val symbol = match.groupValues[1].uppercase()
                intents.add(TradeIntent(Action.SELL, symbol, displayText = "Sell $symbol"))
            }
        }

        return intents.distinctBy { "${it.action}:${it.symbol}" }
    }
}
