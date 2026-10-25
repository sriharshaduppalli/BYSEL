package com.bysel.trader.data.fno

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.round

/**
 * Exchange contract facts the practice desks must agree on with the backend
 * (`backend/app/fo_contracts.py`): lot per contract and expiry weekday.
 *
 * Index lots follow NSE/FAOP/70616 (3 Oct 2025), in force for every contract since
 * 31 Dec 2025. NSE index weeklies expire on Tuesday; monthly index and stock
 * contracts on the last Tuesday of the month. Reviewed 2026-10.
 */
object FoContractFacts {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val EXPIRY_DAY = DayOfWeek.TUESDAY
    private val WEEKLY_INDEXES = setOf("NIFTY", "NIFTY50")

    val KNOWN_LOT_SIZES: Map<String, Int> = mapOf(
        "NIFTY" to 65,
        "NIFTY50" to 65,
        "BANKNIFTY" to 30,
        "NIFTYBANK" to 30,
        "FINNIFTY" to 60,
        "MIDCPNIFTY" to 120,
        "NIFTYNXT50" to 25,
        "SENSEX" to 20,
        "RELIANCE" to 500,
        "TCS" to 225,
        "INFY" to 400,
        "SBIN" to 750,
        "HDFCBANK" to 650,
        "AXISBANK" to 625,
        "BAJFINANCE" to 750,
        "BHARTIARTL" to 475,
        "ADANIENT" to 309,
        "ASIANPAINT" to 250,
        "BEL" to 1425,
        "BPCL" to 1975,
        "CIPLA" to 425,
        "COALINDIA" to 1350,
        "DLF" to 950,
        "DRREDDY" to 625,
        "EICHERMOT" to 100,
        "GOLDM" to 10,
    )

    /** Underlyings offered as one-tap practice picks. */
    val PRACTICE_PICKS: List<String> = listOf(
        "NIFTY", "BANKNIFTY", "FINNIFTY", "RELIANCE", "HDFCBANK", "TCS", "INFY", "SBIN",
    )

    fun lotSize(symbol: String, spot: Double): Int {
        KNOWN_LOT_SIZES[symbol.trim().uppercase()]?.let { return it }
        if (spot <= 0.0) return 100
        val raw = round(120_000.0 / spot).toInt()
        return max(10, ((raw + 4) / 5) * 5)
    }

    fun isWeeklyIndex(symbol: String): Boolean = symbol.trim().uppercase() in WEEKLY_INDEXES

    /** Next NIFTY weekly expiry (Tuesday); today counts when today is expiry day. */
    fun nextWeeklyExpiry(today: LocalDate = LocalDate.now(IST)): LocalDate {
        val ahead = (EXPIRY_DAY.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(ahead.toLong())
    }

    /** Last Tuesday of the month. */
    fun monthlyExpiry(month: YearMonth): LocalDate {
        val last = month.atEndOfMonth()
        val back = (last.dayOfWeek.value - EXPIRY_DAY.value + 7) % 7
        return last.minusDays(back.toLong())
    }

    /** Weekly Tuesday for NIFTY; current or next month's last Tuesday otherwise. */
    fun nextExpiryFor(symbol: String, today: LocalDate = LocalDate.now(IST)): LocalDate {
        if (isWeeklyIndex(symbol)) return nextWeeklyExpiry(today)
        val thisMonth = monthlyExpiry(YearMonth.from(today))
        return if (!thisMonth.isBefore(today)) thisMonth else monthlyExpiry(YearMonth.from(today).plusMonths(1))
    }

    /** Near, next and far monthly expiries from today. */
    fun monthlyCycle(count: Int, today: LocalDate = LocalDate.now(IST)): List<LocalDate> {
        val first = nextExpiryFor("STOCK", today)
        val month = YearMonth.from(first)
        return List(count) { index ->
            if (index == 0) first else monthlyExpiry(month.plusMonths(index.toLong()))
        }
    }

    fun expiryNote(symbol: String): String =
        if (isWeeklyIndex(symbol)) {
            "NIFTY weekly contracts expire on Tuesday; monthly on the last Tuesday."
        } else {
            "This contract expires on the last Tuesday of its month (earlier if that is a holiday)."
        }
}
