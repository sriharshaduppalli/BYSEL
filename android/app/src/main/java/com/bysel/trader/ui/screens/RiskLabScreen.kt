package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.api.PortfolioRiskResponse
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun RiskLabScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
) {
    val appTheme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var riskData by remember { mutableStateOf<PortfolioRiskResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            riskData = try {
                viewModel.fetchPortfolioRisk()
            } catch (_: Exception) {
                null
            }
            if (riskData == null) {
                errorMsg = "Could not load risk data. Check connection and retry."
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appTheme.text)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Risk Lab", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.text)
                Text("Educational paper view — not advice", fontSize = 12.sp, color = appTheme.textSecondary)
            }
            IconButton(onClick = { load() }, enabled = !isLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = appTheme.primary)
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appTheme.primary)
                }
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        errorMsg.orEmpty(),
                        color = appTheme.textSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { load() }, enabled = !isLoading) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val data = riskData
                if (data == null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No risk data available.", color = appTheme.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    RiskLabContent(data = data, appTheme = appTheme)
                }
            }
        }
    }
}

@Composable
private fun RiskLabContent(
    data: PortfolioRiskResponse,
    appTheme: com.bysel.trader.ui.theme.AppTheme,
) {
    // Backend ai_v2 returns percentages already (e.g. -1.8 for -1.8%).
    val metrics = data.resolvedMetrics()
    val var95Pct = formatPct(metrics.var95)
    val var99Pct = formatPct(metrics.var99)
    val annualizedReturnPct = formatPct(metrics.annualizedReturn)
    val annualizedVolPct = formatPct(metrics.annualizedVolatility)
    val maxDrawdownPct = formatPct(metrics.maxDrawdown)
    val sharpeRatio = safeNumber(metrics.sharpeRatio, 2)
    val mcP5 = data.resolvedMonteCarloP5()
    val mcMedian = data.resolvedMonteCarloMedian()
    val mcP95 = data.resolvedMonteCarloP95()
    val riskLevel = data.riskLevel?.takeIf { it.isNotBlank() }
    val sampleNumbers = data.isSample

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (sampleNumbers) {
                        Color(0xFFFF9800).copy(alpha = 0.18f)
                    } else {
                        appTheme.primary.copy(alpha = 0.10f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = when {
                            sampleNumbers && data.demoBasket ->
                                "Sample numbers — not your paper book"
                            sampleNumbers ->
                                "Sample numbers — for learning only"
                            else ->
                                "Educational risk view"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sampleNumbers) Color(0xFFE65100) else appTheme.primary,
                    )
                    Text(
                        text = if (sampleNumbers) {
                            "These figures are for learning. They are not live risk on your holdings."
                        } else {
                            "Educational only — not a SEBI risk report or a forecast."
                        },
                        fontSize = 11.sp,
                        color = appTheme.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (riskLevel != null || data.symbols.isNotEmpty()) {
            item {
                RiskSectionCard(title = "Portfolio", appTheme = appTheme) {
                    if (data.symbols.isNotEmpty()) {
                        RiskRow(
                            label = "Names in this view",
                            value = "${data.symbols.size}",
                            valueColor = appTheme.text,
                        )
                    }
                    if (riskLevel != null) {
                        RiskRow(
                            label = "Risk level",
                            value = riskLevel,
                            valueColor = when (riskLevel.lowercase()) {
                                "low" -> appTheme.positive
                                "high" -> appTheme.negative
                                else -> appTheme.caution
                            },
                        )
                    }
                }
            }
        }

        item {
            RiskSectionCard(
                title = if (sampleNumbers) "Illustrative VaR (sample, 1-day)" else "Value at Risk (1-day)",
                appTheme = appTheme,
            ) {
                RiskRow("VaR 95%", formatSignedPct(metrics.var95), appTheme.caution)
                RiskRow("VaR 99%", formatSignedPct(metrics.var99), appTheme.negative)
                Text(
                    if (sampleNumbers) {
                        "Sample figure only — not a modelled loss on your paper book."
                    } else {
                        "With 95% confidence, a one-day loss in this lab is near $var95Pct%."
                    },
                    fontSize = 11.sp,
                    color = appTheme.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            RiskSectionCard(
                title = if (sampleNumbers) "Illustrative performance (sample)" else "Portfolio performance",
                appTheme = appTheme,
            ) {
                RiskRow(
                    "Annualised Return",
                    formatSignedPct(metrics.annualizedReturn),
                    if (metrics.annualizedReturn >= 0) appTheme.positive else appTheme.negative,
                )
                RiskRow("Annualised Volatility", "$annualizedVolPct%", appTheme.caution)
                RiskRow(
                    "Sharpe Ratio",
                    sharpeRatio,
                    if (metrics.sharpeRatio >= 1) appTheme.positive else appTheme.caution,
                )
                RiskRow("Max Drawdown", formatSignedPct(metrics.maxDrawdown), appTheme.negative)
            }
        }

        item {
            RiskSectionCard(
                title = if (sampleNumbers) {
                    "Possible 30-day outcomes (sample)"
                } else {
                    "Possible 30-day outcomes"
                },
                appTheme = appTheme,
            ) {
                RiskRow("Best Case (P95)", formatSignedPct(mcP95), appTheme.positive)
                RiskRow(
                    "Median Outcome",
                    formatSignedPct(mcMedian),
                    if (mcMedian >= 0) appTheme.positive else appTheme.negative,
                )
                RiskRow("Worst Case (P5)", formatSignedPct(mcP5), appTheme.negative)

                Spacer(modifier = Modifier.height(8.dp))
                MonteCarloBar(
                    p5 = mcP5.toFloat(),
                    median = mcMedian.toFloat(),
                    p95 = mcP95.toFloat(),
                )
            }
        }

        if (data.correlationMatrix.isNotEmpty() && data.symbols.size > 1) {
            val peak = peakPairwiseCorrelation(data.correlationMatrix)
            item {
                RiskSectionCard(title = "How names move together", appTheme = appTheme) {
                    RiskRow(
                        "Names used",
                        "${data.symbols.size}",
                        appTheme.text,
                    )
                    if (peak != null) {
                        RiskRow(
                            "Closest pairing",
                            safeNumber(peak, 2),
                            if (peak >= 0.7) appTheme.negative else appTheme.caution,
                        )
                    }
                    Text(
                        "Tickers stay on the Portfolio tab. This lab shows the book as a whole.",
                        fontSize = 11.sp,
                        color = appTheme.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Educational only. These figures are not a forecast or a SEBI risk report.",
                    fontSize = 11.sp,
                    color = appTheme.textSecondary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun RiskSectionCard(
    title: String,
    appTheme: com.bysel.trader.ui.theme.AppTheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = appTheme.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RiskRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = LocalAppTheme.current.textSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun MonteCarloBar(
    p5: Float,
    median: Float,
    p95: Float,
) {
    val safeP5 = p5.takeIf { it.isFinite() } ?: -5f
    val safeMedian = median.takeIf { it.isFinite() } ?: 0f
    val safeP95 = p95.takeIf { it.isFinite() } ?: 5f

    val min = minOf(safeP5, -10f)
    val max = maxOf(safeP95, 10f)
    val range = max(max - min, 0.0001f)

    fun toFraction(v: Float) = ((v - min) / range).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE53935).copy(alpha = 0.2f))
    ) {
        val totalWidth = maxWidth
        val greenStart = toFraction(safeMedian)
        val greenEnd = toFraction(safeP95)
        Box(
            modifier = Modifier
                .offset(x = totalWidth * greenStart)
                .width(totalWidth * (greenEnd - greenStart).coerceAtLeast(0f))
                .fillMaxHeight()
                .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .offset(x = totalWidth * toFraction(safeMedian) - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color(0xFF4CAF50))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("P5", fontSize = 10.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
            Text("Median", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            Text("P95", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
    }
}

private fun peakPairwiseCorrelation(matrix: List<List<Double>>): Double? {
    var peak: Double? = null
    matrix.forEachIndexed { i, row ->
        row.forEachIndexed { j, corr ->
            if (i == j || !corr.isFinite()) return@forEachIndexed
            val current = peak
            if (current == null || corr > current) peak = corr
        }
    }
    return peak
}

private fun safeNumber(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "--"
    return String.format("%.${decimals}f", value)
}

/** Backend already returns percent units (e.g. -1.8). */
private fun formatPct(value: Double): String {
    if (!value.isFinite()) return "--"
    return String.format("%.1f", kotlin.math.abs(value))
}

private fun formatSignedPct(value: Double): String {
    if (!value.isFinite()) return "--"
    val sign = if (value > 0) "+" else ""
    return "$sign${String.format("%.1f", value)}%"
}
