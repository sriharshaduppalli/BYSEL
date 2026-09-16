package com.bysel.trader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerBoardsTest {
    @Test
    fun createCustomKeepsChipsAndSetsActive() {
        val chips = CustomScannerFilters(minScore = 65, rsi = "40-65")
        val shelf = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "My quality",
            mode = "CUSTOM",
            setupFilter = "ALL",
            filters = chips,
        )
        assertEquals(1, shelf.boards.size)
        assertEquals("CUSTOM", shelf.active?.mode)
        assertEquals(65, shelf.active?.filters?.minScore)
        assertEquals("40-65", shelf.active?.filters?.rsi)
    }

    @Test
    fun swingBoardDoesNotSnapshotCustomChips() {
        val shelf = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "Pullbacks",
            mode = "SWING",
            setupFilter = "PULLBACK",
            filters = CustomScannerFilters(minScore = 80),
        )
        assertEquals("SWING", shelf.active?.mode)
        assertEquals("PULLBACK", shelf.active?.setupFilter)
        assertEquals(0, shelf.active?.filters?.activeCount)
    }

    @Test
    fun rejectsFnoAndUnknownMode() {
        val created = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "F&O",
            mode = "FNO",
            setupFilter = "ALL",
            filters = CustomScannerFilters(),
        )
        assertTrue(created.boards.isEmpty())
        assertEquals("LONG_TERM", ScannerBoards.sanitizeMode("not_a_mode"))
    }

    @Test
    fun deleteClearsActiveAndAllowsEmptyShelf() {
        val created = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "Swing",
            mode = "SWING",
            setupFilter = "ALL",
            filters = CustomScannerFilters(),
        )
        val deleted = ScannerBoards.delete(created, created.activeId)
        assertTrue(deleted.boards.isEmpty())
        assertEquals("", deleted.activeId)
    }

    @Test
    fun jsonRoundTripKeepsCustomChips() {
        val created = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "My quality",
            mode = "CUSTOM",
            setupFilter = "ALL",
            filters = CustomScannerFilters(minScore = 65, rsi = "40-65"),
        )
        val restored = ScannerBoardStore.decode(ScannerBoardStore.encode(created))
        assertEquals("CUSTOM", restored.active?.mode)
        assertEquals(65, restored.active?.filters?.minScore)
        assertEquals("40-65", restored.active?.filters?.rsi)
    }

    @Test
    fun decodeBadJsonFallsBackEmpty() {
        val decoded = ScannerBoardStore.decode("[]")
        assertEquals(0, decoded.boards.size)
    }

    @Test
    fun cannotExceedMaxBoards() {
        var shelf = ScannerBoardShelf()
        repeat(ScannerBoards.MAX_BOARDS + 2) { index ->
            shelf = ScannerBoards.create(
                shelf = shelf,
                rawName = "Board $index",
                mode = "VALUE",
                setupFilter = "ALL",
                filters = CustomScannerFilters(),
            )
        }
        assertEquals(ScannerBoards.MAX_BOARDS, shelf.boards.size)
    }
}
