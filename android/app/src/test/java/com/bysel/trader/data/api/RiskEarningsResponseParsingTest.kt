package com.bysel.trader.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEarningsResponseParsingTest {
    private val gson = Gson()

    @Test
    fun portfolioRisk_parsesFlatBackendPayloadWithoutCrashing() {
        val json = """
            {
              "symbols": ["RELIANCE", "TCS"],
              "weights": [0.5, 0.5],
              "var95": -1.8,
              "var99": -2.9,
              "maxDrawdown": -18.4,
              "sharpeRatio": 0.72,
              "correlationMatrix": [[1.0, 0.4], [0.4, 1.0]],
              "monteCarlo": {"horizonDays": 30, "simulations": 500, "p5": -6.2, "p50": 1.1, "p95": 8.4},
              "riskLevel": "Medium"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, PortfolioRiskResponse::class.java)
        assertNotNull(parsed)
        val metrics = parsed.resolvedMetrics()
        assertEquals(-1.8, metrics.var95, 0.001)
        assertEquals(-6.2, parsed.resolvedMonteCarloP5(), 0.001)
        assertEquals(1.1, parsed.resolvedMonteCarloMedian(), 0.001)
        assertEquals("Medium", parsed.riskLevel)
        assertEquals(2, parsed.symbols.size)
        assertEquals(false, parsed.isSample)
    }

    @Test
    fun portfolioRisk_marksIllustrativeSamplePayload() {
        val json = """
            {
              "symbols": ["RELIANCE", "TCS", "INFY"],
              "var95": -1.8,
              "demoBasket": true,
              "illustrative": true,
              "disclaimer": "Illustrative sample numbers"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, PortfolioRiskResponse::class.java)
        assertTrue(parsed.isSample)
        assertTrue(parsed.illustrative)
        assertTrue(parsed.demoBasket)
    }

    @Test
    fun earningsCalendar_parsesItemsPayloadWithoutCrashing() {
        val json = """
            {
              "items": [
                {
                  "symbol": "RELIANCE",
                  "name": "Reliance Industries Limited",
                  "nextEarningsDate": "[datetime.date(2026, 10, 16)]",
                  "epsTrailing": 55.21,
                  "epsForward": 71.52,
                  "revenueGrowth": 29.7,
                  "pe": 23.7,
                  "sector": "Energy"
                }
              ],
              "count": 1,
              "generatedAt": "2026-07-31"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, EarningsCalendarResponse::class.java)
        val entries = parsed.resolvedEntries()
        assertEquals(1, entries.size)
        assertEquals("RELIANCE", entries[0].symbol)
        assertEquals("2026-10-16", entries[0].displayDate())
        assertEquals(71.52, entries[0].displayEpsEstimate()!!, 0.001)
        assertTrue(entries[0].displayTrailingPe()!! > 0)
    }
}
