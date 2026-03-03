package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bysel.trader.data.models.HistoryCandle
import kotlin.math.max

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
    val scrollState = rememberScrollState()

    val closes = remember(history) { history.map { it.close } }
    val highs = remember(history) { history.map { it.high } }
    val lows = remember(history) { history.map { it.low } }
    val maxPrice = remember(history) { (highs.maxOrNull() ?: closes.maxOrNull() ?: 0.0) }
    val minPrice = remember(history) { (lows.minOrNull() ?: closes.minOrNull() ?: 0.0) }
    val priceRange = remember(maxPrice, minPrice) { max(1.0, maxPrice - minPrice) }

    Column(modifier = modifier) {
        // show latest candle summary as a lightweight tooltip/header
        val lastCandle = history.last()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "O: ${String.format("%.2f", lastCandle.open)}", color = Color.Gray)
            Text(text = "H: ${String.format("%.2f", lastCandle.high)}", color = Color.Gray)
            Text(text = "L: ${String.format("%.2f", lastCandle.low)}", color = Color.Gray)
            Text(text = "C: ${String.format("%.2f", lastCandle.close)}", color = Color.Gray)
            Text(text = "Vol: ${lastCandle.volume}", color = Color.Gray)
        }
        // Zoom slider (simple)
        Slider(value = barWidth, onValueChange = { barWidth = it }, valueRange = 4f..40f)

        // Use LazyRow to avoid composing all candle items at once for long histories
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val new = (barWidth * zoom).coerceIn(4f, 80f)
                        barWidth = new
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(8.dp)) }
            itemsIndexed(history) { _: Int, candle: HistoryCandle ->
                val isUp = candle.close >= candle.open
                val color = if (isUp) Color(0xFF00C853) else Color(0xFFE53935)
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

                        drawLine(color = color, start = Offset(centerX, hPx), end = Offset(centerX, lPx), strokeWidth = 2f)

                        val top = minOf(oPx, cPx)
                        val bottom = maxOf(oPx, cPx)
                        val bodyWidth = size.width * 0.6f
                        if (bodyWidth > 2f) {
                            drawRect(color = color, topLeft = Offset(centerX - bodyWidth / 2, top), size = androidx.compose.ui.geometry.Size(bodyWidth, bottom - top))
                        } else {
                            drawLine(color = color, start = Offset(centerX - 2f, top), end = Offset(centerX + 2f, top), strokeWidth = 3f)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.width(8.dp)) }
        }
    }
}
