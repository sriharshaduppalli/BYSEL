package com.bysel.trader.widget

import com.bysel.trader.data.models.HeatmapStock
import com.bysel.trader.data.models.Quote

internal val WIDGET_INDEX_SYMBOLS = setOf("NIFTY50", "NIFTY", "^NSEI")

internal fun resolveNiftyLevel(
    quotes: List<Quote>,
    heatmapStocks: List<HeatmapStock>,
): Pair<Double, Double>? {
    val fromQuote = quotes.firstOrNull {
        it.symbol.uppercase() in WIDGET_INDEX_SYMBOLS && it.last > 0
    }
    if (fromQuote != null) return fromQuote.last to fromQuote.pctChange

    val fromHeatmap = heatmapStocks.firstOrNull {
        it.symbol.uppercase() in WIDGET_INDEX_SYMBOLS && it.price > 0
    }
    return fromHeatmap?.let { it.price to it.pctChange }
}

internal fun equityStocksForMovers(heatmapStocks: List<HeatmapStock>): List<HeatmapStock> =
    heatmapStocks.filterNot { it.symbol.uppercase() in WIDGET_INDEX_SYMBOLS }
