package com.bysel.trader.utils

import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.Quote
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `buildPrompt includes history summary`() {
        val history = listOf(
            HistoryCandle(timestamp = 1, open = 100.0, high = 110.0, low = 95.0, close = 105.0, volume = 1000),
            HistoryCandle(timestamp = 2, open = 105.0, high = 115.0, low = 100.0, close = 110.0, volume = 1200),
            HistoryCandle(timestamp = 3, open = 110.0, high = 120.0, low = 105.0, close = 115.0, volume = 1500)
        )

        val quote = Quote(symbol = "TEST", last = 115.0, pctChange = 1.5)
        val prompt = PromptBuilder.buildPrompt(
            userQuery = "what now",
            holdingsSummary = "",
            wallet = 5000.0,
            portfolioScore = 42,
            selectedQuote = quote,
            recentHistory = history
        )

        assertTrue(prompt.contains("history_count=3"))
        assertTrue(prompt.contains("history_avg="))
        assertTrue(prompt.contains("history_closes=[105.00,110.00,115.00]"))
        assertTrue(prompt.contains("symbol=TEST"))
        assertTrue(prompt.contains("price=115.0"))
    }

    @Test
    fun `buildPrompt no history still returns context`() {
        val prompt = PromptBuilder.buildPrompt(
            userQuery = "hello",
            holdingsSummary = "ABC:1@100",
            wallet = 100.0,
            portfolioScore = null,
            selectedQuote = null,
            recentHistory = emptyList()
        )

        assertTrue(prompt.contains("holdings=ABC:1@100"))
        assertTrue(prompt.contains("wallet=100.0"))
        assertFalse(prompt.contains("history_count="))
    }
}
