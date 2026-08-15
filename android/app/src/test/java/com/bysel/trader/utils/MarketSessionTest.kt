package com.bysel.trader.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MarketSessionTest {
    private val ist: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun istMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(ist).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun casWeekdayMidSessionIsOpen() {
        // Friday 14 Aug 2026 11:00 IST
        assertTrue(MarketSession.isOpen(istMillis(2026, Calendar.AUGUST, 14, 11, 0)))
    }

    @Test
    fun casWindowStillOpenAt1532() {
        // Tuesday 4 Aug 2026 15:32 IST — CAS / F&O still live
        assertTrue(MarketSession.isOpen(istMillis(2026, Calendar.AUGUST, 4, 15, 32)))
    }

    @Test
    fun closedAfterFoDerivatives() {
        assertFalse(MarketSession.isOpen(istMillis(2026, Calendar.AUGUST, 4, 15, 41)))
    }

    @Test
    fun weekendIsClosed() {
        // Saturday 15 Aug 2026
        assertFalse(MarketSession.isOpen(istMillis(2026, Calendar.AUGUST, 15, 11, 0)))
    }

    @Test
    fun preCasCloseIs1530() {
        // Friday 31 Jul 2026 15:35 IST — still legacy 15:30 close
        assertFalse(MarketSession.isOpen(istMillis(2026, Calendar.JULY, 31, 15, 35)))
        assertTrue(MarketSession.isOpen(istMillis(2026, Calendar.JULY, 31, 15, 29)))
    }

    @Test
    fun holidayPhaseIsNotAfterHours() {
        val phase = MarketSession.phase(
            nowMs = istMillis(2026, Calendar.AUGUST, 14, 11, 0),
            isHoliday = true,
        )
        assertEquals("holiday", phase.id)
        assertFalse(phase.isOpen)
    }

    @Test
    fun firstHourAndClosingWindow() {
        val first = MarketSession.phase(istMillis(2026, Calendar.AUGUST, 4, 9, 40))
        assertEquals("first_hour", first.id)
        val close = MarketSession.phase(istMillis(2026, Calendar.AUGUST, 4, 15, 10))
        assertEquals("closing_window", close.id)
        val after = MarketSession.phase(istMillis(2026, Calendar.AUGUST, 4, 16, 0))
        assertEquals("after_hours", after.id)
    }
}
