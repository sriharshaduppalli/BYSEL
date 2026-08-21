package com.bysel.trader.data.importbook

data class ImportedHolding(
    val symbol: String,
    val qty: Int,
    val avgPrice: Double = 0.0,
    val name: String = "",
    val isin: String = "",
    val lastMark: Double = 0.0,
)

data class ImportedBook(
    val sourceLabel: String = "",
    val fileName: String = "",
    val importedAtMs: Long = 0L,
    val rows: List<ImportedHolding> = emptyList(),
    val skipped: List<String> = emptyList(),
    val overlapIgnored: Int = 0,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class ImportParseResult(
    val book: ImportedBook,
    val error: String? = null,
)
