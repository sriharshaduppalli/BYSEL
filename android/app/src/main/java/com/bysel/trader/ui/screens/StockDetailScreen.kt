package com.bysel.trader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.AdvancedOrderRequest
import com.bysel.trader.data.models.Alert
import com.bysel.trader.data.models.CopilotSignal
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.models.PreTradeEstimateResponse
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

private data class DetailMetric(
    val title: String,
    val value: String,
    val caption: String,
    val accent: Color,
)

private enum class DetailHistoryWindow(val label: String, val period: String, val interval: String) {
    FiveDay("5D", "5d", "15m"),
    OneMonth("1M", "1mo", "1d"),
    ThreeMonth("3M", "3mo", "1d"),
    OneYear("1Y", "1y", "1wk"),
}

@Composable
fun StockDetailScreen(
    quote: Quote?,
    history: List<HistoryCandle> = emptyList(),
    onBackPress: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onOpenTrustCenter: (String?) -> Unit,
    onAiQuery: ((String) -> Unit)? = null,
    viewModel: TradingViewModel
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val detailNews by viewModel.detailNews.collectAsState()
    val detailNewsLoading by viewModel.detailNewsLoading.collectAsState()
    val detailNewsError by viewModel.detailNewsError.collectAsState()
    val activeAlerts by viewModel.alerts.collectAsState()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsState()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsState()
    val copilotPortfolioActions by viewModel.copilotPortfolioActions.collectAsState()
    val lastOrderTraceId by viewModel.lastOrderTraceId.collectAsState()
    val marketStatus by viewModel.marketStatus.collectAsState()

    if (quote == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text("Stock not found", color = theme.text)
        }
        return
    }

    var historyWindowName by rememberSaveable(quote.symbol) { mutableStateOf(DetailHistoryWindow.OneMonth.name) }
    var previewSide by rememberSaveable(quote.symbol) { mutableStateOf("BUY") }
    var quantityText by rememberSaveable(quote.symbol) { mutableStateOf("1") }

    val historyWindow = remember(historyWindowName) { DetailHistoryWindow.valueOf(historyWindowName) }
    val tradeQuantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val symbolAlerts = remember(activeAlerts, quote.symbol) {
        activeAlerts.filter { it.symbol.equals(quote.symbol, ignoreCase = true) && it.isActive }
    }

    val previousClose = quote.prevClose ?: quote.open ?: quote.last
    val intradayRangePct = remember(quote.dayHigh, quote.dayLow, previousClose) {
        val high = quote.dayHigh ?: quote.last
        val low = quote.dayLow ?: quote.last
        if (previousClose <= 0.0) 0.0 else ((high - low) / previousClose) * 100.0
    }
    val volumeRatio = remember(quote.volume, quote.avgVolume) {
        val avgVolume = quote.avgVolume ?: 0L
        if (avgVolume <= 0L) null else (quote.volume?.toDouble() ?: 0.0) / avgVolume.toDouble()
    }
    val yearPositionPct = remember(quote.fiftyTwoWeekHigh, quote.fiftyTwoWeekLow, quote.last) {
        val high = quote.fiftyTwoWeekHigh
        val low = quote.fiftyTwoWeekLow
        if (high == null || low == null || high <= low) null else ((quote.last - low) / (high - low)) * 100.0
    }
    val targetGapPct = remember(quote.targetMeanPrice, quote.last) {
        val target = quote.targetMeanPrice ?: return@remember null
        if (quote.last <= 0.0) null else ((target - quote.last) / quote.last) * 100.0
    }
    val spreadPct = remember(quote.bid, quote.ask, quote.last) {
        val bid = quote.bid ?: return@remember null
        val ask = quote.ask ?: return@remember null
        if (quote.last <= 0.0) null else ((ask - bid) / quote.last) * 100.0
    }
    val metrics = remember(quote, intradayRangePct, volumeRatio, yearPositionPct, targetGapPct, spreadPct) {
        listOf(
            DetailMetric(
                title = "Intraday Range",
                value = formatSignedPercent(intradayRangePct),
                caption = "How much territory the stock covered today",
                accent = if (intradayRangePct >= 0.0) theme.primary else theme.textSecondary,
            ),
            DetailMetric(
                title = "Volume Pulse",
                value = volumeRatio?.let { "${String.format("%.2f", it)}x" } ?: "N/A",
                caption = "Current volume versus average participation",
                accent = if ((volumeRatio ?: 0.0) >= 1.0) theme.positive else theme.textSecondary,
            ),
            DetailMetric(
                title = "52W Position",
                value = yearPositionPct?.let { "${String.format("%.0f", it)}%" } ?: "N/A",
                caption = "Where price sits inside the 52-week range",
                accent = theme.primary,
            ),
            DetailMetric(
                title = "Target Gap",
                value = targetGapPct?.let { formatSignedPercent(it) } ?: "N/A",
                caption = "Distance to the consensus target mean price",
                accent = when {
                    targetGapPct == null -> theme.textSecondary
                    targetGapPct >= 0.0 -> theme.positive
                    else -> theme.negative
                },
            ),
            DetailMetric(
                title = "Bid / Ask",
                value = spreadPct?.let { formatSignedPercent(it) } ?: "N/A",
                caption = "Indicative spread relative to the last traded price",
                accent = theme.textSecondary,
            ),
        )
    }

    val estimateForView = preTradeEstimate?.takeIf {
        it.symbol.equals(quote.symbol, ignoreCase = true) &&
            it.side.equals(previewSide, ignoreCase = true) &&
            it.qty == tradeQuantity
    }
    val signalForView = estimateForView?.signal ?: preTradeSignal

    val refreshDetailContext = {
        viewModel.refreshQuotes()
        viewModel.fetchQuoteHistory(quote.symbol, historyWindow.period, historyWindow.interval)
        viewModel.refreshDetailNews(quote.symbol)
        viewModel.fetchPreTradeEstimate(
            AdvancedOrderRequest(
                symbol = quote.symbol,
                qty = tradeQuantity,
                side = previewSide,
                orderType = "MARKET",
                validity = "DAY"
            )
        )
    }

    DisposableEffect(quote.symbol, viewModel) {
        viewModel.startFastRefresh(symbols = listOf(quote.symbol))
        onDispose { viewModel.stopFastRefresh() }
    }

    LaunchedEffect(quote.symbol, historyWindow.period, historyWindow.interval) {
        viewModel.fetchQuoteHistory(quote.symbol, historyWindow.period, historyWindow.interval)
    }

    LaunchedEffect(quote.symbol, tradeQuantity, previewSide) {
        viewModel.fetchPreTradeEstimate(
            AdvancedOrderRequest(
                symbol = quote.symbol,
                qty = tradeQuantity,
                side = previewSide,
                orderType = "MARKET",
                validity = "DAY"
            )
        )
    }

    LaunchedEffect(quote.symbol) {
        if (copilotPortfolioActions == null) {
            viewModel.loadPortfolioCopilotActions()
        }
    }

    Scaffold(
        containerColor = theme.surface,
        bottomBar = {
            StockDetailActionBar(
                quote = quote,
                tradeQuantity = tradeQuantity,
                onBuy = { onBuy(quote.symbol, tradeQuantity) },
                onSell = { onSell(quote.symbol, tradeQuantity) },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.surface),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.text,
                        )
                    }
                    FilledTonalButton(onClick = refreshDetailContext) {
                        Text("Refresh context")
                    }
                }
            }

            item {
                StockDetailHeroCard(
                    quote = quote,
                    marketStatus = marketStatus?.message,
                    marketOpen = marketStatus?.isOpen,
                    targetGapPct = targetGapPct,
                    yearPositionPct = yearPositionPct,
                )
            }

            item {
                SectionHeader(
                    title = "Decision Setup",
                    subtitle = "Preview cost, conviction, and alert placement before you route the order.",
                )
            }

            item {
                TradeSetupCard(
                    quote = quote,
                    quantityText = quantityText,
                    onQuantityChange = { nextValue ->
                        quantityText = nextValue.filter { it.isDigit() }.take(4).ifBlank { "1" }
                    },
                    previewSide = previewSide,
                    onPreviewSideChange = { previewSide = it },
                    estimate = estimateForView,
                    signal = signalForView,
                )
            }

            item {
                SectionHeader(
                    title = "Trust & Tools",
                    subtitle = "Inline guidance, support traceability, and decision tools surfaced before you place the trade.",
                )
            }

            // AI Quick Actions
            if (onAiQuery != null) {
                item {
                    SectionHeader(
                        title = "AI Intelligence",
                        subtitle = "One-tap AI-powered analysis, price predictions, and risk assessment.",
                    )
                }
                item {
                    AiQuickActionsRow(
                        symbol = quote.symbol,
                        onAiQuery = onAiQuery,
                    )
                }
            }

            item {
                DetailTrustToolsCard(
                    estimate = estimateForView,
                    signal = signalForView,
                    portfolioActions = copilotPortfolioActions,
                    lastOrderTraceId = lastOrderTraceId,
                    onRefreshGuidance = {
                        viewModel.loadPortfolioCopilotActions()
                        refreshDetailContext()
                    },
                    onOpenTrustCenter = onOpenTrustCenter,
                )
            }

            item {
                SectionHeader(
                    title = "Price Story",
                    subtitle = "Switch timeframe and keep the essential levels, liquidity, and valuation context visible.",
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DetailHistoryWindow.entries, key = { it.name }) { window ->
                        FilterChip(
                            selected = historyWindow == window,
                            onClick = { historyWindowName = window.name },
                            label = { Text(window.label) },
                        )
                    }
                }
            }

            item {
                PriceStoryCard(
                    quote = quote,
                    history = history,
                    historyLabel = historyWindow.label,
                    intradayRangePct = intradayRangePct,
                )
            }

            item {
                DetailMetricsRow(metrics = metrics)
            }

            item {
                SectionHeader(
                    title = "Alert Layer",
                    subtitle = "Turn the current setup into triggers so you do not need to watch the screen continuously.",
                )
            }

            item {
                AlertsCard(
                    quote = quote,
                    alerts = symbolAlerts,
                    onCreateAbove = {
                        viewModel.createAlert(quote.symbol, quote.last * 1.02, "ABOVE")
                        Toast.makeText(context, "Created alert at +2%", Toast.LENGTH_SHORT).show()
                    },
                    onCreateBelow = {
                        viewModel.createAlert(quote.symbol, quote.last * 0.98, "BELOW")
                        Toast.makeText(context, "Created alert at -2%", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { viewModel.deleteAlert(it.id) },
                )
            }

            item {
                SectionHeader(
                    title = "Headline Context",
                    subtitle = "Latest symbol-specific headlines to validate whether price action has a clear narrative behind it.",
                )
            }

            item {
                HeadlinesCard(
                    symbol = quote.symbol,
                    headlines = detailNews,
                    isLoading = detailNewsLoading,
                    error = detailNewsError,
                    onRefresh = { viewModel.refreshDetailNews(quote.symbol) },
                )
            }

            item {
                DetailSnapshotCard(quote = quote)
            }
        }
    }
}

