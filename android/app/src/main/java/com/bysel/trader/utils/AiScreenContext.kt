package com.bysel.trader.utils

/**
 * Where the user is looking when they send an AI ask.
 * Sent as /ai/ask screen_context so follow-ups like "this stock" resolve
 * without leaking a stale selected quote from Home or More.
 */
data class AiScreenContext(
    val source: String = "home",
    val symbol: String? = null,
    val scannerMode: String? = null,
) {
    val allowsStockInherit: Boolean
        get() = !symbol.isNullOrBlank() && source in STOCK_SOURCES

    fun toRequestMap(): Map<String, String> {
        val out = linkedMapOf("source" to source)
        symbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { out["symbol"] = it }
        scannerMode?.trim()?.takeIf { it.isNotBlank() }?.let { out["scanner_mode"] = it }
        return out
    }

    companion object {
        val STOCK_SOURCES = setOf("stock_detail", "trade", "ai")

        fun forTab(
            tab: Int,
            selectedSymbol: String?,
            previous: AiScreenContext,
            scannerMode: String? = null,
        ): AiScreenContext {
            val quote = selectedSymbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            return when (tab) {
                0 -> AiScreenContext(source = "home")
                1 -> {
                    val keep = previous.source in setOf("stock_detail", "trade") &&
                        !previous.symbol.isNullOrBlank()
                    AiScreenContext(
                        source = "ai",
                        symbol = if (keep) previous.symbol else null,
                    )
                }
                2 -> AiScreenContext(source = "trade", symbol = quote)
                3 -> AiScreenContext(source = "portfolio")
                4 -> AiScreenContext(source = "heatmap")
                5 -> AiScreenContext(source = "more")
                6 -> AiScreenContext(source = "search")
                9 -> AiScreenContext(source = "stock_detail", symbol = quote)
                20 -> AiScreenContext(source = "signal_lab")
                21 -> AiScreenContext(source = "smart_money")
                22 -> AiScreenContext(source = "risk_lab")
                28 -> AiScreenContext(source = "scanner", scannerMode = scannerMode)
                else -> AiScreenContext(source = "more")
            }
        }
    }
}
