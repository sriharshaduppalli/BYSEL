package com.bysel.trader.ui.components

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote

/** Display-only sort modes for portfolio holdings (does not change persisted order). */
enum class PortfolioSortMode(val label: String) {
    NAME("Name A–Z"),
    VALUE("Value"),
    PNL("P&L ₹"),
    PNL_PCT("P&L %"),
    DAY("Day %"),
    QTY("Qty"),
}

fun List<Holding>.sortedByPortfolioMode(
    mode: PortfolioSortMode,
    quoteBySymbol: Map<String, Quote> = emptyMap(),
): List<Holding> {
    fun lastPrice(holding: Holding): Double {
        val quote = quoteBySymbol[holding.symbol.uppercase()]
        return if (quote != null && quote.last > 0.0) quote.last else holding.last
    }
    fun marketValue(holding: Holding): Double = lastPrice(holding) * holding.qty
    fun pnlRupee(holding: Holding): Double = (lastPrice(holding) - holding.avgPrice) * holding.qty
    fun pnlPercent(holding: Holding): Double {
        val invested = holding.avgPrice * holding.qty
        return if (invested > 0.0) pnlRupee(holding) / invested * 100.0 else 0.0
    }
    fun dayPercent(holding: Holding): Double =
        quoteBySymbol[holding.symbol.uppercase()]?.pctChange ?: 0.0

    return when (mode) {
        PortfolioSortMode.NAME -> sortedBy { it.symbol.uppercase() }
        PortfolioSortMode.VALUE -> sortedWith(
            compareByDescending<Holding> { marketValue(it) }.thenBy { it.symbol.uppercase() },
        )
        PortfolioSortMode.PNL -> sortedWith(
            compareByDescending<Holding> { pnlRupee(it) }.thenBy { it.symbol.uppercase() },
        )
        PortfolioSortMode.PNL_PCT -> sortedWith(
            compareByDescending<Holding> { pnlPercent(it) }.thenBy { it.symbol.uppercase() },
        )
        PortfolioSortMode.DAY -> sortedWith(
            compareByDescending<Holding> { dayPercent(it) }.thenBy { it.symbol.uppercase() },
        )
        PortfolioSortMode.QTY -> sortedWith(
            compareByDescending<Holding> { it.qty }.thenBy { it.symbol.uppercase() },
        )
    }
}
