package com.bysel.trader.data.importbook

/**
 * Read-only holdings import. Accepts broker CSV / CAS-style tables.
 * Never invents qty or average. Name-only rows are skipped unless [nameToSymbol] resolves them.
 */
object CasCsvParser {
    const val MAX_ROWS = 80

    private val SYMBOL_HEADERS = setOf(
        "symbol", "tradingsymbol", "trading symbol", "nse symbol", "ticker",
        "instrument", "scrip", "scrip symbol", "scripsymbol", "nsecode",
    )
    private val QTY_HEADERS = setOf(
        "qty", "qty.", "quantity", "qty available", "available qty",
        "current qty", "closing balance", "balance", "free qty", "net qty",
    )
    private val AVG_HEADERS = setOf(
        "avg", "avg.", "avg price", "avg. cost", "average", "average price",
        "average buy price", "avg cost", "buy avg", "buy average",
        "average cost", "avg buy price",
    )
    private val NAME_HEADERS = setOf(
        "name", "stock name", "company", "company name", "security name",
        "scrip name", "stock",
    )
    private val ISIN_HEADERS = setOf("isin")
    private val LAST_HEADERS = setOf("ltp", "last", "last price", "close", "closing price")

    fun parse(
        text: String,
        fileName: String = "",
        nameToSymbol: Map<String, String> = emptyMap(),
    ): ImportParseResult {
        val raw = text.replace("\uFEFF", "").trim()
        if (raw.isBlank()) {
            return ImportParseResult(
                book = ImportedBook(fileName = fileName),
                error = "That file was empty.",
            )
        }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return ImportParseResult(ImportedBook(fileName = fileName), "That file was empty.")
        }

        val delimiter = detectDelimiter(lines.first())
        val headerCells = splitRow(lines.first(), delimiter).map { normalizeHeader(it) }
        val symbolIdx = indexOf(headerCells, SYMBOL_HEADERS)
        val qtyIdx = indexOf(headerCells, QTY_HEADERS)
        val avgIdx = indexOf(headerCells, AVG_HEADERS)
        val nameIdx = indexOf(headerCells, NAME_HEADERS)
        val isinIdx = indexOf(headerCells, ISIN_HEADERS)
        val lastIdx = indexOf(headerCells, LAST_HEADERS)

        val hasHeader = symbolIdx != null || qtyIdx != null || nameIdx != null
        val dataLines = if (hasHeader) lines.drop(1) else lines
        val fallbackSymbol = if (!hasHeader) 0 else symbolIdx
        val fallbackQty = if (!hasHeader && lines.first().let { splitRow(it, delimiter).size >= 2 }) 1 else qtyIdx
        val fallbackAvg = if (!hasHeader && lines.first().let { splitRow(it, delimiter).size >= 3 }) 2 else avgIdx

        if (fallbackQty == null && qtyIdx == null) {
            return ImportParseResult(
                ImportedBook(fileName = fileName),
                "Need a Quantity column (Zerodha / Groww / generic Symbol,Qty,Avg).",
            )
        }

        val rows = linkedMapOf<String, ImportedHolding>()
        val skipped = mutableListOf<String>()
        for (line in dataLines) {
            if (rows.size >= MAX_ROWS) {
                skipped += "Stopped at $MAX_ROWS names — import the rest in a second file if needed."
                break
            }
            val cells = splitRow(line, delimiter)
            if (cells.isEmpty() || looksLikeTotal(cells)) continue
            val qty = parseNumber(cells.getOrNull(fallbackQty ?: qtyIdx ?: -1)).toInt()
            if (qty <= 0) {
                skipped += "Skipped a row with no quantity."
                continue
            }
            val rawSymbol = cells.getOrNull(fallbackSymbol ?: -1).orEmpty()
            val name = cells.getOrNull(nameIdx ?: -1).orEmpty().trim()
            val resolved = normalizeSymbol(rawSymbol)
                ?: resolveName(name, nameToSymbol)
            if (resolved == null) {
                skipped += "No NSE symbol for “${name.ifBlank { rawSymbol.ifBlank { line.take(40) }}}”."
                continue
            }
            val avg = parseNumber(cells.getOrNull(fallbackAvg ?: -1))
            val last = parseNumber(cells.getOrNull(lastIdx ?: -1))
            val existing = rows[resolved]
            if (existing != null) {
                rows[resolved] = existing.copy(
                    qty = existing.qty + qty,
                    avgPrice = blendedAvg(existing.qty, existing.avgPrice, qty, avg),
                    lastMark = if (last > 0) last else existing.lastMark,
                    name = existing.name.ifBlank { name },
                )
            } else {
                rows[resolved] = ImportedHolding(
                    symbol = resolved,
                    qty = qty,
                    avgPrice = avg,
                    name = name,
                    isin = cells.getOrNull(isinIdx ?: -1).orEmpty().trim().uppercase(),
                    lastMark = last,
                )
            }
        }