@Composable
private fun StockDetailHeroCard(
    quote: Quote,
    marketStatus: String?,
    marketOpen: Boolean?,
    targetGapPct: Double?,
    yearPositionPct: Double?,
) {
    val theme = LocalAppTheme.current
    val priceColor = if (quote.pctChange >= 0.0) theme.positive else theme.negative

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.card,
                            theme.primary.copy(alpha = 0.28f),
                            theme.surface,
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = if (quote.pctChange >= 0.0) "Leadership candidate" else "Pressure candidate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textSecondary,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (marketOpen) {
                                true -> "Market live"
                                false -> "Market closed"
                                null -> "Market status"
                            }
                        )
                    },
                )
            }

            Text(
                text = formatCurrency(quote.last),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = formatSignedPercent(quote.pctChange),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = priceColor,
            )
            if (!marketStatus.isNullOrBlank()) {
                Text(
                    text = marketStatus,
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targetGapPct?.let {
                    AssistChip(onClick = {}, label = { Text("Target ${formatSignedPercent(it)}") })
                }
                yearPositionPct?.let {
                    AssistChip(onClick = {}, label = { Text("52W ${String.format("%.0f", it)}%") })
                }
                quote.volume?.let {
                    AssistChip(onClick = {}, label = { Text("Vol ${formatCompactVolume(it)}") })
                }
            }
        }
    }
}

