package com.bysel.trader.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSessionTest {
    @Test
    fun starterPackIsThreeCashNames() {
        assertEquals(3, FirstSession.PICK_COUNT)
        assertEquals(6, FirstSession.STARTER_NAMES.size)
        assertTrue(FirstSession.STARTER_NAMES.containsAll(listOf("RELIANCE", "TCS", "HDFCBANK")))
        assertEquals(25_000.0, FirstSession.STARTER_CREDIT, 0.01)
    }
}
