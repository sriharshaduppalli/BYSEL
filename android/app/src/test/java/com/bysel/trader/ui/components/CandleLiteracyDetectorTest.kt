package com.bysel.trader.ui.components

import com.bysel.trader.data.models.HistoryCandle
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleLiteracyDetectorTest {

    @Test
    fun detectsBullishEngulfingOnLastBar() {
        val bars = listOf(
            candle(100, 105, 99, 104),
            candle(104, 105, 98, 99),
            candle(98, 110, 97, 109),
        )
        val lessons = CandleLiteracyDetector.detectRecent(bars)
        assertTrue(lessons.any { it.name.contains("engulfing", ignoreCase = true) })
    }

    @Test
    fun detectsDoji() {
        val bars = listOf(
            candle(100, 108, 99, 107),
            candle(107, 110, 104, 109),
            candle(109, 112, 106, 109.1), // tiny body
        )
        val lessons = CandleLiteracyDetector.detectRecent(bars)
        assertTrue(lessons.any { it.name.equals("Doji", ignoreCase = true) })
    }

    private fun candle(o: Number, h: Number, l: Number, c: Number) = HistoryCandle(
        timestamp = System.currentTimeMillis(),
        open = o.toDouble(),
        high = h.toDouble(),
        low = l.toDouble(),
        close = c.toDouble(),
        volume = 1_000_000,
    )
}
