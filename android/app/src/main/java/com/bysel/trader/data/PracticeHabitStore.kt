package com.bysel.trader.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local day-scoped habit state for Idea → Paper trade → Review.
 * IST calendar day so "today" matches Indian market sessions.
 * Streak / SL discipline counters persist across days (prefs only).
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

    /** Cross-day practice proof shown on Home. */
    data class Progress(
        val streakDays: Int = 0,
        val reviewsCompleted: Int = 0,
        val slRespected: Int = 0,
        val planFollowed: Int = 0,
    ) {
        val slDisciplinePct: Int?
            get() = if (reviewsCompleted <= 0) null
            else ((slRespected * 100.0) / reviewsCompleted).toInt().coerceIn(0, 100)
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

    fun loadProgress(context: Context): Progress {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Progress(
            streakDays = prefs.getInt("streak_count", 0).coerceAtLeast(0),
            reviewsCompleted = prefs.getInt("reviews_completed", 0).coerceAtLeast(0),
            slRespected = prefs.getInt("sl_respected", 0).coerceAtLeast(0),
            planFollowed = prefs.getInt("plan_followed", 0).coerceAtLeast(0),
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
        val alreadyReviewed = current.reviewed
        val next = current.copy(
            reviewed = true,
            setSl = setSl,
            followedPlan = followedPlan,
            ideaSeen = true,
        )
        save(context, next)
        if (!alreadyReviewed) {
            recordReviewProgress(context, setSl = setSl, followedPlan = followedPlan, dayKey = next.dateKey)
        }
        return next
    }

    private fun recordReviewProgress(
        context: Context,
        setSl: Boolean,
        followedPlan: Boolean,
        dayKey: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reviews = prefs.getInt("reviews_completed", 0) + 1
        val slOk = prefs.getInt("sl_respected", 0) + if (setSl) 1 else 0
        val planOk = prefs.getInt("plan_followed", 0) + if (followedPlan) 1 else 0

        val lastCompleted = prefs.getString("last_completed_date", null)
        val streak = when {
            lastCompleted == dayKey -> prefs.getInt("streak_count", 0).coerceAtLeast(1)
            lastCompleted != null && isYesterday(lastCompleted, dayKey) ->
                prefs.getInt("streak_count", 0).coerceAtLeast(0) + 1
            else -> 1
        }

        prefs.edit()
            .putInt("reviews_completed", reviews)
            .putInt("sl_respected", slOk)
            .putInt("plan_followed", planOk)
            .putInt("streak_count", streak)
            .putString("last_completed_date", dayKey)
            .apply()
    }

    private fun isYesterday(previousKey: String, todayKey: String): Boolean {
        return try {
            val today = dayFormat.parse(todayKey) ?: return false
            val cal = Calendar.getInstance(ist).apply {
                time = today
                add(Calendar.DAY_OF_YEAR, -1)
            }
            dayFormat.format(cal.time) == previousKey
        } catch (_: Exception) {
            false
        }
    }
}
