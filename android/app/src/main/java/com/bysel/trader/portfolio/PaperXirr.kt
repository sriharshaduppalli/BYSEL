package com.bysel.trader.portfolio

import com.bysel.trader.data.api.TradeHistory
import kotlin.math.abs
import kotlin.math.pow

/**
 * Money-weighted annual return of the practice book.
 * Buys (plus charges) are cash out. Sells net of charges are cash in.
 * Today's open-position value is the closing cash-in. Wallet cash is not a return.
 */
object PaperXirr {
    fun annualizedPercent(
        trades: List<TradeHistory>,
        openValue: Double,
        nowMs: Long = System.currentTimeMillis(),
    ): Double? {
        val flows = ArrayList<Flow>(trades.size + 1)
        for (trade in trades) {
            if (trade.timestamp <= 0L || trade.quantity == 0) continue
            val buy = trade.side.equals("BUY", ignoreCase = true)
            val cash = if (buy) -(trade.total + trade.charges) else (trade.total - trade.charges)
            if (cash != 0.0) flows.add(Flow(trade.timestamp, cash))
        }
        if (openValue > 0.0) flows.add(Flow(nowMs, openValue))
        if (flows.size < 2) return null
        val start = flows.minOf { it.epochMs }
        val end = flows.maxOf { it.epochMs }
        if (end - start < DAY_MS) return null
        if (flows.none { it.amount < 0.0 } || flows.none { it.amount > 0.0 }) return null
        val rate = solve(flows) ?: return null
        return rate * 100.0
    }

    private data class Flow(val epochMs: Long, val amount: Double)

    private fun solve(flows: List<Flow>): Double? {
        val origin = flows.minOf { it.epochMs }
        fun years(ms: Long) = (ms - origin).toDouble() / YEAR_MS
        var rate = 0.10
        repeat(60) {
            var npv = 0.0
            var slope = 0.0
            for (flow in flows) {
                val yearsHeld = years(flow.epochMs)
                val base = 1.0 + rate
                if (base <= 0.0) return null
                val discount = base.pow(yearsHeld)
                if (discount == 0.0 || discount.isNaN()) return null
                npv += flow.amount / discount
                slope += -yearsHeld * flow.amount / (discount * base)
            }
            if (abs(npv) < 0.01) return rate
            if (slope == 0.0 || slope.isNaN()) return null
            val next = rate - npv / slope
            if (next <= -0.99 || next.isNaN() || next.isInfinite()) return null
            if (abs(next - rate) < 1e-7) return next
            rate = next
        }
        return null
    }

    private const val DAY_MS = 86_400_000.0
    private const val YEAR_MS = 365.0 * DAY_MS
}
