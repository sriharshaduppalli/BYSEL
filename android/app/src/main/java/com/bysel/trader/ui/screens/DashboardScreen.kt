package com.bysel.trader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.NewsWidget
import com.bysel.trader.ui.components.WatchlistWidget
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import com.bysel.trader.ui.components.DashboardSkeletonLoader
import androidx.compose.foundation.clickable
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.viewmodel.DashboardViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private data class DashboardMetric(
    val title: String,
    val value: String,
    val caption: String,
    val accent: Color,
)

@Composable
fun DashboardScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    onAiClick: (() -> Unit)? = null
) {
    val dashboardViewModel: DashboardViewModel = viewModel()
    val pinnedStocks by dashboardViewModel.pinnedStocks.collectAsState()
    val portfolioPinned by dashboardViewModel.portfolioPinned.collectAsState()
    val newsPinned by dashboardViewModel.newsPinned.collectAsState()
    val widgetOrder by dashboardViewModel.widgetOrder.collectAsState()
    val watchlistPinned by dashboardViewModel.watchlistPinned.collectAsState()
    val marketNews by dashboardViewModel.marketNews.collectAsState()
    val newsSymbols by dashboardViewModel.newsSymbols.collectAsState()
    val newsLoading by dashboardViewModel.newsLoading.collectAsState()
    val newsError by dashboardViewModel.newsError.collectAsState()

    var showOnboarding by rememberSaveable { mutableStateOf(false) }

    val ctx = LocalContext.current
    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            title = { Text("Home Guide") },
            text = {
                Text("\u2022 Pin/unpin News and Market Watch widgets using the star icon.\n\u2022 Reorder pinned widgets with the up/down arrows.\n\u2022 Portfolio widget pinned = stays in Your Space; unpinned = still live but below your pinned stack.\n\u2022 Use Home as a live cockpit for portfolio, news, and market movers.")
            },
            confirmButton = {
                TextButton(onClick = { showOnboarding = false }) { Text("Got it!") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOnboarding = false
                    Toast.makeText(ctx, "Use pinning to shape Home around your session workflow.", Toast.LENGTH_SHORT).show()
                }) { Text("Try it now!") }
            }
        )
    }

    if (isLoading && quotes.isEmpty()) {
        DashboardSkeletonLoader(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
        )
    } else {
        DashboardContent(
            dashboardViewModel = dashboardViewModel,
            holdings = holdings,
            quotes = quotes,
            error = error,
            onTradeClick = onTradeClick,
            onErrorDismiss = onErrorDismiss,
            onRefresh = onRefresh,
            onShowGuide = { showOnboarding = true },
            isRefreshing = isLoading || newsLoading,
            portfolioPinned = portfolioPinned,
            newsPinned = newsPinned,
            watchlistPinned = watchlistPinned,
            widgetOrder = widgetOrder,
            pinnedStocks = pinnedStocks,
            marketNews = marketNews,
            newsSymbols = newsSymbols,
            newsLoading = newsLoading,
            newsError = newsError,
            onAiClick = onAiClick,
        )
    }
}

