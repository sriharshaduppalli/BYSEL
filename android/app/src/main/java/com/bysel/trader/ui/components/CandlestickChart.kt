package com.bysel.trader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
}

private val MACD_LINE = Color(0xFF29B6F6)
private val MACD_SIGNAL = Color(0xFFFFA726)

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DAY_SHORT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
private val MONTH_YEAR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy")

private data class AxisTick(val index: Int, val label: String)

private fun candleMillis(ts: Long): Long =
    when {
        ts <= 0L -> 0L
        ts < 10_000_000_000L -> ts * 1000L
        else -> ts
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
    price >= 10_000 -> String.format("%.0f", price)
    price >= 1_000 -> String.format("%.1f", price)
    price >= 100 -> String.format("%.1f", price)
    else -> String.format("%.2f", price)
}

/**
 * Build X-axis ticks for the visible window based on chart range.
 * 5D → one label per session day; 1M/3M → spaced calendar dates; 1Y → month starts.
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
    val range = rangeLabel.trim().uppercase()

    return when (range) {
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
 * Pinch zooms the bars. Drag left or right moves the window. A double tap fits the latest bars again.
 * Draws Y-axis prices and range-aware X-axis dates.
 */
@Composable
fun CandlestickChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    initialBarWidthDp: Float = 10f,
    rangeLabel: String = "1M",
    expandPlot: Boolean = false,
) {
    if (history.isEmpty()) return

    var activeOverlays by remember { mutableStateOf(setOf<Overlay>()) }
    var heikinAshi by remember { mutableStateOf(false) }
    var showMacd by remember { mutableStateOf(false) }
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

    val seriesKey = remember(history, rangeLabel) {
        "$rangeLabel:${history.firstOrNull()?.timestamp}"
    }

    val theme = LocalAppTheme.current
    val axisLabelStyle = remember(theme.textSecondary) {
        TextStyle(color = theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    val gridColor = theme.text.copy(alpha = 0.12f)
    val axisLineColor = theme.text.copy(alpha = 0.20f)

    Column(modifier = if (expandPlot) modifier.fillMaxSize() else modifier) {
        val lastCandle = history.last()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "O: ${String.format("%.2f", lastCandle.open)}", color = theme.textSecondary, fontSize = 11.sp)
            Text(text = "H: ${String.format("%.2f", lastCandle.high)}", color = theme.textSecondary, fontSize = 11.sp)
            Text(text = "L: ${String.format("%.2f", lastCandle.low)}", color = theme.textSecondary, fontSize = 11.sp)
            Text(text = "C: ${String.format("%.2f", lastCandle.close)}", color = theme.textSecondary, fontSize = 11.sp)
        }

        if (!expandPlot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        label = { Text(overlay.label, fontSize = 10.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = overlay.color.copy(alpha = 0.2f),
                            selectedLabelColor = overlay.color,
                        ),
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = heikinAshi,
                    onClick = { heikinAshi = !heikinAshi },
                    label = { Text("Heikin-Ashi", fontSize = 10.sp, maxLines = 1) },
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = showMacd,
                    onClick = { showMacd = !showMacd },
                    label = { Text("MACD", fontSize = 10.sp, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MACD_LINE.copy(alpha = 0.2f),
                        selectedLabelColor = MACD_LINE,
                    ),
                    modifier = Modifier.height(28.dp),
                )
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
                        text = "MACD(12,26,9): ${String.format("%.2f", lastMacd)} · signal ${String.format("%.2f", lastSignal)} — " +
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
                    text = "RSI(14): ${String.format("%.1f", lastRsi)} — $rsiLabel",
                    color = rsiColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Text(
            text = "Pinch to zoom · drag left or right · double-tap to fit",
            color = theme.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )

        BoxWithConstraints(
            modifier = if (expandPlot) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val widthOk = widthPx.isFinite() && widthPx > 0f
            val yAxisPadPx = with(density) { 48.dp.toPx() }
            val plotWidthPx = if (widthOk) (widthPx - yAxisPadPx).coerceAtLeast(1f) else 1f

            val fittedBarWidth = remember(seriesKey, plotWidthPx, initialBarWidthDp) {
                val targetVisible = when {
                    history.size <= 28 -> history.size.coerceAtLeast(8)
                    history.size <= 80 -> 48
                    history.size <= 160 -> 56
                    else -> 64
                }.coerceAtMost(history.size).coerceAtLeast(1)
                val raw = plotWidthPx / targetVisible / density.density
                if (raw.isFinite()) raw.coerceIn(2.5f, 18f) else 8f
            }

            val barWidthState = remember(seriesKey) { mutableFloatStateOf(fittedBarWidth) }
            val startIndexState = remember(seriesKey) { mutableFloatStateOf(0f) }
            val followLatest = remember(seriesKey) { mutableStateOf(true) }
            val candleCount = remember(seriesKey) { mutableIntStateOf(history.size) }
            val fittedState = remember(seriesKey) { mutableFloatStateOf(fittedBarWidth) }
            candleCount.intValue = history.size
            fittedState.floatValue = fittedBarWidth
            val barWidth = barWidthState.floatValue
            val startIndex = startIndexState.floatValue

            fun maxStartFor(barDp: Float): Float {
                val barPx = barDp * density.density
                val visible = (plotWidthPx / barPx).coerceAtLeast(1f)
                return (history.size - visible).coerceAtLeast(0f)
            }

            fun zoomAroundCenter(nextWidth: Float) {
                val oldPx = (barWidthState.floatValue * density.density).coerceAtLeast(1f)
                val newPx = (nextWidth * density.density).coerceAtLeast(1f)
                val mid = startIndexState.floatValue + (plotWidthPx / 2f) / oldPx
                val nextMax = (history.size - plotWidthPx / newPx).coerceAtLeast(0f)
                val nextStart = (mid - (plotWidthPx / 2f) / newPx).clampLabel(0f, nextMax)
                barWidthState.floatValue = nextWidth
                startIndexState.floatValue = nextStart
                followLatest.value = nextStart >= nextMax - 0.6f
            }

            LaunchedEffect(seriesKey, fittedBarWidth) {
                barWidthState.floatValue = fittedBarWidth
                startIndexState.floatValue = maxStartFor(fittedBarWidth)
                followLatest.value = true
            }
            LaunchedEffect(history.size, plotWidthPx) {
                val maxStart = maxStartFor(barWidthState.floatValue)
                startIndexState.floatValue = if (followLatest.value) {
                    maxStart
                } else {
                    startIndexState.floatValue.clampLabel(0f, maxStart)
                }
            }

            val sliderValue = if (barWidth.isFinite()) barWidth.coerceIn(2.5f, 48f) else 8f
            if (!widthOk) {
                return@BoxWithConstraints
            }

            Column(modifier = if (expandPlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { zoomAroundCenter(it) },
                    valueRange = 2.5f..48f,
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
                        .pointerInput(seriesKey, plotWidthPx) {
                            fun maxStart(barDp: Float, count: Int): Float {
                                val px = (barDp * density.density).coerceAtLeast(1f)
                                val visible = (plotWidthPx / px).coerceAtLeast(1f)
                                return (count - visible).coerceAtLeast(0f)
                            }
                            detectChartGestures(
                                onPanZoom = { panX, zoom ->
                                    val count = candleCount.intValue
                                    val nextWidth = (barWidthState.floatValue * zoom).coerceIn(2.5f, 48f)
                                    val barPx = (nextWidth * density.density).coerceAtLeast(1f)
                                    if (zoom != 1f) {
                                        val oldPx = (barWidthState.floatValue * density.density).coerceAtLeast(1f)
                                        val mid = startIndexState.floatValue + (plotWidthPx / 2f) / oldPx
                                        val zoomedMax = (count - plotWidthPx / barPx).coerceAtLeast(0f)
                                        val zoomedStart = (mid - (plotWidthPx / 2f) / barPx - panX / barPx)
                                            .clampLabel(0f, zoomedMax)
                                        barWidthState.floatValue = nextWidth
                                        startIndexState.floatValue = zoomedStart
                                        followLatest.value = zoomedStart >= zoomedMax - 0.6f
                                    } else {
                                        val nextMax = (count - plotWidthPx / barPx).coerceAtLeast(0f)
                                        val nextStart = (startIndexState.floatValue - panX / barPx).clampLabel(0f, nextMax)
                                        startIndexState.floatValue = nextStart
                                        followLatest.value = nextStart >= nextMax - 0.6f
                                    }
                                },
                                onDoubleTap = {
                                    val fit = fittedState.floatValue
                                    barWidthState.floatValue = fit
                                    startIndexState.floatValue = maxStart(fit, candleCount.intValue)
                                    followLatest.value = true
                                },
                            )
                        },
                ) {
                    if (size.width < 2f || size.height < 2f) return@Canvas
                    val leftPad = yAxisPadPx
                    val bottomPad = 26.dp.toPx()
                    val topPad = 6f
                    val plotW = (size.width - leftPad).coerceAtLeast(1f)
                    val fullH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    // MACD takes the bottom quarter of the price area so both share one x-axis.
                    val macdGap = if (showMacd && fullH > 120f) 10f else 0f
                    val macdH = if (showMacd && fullH > 120f) fullH * 0.26f else 0f
                    val plotH = (fullH - macdH - macdGap).coerceAtLeast(1f)
                    val barPx = (barWidth.dp.toPx()).coerceAtLeast(2f)
                    val first = startIndex.toInt().coerceIn(0, history.lastIndex)
                    val visibleCount = (plotW / barPx).toInt().coerceAtLeast(1)
                    val lastVisible = (first + visibleCount - 1).coerceIn(first, history.lastIndex)
                    var visMin = bars[first].low
                    var visMax = bars[first].high
                    for (index in first..lastVisible) {
                        val candle = bars[index]
                        visMin = min(visMin, candle.low)
                        visMax = max(visMax, candle.high)
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

                    // Candles + overlays
                    var i = first
                    while (i < history.size) {
                        val xCenter = xFor(i)
                        if (xCenter > size.width + barPx) break
                        if (xCenter >= leftPad - barPx) {
                            val candle = bars[i]
                            val isUp = candle.close >= candle.open
                            val color = if (isUp) theme.positive else theme.negative
                            val highY = yFor(candle.high)
                            val lowY = yFor(candle.low)
                            val openY = yFor(candle.open)
                            val closeY = yFor(candle.close)
                            val wickW = (barPx * 0.12f).coerceIn(1.2f, 3f)
                            val bodyW = (barPx * 0.68f).coerceAtLeast(2.5f)
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
                        }
                        i++
                    }

                    // MACD pane: histogram bars, MACD line, signal line, zero line.
                    if (macdH > 0f) {
                        val paneTop = topPad + plotH + macdGap
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

private suspend fun PointerInputScope.detectChartGestures(
    onPanZoom: (panX: Float, zoom: Float) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    var lastTapUptime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var chartOwns = false
        var moved = false
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
            if (chartOwns) {
                event.changes.forEach { it.consume() }
                if (zoom != 1f || pan.x != 0f) onPanZoom(pan.x, zoom)
            }
            pointerDown = event.changes.any { it.pressed }
        }
        if (!moved) {
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
