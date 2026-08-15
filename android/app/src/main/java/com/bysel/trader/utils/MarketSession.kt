package com.bysel.trader.utils

import java.util.Calendar
import java.util.TimeZone

/**
 * NSE/BSE cash + F&O live window in IST.
 *
 * From 3 Aug 2026 (CAS): treat the tape as open through the latest equity-related
 * close (F&O derivatives 15:40 IST). Before that date, close is 15:30 IST.
 */
object MarketSession {
    private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private const val OPEN_MINUTE = 9 * 60 + 15
    private const val LEGACY_CLOSE_MINUTE = 15 * 60 + 30
    private const val CAS_CLOSE_MINUTE = 15 * 60 + 40

    data class Phase(
        val id: String,
        val label: String,
        val isOpen: Boolean,
    )

    @JvmStatic
    fun isOpen(nowMs: Long = System.currentTimeMillis()): Boolean {
        val ist = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
        val dow = ist.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val timeInMin = ist.get(Calendar.HOUR_OF_DAY) * 60 + ist.get(Calendar.MINUTE)
        return timeInMin in OPEN_MINUTE..closeMinute(ist)
    }

    /** IST session-habit window. Holidays must be passed in — weekends are detected here. */
    @JvmStatic
    fun phase(nowMs: Long = System.currentTimeMillis(), isHoliday: Boolean = false): Phase {
        val ist = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
        val dow = ist.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            return Phase("weekend", "Weekend", isOpen = false)
        }
        if (isHoliday) {
            return Phase("holiday", "Market holiday", isOpen = false)
        }
        val mins = ist.get(Calendar.HOUR_OF_DAY) * 60 + ist.get(Calendar.MINUTE)
        val close = closeMinute(ist)
        return when {
            mins < 9 * 60 -> Phase("pre_market", "Pre-market", isOpen = false)
            mins < OPEN_MINUTE -> Phase("pre_open", "Pre-open auction", isOpen = false)
            mins > close -> Phase("after_hours", "After hours", isOpen = false)
            mins < 10 * 60 + 15 -> Phase("first_hour", "First hour", isOpen = true)
            mins < 12 * 60 -> Phase("mid_morning", "Mid-morning", isOpen = true)
            mins < 13 * 60 + 30 -> Phase("lunch_lull", "Midday lull", isOpen = true)
            mins < 14 * 60 + 45 -> Phase("afternoon", "Afternoon", isOpen = true)
            else -> Phase("closing_window", "Closing window", isOpen = true)
        }
    }

    private fun closeMinute(ist: Calendar): Int {
        val casGoLive = Calendar.getInstance(IST).apply {
            set(2026, Calendar.AUGUST, 3, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return if (!ist.before(casGoLive)) CAS_CLOSE_MINUTE else LEGACY_CLOSE_MINUTE
    }
}
