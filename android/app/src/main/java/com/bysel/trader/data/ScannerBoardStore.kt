package com.bysel.trader.data

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

    fun read(context: Context, userId: Int?): ScannerBoardShelf {
        val raw = runCatching { prefs(context).getString(key(userId), null) }.getOrNull()
        return ScannerBoards.sanitize(decode(raw))
    }

    fun write(context: Context, userId: Int?, shelf: ScannerBoardShelf) {
        val clean = ScannerBoards.sanitize(shelf)
        runCatching {
            prefs(context).edit().putString(key(userId), encode(clean)).apply()
        }
    }

    internal fun encode(shelf: ScannerBoardShelf): String {
        val boards = JsonArray()
        shelf.boards.forEach { board ->
            val node = JsonObject()
            node.addProperty("id", board.id)
            node.addProperty("name", board.name)
            node.addProperty("mode", board.mode)
            node.addProperty("setupFilter", board.setupFilter)
            node.add("filters", encodeFilters(board.filters))
            boards.add(node)
        }
        val root = JsonObject()
        root.addProperty("activeId", shelf.activeId)
        root.add("boards", boards)
        return root.toString()
    }

    internal fun decode(raw: String?): ScannerBoardShelf {
        if (raw.isNullOrBlank()) return ScannerBoardShelf()
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            val boardsJson = root.getAsJsonArray("boards") ?: JsonArray()
            val boards = boardsJson.mapNotNull { element ->
                val node = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                ScannerBoard(
                    id = node.stringOrEmpty("id"),
                    name = node.stringOrEmpty("name"),
                    mode = node.stringOrEmpty("mode"),
                    setupFilter = node.stringOrEmpty("setupFilter").ifBlank { "ALL" },
                    filters = decodeFilters(node.getAsJsonObjectOrNull("filters")),
                )
            }
            ScannerBoardShelf(
                boards = boards,
                activeId = root.stringOrEmpty("activeId"),
            )
        }.getOrDefault(ScannerBoardShelf())
    }

    private fun encodeFilters(filters: CustomScannerFilters): JsonObject {
        val root = JsonObject()
        filters.minScore?.let { root.addProperty("minScore", it) }
        filters.rsi?.let { root.addProperty("rsi", it) }
        filters.dma?.let { root.addProperty("dma", it) }
        filters.minVolume?.let { root.addProperty("minVolume", it) }
        filters.maxPe?.let { root.addProperty("maxPe", it) }
        filters.minChange?.let { root.addProperty("minChange", it) }
        return root
    }

    private fun decodeFilters(root: JsonObject?): CustomScannerFilters {
        if (root == null) return CustomScannerFilters()
        return CustomScannerFilters(
            minScore = root.intOrNull("minScore"),
            rsi = root.stringOrNull("rsi"),
            dma = root.stringOrNull("dma"),
            minVolume = root.doubleOrNull("minVolume"),
            maxPe = root.doubleOrNull("maxPe"),
            minChange = root.doubleOrNull("minChange"),
        )
    }

    private fun JsonObject.stringOrEmpty(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asDouble

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun key(userId: Int?) = "shelf_${WatchlistSymbols.userKey(userId)}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
