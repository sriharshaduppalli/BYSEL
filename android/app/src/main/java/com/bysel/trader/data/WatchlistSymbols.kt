package com.bysel.trader.data

import com.bysel.trader.data.models.Quote

/**
 * Watchlist symbol identity, encoding, and merge rules.
 *
 * Local names are the source of truth. An empty remote/error payload must never
 * replace a last-good list unless the user explicitly cleared it.
 */
object WatchlistSymbols {
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
