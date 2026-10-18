package com.bysel.trader.portfolio

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Marks a booked paper option or future from the underlying price.
 * Option prices use a fixed 16% volatility. They are not traded option prints.
 */
object PaperFoMark {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val MONTHS = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )
    private val OPTION = Regex(
        "^([A-Z][A-Z0-9]*?)(\\d{2})(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(CE|PE)(\\d+)$",
    )
    private val FUTURE = Regex(
        "^([A-Z][A-Z0-9]*?)(\\d{2})(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)FUT$",
    )

    fun note(symbol: String): String? {
        val parsed = parse(symbol) ?: return null
        return if (parsed.call == null) {
            "Marked from the underlying price. Not the futures contract last."
        } else {
            "Marked from the underlying price at 16% volatility. Not a traded option price."
        }
    }

    fun apply(holding: Holding, quotes: List<Quote>): Holding {
        val parsed = parse(holding.symbol) ?: return holding
        val spot = underlyingLast(parsed.root, quotes)
        if (spot <= 0.0) return holding
        val last = if (parsed.call == null) {
            spot
        } else {
            premium(spot, parsed.strike, daysUntil(parsed), 0.16, parsed.call)
        }
        val pnl = (last - holding.avgPrice) * holding.qty
        if (holding.last == last && holding.pnl == pnl) return holding
        return holding.copy(last = last, pnl = pnl)
    }

    fun premium(spot: Double, strike: Double, days: Double, iv: Double, call: Boolean): Double {
        if (spot <= 0.0 || strike <= 0.0) return 0.0
        val t = days.coerceAtLeast(1.0) / 365.0
        val vol = iv.coerceIn(0.08, 0.80)
        val sqrtT = sqrt(t)
        val d1 = (ln(spot / strike) + (0.065 + 0.5 * vol * vol) * t) / (vol * sqrtT)
        val d2 = d1 - vol * sqrtT
        val discount = exp(-0.065 * t)
        val price = if (call) {
            spot * normCdf(d1) - strike * discount * normCdf(d2)
        } else {
            strike * discount * normCdf(-d2) - spot * normCdf(-d1)
        }
        return price.coerceAtLeast(0.05)
    }

    private data class Parsed(
        val root: String,
        val day: Int,
        val month: Int,
        val call: Boolean?,
        val strike: Double,
    )

    private fun parse(symbol: String): Parsed? {
        val raw = symbol.trim().uppercase()
        OPTION.matchEntire(raw)?.let { match ->
            val month = MONTHS.indexOf(match.groupValues[3]) + 1
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val strike = match.groupValues[5].toDoubleOrNull() ?: return null
            if (month <= 0 || day !in 1..31 || strike <= 0.0) return null
            return Parsed(match.groupValues[1], day, month, match.groupValues[4] == "CE", strike)
        }
        FUTURE.matchEntire(raw)?.let { match ->
            val month = MONTHS.indexOf(match.groupValues[3]) + 1
            val day = match.groupValues[2].toIntOrNull() ?: return null
            if (month <= 0 || day !in 1..31) return null
            return Parsed(match.groupValues[1], day, month, null, 0.0)
        }
        return null
    }

    private fun daysUntil(parsed: Parsed): Double {
        val today = LocalDate.now(IST)
        val date = runCatching { LocalDate.of(today.year, parsed.month, parsed.day) }.getOrNull() ?: return 7.0
        val expiry = if (date.isBefore(today)) date.plusYears(1) else date
        return ChronoUnit.DAYS.between(today, expiry).toDouble().coerceAtLeast(1.0)
    }

    private fun underlyingLast(root: String, quotes: List<Quote>): Double {
        val keys = when (root) {
            "NIFTY" -> listOf("NIFTY50", "NIFTY", "^NSEI")
            "BANKNIFTY" -> listOf("BANKNIFTY", "NIFTYBANK")
            else -> listOf(root)
        }
        val bySymbol = quotes.associateBy { it.symbol.trim().uppercase() }
        return keys.firstNotNullOfOrNull { key -> bySymbol[key]?.last?.takeIf { it > 0.0 } } ?: 0.0
    }

    private fun normCdf(x: Double): Double = 0.5 * (1.0 + erf(x / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x >= 0.0) 1.0 else -1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }
}
