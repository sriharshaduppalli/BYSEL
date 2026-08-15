package com.bysel.trader.ui.components

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioSortTest {

    private fun holding(
        symbol: String,
        qty: Int,
        avgPrice: Double,
        last: Double,
    ) = Holding(
        symbol = symbol,
        qty = qty,
        avgPrice = avgPrice,
        last = last,
        pnl = (last - avgPrice) * qty,
    )

    private fun quote(symbol: String, last: Double, pctChange: Double) = Quote(
        symbol = symbol,
        last = last,
        pctChange = pctChange,
    )

    private val holdings = listOf(
        holding("INFY", qty = 10, avgPrice = 100.0, last = 110.0), // value 1100, pnl +100, +10%
        holding("TCS", qty = 5, avgPrice = 200.0, last = 180.0), // value 900, pnl -100, -10%
        holding("WIPRO", qty = 20, avgPrice = 50.0, last = 55.0), // value 1100, pnl +100, +10%
        holding("RELIANCE", qty = 2, avgPrice = 1000.0, last = 1300.0), // value 2600, pnl +600, +30%
    )

    private val quotes = mapOf(
        "INFY" to quote("INFY", last = 120.0, pctChange = 1.5),
        "TCS" to quote("TCS", last = 170.0, pctChange = -3.2),
        "WIPRO" to quote("WIPRO", last = 55.0, pctChange = 0.4),
        "RELIANCE" to quote("RELIANCE", last = 1400.0, pctChange = 2.8),
    )

    @Test
    fun nameSortsAlphabetically() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.NAME).map { it.symbol }
        assertEquals(listOf("INFY", "RELIANCE", "TCS", "WIPRO"), ordered)
    }

    @Test
    fun valueUsesLiveQuoteWhenPresent() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.VALUE, quotes).map { it.symbol }
        // RELIANCE 2800, INFY 1200, WIPRO 1100, TCS 850
        assertEquals(listOf("RELIANCE", "INFY", "WIPRO", "TCS"), ordered)
    }

    @Test
    fun pnlRupeeRanksHighestGainFirst() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.PNL, quotes).map { it.symbol }
        // RELIANCE +800, INFY +200, WIPRO +100, TCS -150
        assertEquals(listOf("RELIANCE", "INFY", "WIPRO", "TCS"), ordered)
    }

    @Test
    fun pnlPercentRanksHighestPercentFirst() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.PNL_PCT, quotes).map { it.symbol }
        // RELIANCE +40%, INFY +20%, WIPRO +10%, TCS -15%
        assertEquals(listOf("RELIANCE", "INFY", "WIPRO", "TCS"), ordered)
    }

    @Test
    fun dayChangeRanksBySessionPercent() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.DAY, quotes).map { it.symbol }
        assertEquals(listOf("RELIANCE", "INFY", "WIPRO", "TCS"), ordered)
    }

    @Test
    fun quantityRanksLargestPositionFirst() {
        val ordered = holdings.sortedByPortfolioMode(PortfolioSortMode.QTY).map { it.symbol }
        assertEquals(listOf("WIPRO", "INFY", "TCS", "RELIANCE"), ordered)
    }
}
