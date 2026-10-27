package com.bysel.trader.data.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarketHoliday2027Test {
    @Test
    fun weekdayNationalHolidaysAreClosed() {
        assertTrue(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2027, 1, 26)))
        assertTrue(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2027, 4, 14)))
    }

    @Test
    fun weekendNationalDaysAreNotAddedAsExtraClosures() {
        assertFalse(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2027, 8, 15)))
        assertFalse(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2027, 10, 2)))
        assertFalse(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2027, 12, 25)))
    }

    @Test
    fun published2026HolidayStillClosesTheMarket() {
        assertTrue(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2026, 11, 11)))
    }
}
