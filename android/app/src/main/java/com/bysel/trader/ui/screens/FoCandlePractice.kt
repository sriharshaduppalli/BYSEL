package com.bysel.trader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.StrategyPayoffPoint
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.components.exclusiveHorizontalScroll
import com.bysel.trader.ui.theme.LocalAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

private data class CandleSim(
    val right: String,
    val strike: Double,
    val entryPremium: Double,
    val entrySpot: Double,
)

@Composable
fun FoCandlePractice(
    symbol: String,
    expiry: String,
    candles: List<HistoryCandle>,
    loading: Boolean,
    iv: Double,
    onReload: () -> Unit,
) {
    val theme = LocalAppTheme.current
    var cursor by remember(symbol, candles.size) { mutableIntStateOf((candles.size - 1).coerceAtLeast(0)) }
    var sim by remember(symbol) { mutableStateOf<CandleSim?>(null) }
    val safeCursor = cursor.coerceIn(0, (candles.size - 1).coerceAtLeast(0))
    val shown = if (candles.isEmpty()) emptyList() else candles.take(safeCursor + 1)
    val bar = candles.getOrNull(safeCursor)
    val spot = bar?.close ?: 0.0
    val strike = if (spot > 0.0) practiceStrike(spot) else 0.0
    val days = if (bar == null) 7.0 else daysAhead(bar.timestamp, expiry)
    val vol = if (iv > 0.0) iv else 0.16
    val lot = practiceLot(symbol, spot)
    val callNow = if (spot > 0.0) simulatedPremium(spot, strike, days, vol, call = true) else 0.0
    val putNow = if (spot > 0.0) simulatedPremium(spot, strike, days, vol, call = false) else 0.0

    Card(colors = CardDefaults.cardColors(containerColor = theme.card)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Candle practice",
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "These are ${symbol.uppercase()} candles. A replay buy uses a simulated premium from that bar's close. It does not spend practice cash.",
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.primary)
            }
            if (shown.isEmpty() && !loading) {
                Text(
                    "No 15-minute candles for this name yet.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onReload) { Text("Load candles") }
            }
            if (shown.isNotEmpty()) {
                PriceHistoryChart(
                    history = shown,
                    symbol = symbol,
                    modifier = Modifier.fillMaxWidth(),
                    isDarkTheme = !theme.isLight,
                    rangeLabel = "5D",
                )
                Text(
                    "Bar ${safeCursor + 1} of ${candles.size} · close ₹${String.format("%.2f", spot)} · ATM ₹${String.format("%.0f", strike)}",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (candles.size > 1) {
                    Slider(
                        value = safeCursor.toFloat(),
                        onValueChange = { cursor = it.toInt() },
                        valueRange = 0f..(candles.size - 1).toFloat(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).exclusiveHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { cursor = 0 }) { Text("Replay start", maxLines = 1) }
                    OutlinedButton(onClick = { cursor = (safeCursor - 1).coerceAtLeast(0) }, enabled = safeCursor > 0) {
                        Text("Previous bar", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { cursor = (safeCursor + 1).coerceAtMost(candles.lastIndex) },
                        enabled = safeCursor < candles.lastIndex,
                    ) {
                        Text("Next bar", maxLines = 1)
                    }
                }
                Text(
                    "Simulated call ₹${String.format("%.2f", callNow)} · simulated put ₹${String.format("%.2f", putNow)} · 1 lot is $lot units",
                    color = theme.text,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).exclusiveHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        sim = CandleSim("CALL", strike, callNow, spot)
                    }) { Text("Simulate buy call", maxLines = 1) }
                    OutlinedButton(onClick = {
                        sim = CandleSim("PUT", strike, putNow, spot)
                    }) { Text("Simulate buy put", maxLines = 1) }
                }
                sim?.let { open ->
                    val mark = simulatedPremium(spot, open.strike, days, vol, open.right == "CALL")
                    val pnl = (mark - open.entryPremium) * lot
                    Text(
                        "Simulated ${open.right.lowercase()} bought at ₹${String.format("%.2f", open.entryPremium)}. This bar marks ₹${String.format("%.2f", mark)}. 1 lot P&L ₹${String.format("%.0f", pnl)}.",
                        color = if (pnl >= 0) theme.positive else theme.negative,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                if (spot > 0.0) {
                    val up = simulatedPremium(spot * 1.01, strike, days, vol, true)
                    val down = simulatedPremium(spot * 0.99, strike, days, vol, true)
                    val decay = simulatedPremium(spot, strike, (days - 5.0).coerceAtLeast(1.0), vol, true)
                    val futureMove = spot * 0.01 * lot
                    Text(
                        "Experiments on this bar, simulated: call +1% spot ₹${String.format("%.0f", (up - callNow) * lot)}, call −1% ₹${String.format("%.0f", (down - callNow) * lot)}. Five sessions of time decay ₹${String.format("%.0f", (decay - callNow) * lot)}. A future 1% move is about ₹${String.format("%.0f", futureMove)}.",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun PayoffCurveChart(
    points: List<StrategyPayoffPoint>,
    spot: Double,
) {
    val theme = LocalAppTheme.current
    if (points.size < 2) return
    val minSpot = points.minOf { it.spot }
    val maxSpot = points.maxOf { it.spot }
    val minPay = points.minOf { it.payoff }
    val maxPay = points.maxOf { it.payoff }
    val spanX = (maxSpot - minSpot).takeIf { it > 0.0 } ?: 1.0
    val spanY = (maxPay - minPay).takeIf { abs(it) > 0.0 } ?: 1.0
    Text(
        "Payoff at expiry",
        color = theme.text,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.spot - minSpot) / spanX).toFloat() * size.width
            val y = size.height - (((point.payoff - minPay) / spanY).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val zeroY = size.height - (((0.0 - minPay) / spanY).toFloat() * size.height)
        drawLine(
            color = theme.textSecondary,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
        )
        if (spot in minSpot..maxSpot) {
            val spotX = ((spot - minSpot) / spanX).toFloat() * size.width
            drawLine(
                color = theme.primary,
                start = Offset(spotX, 0f),
                end = Offset(spotX, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawPath(
            path = path,
            color = theme.text,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    Text(
        "Horizontal line is zero. Vertical line is today's spot. The curve is the recipe at expiry.",
        color = theme.textSecondary,
        fontSize = 10.sp,
    )
}

private fun practiceStrike(spot: Double): Double {
    val step = when {
        spot >= 1000.0 -> 50.0
        spot >= 300.0 -> 20.0
        else -> 10.0
    }
    return round(spot / step) * step
}

private fun practiceLot(symbol: String, spot: Double): Int {
    return when (symbol.trim().uppercase()) {
        "NIFTY", "NIFTY50" -> 50
        "BANKNIFTY", "NIFTYBANK" -> 25
        "FINNIFTY" -> 65
        "RELIANCE" -> 250
        "TCS" -> 150
        "INFY" -> 300
        "SBIN" -> 750
        else -> {
            if (spot <= 0.0) 1
            else max(10, ((round(120_000.0 / spot).toInt() + 4) / 5) * 5)
        }
    }
}

private fun daysAhead(timestamp: Long, expiry: String): Double {
    val expiryDate = runCatching { LocalDate.parse(expiry.trim().take(10)) }.getOrNull() ?: return 7.0
    val ms = when {
        timestamp <= 0L -> 0L
        timestamp < 10_000_000_000L -> timestamp * 1000L
        else -> timestamp
    }
    val barDate = if (ms > 0L) {
        Instant.ofEpochMilli(ms).atZone(IST).toLocalDate()
    } else {
        LocalDate.now(IST)
    }
    return ChronoUnit.DAYS.between(barDate, expiryDate).toDouble().coerceAtLeast(1.0)
}

private fun simulatedPremium(
    spot: Double,
    strike: Double,
    days: Double,
    iv: Double,
    call: Boolean,
): Double {
    if (spot <= 0.0 || strike <= 0.0) return 0.0
    val t = days.coerceAtLeast(1.0) / 365.0
    val vol = iv.coerceIn(0.08, 0.80)
    val sqrtT = sqrt(t)
    val d1 = (ln(spot / strike) + (0.065 + 0.5 * vol * vol) * t) / (vol * sqrtT)
    val d2 = d1 - vol * sqrtT
    val discount = exp(-0.065 * t)
    val price = if (call) {
        spot * normCdf(d1) - strike * discount * normCdf(d2)
    } else {
        strike * discount * normCdf(-d2) - spot * normCdf(-d1)
    }
    return price.coerceAtLeast(0.05)
}

private fun normCdf(x: Double): Double = 0.5 * (1.0 + erf(x / sqrt(2.0)))

private fun erf(x: Double): Double {
    val sign = if (x >= 0.0) 1.0 else -1.0
    val ax = abs(x)
    val t = 1.0 / (1.0 + 0.3275911 * ax)
    val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
    return sign * y
}
