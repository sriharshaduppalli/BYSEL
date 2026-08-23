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
            learnQuery = "What is the NSE opening range and first-hour volatility? Educational paper-practice habits only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "risk",
            title = "Stops, size, and FOMO",
            learnQuery = "How should beginners set stop-loss, size, and avoid FOMO on NSE paper trades? Educational only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "chop",
            title = "Midday chop & revenge trades",
            learnQuery = "What is midday chop and revenge trading on NSE? Educational session habits only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "close",
            title = "Close, CAS, and square-off",
            learnQuery = "What is the NSE closing auction CAS and why square off intraday paper trades? Educational only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "weekend",
            title = "Weekend and holiday prep",
            learnQuery = "How should beginners review paper trades on weekends and holidays? Educational session habits only.",
        ),
    )

    val investorLinks: List<HabitLearnLink> = listOf(
        HabitLearnLink(
            id = "long_term",
            title = "Long-term investing",
            learnQuery = "How should beginners do long-term investing in Indian stocks? Educational investor habits only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "mutual_funds",
            title = "Mutual funds",
            learnQuery = "What are mutual funds, NAV, SIP and TER for Indian beginners? Educational only — not a fund recommendation.",
        ),
        HabitLearnLink(
            id = "ipo",
            title = "IPOs",
            learnQuery = "How should beginners read an IPO DRHP, valuation vs peers, and allotment risk? Educational only — not an IPO recommendation.",
        ),
        HabitLearnLink(
            id = "fno",
            title = "F&O paper habits",
            learnQuery = "What are futures vs options, lot size, margin and expiry for NSE beginners? Educational paper habits only — no buy or sell.",
        ),
        HabitLearnLink(
            id = "sgb",
            title = "Sovereign Gold Bonds",
            learnQuery = "What are Sovereign Gold Bonds vs gold ETF for Indian beginners? Educational only — not a bond recommendation.",
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
}
