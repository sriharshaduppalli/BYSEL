package com.bysel.trader.data

import com.bysel.trader.data.models.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistSymbolsTest {

    @Test
    fun userKeySplitsAnonAndLoggedIn() {
        assertEquals("anon", WatchlistSymbols.userKey(null))
        assertEquals("anon", WatchlistSymbols.userKey(0))
        assertEquals("u_42", WatchlistSymbols.userKey(42))
    }

    @Test
    fun normalizeStripsNseSuffixAndPrefixes() {
        assertEquals("RELIANCE", WatchlistSymbols.normalize("reliance.ns"))
        assertEquals("RELIANCE", WatchlistSymbols.normalize("NSE:RELIANCE"))
        assertEquals("500325.BO", WatchlistSymbols.normalize("BSE:500325"))
        assertEquals("500325.BO", WatchlistSymbols.normalize("500325"))
    }

    @Test
    fun coalesceKeepsLastGoodWhenIncomingEmpty() {
        val kept = WatchlistSymbols.coalesce(
            incoming = emptyList(),
            lastGood = listOf("RELIANCE", "TCS"),
            allowEmpty = false,
        )
        assertEquals(listOf("RELIANCE", "TCS"), kept)
    }

    @Test
    fun coalesceAllowsExplicitUserClear() {
        val cleared = WatchlistSymbols.coalesce(
            incoming = emptyList(),
            lastGood = listOf("RELIANCE"),
            allowEmpty = true,
        )
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun coalesceDoesNotInventSymbols() {
        val incoming = WatchlistSymbols.coalesce(
            incoming = listOf("INFY"),
            lastGood = listOf("RELIANCE", "TCS"),
            allowEmpty = false,
        )
        assertEquals(listOf("INFY"), incoming)
    }

    @Test
    fun unionKeepsPrimaryOrderAndRecoversMissingNames() {
        val merged = WatchlistSymbols.unionPreserveOrder(
            primary = listOf("RELIANCE", "TCS"),
            extra = listOf("INFY", "tcs.ns", "WIPRO"),
        )
        assertEquals(listOf("RELIANCE", "TCS", "INFY", "WIPRO"), merged)
    }

    @Test
    fun unionDoesNotLetAShorterSnapshotWin() {
        val merged = WatchlistSymbols.unionPreserveOrder(
            primary = listOf("RELIANCE", "TCS", "INFY"),
            extra = listOf("INFY"),
        )
        assertEquals(listOf("RELIANCE", "TCS", "INFY"), merged)
    }

    @Test
    fun findQuoteMatchesNsAlias() {
        val quotes = listOf(Quote(symbol = "RELIANCE.NS", last = 1400.0, pctChange = 1.2))
        val found = WatchlistSymbols.findQuote(quotes, "RELIANCE")
        assertEquals("RELIANCE.NS", found?.symbol)
        assertTrue(WatchlistSymbols.matches("RELIANCE", "RELIANCE.NS"))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val symbols = listOf("TCS", "reliance.ns", "INFY")
        val decoded = WatchlistSymbols.decode(WatchlistSymbols.encode(symbols))
        assertEquals(listOf("TCS", "RELIANCE", "INFY"), decoded)
    }
}
