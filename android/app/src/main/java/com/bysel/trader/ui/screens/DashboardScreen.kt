package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bysel.trader.ui.viewmodel.DashboardViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.NewsWidget
import com.bysel.trader.ui.components.WatchlistWidget
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke

@Composable
fun DashboardScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit
) {
    val dashboardViewModel: DashboardViewModel = viewModel()
    val pinnedStocks by dashboardViewModel.pinnedStocks.collectAsState()
    val portfolioPinned by dashboardViewModel.portfolioPinned.collectAsState()
    val newsPinned by dashboardViewModel.newsPinned.collectAsState()
    val widgetOrder by dashboardViewModel.widgetOrder.collectAsState()
    val watchlistPinned by dashboardViewModel.watchlistPinned.collectAsState()

    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    // Show onboarding on first launch or via help button
    LaunchedEffect(Unit) {
        // TODO: Replace with persistent flag (e.g., DataStore) for production
        if (!showOnboarding) showOnboarding = true
    }

    when {
        showOnboarding -> {
            AlertDialog(
                onDismissRequest = { showOnboarding = false },
                title = { Text("Customize Your Dashboard") },
                text = {
                    Text("\u2022 Pin/unpin Portfolio and News widgets using the star icon.\n\u2022 Reorder pinned widgets with the up/down arrows.\n\u2022 Your layout is saved automatically.\n\nTry it now!")
                },
                confirmButton = {
                    TextButton(onClick = { showOnboarding = false }) { Text("Got it!") }
                }
            )
        }
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalAppTheme.current.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LocalAppTheme.current.primary)
            }
        }
        else -> {
            DashboardContent(
                dashboardViewModel = dashboardViewModel,
                holdings = holdings,
                quotes = quotes,
                error = error,
                onTradeClick = onTradeClick,
                onErrorDismiss = onErrorDismiss,
                portfolioPinned = portfolioPinned,
                newsPinned = newsPinned,
                watchlistPinned = watchlistPinned,
                widgetOrder = widgetOrder,
                pinnedStocks = pinnedStocks
            )
        }
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
    portfolioPinned: Boolean,
    newsPinned: Boolean,
    watchlistPinned: Boolean,
    widgetOrder: List<String>,
    pinnedStocks: Set<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Reset layout button
        Button(onClick = { dashboardViewModel.resetDashboardLayout() }, colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.negative)) {
            Icon(Icons.Default.Restore, contentDescription = "Reset Layout")
            Spacer(Modifier.width(4.dp))
            Text("Reset Layout")
        }
        // Onboarding/tutorial button
        IconButton(onClick = { /* handled in DashboardScreen */ }) {
            Icon(Icons.Default.Info, contentDescription = "Show Dashboard Tutorial")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Portfolio Overview",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }

        // Render pinned widgets in user order with up/down controls
        widgetOrder.forEachIndexed { idx, widget ->
                when (widget) {
                    "portfolio" -> if (portfolioPinned) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                                elevation = CardDefaults.cardElevation(8.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                    PortfolioSummaryCard(holdings, quotes)
                                    Column {
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
                                .padding(vertical = 4.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                NewsWidget(isPinned = true, onPinClick = { dashboardViewModel.toggleNewsPin() })
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
                                .padding(vertical = 4.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else null
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                WatchlistWidget(isPinned = true, onPinClick = { dashboardViewModel.toggleWatchlistPin() })
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

        // Portfolio Summary Card (if not pinned)
        if (!portfolioPinned) {
            item {
                PortfolioSummaryCard(holdings, quotes)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // News Widget (if not pinned)
        if (!newsPinned) {
            item {
                NewsWidget(isPinned = false, onPinClick = { dashboardViewModel.toggleNewsPin() })
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Watchlist Widget (if not pinned)
        if (!watchlistPinned) {
            item {
                WatchlistWidget(isPinned = false, onPinClick = { dashboardViewModel.toggleWatchlistPin() })
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Pinned Stocks Section
        if (pinnedStocks.isNotEmpty()) {
            item {
                Text(
                    text = "Pinned Stocks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(quotes.filter { pinnedStocks.contains(it.symbol) }) { quote ->
                GainerLosersCard(
                    quote,
                    isGainer = quote.pctChange >= 0,
                    isPinned = true,
                    onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                    onClick = { onTradeClick(quote.symbol) }
                )
            }
        }

        // Top Gainers
        item {
            Text(
                text = "Top Gainers",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        items(quotes.sortedByDescending { it.pctChange }.take(3).filter { !pinnedStocks.contains(it.symbol) }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = true,
                isPinned = false,
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }

        item {
            Text(
                text = "Top Losers",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 0.dp)
            )
        }

        // Top Losers
        items(quotes.sortedBy { it.pctChange }.take(3).filter { !pinnedStocks.contains(it.symbol) }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = false,
                isPinned = false,
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }

        if (error != null) {
            item {
                Snackbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = onErrorDismiss) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Column {
                        Text(error)
                        // Pinned Portfolio Section
                        if (portfolioPinned) {
                            PortfolioSummaryCard(holdings, quotes)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun PortfolioSummaryCard(holdings: List<Holding>, quotes: List<Quote>) {
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
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
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
