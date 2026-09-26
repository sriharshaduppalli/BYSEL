package com.bysel.trader.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Mirrors TradingViewModel.init: named lists and Scanner boards load on every authenticated start.
 * A throw here is a Play launch crash. Robolectric still is not R8, so keep
 * [LocalStoreReleaseContractTest] as the minify guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StartupLocalStoreTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun classInitOfStartupStoresDoesNotThrow() {
        STARTUP_STORE_CLASSES.forEach { name ->
            Class.forName(name)
        }
    }

    @Test
    fun namedWatchlistsLoadOnEmptyPrefsWithoutThrowing() {
        val board = NamedWatchlistStore.read(context, userId = 7, seedSymbols = listOf("RELIANCE", "TCS"))
        assertEquals(listOf("RELIANCE", "TCS"), board.featured?.symbols)
        assertTrue(board.featured?.pinned == true)
    }

    @Test
    fun namedWatchlistsSurviveCorruptPrefs() {
        context.getSharedPreferences("bysel_named_watchlists", 0)
            .edit()
            .putString("board_u_7", "{not-json")
            .commit()
        val board = NamedWatchlistStore.read(context, userId = 7, seedSymbols = listOf("INFY"))
        assertEquals(listOf("INFY"), board.allSymbols)
    }

    @Test
    fun namedWatchlistsRoundTripThroughPrefs() {
        val written = NamedWatchlists.seed(listOf("RELIANCE"))
        NamedWatchlistStore.write(context, userId = 3, written)
        val read = NamedWatchlistStore.read(context, userId = 3, seedSymbols = emptyList())
        assertEquals(written.activeId, read.activeId)
        assertEquals(written.featured?.symbols, read.featured?.symbols)
    }

    @Test
    fun scannerBoardsLoadOnEmptyAndCorruptPrefsWithoutThrowing() {
        val empty = ScannerBoardStore.read(context, userId = 9)
        assertTrue(empty.boards.isEmpty())

        context.getSharedPreferences("bysel_scanner_boards", 0)
            .edit()
            .putString("shelf_u_9", "[]")
            .commit()
        val recovered = ScannerBoardStore.read(context, userId = 9)
        assertTrue(recovered.boards.isEmpty())
    }

    @Test
    fun scannerBoardsRoundTripCustomChips() {
        val shelf = ScannerBoards.create(
            shelf = ScannerBoardShelf(),
            rawName = "My quality",
            mode = "CUSTOM",
            setupFilter = "ALL",
            filters = CustomScannerFilters(minScore = 65, rsi = "40-65"),
        )
        ScannerBoardStore.write(context, userId = 4, shelf)
        val read = ScannerBoardStore.read(context, userId = 4)
        assertEquals("CUSTOM", read.active?.mode)
        assertEquals(65, read.active?.filters?.minScore)
        assertEquals("40-65", read.active?.filters?.rsi)
    }

    companion object {
        private val STARTUP_STORE_CLASSES = listOf(
            "com.bysel.trader.data.NamedWatchlistStore",
            "com.bysel.trader.data.ScannerBoardStore",
            "com.bysel.trader.data.WatchlistStore",
            "com.bysel.trader.data.importbook.ImportedBookStore",
            "com.bysel.trader.data.DailyRecommendationsStore",
        )
    }
}
