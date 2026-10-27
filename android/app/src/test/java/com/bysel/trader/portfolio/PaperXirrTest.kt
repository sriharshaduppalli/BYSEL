package com.bysel.trader.portfolio

import com.bysel.trader.data.api.TradeHistory
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperXirrTest {
    @Test
    fun oneYearDoubleIsAboutOneHundredPercent() {
        val start = 1_700_000_000_000L
        val year = start + 365L * 86_400_000L
        val trades = listOf(TradeHistory(1, "TCS", "BUY", 1, 10_000.0, 10_000.0, start, 0.0))
        val rate = PaperXirr.annualizedPercent(trades, 20_000.0, year)
        assertTrue(rate!! in 99.0..101.0)
    }

    @Test
    fun sameDayBookHasNoAnnualizedRate() {
        val start = 1_700_000_000_000L
        val trades = listOf(TradeHistory(1, "TCS", "BUY", 1, 10_000.0, 10_000.0, start, 0.0))
        assertNull(PaperXirr.annualizedPercent(trades, 10_100.0, start + 3_600_000L))
    }

    @Test
    fun chargesReduceTheOutflow() {
        val start = 1_700_000_000_000L
        val year = start + 365L * 86_400_000L
        val trades = listOf(TradeHistory(1, "TCS", "BUY", 1, 10_000.0, 10_000.0, start, 100.0))
        val withCharges = PaperXirr.annualizedPercent(trades, 10_100.0, year)
        val without = PaperXirr.annualizedPercent(
            listOf(TradeHistory(1, "TCS", "BUY", 1, 10_000.0, 10_000.0, start, 0.0)),
            10_100.0,
            year,
        )
        assertTrue(withCharges!! < without!!)
    }
}
