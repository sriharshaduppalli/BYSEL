package com.bysel.trader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedWatchlistsTest {
    @Test
    fun seedPutsExistingNamesOnMyList() {
        val board = NamedWatchlists.seed(listOf("RELIANCE", "tcs"))
        assertEquals(1, board.lists.size)
        assertEquals(listOf("RELIANCE", "TCS"), board.active?.symbols)
        assertTrue(board.featured?.pinned == true)
    }

    @Test
    fun createThenAddStaysOnActiveList() {
        val seeded = NamedWatchlists.seed(listOf("RELIANCE"))
        val created = NamedWatchlists.create(seeded, "Swing")
        val added = NamedWatchlists.addSymbol(created, created.activeId, "INFY")
        assertEquals(listOf("INFY"), added.active?.symbols)
        assertEquals(listOf("RELIANCE", "INFY"), added.allSymbols)
    }

    @Test
    fun removeFromOneListKeepsOtherBoards() {
        var board = NamedWatchlists.seed(listOf("RELIANCE", "TCS"))
        board = NamedWatchlists.create(board, "IT")
        board = NamedWatchlists.addSymbol(board, board.activeId, "TCS")
        board = NamedWatchlists.removeSymbol(board, board.activeId, "TCS")
        assertTrue(board.active?.symbols.orEmpty().none { it == "TCS" })
        assertTrue(board.allSymbols.contains("TCS"))
    }

    @Test
    fun absorbUnassignedDoesNotDropMasterNames() {
        val board = NamedWatchlists.seed(listOf("RELIANCE"))
        val absorbed = NamedWatchlists.absorbUnassigned(board, listOf("RELIANCE", "INFY"))
        assertEquals(listOf("RELIANCE", "INFY"), absorbed.allSymbols)
    }

    @Test
    fun cannotDeleteLastList() {
        val board = NamedWatchlists.seed(listOf("RELIANCE"))
        assertEquals(1, NamedWatchlists.delete(board, NamedWatchlists.DEFAULT_ID).lists.size)
    }

    @Test
    fun jsonRoundTripKeepsPinnedList() {
        val original = NamedWatchlists.seed(listOf("RELIANCE", "TCS"))
        val restored = NamedWatchlistStore.decode(NamedWatchlistStore.encode(original))
        assertEquals(original.activeId, restored.activeId)
        assertEquals(original.featured?.symbols, restored.featured?.symbols)
        assertEquals(true, restored.featured?.pinned)
    }

    @Test
    fun decodeBadJsonFallsBackEmpty() {
        val decoded = NamedWatchlistStore.decode("{not-json")
        assertEquals(0, decoded.lists.size)
    }
}
