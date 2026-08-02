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
    WatchlistSortMode.MOVE -> sortedByDescending { kotlin.math.abs(it.pctChange) }
    WatchlistSortMode.GAINERS -> sortedByDescending { it.pctChange }
    WatchlistSortMode.LOSERS -> sortedBy { it.pctChange }
    WatchlistSortMode.VOLUME -> sortedByDescending { it.volume ?: 0L }
    WatchlistSortMode.ALPHA -> sortedBy { it.symbol.uppercase() }
}
