package com.bysel.trader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatInrCompact
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.ui.format.formatVolumeCompact
import com.bysel.trader.data.models.AdvancedOrderRequest
import com.bysel.trader.data.models.Alert
import com.bysel.trader.data.models.CopilotSignal
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.models.PreTradeEstimateResponse
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.PricePrediction
import com.bysel.trader.data.models.StockAnalysis
import com.bysel.trader.data.models.StockPredictionResponse
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.components.PaperPositionSizeHelper
import com.bysel.trader.ui.components.StockLiteracyCatalog
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.components.InfoChip
import com.bysel.trader.ui.components.StockNotesIcon
import com.bysel.trader.ui.components.StockNotesPreviewText
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
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

private enum class DetailHistoryWindow(
    val label: String,
    val period: String,
    val interval: String,
    val subtitle: String,
) {
    FiveDay("5D", "5d", "15m", "15-min candles · last 5 sessions"),
    OneMonth("1M", "1mo", "1d", "Daily candles · ~1 month"),
    ThreeMonth("3M", "3mo", "1d", "Daily candles · ~3 months"),
    OneYear("1Y", "1y", "1d", "Daily candles · ~1 year"),
}

@Composable
fun StockDetailScreen(
    quote: Quote?,
    history: List<HistoryCandle> = emptyList(),
    historyLoading: Boolean = false,
    onBackPress: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onOpenTrustCenter: (String?) -> Unit,
    onAiQuery: ((String) -> Unit)? = null,
    viewModel: TradingViewModel
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val detailNews by viewModel.detailNews.collectAsStateWithLifecycle()
    val detailNewsLoading by viewModel.detailNewsLoading.collectAsStateWithLifecycle()
    val detailNewsError by viewModel.detailNewsError.collectAsStateWithLifecycle()
    val activeAlerts by viewModel.alerts.collectAsStateWithLifecycle()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsStateWithLifecycle()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsStateWithLifecycle()
    val copilotPortfolioActions by viewModel.copilotPortfolioActions.collectAsStateWithLifecycle()
    val lastOrderTraceId by viewModel.lastOrderTraceId.collectAsStateWithLifecycle()
    val marketStatus by viewModel.marketStatus.collectAsStateWithLifecycle()
    val scannerXrayRow by viewModel.selectedScannerRow.collectAsStateWithLifecycle()
    val symbolXray by viewModel.symbolXray.collectAsStateWithLifecycle()
    val scoreHistory by viewModel.scoreHistory.collectAsStateWithLifecycle()
    val walletBalance by viewModel.walletBalance.collectAsStateWithLifecycle()
    val stockAnalysis by viewModel.stockAnalysis.collectAsStateWithLifecycle()
    val stockPrediction by viewModel.stockPrediction.collectAsStateWithLifecycle()
    val stockResearchLoading by viewModel.stockResearchLoading.collectAsStateWithLifecycle()
    val stockResearchError by viewModel.stockResearchError.collectAsStateWithLifecycle()

    if (quote == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Stock not found",
                    color = theme.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "That chart link didn’t resolve to a valid NSE symbol. Go back and try again from a stock-specific reply.",
                    color = theme.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onBackPress) {
                    Text("Go back")
                }
            }
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
        val sessionVolume = quote.volume ?: 0L
        if (avgVolume <= 0L || sessionVolume <= 0L) null else sessionVolume.toDouble() / avgVolume.toDouble()
    }
    val volumePulseLabel = remember(volumeRatio) {
        when {
            volumeRatio == null -> null
            volumeRatio >= 1.5 -> "High"
            volumeRatio >= 0.7 -> "Normal"
            else -> "Low"
        }
    }
    val yearPositionPct = remember(quote.fiftyTwoWeekHigh, quote.fiftyTwoWeekLow, quote.last) {
        val high = quote.fiftyTwoWeekHigh
        val low = quote.fiftyTwoWeekLow
        if (high == null || low == null || high <= low) null else ((quote.last - low) / (high - low)) * 100.0
    }
    val targetPrice = quote.targetMeanPrice
    val targetIsAnalyst = targetPrice != null && targetPrice > 0.0
    val targetFallback = if (targetIsAnalyst) targetPrice else quote.fiftyTwoWeekHigh
    val targetGapPct = remember(targetFallback, quote.last) {
        val target = targetFallback ?: return@remember null
        if (quote.last <= 0.0) null else ((target - quote.last) / quote.last) * 100.0
    }
    val spreadPct = remember(quote.bid, quote.ask, quote.last) {
        val bid = quote.bid ?: return@remember null
        val ask = quote.ask ?: return@remember null
        if (quote.last <= 0.0) null else ((ask - bid) / quote.last) * 100.0
    }
    val metrics = remember(
        quote,
        intradayRangePct,
        volumeRatio,
        volumePulseLabel,
        yearPositionPct,
        targetGapPct,
        targetIsAnalyst,
        spreadPct,
    ) {
        val volumeValue = when {
            volumeRatio != null && volumePulseLabel != null ->
                "${String.format("%.2f", volumeRatio)}x · $volumePulseLabel"
            (quote.volume ?: 0L) > 0L -> formatVolumeCompact(quote.volume)
            else -> "—"
        }
        val volumeCaption = when {
            volumeRatio != null -> "Session volume vs 3-month average"
            (quote.volume ?: 0L) > 0L -> "Session volume (average not published)"
            else -> "No session volume yet"
        }
        val targetValue = targetGapPct?.let { formatSignedPercent(it) } ?: "No target yet"
        val targetCaption = when {
            targetIsAnalyst -> "Distance to consensus analyst target"
            targetGapPct != null -> "Distance to 52-week high (no analyst target)"
            else -> "No analyst target or 52-week high yet"
        }
        val bookValue = when {
            quote.bid != null && quote.ask != null && spreadPct != null ->
                "${formatCurrency(quote.bid)} / ${formatCurrency(quote.ask)}"
            else -> "—"
        }
        val bookCaption = when {
            spreadPct != null -> "Spread ${formatSignedPercent(spreadPct)} vs last"
            else -> "No live book — common after hours on NSE"
        }
        listOf(
            DetailMetric(
                title = "Intraday Range",
                value = formatSignedPercent(intradayRangePct),
                caption = "How much territory the stock covered today",
                accent = if (intradayRangePct >= 0.0) theme.primary else theme.textSecondary,
            ),
            DetailMetric(
                title = "Volume Pulse",
                value = volumeValue,
                caption = volumeCaption,
                accent = if ((volumeRatio ?: 0.0) >= 1.0 || (quote.volume ?: 0L) > 0L) {
                    theme.positive
                } else {
                    theme.textSecondary
                },
            ),
            DetailMetric(
                title = "52W Position",
                value = yearPositionPct?.let { "${String.format("%.0f", it)}%" } ?: "N/A",
                caption = "Where price sits inside the 52-week range",
                accent = theme.primary,
            ),
            DetailMetric(
                title = "Target Gap",
                value = targetValue,
                caption = targetCaption,
                accent = when {
                    targetGapPct == null -> theme.textSecondary
                    targetGapPct >= 0.0 -> theme.positive
                    else -> theme.negative
                },
            ),
            DetailMetric(
                title = "Bid / Ask",
                value = bookValue,
                caption = bookCaption,
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

    LaunchedEffect(quote.symbol, viewModel) {
        viewModel.clearStockResearch()
        viewModel.startFastRefresh(symbols = listOf(quote.symbol))
        viewModel.loadSymbolXray(quote.symbol)
        viewModel.loadScoreHistory(quote.symbol, days = 90)
    }

    // Fetch sentiment score for this stock
    var sentimentData by remember(quote.symbol) { mutableStateOf<com.bysel.trader.data.api.SentimentScoreResponse?>(null) }
    var sentimentLoading by remember(quote.symbol) { mutableStateOf(false) }
    LaunchedEffect(quote.symbol) {
        sentimentLoading = true
        try {
            sentimentData = viewModel.fetchSentimentScore(quote.symbol)
        } catch (_: Exception) {}
        sentimentLoading = false
    }

    // Fetch chart patterns for overlay
    var chartPatterns by remember(quote.symbol) { mutableStateOf<List<com.bysel.trader.data.api.ChartPattern>>(emptyList()) }
    LaunchedEffect(quote.symbol) {
        try {
            chartPatterns = viewModel.fetchChartPatterns(quote.symbol)?.patterns ?: emptyList()
        } catch (_: Exception) {}
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
                    StockNotesIcon(
                        symbol = quote.symbol,
                        showPreview = true,
                        iconSize = 22.dp,
                        buttonSize = 36.dp,
                        modifier = Modifier.weight(1f),
                    )
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

            val scannerXray = scannerXrayRow?.takeIf { it.symbol.equals(quote.symbol, ignoreCase = true) }
                ?: symbolXray?.takeIf { it.symbol.equals(quote.symbol, ignoreCase = true) }
            if (scannerXray != null) {
                item {
                    ByselExplainabilityCard(
                        row = scannerXray,
                        history = scoreHistory,
                    )
                }
            }

            item {
                StockResearchCard(
                    symbol = quote.symbol,
                    analysis = stockAnalysis?.takeIf { it.symbol.equals(quote.symbol, ignoreCase = true) },
                    prediction = stockPrediction?.takeIf { it.symbol.equals(quote.symbol, ignoreCase = true) },
                    loading = stockResearchLoading,
                    error = stockResearchError,
                    onAnalyze = { viewModel.analyzeStock(quote.symbol) },
                    onPredict = { viewModel.predictStock(quote.symbol) },
                )
            }

            // Chart first — AI "View chart" and stock opens should show candles immediately.
            item {
                SectionHeader(
                    title = "Price chart",
                    subtitle = historyWindow.subtitle,
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

            item(key = "chart-${historyWindow.period}-${historyWindow.interval}-${quote.symbol}") {
                PriceStoryCard(
                    quote = quote,
                    history = history,
                    historyLoading = historyLoading,
                    historyLabel = historyWindow.label,
                    historyHint = historyWindow.subtitle,
                    intradayRangePct = intradayRangePct,
                    patterns = chartPatterns,
                    sentimentData = sentimentData,
                    sentimentLoading = sentimentLoading,
                    onAiQuery = onAiQuery,
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
                    walletBalance = walletBalance,
                    entryPrice = quote.last,
                    onApplySizedQty = { nextQty ->
                        quantityText = nextQty.toString()
                    },
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
                        subtitle = "Analyze and ensemble preview on this name — educational, not a forecast.",
                    )
                }
                item {
                    AiQuickActionsRow(
                        symbol = quote.symbol,
                        onAiQuery = onAiQuery,
                        onAnalyze = { viewModel.analyzeStock(quote.symbol) },
                        onPredict = { viewModel.predictStock(quote.symbol) },
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

@OptIn(ExperimentalLayoutApi::class)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quote.symbol,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StockNotesIcon(
                            symbol = quote.symbol,
                            iconSize = 20.dp,
                            buttonSize = 36.dp,
                        )
                    }
                    StockNotesPreviewText(
                        symbol = quote.symbol,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = if (quote.pctChange >= 0.0) "Leadership candidate" else "Pressure candidate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textSecondary,
                    )
                }
                InfoChip(
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targetGapPct?.let {
                    InfoChip(label = {
                        Text("Target ${formatSignedPercent(it)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
                }
                yearPositionPct?.let {
                    InfoChip(label = {
                        Text("52W ${String.format("%.0f", it)}%", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
                }
                quote.volume?.let {
                    InfoChip(label = {
                        Text("Vol ${formatCompactVolume(it)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
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
    walletBalance: Double,
    entryPrice: Double,
    onApplySizedQty: (Int) -> Unit,
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

            PaperPositionSizeHelper(
                walletBalance = walletBalance,
                entryPrice = entryPrice,
                onApplyQty = onApplySizedQty,
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
                            text = "BYSEL signal: ${it.verdict} • ${it.confidence}% confidence",
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

@OptIn(ExperimentalLayoutApi::class)
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
                InfoChip(
                    label = { Text(if (estimate != null) "Charge engine live" else "Quote context live") },
                )
            }

            Text(
                text = signal?.let { "BYSEL signal ${it.verdict} at ${it.confidence}% confidence is already incorporated into this decision view." }
                    ?: "BYSEL signal and charge guidance appear inline here so you don’t need to leave the detail screen.",
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { onOpenTrustCenter(null) }) {
                    Text("Pre-trade", maxLines = 1)
                }
                OutlinedButton(onClick = onRefreshGuidance) {
                    Text("Refresh", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PriceStoryCard(
    quote: Quote,
    history: List<HistoryCandle>,
    historyLoading: Boolean = false,
    historyLabel: String,
    historyHint: String = "",
    intradayRangePct: Double,
    patterns: List<com.bysel.trader.data.api.ChartPattern> = emptyList(),
    sentimentData: com.bysel.trader.data.api.SentimentScoreResponse? = null,
    sentimentLoading: Boolean = false,
    onAiQuery: ((String) -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val literacyCards = remember(history) { StockLiteracyCatalog.cardsFor(history) }
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
                        text = "$historyLabel chart",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (historyHint.isNotBlank()) {
                        Text(
                            text = historyHint,
                            fontSize = 11.sp,
                            color = theme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                InfoChip(label = {
                    Text(
                        if (history.isNotEmpty()) "${history.size} bars" else "Range ${formatSignedPercent(intradayRangePct)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                })
            }

            when {
                history.isEmpty() && historyLoading -> {
                    Text(
                        text = "Loading $historyLabel candles…",
                        color = theme.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                history.isEmpty() -> {
                    Text(
                        text = "No candles for $historyLabel yet. Try another window or a liquid NSE name.",
                        color = theme.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                else -> {
                    if (historyLoading) {
                        Text(
                            text = "Refreshing $historyLabel…",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    PriceHistoryChart(
                        history = history,
                        symbol = quote.symbol,
                        modifier = Modifier.fillMaxWidth(),
                        isDarkTheme = !theme.isLight,
                        patterns = patterns,
                        rangeLabel = historyLabel,
                    )
                }
            }

            if (onAiQuery != null && literacyCards.isNotEmpty()) {
                literacyCards.forEach { card ->
                    TextButton(
                        onClick = { onAiQuery(card.learnQuery) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "Learn: ${card.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.primary,
                        )
                    }
                }
            }

            if (patterns.isNotEmpty()) {
                Text(
                    text = "Chart structures",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                patterns.take(5).forEach { detected ->
                    val label = detected.pattern.ifBlank { detected.type }
                    val signal = detected.signal.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    val conf = detected.confidence.takeIf { it > 0 }?.let { " · $it% conf" }.orEmpty()
                    val desc = detected.description.takeIf { it.isNotBlank() }
                    Text(
                        text = "• $label$signal$conf",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                    if (desc != null) {
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = theme.textSecondary.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Sentiment bar — below chart
            com.bysel.trader.ui.components.SentimentBar(
                sentiment = sentimentData,
                isLoading = sentimentLoading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

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
                    Text(
                        metric.title,
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metric.value,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = metric.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metric.caption,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = LocalAppTheme.current.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                value = when {
                    quote.bid != null || quote.ask != null ->
                        "${quote.bid?.let { formatCurrency(it) } ?: "—"} / ${quote.ask?.let { formatCurrency(it) } ?: "—"}"
                    else -> "— / —  (no live book)"
                },
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

@Composable
private fun StockResearchCard(
    symbol: String,
    analysis: StockAnalysis?,
    prediction: StockPredictionResponse?,
    loading: Boolean,
    error: String?,
    onAnalyze: () -> Unit,
    onPredict: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Research (educational)", fontWeight = FontWeight.SemiBold, color = theme.text)
            Text(
                "Analyze uses available market fields. Ensemble is linear trend 40% + smoothing 35% + momentum 25% — not a forecast.",
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAnalyze, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Analyze $symbol")
                }
                OutlinedButton(onClick = onPredict, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Ensemble")
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.primary)
                    Text("Loading research…", fontSize = 12.sp, color = theme.textSecondary)
                }
            }
            error?.let {
                Text(it, fontSize = 12.sp, color = theme.negative)
            }
            analysis?.let { row ->
                Text(row.summary.ifBlank { "No summary in this snapshot." }, fontSize = 13.sp, color = theme.text)
                if (row.scoreBreakdown.isNotEmpty()) {
                    row.scoreBreakdown.entries.take(4).forEach { (key, value) ->
                        DetailLineItem(label = key.replaceFirstChar { it.uppercase() }, value = value.toString())
                    }
                }
                if (row.signal.isNotBlank()) {
                    Text(
                        educationalStanceLabel(row.signal),
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
                Text(
                    row.disclaimer.ifBlank {
                        "Educational snapshot. Not a Buy/Sell/Hold rating."
                    },
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
            prediction?.let { row ->
                Text("Ensemble preview", fontWeight = FontWeight.Medium, color = theme.text)
                row.predictions.take(3).forEach { point ->
                    EnsembleHorizonRow(point)
                }
                if (row.modelAccuracy > 0) {
                    Text(
                        "Directional backtest fit ${row.modelAccuracy.toInt()}% — capped educational score, not live accuracy.",
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                    )
                }
                Text(
                    row.disclaimer.ifBlank {
                        "Educational ensemble — not a forecast."
                    },
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EnsembleHorizonRow(point: PricePrediction) {
    val theme = LocalAppTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            point.horizon.ifBlank { "${point.days}d" },
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = theme.text,
        )
        Text(
            formatInr(point.predictedPrice),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = theme.text,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            formatSignedPct(point.changePercent),
            fontSize = 12.sp,
            color = if (point.changePercent >= 0) theme.positive else theme.negative,
        )
    }
}

private fun educationalStanceLabel(signal: String): String {
    val lean = when (signal.trim().uppercase()) {
        "STRONG_BUY", "BUY", "UP_LEAN", "SLIGHT_UP" -> "constructive"
        "STRONG_SELL", "SELL", "DOWN_LEAN" -> "cautious"
        else -> "mixed"
    }
    return "Tape lean: $lean — not a recommendation."
}

private fun formatCurrency(value: Double): String = formatInr(value)

private fun formatSignedPercent(value: Double): String = formatSignedPct(value)

private fun formatCompactVolume(value: Long): String = formatVolumeCompact(value)

private fun formatCompactNumber(value: Double): String = formatInrCompact(value)

@Composable
private fun AiQuickActionsRow(
    symbol: String,
    onAiQuery: (String) -> Unit,
    onAnalyze: () -> Unit,
    onPredict: () -> Unit,
) {
    val theme = LocalAppTheme.current
    data class AiAction(val emoji: String, val label: String, val onClick: () -> Unit)
    val actions = listOf(
        AiAction("🤖", "Analyze", onAnalyze),
        AiAction("📈", "Ensemble", onPredict),
        AiAction("🛡️", "Risk check") {
            onAiQuery("What are the key risks for $symbol from available market fields? Educational only — no buy or sell.")
        },
        AiAction("📓", "Practice note") {
            onAiQuery("Help me write a paper-journal setup for $symbol: thesis, invalidation, and what I will measure. No buy or sell advice.")
        },
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
                        onClick = action.onClick,
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
                        onClick = action.onClick,
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
