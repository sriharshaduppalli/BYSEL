package com.bysel.trader.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local day-scoped habit state for Idea → Paper trade → Review.
 * IST calendar day so "today" matches Indian market sessions.
 */
object PracticeHabitStore {
    private const val PREFS = "bysel_practice_habit"
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }

    data class DayState(
        val dateKey: String,
        val ideaSeen: Boolean = false,
        val tradedSymbol: String? = null,
        val alertSet: Boolean = false,
        val reviewed: Boolean = false,
        val setSl: Boolean = false,
        val followedPlan: Boolean = false,
    ) {
        val tradeDone: Boolean get() = !tradedSymbol.isNullOrBlank()
        /** 0–3 habit score for the day. */
        val score: Int
            get() = listOf(ideaSeen, tradeDone || alertSet, reviewed).count { it }
    }

    fun todayKey(): String = dayFormat.format(Date())

    fun load(context: Context): DayState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayKey()
        val storedDay = prefs.getString("date_key", null)
        if (storedDay != today) {
            return DayState(dateKey = today)
        }
        return DayState(
            dateKey = today,
            ideaSeen = prefs.getBoolean("idea_seen", false),
            tradedSymbol = prefs.getString("traded_symbol", null)?.takeIf { it.isNotBlank() },
            alertSet = prefs.getBoolean("alert_set", false),
            reviewed = prefs.getBoolean("reviewed", false),
            setSl = prefs.getBoolean("set_sl", false),
            followedPlan = prefs.getBoolean("followed_plan", false),
        )
    }

    private fun save(context: Context, state: DayState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("date_key", state.dateKey)
            .putBoolean("idea_seen", state.ideaSeen)
            .putString("traded_symbol", state.tradedSymbol.orEmpty())
            .putBoolean("alert_set", state.alertSet)
            .putBoolean("reviewed", state.reviewed)
            .putBoolean("set_sl", state.setSl)
            .putBoolean("followed_plan", state.followedPlan)
            .apply()
    }

    fun markIdeaSeen(context: Context): DayState {
        val current = load(context)
        if (current.ideaSeen) return current
        val next = current.copy(ideaSeen = true)
        save(context, next)
        return next
    }

    fun markTraded(context: Context, symbol: String): DayState {
        val current = load(context)
        val next = current.copy(
            ideaSeen = true,
            tradedSymbol = symbol.uppercase(Locale.US),
        )
        save(context, next)
        return next
    }

    fun markAlertSet(context: Context, symbol: String? = null): DayState {
        val current = load(context)
        val next = current.copy(
            ideaSeen = true,
            alertSet = true,
            tradedSymbol = current.tradedSymbol ?: symbol?.uppercase(Locale.US),
        )
        save(context, next)
        return next
    }

    fun markReviewed(
        context: Context,
        setSl: Boolean,
        followedPlan: Boolean,
    ): DayState {
        val current = load(context)
        val next = current.copy(
            reviewed = true,
            setSl = setSl,
            followedPlan = followedPlan,
            ideaSeen = true,
        )
        save(context, next)
        return next
    }
}
