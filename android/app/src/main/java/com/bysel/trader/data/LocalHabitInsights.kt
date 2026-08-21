package com.bysel.trader.data

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.IntradayTip
import com.bysel.trader.data.models.IntradayTipsResponse
import com.bysel.trader.data.models.InvestorTip
import com.bysel.trader.data.models.InvestorTipsResponse

/**
 * Device-local habit cards from Today's Practice, paper holdings, and watchlist.
 * Complements backend scoring (which sees the full paper-order log).
 * Educational copy only — never a buy/sell call.
 */
object LocalHabitInsights {

    fun sessionHabits(
        habit: PracticeHabitStore.DayState,
        progress: PracticeHabitStore.Progress,
        holdings: List<Holding>,
        watchlistSize: Int,
    ): List<IntradayTip> {
        val tips = mutableListOf<IntradayTip>()
        if (habit.tradeDone && !habit.reviewed) {
            tips += tip(
                id = "local_review_due",
                title = "Paper fill waiting on review",
                body = "You paper-traded ${habit.tradedSymbol ?: "a name"} today but have not closed the loop. " +
                    "Tag stop used / plan followed — that is the habit, not the P&L.",
                category = "process",
                evidence = "${habit.tradedSymbol ?: "paper fill"} · review pending",
            )
        } else if (!habit.tradeDone && !habit.alertSet && habit.score == 0) {
            tips += tip(
                id = "local_unused_today",
                title = "Today's practice loop is unused",
                body = "Idea → paper trade (or Alert @ SL) → Review is still 0/3. " +
                    "One reviewed paper ticket teaches more than scrolling the heatmap.",
                category = "process",
                evidence = "score ${habit.score}/3 today (IST)",
            )
        } else if (habit.alertSet && !habit.tradeDone) {
            tips += tip(
                id = "local_alert_first",
                title = "Stop marked before a fill",
                body = "You set an alert without a paper fill — a risk-first habit. " +
                    "If you later paper-trade, keep that same invalidation.",
                category = "risk",
                evidence = "Alert set · no paper fill yet",
            )
        }

        progress.slDisciplinePct?.let { pct ->
            if (progress.reviewsCompleted >= 3 && pct < 50) {
                tips += tip(
                    id = "local_sl_weak",
                    title = "Stop discipline is below half",
                    body = "In ${progress.reviewsCompleted} reviews, SL was marked on ${progress.slRespected} " +
                        "($pct%). A stop is where the thesis dies — skip the ticket if you cannot place one.",
                    category = "risk",
                    evidence = "SL $pct% (${progress.slRespected}/${progress.reviewsCompleted})",
                )
            }
        }

        if (holdings.size == 1 && habit.tradeDone) {
            val symbol = holdings.first().symbol
            tips += tip(
                id = "local_one_name",
                title = "Practice book is a single name",
                body = "$symbol is the only open paper holding. Fine for a drill — " +
                    "repeat tickets in one name without a new thesis is usually overtrading.",
                category = "risk",
                evidence = "1 paper holding · $symbol",
            )
        }

        if (watchlistSize >= 12 && !habit.tradeDone) {
            tips += tip(
                id = "local_watchlist_bloat",
                title = "Watchlist is crowded vs today's fills",
                body = "You are watching $watchlistSize names and have no paper fill today. " +
                    "Crowded lists feed FOMO entries — keep 5–8 liquid names with a written level.",
                category = "process",
                evidence = "$watchlistSize watchlist names · 0 paper fills today",
            )
        }
        return tips
    }

