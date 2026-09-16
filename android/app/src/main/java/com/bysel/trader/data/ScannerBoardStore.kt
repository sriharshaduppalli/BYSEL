package com.bysel.trader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class ScannerBoard(
    val id: String,
    val name: String,
    val mode: String,
    val setupFilter: String = "ALL",
    val filters: CustomScannerFilters = CustomScannerFilters(),
)

data class ScannerBoardShelf(
    val boards: List<ScannerBoard> = emptyList(),
    val activeId: String = "",
) {
    val active: ScannerBoard?
        get() = boards.firstOrNull { it.id == activeId }
}

object ScannerBoards {
    const val MAX_BOARDS = 8
    const val MAX_NAME = 24
    val ALLOWED_MODES = setOf(
        "LONG_TERM",
        "SWING",
        "HIGH_QUALITY",
        "MOMENTUM",
        "VALUE",
        "QUALITY_SCREEN",
        "CUSTOM",
    )
    val ALLOWED_SETUPS = setOf("ALL", "PULLBACK", "BREAKOUT")

    fun sanitizeMode(raw: String?): String {
        val key = raw?.trim()?.uppercase().orEmpty()
        return if (key in ALLOWED_MODES) key else "LONG_TERM"
    }

    fun sanitizeSetup(raw: String?): String {
        val key = raw?.trim()?.uppercase().orEmpty()
        return if (key in ALLOWED_SETUPS) key else "ALL"
    }

    fun sanitize(shelf: ScannerBoardShelf): ScannerBoardShelf {
        val boards = shelf.boards.map { board ->
            board.copy(
                name = board.name.trim().take(MAX_NAME).ifBlank { "Board" },
                mode = sanitizeMode(board.mode),
                setupFilter = sanitizeSetup(board.setupFilter),
            )
        }.take(MAX_BOARDS)
        val activeId = shelf.activeId.takeIf { id -> boards.any { it.id == id } }.orEmpty()
        return ScannerBoardShelf(boards = boards, activeId = activeId)
    }

    fun create(
        shelf: ScannerBoardShelf,
        rawName: String,
        mode: String,
        setupFilter: String,
        filters: CustomScannerFilters,
    ): ScannerBoardShelf {
        if (shelf.boards.size >= MAX_BOARDS) return shelf
        val requested = mode.trim().uppercase()
        if (requested !in ALLOWED_MODES) return shelf
        val cleanMode = requested
        val name = rawName.trim().take(MAX_NAME).ifBlank { defaultName(cleanMode, shelf.boards.size) }
        val board = ScannerBoard(
            id = "sb_${UUID.randomUUID().toString().take(8)}",
            name = name,
            mode = cleanMode,
            setupFilter = sanitizeSetup(setupFilter),
            filters = if (cleanMode == "CUSTOM") filters else CustomScannerFilters(),
        )
        return shelf.copy(boards = shelf.boards + board, activeId = board.id)
    }

    fun setActive(shelf: ScannerBoardShelf, id: String): ScannerBoardShelf {
        if (id.isBlank()) return shelf.copy(activeId = "")
        if (shelf.boards.none { it.id == id }) return shelf
        return shelf.copy(activeId = id)
    }

    fun update(
        shelf: ScannerBoardShelf,
        id: String,
        mode: String,
        setupFilter: String,
        filters: CustomScannerFilters,
    ): ScannerBoardShelf {
        if (shelf.boards.none { it.id == id }) return shelf
        val requested = mode.trim().uppercase()
        if (requested !in ALLOWED_MODES) return shelf
        val cleanMode = requested
        return shelf.copy(
            boards = shelf.boards.map { board ->
                if (board.id != id) board
                else board.copy(
                    mode = cleanMode,
                    setupFilter = sanitizeSetup(setupFilter),
                    filters = if (cleanMode == "CUSTOM") filters else CustomScannerFilters(),
                )
            },
            activeId = id,
        )
    }

    fun delete(shelf: ScannerBoardShelf, id: String): ScannerBoardShelf {
        val remaining = shelf.boards.filterNot { it.id == id }
        val nextActive = if (shelf.activeId == id) "" else shelf.activeId
        return shelf.copy(boards = remaining, activeId = nextActive)
    }

    fun defaultName(mode: String, existingCount: Int): String {
        val label = when (sanitizeMode(mode)) {
            "LONG_TERM" -> "Long-term board"
            "SWING" -> "Swing board"
            "HIGH_QUALITY" -> "High Quality board"
            "MOMENTUM" -> "QM board"
            "VALUE" -> "Value board"
            "QUALITY_SCREEN" -> "Quality board"
            "CUSTOM" -> "Custom board"
            else -> "Scanner board"
        }
        return if (existingCount == 0) label else "$label ${existingCount + 1}"
    }
}

/** Device-local named Scanner snapshots. Does not change live scan results. */
object ScannerBoardStore {
    private const val PREFS = "bysel_scanner_boards"
    private val gson = Gson()
    private val type = object : TypeToken<ScannerBoardShelf>() {}.type

    fun read(context: Context, userId: Int?): ScannerBoardShelf {
        val raw = prefs(context).getString(key(userId), null)
        val parsed = raw?.let {
            runCatching { gson.fromJson<ScannerBoardShelf>(it, type) }.getOrNull()
        }
        return ScannerBoards.sanitize(parsed ?: ScannerBoardShelf())
    }

    fun write(context: Context, userId: Int?, shelf: ScannerBoardShelf) {
        val clean = ScannerBoards.sanitize(shelf)
        prefs(context).edit().putString(key(userId), gson.toJson(clean)).apply()
    }

    private fun key(userId: Int?) = "shelf_${WatchlistSymbols.userKey(userId)}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
