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
    fun extendStepsToAMonthThenThreeMonths() {
        assertEquals("1mo" to "15m", FoCandleSpan.request("5d", extend = true))
        assertEquals("3mo" to "1h", FoCandleSpan.request("1mo", extend = true))
        assertEquals("3mo" to "1h", FoCandleSpan.request("3mo", extend = true))
    }
}
