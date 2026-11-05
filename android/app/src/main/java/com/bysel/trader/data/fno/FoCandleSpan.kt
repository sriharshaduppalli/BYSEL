package com.bysel.trader.data.fno

/**
 * Replay starts on five days of 15-minute bars. Extend steps to a month of
 * 15-minute bars, then three months of hourly bars. Reload always returns to five days.
 * The open session starts on one day of 1-minute bars. If that series is empty, the
 * loader steps to 5-minute. Pinching past the left edge widens the window.
 */
object FoCandleSpan {
    fun live(): Pair<String, String> = "1d" to "1m"

    /** Pinching past the left edge steps the open-session window sideways. */
    fun widen(current: Pair<String, String>): Pair<String, String> = when (current) {
        "1d" to "1m" -> "5d" to "5m"
        "1d" to "5m" -> "5d" to "5m"
        "5d" to "5m" -> "1mo" to "15m"
        "1mo" to "15m" -> "3mo" to "1h"
        else -> current
    }

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
