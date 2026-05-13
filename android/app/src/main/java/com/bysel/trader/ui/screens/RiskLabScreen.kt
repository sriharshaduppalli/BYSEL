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
            riskData = viewModel.fetchPortfolioRisk()
            if (riskData == null) errorMsg = "Could not load risk data. Check your holdings."
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.surface)
    ) {
        // Top bar
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
                Text("Portfolio VaR & Monte Carlo", fontSize = 12.sp, color = appTheme.textSecondary)
            }
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = appTheme.primary)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appTheme.primary)
            }
            return@Column
        }

        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = appTheme.textSecondary, fontSize = 14.sp)
            }
            return@Column
        }

        val data = riskData ?: return@Column

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RiskSectionCard(title = "Value at Risk (1-Day)", appTheme = appTheme) {
                    RiskRow("VaR 95%", "-${String.format("%.2f", data.metrics.var95 * 100)}%", Color(0xFFFF7043))
                    RiskRow("VaR 99%", "-${String.format("%.2f", data.metrics.var99 * 100)}%", Color(0xFFE53935))
                    Text(
                        "With 95% confidence, daily loss won't exceed ${String.format("%.2f", data.metrics.var95 * 100)}%",
                        fontSize = 11.sp, color = appTheme.textSecondary, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                RiskSectionCard(title = "Portfolio Performance", appTheme = appTheme) {
                    RiskRow("Annualised Return", "${String.format("%.1f", data.metrics.annualizedReturn * 100)}%",
                        if (data.metrics.annualizedReturn >= 0) Color(0xFF4CAF50) else Color(0xFFE53935))
                    RiskRow("Annualised Volatility", "${String.format("%.1f", data.metrics.annualizedVolatility * 100)}%", Color(0xFFFF9800))
                    RiskRow("Sharpe Ratio", String.format("%.2f", data.metrics.sharpeRatio),
                        if (data.metrics.sharpeRatio >= 1) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    RiskRow("Max Drawdown", "-${String.format("%.1f", data.metrics.maxDrawdown * 100)}%", Color(0xFFE53935))
                }
            }

            item {
                RiskSectionCard(title = "Monte Carlo (500 simulations, 30-day)", appTheme = appTheme) {
                    RiskRow("Best Case (P95)", "+${String.format("%.1f", (data.monteCarloP95 - 1) * 100)}%", Color(0xFF4CAF50))
                    RiskRow("Median Outcome", "${String.format("%.1f", (data.monteCarloMedian - 1) * 100)}%",
                        if (data.monteCarloMedian >= 1) Color(0xFF4CAF50) else Color(0xFFE53935))
                    RiskRow("Worst Case (P5)", "${String.format("%.1f", (data.monteCarloP5 - 1) * 100)}%", Color(0xFFE53935))

                    Spacer(modifier = Modifier.height(8.dp))
                    MonteCarloBar(
                        p5 = data.monteCarloP5.toFloat(),
                        median = data.monteCarloMedian.toFloat(),
                        p95 = data.monteCarloP95.toFloat(),
                        appTheme = appTheme
                    )
                }
            }

            if (data.correlationMatrix.isNotEmpty() && data.symbols.size > 1) {
                item {
                    RiskSectionCard(title = "Correlation Matrix", appTheme = appTheme) {
                        CorrelationMatrixView(
                            symbols = data.symbols,
                            matrix = data.correlationMatrix,
                            appTheme = appTheme
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Risk metrics are calculated using 1-year historical data. VaR assumes normal distribution and does not guarantee future results.",
                        fontSize = 11.sp,
                        color = appTheme.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    val min = minOf(p5, 0.9f)
    val max = maxOf(p95, 1.1f)
    val range = max - min

    fun toFraction(v: Float) = ((v - min) / range).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE53935).copy(alpha = 0.2f))
    ) {
        val totalWidth = maxWidth

        // Green zone: median to p95
        val greenStart = toFraction(median)
        val greenEnd = toFraction(p95)
        Box(
            modifier = Modifier
                .offset(x = totalWidth * greenStart)
                .width(totalWidth * (greenEnd - greenStart))
                .fillMaxHeight()
                .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
        )

        // Median marker
        Box(
            modifier = Modifier
                .offset(x = totalWidth * toFraction(median) - 1.dp)
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

@Composable
private fun CorrelationMatrixView(
    symbols: List<String>,
    matrix: List<List<Double>>,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    Column {
        // Header row
        Row {
            Box(modifier = Modifier.width(60.dp))
            symbols.forEach { sym ->
                Text(
                    sym.take(5),
                    modifier = Modifier.width(52.dp),
                    fontSize = 10.sp,
                    color = appTheme.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        matrix.forEachIndexed { rowIdx, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    symbols.getOrElse(rowIdx) { "" }.take(5),
                    modifier = Modifier.width(60.dp),
                    fontSize = 10.sp,
                    color = appTheme.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                row.forEach { corr ->
                    val bg = when {
                        corr >= 0.7 -> Color(0xFFE53935).copy(alpha = 0.5f)
                        corr >= 0.3 -> Color(0xFFFF9800).copy(alpha = 0.4f)
                        corr <= -0.3 -> Color(0xFF4CAF50).copy(alpha = 0.4f)
                        else -> appTheme.card
                    }
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(28.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            String.format("%.2f", corr),
                            fontSize = 9.sp,
                            color = appTheme.text,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
