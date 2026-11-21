package com.bysel.trader.data.fno

import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.ui.components.sessionChartLabel
import com.bysel.trader.ui.screens.inferCandleIntervalLabel
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

    @Test
    fun inferCandleIntervalLabelMatchesOneMinuteAndFiveMinuteSeries() {
        val oneMinuteBars = listOf(
            HistoryCandle(timestamp = 1_760_000_000_000L, open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 0),
            HistoryCandle(timestamp = 1_760_000_060_000L, open = 100.5, high = 101.5, low = 100.0, close = 101.0, volume = 0),
            HistoryCandle(timestamp = 1_760_000_120_000L, open = 101.0, high = 102.0, low = 100.8, close = 101.8, volume = 0),
        )
        assertEquals("1-minute", inferCandleIntervalLabel(oneMinuteBars))
        assertEquals("Session · 1-minute candles", sessionChartLabel(marketOpen = true, intervalLabel = inferCandleIntervalLabel(oneMinuteBars)).title)

        val fiveMinuteBars = listOf(
            HistoryCandle(timestamp = 1_760_000_000_000L, open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 0),
            HistoryCandle(timestamp = 1_760_000_300_000L, open = 100.5, high = 101.5, low = 100.0, close = 101.0, volume = 0),
        )
        assertEquals("5-minute", inferCandleIntervalLabel(fiveMinuteBars))
        assertEquals("Last session · 5-minute candles", sessionChartLabel(marketOpen = false, intervalLabel = inferCandleIntervalLabel(fiveMinuteBars)).title)
    }
}
