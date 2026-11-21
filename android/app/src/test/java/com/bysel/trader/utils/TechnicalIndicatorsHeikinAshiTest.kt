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

    @Test
    fun vwap_dailyBarsUseRollingWindowInsteadOfResettingEveryDay() {
        val dayMs = 24L * 3600L * 1000L
        val baseTs = 1_700_000_000_000L
        val dailyBars = listOf(
            HistoryCandle(baseTs, open = 100.0, high = 100.0, low = 100.0, close = 100.0, volume = 100L),
            HistoryCandle(baseTs + dayMs, open = 200.0, high = 200.0, low = 200.0, close = 200.0, volume = 300L),
        )
        val vwap = TechnicalIndicators.vwap(dailyBars, rollingWindow = 20)
        assertEquals(100.0, vwap[0]!!, 1e-6)
        // Rolling VWAP over day 1 (100 * 100) + day 2 (200 * 300) / 400 = 175.0 (not 200.0)
        assertEquals(175.0, vwap[1]!!, 1e-6)
    }

    @Test
    fun vwap_intradayBarsAccumulateWithinSessionAndResetOnNewIstDay() {
        val fiveMinMs = 5L * 60L * 1000L
        val dayMs = 24L * 3600L * 1000L
        // 2023-11-15 10:00 IST in epoch ms
        val session1Start = 1_700_022_600_000L
        val intradayBars = listOf(
            HistoryCandle(session1Start, open = 100.0, high = 100.0, low = 100.0, close = 100.0, volume = 100L),
            HistoryCandle(session1Start + fiveMinMs, open = 110.0, high = 110.0, low = 110.0, close = 110.0, volume = 100L),
            HistoryCandle(session1Start + dayMs, open = 150.0, high = 150.0, low = 150.0, close = 150.0, volume = 100L),
        )
        val vwap = TechnicalIndicators.vwap(intradayBars)
        assertEquals(100.0, vwap[0]!!, 1e-6)
        assertEquals(105.0, vwap[1]!!, 1e-6)
        // Next IST day resets cumulative volume
        assertEquals(150.0, vwap[2]!!, 1e-6)
    }

    @Test
    fun supertrend_detectsUptrendAndFlipsOnBreakdown() {
        val candles = mutableListOf<HistoryCandle>()
        val baseTs = 1_700_000_000_000L
        for (i in 0 until 16) {
            val c = 100.0 + i * 3.0
            candles.add(HistoryCandle(baseTs + i * 60_000L, open = c - 1.0, high = c + 2.0, low = c - 2.0, close = c, volume = 1000L))
        }
        // Sharp breakdown bar
        candles.add(HistoryCandle(baseTs + 16 * 60_000L, open = 140.0, high = 141.0, low = 110.0, close = 112.0, volume = 5000L))

        val st = TechnicalIndicators.supertrend(candles, period = 10, multiplier = 3.0)
        assertEquals(candles.size, st.size)
        assertTrue(st[14]!!.bullish)
        assertTrue(!st.last()!!.bullish)
    }
}
