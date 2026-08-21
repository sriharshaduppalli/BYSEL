package com.bysel.trader.paper

import kotlin.math.abs
import kotlin.math.floor

/** Paper ticket helper — size from wallet × risk% / (entry − stop). Not a recommended size. */
object PaperPositionSizer {
    fun suggestedQty(
        capital: Double,
        riskPercent: Double,
        entry: Double,
        stop: Double,
    ): Int? {
        if (capital <= 0.0 || riskPercent <= 0.0 || entry <= 0.0 || stop <= 0.0) return null
        val perShare = abs(entry - stop)
        if (perShare < 0.01) return null
        val budget = capital * (riskPercent / 100.0)
        val qty = floor(budget / perShare).toInt()
        return qty.takeIf { it > 0 }
    }

    fun riskBudget(capital: Double, riskPercent: Double): Double =
        if (capital <= 0.0 || riskPercent <= 0.0) 0.0 else capital * (riskPercent / 100.0)
}
