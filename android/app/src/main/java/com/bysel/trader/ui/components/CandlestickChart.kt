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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.utils.TechnicalIndicators
import kotlin.math.max

private enum class Overlay(val label: String, val color: Color) {
    SMA20("SMA 20", Color(0xFFFFD600)),
    SMA50("SMA 50", Color(0xFF00B0FF)),
    EMA20("EMA 20", Color(0xFFFF6D00)),
    BB("Bollinger", Color(0xFFAB47BC)),
}

/**
 * Canvas candlestick chart that fits 5D / 1M / 3M / 1Y ranges.
 * Latest bars stay pinned to the right; pinch/slider zoom; drag to pan.
 */
@Composable
fun CandlestickChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    initialBarWidthDp: Float = 10f,
) {
    if (history.isEmpty()) return

    var activeOverlays by remember { mutableStateOf(setOf<Overlay>()) }
    val density = LocalDensity.current

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

    val historyKey = remember(history) {
        "${history.size}:${history.firstOrNull()?.timestamp}:${history.lastOrNull()?.timestamp}"
    }

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
            text = "${history.size} candles · drag to pan · pinch / slider to zoom · latest on right",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

            val fittedBarWidth = remember(historyKey, widthPx, initialBarWidthDp) {
                val targetVisible = when {
                    history.size <= 28 -> history.size.coerceAtLeast(8)
                    history.size <= 80 -> 48
                    history.size <= 160 -> 56
                    else -> 64
                }.coerceAtMost(history.size).coerceAtLeast(1)
                val raw = widthPx / targetVisible / density.density
                raw.coerceIn(3.5f, 18f)
            }

            val barWidthState = remember(historyKey) { mutableFloatStateOf(fittedBarWidth) }
            val startIndexState = remember(historyKey) { mutableFloatStateOf(0f) }
            val barWidth = barWidthState.floatValue
            val startIndex = startIndexState.floatValue

            fun maxStartFor(barDp: Float): Float {
                val barPx = barDp * density.density
                val visible = (widthPx / barPx).coerceAtLeast(1f)
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
                        .height(300.dp)
                        .background(Color(0x141A1A1A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .pointerInput(historyKey, widthPx, history.size) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextWidth = (barWidthState.floatValue * zoom).coerceIn(3.5f, 36f)
                                val barPx = nextWidth * density.density
                                val nextMax = (history.size - (size.width / barPx)).coerceAtLeast(0f)
                                barWidthState.floatValue = nextWidth
                                startIndexState.floatValue =
                                    (startIndexState.floatValue - pan.x / barPx).coerceIn(0f, nextMax)
                            }
                        },
                ) {
                    val barPx = (barWidth.dp.toPx()).coerceAtLeast(2f)
                    val first = startIndex.toInt().coerceIn(0, history.lastIndex)
                    val padY = 4f
                    val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                    fun yFor(price: Double): Float {
                        val norm = ((price - minPrice) / priceRange).toFloat().coerceIn(0f, 1f)
                        return padY + plotH * (1f - norm)
                    }

                    var i = first
                    while (i < history.size) {
                        val xCenter = (i - startIndex) * barPx + barPx / 2f
                        if (xCenter > size.width + barPx) break
                        if (xCenter >= -barPx) {
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
                }
            }
        }
    }
}
