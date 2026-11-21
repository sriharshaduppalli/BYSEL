package com.bysel.trader.utils

import com.bysel.trader.data.models.HistoryCandle
import java.time.Instant
import java.time.ZoneId
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

    /**
     * Heikin-Ashi bars from ordinary candles.
     *
     * Each bar averages the move so a trend reads as a run of one colour. Timestamps and
     * volume are kept so the same x-axis and indicators still line up; indicators should
     * still be computed on the real closes, not on these.
     */
    fun heikinAshi(candles: List<HistoryCandle>): List<HistoryCandle> {
        if (candles.isEmpty()) return emptyList()
        val out = ArrayList<HistoryCandle>(candles.size)
        var prevOpen = (candles[0].open + candles[0].close) / 2.0
        var prevClose = (candles[0].open + candles[0].high + candles[0].low + candles[0].close) / 4.0
        candles.forEachIndexed { index, c ->
            val haClose = (c.open + c.high + c.low + c.close) / 4.0
            val haOpen = if (index == 0) (c.open + c.close) / 2.0 else (prevOpen + prevClose) / 2.0
            val haHigh = maxOf(c.high, haOpen, haClose)
            val haLow = minOf(c.low, haOpen, haClose)
            out.add(c.copy(open = haOpen, high = haHigh, low = haLow, close = haClose))
            prevOpen = haOpen
            prevClose = haClose
        }
        return out
    }

    data class SupertrendPoint(
        val value: Double,
        val bullish: Boolean,
    )

    /**
     * VWAP resets each IST session on intraday candles (1D/5D), and switches to a
     * 20-bar rolling VWAP on daily/weekly candles so daily bars never collapse to typical price.
     */
    fun vwap(candles: List<HistoryCandle>, rollingWindow: Int = 20): List<Double?> {
        if (candles.isEmpty()) return emptyList()
        val result = MutableList<Double?>(candles.size) { null }
        fun epochMs(ts: Long): Long = when {
            ts <= 0L -> 0L
            ts < 10_000_000_000L -> ts * 1000L
            else -> ts
        }
        val deltas = buildList {
            for (i in 1 until candles.size) {
                val prev = epochMs(candles[i - 1].timestamp)
                val curr = epochMs(candles[i].timestamp)
                if (prev > 0L && curr > prev) add(curr - prev)
            }
        }.sorted()
        val intradaySteps = deltas.count { it > 0L && it < 6L * 3600L * 1000L }
        val isDailyOrWider = if (deltas.isNotEmpty()) {
            intradaySteps * 2 < deltas.size
        } else {
            candles.size >= 2
        }

        if (isDailyOrWider) {
            val win = rollingWindow.coerceAtLeast(1)
            for (i in candles.indices) {
                val start = (i - win + 1).coerceAtLeast(0)
                var cumVolume = 0L
                var cumTP = 0.0
                for (j in start..i) {
                    val c = candles[j]
                    val tp = (c.high + c.low + c.close) / 3.0
                    cumVolume += c.volume
                    cumTP += tp * c.volume
                }
                result[i] = if (cumVolume > 0L) cumTP / cumVolume else null
            }
            return result
        }

        val zone = ZoneId.of("Asia/Kolkata")
        var day = Long.MIN_VALUE
        var cumVolume = 0L
        var cumTP = 0.0
        for (i in candles.indices) {
            val c = candles[i]
            val ms = epochMs(c.timestamp)
            val session = if (ms <= 0L) day else Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
            if (session != day) {
                day = session
                cumVolume = 0L
                cumTP = 0.0
            }
            val tp = (c.high + c.low + c.close) / 3.0
            cumVolume += c.volume
            cumTP += tp * c.volume
            result[i] = if (cumVolume > 0) cumTP / cumVolume else null
        }
        return result
    }

    /**
     * Wilder ATR-based Supertrend (default 10, 3.0) matching ISM analysis_math.supertrend.
     */
    fun supertrend(
        candles: List<HistoryCandle>,
        period: Int = 10,
        multiplier: Double = 3.0,
    ): List<SupertrendPoint?> {
        val n = candles.size
        val result = MutableList<SupertrendPoint?>(n) { null }
        if (n < period + 2 || period <= 0) return result

        val trs = DoubleArray(n)
        trs[0] = candles[0].high - candles[0].low
        for (i in 1 until n) {
            val h = candles[i].high
            val l = candles[i].low
            val prevC = candles[i - 1].close
            trs[i] = maxOf(h - l, kotlin.math.abs(h - prevC), kotlin.math.abs(l - prevC))
        }

        val atrSeries = DoubleArray(n)
        var atrVal = 0.0
        for (i in 0 until period) atrVal += trs[i]
        atrVal /= period.toDouble()
        atrSeries[period - 1] = atrVal
        for (i in period until n) {
            atrVal = (atrVal * (period - 1) + trs[i]) / period.toDouble()
            atrSeries[i] = atrVal
        }

        val finalUpper = DoubleArray(n)
        val finalLower = DoubleArray(n)
        var bullish = true

        for (i in (period - 1) until n) {
            val hl2 = (candles[i].high + candles[i].low) / 2.0
            val atr = atrSeries[i]
            val basicUpper = hl2 + multiplier * atr
            val basicLower = hl2 - multiplier * atr
            if (i == period - 1) {
                finalUpper[i] = basicUpper
                finalLower[i] = basicLower
                bullish = candles[i].close >= basicLower
            } else {
                val prevFu = finalUpper[i - 1]
                val prevFl = finalLower[i - 1]
                finalUpper[i] = if (basicUpper < prevFu || candles[i - 1].close > prevFu) basicUpper else prevFu
                finalLower[i] = if (basicLower > prevFl || candles[i - 1].close < prevFl) basicLower else prevFl
                bullish = if (bullish) {
                    candles[i].close >= finalLower[i]
                } else {
                    candles[i].close > finalUpper[i]
                }
            }
            val line = if (bullish) finalLower[i] else finalUpper[i]
            result[i] = SupertrendPoint(value = line, bullish = bullish)
        }
        return result
    }
}
