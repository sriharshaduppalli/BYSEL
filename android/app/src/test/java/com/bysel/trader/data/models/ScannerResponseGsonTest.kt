package com.bysel.trader.data.models

import com.bysel.trader.data.api.SafeGsonFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerResponseGsonTest {

    private val gson = SafeGsonFactory.create()

    @Test
    fun gsonCanBuildAdapterForScannerResponse() {
        val adapter = gson.getAdapter(ScannerResponse::class.java)
        val parsed = adapter.fromJson(
            """
            {
              "mode": "long_term",
              "generatedAt": "2026-08-21T00:00:00Z",
              "education": {"title": "Scanner", "filters": []},
              "rows": [
                {
                  "symbol": "RELIANCE",
                  "name": "Reliance",
                  "last": 1400.5,
                  "ai_summary": "Quality held",
                  "why": "kept for display",
                  "metrics": {"pe": 22.5, "marketCap": 1800000},
                  "pillars": {
                    "quality": {
                      "score": 70,
                      "metrics": {
                        "roe": {"value": 16.2, "score": 70, "used": true}
                      }
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("long_term", parsed.mode)
        assertEquals(1, parsed.rows.size)
        assertEquals("RELIANCE", parsed.rows[0].symbol)
        assertEquals("Quality held", parsed.rows[0].aiSummary)
        assertEquals("kept for display", parsed.rows[0].why)
        assertEquals(22.5, parsed.rows[0].metrics.pe!!, 0.01)
        assertTrue(parsed.rows[0].displaySummary.isNotBlank())
    }
}
