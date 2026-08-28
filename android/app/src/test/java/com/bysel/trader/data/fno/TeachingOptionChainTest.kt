package com.bysel.trader.data.fno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingOptionChainTest {

    @Test
    fun niftyBoardUsesEducationalSpotAndContracts() {
        val chain = TeachingOptionChain.build("NIFTY", "2026-09-17")
        assertEquals("NIFTY", chain.symbol)
        assertEquals("synthetic", chain.source)
        assertEquals(24_500.0, chain.spot, 0.01)
        assertTrue(chain.contracts.size >= 10)
        assertTrue(chain.notes.any { it.contains("Teaching chain", ignoreCase = true) })
        assertTrue(chain.pcr != null && chain.pcr!! > 0.0)
    }

    @Test
    fun missingLiveSpotMatchesProduction404Copy() {
        assertTrue(
            TeachingOptionChain.isMissingLiveSpot(
                RuntimeException("HTTP 404 Could not fetch live spot for NIFTY"),
            ),
        )
    }
}
