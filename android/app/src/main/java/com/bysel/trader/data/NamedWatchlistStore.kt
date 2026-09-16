package com.bysel.trader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class NamedWatchlist(
    val id: String,
    val name: String,
    val symbols: List<String> = emptyList(),
    val pinned: Boolean = false,
)

data class NamedWatchlistBoard(
    val lists: List<NamedWatchlist> = emptyList(),
    val activeId: String = "",
) {
    val active: NamedWatchlist?
        get() = lists.firstOrNull { it.id == activeId } ?: lists.firstOrNull()

    val featured: NamedWatchlist?
        get() = lists.firstOrNull { it.pinned } ?: active

    val allSymbols: List<String>
        get() = WatchlistSymbols.normalizeAll(lists.flatMap { it.symbols })
}

object NamedWatchlists {
    const val DEFAULT_ID = "default"
    const val DEFAULT_NAME = "My list"
    const val MAX_LISTS = 8
    const val MAX_NAME = 24

    fun seed(symbols: List<String>): NamedWatchlistBoard {
        val names = WatchlistSymbols.normalizeAll(symbols)
        return NamedWatchlistBoard(
            lists = listOf(
                NamedWatchlist(
                    id = DEFAULT_ID,
                    name = DEFAULT_NAME,
                    symbols = names,
                    pinned = true,
                ),
            ),
            activeId = DEFAULT_ID,
        )
    }

    fun ensureSeeded(board: NamedWatchlistBoard, seedSymbols: List<String>): NamedWatchlistBoard {
        if (board.lists.isEmpty()) return seed(seedSymbols)
        return absorbUnassigned(board, seedSymbols)
    }

    fun absorbUnassigned(board: NamedWatchlistBoard, seedSymbols: List<String>): NamedWatchlistBoard {
        val known = board.allSymbols.map { WatchlistSymbols.normalize(it) }.toSet()
        val missing = WatchlistSymbols.normalizeAll(seedSymbols).filter { it !in known }
        if (missing.isEmpty()) return board
        val targetId = board.lists.firstOrNull { it.id == DEFAULT_ID }?.id
            ?: board.lists.first().id
        return updateList(board, targetId) { list ->
            list.copy(symbols = WatchlistSymbols.unionPreserveOrder(list.symbols, missing))
        }
    }

    fun create(board: NamedWatchlistBoard, rawName: String): NamedWatchlistBoard {
        if (board.lists.size >= MAX_LISTS) return board
        val name = rawName.trim().take(MAX_NAME).ifBlank { "List ${board.lists.size + 1}" }
        val id = "wl_${UUID.randomUUID().toString().take(8)}"
        val next = NamedWatchlist(id = id, name = name)
        return board.copy(lists = board.lists + next, activeId = id)
    }

    fun setActive(board: NamedWatchlistBoard, id: String): NamedWatchlistBoard {
        if (board.lists.none { it.id == id }) return board
        return board.copy(activeId = id)
    }

    fun pin(board: NamedWatchlistBoard, id: String): NamedWatchlistBoard {
        if (board.lists.none { it.id == id }) return board
        return board.copy(
            lists = board.lists.map { it.copy(pinned = it.id == id) },
            activeId = id,
        )
    }

    fun delete(board: NamedWatchlistBoard, id: String): NamedWatchlistBoard {
        if (board.lists.size <= 1) return board
        val remaining = board.lists.filterNot { it.id == id }
        val nextActive = if (board.activeId == id) {
            remaining.firstOrNull { it.pinned }?.id ?: remaining.first().id
        } else {
            board.activeId
        }
        return board.copy(lists = remaining, activeId = nextActive)
    }

    fun addSymbol(board: NamedWatchlistBoard, listId: String, symbol: String): NamedWatchlistBoard {
        val normalized = WatchlistSymbols.normalize(symbol)
        if (normalized.isBlank()) return board
        val target = listId.takeIf { id -> board.lists.any { it.id == id } }
            ?: board.active?.id
            ?: return board
        return updateList(board, target) { list ->
            if (list.symbols.any { WatchlistSymbols.matches(it, normalized) }) list
            else list.copy(symbols = WatchlistSymbols.unionPreserveOrder(list.symbols, listOf(normalized)))
        }
    }

    fun removeSymbol(board: NamedWatchlistBoard, listId: String, symbol: String): NamedWatchlistBoard {
        val normalized = WatchlistSymbols.normalize(symbol)
        if (normalized.isBlank()) return board
        return updateList(board, listId) { list ->
            list.copy(symbols = list.symbols.filterNot { WatchlistSymbols.matches(it, normalized) })
        }
    }

    fun removeSymbolEverywhere(board: NamedWatchlistBoard, symbol: String): NamedWatchlistBoard {
        val normalized = WatchlistSymbols.normalize(symbol)
        if (normalized.isBlank()) return board
        return board.copy(
            lists = board.lists.map { list ->
                list.copy(symbols = list.symbols.filterNot { WatchlistSymbols.matches(it, normalized) })
            },
        )
    }

    private fun updateList(
        board: NamedWatchlistBoard,
        id: String,
        transform: (NamedWatchlist) -> NamedWatchlist,
    ): NamedWatchlistBoard {
        return board.copy(
            lists = board.lists.map { if (it.id == id) transform(it) else it },
        )
    }
}

/** Device-local named boards. Master watchlist symbols stay in [WatchlistStore]. */
object NamedWatchlistStore {
    private const val PREFS = "bysel_named_watchlists"
    private val gson = Gson()
    private val type = object : TypeToken<NamedWatchlistBoard>() {}.type

    fun read(context: Context, userId: Int?, seedSymbols: List<String>): NamedWatchlistBoard {
        val raw = prefs(context).getString(key(userId), null)
        val parsed = raw?.let {
            runCatching { gson.fromJson<NamedWatchlistBoard>(it, type) }.getOrNull()
        }
        return NamedWatchlists.ensureSeeded(parsed ?: NamedWatchlistBoard(), seedSymbols)
    }

    fun write(context: Context, userId: Int?, board: NamedWatchlistBoard) {
        val seeded = NamedWatchlists.ensureSeeded(board, board.allSymbols)
        prefs(context).edit().putString(key(userId), gson.toJson(seeded)).apply()
    }

    private fun key(userId: Int?) = "board_${WatchlistSymbols.userKey(userId)}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
