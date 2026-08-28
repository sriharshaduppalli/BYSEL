package com.bysel.trader.data.fno

import com.bysel.trader.data.models.FuturesTicketPreviewRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingFuturesTest {

    @Test
    fun niftyBoardHasThreeExpiriesAndIndexLot() {
        val board = TeachingFutures.build("NIFTY")
        assertEquals("NIFTY", board.symbol)
        assertEquals("synthetic", board.source)
        assertEquals(3, board.contracts.size)
        assertEquals(50, board.contracts.first().lotSize)
        assertTrue(board.spot > 0.0)
        assertTrue(board.contracts.all { it.marginPerLot > 0.0 })
    }

    @Test
    fun previewBuyUsesLoadedExpiry() {
        val board = TeachingFutures.build("NIFTY")
        val expiry = board.contracts[1].expiry
        val preview = TeachingFutures.preview(
            FuturesTicketPreviewRequest(
                symbol = "NIFTY",
                expiry = expiry,
                side = "BUY",
                lots = 2,
            ),
            board,
        )
        assertEquals("BUY", preview.side)
        assertEquals(2, preview.lots)
        assertEquals(100, preview.quantity)
        assertEquals(expiry, preview.expiry)
        assertTrue(preview.estimatedMargin > 0.0)
    }
}
