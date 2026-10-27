package com.bysel.trader.data.fno

/**
 * Replay starts on five days of 15-minute bars. Extend steps to a month of
 * 15-minute bars, then three months of hourly bars. Reload always returns to five days.
 */
object FoCandleSpan {
    fun request(currentPeriod: String, extend: Boolean): Pair<String, String> {
        val period = if (!extend) {
            "5d"
        } else {
            when (currentPeriod) {
                "5d" -> "1mo"
                "1mo" -> "3mo"
                else -> "3mo"
            }
        }
        val interval = if (period == "3mo") "1h" else "15m"
        return period to interval
    }
}