@Composable
private fun TradeSetupCard(
    quote: Quote,
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    previewSide: String,
    onPreviewSideChange: (String) -> Unit,
    estimate: PreTradeEstimateResponse?,
    signal: CopilotSignal?,
) {
    val theme = LocalAppTheme.current
    val signalAccent = when {
        signal?.verdict.equals("BUY", ignoreCase = true) -> theme.positive
        signal?.verdict.equals("SELL", ignoreCase = true) -> theme.negative
        else -> theme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trade preview",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(
                    text = "Ref ${formatCurrency(quote.last)}",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BUY", "SELL").forEach { side ->
                    FilterChip(
                        selected = previewSide == side,
                        onClick = { onPreviewSideChange(side) },
                        label = { Text(side) },
                    )
                }
                listOf("1", "5", "10").forEach { quickQty ->
                    FilterChip(
                        selected = quantityText == quickQty,
                        onClick = { onQuantityChange(quickQty) },
                        label = { Text("$quickQty x") },
                    )
                }
            }

            OutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityChange,
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = appOutlinedTextFieldColors(containerColor = theme.surface),
            )

            signal?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = signalAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Copilot signal: ${it.verdict} • ${it.confidence}% confidence",
                            color = signalAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        if (it.flags.isNotEmpty()) {
                            Text(
                                text = it.flags.joinToString(" • "),
                                color = theme.text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        if (it.guidance.isNotEmpty()) {
                            Text(
                                text = it.guidance.take(2).joinToString(" "),
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            if (estimate == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = theme.primary,
                    )
                    Text(
                        text = "Calculating trade estimate...",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLineItem(
                        label = "Trade value",
                        value = formatCurrency(estimate.tradeValue),
                    )
                    DetailLineItem(
                        label = "Estimated charges",
                        value = formatCurrency(estimate.charges.totalCharges),
                    )
                    DetailLineItem(
                        label = "Net amount",
                        value = formatCurrency(estimate.netAmount),
                    )
                    DetailLineItem(
                        label = "Wallet utilization",
                        value = "${String.format("%.1f", estimate.walletUtilizationPct)}%",
                    )
                    DetailLineItem(
                        label = "Impact tag",
                        value = estimate.impactTag,
                    )
                    Text(
                        text = if (estimate.canAfford) "This preview fits current wallet balance." else "This preview breaches the current wallet balance.",
                        fontSize = 12.sp,
                        color = if (estimate.canAfford) theme.positive else theme.negative,
                    )
                    if (estimate.warnings.isNotEmpty()) {
                        Text(
                            text = estimate.warnings.joinToString(" • "),
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTrustToolsCard(
    estimate: PreTradeEstimateResponse?,
    signal: CopilotSignal?,
    portfolioActions: com.bysel.trader.data.models.CopilotPortfolioActionsResponse?,
    lastOrderTraceId: String?,
    onRefreshGuidance: () -> Unit,
    onOpenTrustCenter: (String?) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trust Center",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (estimate != null) "Charge engine live" else "Quote context live") },
                )
            }

            Text(
                text = signal?.let { "Copilot verdict ${it.verdict} at ${it.confidence}% confidence is already incorporated into this decision view." }
                    ?: "Copilot and charge guidance appear inline here so the user does not need to leave the detail screen.",
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 18.sp,
            )

            portfolioActions?.let { actions ->
                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Portfolio Copilot • ${actions.priority}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.text,
                        )
                        Text(
                            text = actions.rationale,
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 18.sp,
                        )
                        actions.actions.take(2).forEach { action ->
                            Text(
                                text = "• $action",
                                fontSize = 12.sp,
                                color = theme.text,
                            )
                        }
                    }
                }
            }

            lastOrderTraceId?.takeIf { it.isNotBlank() }?.let { traceId ->
                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trace-aware support available",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.text,
                            )
                            Text(
                                text = traceId,
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(onClick = { onOpenTrustCenter(traceId) }) {
                            Text("Open Trace")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpenTrustCenter(null) }) {
                    Text("Open Copilot Center")
                }
                OutlinedButton(onClick = onRefreshGuidance) {
                    Text("Refresh Guidance")
                }
            }
        }
    }
}

