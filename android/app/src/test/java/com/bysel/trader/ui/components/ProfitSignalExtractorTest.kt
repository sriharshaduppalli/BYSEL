package com.bysel.trader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitSignalExtractorTest {

    private val sampleAnswer = """
        **KAYNES** — Kaynes Technology India Limited — paper-practice view

        **Direct answer:** BUY bias (paper) — wait for entry zone
        **Action:** BUY (score 4, confidence 0.72, swing)
        • Entry zone: 3791.8 – 3856.3
        • Stop: 3669.2 | Target 1: 4136.9 | Target 2: 4324.0
        • Risk/reward: 1.50 | suggested qty (risk budget): 26

        **Sentiment analysis:**
        • Overall: **Mildly Bullish** (score +0.28, confidence 0.71)

        **Key levels & tape:**
        • Price: 3856.3
    """.trimIndent()

    @Test
    fun `parses entry zone midpoint not rupee 1`() {
        val signal = ProfitSignalExtractor.extract(sampleAnswer, "KAYNES")
        assertNotNull(signal)
        val entry = requireNotNull(signal!!.entry)
        assertTrue("entry should be mid of zone, was $entry", entry in 3791.0..3857.0)
        assertTrue("target should be Target 1 price, was ${signal.target}", (signal.target ?: 0.0) >= 4130.0)
        assertTrue("stop should be real stop, was ${signal.stopLoss}", (signal.stopLoss ?: 0.0) >= 3660.0)
    }

    @Test
    fun `parses decimal confidence 0_72 as 72 percent`() {
        assertEquals(72, ProfitSignalExtractor.parseConfidencePercent("0.72"))
        assertEquals(71, ProfitSignalExtractor.parseConfidencePercent("0.71"))
        assertEquals(85, ProfitSignalExtractor.parseConfidencePercent("85"))
        val signal = ProfitSignalExtractor.extract(sampleAnswer, "KAYNES")
        assertEquals(72, signal?.confidence)
    }

    @Test
    fun `does not treat Target 1 index as price`() {
        val text = "Stop: 100.0 | Target 1: 150.5 | Target 2: 180.0"
        val signal = ProfitSignalExtractor.extract(text, "TCS")
        assertNotNull(signal)
        assertEquals(150.5, signal!!.target!!, 0.01)
    }

    @Test
    fun `header ticker wins over sentiment Overall or Mildly prose`() {
        val signal = ProfitSignalExtractor.extract(sampleAnswer, contextSymbol = null)
        assertNotNull(signal)
        assertEquals("KAYNES", signal!!.symbol)
    }
}
