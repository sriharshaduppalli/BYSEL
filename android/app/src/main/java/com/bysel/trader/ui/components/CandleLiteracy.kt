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

data class LiteracyCard(
    val title: String,
    val tag: String,
    val summary: String,
    val learnQuery: String,
    val seenOnChart: Boolean = false,
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

        // Hammer only after a dip — skip the same shape after a rally (awkward names).
        if (lower >= body * 2.0 && upper <= body * 0.6 && body / range <= 0.45 && priorTrend < 0) {
            return CandleLesson(
                name = "Hammer",
                bias = "Bullish",
                summary = "Long lower wick after a dip — buyers recovered. Confirm next candle/volume.",
                learnQuery = "What is a hammer candlestick?",
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

/**
 * Beginner Indian-market cards for stock detail.
 * Packed from the Indian Stock Analysis Techniques essay — education only, never a trade call.
 */
object StockLiteracyCatalog {

    fun cardsFor(history: List<HistoryCandle>): List<LiteracyCard> {
        val detected = CandleLiteracyDetector.detectRecent(history)
        val seenNames = detected.map { it.name.lowercase() }
        val seenCandle = seenNames.any { name ->
            name.contains("engulfing") || name == "doji" || name == "hammer"
        }
        val seenHint = detected.firstOrNull()?.let { " Recent bars show ${it.name.lowercase()}." }.orEmpty()

        return listOf(
            LiteracyCard(
                title = "Engulfing, doji, hammer",
                tag = "Candle",
                summary = "Engulfing = one body covers the prior. Doji = open ≈ close (pause). " +
                    "Hammer = long lower wick after a dip. Confirm with volume — not a trade by itself.$seenHint",
                learnQuery = "What are engulfing, doji and hammer candlesticks?",
                seenOnChart = seenCandle,
            ),
            LiteracyCard(
                title = "P/E, P/B, PEG",
                tag = "Fundamental",
                summary = "P/E = price vs earnings (India large-caps often cited 15–25; always vs sector). " +
                    "P/B = price vs book (<3 often cited; banks judged with ROE). " +
                    "PEG = P/E vs growth (~1 roughly fair if growth is real). Compare, don’t treat as a buy button.",
                learnQuery = "What are P/E, P/B and PEG ratios for Indian stocks?",
            ),
            LiteracyCard(
                title = "ROE, ROCE, D/E, coverage",
                tag = "Fundamental",
                summary = "ROE = profit vs equity (>15% often healthy, >20% strong). " +
                    "ROCE = profit vs all capital (>15% preferred). " +
                    "D/E = debt vs equity (<1 typical for non-banks). " +
                    "Interest coverage = EBIT / interest (>3× often comfortable). " +
                    "FCF should back profit; high promoter pledging is a stress flag.",
                learnQuery = "What are ROE, ROCE, debt to equity and interest coverage?",
            ),
            LiteracyCard(
                title = "DMA, RSI, MACD, volume",
                tag = "Technical",
                summary = "50/200 DMA: 50 above 200 = golden cross, opposite = death cross (lagging trend). " +
                    "RSI 14 near 70/30 is stretched, not auto sell/buy. MACD = momentum turn. " +
                    "Volume + delivery % show if a move is real. SuperTrend/VWAP for trend/intraday context. " +
                    "S/R are zones where price paused.",
                learnQuery = "How do 50 DMA 200 DMA RSI 14 MACD volume and VWAP work in NSE stocks?",
            ),
            LiteracyCard(
                title = "Long-term (3+ years)",
                tag = "How to use",
                summary = "Quality + valuation + patience. Often cited: ROCE 15–18%, ROE 15–20%, low debt, PEG <1.5, " +
                    "avoid high pledging. Think 5–10 years. Not a buy order.",
                learnQuery = "How should beginners do long-term investing in Indian stocks?",
            ),
            LiteracyCard(
                title = "Swing (3–20 days)",
                tag = "How to use",
                summary = "Trend + momentum + timing. Trade with 50/200 DMA, pullback RSI 40–55, volume/delivery, " +
                    "1–2% risk and a defined stop. Paper practice first.",
                learnQuery = "How does swing trading work on NSE with DMA and RSI?",
            ),
            LiteracyCard(
                title = "F&O — paper gym",
                tag = "How to use",
                summary = "Futures = later buy/sell (levered). Options = paid right to buy (call) or sell (put). " +
                    "Read lot, margin vs notional, PCR, and Greeks on Trade → Options / Futures. Paper only.",
                learnQuery = "What are futures vs options, lot size, margin, PCR and Greeks for NSE beginners?",
            ),
            LiteracyCard(
                title = "Best practice",
                tag = "How to use",
                summary = "Filter quality on fundamentals → check valuation → use technicals only for timing → " +
                    "size with a stop-loss. Paper-practice the steps. No method guarantees returns.",
                learnQuery = "What is a beginner stock analysis checklist for Indian markets?",
            ),
        )
    }
}
