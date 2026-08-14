package com.bysel.trader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HeatmapSector
import com.bysel.trader.data.models.HeatmapStock
import com.bysel.trader.data.models.MarketHeatmap
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.theme.AppTheme
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.ui.theme.contentColorForFill
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

private fun isNseMarketOpen(): Boolean {
    val ist = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    val dow = ist.get(Calendar.DAY_OF_WEEK)
    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
    val timeInMin = ist.get(Calendar.HOUR_OF_DAY) * 60 + ist.get(Calendar.MINUTE)
    // From 3 Aug 2026: keep live until latest equity close (F&O derivatives 15:40 IST).
    val casGoLive = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        set(2026, Calendar.AUGUST, 3, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val closeMin = if (!ist.before(casGoLive)) (15 * 60 + 40) else (15 * 60 + 30)
    return timeInMin in (9 * 60 + 15)..closeMin
}

/** Snapshot of advance/decline/unchanged share for the live breath graph. */
private data class BreathSample(
    val advanceShare: Float,
    val declineShare: Float,
    val unchangedShare: Float,
    val tqi: Int,
)

/**
 * Trade Quality Index (0–100%) — “how safe is the tape to trade”,
 * analogous to AQI but inverted toward healthier = higher %.
 */
private data class TradeQualityIndex(
    val score: Int,
    val label: String,
    val guidance: String,
    val color: Color,
)

private fun computeTradeQualityIndex(
    advanceRatio: Double,
    advances: Int,
    declines: Int,
    total: Int,
    mood: String,
): TradeQualityIndex {
    val safeTotal = total.coerceAtLeast(1)
    val raw = when {
        advanceRatio > 0.0 -> advanceRatio * 100.0
        else -> (advances.toDouble() / safeTotal) * 100.0
    }
    // Slight mood tilt so fearful markets never read as “excellent”.
    val moodBias = when (mood.uppercase()) {
        "EUPHORIC" -> 6.0
        "BULLISH" -> 3.0
        "NEUTRAL" -> 0.0
        "BEARISH" -> -6.0
        "FEARFUL" -> -12.0
        else -> 0.0
    }
    val score = (raw + moodBias).coerceIn(0.0, 100.0).roundToInt()
    return when {
        score >= 81 -> TradeQualityIndex(
            score = score,
            label = "Excellent",
            guidance = "Strong market breath — constructive for selective paper trades.",
            color = Color(0xFF00C853),
        )
        score >= 61 -> TradeQualityIndex(
            score = score,
            label = "Good",
            guidance = "Healthy breath — favor high-participation setups only.",
            color = Color(0xFF43A047),
        )
        score >= 41 -> TradeQualityIndex(
            score = score,
            label = "Moderate",
            guidance = "Mixed breath — keep size small and wait for clearer leadership.",
            color = Color(0xFFFFB300),
        )
        score >= 21 -> TradeQualityIndex(
            score = score,
            label = "Unhealthy",
            guidance = "Weak breath — defensive bias; avoid chasing breakouts.",
            color = Color(0xFFFF7043),
        )
        else -> TradeQualityIndex(
            score = score,
            label = "Hazardous",
            guidance = "Toxic breath — prioritize capital preservation over new risk.",
            color = Color(0xFFE53935),
        )
    }
}

@Composable
fun HeatmapScreen(
    heatmap: MarketHeatmap?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onForceRefresh: () -> Unit = onRefresh,
    onStockClick: (String) -> Unit,
    heatmapInterval: Int = 5_000,
    isActive: Boolean = true,
) {
    val tapeLive = isNseMarketOpen() && heatmap?.marketOpen != false
    var marketOpen by remember { mutableStateOf(tapeLive) }
    val breathHistory = remember { mutableStateListOf<BreathSample>() }

    LaunchedEffect(tapeLive) {
        marketOpen = tapeLive
    }

    LaunchedEffect(Unit) {
        if (heatmap == null) onRefresh()
    }

    // Append live breath samples as heatmap refreshes (for the distribution graph).
    // After hours TQI is a frozen close print — don't keep charting a moving tape.
    LaunchedEffect(heatmap?.lastUpdated, heatmap?.marketBreadth?.advances, heatmap?.marketBreadth?.declines, tapeLive) {
        if (!tapeLive) return@LaunchedEffect
        val breadth = heatmap?.marketBreadth ?: return@LaunchedEffect
        val total = breadth.total.toFloat().coerceAtLeast(1f)
        val advanceShare = breadth.advances / total
        val declineShare = breadth.declines / total
        val unchangedShare = (1f - advanceShare - declineShare).coerceAtLeast(0f)
        val tqi = computeTradeQualityIndex(
            advanceRatio = breadth.advanceRatio,
            advances = breadth.advances,
            declines = breadth.declines,
            total = breadth.total,
            mood = heatmap.mood,
        ).score
        val sample = BreathSample(advanceShare, declineShare, unchangedShare, tqi)
        val last = breathHistory.lastOrNull()
        if (last == null ||
            last.advanceShare != sample.advanceShare ||
            last.declineShare != sample.declineShare ||
            last.tqi != sample.tqi
        ) {
            breathHistory.add(sample)
            while (breathHistory.size > 36) {
                breathHistory.removeAt(0)
            }
        }
    }

    LaunchedEffect(heatmapInterval, isActive, heatmap?.marketOpen) {
        if (!isActive) return@LaunchedEffect
        val pollMs = heatmapInterval.toLong().coerceIn(2_000L, 10_000L)
        while (true) {
            kotlinx.coroutines.delay(pollMs)
            val live = isNseMarketOpen() && heatmap?.marketOpen != false
            val waitingForSemiconductor = heatmap?.sectors?.none {
                it.name.equals("Semiconductor", ignoreCase = true)
            } != false
            marketOpen = live
            if (live || waitingForSemiconductor) {
                // Silent poll — spinner only on first load / pull-to-refresh.
                onRefresh()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        HeatmapHeader(heatmap)

        MarketStatusBanner(
            marketOpen = marketOpen,
            staleReason = heatmap?.staleReason ?: heatmap?.moodDescription?.takeIf { heatmap.isStale },
        )

        if (isLoading && heatmap == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LocalAppTheme.current.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading market data...", color = LocalAppTheme.current.textSecondary, fontSize = 14.sp)
                }
            }
        } else if (
            heatmap != null &&
            (
                heatmap.marketBreadth.total > 0 ||
                heatmap.sectors.any { it.stocks.isNotEmpty() }
            )
        ) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onForceRefresh,
                enabled = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        MarketBreathCard(
                            heatmap = heatmap,
                            breathHistory = breathHistory.toList(),
                        )
                    }

                    items(heatmap.sectors, key = { it.name }) { sector ->
                        SectorHeatmapCard(sector, onStockClick)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoading || heatmap?.isStale == true) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = heatmap?.staleReason
                            ?: heatmap?.moodDescription?.takeIf { it.isNotBlank() }
                            ?: "No heatmap snapshot yet. Pull to refresh when the market is open, or retry now.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text("Retry heatmap") }
                }
            }
        }
    }
}

