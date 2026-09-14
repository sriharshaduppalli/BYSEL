package com.bysel.trader.portfolio

/**
 * Display-time asset class for a holding. Paper orders stay symbol-only
 * on the server; the portfolio UI groups them here.
 */
enum class HoldingAssetClass(val label: String, val shortLabel: String) {
    EQUITY("Equity", "Equity"),
    ETF("ETFs", "ETFs"),
    MUTUAL_FUND("Mutual funds", "MF"),
    FNO("F&O", "F&O"),
}

object HoldingsClassifier {
    private val ETF_SUFFIXES = listOf("BEES", "ETF", "IETF")
    private val KNOWN_ETFS = setOf(
        "NIFTYBEES", "JUNIORBEES", "BANKBEES", "GOLDBEES", "SILVERBEES",
        "ITBEES", "PSUBNKBEES", "SETFNIF50", "SETFNIFBK", "ICICINIFTY",
        "HDFCSENSEX", "MON100", "GOLDIETF", "SILVERIETF", "NIFTYIETF",
        "SENSEXIETF", "BANKIETF", "IT IETF", "NEXT50IETF",
    )
    private val FNO_EXPIRY = Regex(
        """\d{2}(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(FUT|CE|PE)\d*""",
        RegexOption.IGNORE_CASE,
    )

    fun classify(
        symbol: String,
        name: String = "",
        isin: String = "",
        etfSymbols: Set<String> = emptySet(),
    ): HoldingAssetClass {
        val sym = symbol.trim().uppercase()
        val label = name.trim()
        val isinU = isin.trim().uppercase()
        if (sym.isBlank()) return HoldingAssetClass.EQUITY
        if (isFuturesOrOptions(sym, label)) return HoldingAssetClass.FNO
        if (isEtf(sym, label, etfSymbols)) return HoldingAssetClass.ETF
        if (isMutualFund(sym, label, isinU, etfSymbols)) return HoldingAssetClass.MUTUAL_FUND
        return HoldingAssetClass.EQUITY
    }

    private fun isFuturesOrOptions(symbol: String, name: String): Boolean {
        if (FNO_EXPIRY.containsMatchIn(symbol)) return true
        if (symbol.endsWith("FUT") || symbol.endsWith("-FUT")) return true
        val n = name.uppercase()
        return n.contains("FUTURE") || n.contains(" CALL ") || n.contains(" PUT ")
    }

    private fun isEtf(symbol: String, name: String, etfSymbols: Set<String>): Boolean {
        if (etfSymbols.any { it.equals(symbol, ignoreCase = true) }) return true
        if (symbol in KNOWN_ETFS) return true
        if (ETF_SUFFIXES.any { symbol.endsWith(it) }) return true
        val n = name.uppercase()
        return n.contains(" ETF") || n.endsWith("ETF") || n.contains("BEES")
    }

    private fun isMutualFund(
        symbol: String,
        name: String,
        isin: String,
        etfSymbols: Set<String>,
    ): Boolean {
        if (isEtf(symbol, name, etfSymbols)) return false
        if (isin.startsWith("INF") && isin.length >= 12) return true
        val n = name.uppercase()
        return n.contains("MUTUAL FUND") ||
            n.contains("GROWTH DIRECT") ||
            n.contains("IDCW") ||
            Regex("""\b(GILT|ELSS|INDEX FUND|FLEXI CAP|LARGE CAP|MID CAP|SMALL CAP)\b""")
                .containsMatchIn(n)
    }
}
