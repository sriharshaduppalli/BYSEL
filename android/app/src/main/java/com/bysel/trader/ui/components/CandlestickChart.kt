package com.bysel.trader.ui.components
import com.bysel.trader.ui.format.usFmt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bysel.trader.data.api.ChartPattern
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.utils.TechnicalIndicators
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class Overlay(val label: String, val color: Color) {
    SMA20("SMA 20", Color(0xFFFFD600)),
    SMA50("SMA 50", Color(0xFF00B0FF)),
    EMA20("EMA 20", Color(0xFFFF6D00)),
    BB("Bollinger", Color(0xFFAB47BC)),
    VWAP("VWAP", Color(0xFF26A69A)),
}

private val MACD_LINE = Color(0xFF29B6F6)
private val MACD_SIGNAL = Color(0xFFFFA726)

/** Overlay toggles persist across rotation by name. */
private val OverlaySetSaver: Saver<Set<Overlay>, Any> = listSaver(
    save = { set -> set.map { it.name } },
    restore = { names -> names.mapNotNull { n -> Overlay.entries.firstOrNull { it.name == n } }.toSet() },
)

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DAY_SHORT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
private val MONTH_YEAR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy")
private val CLOCK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class AxisTick(val index: Int, val label: String)

private fun candleMillis(ts: Long): Long =
    when {
        ts <= 0L -> 0L
        ts < 10_000_000_000L -> ts * 1000L
        else -> ts
    }

private fun candleClock(ts: Long): String? {
    val ms = candleMillis(ts)
    if (ms <= 0L) return null
    return runCatching { Instant.ofEpochMilli(ms).atZone(IST).format(CLOCK_FMT) }.getOrNull()
}

private fun candleDate(ts: Long): LocalDate? {
    val ms = candleMillis(ts)
    if (ms <= 0L) return null
    return runCatching { Instant.ofEpochMilli(ms).atZone(IST).toLocalDate() }.getOrNull()
}

/** Label placement must not throw when the plot is shorter than the text. */
private fun Float.clampLabel(min: Float, max: Float): Float {
    if (!isFinite()) return min
    val end = if (max.isFinite()) max else min
    if (end <= min) return min
    return coerceIn(min, end)
}

private fun formatAxisPrice(price: Double): String = when {
    price >= 10_000 -> usFmt("%.0f", price)
    price >= 1_000 -> usFmt("%.1f", price)
    price >= 100 -> usFmt("%.1f", price)
    else -> usFmt("%.2f", price)
}

/**
 * Build X-axis ticks for the visible window based on chart range.
 * 1D → clock time; 5D → one label per session day; 1M/3M → spaced calendar dates; 1Y → month starts.
 */
private fun buildXAxisTicks(
    history: List<HistoryCandle>,
    rangeLabel: String,
    first: Int,
    lastInclusive: Int,
): List<AxisTick> {
    if (history.isEmpty() || first > lastInclusive) return emptyList()
    val lo = first.coerceIn(0, history.lastIndex)
    val hi = lastInclusive.coerceIn(lo, history.lastIndex)
    val spanMs = candleMillis(history[hi].timestamp) - candleMillis(history[lo].timestamp)
    val requested = rangeLabel.trim().uppercase()
    // A widened session still arrives labeled 1D. Switch the axis once it covers more than a day.
    val range = when {
        requested == "1D" && spanMs > 36L * 3_600_000L ->
            if (spanMs > 20L * 86_400_000L) "1M" else "5D"
        else -> requested
    }

    return when (range) {
        "1D" -> {
            val span = hi - lo
            if (span <= 0) {
                val label = candleClock(history[lo].timestamp) ?: return emptyList()
                return listOf(AxisTick(lo, label))
            }
            val count = 5.coerceAtMost(span + 1)
            val ticks = ArrayList<AxisTick>(count)
            var lastLabel: String? = null
            for (t in 0 until count) {
                val idx = lo + ((span * t) / (count - 1).coerceAtLeast(1))
                val label = candleClock(history[idx].timestamp) ?: continue
                if (label == lastLabel && ticks.isNotEmpty()) continue
                ticks += AxisTick(idx, label)
                lastLabel = label
            }
            ticks
        }
        "5D" -> {
            // One tick per trading day in the visible window (up to 5).
            val byDay = linkedMapOf<LocalDate, Int>()
            for (i in lo..hi) {
                val d = candleDate(history[i].timestamp) ?: continue
                if (d !in byDay) byDay[d] = i
            }
            byDay.entries
                .toList()
                .takeLast(5)
                .map { (date, idx) -> AxisTick(idx, date.format(DAY_SHORT_FMT)) }
        }
        "1M" -> evenlySpacedDateTicks(history, lo, hi, targetCount = 5, formatter = DAY_FMT)
        "3M" -> {
            // Prefer ~bi-weekly / weekly cadence across ~3 months.
            evenlySpacedDateTicks(history, lo, hi, targetCount = 6, formatter = DAY_FMT)
        }
        "1Y" -> {
            // One tick near the first candle of each month in range.
            val byMonth = linkedMapOf<String, Int>()
            for (i in lo..hi) {
                val d = candleDate(history[i].timestamp) ?: continue
                val key = "${d.year}-${d.monthValue}"
                if (key !in byMonth) byMonth[key] = i
            }
            val months = byMonth.entries.toList()
            val step = max(1, months.size / 8)
            months.filterIndexed { index, _ -> index % step == 0 || index == months.lastIndex }
                .take(12)
                .map { (_, idx) ->
                    val d = candleDate(history[idx].timestamp)
                    val label = when {
                        d == null -> ""
                        d.year != LocalDate.now(IST).year -> d.format(MONTH_YEAR_FMT)
                        else -> d.format(MONTH_FMT)
                    }
                    AxisTick(idx, label)
                }
                .filter { it.label.isNotBlank() }
        }
        else -> evenlySpacedDateTicks(history, lo, hi, targetCount = 5, formatter = DAY_FMT)
    }
}

