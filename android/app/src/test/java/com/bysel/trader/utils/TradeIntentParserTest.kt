package com.bysel.trader.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeIntentParserTest {

    @Test
    fun datedEarningsCopyDoesNotBecomeSellButton() {
        val samples = listOf(
            "Paper sell plan. Watch the 2026 earnings print before adding.",
            "**RELIANCE** - paper sell plan",
            "Exit on strength ahead of 2026 earnings.",
            "Book profits before 2026 earnings if the tape is extended.",
            "**INFY - corporate actions** Dated line 2026-05-15. Not a sell 2026 earnings call.",
        )
        samples.forEach { text ->
            val sells = TradeIntentParser.parse(text).filter {
                it.action == TradeIntentParser.Action.SELL
            }
            assertTrue("Unexpected sell from: $text → $sells", sells.isEmpty())
        }
    }

    @Test
    fun realShareSellStillParses() {
        val intents = TradeIntentParser.parse("Sell 10 shares of TCS from the paper book.")
        val sell = intents.single { it.action == TradeIntentParser.Action.SELL }
        assertEquals("TCS", sell.symbol)
        assertEquals(10, sell.quantity)
        assertEquals("Sell 10 TCS", sell.displayText)
    }

    @Test
    fun marketCapScaleDoesNotBecomeBuyButton() {
        val samples = listOf(
            "Accumulate on dips. Market cap is about 50 million USD equivalent.",
            "Go long only if structure holds. Volume was 50 million.",
            "Buy the idea, not the headline — 50 million shares already changed hands.",
            "Last ₹1287 · Market cap ₹17.4 lakh crore",
        )
        samples.forEach { text ->
            val buys = TradeIntentParser.parse(text).filter {
                it.action == TradeIntentParser.Action.BUY
            }
            assertTrue("Unexpected buy from: $text → $buys", buys.isEmpty())
        }
    }

    @Test
    fun buySharesStillParses() {
        val intents = TradeIntentParser.parse("Buy 50 shares of RELIANCE in the paper book.")
        val buy = intents.single { it.action == TradeIntentParser.Action.BUY }
        assertEquals("RELIANCE", buy.symbol)
        assertEquals(50, buy.quantity)
        assertEquals("Buy 50 RELIANCE", buy.displayText)
    }

    @Test
    fun sellSymbolWithoutYearStillParses() {
        val intents = TradeIntentParser.parse("Sell INFY if the swing fails.")
        val sell = intents.single { it.action == TradeIntentParser.Action.SELL }
        assertEquals("INFY", sell.symbol)
        assertEquals("Sell INFY", sell.displayText)
    }
}