@Composable
private fun PriceStoryCard(
    quote: Quote,
    history: List<HistoryCandle>,
    historyLabel: String,
    intradayRangePct: Double,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$historyLabel chart",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                AssistChip(onClick = {}, label = { Text("Range ${formatSignedPercent(intradayRangePct)}") })
            }

            if (history.isEmpty()) {
                Text(
                    text = "Historical price context is still loading.",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                )
            } else {
                PriceHistoryChart(history = history, modifier = Modifier.fillMaxWidth())
            }

            DetailLineItem(label = "Open", value = formatCurrency(quote.open ?: quote.prevClose ?: quote.last))
            DetailLineItem(label = "Day high", value = formatCurrency(quote.dayHigh ?: quote.last))
            DetailLineItem(label = "Day low", value = formatCurrency(quote.dayLow ?: quote.last))
            DetailLineItem(label = "Prev close", value = formatCurrency(quote.prevClose ?: quote.last))
        }
    }
}

@Composable
private fun DetailMetricsRow(metrics: List<DetailMetric>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(metrics, key = { it.title }) { metric ->
            Card(
                modifier = Modifier.width(190.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(metric.title, fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
                    Text(metric.value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = metric.accent)
                    Text(metric.caption, fontSize = 12.sp, lineHeight = 18.sp, color = LocalAppTheme.current.text)
                }
            }
        }
    }
}