@Composable
private fun MarketStatusBanner(marketOpen: Boolean, staleReason: String? = null) {
    val ist = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    val dow = ist.get(Calendar.DAY_OF_WEEK)
    val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY

    val theme = LocalAppTheme.current
    val (bgColor, icon, message) = when {
        marketOpen -> Triple(Color(0xFF1B5E20), Icons.Filled.TrendingUp, "Market open")
        isWeekend -> Triple(
            theme.card,
            Icons.Filled.Weekend,
            staleReason ?: "Weekend  •  Market closed  •  Showing last session data",
        )
        else -> Triple(
            Color(0xFF4A1010),
            Icons.Filled.Schedule,
            staleReason
                ?: "Market Closed  •  NSE/BSE Mon–Fri from 9:15 IST  •  From 3 Aug 2026: CAS/F&O multi-close (to 3:40)  •  Showing last session data",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (bgColor.luminance() > 0.5f) theme.text else Color.White.copy(alpha = 0.9f)
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(message, color = contentColor, fontSize = 11.sp)
    }
}

@Composable
private fun HeatmapHeader(heatmap: MarketHeatmap?) {
    val moodColors = when (heatmap?.mood) {
        "EUPHORIC" -> listOf(Color(0xFF00C853), Color(0xFF1B5E20))
        "BULLISH" -> listOf(Color(0xFF43A047), Color(0xFF1B5E20))
        "NEUTRAL" -> listOf(Color(0xFFFFB300), Color(0xFF795548))
        "BEARISH" -> listOf(Color(0xFFE53935), Color(0xFF880E4F))
        "FEARFUL" -> listOf(Color(0xFFB71C1C), Color(0xFF4A148C))
        else -> listOf(Color(0xFF1A237E), Color(0xFF7C4DFF))
    }
    val onMood = contentColorForFill(moodColors.last())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(colors = moodColors)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = null,
                        tint = onMood,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Smart Sentiment Heatmap",
                            color = onMood,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (heatmap != null) {
                            Text(
                                "Market Mood: ${heatmap.moodEmoji} ${heatmap.mood}",
                                color = onMood.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            if (heatmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    heatmap.moodDescription,
                    color = onMood.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MarketBreathCard(
    heatmap: MarketHeatmap,
    breathHistory: List<BreathSample>,
) {
    val breadth = heatmap.marketBreadth
    val total = breadth.total.toFloat().coerceAtLeast(1f)
    val advancePct = breadth.advances / total
    val declinePct = breadth.declines / total
    val unchangedPct = (1f - advancePct - declinePct).coerceAtLeast(0f)
    val tqi = remember(breadth.advances, breadth.declines, breadth.total, breadth.advanceRatio, heatmap.mood) {
        computeTradeQualityIndex(
            advanceRatio = breadth.advanceRatio,
            advances = breadth.advances,
            declines = breadth.declines,
            total = breadth.total,
            mood = heatmap.mood,
        )
    }
    val animatedTqi by animateFloatAsState(
        targetValue = tqi.score / 100f,
        animationSpec = tween(650),
        label = "tqi",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Market Breath",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            val coverageLabel = when {
                heatmap.marketOpen == false && heatmap.quotedCount > 0 ->
                    "Last session close — TQI frozen until the next open (${heatmap.quotedCount} quoted names)."
                heatmap.universeSize > 0 && heatmap.quotedCount > 0 ->
                    "Breath across ${heatmap.quotedCount} quoted of ${heatmap.universeSize} active NSE listings" +
                        if (heatmap.pendingQuotes > 0) " (${heatmap.pendingQuotes} still warming)." else "."
                heatmap.universeSize > 0 ->
                    "Covering ${heatmap.universeSize} active NSE listings (quotes filling in)."
                else ->
                    "How the market is breathing — advances vs declines across the heatmap universe."
            }
            Text(
                coverageLabel,
                color = LocalAppTheme.current.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TqiGauge(
                    progress = animatedTqi,
                    score = tqi.score,
                    color = tqi.color,
                    modifier = Modifier.size(88.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Trade Quality Index",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        "${tqi.score}% · ${tqi.label}",
                        color = tqi.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tqi.guidance,
                        color = LocalAppTheme.current.text.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Text(
                        "Like AQI for trading: higher % = healthier breath to take risk.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Live breath distribution",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val theme = LocalAppTheme.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
            ) {
                if (advancePct > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(advancePct.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(theme.positive)
                    )
                }
                if (unchangedPct > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(unchangedPct)
                            .fillMaxHeight()
                            .background(theme.textSecondary.copy(alpha = 0.55f))
                    )
                }
                if (declinePct > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(declinePct.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(theme.negative)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BreathLabel("Advances", breadth.advances, theme.positive, advancePct)
                BreathLabel("Unchanged", breadth.unchanged, theme.textSecondary, unchangedPct)
                BreathLabel("Declines", breadth.declines, theme.negative, declinePct)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                if (breathHistory.size >= 2) "Breath trend (live)" else "Breath trend builds as the heatmap refreshes",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            BreathDistributionGraph(
                samples = if (breathHistory.isNotEmpty()) {
                    breathHistory
                } else {
                    listOf(BreathSample(advancePct, declinePct, unchangedPct, tqi.score))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalAppTheme.current.surface.copy(alpha = 0.55f))
                    .border(1.dp, LocalAppTheme.current.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = LocalAppTheme.current.cardOutline)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val bestChange = heatmap.bestSector.change
                val worstChange = heatmap.worstSector.change
                Column(modifier = Modifier.weight(1f)) {
                    Text("Best Sector", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                    Text(
                        "${heatmap.bestSector.name} (${String.format("%+.2f", bestChange)}%)",
                        color = LocalAppTheme.current.positive,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("Worst Sector", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                    Text(
                        "${heatmap.worstSector.name} (${String.format("%+.2f", worstChange)}%)",
                        color = LocalAppTheme.current.negative,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TqiGauge(
    progress: Float,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = color.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score%",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text("TQI", color = LocalAppTheme.current.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BreathDistributionGraph(
    samples: List<BreathSample>,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val advanceColor = theme.positive
    val declineColor = theme.negative
    val unchangedColor = theme.textSecondary.copy(alpha = 0.55f)
    val gridColor = theme.textSecondary.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val n = max(samples.size - 1, 1)

        listOf(0.25f, 0.5f, 0.75f).forEach { yFrac ->
            val y = h * (1f - yFrac)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        fun pointX(index: Int): Float = if (samples.size == 1) w / 2f else (index.toFloat() / n) * w

        fun stackedTops(sample: BreathSample): Pair<Float, Float> {
            val adv = sample.advanceShare.coerceIn(0f, 1f)
            val unc = sample.unchangedShare.coerceIn(0f, 1f - adv)
            val yAdv = h * (1f - adv)
            val yUnc = h * (1f - (adv + unc))
            return yAdv to yUnc
        }

        // Advances (green) from bottom
        val advancePath = Path().apply {
            moveTo(pointX(0), h)
            samples.forEachIndexed { index, sample ->
                lineTo(pointX(index), stackedTops(sample).first)
            }
            lineTo(pointX(samples.lastIndex), h)
            close()
        }
        // Unchanged (grey) mid band
        val unchangedPath = Path().apply {
            val first = stackedTops(samples.first())
            moveTo(pointX(0), first.first)
            samples.forEachIndexed { index, sample ->
                lineTo(pointX(index), stackedTops(sample).second)
            }
            for (index in samples.indices.reversed()) {
                lineTo(pointX(index), stackedTops(samples[index]).first)
            }
            close()
        }
        // Declines (red) — area between unchanged-top and chart top
        val declineArea = Path().apply {
            moveTo(pointX(0), stackedTops(samples.first()).second)
            samples.forEachIndexed { index, sample ->
                lineTo(pointX(index), stackedTops(sample).second)
            }
            lineTo(pointX(samples.lastIndex), 0f)
            lineTo(pointX(0), 0f)
            close()
        }

        drawPath(declineArea, declineColor.copy(alpha = 0.50f))
        drawPath(unchangedPath, unchangedColor.copy(alpha = 0.45f))
        drawPath(advancePath, advanceColor.copy(alpha = 0.55f))

        val tqiPath = Path()
        samples.forEachIndexed { index, sample ->
            val x = pointX(index)
            val y = h * (1f - (sample.tqi / 100f).coerceIn(0f, 1f))
            if (index == 0) tqiPath.moveTo(x, y) else tqiPath.lineTo(x, y)
        }
        drawPath(
            tqiPath,
            color = Color.White.copy(alpha = 0.9f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun BreathLabel(label: String, count: Int, color: Color, share: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            label,
            color = LocalAppTheme.current.textSecondary,
            fontSize = 11.sp
        )
        Text(
            "${(share * 100f).roundToInt()}%",
            color = color.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun heatmapIntensityFill(intensity: String, theme: AppTheme): Color = when (intensity) {
    "strong_positive" -> theme.positive
    "positive" -> theme.positive.copy(alpha = 0.78f).compositeOver(theme.card)
    "slight_positive" -> theme.positive.copy(alpha = 0.40f).compositeOver(theme.card)
    "neutral" -> Color(0xFFFFB300).copy(alpha = 0.55f).compositeOver(theme.card)
    "slight_negative" -> theme.negative.copy(alpha = 0.40f).compositeOver(theme.card)
    "negative" -> theme.negative.copy(alpha = 0.78f).compositeOver(theme.card)
    "strong_negative" -> theme.negative
    else -> theme.mutedSurface
}

@Composable
private fun SectorHeatmapCard(sector: HeatmapSector, onStockClick: (String) -> Unit) {
    val theme = LocalAppTheme.current
    val sectorColor = heatmapIntensityFill(sector.intensity, theme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(sectorColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        sector.name,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Text(
                    "${String.format("%+.2f", sector.avgChange)}%",
                    color = sectorColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                buildString {
                    append("↑${sector.advances} ↓${sector.declines}")
                    append(" · ${sector.totalStocks} quoted")
                    if (sector.listedStocks > 0) append(" / ${sector.listedStocks} listed")
                    if (sector.tilesTruncated) append(" · showing top movers")
                },
                color = LocalAppTheme.current.textSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val chunked = sector.stocks.chunked(4)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { stock ->
                        StockHeatTile(
                            stock = stock,
                            modifier = Modifier.weight(1f),
                            onClick = { onStockClick(stock.symbol) }
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StockHeatTile(
    stock: HeatmapStock,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val bgColor = heatmapIntensityFill(stock.intensity, theme)
    val onTile = contentColorForFill(bgColor)

    Card(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stock.symbol,
                color = onTile,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${String.format("%+.1f", stock.pctChange)}%",
                color = onTile.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
