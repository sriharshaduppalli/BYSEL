package com.bysel.trader.ui.components

/**
 * Learn-only habit topics for Home / product screens.
 * Lesson text lives in Ask AI — not on the card.
 */
data class HabitLearnLink(
    val title: String,
    val learnQuery: String,
    val id: String = title,
)

object HabitLiteracyCatalog {

    val sessionLinks: List<HabitLearnLink> = listOf(
        HabitLearnLink(
            id = "open",
            title = "Opening range & first hour",
            learnQuery = "Teach the NSE opening range and first-hour volatility as a paper habit. Cover: mark the first 15-minute band after 9:15, why the first hour is noisy, one wait-for-break rule, and the FOMO chase mistake. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "risk",
            title = "Stops, size, and FOMO",
            learnQuery = "Teach stop-loss, size, and how to avoid FOMO on NSE paper trades. Cover: write invalidation first, size so a full stop is small, and skip late chases. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "chop",
            title = "Midday chop & revenge trades",
            learnQuery = "Teach midday chop and revenge trading on NSE as a paper habit. Cover: when to stand aside after lunch, and why a second trade to 'get even' is a journal fail. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "close",
            title = "Close, CAS, and square-off",
            learnQuery = "Teach the NSE closing auction CAS and why to square off intraday paper trades. Cover: flatten before the close window, and that the official close can differ from the last tick. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "weekend",
            title = "Weekend and holiday prep",
            learnQuery = "Teach how beginners should review paper trades on weekends and holidays. Cover: three-line journal, next week's calendar, and watchlist hygiene. Not a stock pick.",
        ),
    )

    val investorLinks: List<HabitLearnLink> = listOf(
        HabitLearnLink(
            id = "long_term",
            title = "Long-term investing",
            learnQuery = "Teach how beginners should do long-term investing in Indian stocks. Cover: match money to a horizon, a simple mix, costs, and why monthly stock-swapping is not investing. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "mutual_funds",
            title = "Mutual funds",
            learnQuery = "What are mutual funds, NAV, SIP and TER for Indian beginners? Teach one factsheet habit. Educational only — not a fund recommendation.",
        ),
        HabitLearnLink(
            id = "ipo",
            title = "IPOs",
            learnQuery = "Teach how beginners should read an IPO DRHP, valuation vs peers, and allotment risk. Cover: use of proceeds, risk factors, and why oversubscription is not a thesis. Not an IPO recommendation.",
        ),
        HabitLearnLink(
            id = "fno",
            title = "F&O paper habits",
            learnQuery = "What are futures vs options, lot size, margin and expiry for NSE beginners? Educational paper habits only. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "sgb",
            title = "Sovereign Gold Bonds",
            learnQuery = "What are Sovereign Gold Bonds vs gold ETF for Indian beginners? Teach one issue-circular habit. Educational only — not a bond recommendation.",
        ),
    )

    fun investorLinksFor(topic: String?): List<HabitLearnLink> {
        val key = topic?.trim()?.lowercase()?.replace("-", "_").orEmpty()
        val mapped = when (key) {
            "mf", "mutual_fund", "mutual_funds", "funds" -> "mutual_funds"
            "ipos", "ipo", "listing" -> "ipo"
            "fo", "fno", "futures", "options", "derivatives" -> "fno"
            "sgb", "sovereign_gold", "sovereign_gold_bonds", "gold_bond" -> "sgb"
            "long_term", "longterm" -> "long_term"
            else -> key
        }
        if (mapped.isBlank()) return investorLinks
        return investorLinks.filter { it.id == mapped }.ifEmpty { investorLinks }
    }

    val fnoScannerLinks: List<HabitLearnLink> = listOf(
        HabitLearnLink(
            id = "fno_vs",
            title = "Futures vs options",
            learnQuery = "What is the difference between NSE futures and options for beginners? Educational paper practice only. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "fno_lot",
            title = "Lot size and margin",
            learnQuery = "What are lot size and margin in NSE F&O for beginners? Educational paper practice only. Not a stock pick.",
        ),
        HabitLearnLink(
            id = "fno_expiry",
            title = "Expiry and why options lose value",
            learnQuery = "Why do NSE options lose value as expiry nears? Educational paper practice only. Not a stock pick.",
        ),
    )

    fun allLearnQueries(): List<String> =
        (sessionLinks + investorLinks + fnoScannerLinks).map { it.learnQuery }

    fun isHabitLearnQuery(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (allLearnQueries().any { it.equals(q, ignoreCase = true) }) return true
        if (q.startsWith("Teach this paper habit:", ignoreCase = true)) return true
        return q.equals("What are mutual funds?", ignoreCase = true) ||
            q.equals("What are Sovereign Gold Bonds?", ignoreCase = true)
    }

    fun tipLearnQuery(title: String, body: String = ""): String {
        val cleanedTitle = title.trim().ifBlank { "this investor habit" }
        val cleanedBody = body.trim()
        return if (cleanedBody.isBlank()) {
            "Teach this paper habit: $cleanedTitle. Educational investor habits only. Not a stock pick."
        } else {
            "Teach this paper habit: $cleanedTitle. Context: $cleanedBody Educational investor habits only. Not a stock pick."
        }
    }
}