@Composable
private fun AlertsCard(
    quote: Quote,
    alerts: List<Alert>,
    onCreateAbove: () -> Unit,
    onCreateBelow: () -> Unit,
    onDelete: (Alert) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCreateAbove) {
                    Text("Alert +2%")
                }
                OutlinedButton(onClick = onCreateBelow) {
                    Text("Alert -2%")
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = "No active alerts for ${quote.symbol}. Use quick triggers to stay informed away from the screen.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            } else {
                alerts.forEach { alert ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.surface,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${alert.alertType} ${formatCurrency(alert.thresholdPrice)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.text,
                                )
                                Text(
                                    text = "Created to monitor a decisive break above or below current price.",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 18.sp,
                                )
                            }
                            TextButtonLike(text = "Delete", onClick = { onDelete(alert) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadlinesCard(
    symbol: String,
    headlines: List<MarketNewsHeadline>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$symbol headlines",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                    )
                    Text(
                        text = "Symbol-specific headlines reduce the gap between move and narrative.",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
                FilledTonalButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }

            when {
                isLoading && headlines.isEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = theme.primary,
                        )
                        Text(
                            text = "Refreshing symbol headlines...",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                        )
                    }
                }

                error != null && headlines.isEmpty() -> {
                    Text(
                        text = error,
                        color = theme.negative,
                        fontSize = 12.sp,
                    )
                }

                headlines.isEmpty() -> {
                    Text(
                        text = "Headline context is unavailable for this symbol right now.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                else -> {
                    headlines.forEach { headline ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.surface,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = headline.title,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = theme.text,
                                )
                                val meta = listOf(headline.source, headline.publishedLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        text = meta,
                                        fontSize = 12.sp,
                                        color = theme.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSnapshotCard(quote: Quote) {
    val theme = LocalAppTheme.current
    val lastUpdated = remember(quote.timestamp) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(quote.timestamp))
        }.getOrDefault("")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Snapshot",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.text,
            )
            DetailLineItem(
                label = "Bid / Ask",
                value = "${quote.bid?.let { formatCurrency(it) } ?: "N/A"} / ${quote.ask?.let { formatCurrency(it) } ?: "N/A"}",
            )
            DetailLineItem(
                label = "52W High / Low",
                value = "${quote.fiftyTwoWeekHigh?.let { formatCurrency(it) } ?: "N/A"} / ${quote.fiftyTwoWeekLow?.let { formatCurrency(it) } ?: "N/A"}",
            )
            DetailLineItem(
                label = "Market cap",
                value = quote.marketCap?.let { formatCompactNumber(it.toDouble()) } ?: "N/A",
            )
            DetailLineItem(
                label = "P/E / EPS",
                value = "${quote.trailingPE?.let { String.format("%.2f", it) } ?: "N/A"} / ${quote.eps?.let { String.format("%.2f", it) } ?: "N/A"}",
            )
            DetailLineItem(
                label = "Dividend yield",
                value = quote.dividendYield?.let { "${String.format("%.2f", it)}%" } ?: "N/A",
            )
            if (lastUpdated.isNotBlank()) {
                DetailLineItem(label = "Last updated", value = lastUpdated)
            }
        }
    }
}