        if (rows.isEmpty()) {
            return ImportParseResult(
                ImportedBook(
                    sourceLabel = sourceLabel(fileName, headerCells),
                    fileName = fileName,
                    importedAtMs = System.currentTimeMillis(),
                    skipped = skipped.take(12),
                ),
                "No holdings with an NSE symbol and quantity. Use Symbol, Qty, Avg price.",
            )
        }

        return ImportParseResult(
            ImportedBook(
                sourceLabel = sourceLabel(fileName, headerCells),
                fileName = fileName,
                importedAtMs = System.currentTimeMillis(),
                rows = rows.values.toList(),
                skipped = skipped.distinct().take(12),
            )
        )
    }

    fun normalizeSymbol(raw: String): String? {
        var value = raw.trim().uppercase()
        if (value.isEmpty() || value == "SYMBOL" || value == "INSTRUMENT") return null
        value = value.removePrefix("NSE:").removePrefix("BSE:")
        value = value.removeSuffix(".NS").removeSuffix(".BO").removeSuffix(".BSE")
        val dash = value.indexOf('-')
        if (dash > 0) {
            val suffix = value.substring(dash + 1)
            if (suffix in setOf("EQ", "BE", "BZ", "SM", "ST", "N1", "N2")) {
                value = value.substring(0, dash)
            }
        }
        val cleaned = value.replace(" ", "")
        if (cleaned.length !in 2..20) return null
        if (!cleaned.all { it.isLetterOrDigit() || it == '&' }) return null
        if (cleaned.all { it.isDigit() }) return null
        return cleaned
    }

    private fun resolveName(name: String, map: Map<String, String>): String? {
        val key = normalizeName(name)
        if (key.isEmpty()) return null
        map[key]?.let { return it }
        return map.entries.firstOrNull { (candidate, _) ->
            candidate == key || candidate.startsWith(key) || key.startsWith(candidate)
        }?.value
    }

    fun normalizeName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9&]+"), " ")
            .replace(Regex("\\b(ltd|limited|the)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun blendedAvg(qtyA: Int, avgA: Double, qtyB: Int, avgB: Double): Double {
        if (avgA <= 0.0 && avgB <= 0.0) return 0.0
        if (avgA <= 0.0) return avgB
        if (avgB <= 0.0) return avgA
        val den = (qtyA + qtyB).toDouble()
        if (den <= 0.0) return 0.0
        return (avgA * qtyA + avgB * qtyB) / den
    }

    private fun sourceLabel(fileName: String, headers: List<String>): String {
        val joined = headers.joinToString(" ")
        val lower = "$fileName $joined".lowercase()
        return when {
            "instrument" in headers && "avg. cost" in headers -> "Zerodha holdings CSV"
            "tradingsymbol" in headers -> "Broker holdings CSV"
            "isin" in headers && "closing balance" in headers -> "CAS / DP statement"
            "groww" in lower -> "Groww holdings CSV"
            fileName.contains("cas", ignoreCase = true) -> "CAS statement"
            else -> "Holdings CSV"
        }
    }

    private fun detectDelimiter(line: String): Char {
        val commas = line.count { it == ',' }
        val tabs = line.count { it == '\t' }
        val semis = line.count { it == ';' }
        return when {
            tabs > commas && tabs > semis -> '\t'
            semis > commas -> ';'
            else -> ','
        }
    }

    private fun splitRow(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var quoted = false
        line.forEach { ch ->
            when {
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> {
                    out += buf.toString().trim().trim('"')
                    buf.clear()
                }
                else -> buf.append(ch)
            }
        }
        out += buf.toString().trim().trim('"')
        return out
    }

    private fun normalizeHeader(value: String): String =
        value.lowercase().replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun indexOf(headers: List<String>, aliases: Set<String>): Int? {
        headers.forEachIndexed { index, header ->
            if (header in aliases) return index
        }
        return null
    }

    private fun parseNumber(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val cleaned = raw.replace(",", "").replace("₹", "").replace("%", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun looksLikeTotal(cells: List<String>): Boolean {
        val first = cells.firstOrNull()?.lowercase().orEmpty()
        return first.startsWith("total") || first == "grand total" || first == "portfolio"
    }
}
