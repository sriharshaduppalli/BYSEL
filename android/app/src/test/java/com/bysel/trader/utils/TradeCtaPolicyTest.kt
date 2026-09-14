package com.bysel.trader.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeCtaPolicyTest {

    @Test
    fun literacyAskDoesNotNameAStock() {
        assertNull(TradeCtaPolicy.namedSymbol("What is RSI?"))
        assertTrue(TradeCtaPolicy.isGeneralTopic("What is RSI?"))
        assertTrue(TradeCtaPolicy.isGeneralTopic("How does SIP work?"))
        assertFalse(TradeCtaPolicy.allowsAttachedSymbol("What is RSI?"))
        assertFalse(TradeCtaPolicy.allowsPracticeTrade("What is RSI?", "RSI is momentum", "TCS"))
    }

    @Test
    fun namedStockTradeAskKeepsPracticeBuy() {
        assertEquals("RELIANCE", TradeCtaPolicy.namedSymbol("Should I buy RELIANCE?"))
        assertTrue(TradeCtaPolicy.allowsAttachedSymbol("Should I buy RELIANCE?"))
        assertTrue(
            TradeCtaPolicy.allowsPracticeTrade(
                "Should I buy RELIANCE?",
                "**Direct answer:** HOLD",
                "RELIANCE",
            )
        )
    }

    @Test
    fun followUpWithoutNewTickerCanKeepSymbol() {
        assertTrue(TradeCtaPolicy.isFollowUp("what about sentiment?"))
        assertTrue(TradeCtaPolicy.allowsAttachedSymbol("what about sentiment?"))
        assertFalse(TradeCtaPolicy.allowsPracticeTrade("what about sentiment?", "Mood is mixed", "TCS"))
    }

    @Test
    fun filtersBuyChipsOnGeneralAsks() {
        val tips = TradeCtaPolicy.filterSuggestions(
            "What is RSI?",
            listOf("Should I buy TCS?", "What is MACD?"),
        )
        assertEquals(listOf("What is MACD?"), tips)
    }
}
