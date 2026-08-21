package com.bysel.trader.data.importbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasCsvParserTest {

    @Test
    fun parsesZerodhaHoldingsCsv() {
        val csv = """
            Instrument,Qty.,Avg. cost,LTP
            RELIANCE-EQ,10,2500.00,2600
            TCS-EQ,5,"3,500.50",3600
        """.trimIndent()
        val result = CasCsvParser.parse(csv, fileName = "holdings.csv")
        assertNull(result.error)
        assertEquals("Zerodha holdings CSV", result.book.sourceLabel)
        assertEquals(2, result.book.rows.size)
        assertEquals("RELIANCE", result.book.rows[0].symbol)
        assertEquals(10, result.book.rows[0].qty)
        assertEquals(2500.0, result.book.rows[0].avgPrice, 0.01)
        assertEquals("TCS", result.book.rows[1].symbol)
        assertEquals(3500.50, result.book.rows[1].avgPrice, 0.01)
    }

    @Test
    fun parsesGenericSymbolQtyAvg() {
        val csv = "Symbol,Qty,AvgPrice\nINFY,8,1400\n"
        val result = CasCsvParser.parse(csv)
        assertEquals("INFY", result.book.rows.single().symbol)
        assertEquals(8, result.book.rows.single().qty)
    }

    @Test
    fun resolvesCompanyNameFromCatalog() {
        val csv = "Stock Name,Quantity,Average buy price\nInfosys Limited,12,1500\n"
        val result = CasCsvParser.parse(
            csv,
            nameToSymbol = mapOf("infosys" to "INFY"),
        )
        assertEquals("INFY", result.book.rows.single().symbol)
        assertEquals(12, result.book.rows.single().qty)
    }

    @Test
    fun skipsNameOnlyWithoutCatalog() {
        val csv = "Company Name,Closing Balance\nSome Unknown Pvt Ltd,20\n"
        val result = CasCsvParser.parse(csv)
        assertTrue(result.book.rows.isEmpty())
        assertTrue(result.error?.contains("NSE symbol") == true)
    }

    @Test
    fun normalizeStripsExchangeSuffix() {
        assertEquals("HDFCBANK", CasCsvParser.normalizeSymbol("NSE:HDFCBANK.NS"))
        assertEquals("SBIN", CasCsvParser.normalizeSymbol("SBIN-EQ"))
        assertNull(CasCsvParser.normalizeSymbol("12345"))
    }
}
