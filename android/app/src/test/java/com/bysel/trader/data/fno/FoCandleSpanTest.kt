package com.bysel.trader.data.fno

import org.junit.Assert.assertEquals
import org.junit.Test

class FoCandleSpanTest {
    @Test
    fun reloadAlwaysStartsAtFiveDays() {
        assertEquals("5d" to "15m", FoCandleSpan.request("1mo", extend = false))
        assertEquals("5d" to "15m", FoCandleSpan.request("3mo", extend = false))
    }

    @Test
    fun liveSessionUsesOneDayOfOneMinuteBars() {
        assertEquals("1d" to "1m", FoCandleSpan.live())
    }

    @Test
    fun widenStepsTheSessionSidewaysThenStops() {
        assertEquals("5d" to "5m", FoCandleSpan.widen("1d" to "1m"))
        assertEquals("5d" to "5m", FoCandleSpan.widen("1d" to "5m"))
        assertEquals("1mo" to "15m", FoCandleSpan.widen("5d" to "5m"))
        assertEquals("3mo" to "1h", FoCandleSpan.widen("1mo" to "15m"))
        assertEquals("3mo" to "1h", FoCandleSpan.widen("3mo" to "1h"))
    }

    @Test
    fun extendStepsToAMonthThenThreeMonths() {
        assertEquals("1mo" to "15m", FoCandleSpan.request("5d", extend = true))
        assertEquals("3mo" to "1h", FoCandleSpan.request("1mo", extend = true))
        assertEquals("3mo" to "1h", FoCandleSpan.request("3mo", extend = true))
    }
}
