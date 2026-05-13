package com.bysel.trader.ui.screens

import com.bysel.trader.data.models.Quote
import kotlin.math.abs

internal enum class SignalLabKind {
    BREAKOUT,
    VOLUME,
    DIVIDEND,
    UPSIDE,
    REBOUND,
    GOLDEN_CROSS,
}

internal data class SignalLabBucket(
    val kind: SignalLabKind,
    val title: String,
    val thesis: String,
    val keywords: List<String>,
    val quotes: List<Quote>,
)

internal fun buildSignalLabBuckets(quotes: List<Quote>): List<SignalLabBucket> {
    val cleanQuotes = quotes.filter { it.last > 0.0 }
    return listOf(
        SignalLabBucket(
            kind = SignalLabKind.BREAKOUT,
            title = "Breakout Watch",
            thesis = "Near-high names with price strength and volume confirmation.",
            keywords = listOf("breakout", "52 week high", "near high", "momentum", "breakout stocks"),
            quotes = breakoutCandidates(cleanQuotes),
        ),
        SignalLabBucket(
            kind = SignalLabKind.VOLUME,
            title = "Volume Shockers",
            thesis = "Unusual participation where the tape is speeding up fast.",
            keywords = listOf("volume", "unusual volume", "shockers", "activity", "high volume"),
            quotes = volumeShockers(cleanQuotes),
        ),
        SignalLabBucket(
            kind = SignalLabKind.DIVIDEND,
            title = "Dividend Radar",
            thesis = "Income names with live yield support on the board.",
            keywords = listOf("dividend", "yield", "income", "dividend stocks", "yield stocks"),
            quotes = dividendRadar(cleanQuotes),
        ),
        SignalLabBucket(
            kind = SignalLabKind.UPSIDE,
            title = "Upside Radar",
            thesis = "Names still trading below mean target expectations.",
            keywords = listOf("upside", "target", "analyst target", "value gap", "undervalued"),
            quotes = upsideRadar(cleanQuotes),
        ),
        SignalLabBucket(
            kind = SignalLabKind.REBOUND,
            title = "Rebound Watch",
            thesis = "Names lifting from support instead of breaking lower.",
            keywords = listOf("rebound", "recovery", "bounce", "oversold", "support"),
            quotes = reboundCandidates(cleanQuotes),
        ),
        SignalLabBucket(
            kind = SignalLabKind.GOLDEN_CROSS,
            title = "Golden Cross",
            thesis = "50-day MA has crossed above the 200-day MA — a classic bullish signal for trend followers.",
            keywords = listOf("golden cross", "ma crossover", "50 200 ma", "moving average", "trend", "bull trend"),
            quotes = goldenCrossCandidates(cleanQuotes),
        ),
    ).filter { it.quotes.isNotEmpty() }
}

internal fun signalLabMatchesQuery(query: String, bucket: SignalLabBucket): Boolean {
    if (query.isBlank()) return true
    return bucket.title.contains(query, ignoreCase = true) ||
        bucket.thesis.contains(query, ignoreCase = true) ||
        bucket.keywords.any { keyword ->
            keyword.contains(query, ignoreCase = true) || query.contains(keyword, ignoreCase = true)
        }
}

internal fun signalLabLeadSummary(bucket: SignalLabBucket): String {
    val lead = bucket.quotes.firstOrNull() ?: return "No live setups yet."
    return when (bucket.kind) {
        SignalLabKind.BREAKOUT -> {
            val reference = lead.fiftyTwoWeekHigh ?: lead.dayHigh
            val distance = percentFromLevel(lead.last, reference)?.let { if (it >= 0.0) "${formatPercent(abs(it))} through the high" else "${formatPercent(abs(it))} below the high" }
            val volume = volumeRatio(lead)?.let { "${formatMultiple(it)} normal volume" }
            listOfNotNull(distance, volume).joinToString(" • ").ifBlank {
                "${lead.symbol} is pressing its resistance band with fresh momentum."
            }
        }
        SignalLabKind.VOLUME -> {
            val volume = volumeRatio(lead)?.let { "${formatMultiple(it)} normal volume" } ?: "heavy tape"
            "${lead.symbol} is moving ${formatSignedPercent(lead.pctChange)} on $volume."
        }
        SignalLabKind.DIVIDEND -> {
            val dividend = lead.dividendYield?.let { "${formatPercent(it)} yield" } ?: "income support"
            "${lead.symbol} offers $dividend while trading ${formatSignedPercent(lead.pctChange)} today."
        }
        SignalLabKind.UPSIDE -> {
            val upside = upsidePercent(lead)?.let { "${formatPercent(it)} target gap" } ?: "room to target"
            "${lead.symbol} still carries $upside versus the current board price."
        }
        SignalLabKind.REBOUND -> {
            val support = lead.fiftyTwoWeekLow ?: lead.dayLow
            val distance = percentFromLevel(lead.last, support)?.let { "${formatPercent(it)} above support" }
            listOfNotNull(distance).joinToString(" • ").ifBlank {
                "${lead.symbol} is stabilizing off a lower support zone."
            } + " • ${formatSignedPercent(lead.pctChange)} today"
        }
        SignalLabKind.GOLDEN_CROSS -> {
            val ma50 = lead.fiftyDayAverage
            val ma200 = lead.twoHundredDayAverage
            val spread = if (ma50 != null && ma200 != null && ma200 > 0.0)
                "${formatPercent(((ma50 - ma200) / ma200) * 100.0)} spread MA50 vs MA200"
            else null
            listOfNotNull(spread).joinToString(" • ").ifBlank {
                "${lead.symbol} shows 50DMA above 200DMA with momentum on the board."
            } + " • ${formatSignedPercent(lead.pctChange)} today"
        }
    }
}

