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
        InvestorTip("lt_horizon", "Define the horizon first", "Money needed within 3 years usually shouldn't sit in concentrated equity bets. Match asset risk to time.", "process", "topic"),
        InvestorTip("lt_allocation", "Allocation beats stock-picking", "Equity/debt/gold mix usually drives multi-year outcomes more than swapping one large-cap for another.", "process", "topic"),
        InvestorTip("lt_drawdown", "Plan for drawdowns", "If a 30–40% equity drawdown would force a sale, allocation is too aggressive for your cash needs.", "psychology", "topic"),
        InvestorTip("lt_emergency", "Protect the emergency sleeve", "Keep 6–12 months expenses in liquid/safe assets. Don't count equity funds as emergency cash.", "risk", "topic"),
        InvestorTip("lt_thesis", "Write a one-line thesis", "For every multi-year holding: why own it, what would invalidate it. No thesis → easy panic selling.", "process", "topic"),
    ),
    "mutual_funds" to listOf(
        InvestorTip("mf_pool", "MF = pooled access", "Units give exposure to many securities via an AMC — handy vs buying an index yourself.", "process", "topic"),
        InvestorTip("mf_nav", "NAV is unit price, not quality", "NAV = (assets − liabilities) / units. Judge returns, risk, and TER — not a ‘cheap’ ₹10 NAV.", "process", "topic"),
        InvestorTip("mf_goal", "Fund follows the goal", "Pick category from goal + horizon, then compare funds inside that box.", "process", "topic"),
        InvestorTip("mf_risk_appetite", "Risk = what you won't abandon", "Size equity so a drawdown won't make you stop the SIP.", "psychology", "topic"),
        InvestorTip("mf_fees", "TER + exit load matter", "Annual expense drag and early-exit loads both reduce net wealth.", "risk", "topic"),
        InvestorTip("mf_factsheet", "Read the factsheet", "Check expense ratio, concentration, and exit load before chasing past returns.", "process", "topic"),
        InvestorTip("mf_overlap", "Watch portfolio overlap", "Three large-cap funds can be the same Nifty names thrice.", "risk", "topic"),
    ),
    "ipo" to listOf(
        InvestorTip("ipo_drhp", "Read risk factors first", "DRHP risk factors matter more than grey-market premium chatter.", "process", "topic"),
        InvestorTip("ipo_valuation", "Anchor vs listed peers", "Compare growth and margins to peers — listing gains are uncertain.", "risk", "topic"),
        InvestorTip("ipo_hype", "GMP isn't a guarantee", "Grey market premium is informal and can vanish overnight.", "risk", "topic"),
        InvestorTip("ipo_allotment", "Allotment is a lottery", "Retail quotas are often oversubscribed. Don't lever up hoping for a full allotment.", "psychology", "topic"),
        InvestorTip("ipo_asba", "Practice apply only", "BYSEL IPO apply is paper practice — no real ASBA block, UPI debit, or exchange allotment.", "process", "topic"),
    ),
    "fno" to listOf(
        InvestorTip("fo_margin", "Margin ≠ capital at risk", "Size from max loss, not how many lots the margin allows.", "risk", "topic"),
        InvestorTip("fo_expiry", "Respect expiry & CAS", "Theta accelerates near expiry; F&O cash continuous ~15:15, CAS ~15:35, derivatives ~15:40 IST.", "session", "topic"),
        InvestorTip("fo_liquidity", "Trade liquid strikes", "Wide spreads and thin OI amplify slippage — prefer real depth.", "risk", "topic"),
        InvestorTip("fo_overnight", "Overnight gap risk", "Short options into results/policy can gap through stops. Reduce size or stay flat into binary risk.", "risk", "topic"),
    ),
    "sgb" to listOf(
        InvestorTip("sgb_horizon", "Treat SGB as multi-year gold", "Plan for the tenor and limited early exit — not a swing-trade ticket.", "process", "topic"),
        InvestorTip("sgb_vs_etf", "Compare SGB vs gold ETF", "SGB may pay interest; ETFs trade freer but charge TER and pay no coupon.", "process", "topic"),
        InvestorTip("sgb_liquidity", "Secondary liquidity can be thin", "Exchange volumes for older series can be sparse — size only what you can hold.", "risk", "topic"),
        InvestorTip("sgb_tax", "Verify tax before you buy", "Interest is usually taxable; maturity CG treatment has often differed from physical gold — confirm current rules.", "risk", "topic"),
        InvestorTip("sgb_allocation", "Cap gold as a sleeve", "Gold is diversifier, not a full equity replacement. Keep it a planned % of net worth.", "process", "topic"),
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
        paperNote = "Topic cues only until your paper book has enough fills.",
    )
}
