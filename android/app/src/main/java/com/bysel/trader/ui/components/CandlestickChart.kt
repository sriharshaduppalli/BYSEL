package com.bysel.trader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.utils.TechnicalIndicators
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private enum class Overlay(val label: String, val color: Color) {
    SMA20("SMA 20", Color(0xFFFFD600)),
    SMA50("SMA 50", Color(0xFF00B0FF)),
    EMA20("EMA 20", Color(0xFFFF6D00)),
    BB("Bollinger", Color(0xFFAB47BC)),
}

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
    return Instant.ofEpochMilli(ms).atZone(IST).toLocalDate()
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
 * Latest bars stay pinned to the right; pinch/slider zoom; drag to pan.
 * Draws Y-axis prices and range-aware X-axis dates.
 */
@Composable
fun CandlestickChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    initialBarWidthDp: Float = 10f,
    rangeLabel: String = "1M",
) {
    if (history.isEmpty()) return

    var activeOverlays by remember { mutableStateOf(setOf<Overlay>()) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val closes = remember(history) { history.map { it.close } }
    val highs = remember(history) { history.map { it.high } }
    val lows = remember(history) { history.map { it.low } }

    val sma20 = remember(closes) { TechnicalIndicators.sma(closes, 20) }
    val sma50 = remember(closes) { TechnicalIndicators.sma(closes, 50) }
    val ema20 = remember(closes) { TechnicalIndicators.ema(closes, 20) }
    val bb = remember(closes) { TechnicalIndicators.bollingerBands(closes, 20) }
    val rsi = remember(closes) { TechnicalIndicators.rsi(closes, 14) }

    val allPrices = remember(highs, lows, bb, activeOverlays) {
        val prices = highs + lows
        if (Overlay.BB in activeOverlays) {
            prices + bb.upper.filterNotNull() + bb.lower.filterNotNull()
        } else {
            prices
        }
    }
    val maxPrice = remember(allPrices) { allPrices.maxOrNull() ?: 0.0 }
    val minPrice = remember(allPrices) { allPrices.minOrNull() ?: 0.0 }
    val priceRange = remember(maxPrice, minPrice) { max(1e-6, maxPrice - minPrice) }

    val historyKey = remember(history, rangeLabel) {
        "${rangeLabel}:${history.size}:${history.firstOrNull()?.timestamp}:${history.lastOrNull()?.timestamp}"
    }

    val axisLabelStyle = remember {
        TextStyle(color = Color(0xFF9E9E9E), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    val gridColor = Color(0x22FFFFFF)
    val axisLineColor = Color(0x33FFFFFF)

    Column(modifier = modifier) {
        val lastCandle = history.last()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "O: ${String.format("%.2f", lastCandle.open)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "H: ${String.format("%.2f", lastCandle.high)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "L: ${String.format("%.2f", lastCandle.low)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "C: ${String.format("%.2f", lastCandle.close)}", color = Color.Gray, fontSize = 11.sp)
        }

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

        val lastRsi = rsi.lastOrNull { it != null }
        if (lastRsi != null) {
            val rsiColor = when {
                lastRsi >= 70 -> Color(0xFFE53935)
                lastRsi <= 30 -> Color(0xFF00C853)
                else -> Color.Gray
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

        Text(
            text = "$rangeLabel · ${history.size} candles · drag to pan · pinch / slider to zoom",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val yAxisPadPx = with(density) { 48.dp.toPx() }
            val plotWidthPx = (widthPx - yAxisPadPx).coerceAtLeast(1f)

            val fittedBarWidth = remember(historyKey, plotWidthPx, initialBarWidthDp) {
                val targetVisible = when {
                    history.size <= 28 -> history.size.coerceAtLeast(8)
                    history.size <= 80 -> 48
                    history.size <= 160 -> 56
                    else -> 64
                }.coerceAtMost(history.size).coerceAtLeast(1)
                val raw = plotWidthPx / targetVisible / density.density
                raw.coerceIn(3.5f, 18f)
            }

            val barWidthState = remember(historyKey) { mutableFloatStateOf(fittedBarWidth) }
            val startIndexState = remember(historyKey) { mutableFloatStateOf(0f) }
            val barWidth = barWidthState.floatValue
            val startIndex = startIndexState.floatValue

            fun maxStartFor(barDp: Float): Float {
                val barPx = barDp * density.density
                val visible = (plotWidthPx / barPx).coerceAtLeast(1f)
                return (history.size - visible).coerceAtLeast(0f)
            }

            LaunchedEffect(historyKey, fittedBarWidth) {
                barWidthState.floatValue = fittedBarWidth
                startIndexState.floatValue = maxStartFor(fittedBarWidth)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = barWidth,
                    onValueChange = {
                        barWidthState.floatValue = it
                        startIndexState.floatValue = startIndexState.floatValue.coerceIn(0f, maxStartFor(it))
                    },
                    valueRange = 3.5f..28f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color(0x141A1A1A), RoundedCornerShape(12.dp))
                        .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)
                        .pointerInput(historyKey, plotWidthPx, history.size) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextWidth = (barWidthState.floatValue * zoom).coerceIn(3.5f, 36f)
                                val barPx = nextWidth * density.density
                                val nextMax = (history.size - (plotWidthPx / barPx)).coerceAtLeast(0f)
                                barWidthState.floatValue = nextWidth
                                startIndexState.floatValue =
                                    (startIndexState.floatValue - pan.x / barPx).coerceIn(0f, nextMax)
                            }
                        },
                ) {
                    val leftPad = yAxisPadPx
                    val bottomPad = 26.dp.toPx()
                    val topPad = 6f
                    val plotW = (size.width - leftPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val barPx = (barWidth.dp.toPx()).coerceAtLeast(2f)
                    val first = startIndex.toInt().coerceIn(0, history.lastIndex)
                    val visibleCount = (plotW / barPx).toInt().coerceAtLeast(1)
                    val lastVisible = (first + visibleCount).coerceAtMost(history.lastIndex)

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
                        val ty = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                        drawText(layout, topLeft = Offset(tx, ty))
                    }

                    // Candles + overlays
                    var i = first
                    while (i < history.size) {
                        val xCenter = xFor(i)
                        if (xCenter > size.width + barPx) break
                        if (xCenter >= leftPad - barPx) {
                            val candle = history[i]
                            val isUp = candle.close >= candle.open
                            val color = if (isUp) Color(0xFF00C853) else Color(0xFFE53935)
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

                    // X-axis date ticks for this range
                    val xTicks = buildXAxisTicks(history, rangeLabel, first, lastVisible)
                    val axisY = topPad + plotH
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
                        val tx = (x - layout.size.width / 2f)
                            .coerceIn(leftPad, (size.width - layout.size.width).coerceAtLeast(leftPad))
                        val ty = (axisY + 6f).coerceAtMost(size.height - layout.size.height)
                        drawText(layout, topLeft = Offset(tx, ty))
                    }
                }
            }
        }
    }
}
