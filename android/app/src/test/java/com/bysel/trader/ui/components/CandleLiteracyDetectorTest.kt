package com.bysel.trader.ui.components

import com.bysel.trader.data.models.HistoryCandle
import org.junit.Assert.assertFalse
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

    @Test
    fun detectsHammerAfterDip() {
        val bars = listOf(
            candle(100, 101, 99, 100),
            candle(100, 100.5, 96, 97),
            candle(97, 97.2, 94, 95.5),
            candle(95.0, 96.2, 91.0, 96.0), // body > doji, long lower wick after dip
        )
        val lessons = CandleLiteracyDetector.detectRecent(bars)
        assertTrue(lessons.any { it.name.equals("Hammer", ignoreCase = true) })
    }

    @Test
    fun neverLabelsInsideBarAsHarami() {
        val bars = listOf(
            candle(100, 112, 98, 111),
            candle(108, 110, 106, 107), // small body inside prior large body
        )
        val lessons = CandleLiteracyDetector.detectRecent(bars)
        assertFalse(lessons.any { it.name.contains("harami", ignoreCase = true) })
        val cards = StockLiteracyCatalog.cardsFor(bars)
        val links = cards.joinToString("\n") { "Learn: ${it.title}\n${it.learnQuery}" }
        assertFalse(links.contains("harami", ignoreCase = true))
        assertFalse(links.contains("Screener.in", ignoreCase = true))
        assertFalse(links.contains("TradingView", ignoreCase = true))
        assertTrue(cards.size in 4..8)
        assertTrue(cards.any { it.title.contains("Engulfing", ignoreCase = true) })
        assertTrue(cards.any { it.title.contains("P/E", ignoreCase = true) })
        assertTrue(cards.any { it.title.contains("ROE", ignoreCase = true) })
        assertTrue(cards.any { it.learnQuery.contains("PEG", ignoreCase = true) })
        assertTrue(cards.any { it.learnQuery.contains("coverage", ignoreCase = true) })
        assertTrue(cards.any { it.title.contains("Long-term", ignoreCase = true) })
        assertTrue(cards.any { it.title.contains("Swing", ignoreCase = true) })
        assertTrue(cards.any { it.title.contains("F&O", ignoreCase = true) })
        assertTrue(cards.any { it.title.equals("Best practice", ignoreCase = true) })
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
