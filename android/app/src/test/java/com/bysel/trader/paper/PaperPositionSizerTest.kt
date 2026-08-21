package com.bysel.trader.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaperPositionSizerTest {

    @Test
    fun qtyIsBudgetDividedByStopDistance() {
        // ₹1,00,000 × 1% = ₹1,000 risk. Entry 100, stop 90 → ₹10/share → 100 qty.
        assertEquals(100, PaperPositionSizer.suggestedQty(100_000.0, 1.0, 100.0, 90.0))
    }

    @Test
    fun blankOrEqualStopReturnsNull() {
        assertNull(PaperPositionSizer.suggestedQty(100_000.0, 1.0, 100.0, 0.0))
        assertNull(PaperPositionSizer.suggestedQty(100_000.0, 1.0, 100.0, 100.0))
        assertNull(PaperPositionSizer.suggestedQty(0.0, 1.0, 100.0, 90.0))
    }

    @Test
    fun tinyBudgetCannotBuyOneShare() {
        assertNull(PaperPositionSizer.suggestedQty(100.0, 1.0, 500.0, 490.0))
    }
}