@Composable
fun DashboardContent(
    dashboardViewModel: DashboardViewModel,
    holdings: List<Holding>,
    quotes: List<Quote>,
    error: String?,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onShowGuide: () -> Unit,
    isRefreshing: Boolean,
    portfolioPinned: Boolean,
    newsPinned: Boolean,
    watchlistPinned: Boolean,
    widgetOrder: List<String>,
    pinnedStocks: Set<String>,
    marketNews: List<MarketNewsHeadline>,
    newsSymbols: List<String>,
    newsLoading: Boolean,
    newsError: String?,
    onAiClick: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val pinnedList = remember(quotes, pinnedStocks) {
        quotes.filter { pinnedStocks.contains(it.symbol) }
    }
    val topGainers = remember(quotes, pinnedStocks) {
        quotes
            .sortedByDescending { it.pctChange }
            .filter { !pinnedStocks.contains(it.symbol) }
            .take(8)
    }
    val topLosers = remember(quotes, pinnedStocks) {
        quotes
            .sortedBy { it.pctChange }
            .filter { !pinnedStocks.contains(it.symbol) }
            .take(8)
    }
    val totalValue = remember(holdings) { holdings.sumOf { it.qty * it.last } }
    val totalInvested = remember(holdings) { holdings.sumOf { it.qty * it.avgPrice } }
    val totalPnL = remember(totalValue, totalInvested) { totalValue - totalInvested }
    val totalPnLPercent = remember(totalValue, totalInvested) {
        if (totalInvested > 0.0) (totalPnL / totalInvested) * 100.0 else 0.0
    }
    val positiveCount = remember(quotes) { quotes.count { it.pctChange >= 0.0 } }
    val negativeCount = remember(quotes) { quotes.count { it.pctChange < 0.0 } }
    val averageMove = remember(quotes) { if (quotes.isEmpty()) 0.0 else quotes.map { it.pctChange }.average() }
    val marketMoodTitle = remember(positiveCount, negativeCount, averageMove) {
        when {
            averageMove >= 0.75 && positiveCount >= negativeCount -> "Risk-On Session"
            averageMove <= -0.75 && negativeCount > positiveCount -> "Defensive Tape"
            positiveCount >= negativeCount * 2 && positiveCount > 0 -> "Breadth Expansion"
            negativeCount >= positiveCount * 2 && negativeCount > 0 -> "Pressure Building"
            else -> "Selective Opportunity"
        }
    }
    val marketMoodDetail = remember(positiveCount, negativeCount, averageMove, marketNews) {
        when {
            marketNews.isEmpty() && positiveCount == 0 && negativeCount == 0 -> "Waiting for the first full market snapshot."
            averageMove >= 0.75 -> "Momentum is broad enough to support fresh entries, but still reward quality names."
            averageMove <= -0.75 -> "Capital protection matters more right now. Use Home to narrow into resilient names first."
            else -> "The market is mixed. Use news flow and strong relative movers to find cleaner setups."
        }
    }
    val focusQuotes = remember(pinnedList, topGainers, topLosers) {
        (pinnedList + topGainers + topLosers)
            .distinctBy { it.symbol }
            .take(6)
    }
    val signalBuckets = remember(quotes) { buildSignalLabBuckets(quotes).take(3) }
    val dashboardMetrics = remember(totalValue, holdings, positiveCount, negativeCount, focusQuotes, marketNews, theme) {
        listOf(
            DashboardMetric(
                title = "Portfolio",
                value = if (holdings.isEmpty()) "No holdings" else formatCompactCurrency(totalValue),
                caption = if (holdings.isEmpty()) "Build your first tracked book" else "${holdings.size} active holding${if (holdings.size == 1) "" else "s"}",
                accent = theme.primary,
            ),
            DashboardMetric(
                title = "Breadth",
                value = "$positiveCount up / $negativeCount down",
                caption = "Session leadership at a glance",
                accent = if (positiveCount >= negativeCount) theme.positive else theme.negative,
            ),
            DashboardMetric(
                title = "Top Swing",
                value = focusQuotes.firstOrNull()?.symbol ?: "Syncing",
                caption = focusQuotes.firstOrNull()?.let { formatSignedPercent(it.pctChange) } ?: "Waiting for quotes",
                accent = focusQuotes.firstOrNull()?.let { if (it.pctChange >= 0) theme.positive else theme.negative } ?: theme.textSecondary,
            ),
            DashboardMetric(
                title = "News Flow",
                value = "${marketNews.size}",
                caption = if (marketNews.isEmpty()) "No live headlines yet" else "Tracked storylines in motion",
                accent = theme.primary,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                onRefresh()
                dashboardViewModel.refreshMarketNews()
            },
            enabled = true
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
                .padding(16.dp)
        ) {
        item {
            DashboardHeroCard(
                marketMoodTitle = marketMoodTitle,
                marketMoodDetail = marketMoodDetail,
                totalValue = totalValue,
                totalPnL = totalPnL,
                totalPnLPercent = totalPnLPercent,
                holdingsCount = holdings.size,
                leadQuote = focusQuotes.firstOrNull(),
                headlineCount = marketNews.size,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                portfolioPinned = portfolioPinned,
                onRefresh = {
                    onRefresh()
                    dashboardViewModel.refreshMarketNews()
                },
                onShowGuide = onShowGuide,
                onTogglePortfolioPin = { dashboardViewModel.togglePortfolioPin() },
                onResetLayout = { dashboardViewModel.resetDashboardLayout() },
                onOpenLead = { focusQuotes.firstOrNull()?.let { onTradeClick(it.symbol) } },
            )
        }

        if (error != null) {
            item {
                TraceAwareErrorSnackbar(
                    error = error,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }

        item {
            DashboardMetricsRow(metrics = dashboardMetrics)
        }

        // AI Daily Brief Card
        item {
            AiDailyBriefCard(
                holdings = holdings,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                averageMove = averageMove,
                newsCount = marketNews.size,
                topMover = focusQuotes.firstOrNull(),
                onAskAi = onAiClick
            )
        }

        if (focusQuotes.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Quick Board",
                    subtitle = "Open the strongest symbols and storylines from Home without digging through tabs.",
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(focusQuotes.take(4), key = { it.symbol }) { quote ->
                        HomeQuoteBoardCard(
                            quote = quote,
                            onOpen = { onTradeClick(quote.symbol) },
                            isPinned = pinnedStocks.contains(quote.symbol),
                            onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                        )
                    }
                }
            }
        }

        if (signalBuckets.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Signal Playbooks",
                    subtitle = "Home now surfaces breakout, volume, dividend, and upside pockets without leaving the cockpit.",
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(signalBuckets, key = { it.title }) { bucket ->
                        HomeSignalCard(
                            bucket = bucket,
                            onOpen = { bucket.quotes.firstOrNull()?.let { quote -> onTradeClick(quote.symbol) } },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Your Space",
                subtitle = "Pin the modules you want above the fold and reorder them around your session workflow.",
            )
        }

        widgetOrder.forEachIndexed { idx, widget ->
                when (widget) {
                    "portfolio" -> if (portfolioPinned) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                                elevation = CardDefaults.cardElevation(8.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                    PortfolioSummaryCard(holdings)
                                    Column {
                                            IconButton(onClick = { dashboardViewModel.togglePortfolioPin() }) {
                                                Icon(
                                                    Icons.Default.PushPin,
                                                    contentDescription = "Unpin Portfolio",
                                                    tint = LocalAppTheme.current.primary
                                                )
                                            }
                                        IconButton(onClick = { dashboardViewModel.moveWidgetUp("portfolio") }, enabled = idx > 0) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                        }
                                        IconButton(onClick = { dashboardViewModel.moveWidgetDown("portfolio") }, enabled = idx < widgetOrder.size - 1) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < widgetOrder.size - 1) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                "news" -> if (newsPinned) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                NewsWidget(
                                    isPinned = true,
                                    headlines = marketNews,
                                    trackedSymbols = newsSymbols,
                                    isLoading = newsLoading,
                                    error = newsError,
                                    onPinClick = { dashboardViewModel.toggleNewsPin() },
                                    onRefresh = { dashboardViewModel.refreshMarketNews() },
                                )
                                Column {
                                    IconButton(onClick = { dashboardViewModel.moveWidgetUp("news") }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                    IconButton(onClick = { dashboardViewModel.moveWidgetDown("news") }, enabled = idx < widgetOrder.size - 1) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < widgetOrder.size - 1) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                "watchlist" -> if (watchlistPinned) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                WatchlistWidget(
                                    isPinned = true,
                                    quotes = focusQuotes,
                                    onPinClick = { dashboardViewModel.toggleWatchlistPin() },
                                    onQuoteClick = { onTradeClick(it.symbol) },
                                )
                                Column {
                                    IconButton(onClick = { dashboardViewModel.moveWidgetUp("watchlist") }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                    IconButton(onClick = { dashboardViewModel.moveWidgetDown("watchlist") }, enabled = idx < widgetOrder.size - 1) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < widgetOrder.size - 1) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        if (!newsPinned) {
            item {
                NewsWidget(
                    isPinned = false,
                    headlines = marketNews,
                    trackedSymbols = newsSymbols,
                    isLoading = newsLoading,
                    error = newsError,
                    onPinClick = { dashboardViewModel.toggleNewsPin() },
                    onRefresh = { dashboardViewModel.refreshMarketNews() },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (!watchlistPinned) {
            item {
                WatchlistWidget(
                    isPinned = false,
                    quotes = focusQuotes,
                    onPinClick = { dashboardViewModel.toggleWatchlistPin() },
                    onQuoteClick = { onTradeClick(it.symbol) },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (pinnedStocks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Pinned Conviction",
                    subtitle = "The names you explicitly want to keep on the radar while the market rotates.",
                )
            }
            items(items = pinnedList, key = { "pinned_${it.symbol}" }) { quote ->
                GainerLosersCard(
                    quote,
                    isGainer = quote.pctChange >= 0,
                    isPinned = true,
                    onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                    onClick = { onTradeClick(quote.symbol) }
                )
            }
        }

        item {
            SectionHeader(
                title = "Momentum Leaders",
                subtitle = "Strong relative performers worth evaluating before they leave your decision window.",
            )
        }
        items(items = topGainers, key = { "gainer_${it.symbol}" }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = true,
                isPinned = false,
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }

        item {
            SectionHeader(
                title = "Pressure Zone",
                subtitle = "Names under the heaviest selling pressure, useful for risk checks and reversal hunting.",
            )
        }

        items(items = topLosers, key = { "loser_${it.symbol}" }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = false,
                isPinned = false,
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    }
    }
}

@Composable
private fun DashboardHeroCard(
    marketMoodTitle: String,
    marketMoodDetail: String,
    totalValue: Double,
    totalPnL: Double,
    totalPnLPercent: Double,
    holdingsCount: Int,
    leadQuote: Quote?,
    headlineCount: Int,
    positiveCount: Int,
    negativeCount: Int,
    portfolioPinned: Boolean,
    onRefresh: () -> Unit,
    onShowGuide: () -> Unit,
    onTogglePortfolioPin: () -> Unit,
    onResetLayout: () -> Unit,
    onOpenLead: () -> Unit,
) {
    val theme = LocalAppTheme.current

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BYSEL Pulse",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = marketMoodTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                    )
                    Text(
                        text = marketMoodDetail,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
                AssistChip(
                    onClick = onTogglePortfolioPin,
                    label = { Text(if (portfolioPinned) "Portfolio widget pinned" else "Pin portfolio widget") },
                )
            }

            Text(
                text = if (portfolioPinned) {
                    "Pinned: Portfolio card stays in Your Space and can be reordered."
                } else {
                    "Live: Portfolio values still update, but the card is not pinned in Your Space."
                },
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 16.sp,
            )

            if (holdingsCount > 0) {
                Text(
                    text = formatCompactCurrency(totalValue),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = "${formatCompactCurrency(totalPnL)} • ${formatSignedPercent(totalPnLPercent)} since entry across $holdingsCount holding${if (holdingsCount == 1) "" else "s"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (totalPnL >= 0) theme.positive else theme.negative,
                )
            } else {
                Text(
                    text = "No holdings yet",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = "Use Home to move from market signal to stock context quickly, then build positions with conviction.",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onShowGuide) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Home Guide")
                }
                if (leadQuote != null) {
                    Button(onClick = onOpenLead) {
                        Text("Open ${leadQuote.symbol}")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("$headlineCount headlines") })
                AssistChip(onClick = {}, label = { Text("$positiveCount up / $negativeCount down") })
                if (leadQuote != null) {
                    AssistChip(
                        onClick = onOpenLead,
                        label = {
                            Text(
                                text = "${leadQuote.symbol} ${formatSignedPercent(leadQuote.pctChange)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            TextButton(onClick = onResetLayout) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset dashboard layout")
            }
        }
    }
}

@Composable
private fun DashboardMetricsRow(metrics: List<DashboardMetric>) {
    LazyRow(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(metrics, key = { it.title }) { metric ->
            Card(
                modifier = Modifier.width(180.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(metric.title, color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text(metric.value, color = metric.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(metric.caption, color = LocalAppTheme.current.text, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)) {
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
private fun HomeQuoteBoardCard(
    quote: Quote,
    onOpen: () -> Unit,
    isPinned: Boolean,
    onPinClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .padding(vertical = 4.dp)
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(quote.symbol, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onPinClick) {
                    Icon(
                        imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary,
                    )
                }
            }

            Text(
                text = formatSignedPercent(quote.pctChange),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
            )
            Text(
                text = if ((quote.volume ?: 0L) > 0L) "Volume ${formatCompactVolume(quote.volume)}" else "Open stock context and decide fast",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Text("Open stock context")
            }
        }
    }
}

@Composable
private fun HomeSignalCard(
    bucket: SignalLabBucket,
    onOpen: () -> Unit,
) {
    val leadQuote = bucket.quotes.firstOrNull()

    Card(
        modifier = Modifier
            .width(240.dp)
            .padding(vertical = 4.dp)
            .clickable(enabled = leadQuote != null, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bucket.title,
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${bucket.quotes.size} live setup${if (bucket.quotes.size == 1) "" else "s"}",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = signalLabLeadSummary(bucket),
                color = LocalAppTheme.current.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bucket.quotes.take(3).joinToString(" • ") { it.symbol },
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onOpen,
                enabled = leadQuote != null,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (leadQuote != null) "Open ${leadQuote.symbol}" else "No live setup")
            }
        }
    }
}

private fun formatCompactCurrency(value: Double): String {
    val absValue = kotlin.math.abs(value)
    return when {
        absValue >= 10_000_000 -> "₹${String.format("%.2f", value / 10_000_000)}Cr"
        absValue >= 100_000 -> "₹${String.format("%.2f", value / 100_000)}L"
        else -> "₹${String.format("%,.0f", value)}"
    }
}

private fun formatSignedPercent(value: Double): String {
    return buildString {
        if (value > 0) append("+")
        append(String.format("%.2f", value))
        append("%")
    }
}

private fun formatCompactVolume(value: Long?): String {
    val volume = value ?: return "0"
    return when {
        volume >= 10_000_000L -> String.format("%.2fCr", volume / 10_000_000.0)
        volume >= 100_000L -> String.format("%.2fL", volume / 100_000.0)
        volume >= 1_000L -> String.format("%.1fK", volume / 1_000.0)
        else -> volume.toString()
    }
}

@Composable
fun PortfolioSummaryCard(holdings: List<Holding>) {
    val totalValue = holdings.sumOf { it.qty * it.last }
    val totalInvested = holdings.sumOf { it.qty * it.avgPrice }
    val totalPnL = totalValue - totalInvested
    val totalPnLPercent = if (totalInvested > 0) (totalPnL / totalInvested) * 100 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppTheme.current.card)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Total Portfolio Value",
                fontSize = 14.sp,
                color = LocalAppTheme.current.textSecondary
            )
            Text(
                text = "₹${String.format("%.2f", totalValue)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Invested",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalInvested)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "P&L",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalPnL)} (${String.format("%.2f", totalPnLPercent)}%)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalPnL >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color(0xFF2A2A2A)
            )

            Text(
                text = "Holdings: ${holdings.size} stocks",
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary
            )
        }
    }
}

@Composable
fun GainerLosersCard(
    quote: Quote,
    isGainer: Boolean,
    isPinned: Boolean = false,
    onPinClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) LocalAppTheme.current.positive else LocalAppTheme.current.textSecondary
                )
            }

            Row(
                modifier = Modifier
                    .background(
                        color = if (isGainer) Color(0xFF1B5E20).copy(alpha = 0.3f)
                        else Color(0xFFB71C1C).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGainer) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (isGainer) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGainer) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }
        }
    }
}