    fun investorHabits(
        habit: PracticeHabitStore.DayState,
        progress: PracticeHabitStore.Progress,
        holdings: List<Holding>,
        watchlistSize: Int,
        topic: String,
    ): List<InvestorTip> {
        val tips = mutableListOf<InvestorTip>()
        val bookValue = holdings.sumOf { it.qty * (if (it.last > 0) it.last else it.avgPrice) }
        if (holdings.size >= 2 && bookValue > 0) {
            val top = holdings.maxByOrNull { it.qty * (if (it.last > 0) it.last else it.avgPrice) }
            if (top != null) {
                val share = (top.qty * (if (top.last > 0) top.last else top.avgPrice)) / bookValue
                if (share >= 0.40) {
                    tips += investorTip(
                        id = "local_book_weight",
                        title = "Paper book heavy in ${top.symbol}",
                        body = "${top.symbol} is about ${(share * 100).toInt()}% of your paper book " +
                            "(${holdings.size} names). Match that weight to a written horizon — " +
                            "not to the last heatmap mover.",
                        category = "risk",
                        evidence = "${top.symbol} ≈ ${(share * 100).toInt()}% of paper book",
                    )
                }
            }
        } else if (holdings.isEmpty() && !habit.tradeDone && progress.reviewsCompleted == 0) {
            tips += investorTip(
                id = "local_empty_book",
                title = "Practice book is empty",
                body = "No paper holdings and no completed reviews yet. " +
                    "Investor habits here describe your practice book — not live demat. " +
                    "Run Today's Practice once so the next cards can be specific.",
                category = "process",
                evidence = "0 paper holdings · 0 reviews",
            )
        }

        if (watchlistSize >= 15 && holdings.isEmpty()) {
            tips += investorTip(
                id = "local_watch_no_book",
                title = "Watching names you have not practiced",
                body = "$watchlistSize watchlist names and an empty paper book. " +
                    "A short list with a thesis beats a long unused list.",
                category = "process",
                evidence = "$watchlistSize watched · 0 paper holdings",
            )
        }

        val key = topic.trim().lowercase()
        if (key in setOf("long_term", "mutual_funds", "sgb") && habit.tradeDone && habit.reviewed) {
            tips += investorTip(
                id = "local_horizon_note",
                title = "Keep today's drill separate from the plan",
                body = "You completed a paper loop today while this card is on a long-horizon topic. " +
                    "Intraday practice and multi-year allocation use different risk rules — " +
                    "do not copy today's ticket size into a SIP or SGB sleeve.",
                category = "process",
                evidence = "Practice loop done · $key topic",
            )
        }
        if (key == "fno" && habit.tradeDone) {
            tips += investorTip(
                id = "local_fno_vs_spot",
                title = "Spot paper fill vs the F&O tab",
                body = "Today's paper ticket is a cash-style drill. Futures and options use lots, " +
                    "expiry clocks, and max-loss sizing — do not copy this quantity into a contract.",
                category = "risk",
                evidence = "Paper fill today · F&O topic",
            )
        }
        if (key == "ipo" && holdings.isNotEmpty()) {
            tips += investorTip(
                id = "local_ipo_vs_book",
                title = "IPO explorer vs an open paper book",
                body = "You have ${holdings.size} open paper name(s). BYSEL IPO apply is practice ASBA only — " +
                    "listing hype should not resize the equity book.",
                category = "process",
                evidence = "${holdings.size} paper holdings · IPO topic",
            )
        }
        if (key == "sgb") {
            val goldish = holdings.any { holding ->
                val token = holding.symbol.uppercase()
                token.contains("GOLD") || token.contains("SGB") || token.contains("SILVER")
            }
            if (!goldish && (habit.tradeDone || holdings.isNotEmpty())) {
                tips += investorTip(
                    id = "local_sgb_vs_equity",
                    title = "SGB card vs equity paper activity",
                    body = "The paper book has no gold/SGB name. SGB is a multi-year sleeve — " +
                        "do not treat today's stock drill as a gold allocation.",
                    category = "process",
                    evidence = "0 gold/SGB holdings · SGB topic",
                )
            }
        }
        val underwater = holdings.filter { it.last > 0 && it.avgPrice > 0 && it.last < it.avgPrice * 0.97 }
        if (key == "long_term" && underwater.size >= 2) {
            val names = underwater.take(3).joinToString(", ") { it.symbol }
            tips += investorTip(
                id = "local_book_underwater",
                title = "More than one paper name is below cost",
                body = "${underwater.size} open paper names sit below average cost ($names). " +
                    "Marks use last vs avg on the device book — not a live broker P&L.",
                category = "risk",
                evidence = "${underwater.size} paper names below avg cost",
            )
        }
        return tips
    }

    fun mergeSession(
        remote: IntradayTipsResponse?,
        local: List<IntradayTip>,
        fallback: IntradayTipsResponse,
        limit: Int = 4,
    ): IntradayTipsResponse {
        val base = remote ?: fallback
        val remotePaper = base.tips.filter { it.source.equals("paper", ignoreCase = true) }
        val remoteEdu = base.tips.filter { !it.source.equals("paper", ignoreCase = true) }
        val seen = linkedSetOf<String>()
        val merged = (remotePaper + local + remoteEdu).filter { tip ->
            tip.id.isNotBlank() && seen.add(tip.id)
        }.take(limit)
        return base.copy(tips = merged)
    }

    fun mergeInvestor(
        remote: InvestorTipsResponse,
        local: List<InvestorTip>,
        limit: Int = 4,
    ): InvestorTipsResponse {
        val remotePaper = remote.tips.filter { it.source.equals("paper", ignoreCase = true) }
        val remoteEdu = remote.tips.filter { !it.source.equals("paper", ignoreCase = true) }
        val seen = linkedSetOf<String>()
        val merged = (remotePaper + local + remoteEdu).filter { tip ->
            tip.id.isNotBlank() && seen.add(tip.id)
        }.take(limit)
        return remote.copy(tips = merged)
    }

    private fun tip(
        id: String,
        title: String,
        body: String,
        category: String,
        evidence: String,
    ) = IntradayTip(
        id = id,
        title = title,
        body = body,
        category = category,
        source = "paper",
        evidence = evidence,
    )

    private fun investorTip(
        id: String,
        title: String,
        body: String,
        category: String,
        evidence: String,
    ) = InvestorTip(
        id = id,
        title = title,
        body = body,
        category = category,
        source = "paper",
        evidence = evidence,
    )
}
