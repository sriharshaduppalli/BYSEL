package com.bysel.trader.ui.components

import com.bysel.trader.data.models.Quote
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistSortTest {

    private fun quote(
        symbol: String,
        pctChange: Double,
        volume: Long? = null,
        avgVolume: Long? = null,
    ) = Quote(
        symbol = symbol,
        last = 100.0,
        pctChange = pctChange,
        volume = volume,
        avgVolume = avgVolume,
    )

    private val sample = listOf(
        quote("INFY", 1.8, volume = 10_000),
        quote("TCS", -2.4, volume = 50_000),
        quote("RELIANCE", 0.4, volume = 0, avgVolume = 80_000),
        quote("WIPRO", -0.3, volume = 5_000),
        quote("HDFCBANK", 0.0, volume = 20_000),
    )

    @Test
    fun topMoveRanksByAbsolutePercent() {
        val ordered = sample.sortedByWatchlistMode(WatchlistSortMode.MOVE).map { it.symbol }
        assertEquals(listOf("TCS", "INFY", "RELIANCE", "WIPRO", "HDFCBANK"), ordered)
    }

    @Test
    fun gainersRankUpNamesFirstButKeepTheFullList() {
        val ordered = sample.sortedByWatchlistMode(WatchlistSortMode.GAINERS).map { it.symbol }
        assertEquals(listOf("INFY", "RELIANCE", "HDFCBANK", "WIPRO", "TCS"), ordered)
    }

    @Test
    fun losersRankDownNamesFirstButKeepTheFullList() {
        val ordered = sample.sortedByWatchlistMode(WatchlistSortMode.LOSERS).map { it.symbol }
        assertEquals(listOf("TCS", "WIPRO", "HDFCBANK", "RELIANCE", "INFY"), ordered)
    }

    @Test
    fun volumeUsesSessionVolumeThenAverage() {
        val ordered = sample.sortedByWatchlistMode(WatchlistSortMode.VOLUME).map { it.symbol }
        assertEquals(listOf("RELIANCE", "TCS", "HDFCBANK", "INFY", "WIPRO"), ordered)
    }

    @Test
    fun alphaSortsBySymbol() {
        val ordered = sample.sortedByWatchlistMode(WatchlistSortMode.ALPHA).map { it.symbol }
        assertEquals(listOf("HDFCBANK", "INFY", "RELIANCE", "TCS", "WIPRO"), ordered)
    }
}