@Composable
private fun StockDetailActionBar(
    quote: Quote,
    tradeQuantity: Int,
    onBuy: () -> Unit,
    onSell: () -> Unit,
) {
    val theme = LocalAppTheme.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf("BUY") }

    Surface(
        tonalElevation = 8.dp,
        color = theme.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${quote.symbol} • ${formatCurrency(quote.last)}",
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = "Route $tradeQuantity share${if (tradeQuantity == 1) "" else "s"} from this screen",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }
            Button(
                onClick = { pendingAction = "BUY"; showConfirmDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = theme.positive),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Buy")
            }
            Button(
                onClick = { pendingAction = "SELL"; showConfirmDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = theme.negative),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Sell")
            }
        }
    }

    if (showConfirmDialog) {
        val estValue = quote.last * tradeQuantity
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    "Confirm $pendingAction Order",
                    fontWeight = FontWeight.Bold,
                    color = if (pendingAction == "BUY") theme.positive else theme.negative
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${quote.symbol} • $tradeQuantity share${if (tradeQuantity == 1) "" else "s"}")
                    Text("Market price: ${formatCurrency(quote.last)}")
                    Text(
                        "Est. value: ₹${String.format("%,.2f", estValue)}",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (pendingAction == "BUY") onBuy() else onSell()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingAction == "BUY") theme.positive else theme.negative
                    )
                ) {
                    Text("Confirm $pendingAction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppTheme.current.text,
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = LocalAppTheme.current.textSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun DetailLineItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = LocalAppTheme.current.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

private fun formatCurrency(value: Double): String = "₹${String.format("%.2f", value)}"

private fun formatSignedPercent(value: Double): String = buildString {
    if (value > 0.0) append("+")
    append(String.format("%.2f", value))
    append("%")
}

private fun formatCompactVolume(value: Long): String {
    return when {
        value >= 10_000_000L -> String.format("%.2fCr", value / 10_000_000.0)
        value >= 100_000L -> String.format("%.2fL", value / 100_000.0)
        value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatCompactNumber(value: Double): String {
    val absValue = abs(value)
    return when {
        absValue >= 10_000_000 -> "₹${String.format("%.2f", value / 10_000_000)}Cr"
        absValue >= 100_000 -> "₹${String.format("%.2f", value / 100_000)}L"
        else -> "₹${String.format("%,.0f", value)}"
    }
}

@Composable
private fun AiQuickActionsRow(
    symbol: String,
    onAiQuery: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    data class AiAction(val emoji: String, val label: String, val query: String)
    val actions = listOf(
        AiAction("🤖", "Full Analysis", "Analyze $symbol with entry price, target price, and stop-loss levels"),
        AiAction("📈", "Price Prediction", "Predict $symbol price target for next 1 month with confidence and risk-reward"),
        AiAction("⚡", "Quick Signal", "Should I buy or sell $symbol right now? Give entry, target, stop-loss"),
        AiAction("🛡️", "Risk Check", "What are the key risks for $symbol and what is the downside from current price?"),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.take(2).forEach { action ->
                    Button(
                        onClick = { onAiQuery(action.query) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary.copy(alpha = 0.12f),
                            contentColor = theme.primary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("${action.emoji} ${action.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.drop(2).forEach { action ->
                    Button(
                        onClick = { onAiQuery(action.query) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary.copy(alpha = 0.12f),
                            contentColor = theme.primary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("${action.emoji} ${action.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
