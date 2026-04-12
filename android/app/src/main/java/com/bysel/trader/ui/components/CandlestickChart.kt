package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun CandlestickChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    initialBarWidthDp: Float = 12f
) {
    if (history.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) { return }
    }

    var barWidth by remember { mutableStateOf(initialBarWidthDp) }
    var activeOverlays by remember { mutableStateOf(setOf<Overlay>()) }

    val closes = remember(history) { history.map { it.close } }
    val highs = remember(history) { history.map { it.high } }
    val lows = remember(history) { history.map { it.low } }

    // Pre-compute indicators
    val sma20 = remember(closes) { TechnicalIndicators.sma(closes, 20) }
    val sma50 = remember(closes) { TechnicalIndicators.sma(closes, 50) }
    val ema20 = remember(closes) { TechnicalIndicators.ema(closes, 20) }
    val bb = remember(closes) { TechnicalIndicators.bollingerBands(closes, 20) }
    val rsi = remember(closes) { TechnicalIndicators.rsi(closes, 14) }

    // Price range accounting for Bollinger upper band
    val allPrices = remember(highs, lows, bb, activeOverlays) {
        val prices = highs + lows
        if (Overlay.BB in activeOverlays) {
            prices + bb.upper.filterNotNull() + bb.lower.filterNotNull()
        } else prices
    }
    val maxPrice = remember(allPrices) { allPrices.maxOrNull() ?: 0.0 }
    val minPrice = remember(allPrices) { allPrices.minOrNull() ?: 0.0 }
    val priceRange = remember(maxPrice, minPrice) { max(1.0, maxPrice - minPrice) }

    Column(modifier = modifier) {
        // OHLCV header
        val lastCandle = history.last()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "O: ${String.format("%.2f", lastCandle.open)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "H: ${String.format("%.2f", lastCandle.high)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "L: ${String.format("%.2f", lastCandle.low)}", color = Color.Gray, fontSize = 11.sp)
            Text(text = "C: ${String.format("%.2f", lastCandle.close)}", color = Color.Gray, fontSize = 11.sp)
        }

        // Indicator toggle chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Overlay.entries.forEach { overlay ->
                val selected = overlay in activeOverlays
                FilterChip(
                    selected = selected,
                    onClick = {
                        activeOverlays = if (selected) activeOverlays - overlay else activeOverlays + overlay
                    },
                    label = { Text(overlay.label, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = overlay.color.copy(alpha = 0.2f),
                        selectedLabelColor = overlay.color
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        // RSI summary
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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Zoom slider
        Slider(value = barWidth, onValueChange = { barWidth = it }, valueRange = 4f..40f)

        // Chart with candles + overlays
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        barWidth = (barWidth * zoom).coerceIn(4f, 80f)
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(8.dp)) }
            itemsIndexed(history) { idx: Int, candle: HistoryCandle ->
                val isUp = candle.close >= candle.open
                val candleColor = if (isUp) Color(0xFF00C853) else Color(0xFFE53935)
                val h = ((candle.high - minPrice) / priceRange).toFloat()
                val l = ((candle.low - minPrice) / priceRange).toFloat()
                val o = ((candle.open - minPrice) / priceRange).toFloat()
                val c = ((candle.close - minPrice) / priceRange).toFloat()

                Box(modifier = Modifier
                    .width(barWidth.dp)
                    .fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)) {
                        val hPx = size.height * (1f - h)
                        val lPx = size.height * (1f - l)
                        val oPx = size.height * (1f - o)
                        val cPx = size.height * (1f - c)
                        val centerX = size.width / 2

                        // Wick
                        drawLine(color = candleColor, start = Offset(centerX, hPx), end = Offset(centerX, lPx), strokeWidth = 2f)

                        // Body
                        val top = minOf(oPx, cPx)
                        val bottom = maxOf(oPx, cPx)
                        val bodyWidth = size.width * 0.6f
                        if (bodyWidth > 2f) {
                            drawRect(color = candleColor, topLeft = Offset(centerX - bodyWidth / 2, top), size = androidx.compose.ui.geometry.Size(bodyWidth, bottom - top))
                        } else {
                            drawLine(color = candleColor, start = Offset(centerX - 2f, top), end = Offset(centerX + 2f, top), strokeWidth = 3f)
                        }

                        // Overlay dots at this index position
                        fun drawOverlayDot(value: Double?, color: Color) {
                            if (value == null) return
                            val y = size.height * (1f - ((value - minPrice) / priceRange).toFloat())
                            drawCircle(color = color, radius = 2.5f, center = Offset(centerX, y))
                        }

                        if (Overlay.SMA20 in activeOverlays) drawOverlayDot(sma20.getOrNull(idx), Overlay.SMA20.color)
                        if (Overlay.SMA50 in activeOverlays) drawOverlayDot(sma50.getOrNull(idx), Overlay.SMA50.color)
                        if (Overlay.EMA20 in activeOverlays) drawOverlayDot(ema20.getOrNull(idx), Overlay.EMA20.color)
                        if (Overlay.BB in activeOverlays) {
                            drawOverlayDot(bb.upper.getOrNull(idx), Overlay.BB.color)
                            drawOverlayDot(bb.middle.getOrNull(idx), Overlay.BB.color.copy(alpha = 0.5f))
                            drawOverlayDot(bb.lower.getOrNull(idx), Overlay.BB.color)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.width(8.dp)) }
        }
    }
}
