package com.bysel.trader.widget

import com.bysel.trader.data.models.HeatmapStock
import com.bysel.trader.data.models.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetMarketHelpersTest {

    @Test
    fun resolveNiftyLevel_prefersQuoteOverHeatmapFirstStock() {
        val quotes = listOf(Quote(symbol = "NIFTY50", last = 24_500.0, pctChange = 0.42))
        val heatmap = listOf(
            HeatmapStock(symbol = "RELIANCE", name = "Reliance", price = 2_900.0, pctChange = 1.2),
            HeatmapStock(symbol = "NIFTY50", name = "Nifty", price = 24_400.0, pctChange = 0.1),
        )
        val resolved = resolveNiftyLevel(quotes, heatmap)
        assertEquals(24_500.0, resolved!!.first, 0.01)
        assertEquals(0.42, resolved.second, 0.001)
    }

    @Test
    fun resolveNiftyLevel_fallsBackToHeatmapIndex() {
        val heatmap = listOf(
            HeatmapStock(symbol = "TCS", name = "TCS", price = 3_500.0, pctChange = -0.5),
            HeatmapStock(symbol = "NIFTY50", name = "Nifty", price = 24_100.0, pctChange = -0.2),
        )
        val resolved = resolveNiftyLevel(emptyList(), heatmap)
        assertEquals(24_100.0, resolved!!.first, 0.01)
        assertEquals(-0.2, resolved.second, 0.001)
    }

    @Test
    fun resolveNiftyLevel_doesNotUseRandomEquityAsNifty() {
        val heatmap = listOf(
            HeatmapStock(symbol = "RELIANCE", name = "Reliance", price = 2_900.0, pctChange = 1.2),
        )
        assertNull(resolveNiftyLevel(emptyList(), heatmap))
    }

    @Test
    fun equityStocksForMovers_excludesIndexSymbols() {
        val stocks = listOf(
            HeatmapStock(symbol = "NIFTY50", price = 24_000.0, pctChange = 0.1),
            HeatmapStock(symbol = "INFY", price = 1_500.0, pctChange = 2.0),
        )
        val movers = equityStocksForMovers(stocks)
        assertEquals(1, movers.size)
        assertEquals("INFY", movers.first().symbol)
    }
}
