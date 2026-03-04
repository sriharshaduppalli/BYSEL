package com.bysel.trader.data.models

import java.time.LocalDate

/**
 * Represents an Indian stock market trading holiday
 */
data class MarketHoliday(
    val date: LocalDate,
    val holidayName: String,
    val description: String,
    val exchanges: List<String> = listOf("NSE", "BSE") // Default to both exchanges
)

/**
 * Market holiday calendar manager
 */
object MarketHolidayCalendar {
    
    /**
     * Indian stock market holidays for 2026
     * Source: NSE/BSE official holiday calendar
     * 
     * Note: This is a static list. For production, consider:
     * 1. NSE API: https://www.nseindia.com/api/holiday-master?type=trading
     * 2. BSE API: https://api.bseindia.com/BseIndiaAPI/api/ListofHolidays/w
     * 3. Zerodha/Upstox API endpoints
     * 4. Store in Room DB with periodic sync
     */
    private val holidays2026 = listOf(
        MarketHoliday(
            date = LocalDate.of(2026, 1, 26),
            holidayName = "Republic Day",
            description = "National holiday celebrating India's constitution"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 3, 3),
            holidayName = "Maha Shivaratri",
            description = "Hindu festival dedicated to Lord Shiva"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 3, 25),
            holidayName = "Holi",
            description = "Festival of colors"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 3, 30),
            holidayName = "Id-Ul-Fitr (Ramzan Id)",
            description = "Islamic festival marking end of Ramadan"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 4, 2),
            holidayName = "Mahavir Jayanti",
            description = "Jain festival celebrating birth of Lord Mahavir"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 4, 6),
            holidayName = "Ram Navami",
            description = "Hindu festival celebrating Lord Rama's birth"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 4, 10),
            holidayName = "Good Friday",
            description = "Christian holiday commemorating crucifixion of Jesus"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 4, 14),
            holidayName = "Dr. Baba Saheb Ambedkar Jayanti",
            description = "Birth anniversary of BR Ambedkar"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 5, 1),
            holidayName = "Maharashtra Day",
            description = "Formation day of Maharashtra state",
            exchanges = listOf("BSE") // BSE only
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 6, 7),
            holidayName = "Bakri Id",
            description = "Islamic festival of sacrifice"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 8, 15),
            holidayName = "Independence Day",
            description = "National holiday celebrating independence from British rule"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 9, 5),
            holidayName = "Ganesh Chaturthi",
            description = "Hindu festival celebrating Lord Ganesha"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 10, 2),
            holidayName = "Mahatma Gandhi Jayanti",
            description = "Birth anniversary of Mahatma Gandhi"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 10, 22),
            holidayName = "Dussehra",
            description = "Hindu festival celebrating victory of good over evil"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 11, 11),
            holidayName = "Diwali (Laxmi Pujan)",
            description = "Festival of lights - Main trading holiday"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 11, 12),
            holidayName = "Diwali Balipratipada",
            description = "Day after Diwali"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 11, 30),
            holidayName = "Guru Nanak Jayanti",
            description = "Sikh festival celebrating birth of Guru Nanak"
        ),
        MarketHoliday(
            date = LocalDate.of(2026, 12, 25),
            holidayName = "Christmas",
            description = "Christian festival celebrating birth of Jesus Christ"
        )
    )

    /**
     * Check if a given date is a market holiday
     */
    fun isMarketHoliday(date: LocalDate, exchange: String = "NSE"): Boolean {
        return holidays2026.any { 
            it.date == date && it.exchanges.contains(exchange)
        }
    }

    /**
     * Get holiday info for a specific date
     */
    fun getHolidayInfo(date: LocalDate): MarketHoliday? {
        return holidays2026.find { it.date == date }
    }

    /**
     * Get all holidays in a month
     */
    fun getHolidaysInMonth(year: Int, month: Int): List<MarketHoliday> {
        return holidays2026.filter { 
            it.date.year == year && it.date.monthValue == month 
        }
    }

    /**
     * Get upcoming holidays from today
     */
    fun getUpcomingHolidays(count: Int = 5): List<MarketHoliday> {
        val today = LocalDate.now()
        return holidays2026
            .filter { it.date >= today }
            .sortedBy { it.date }
            .take(count)
    }

    /**
     * Get all holidays for the year
     */
    fun getAllHolidays(): List<MarketHoliday> {
        return holidays2026.sortedBy { it.date }
    }

    /**
     * Check if today is a trading day
     */
    fun isTradingDay(date: LocalDate = LocalDate.now()): Boolean {
        // Weekend check
        if (date.dayOfWeek.value in 6..7) { // Saturday = 6, Sunday = 7
            return false
        }
        
        // Holiday check
        return !isMarketHoliday(date)
    }

    /**
     * Get next trading day
     */
    fun getNextTradingDay(from: LocalDate = LocalDate.now()): LocalDate {
        var nextDay = from.plusDays(1)
        while (!isTradingDay(nextDay)) {
            nextDay = nextDay.plusDays(1)
        }
        return nextDay
    }

    /**
     * Get previous trading day
     */
    fun getPreviousTradingDay(from: LocalDate = LocalDate.now()): LocalDate {
        var prevDay = from.minusDays(1)
        while (!isTradingDay(prevDay)) {
            prevDay = prevDay.minusDays(1)
        }
        return prevDay
    }

    /**
     * Get market status message for display
     */
    fun getMarketStatusMessage(): String {
        val today = LocalDate.now()
        val holiday = getHolidayInfo(today)
        
        return when {
            holiday != null -> "Market closed - ${holiday.holidayName}"
            today.dayOfWeek.value == 6 -> "Market closed - Saturday"
            today.dayOfWeek.value == 7 -> "Market closed - Sunday"
            else -> "Market open today"
        }
    }
}

/**
 * API integration notes:
 * 
 * 1. NSE Holiday Calendar API:
 *    GET https://www.nseindia.com/api/holiday-master?type=trading
 *    Returns JSON with holidays for current year
 * 
 * 2. Implementation in TradingRepository:
 *    ```kotlin
 *    suspend fun fetchMarketHolidays(): Result<List<MarketHoliday>> {
 *        return try {
 *            val response = apiService.getHolidayCalendar()
 *            Result.Success(response.data.map { dto ->
 *                MarketHoliday(
 *                    date = LocalDate.parse(dto.tradingDate),
 *                    holidayName = dto.description,
 *                    description = dto.description
 *                )
 *            })
 *        } catch (e: Exception) {
 *            Result.Error(e.message ?: "Failed to fetch holidays")
 *        }
 *    }
 *    ```
 * 
 * 3. Room DB storage:
 *    ```kotlin
 *    @Entity(tableName = "market_holidays")
 *    data class MarketHolidayEntity(
 *        @PrimaryKey val date: String,
 *        val holidayName: String,
 *        val description: String,
 *        val exchanges: String // comma-separated
 *    )
 *    ```
 * 
 * 4. Sync strategy:
 *    - Fetch holidays once per month
 *    - Cache in Room DB
 *    - WorkManager periodic sync (first of every month)
 *    - Fallback to hardcoded list if API fails
 */
