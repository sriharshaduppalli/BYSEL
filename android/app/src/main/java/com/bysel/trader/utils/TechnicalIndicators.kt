package com.bysel.trader.utils

import com.bysel.trader.data.models.HistoryCandle
import kotlin.math.sqrt

object TechnicalIndicators {

    /** Simple Moving Average for a given period. Returns list aligned to input (NaN-padded for initial values). */
    fun sma(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.size < period) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        var sum = closes.subList(0, period).sum()
        result[period - 1] = sum / period
        for (i in period until closes.size) {
            sum += closes[i] - closes[i - period]
            result[i] = sum / period
        }
        return result
    }

    /** Exponential Moving Average. */
    fun ema(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.size < period) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        val multiplier = 2.0 / (period + 1)
        // Seed with SMA
        var prev = closes.subList(0, period).average()
        result[period - 1] = prev
        for (i in period until closes.size) {
            prev = (closes[i] - prev) * multiplier + prev
            result[i] = prev
        }
        return result
    }

    /** RSI (Relative Strength Index) with given period (default 14). */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.size < period + 1) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)
        }
        var avgGain = gains.average()
        var avgLoss = losses.average()
        val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
        result[period] = 100.0 - (100.0 / (1.0 + rs))
        for (i in period + 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            val r = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
            result[i] = 100.0 - (100.0 / (1.0 + r))
        }
        return result
    }

    /** MACD (12, 26, 9). Returns triple: (macdLine, signalLine, histogram). */
    data class MacdResult(
        val macdLine: List<Double?>,
        val signalLine: List<Double?>,
        val histogram: List<Double?>
    )

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): MacdResult {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val macdLine = MutableList<Double?>(closes.size) { null }
        for (i in closes.indices) {
            val f = emaFast[i]
            val s = emaSlow[i]
            macdLine[i] = if (f != null && s != null) f - s else null
        }
        // Signal line: EMA of MACD line
        val macdValues = macdLine.filterNotNull()
        val signalEma = ema(macdValues, signal)
        val signalLine = MutableList<Double?>(closes.size) { null }
        val histogram = MutableList<Double?>(closes.size) { null }
        var macdIdx = 0
        for (i in closes.indices) {
            if (macdLine[i] != null) {
                if (macdIdx < signalEma.size) {
                    signalLine[i] = signalEma[macdIdx]
                    if (signalEma[macdIdx] != null) {
                        histogram[i] = macdLine[i]!! - signalEma[macdIdx]!!
                    }
                }
                macdIdx++
            }
        }
        return MacdResult(macdLine, signalLine, histogram)
    }

    /** Bollinger Bands (default: 20 period, 2 std dev). Returns triple: (upper, middle/SMA, lower). */
    data class BollingerBands(
        val upper: List<Double?>,
        val middle: List<Double?>,
        val lower: List<Double?>
    )

    fun bollingerBands(closes: List<Double>, period: Int = 20, stdDevMultiplier: Double = 2.0): BollingerBands {
        val middle = sma(closes, period)
        val upper = MutableList<Double?>(closes.size) { null }
        val lower = MutableList<Double?>(closes.size) { null }
        for (i in closes.indices) {
            val m = middle[i] ?: continue
            if (i < period - 1) continue
            val window = closes.subList(i - period + 1, i + 1)
            val variance = window.map { (it - m) * (it - m) }.average()
            val stdDev = sqrt(variance)
            upper[i] = m + stdDevMultiplier * stdDev
            lower[i] = m - stdDevMultiplier * stdDev
        }
        return BollingerBands(upper, middle, lower)
    }

    /** VWAP (Volume Weighted Average Price) — intraday only, resets each day. */
    fun vwap(candles: List<HistoryCandle>): List<Double?> {
        if (candles.isEmpty()) return emptyList()
        val result = MutableList<Double?>(candles.size) { null }
        var cumVolume = 0L
        var cumTP = 0.0
        for (i in candles.indices) {
            val c = candles[i]
            val tp = (c.high + c.low + c.close) / 3.0
            cumVolume += c.volume
            cumTP += tp * c.volume
            result[i] = if (cumVolume > 0) cumTP / cumVolume else null
        }
        return result
    }
}