private fun breakoutCandidates(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote ->
            val deltaToHigh = percentFromLevel(quote.last, quote.fiftyTwoWeekHigh ?: quote.dayHigh)
            val hasVolumeSupport = (volumeRatio(quote) ?: 0.0) >= 1.1 || (quote.volume ?: 0L) >= 300_000L
            deltaToHigh != null && deltaToHigh >= -2.0 && quote.pctChange >= 1.2 && hasVolumeSupport
        }
        .sortedByDescending { quote -> quote.pctChange + (volumeRatio(quote) ?: 1.0) }
        .take(6)
}

private fun volumeShockers(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote ->
            val ratio = volumeRatio(quote) ?: 0.0
            (ratio >= 1.75 && abs(quote.pctChange) >= 0.8) ||
                ((quote.volume ?: 0L) >= 500_000L && abs(quote.pctChange) >= 2.0)
        }
        .sortedByDescending { quote -> abs(quote.pctChange) * (volumeRatio(quote) ?: 1.0) }
        .take(6)
}

private fun dividendRadar(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote -> (quote.dividendYield ?: 0.0) >= 1.5 }
        .sortedWith(compareByDescending<Quote> { it.dividendYield ?: 0.0 }.thenByDescending { it.pctChange })
        .take(6)
}

private fun upsideRadar(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote -> (upsidePercent(quote) ?: 0.0) >= 8.0 }
        .sortedByDescending { quote -> upsidePercent(quote) ?: 0.0 }
        .take(6)
}

private fun reboundCandidates(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote ->
            val reboundDistance = percentFromLevel(quote.last, quote.fiftyTwoWeekLow ?: quote.dayLow)
            reboundDistance != null && reboundDistance in 0.5..8.0 && quote.pctChange >= 0.5
        }
        .sortedWith(compareByDescending<Quote> { it.pctChange }.thenBy { percentFromLevel(it.last, it.fiftyTwoWeekLow ?: it.dayLow) ?: Double.MAX_VALUE })
        .take(6)
}

private fun goldenCrossCandidates(quotes: List<Quote>): List<Quote> {
    return quotes
        .filter { quote ->
            val ma50 = quote.fiftyDayAverage ?: return@filter false
            val ma200 = quote.twoHundredDayAverage ?: return@filter false
            // 50DMA must be above 200DMA (golden cross condition) and spread > 1%
            ma50 > ma200 && ((ma50 - ma200) / ma200) * 100.0 >= 1.0 &&
                // Price must be trading above both MAs (confirming uptrend)
                quote.last > ma50
        }
        .sortedByDescending { quote ->
            val ma50 = quote.fiftyDayAverage ?: 0.0
            val ma200 = quote.twoHundredDayAverage ?: 0.0
            if (ma200 > 0.0) ((ma50 - ma200) / ma200) * 100.0 else 0.0
        }
        .take(6)
}

private fun volumeRatio(quote: Quote): Double? {
    val volume = quote.volume ?: return null
    val averageVolume = quote.avgVolume ?: return null
    if (volume <= 0L || averageVolume <= 0L) return null
    return volume.toDouble() / averageVolume.toDouble()
}

private fun upsidePercent(quote: Quote): Double? {
    val target = quote.targetMeanPrice ?: return null
    if (quote.last <= 0.0 || target <= 0.0) return null
    return ((target - quote.last) / quote.last) * 100.0
}

private fun percentFromLevel(last: Double, level: Double?): Double? {
    if (last <= 0.0 || level == null || level <= 0.0) return null
    return ((last - level) / level) * 100.0
}

private fun formatPercent(value: Double): String = String.format("%.1f%%", value)

private fun formatMultiple(value: Double): String = String.format("%.1fx", value)

private fun formatSignedPercent(value: Double): String = buildString {
    if (value > 0) append("+")
    append(String.format("%.2f", value))
    append("%")
}