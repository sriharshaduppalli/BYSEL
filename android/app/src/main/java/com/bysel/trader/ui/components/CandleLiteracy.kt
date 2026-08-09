package com.bysel.trader.ui.components

import com.bysel.trader.data.models.HistoryCandle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight candlestick literacy labels derived from the OHLC already on the chart.
 * Educational paper-practice cues only — not trade signals.
 */
data class CandleLesson(
    val name: String,
    val bias: String, // Bullish / Bearish / Neutral
    val summary: String,
    val learnQuery: String,
    val barIndex: Int,
)

object CandleLiteracyDetector {

    fun detectRecent(history: List<HistoryCandle>, lookback: Int = 8): List<CandleLesson> {
        if (history.size < 2) return emptyList()
        val end = history.lastIndex
        val start = max(1, end - lookback + 1)
        val found = linkedMapOf<String, CandleLesson>()

        for (i in start..end) {
            val curr = history[i]
            val prev = history[i - 1]
            classifyPair(prev, curr, i)?.let { lesson ->
                // Keep the most recent occurrence of each pattern name.
                found[lesson.name] = lesson
            }
            classifySingle(curr, history, i)?.let { lesson ->
                found[lesson.name] = lesson
            }
        }
        return found.values
            .sortedByDescending { it.barIndex }
            .take(3)
    }

    private fun classifySingle(
        c: HistoryCandle,
        history: List<HistoryCandle>,
        index: Int,
    ): CandleLesson? {
        val range = (c.high - c.low).coerceAtLeast(1e-9)
        val body = abs(c.close - c.open)
        val upper = c.high - max(c.open, c.close)
        val lower = min(c.open, c.close) - c.low
        val bullish = c.close >= c.open
        val priorTrend = priorTrend(history, index)

        // Doji: tiny body vs range
        if (body / range <= 0.12 && range > 0) {
            return CandleLesson(
                name = "Doji",
                bias = "Neutral",
                summary = "Open ≈ close — indecision. Meaningful after a clear move; wait for the next candle.",
                learnQuery = "What is a doji candlestick?",
                barIndex = index,
            )
        }

        // Hammer / hanging man: long lower wick, small body near high
        if (lower >= body * 2.0 && upper <= body * 0.6 && body / range <= 0.45) {
            return if (priorTrend < 0) {
                CandleLesson(
                    name = "Hammer",
                    bias = "Bullish",
                    summary = "Long lower wick after a dip — buyers recovered. Confirm next candle/volume.",
                    learnQuery = "What is a hammer candlestick?",
                    barIndex = index,
                )
            } else if (priorTrend > 0) {
                CandleLesson(
                    name = "Hanging man",
                    bias = "Bearish",
                    summary = "Same shape after a rally — warning, not a sell by itself. Wait for confirmation.",
                    learnQuery = "What is a hanging man candlestick?",
                    barIndex = index,
                )
            } else null
        }

        // Shooting star / inverted hammer: long upper wick, small body near low
        if (upper >= body * 2.0 && lower <= body * 0.6 && body / range <= 0.45) {
            return if (priorTrend > 0) {
                CandleLesson(
                    name = "Shooting star",
                    bias = "Bearish",
                    summary = "Long upper wick after a rally — sellers rejected highs. Confirm before fading.",
                    learnQuery = "What is a shooting star candlestick?",
                    barIndex = index,
                )
            } else if (priorTrend < 0) {
                CandleLesson(
                    name = "Inverted hammer",
                    bias = "Bullish",
                    summary = "Long upper wick after a decline — possible reversal cue with confirmation.",
                    learnQuery = "What is an inverted hammer candlestick?",
                    barIndex = index,
                )
            } else null
        }

        // Marubozu: dominant body, tiny wicks
        if (body / range >= 0.78 && upper / range <= 0.12 && lower / range <= 0.12) {
            return CandleLesson(
                name = if (bullish) "Bullish marubozu" else "Bearish marubozu",
                bias = if (bullish) "Bullish" else "Bearish",
                summary = "Strong body with little wick — conviction candle. Still respect nearby S/R.",
                learnQuery = "What is a marubozu candlestick?",
                barIndex = index,
            )
        }

        return null
    }

    private fun classifyPair(
        prev: HistoryCandle,
        curr: HistoryCandle,
        index: Int,
    ): CandleLesson? {
        val prevBody = abs(prev.close - prev.open)
        val currBody = abs(curr.close - curr.open)
        if (prevBody <= 0 || currBody <= 0) return null
        val prevTop = max(prev.open, prev.close)
        val prevBot = min(prev.open, prev.close)
        val currTop = max(curr.open, curr.close)
        val currBot = min(curr.open, curr.close)
        val prevBull = prev.close >= prev.open
        val currBull = curr.close >= curr.open

        // Engulfing
        if (currBot <= prevBot && currTop >= prevTop && currBody > prevBody * 1.05) {
            if (!prevBull && currBull) {
                return CandleLesson(
                    name = "Bullish engulfing",
                    bias = "Bullish",
                    summary = "Green body covers prior red — buyers take over. Best near support with volume.",
                    learnQuery = "What is a bullish engulfing candlestick?",
                    barIndex = index,
                )
            }
            if (prevBull && !currBull) {
                return CandleLesson(
                    name = "Bearish engulfing",
                    bias = "Bearish",
                    summary = "Red body covers prior green — sellers take over. Best near resistance.",
                    learnQuery = "What is a bearish engulfing candlestick?",
                    barIndex = index,
                )
            }
        }

        // Harami: small body inside prior large body
        if (currTop < prevTop && currBot > prevBot && currBody < prevBody * 0.55 && prevBody > currBody * 1.4) {
            return CandleLesson(
                name = "Harami",
                bias = "Neutral",
                summary = "Small body inside prior large body — pause. Wait for a break of the large candle’s range.",
                learnQuery = "What is a harami candlestick?",
                barIndex = index,
            )
        }

        return null
    }

    /** Negative = recent dip, positive = recent rally, 0 = flat/unclear. */
    private fun priorTrend(history: List<HistoryCandle>, index: Int): Int {
        if (index < 3) return 0
        val a = history[index - 3].close
        val b = history[index - 1].close
        val change = (b - a) / a.coerceAtLeast(1e-9)
        return when {
            change <= -0.015 -> -1
            change >= 0.015 -> 1
            else -> 0
        }
    }
}
