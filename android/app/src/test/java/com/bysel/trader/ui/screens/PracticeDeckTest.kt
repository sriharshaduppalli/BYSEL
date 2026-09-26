package com.bysel.trader.ui.screens

import com.bysel.trader.data.models.PracticeIdea
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.StockRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeDeckTest {
    @Test
    fun mergesIdeasAndRecsWithoutDuplicateSymbols() {
        val ideas = listOf(
            PracticeIdea(symbol = "reliance", name = "Reliance", stance = "MOMENTUM_DRILL", suggestedQty = 2),
        )
        val recs = listOf(
            StockRecommendation(symbol = "RELIANCE", name = "Reliance Industries", sector = "Energy", price = 1400.0),
            StockRecommendation(symbol = "TCS", name = "TCS", sector = "IT", price = 3500.0),
        )
        val deck = buildTodaysPracticeDeck(ideas, recs, tapeQuotes = emptyList(), limit = 5)
        assertEquals(listOf("RELIANCE", "TCS"), deck.map { it.symbol })
        assertEquals(2, deck.first().qty)
        assertTrue(deck.first().why.contains("delivery", ignoreCase = true))
        assertFalse(deck.any { it.why.contains("target", ignoreCase = true) })
        assertFalse(deck.any { it.why.contains("BUY RELIANCE", ignoreCase = true) })
    }

    @Test
    fun tapeFallbackFillsWhenFeedsEmpty() {
        val tape = listOf(
            Quote(symbol = "INFY", last = 1500.0, pctChange = 1.4),
            Quote(symbol = "HDFCBANK", last = 1600.0, pctChange = -1.1),
        )
        val deck = buildTodaysPracticeDeck(emptyList(), emptyList(), tape, limit = 5)
        assertEquals(2, deck.size)
        assertTrue(deck.all { it.kind == "session" })
    }

    @Test
    fun ideaWhyNeverLooksLikeACall() {
        val why = whyForPracticeIdea(PracticeIdea(stance = "DIP_DRILL"))
        assertTrue(why.contains("Practice SELL") || why.contains("plan"))
        assertFalse(why.contains("target", ignoreCase = true))
        assertFalse(why.startsWith("BUY"))
    }
}
