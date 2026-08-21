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
    // Sort only — never drop saved names. Gainers/losers rise to the top; the rest stay visible.
    WatchlistSortMode.GAINERS -> sortedWith(
        compareByDescending<Quote> { it.pctChange }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.LOSERS -> sortedWith(
        compareBy<Quote> { it.pctChange }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.VOLUME -> sortedWith(
        compareByDescending<Quote> { it.effectiveVolume() }.thenBy { it.symbol.uppercase() },
    )
    WatchlistSortMode.ALPHA -> sortedBy { it.symbol.uppercase() }
}
