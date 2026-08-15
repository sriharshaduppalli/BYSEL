package com.bysel.trader.data

/** Normalize a ticker the same way backend stock notes do (e.g. RELIANCE → RELIANCE.NS). */
fun normalizeStockNoteSymbol(raw: String): String {
    var cleaned = raw.trim().uppercase().replace(" ", "")
    if (cleaned.isBlank()) return ""

    when {
        cleaned.startsWith("NSE:") -> cleaned = cleaned.removePrefix("NSE:")
        cleaned.startsWith("BSE:") -> {
            val base = cleaned.removePrefix("BSE:").removeSuffix(".BO")
            return if (base.isNotBlank()) "$base.BO" else ""
        }
    }

    if (cleaned.endsWith(".BO")) {
        val base = cleaned.removeSuffix(".BO")
        return if (base.isNotBlank()) "$base.BO" else ""
    }
    if (cleaned.endsWith(".NS")) return cleaned
    if (cleaned.length == 6 && cleaned.all { it.isDigit() }) return "$cleaned.BO"
    return "$cleaned.NS"
}

fun stockNoteDisplayBase(symbol: String): String {
    val normalized = normalizeStockNoteSymbol(symbol).ifBlank { symbol.trim().uppercase() }
    return normalized.removeSuffix(".NS").removeSuffix(".BO")
}