@Composable
fun AiDailyBriefCard(
    holdings: List<Holding>,
    positiveCount: Int,
    negativeCount: Int,
    averageMove: Double,
    newsCount: Int,
    topMover: Quote?,
    onAskAi: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val briefLines = remember(holdings, positiveCount, negativeCount, averageMove, newsCount, topMover) {
        buildList {
            // Market tone
            when {
                averageMove >= 0.75 -> add("Markets are broadly positive today — momentum favours fresh entries.")
                averageMove <= -0.75 -> add("Selling pressure is dominant — consider tightening stops.")
                positiveCount > negativeCount -> add("Mixed session leaning bullish — selective opportunities.")
                else -> add("Caution in the tape — breadth is weak today.")
            }
            // Portfolio
            if (holdings.isNotEmpty()) {
                val pnl = holdings.sumOf { it.qty * it.last } - holdings.sumOf { it.qty * it.avgPrice }
                if (pnl >= 0) add("Your portfolio is up ₹${String.format("%,.0f", pnl)} today.")
                else add("Your portfolio is down ₹${String.format("%,.0f", -pnl)} — review weak links.")
            } else {
                add("No holdings yet — ask AI for starter picks.")
            }
            // Top mover
            topMover?.let {
                add("${it.symbol} leads at ${if (it.pctChange >= 0) "+" else ""}${String.format("%.1f", it.pctChange)}%.")
            }
            // News
            if (newsCount > 0) add("$newsCount live headlines driving sentiment.")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Daily Brief",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = theme.text
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            briefLines.forEach { line ->
                Text(
                    text = "• $line",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp,
                )
            }
            if (onAskAi != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAskAi,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Ask AI for Detailed Analysis", fontSize = 13.sp)
                }
            }
        }
    }
}