private fun evenlySpacedDateTicks(
    history: List<HistoryCandle>,
    lo: Int,
    hi: Int,
    targetCount: Int,
    formatter: DateTimeFormatter,
): List<AxisTick> {
    val span = hi - lo
    if (span <= 0) {
        val d = candleDate(history[lo].timestamp) ?: return emptyList()
        return listOf(AxisTick(lo, d.format(formatter)))
    }
    val count = targetCount.coerceIn(2, span + 1)
    val ticks = ArrayList<AxisTick>(count)
    var lastLabel: String? = null
    for (t in 0 until count) {
        val idx = lo + ((span * t) / (count - 1).coerceAtLeast(1))
        val d = candleDate(history[idx].timestamp) ?: continue
        val label = d.format(formatter)
        if (label == lastLabel && ticks.isNotEmpty()) continue
        ticks += AxisTick(idx, label)
        lastLabel = label
    }
    return ticks
}

/**
 * Canvas candlestick chart that fits 5D / 1M / 3M / 1Y ranges.
 * Pinch scales the time axis around the fingers. Drag moves the window.
 * Pinching or dragging past the left edge asks for older bars. A double tap fits the latest bars again.
 * When [livePrice] is set, the last candle and a price line follow that print.
 */
@Composable
fun CandlestickChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    initialBarWidthDp: Float = 10f,
    rangeLabel: String = "1M",
    expandPlot: Boolean = false,
    /** Detected formations (index ranges into [history]) drawn as study bands. */
    patterns: List<ChartPattern> = emptyList(),
    /** Latest quote. Moves the forming candle between history refreshes. 0 leaves the bar as loaded. */
    livePrice: Double = 0.0,
    /** Fired once when a pinch or drag tries to move past the oldest loaded bar. */
    onNeedOlderBars: () -> Unit = {},
    /** Closes of another symbol, drawn on their own scale so they are not read as rupees. */
    compareCloses: List<Double> = emptyList(),
    compareLabel: String = "",
    /** yyyy-mm-dd from a calendar the app already has. Blank draws nothing. */
    eventDate: String = "",
    eventCaption: String = "",
    /** Fired with the drawn horizontal price. The caller chooses above or below. */
    onAlertAtLevel: (Double) -> Unit = {},
    /** Null clears the line. The chat desk reads this as the open chart's mark. */
    onLevelChange: (Double?) -> Unit = {},
) {
    if (history.isEmpty()) return

    // Saveable so indicator toggles survive rotation.
    var activeOverlays by rememberSaveable(stateSaver = OverlaySetSaver) { mutableStateOf(setOf<Overlay>()) }
    var heikinAshi by rememberSaveable { mutableStateOf(false) }
    var showMacd by rememberSaveable { mutableStateOf(false) }
    var showVolume by rememberSaveable { mutableStateOf(false) }
    var inspectIndex by rememberSaveable { mutableIntStateOf(-1) }
    var drawMode by rememberSaveable { mutableStateOf("none") }
    var drawPending by rememberSaveable { mutableIntStateOf(-1) }
    var trendStore by rememberSaveable { mutableStateOf("") }
    var fibStore by rememberSaveable { mutableStateOf("") }
    var noteIndex by rememberSaveable { mutableIntStateOf(-1) }
    var noteWord by rememberSaveable { mutableStateOf("Note") }
    var markedPrice by rememberSaveable { mutableStateOf(Float.NaN) }
    LaunchedEffect(markedPrice) {
        val level = markedPrice.takeIf { it.isFinite() && it > 0f }?.toDouble()
        onLevelChange(level)
    }
    val drawablePatterns = remember(patterns, history.size) {
        patterns.filter { it.startIdx in history.indices && it.endIdx in history.indices && it.endIdx >= it.startIdx }
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val closes = remember(history) { history.map { it.close } }
    val highs = remember(history) { history.map { it.high } }
    val lows = remember(history) { history.map { it.low } }
    // Bars drawn on screen. Indicators stay on the real closes either way.
    val bars = remember(history, heikinAshi) {
        if (heikinAshi) TechnicalIndicators.heikinAshi(history) else history
    }

    val sma20 = remember(closes) { TechnicalIndicators.sma(closes, 20) }
    val sma50 = remember(closes) { TechnicalIndicators.sma(closes, 50) }
    val ema20 = remember(closes) { TechnicalIndicators.ema(closes, 20) }
    val bb = remember(closes) { TechnicalIndicators.bollingerBands(closes, 20) }
    val rsi = remember(closes) { TechnicalIndicators.rsi(closes, 14) }
    val macd = remember(closes) { TechnicalIndicators.macd(closes) }
    val vwap = remember(history) { TechnicalIndicators.vwap(history) }
    val trendPairs = remember(trendStore) { parseIndexPairs(trendStore) }
    val fibPair = remember(fibStore) { parseIndexPairs(fibStore).firstOrNull() }

    val theme = LocalAppTheme.current
    val axisLabelStyle = remember(theme.textSecondary) {
        TextStyle(color = theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    val gridColor = theme.text.copy(alpha = 0.12f)
    val axisLineColor = theme.text.copy(alpha = 0.20f)

    Column(modifier = if (expandPlot) modifier.fillMaxSize() else modifier) {
        val lastCandle = history.last()
        val headerClose = if (livePrice > 0.0) livePrice else lastCandle.close
        val headerHigh = if (livePrice > 0.0) max(lastCandle.high, livePrice) else lastCandle.high
        val headerLow = if (livePrice > 0.0) min(lastCandle.low, livePrice) else lastCandle.low
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .blockParentHorizontalPager()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                "O: ${usFmt("%.2f", lastCandle.open)}",
                "H: ${usFmt("%.2f", headerHigh)}",
                "L: ${usFmt("%.2f", headerLow)}",
                "C: ${usFmt("%.2f", headerClose)}",
            ).forEach { label ->
                Text(
                    text = label,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .blockParentHorizontalPager()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Overlay.entries.forEach { overlay ->
                val selected = overlay in activeOverlays
                FilterChip(
                    selected = selected,
                    onClick = {
                        activeOverlays = if (selected) activeOverlays - overlay else activeOverlays + overlay
                    },
                    label = { Text(overlay.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = overlay.color.copy(alpha = 0.2f),
                        selectedLabelColor = overlay.color,
                    ),
                )
            }
            FilterChip(
                selected = heikinAshi,
                onClick = { heikinAshi = !heikinAshi },
                label = { Text("Heikin-Ashi", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = markedPrice.isFinite() && markedPrice > 0f,
                onClick = {
                    val last = history.lastOrNull()?.close?.toFloat() ?: Float.NaN
                    markedPrice = if (markedPrice.isFinite() && markedPrice > 0f) Float.NaN else last
                },
                label = { Text("Level", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = showMacd,
                onClick = { showMacd = !showMacd },
                label = { Text("MACD", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MACD_LINE.copy(alpha = 0.2f),
                    selectedLabelColor = MACD_LINE,
                ),
            )
            FilterChip(
                selected = showVolume,
                onClick = { showVolume = !showVolume },
                label = { Text("Volume", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = drawMode == "trend",
                onClick = {
                    drawMode = if (drawMode == "trend") "none" else "trend"
                    drawPending = -1
                },
                label = { Text(if (drawMode == "trend") "Tap 2 bars" else "Trend", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = drawMode == "fib",
                onClick = {
                    drawMode = if (drawMode == "fib") "none" else "fib"
                    drawPending = -1
                },
                label = { Text("Fib", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = drawMode == "hline",
                onClick = { drawMode = if (drawMode == "hline") "none" else "hline" },
                label = { Text("H-line", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = drawMode == "note",
                onClick = {
                    if (drawMode == "note") {
                        noteWord = when (noteWord) {
                            "Note" -> "Support"
                            "Support" -> "Resistance"
                            else -> "Note"
                        }
                    } else {
                        drawMode = "note"
                    }
                },
                label = { Text(noteWord, fontSize = 11.sp, maxLines = 1, softWrap = false) },
            )
            if (markedPrice.isFinite() && markedPrice > 0f) {
                FilterChip(
                    selected = false,
                    onClick = { onAlertAtLevel(markedPrice.toDouble()) },
                    label = { Text("Alert at line", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                )
            }
            if (trendStore.isNotEmpty() || fibStore.isNotEmpty() || noteIndex >= 0 || inspectIndex >= 0 || markedPrice.isFinite()) {
                FilterChip(
                    selected = false,
                    onClick = {
                        trendStore = ""
                        fibStore = ""
                        noteIndex = -1
                        drawPending = -1
                        drawMode = "none"
                        inspectIndex = -1
                        markedPrice = Float.NaN
                    },
                    label = { Text("Clear", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                )
            }
        }
            if (heikinAshi) {
                Text(
                    text = "Heikin-Ashi smooths each bar from the one before it. Read the colour run, not the exact open or close.",
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            if (showMacd) {
                val lastMacd = macd.macdLine.lastOrNull { it != null }
                val lastSignal = macd.signalLine.lastOrNull { it != null }
                val lastHist = macd.histogram.lastOrNull { it != null }
                if (lastMacd != null && lastSignal != null && lastHist != null) {
                    val above = lastMacd >= lastSignal
                    Text(
                        text = "MACD(12,26,9): ${usFmt("%.2f", lastMacd)} · signal ${usFmt("%.2f", lastSignal)} — " +
                            if (above) "MACD above signal (momentum up)" else "MACD below signal (momentum down)",
                        color = if (above) theme.positive else theme.negative,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            val lastRsi = rsi.lastOrNull { it != null }
            if (lastRsi != null) {
                val rsiColor = when {
                    lastRsi >= 70 -> theme.negative
                    lastRsi <= 30 -> theme.positive
                    else -> theme.textSecondary
                }
                val rsiLabel = when {
                    lastRsi >= 70 -> "Overbought"
                    lastRsi <= 30 -> "Oversold"
                    else -> "Neutral"
                }
                Text(
                    text = "RSI(14): ${usFmt("%.1f", lastRsi)} — $rsiLabel",
                    color = rsiColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

        Text(
            text = "Pinch to scale time. Drag sideways. Pinch past the left edge for older bars. Hold a bar for its open, high, low, and close.",
            color = theme.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (inspectIndex in history.indices) {
            val bar = history[inspectIndex]
            val whenLabel = candleClock(bar.timestamp) ?: candleDate(bar.timestamp)?.toString() ?: "selected bar"
            Text(
                text = "$whenLabel  O ${formatAxisPrice(bar.open)}  H ${formatAxisPrice(bar.high)}  L ${formatAxisPrice(bar.low)}  C ${formatAxisPrice(bar.close)}",
                color = theme.text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (drawMode != "none") {
            Text(
                text = when (drawMode) {
                    "trend" -> if (drawPending < 0) "Tap the first bar of a trend. Up to four lines." else "Tap the second bar."
                    "fib" -> if (drawPending < 0) "Tap the first bar of the swing." else "Tap the second bar."
                    "hline" -> "Tap a bar to place a horizontal line on its close."
                    else -> "Tap a bar to place $noteWord. Tap the chip again to change the word."
                },
                color = theme.primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        if (compareCloses.size >= 2 && compareLabel.isNotBlank()) {
            Text(
                text = "$compareLabel is the extra line. It uses its own scale, not the rupee axis.",
                color = theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }

        BoxWithConstraints(
            modifier = if (expandPlot) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val widthOk = widthPx.isFinite() && widthPx > 0f
            val yAxisPadPx = with(density) { 64.dp.toPx() }
            val plotWidthPx = if (widthOk) (widthPx - yAxisPadPx).coerceAtLeast(1f) else 1f

            val fittedBarWidth = run {
                val targetVisible = when {
                    history.size <= 28 -> history.size.coerceAtLeast(8)
                    history.size <= 80 -> 48
                    history.size <= 160 -> 56
                    else -> 64
                }.coerceAtMost(history.size).coerceAtLeast(1)
                val raw = plotWidthPx / targetVisible / density.density
                if (raw.isFinite()) raw.coerceIn(2.5f, 18f) else initialBarWidthDp
            }
            // Pinch-out can show every loaded bar. Pinch-in can isolate a few wide candles.
            val minBarDp = run {
                val fitAll = plotWidthPx / history.size.coerceAtLeast(1) / density.density
                if (fitAll.isFinite()) fitAll.coerceAtLeast(0.45f) else 2.5f
            }
            val maxBarDp = max(minBarDp + 4f, 96f)

            val barWidthState = remember(rangeLabel) { mutableFloatStateOf(fittedBarWidth) }
            val startIndexState = remember(rangeLabel) { mutableFloatStateOf(0f) }
            val followLatest = remember(rangeLabel) { mutableStateOf(true) }
            val candleCount = remember(rangeLabel) { mutableIntStateOf(history.size) }
            val fittedState = remember(rangeLabel) { mutableFloatStateOf(fittedBarWidth) }
            val anchorTs = remember(rangeLabel) { mutableLongStateOf(0L) }
            var olderArmed by remember(rangeLabel) { mutableStateOf(true) }
            candleCount.intValue = history.size
            fittedState.floatValue = fittedBarWidth
            val barWidth = barWidthState.floatValue
            val startIndex = startIndexState.floatValue
            val onOlder = rememberUpdatedState(onNeedOlderBars)

            fun maxStartFor(barDp: Float, count: Int = history.size): Float {
                val barPx = (barDp * density.density).coerceAtLeast(0.5f)
                val visible = (plotWidthPx / barPx).coerceAtLeast(1f)
                return (count - visible).coerceAtLeast(0f)
            }

            fun applyWindow(count: Int, nextWidth: Float, focalPx: Float, panX: Float, zoom: Float) {
                val width = nextWidth.coerceIn(minBarDp, maxBarDp)
                val oldPx = (barWidthState.floatValue * density.density).coerceAtLeast(0.5f)
                val newPx = (width * density.density).coerceAtLeast(0.5f)
                val focal = (focalPx - yAxisPadPx).coerceIn(0f, plotWidthPx)
                val anchor = startIndexState.floatValue + focal / oldPx
                val nextMax = (count - plotWidthPx / newPx).coerceAtLeast(0f)
                val rawStart = anchor - focal / newPx - panX / newPx
                val nextStart = rawStart.clampLabel(0f, nextMax)
                barWidthState.floatValue = width
                startIndexState.floatValue = nextStart
                followLatest.value = nextStart >= nextMax - 0.6f
                val visible = nextStart.toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                anchorTs.longValue = history.getOrNull(visible)?.timestamp ?: anchorTs.longValue
                if (nextStart > 1.5f) olderArmed = true
                val reachingOlder = rawStart < -0.15f ||
                    (nextStart <= 0.05f && (zoom < 0.999f || panX > 2f))
                if (reachingOlder && olderArmed) {
                    olderArmed = false
                    onOlder.value()
                }
            }

            LaunchedEffect(rangeLabel, plotWidthPx) {
                barWidthState.floatValue = fittedBarWidth
                startIndexState.floatValue = maxStartFor(fittedBarWidth)
                followLatest.value = true
                olderArmed = true
            }
            LaunchedEffect(history.size, history.firstOrNull()?.timestamp, plotWidthPx) {
                val maxStart = maxStartFor(barWidthState.floatValue)
                if (followLatest.value || anchorTs.longValue == 0L) {
                    startIndexState.floatValue = maxStart
                } else {
                    val previous = anchorTs.longValue
                    val idx = history.indexOfFirst { it.timestamp >= previous }
                    startIndexState.floatValue = if (idx >= 0) {
                        idx.toFloat().clampLabel(0f, maxStart)
                    } else {
                        startIndexState.floatValue.clampLabel(0f, maxStart)
                    }
                }
                val visible = startIndexState.floatValue.toInt().coerceIn(0, history.lastIndex)
                anchorTs.longValue = history[visible].timestamp
            }
            LaunchedEffect(history.firstOrNull()?.timestamp) {
                olderArmed = true
            }

            val sliderValue = if (barWidth.isFinite()) barWidth.coerceIn(minBarDp, maxBarDp) else fittedBarWidth
            if (!widthOk) {
                return@BoxWithConstraints
            }

            val onGesture = rememberUpdatedState<(Float, Float, Float) -> Unit> { panX, zoom, focalX ->
                if (!focalX.isFinite() || !zoom.isFinite()) return@rememberUpdatedState
                applyWindow(
                    count = candleCount.intValue,
                    nextWidth = barWidthState.floatValue * zoom,
                    focalPx = focalX,
                    panX = panX,
                    zoom = zoom,
                )
            }
            val onTrendTap = rememberUpdatedState<(Offset) -> Boolean> { position ->
                if (drawMode == "none") return@rememberUpdatedState false
                val index = candleIndexAt(
                    x = position.x,
                    start = startIndexState.floatValue,
                    barPx = barWidthState.floatValue * density.density,
                    leftPad = yAxisPadPx,
                    count = candleCount.intValue,
                )
                when (drawMode) {
                    "hline" -> {
                        markedPrice = history.getOrNull(index)?.close?.toFloat() ?: markedPrice
                        drawMode = "none"
                    }
                    "note" -> {
                        noteIndex = index
                        drawMode = "none"
                    }
                    "trend", "fib" -> {
                        if (drawPending < 0) {
                            drawPending = index
                        } else {
                            val pair = "${drawPending}-$index"
                            if (drawMode == "trend") {
                                val next = parseIndexPairs(trendStore).take(3) + (drawPending to index)
                                trendStore = next.joinToString(",") { "${it.first}-${it.second}" }
                            } else {
                                fibStore = pair
                            }
                            drawPending = -1
                            drawMode = "none"
                        }
                    }
                }
                true
            }
            val onInspect = rememberUpdatedState<(Offset) -> Unit> { position ->
                inspectIndex = candleIndexAt(
                    x = position.x,
                    start = startIndexState.floatValue,
                    barPx = barWidthState.floatValue * density.density,
                    leftPad = yAxisPadPx,
                    count = candleCount.intValue,
                )
            }

            Column(modifier = if (expandPlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { next ->
                        val zoom = if (next < barWidthState.floatValue) 0.5f else 1.5f
                        applyWindow(history.size, next, plotWidthPx / 2f + yAxisPadPx, 0f, zoom)
                    },
                    valueRange = minBarDp..maxBarDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (expandPlot) Modifier.weight(1f) else Modifier.height(320.dp))
                        .background(theme.mutedSurface, RoundedCornerShape(12.dp))
                        .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)
                        .pointerInput(rangeLabel, plotWidthPx) {
                            detectChartGestures(
                                onPanZoom = { panX, zoom, focalX -> onGesture.value(panX, zoom, focalX) },
                                onDoubleTap = {
                                    val fit = fittedState.floatValue.coerceIn(minBarDp, maxBarDp)
                                    barWidthState.floatValue = fit
                                    val maxStart = maxStartFor(fit, candleCount.intValue)
                                    startIndexState.floatValue = maxStart
                                    followLatest.value = true
                                    olderArmed = true
                                },
                                onTap = { onTrendTap.value(it) },
                                onLongPress = { onInspect.value(it) },
                            )
                        },
                ) {
                    if (size.width < 2f || size.height < 2f) return@Canvas
                    val leftPad = yAxisPadPx
                    val bottomPad = 36.dp.toPx()
                    val topPad = 6f
                    val plotW = (size.width - leftPad).coerceAtLeast(1f)
                    val fullH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    // MACD takes the bottom quarter of the price area so both share one x-axis.
                    val macdGap = if (showMacd && fullH > 160f) 8f else 0f
                    val macdH = if (showMacd && fullH > 160f) fullH * 0.22f else 0f
                    val volumeGap = if (showVolume && fullH > 160f) 8f else 0f
                    val volumeH = if (showVolume && fullH > 160f) fullH * 0.16f else 0f
                    val plotH = (fullH - macdH - macdGap - volumeH - volumeGap).coerceAtLeast(1f)
                    val barPx = (barWidth.dp.toPx()).coerceAtLeast(0.5f)
                    val first = startIndex.toInt().coerceIn(0, history.lastIndex)
                    val visibleCount = (plotW / barPx).toInt().coerceAtLeast(1)
                    val lastVisible = (first + visibleCount - 1).coerceIn(first, history.lastIndex)
                    fun barLow(index: Int, candle: HistoryCandle): Double =
                        if (livePrice > 0.0 && index == history.lastIndex) min(candle.low, livePrice) else candle.low
                    fun barHigh(index: Int, candle: HistoryCandle): Double =
                        if (livePrice > 0.0 && index == history.lastIndex) max(candle.high, livePrice) else candle.high
                    fun barClose(index: Int, candle: HistoryCandle): Double =
                        if (livePrice > 0.0 && index == history.lastIndex) livePrice else candle.close
                    var visMin = barLow(first, bars[first])
                    var visMax = barHigh(first, bars[first])
                    for (index in first..lastVisible) {
                        val candle = bars[index]
                        visMin = min(visMin, barLow(index, candle))
                        visMax = max(visMax, barHigh(index, candle))
                    }
                    val pad = ((visMax - visMin) * 0.08).coerceAtLeast(visMax * 0.002).coerceAtLeast(0.05)
                    val minPrice = visMin - pad
                    val priceRange = max(1e-6, (visMax + pad) - minPrice)

                    fun yFor(price: Double): Float {
                        val norm = ((price - minPrice) / priceRange).toFloat().coerceIn(0f, 1f)
                        return topPad + plotH * (1f - norm)
                    }

                    fun xFor(index: Int): Float =
                        leftPad + (index - startIndex) * barPx + barPx / 2f

                    // Plot frame
                    drawLine(
                        color = axisLineColor,
                        start = Offset(leftPad, topPad),
                        end = Offset(leftPad, topPad + plotH),
                        strokeWidth = 1.2f,
                    )
                    drawLine(
                        color = axisLineColor,
                        start = Offset(leftPad, topPad + plotH),
                        end = Offset(size.width, topPad + plotH),
                        strokeWidth = 1.2f,
                    )

                    // Y-axis grid + price labels
                    val ySteps = 4
                    for (step in 0..ySteps) {
                        val t = step / ySteps.toFloat()
                        val price = minPrice + priceRange * (1.0 - t) // top → max
                        val y = topPad + plotH * t
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )
                        val label = formatAxisPrice(price)
                        val layout = textMeasurer.measure(label, style = axisLabelStyle)
                        val tx = (leftPad - layout.size.width - 6f).coerceAtLeast(0f)
                        val ty = (y - layout.size.height / 2f).clampLabel(0f, size.height - layout.size.height)
                        if (layout.size.height < size.height) {
                            drawText(layout, topLeft = Offset(tx, ty))
                        }
                    }

                    if (markedPrice.isFinite() && markedPrice > 0f) {
                        val levelY = yFor(markedPrice.toDouble())
                        drawLine(
                            color = theme.caution,
                            start = Offset(leftPad, levelY),
                            end = Offset(size.width, levelY),
                            strokeWidth = 1.6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        )
                        val levelLabel = textMeasurer.measure(
                            "Level ${formatAxisPrice(markedPrice.toDouble())}",
                            style = axisLabelStyle.copy(color = theme.caution),
                        )
                        if (levelLabel.size.height < size.height) {
                            drawText(
                                levelLabel,
                                topLeft = Offset(
                                    leftPad + 6f,
                                    (levelY - levelLabel.size.height - 2f).clampLabel(0f, size.height - levelLabel.size.height),
                                ),
                            )
                        }
                    }

                    // Candles + overlays
                    var i = first
                    while (i < history.size) {
                        val xCenter = xFor(i)
                        if (xCenter > size.width + barPx) break
                        if (xCenter >= leftPad - barPx) {
                            val candle = bars[i]
                            val closePrice = barClose(i, candle)
                            val isUp = closePrice >= candle.open
                            val color = if (isUp) theme.positive else theme.negative
                            val highY = yFor(barHigh(i, candle))
                            val lowY = yFor(barLow(i, candle))
                            val openY = yFor(candle.open)
                            val closeY = yFor(closePrice)
                            val wickW = (barPx * 0.14f).coerceIn(0.6f, 3f)
                            val bodyW = (barPx * 0.68f).coerceIn(0.6f, barPx.coerceAtLeast(0.6f))
                            val top = minOf(openY, closeY)
                            val bodyH = (maxOf(openY, closeY) - top).coerceAtLeast(2.2f)

                            drawLine(
                                color = color,
                                start = Offset(xCenter, highY),
                                end = Offset(xCenter, lowY),
                                strokeWidth = wickW,
                            )
                            drawRect(
                                color = color,
                                topLeft = Offset(xCenter - bodyW / 2f, top),
                                size = Size(bodyW, bodyH),
                            )

                            fun dot(value: Double?, color: Color) {
                                if (value == null) return
                                drawCircle(
                                    color = color,
                                    radius = 2.4f,
                                    center = Offset(xCenter, yFor(value)),
                                )
                            }
                            if (Overlay.SMA20 in activeOverlays) dot(sma20.getOrNull(i), Overlay.SMA20.color)
                            if (Overlay.SMA50 in activeOverlays) dot(sma50.getOrNull(i), Overlay.SMA50.color)
                            if (Overlay.EMA20 in activeOverlays) dot(ema20.getOrNull(i), Overlay.EMA20.color)
                            if (Overlay.BB in activeOverlays) {
                                dot(bb.upper.getOrNull(i), Overlay.BB.color)
                                dot(bb.middle.getOrNull(i), Overlay.BB.color.copy(alpha = 0.55f))
                                dot(bb.lower.getOrNull(i), Overlay.BB.color)
                            }
                            if (Overlay.VWAP in activeOverlays) dot(vwap.getOrNull(i), Overlay.VWAP.color)
                        }
                        i++
                    }

                    // Pattern study bands: tint the bars the detector grouped, name them at the top.
                    drawablePatterns.forEachIndexed { pIndex, pattern ->
                        val x0 = xFor(pattern.startIdx) - barPx / 2f
                        val x1 = xFor(pattern.endIdx) + barPx / 2f
                        if (x1 < leftPad || x0 > size.width) return@forEachIndexed
                        val clampedX0 = x0.coerceAtLeast(leftPad)
                        val clampedX1 = x1.coerceAtMost(size.width)
                        val tint = when (pattern.signal.uppercase()) {
                            "BUY", "BULLISH" -> theme.positive
                            "SELL", "BEARISH" -> theme.negative
                            else -> theme.caution
                        }
                        drawRect(
                            color = tint.copy(alpha = 0.10f),
                            topLeft = Offset(clampedX0, topPad),
                            size = Size((clampedX1 - clampedX0).coerceAtLeast(1f), plotH),
                        )
                        drawLine(
                            color = tint.copy(alpha = 0.6f),
                            start = Offset(clampedX0, topPad + plotH),
                            end = Offset(clampedX1, topPad + plotH),
                            strokeWidth = 3f,
                        )
                        val tag = textMeasurer.measure(
                            "${pattern.pattern} · ${pattern.confidence}%",
                            style = axisLabelStyle.copy(color = tint),
                        )
                        // Stagger labels so overlapping patterns stay legible.
                        val ty = topPad + 2f + (pIndex % 3) * (tag.size.height + 2f)
                        val tx = (clampedX0 + 4f).clampLabel(leftPad, (size.width - tag.size.width).coerceAtLeast(leftPad))
                        if (tag.size.width < plotW && ty + tag.size.height < topPad + plotH) {
                            drawText(tag, topLeft = Offset(tx, ty))
                        }
                    }

                    // MACD pane: histogram bars, MACD line, signal line, zero line.
                    if (macdH > 0f) {
                        val paneTop = topPad + plotH + volumeH + volumeGap + macdGap
                        val paneH = macdH
                        var absMax = 1e-9
                        for (index in first..lastVisible) {
                            macd.macdLine.getOrNull(index)?.let { absMax = max(absMax, abs(it)) }
                            macd.signalLine.getOrNull(index)?.let { absMax = max(absMax, abs(it)) }
                            macd.histogram.getOrNull(index)?.let { absMax = max(absMax, abs(it)) }
                        }
                        val zeroY = paneTop + paneH / 2f
                        fun yMacd(v: Double): Float =
                            (zeroY - (v / absMax).toFloat().coerceIn(-1f, 1f) * (paneH / 2f - 2f))
                        drawLine(
                            color = axisLineColor,
                            start = Offset(leftPad, paneTop),
                            end = Offset(size.width, paneTop),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, zeroY),
                            end = Offset(size.width, zeroY),
                            strokeWidth = 1f,
                        )
                        val histW = (barPx * 0.5f).coerceAtLeast(1.5f)
                        var macdPath: Path? = null
                        var signalPath: Path? = null
                        var j = first
                        while (j < history.size) {
                            val x = xFor(j)
                            if (x > size.width + barPx) break
                            if (x >= leftPad - barPx) {
                                macd.histogram.getOrNull(j)?.let { h ->
                                    val y = yMacd(h)
                                    val top = min(y, zeroY)
                                    drawRect(
                                        color = (if (h >= 0) theme.positive else theme.negative).copy(alpha = 0.55f),
                                        topLeft = Offset(x - histW / 2f, top),
                                        size = Size(histW, (abs(y - zeroY)).coerceAtLeast(1f)),
                                    )
                                }
                                macd.macdLine.getOrNull(j)?.let { v ->
                                    val y = yMacd(v)
                                    macdPath = (macdPath ?: Path().apply { moveTo(x, y) }).also { it.lineTo(x, y) }
                                }
                                macd.signalLine.getOrNull(j)?.let { v ->
                                    val y = yMacd(v)
                                    signalPath = (signalPath ?: Path().apply { moveTo(x, y) }).also { it.lineTo(x, y) }
                                }
                            }
                            j++
                        }
                        macdPath?.let { drawPath(it, color = MACD_LINE, style = Stroke(width = 2f)) }
                        signalPath?.let { drawPath(it, color = MACD_SIGNAL, style = Stroke(width = 2f)) }
                        val tag = textMeasurer.measure("MACD", style = axisLabelStyle)
                        if (tag.size.height < paneH && tag.size.width < plotW) {
                            drawText(tag, topLeft = Offset(leftPad + 4f, paneTop + 2f))
                        }
                    }

                    trendPairs.forEach { (startBar, endBar) ->
                        if (startBar in bars.indices && endBar in bars.indices) {
                            drawLine(
                                color = theme.primary,
                                start = Offset(xFor(startBar), yFor(barClose(startBar, bars[startBar]))),
                                end = Offset(xFor(endBar), yFor(barClose(endBar, bars[endBar]))),
                                strokeWidth = 2.5f,
                            )
                        }
                    }
                    fibPair?.let { (startBar, endBar) ->
                        if (startBar in bars.indices && endBar in bars.indices) {
                            val from = barClose(startBar, bars[startBar])
                            val to = barClose(endBar, bars[endBar])
                            listOf(0.0 to "0", 0.382 to "38.2", 0.5 to "50", 0.618 to "61.8", 1.0 to "100").forEach { (ratio, label) ->
                                val price = from + (to - from) * ratio
                                val y = yFor(price)
                                drawLine(
                                    color = theme.caution.copy(alpha = 0.85f),
                                    start = Offset(leftPad, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
                                )
                                val fibLabel = textMeasurer.measure(label, style = axisLabelStyle.copy(color = theme.caution))
                                if (fibLabel.size.height < size.height) {
                                    drawText(
                                        fibLabel,
                                        topLeft = Offset(leftPad + 4f, (y - fibLabel.size.height - 1f).clampLabel(0f, size.height - fibLabel.size.height)),
                                    )
                                }
                            }
                        }
                    }
                    if (noteIndex in first..lastVisible) {
                        val noteLabel = textMeasurer.measure(noteWord, style = axisLabelStyle.copy(color = theme.text))
                        val noteX = xFor(noteIndex)
                        val noteY = (yFor(barHigh(noteIndex, bars[noteIndex])) - noteLabel.size.height - 4f)
                            .clampLabel(topPad, topPad + plotH - noteLabel.size.height)
                        if (noteLabel.size.height < size.height) {
                            drawText(noteLabel, topLeft = Offset(noteX, noteY))
                        }
                    }
                    if (compareCloses.size >= 2) {
                        val slice = compareCloses.takeLast(history.size)
                        val cMin = slice.minOrNull() ?: 0.0
                        val cMax = slice.maxOrNull() ?: cMin
                        val cSpan = (cMax - cMin).coerceAtLeast(1e-6)
                        val origin = history.size - slice.size
                        var comparePath: Path? = null
                        slice.forEachIndexed { offset, value ->
                            val index = origin + offset
                            if (index !in first..lastVisible) return@forEachIndexed
                            val x = xFor(index)
                            val y = topPad + plotH * (1f - ((value - cMin) / cSpan).toFloat())
                            comparePath = (comparePath ?: Path().apply { moveTo(x, y) }).also { it.lineTo(x, y) }
                        }
                        comparePath?.let {
                            drawPath(it, color = Color(0xFF90A4AE), style = Stroke(width = 2f))
                        }
                    }
                    if (volumeH > 0f) {
                        val paneTop = topPad + plotH + volumeGap
                        var maxVolume = 1L
                        for (index in first..lastVisible) {
                            maxVolume = max(maxVolume, history[index].volume)
                        }
                        for (index in first..lastVisible) {
                            val candle = history[index]
                            val barHeight = candle.volume.toFloat() / maxVolume.toFloat() * (volumeH - 4f)
                            val up = barClose(index, bars[index]) >= candle.open
                            drawRect(
                                color = (if (up) theme.positive else theme.negative).copy(alpha = 0.75f),
                                topLeft = Offset(xFor(index) - barPx * 0.3f, paneTop + volumeH - barHeight),
                                size = Size((barPx * 0.6f).coerceAtLeast(1f), barHeight.coerceAtLeast(0f)),
                            )
                        }
                        val volumeTag = textMeasurer.measure("Volume", style = axisLabelStyle)
                        if (volumeTag.size.height < volumeH) {
                            drawText(volumeTag, topLeft = Offset(leftPad + 4f, paneTop))
                        }
                    }

                    if (inspectIndex in first..lastVisible) {
                        val inspectX = xFor(inspectIndex)
                        drawLine(
                            color = theme.text.copy(alpha = 0.7f),
                            start = Offset(inspectX, topPad),
                            end = Offset(inspectX, topPad + plotH),
                            strokeWidth = 1.4f,
                        )
                    }
                    if (eventDate.isNotBlank()) {
                        val eventIndex = history.indexOfFirst { candleDate(it.timestamp)?.toString() == eventDate }
                        if (eventIndex in first..lastVisible) {
                            val eventX = xFor(eventIndex)
                            drawLine(
                                color = theme.caution,
                                start = Offset(eventX, topPad),
                                end = Offset(eventX, topPad + plotH),
                                strokeWidth = 1.6f,
                            )
                            val eventLabel = textMeasurer.measure(
                                eventCaption.ifBlank { "Result" },
                                style = axisLabelStyle.copy(color = theme.caution),
                            )
                            if (eventLabel.size.height < size.height) {
                                drawText(
                                    eventLabel,
                                    topLeft = Offset(
                                        (eventX + 4f).clampLabel(leftPad, size.width - eventLabel.size.width),
                                        topPad + 2f,
                                    ),
                                )
                            }
                        }
                    }

                    if (livePrice > 0.0 && lastVisible == history.lastIndex) {
                        val liveY = yFor(livePrice)
                        drawLine(
                            color = theme.primary,
                            start = Offset(leftPad, liveY),
                            end = Offset(size.width, liveY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f),
                        )
                        val liveLabel = textMeasurer.measure(
                            formatAxisPrice(livePrice),
                            style = axisLabelStyle.copy(color = theme.primary),
                        )
                        if (liveLabel.size.height < size.height) {
                            drawText(
                                liveLabel,
                                topLeft = Offset(
                                    (size.width - liveLabel.size.width - 2f).coerceAtLeast(leftPad),
                                    (liveY - liveLabel.size.height - 2f).clampLabel(0f, size.height - liveLabel.size.height),
                                ),
                            )
                        }
                    }

                    // X-axis date ticks for this range
                    val xTicks = buildXAxisTicks(history, rangeLabel, first, lastVisible)
                    val axisY = topPad + plotH + macdGap + macdH
                    xTicks.forEach { tick ->
                        val x = xFor(tick.index)
                        if (x < leftPad - 4f || x > size.width + 4f) return@forEach
                        drawLine(
                            color = axisLineColor,
                            start = Offset(x, axisY),
                            end = Offset(x, axisY + 4f),
                            strokeWidth = 1.2f,
                        )
                        val layout = textMeasurer.measure(tick.label, style = axisLabelStyle)
                        if (layout.size.height >= size.height || layout.size.width >= size.width) return@forEach
                        val tx = (x - layout.size.width / 2f)
                            .clampLabel(leftPad, size.width - layout.size.width)
                        val ty = (axisY + 6f).clampLabel(0f, size.height - layout.size.height)
                        drawText(layout, topLeft = Offset(tx, ty))
                    }
                }
            }
        }
    }
}

private fun parseIndexPairs(raw: String): List<Pair<Int, Int>> {
    if (raw.isBlank()) return emptyList()
    return raw.split(',').mapNotNull { part ->
        val bits = part.split('-')
        val left = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val right = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        left to right
    }
}

internal fun candleIndexAt(x: Float, start: Float, barPx: Float, leftPad: Float, count: Int): Int {
    if (count <= 0) return 0
    val raw = start + (x - leftPad) / barPx.coerceAtLeast(0.5f)
    return raw.toInt().coerceIn(0, count - 1)
}

internal suspend fun PointerInputScope.detectChartGestures(
    onPanZoom: (panX: Float, zoom: Float, focalX: Float) -> Unit,
    onDoubleTap: () -> Unit,
    onTap: (Offset) -> Boolean = { false },
    onLongPress: (Offset) -> Unit = {},
) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    var lastTapUptime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var chartOwns = false
        var moved = false
        var longFired = false
        var pointerDown = true
        while (pointerDown) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!chartOwns && event.changes.any { it.isConsumed }) break
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            val pressed = event.changes.count { it.pressed }
            totalX += pan.x
            totalY += pan.y
            if (!chartOwns && (pressed >= 2 || (abs(totalX) > slop && abs(totalX) >= abs(totalY)))) {
                chartOwns = true
            }
            if (abs(totalX) > slop || abs(totalY) > slop || zoom != 1f) moved = true
            val held = event.changes.maxOf { it.uptimeMillis } - down.uptimeMillis
            if (!longFired && !moved && held >= longPressMs) {
                longFired = true
                chartOwns = true
                onLongPress(down.position)
            }
            if (longFired || chartOwns) {
                event.changes.forEach { it.consume() }
                if (!longFired && (zoom != 1f || pan.x != 0f)) {
                    onPanZoom(pan.x, zoom, event.calculateCentroid(useCurrent = true).x)
                }
            }
            pointerDown = event.changes.any { it.pressed }
        }
        if (!moved && !longFired && !onTap(down.position)) {
            val now = down.uptimeMillis
            if (now - lastTapUptime < viewConfiguration.doubleTapTimeoutMillis) {
                onDoubleTap()
                lastTapUptime = 0L
            } else {
                lastTapUptime = now
            }
        } else {
            lastTapUptime = 0L
        }
    }
}
