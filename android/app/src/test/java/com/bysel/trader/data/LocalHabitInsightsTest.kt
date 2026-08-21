package com.bysel.trader.data

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.IntradayTip
import com.bysel.trader.data.models.IntradayTipsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHabitInsightsTest {

    @Test
    fun unusedTodayWhenLoopIsEmpty() {
        val tips = LocalHabitInsights.sessionHabits(
            habit = PracticeHabitStore.DayState(dateKey = "2026-08-15"),
            progress = PracticeHabitStore.Progress(),
            holdings = emptyList(),
            watchlistSize = 3,
        )
        assertTrue(tips.any { it.id == "local_unused_today" })
        assertTrue(tips.all { it.source == "paper" })
    }

    @Test
    fun reviewDueAfterPaperFill() {
        val tips = LocalHabitInsights.sessionHabits(
            habit = PracticeHabitStore.DayState(
                dateKey = "2026-08-15",
                ideaSeen = true,
                tradedSymbol = "TCS",
            ),
            progress = PracticeHabitStore.Progress(),
            holdings = emptyList(),
            watchlistSize = 2,
        )
        assertTrue(tips.any { it.id == "local_review_due" && it.body.contains("TCS") })
    }

    @Test
    fun watchlistBloatWithoutFill() {
        val tips = LocalHabitInsights.sessionHabits(
            habit = PracticeHabitStore.DayState(dateKey = "2026-08-15"),
            progress = PracticeHabitStore.Progress(),
            holdings = emptyList(),
            watchlistSize = 16,
        )
        assertTrue(tips.any { it.id == "local_watchlist_bloat" })
    }

    @Test
    fun concentratedPaperBook() {
        val tips = LocalHabitInsights.investorHabits(
            habit = PracticeHabitStore.DayState(dateKey = "2026-08-15"),
            progress = PracticeHabitStore.Progress(),
            holdings = listOf(
                Holding("RELIANCE", qty = 10, avgPrice = 1400.0, last = 1400.0, pnl = 0.0),
                Holding("ITC", qty = 1, avgPrice = 400.0, last = 400.0, pnl = 0.0),
            ),
            watchlistSize = 4,
            topic = "long_term",
        )
        assertTrue(tips.any { it.id == "local_book_weight" && it.body.contains("RELIANCE") })
    }

    @Test
    fun mergeKeepsRemotePaperFirst() {
        val remote = IntradayTipsResponse(
            phase = "first_hour",
            phaseLabel = "First hour",
            tips = listOf(
                IntradayTip("open_cluster", "Clustered", "body", "session", "paper", "6/8"),
                IntradayTip("fh_patience", "Patience", "body", "session", "session"),
            ),
            hasEnoughData = true,
            sampleSize = 8,
        )
        val local = listOf(
            IntradayTip("local_unused_today", "Unused", "body", "process", "paper"),
        )
        val merged = LocalHabitInsights.mergeSession(remote, local, remote, limit = 4)
        assertEquals("open_cluster", merged.tips.first().id)
        assertTrue(merged.tips.any { it.id == "local_unused_today" })
        assertTrue(merged.tips.any { it.id == "fh_patience" })
    }

    @Test
    fun fnoTabFlagsSpotFill() {
        val tips = LocalHabitInsights.investorHabits(
            habit = PracticeHabitStore.DayState(
                dateKey = "2026-08-15",
                ideaSeen = true,
                tradedSymbol = "TCS",
            ),
            progress = PracticeHabitStore.Progress(),
            holdings = emptyList(),
            watchlistSize = 2,
            topic = "fno",
        )
        assertTrue(tips.any { it.id == "local_fno_vs_spot" })
    }
}
