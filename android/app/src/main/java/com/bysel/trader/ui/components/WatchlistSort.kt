package com.bysel.trader.ui.components

import com.bysel.trader.data.models.Quote

/** Display-only sort modes for watchlist boards (does not change persisted order). */
enum class WatchlistSortMode(val label: String) {
    MOVE("Top move"),
    GAINERS("Gainers"),
    LOSERS("Losers"),
    VOLUME("Volume"),
    ALPHA("A–Z"),
}

fun List<Quote>.sortedByWatchlistMode(mode: WatchlistSortMode): List<Quote> = when (mode) {
    WatchlistSortMode.MOVE -> sortedWith(
        compareByDescending<Quote> { kotlin.math.abs(it.pctChange) }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.GAINERS -> filter { it.pctChange > 0.0 }.sortedWith(
        compareByDescending<Quote> { it.pctChange }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.LOSERS -> filter { it.pctChange < 0.0 }.sortedWith(
        compareBy<Quote> { it.pctChange }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.VOLUME -> sortedWith(
        compareByDescending<Quote> { it.effectiveVolume() }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.ALPHA -> sortedBy { it.symbol.uppercase() }
}
