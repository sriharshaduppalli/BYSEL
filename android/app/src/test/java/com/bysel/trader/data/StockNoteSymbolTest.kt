package com.bysel.trader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StockNoteSymbolTest {

    @Test
    fun nseEquitiesBecomeDotNs() {
        assertEquals("RELIANCE.NS", normalizeStockNoteSymbol("reliance"))
        assertEquals("RELIANCE.NS", normalizeStockNoteSymbol("RELIANCE.NS"))
        assertEquals("RELIANCE.NS", normalizeStockNoteSymbol("NSE:RELIANCE"))
    }

    @Test
    fun bseCodesKeepDotBo() {
        assertEquals("500325.BO", normalizeStockNoteSymbol("500325"))
        assertEquals("500325.BO", normalizeStockNoteSymbol("BSE:500325"))
        assertEquals("RELIANCE.BO", normalizeStockNoteSymbol("RELIANCE.BO"))
    }

    @Test
    fun displayBaseStripsExchangeSuffix() {
        assertEquals("RELIANCE", stockNoteDisplayBase("RELIANCE.NS"))
        assertEquals("RELIANCE", stockNoteDisplayBase("reliance"))
    }
}
