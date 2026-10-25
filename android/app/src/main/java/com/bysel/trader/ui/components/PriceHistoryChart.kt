package com.bysel.trader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bysel.trader.data.api.ChartPattern
import com.bysel.trader.data.models.HistoryCandle

/**
 * Single entry point for price history. The Canvas renderer is the only engine
 * shipped; the WebView (TradingView) and SciChart branches were dormant code
 * that silently dropped [patterns], so they are gone.
 */
@Composable
fun PriceHistoryChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    patterns: List<ChartPattern> = emptyList(),
    /** Chart window label used for X-axis cadence: 5D / 1M / 3M / 1Y */
    rangeLabel: String = "1M",
    expandPlot: Boolean = false,
) {
    CandlestickChart(
        history = history,
        modifier = modifier,
        rangeLabel = rangeLabel,
        expandPlot = expandPlot,
        patterns = patterns,
    )
}
