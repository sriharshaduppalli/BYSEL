package com.bysel.trader.utils

import com.bysel.trader.data.models.HistoryCandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorsHeikinAshiTest {

    private val bars = listOf(
        HistoryCandle(1, open = 100.0, high = 110.0, low = 95.0, close = 105.0, volume = 10),
        HistoryCandle(2, open = 105.0, high = 112.0, low = 103.0, close = 110.0, volume = 10),
        HistoryCandle(3, open = 110.0, high = 111.0, low = 100.0, close = 101.0, volume = 10),
    )

    @Test
    fun firstBar_usesOwnOpenCloseMidpoint() {
        val ha = TechnicalIndicators.heikinAshi(bars)
        assertEquals(102.5, ha[0].open, 1e-9)
        assertEquals((100.0 + 110.0 + 95.0 + 105.0) / 4.0, ha[0].close, 1e-9)
    }

    @Test
    fun laterBars_openFromPreviousHaBody_andRangeCoversBody() {
        val ha = TechnicalIndicators.heikinAshi(bars)
        val expectedOpen = (ha[0].open + ha[0].close) / 2.0
        assertEquals(expectedOpen, ha[1].open, 1e-9)
        ha.forEach { bar ->
            assertTrue(bar.high >= maxOf(bar.open, bar.close))
            assertTrue(bar.low <= minOf(bar.open, bar.close))
        }
    }

    @Test
    fun keepsTimestampsAndLength() {
        val ha = TechnicalIndicators.heikinAshi(bars)
        assertEquals(bars.size, ha.size)
        assertEquals(bars.map { it.timestamp }, ha.map { it.timestamp })
        assertTrue(TechnicalIndicators.heikinAshi(emptyList()).isEmpty())
    }
}
