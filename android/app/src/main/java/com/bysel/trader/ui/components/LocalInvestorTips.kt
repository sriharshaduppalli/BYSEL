package com.bysel.trader.ui.components

import com.bysel.trader.data.models.InvestorTip
import com.bysel.trader.data.models.InvestorTipsResponse
import com.bysel.trader.data.models.InvestorTopicInfo

private val DEFAULT_TOPICS = listOf(
    InvestorTopicInfo("long_term", "Long-term"),
    InvestorTopicInfo("mutual_funds", "Mutual funds"),
    InvestorTopicInfo("ipo", "IPOs"),
    InvestorTopicInfo("fno", "F&O"),
    InvestorTopicInfo("sgb", "SGB"),
)

private val LOCAL_BANKS: Map<String, List<InvestorTip>> = mapOf(
    "long_term" to listOf(
        InvestorTip("lt_horizon", "Define the horizon first", "Money needed within 3 years usually shouldn't sit in concentrated equity bets.", "process"),
        InvestorTip("lt_allocation", "Allocation beats stock-picking", "Equity/debt mix usually drives multi-year outcomes more than swapping one large-cap for another.", "process"),
        InvestorTip("lt_drawdown", "Plan for drawdowns", "If a 30–40% equity drawdown would force a sale, allocation is too aggressive.", "psychology"),
    ),
    "mutual_funds" to listOf(
        InvestorTip("mf_pool", "MF = pooled access", "Units give exposure to many securities via an AMC — handy vs buying an index yourself.", "process"),
        InvestorTip("mf_nav", "NAV is unit price, not quality", "NAV = (assets − liabilities) / units. Judge returns, risk, and TER — not a ‘cheap’ ₹10 NAV.", "process"),
        InvestorTip("mf_goal", "Fund follows the goal", "Pick category from goal + horizon, then compare funds inside that box.", "process"),
        InvestorTip("mf_risk_appetite", "Risk = what you won't abandon", "Size equity so a drawdown won't make you stop the SIP.", "psychology"),
        InvestorTip("mf_fees", "TER + exit load matter", "Annual expense drag and early-exit loads both reduce net wealth.", "risk"),
        InvestorTip("mf_factsheet", "Read the factsheet", "Check expense ratio, concentration, and exit load before chasing past returns.", "process"),
        InvestorTip("mf_overlap", "Watch portfolio overlap", "Three large-cap funds can be the same Nifty names thrice.", "risk"),
    ),
    "ipo" to listOf(
        InvestorTip("ipo_drhp", "Read risk factors first", "DRHP risk factors matter more than grey-market premium chatter.", "process"),
        InvestorTip("ipo_valuation", "Anchor vs listed peers", "Compare growth and margins to peers — listing gains are uncertain.", "risk"),
        InvestorTip("ipo_hype", "GMP isn't a guarantee", "Grey market premium is informal and can vanish overnight.", "risk"),
    ),
    "fno" to listOf(
        InvestorTip("fo_margin", "Margin ≠ capital at risk", "Size from max loss, not how many lots the margin allows.", "risk"),
        InvestorTip("fo_expiry", "Respect expiry & CAS", "Theta accelerates near expiry; know broker square-off and CAS clocks.", "session"),
        InvestorTip("fo_liquidity", "Trade liquid strikes", "Wide spreads and thin OI amplify slippage — prefer real depth.", "risk"),
    ),
    "sgb" to listOf(
        InvestorTip("sgb_horizon", "Treat SGB as multi-year gold", "Plan for the tenor and limited early exit — not a swing-trade ticket.", "process"),
        InvestorTip("sgb_vs_etf", "Compare SGB vs gold ETF", "SGB may pay interest; ETFs trade freer but charge TER and pay no coupon.", "process"),
        InvestorTip("sgb_liquidity", "Secondary liquidity can be thin", "Exchange volumes for older series can be sparse — size only what you can hold.", "risk"),
        InvestorTip("sgb_tax", "Verify tax before you buy", "Interest is usually taxable; maturity CG treatment has often differed from physical gold — confirm current rules.", "risk"),
        InvestorTip("sgb_allocation", "Cap gold as a sleeve", "Gold is diversifier, not a full equity replacement. Keep it a planned % of net worth.", "process"),
    ),
)

fun localInvestorTips(topic: String, limit: Int = 3): InvestorTipsResponse {
    val key = when (topic.trim().lowercase().replace("-", "_")) {
        "mf", "mutual_fund", "mutual_funds", "funds" -> "mutual_funds"
        "ipos", "ipo", "listing" -> "ipo"
        "fo", "fno", "futures", "options", "derivatives" -> "fno"
        "sgb", "sovereign_gold", "sovereign_gold_bond", "sovereign_gold_bonds", "gold_bond", "gold_bonds" -> "sgb"
        else -> "long_term"
    }
    val label = DEFAULT_TOPICS.firstOrNull { it.id == key }?.label ?: "Long-term"
    return InvestorTipsResponse(
        topic = key,
        topicLabel = label,
        tips = (LOCAL_BANKS[key] ?: LOCAL_BANKS.getValue("long_term")).take(limit),
        topics = DEFAULT_TOPICS,
        disclaimer = "Educational investor habits — not stock, fund, IPO, or SGB recommendations.",
    )
}
