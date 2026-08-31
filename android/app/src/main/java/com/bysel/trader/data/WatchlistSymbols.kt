package com.bysel.trader.data

import com.bysel.trader.data.models.Quote

/**
 * Watchlist symbol identity, encoding, and merge rules.
 *
 * Local names are the source of truth. An empty remote/error payload must never
 * replace a last-good list unless the user explicitly cleared it.
 */
object WatchlistSymbols {
    /** User-independent last-known copy so a session blip cannot hide the list. */
    const val DEVICE_KEY = "device"

    fun userKey(userId: Int?): String =
        if (userId != null && userId > 0) "u_$userId" else "anon"

    fun normalize(raw: String): String {
        val cleaned = raw.trim().uppercase().replace(" ", "")
        if (cleaned.isBlank()) return ""

        val prefixed = when {
            cleaned.startsWith("NSE:") -> cleaned.removePrefix("NSE:")
            cleaned.startsWith("BSE:") -> "${cleaned.removePrefix("BSE:").removeSuffix(".BO")}.BO"
            else -> cleaned
        }

        if (prefixed.endsWith(".BO")) {
            val base = prefixed.removeSuffix(".BO")
            return if (base.isNotBlank()) "$base.BO" else ""
        }

        if (prefixed.endsWith(".NS")) {
            return prefixed.removeSuffix(".NS")
        }

        if (prefixed.length == 6 && prefixed.all { it.isDigit() }) {
            return "$prefixed.BO"
        }

        return prefixed
    }

    fun normalizeAll(symbols: Collection<String>): List<String> =
        symbols.map { normalize(it) }.filter { it.isNotBlank() }.distinct()

    /**
     * @param allowEmpty true only for an explicit user edit (removed the last name).
     */
    fun coalesce(
        incoming: List<String>,
        lastGood: List<String>,
        allowEmpty: Boolean,
    ): List<String> {
        val normalizedIncoming = normalizeAll(incoming)
        val normalizedGood = normalizeAll(lastGood)
        if (normalizedIncoming.isEmpty() && normalizedGood.isNotEmpty() && !allowEmpty) {
            return normalizedGood
        }
        return normalizedIncoming
    }

    /**
     * Same-user restore: keep [primary] order and append any extra names from [extra].
     * A stale shorter snapshot must never drop names that the other store still has.
     */
    /**
     * After an app update, token expiry, or re-login the current owner key is often
     * `anon` or a new `u_*` while the names still live under another key on disk.
     * Union every stored bucket so that empty current key never hides a last-good list.
     */
    fun recoverFromKeyedStores(
        currentUserId: Int?,
        keyedLists: Map<String, List<String>>,
        legacy: List<String> = emptyList(),
        lastGood: List<String> = emptyList(),
    ): List<String> {
        val currentKey = userKey(currentUserId)
        var recovered = normalizeAll(keyedLists[currentKey].orEmpty())
        recovered = unionPreserveOrder(recovered, keyedLists[DEVICE_KEY].orEmpty())
        recovered = unionPreserveOrder(recovered, legacy)
        for ((key, list) in keyedLists) {
            if (key == currentKey || key == DEVICE_KEY) continue
            recovered = unionPreserveOrder(recovered, list)
        }
        return coalesce(recovered, lastGood, allowEmpty = false)
    }

    fun unionPreserveOrder(primary: List<String>, extra: List<String>): List<String> {
        val first = normalizeAll(primary)
        val second = normalizeAll(extra)
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val seen = first.toMutableSet()
        val out = first.toMutableList()
        for (symbol in second) {
            if (seen.add(symbol)) {
                out.add(symbol)
            }
        }
        return out
    }

    fun aliases(symbol: String): Set<String> {
        val normalized = normalize(symbol)
        if (normalized.isBlank()) return emptySet()
        val base = normalized.removeSuffix(".BO").removeSuffix(".NS")
        return linkedSetOf(normalized, base, "$base.NS", "$base.BO")
    }

    fun matches(watchlistSymbol: String, other: String): Boolean {
        val wanted = aliases(watchlistSymbol)
        return aliases(other).any { it in wanted }
    }

    fun findQuote(quotes: List<Quote>, watchlistSymbol: String): Quote? {
        val wanted = aliases(watchlistSymbol)
        if (wanted.isEmpty()) return null
        return quotes.firstOrNull { quote -> aliases(quote.symbol).any { it in wanted } }
    }

    fun encode(symbols: List<String>): String = normalizeAll(symbols).joinToString(",")

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return normalizeAll(raw.split(',', '\n', ';', '|'))
    }
}
