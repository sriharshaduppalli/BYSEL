package com.bysel.trader.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiScreenContextTest {

    @Test
    fun homeDoesNotInheritStaleQuote() {
        val previous = AiScreenContext(source = "stock_detail", symbol = "INFY")
        val home = AiScreenContext.forTab(0, "INFY", previous)
        assertEquals("home", home.source)
        assertNull(home.symbol)
        assertFalse(home.allowsStockInherit)
    }

    @Test
    fun aiKeepsSymbolOnlyWhenComingFromStockDetail() {
        val fromDetail = AiScreenContext.forTab(
            1,
            "RELIANCE",
            AiScreenContext(source = "stock_detail", symbol = "RELIANCE"),
        )
        assertEquals("ai", fromDetail.source)
        assertEquals("RELIANCE", fromDetail.symbol)
        assertTrue(fromDetail.allowsStockInherit)

        val fromHome = AiScreenContext.forTab(
            1,
            "RELIANCE",
            AiScreenContext(source = "home"),
        )
        assertNull(fromHome.symbol)
        assertFalse(fromHome.allowsStockInherit)
    }

    @Test
    fun stockDetailAndScannerMaps() {
        val detail = AiScreenContext.forTab(9, "tcs", AiScreenContext())
        assertEquals("stock_detail", detail.source)
        assertEquals("TCS", detail.symbol)

        val scanner = AiScreenContext.forTab(
            28,
            "TCS",
            AiScreenContext(),
            scannerMode = "quality_screen",
        )
        assertEquals("scanner", scanner.source)
        assertNull(scanner.symbol)
        assertEquals("quality_screen", scanner.scannerMode)
        assertEquals(
            mapOf("source" to "scanner", "scanner_mode" to "quality_screen"),
            scanner.toRequestMap(),
        )
    }
}
