package com.bysel.trader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.WatchlistSymbols
import com.bysel.trader.data.importbook.ImportedBook
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.PaperPortfolioRisk
import com.bysel.trader.data.models.PortfolioHealthScore
import com.bysel.trader.portfolio.PaperPortfolioRiskMath
import com.bysel.trader.ui.components.QuoteCard
import com.bysel.trader.ui.components.StockNotesIcon
import com.bysel.trader.ui.components.LoadingScreen
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.exclusiveHorizontalScroll
import com.bysel.trader.ui.components.SwipeToDismissItem
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import com.bysel.trader.ui.components.PortfolioSkeletonLoader
import com.bysel.trader.ui.components.DashboardSkeletonLoader
import com.bysel.trader.ui.components.WatchlistSortMode
import com.bysel.trader.ui.components.sortedByWatchlistMode
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.saveable.rememberSaveable
import com.bysel.trader.ui.components.PortfolioSortMode
import com.bysel.trader.ui.components.sortedByPortfolioMode
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatSignedPct

@Composable
fun WatchlistScreen(
    quotes: List<Quote>,
    watchlistSymbols: List<String> = emptyList(),
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onErrorDismiss: () -> Unit
) {
    var sortModeName by rememberSaveable { mutableStateOf(WatchlistSortMode.MOVE.name) }
    val sortMode = remember(sortModeName) {
        runCatching { WatchlistSortMode.valueOf(sortModeName) }.getOrDefault(WatchlistSortMode.MOVE)
    }
    val displayQuotes = remember(quotes, watchlistSymbols) {
        val saved = WatchlistSymbols.normalizeAll(watchlistSymbols)
        if (saved.isEmpty()) {
            quotes
        } else {
            saved.map { symbol ->
                WatchlistSymbols.findQuote(quotes, symbol) ?: Quote(symbol = symbol)
            }
        }
    }
    val sortedQuotes = remember(displayQuotes, sortMode) { displayQuotes.sortedByWatchlistMode(sortMode) }
    // Quote-load failures only. Order/F&O validation lives on other channels.
    val watchlistLoadError = error

    if (isLoading && quotes.isEmpty()) {
        DashboardSkeletonLoader(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
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
                        text = "My Watchlist",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = if (sortedQuotes.isEmpty()) {
                            "Empty · add from Search (full NSE catalog)"
                        } else {
                            "${sortedQuotes.size} tracked · ${sortMode.label}"
                        },
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                    )
                }
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Refresh", fontSize = 12.sp)
                }
            }

            if (quotes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .exclusiveHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(WatchlistSortMode.entries, key = { it.name }) { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { sortModeName = mode.name },
                            label = { Text(mode.label, fontSize = 11.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (watchlistLoadError != null) {
                TraceAwareErrorSnackbar(
                    error = watchlistLoadError,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                enabled = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (sortedQuotes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No tracked symbols yet",
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search the full NSE listed universe, tap Watch, then open Trade → My list.",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(items = sortedQuotes, key = { it.symbol }) { quote ->
                            if (quote.last <= 0.0) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .clickable { onQuoteClick(quote) },
                                    colors = byselCardColors(),
                                    elevation = byselCardElevation(),
                                    border = byselCardBorder(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = quote.symbol,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LocalAppTheme.current.text,
                                        )
                                        Text(
                                            text = "Saved on My list · last price still loading",
                                            fontSize = 13.sp,
                                            color = LocalAppTheme.current.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            } else {
                                UpgradedQuoteCard(quote) { onQuoteClick(quote) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpgradedQuoteCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quote.symbol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text
                        )
                        StockNotesIcon(symbol = quote.symbol)
                    }
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 16.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    val ago = remember(quote.timestamp) { formatTimeAgo(quote.timestamp) }
                    Text(
                        text = ago,
                        fontSize = 10.sp,
                        color = LocalAppTheme.current.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        modifier = Modifier
                            .background(
                        color = if (quote.pctChange > 0) LocalAppTheme.current.pnlWash(true)
                                else LocalAppTheme.current.pnlWash(false),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (quote.pctChange > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                        )
                    }
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("View Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    portfolioHealth: PortfolioHealthScore?,
    healthLoading: Boolean,
    paperRisk: PaperPortfolioRisk? = null,
    scannerScores: Map<String, Int> = emptyMap(),
    importedBook: ImportedBook? = null,
    onImportCsv: (String, String) -> Unit = { _, _ -> },
    onClearImport: () -> Unit = {},
    onOpenImportedSymbol: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onRefreshHealth: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onErrorDismiss: () -> Unit,
    onNavigateToTrade: () -> Unit
) {
    val hasImported = importedBook?.rows?.isNotEmpty() == true
    LaunchedEffect(holdings.isNotEmpty(), hasImported) {
        if ((holdings.isNotEmpty() || hasImported) && (portfolioHealth == null || paperRisk == null)) {
            onRefreshHealth()
        }
    }

    val context = LocalContext.current
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) return@rememberLauncherForActivityResult
        onImportCsv(text, name)
    }

    val quoteBySymbol = remember(quotes) { quotes.associateBy { it.symbol.uppercase() } }
    val importedHoldings = remember(importedBook, quotes) {
        PaperPortfolioRiskMath.importedAsHoldings(importedBook?.rows.orEmpty(), quotes)
    }
    val (riskHoldings, importOverlap) = remember(holdings, importedHoldings) {
        PaperPortfolioRiskMath.mergePaperAndImported(holdings, importedHoldings)
    }
    val localRisk = remember(riskHoldings, quotes, scannerScores, portfolioHealth, importedBook, importOverlap) {
        PaperPortfolioRiskMath.fromHoldings(
            holdings = riskHoldings,
            quotes = quotes,
            scores = scannerScores,
            health = portfolioHealth,
            importNote = PaperPortfolioRiskMath.importNoteFor(importedBook, importOverlap),
        )
    }
    val riskSnapshot = remember(localRisk, paperRisk, hasImported) {
        if (hasImported) localRisk else PaperPortfolioRiskMath.preferRemote(localRisk, paperRisk)
    }
    var sortModeName by rememberSaveable { mutableStateOf(PortfolioSortMode.VALUE.name) }
    val sortMode = remember(sortModeName) {
        runCatching { PortfolioSortMode.valueOf(sortModeName) }.getOrDefault(PortfolioSortMode.VALUE)
    }
    val sortedHoldings = remember(holdings, quoteBySymbol, sortMode) {
        holdings.sortedByPortfolioMode(sortMode, quoteBySymbol)
    }

    var pendingSell by remember { mutableStateOf<Holding?>(null) }

    pendingSell?.let { holding ->
        val proceeds = holding.last * holding.qty
        val invested = holding.avgPrice * holding.qty
        val pnl = proceeds - invested
        AlertDialog(
            onDismissRequest = { pendingSell = null },
            title = { Text("Sell entire ${holding.symbol} holding?") },
            text = {
                Column {
                    Text("${holding.qty} share(s) at ₹${String.format("%.2f", holding.last)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estimated proceeds: ₹${String.format("%.2f", proceeds)}")
                    Text(
                        text = "Realised P&L: ${if (pnl >= 0) "+" else ""}₹${String.format("%.2f", pnl)}",
                        color = if (pnl >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This closes the whole position and cannot be undone.",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSell(holding.symbol, holding.qty)
                        pendingSell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.negative)
                ) {
                    Text("Sell all")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSell = null }) { Text("Cancel") }
            },
            containerColor = LocalAppTheme.current.card
        )
    }

    // `quotes` used for educational holding stance (day move)

    if (isLoading && holdings.isEmpty()) {
        PortfolioSkeletonLoader(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
        ) {
            val theme = LocalAppTheme.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Portfolio",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (holdings.isEmpty()) {
                            "Paper Practice · Simulated holdings"
                        } else {
                            "Paper Practice · ${sortMode.label}"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                OutlinedButton(
                    onClick = {
                        val sb = StringBuilder()
                        sb.appendLine("Symbol,Qty,Avg Price,Current Price,Value,P&L,P&L %")
                        sortedHoldings.forEach { h ->
                            val quote = quoteBySymbol[h.symbol.uppercase()]
                            val last = if (quote != null && quote.last > 0.0) quote.last else h.last
                            val value = last * h.qty
                            val invested = h.avgPrice * h.qty
                            val pnl = value - invested
                            val pct = if (invested > 0) "%.2f".format(pnl / invested * 100) else "0.00"
                            sb.appendLine(
                                "${h.symbol},${h.qty},${"%.2f".format(h.avgPrice)},${"%.2f".format(last)}," +
                                    "${"%.2f".format(value)},${"%.2f".format(pnl)},$pct",
                            )
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "BYSEL Portfolio Export")
                            putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                        }
                        ctx.startActivity(android.content.Intent.createChooser(intent, "Share Portfolio"))
                    },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary),
                    border = BorderStroke(1.dp, theme.primary),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = theme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", fontSize = 12.sp, color = theme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = theme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = theme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isLoading) "Updating" else "Refresh", fontSize = 12.sp, color = theme.onPrimary)
                }
            }

            if (holdings.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PortfolioSortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { sortModeName = mode.name },
                            label = { Text(mode.label, fontSize = 11.sp) },
                        )
                    }
                }
            }

            if (error != null) {
                TraceAwareErrorSnackbar(
                    error = error,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            if (holdings.isEmpty() && !hasImported && isLoading) {
                // Without this the first load flashes "No holdings yet" at users who do
                // in fact hold stock, because holdings are empty until the call returns.
                PortfolioSkeletonLoader(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (holdings.isEmpty() && !hasImported) {
                val loadFailed = !error.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = LocalAppTheme.current.textSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (loadFailed) {
                                "Couldn't load portfolio"
                            } else {
                                "No holdings yet"
                            },
                            fontSize = 16.sp,
                            color = LocalAppTheme.current.textSecondary,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = if (loadFailed) {
                                error ?: "Tap Refresh to retry."
                            } else {
                                "Start paper trading, or import a broker CSV / CAS extract (read-only)."
                            },
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToTrade,
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Start Trading")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                csvPicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
                            }
                        ) {
                            Text("Import CSV / CAS")
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = onRefresh,
                    enabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                    item {
                        PortfolioRiskDashboardCard(risk = riskSnapshot)
                    }
                    item {
                        ImportedBookCard(
                            book = importedBook,
                            quotes = quoteBySymbol,
                            onImport = {
                                csvPicker.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.ms-excel",
                                    )
                                )
                            },
                            onClear = onClearImport,
                            onOpenSymbol = onOpenImportedSymbol,
                        )
                    }

                    // Portfolio Health Score Card
                    if (portfolioHealth != null || healthLoading) {
                        item {
                            PortfolioHealthCard(
                                health = portfolioHealth,
                                isLoading = healthLoading,
                                onRefresh = onRefreshHealth
                            )
                        }
                    }

                    items(items = sortedHoldings, key = { it.symbol }) { holding ->
                        val quote = quoteBySymbol[holding.symbol.uppercase()]
                        val displayHolding = if (quote != null && quote.last > 0.0) {
                            holding.copy(
                                last = quote.last,
                                pnl = (quote.last - holding.avgPrice) * holding.qty,
                            )
                        } else {
                            holding
                        }
                        SwipeToDismissItem(
                            item = displayHolding,
                            onDismiss = { pendingSell = it },
                            enabled = true,
                            requireConfirmation = true,
                            dismissIcon = Icons.Filled.Sell,
                            dismissLabel = "Sell entire holding"
                        ) {
                            UpgradedPortfolioHoldingItem(
                                holding = displayHolding,
                                dayPctChange = quote?.pctChange ?: 0.0,
                                onBuy = { onBuy(holding.symbol, 1) },
                                onSell = { onSell(holding.symbol, 1) }
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun UpgradedPortfolioHoldingItem(
    holding: Holding,
    dayPctChange: Double = 0.0,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    val theme = LocalAppTheme.current
    val invested = holding.avgPrice * holding.qty
    val pnlPct = if (invested > 0) (holding.pnl / invested) * 100.0 else 0.0
    val stance = remember(holding.symbol, pnlPct, dayPctChange) {
        computeEducationalHoldingStance(pnlPct = pnlPct, dayPct = dayPctChange)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = holding.symbol,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.text
                        )
                        StockNotesIcon(
                            symbol = holding.symbol,
                            iconSize = 18.dp,
                            buttonSize = 48.dp,
                        )
                    }
                    Text(
                        text = "₹${String.format("%.2f", holding.last)}",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (holding.pnl > 0) "+" else ""}₹${String.format("%.2f", holding.pnl)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (holding.pnl > 0) theme.positive else theme.negative,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatSignedPct(dayPctChange),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dayPctChange >= 0) theme.positive else theme.negative,
                        maxLines = 1,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(stance.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Practice stance · ${stance.label}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stance.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stance.reason,
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(top = 1.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = theme.textSecondary.copy(alpha = 0.25f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Quantity",
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = "${holding.qty}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text
                    )
                }
                Column {
                    Text(
                        text = "Avg Cost",
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.avgPrice)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text
                    )
                }
                Column {
                    Text(
                        text = "Current Value",
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.qty * holding.last)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBuy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppTheme.current.positive,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSell,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.negative),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class EducationalHoldingStance(
    val label: String,
    val reason: String,
    val accent: Color,
)

private fun computeEducationalHoldingStance(pnlPct: Double, dayPct: Double): EducationalHoldingStance {
    return when {
        pnlPct >= 12.0 -> EducationalHoldingStance(
            label = "Practice Trim",
            reason = "Strong paper gain — rehearse booking a partial profit instead of hoping forever.",
            accent = Color(0xFF2E7D32),
        )
        pnlPct <= -10.0 -> EducationalHoldingStance(
            label = "Review Risk",
            reason = "Deep paper drawdown — journal the thesis or practice cutting size.",
            accent = Color(0xFFC62828),
        )
        dayPct <= -2.0 && pnlPct < 0.0 -> EducationalHoldingStance(
            label = "Tighten Stop",
            reason = "Weak day on a losing name — practice stop discipline before averaging down.",
            accent = Color(0xFFE65100),
        )
        pnlPct >= 4.0 && dayPct >= 0.5 -> EducationalHoldingStance(
            label = "Hold Strong",
            reason = "Working in your favor — avoid overtrading a winner just for activity.",
            accent = Color(0xFF1565C0),
        )
        else -> EducationalHoldingStance(
            label = "Hold & Journal",
            reason = "Neutral zone — note why you still own it in your practice journal.",
            accent = Color(0xFF546E7A),
        )
    }
}

@Composable
fun PortfolioHealthCard(
    health: PortfolioHealthScore?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
    ) {
        if (isLoading && health == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = LocalAppTheme.current.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing portfolio health...", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                }
            }
        } else if (health != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.HealthAndSafety,
                            contentDescription = null,
                            tint = LocalAppTheme.current.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Portfolio Health",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    // Grade badge
                    val theme = LocalAppTheme.current
                    val gradeColor = when {
                        health.overallScore >= 75 -> theme.positive
                        health.overallScore >= 55 -> Color(0xFFFFB300)
                        health.overallScore >= 35 -> Color(0xFFFF9100)
                        else -> theme.negative
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(gradeColor, gradeColor.copy(alpha = 0.3f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            health.grade,
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    // Refresh action for the health card
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh health", tint = LocalAppTheme.current.text)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Score bar
                val scoreColor = when {
                    health.overallScore >= 75 -> Color(0xFF00C853)
                    health.overallScore >= 55 -> Color(0xFFFFB300)
                    health.overallScore >= 35 -> Color(0xFFFF9100)
                    else -> Color(0xFFE53935)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { health.overallScore / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = scoreColor,
                        trackColor = LocalAppTheme.current.mutedSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${health.overallScore}/100",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    health.snapshotNote.ifBlank {
                        "Snapshot of current book quality — not a return forecast."
                    },
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                val buckets = healthScoreBuckets(health)
                if (buckets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Why this score",
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    buckets.forEach { bucket ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                bucket.title,
                                color = LocalAppTheme.current.text,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${bucket.score}/${bucket.maxScore}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (bucket.score / bucket.maxScore.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = scoreColor,
                            trackColor = LocalAppTheme.current.mutedSurface,
                        )
                        if (bucket.details.isNotBlank()) {
                            Text(
                                bucket.details,
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(bottom = 6.dp, top = 2.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary
                Text(
                    health.summary,
                    color = LocalAppTheme.current.text.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                // Risk level
                Spacer(modifier = Modifier.height(8.dp))
                val riskColor = when (health.riskLevel) {
                    "low" -> Color(0xFF00C853)
                    "moderate" -> Color(0xFFFFB300)
                    "high" -> Color(0xFFFF9100)
                    else -> Color(0xFFE53935)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Risk: ${health.riskLevel.uppercase()}",
                        color = riskColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "${health.stockCount} stocks, ${health.sectorCount} sectors",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp
                    )
                }

                // Suggestions (show first 3)
                if (health.suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = LocalAppTheme.current.textSecondary.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Suggestions",
                        color = LocalAppTheme.current.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    health.suggestions.take(3).forEach { suggestion ->
                        Text(
                            suggestion,
                            color = LocalAppTheme.current.text.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportedBookCard(
    book: ImportedBook?,
    quotes: Map<String, Quote>,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Imported book", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = theme.text)
            Text(
                if (book == null || book.rows.isEmpty()) {
                    "Read-only overlay from a broker CSV or CAS extract. Live last price when NSE is open. Not your paper wallet."
                } else {
                    "${book.sourceLabel} · ${book.rows.size} names. Read-only — live marks when the session is open."
                },
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport) { Text("Import CSV") }
                if (book?.rows?.isNotEmpty() == true) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            book?.rows.orEmpty().take(12).forEach { row ->
                val quote = quotes[row.symbol]
                val last = quote?.last?.takeIf { it > 0 } ?: row.lastMark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSymbol(row.symbol) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.symbol, color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${row.qty} × ${if (last > 0) formatInr(last) else "—"}",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            if ((book?.rows?.size ?: 0) > 12) {
                Text("+${book!!.rows.size - 12} more", fontSize = 11.sp, color = theme.textSecondary)
            }
        }
    }
}

private data class HealthScoreBucket(
    val title: String,
    val score: Int,
    val maxScore: Int,
    val details: String,
)

private fun healthScoreBuckets(health: PortfolioHealthScore): List<HealthScoreBucket> {
    val labels = listOf(
        "diversification" to "Diversification",
        "risk" to "Risk spread",
        "quality" to "Name quality",
        "balance" to "Balance",
    )
    return labels.mapNotNull { (key, title) ->
        val raw = health.breakdown[key] ?: return@mapNotNull null
        val score = (raw["score"] as? Number)?.toInt() ?: return@mapNotNull null
        val maxScore = (raw["maxScore"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 25
        val details = raw["details"]?.toString().orEmpty()
        HealthScoreBucket(title, score, maxScore, details)
    }
}

/** Format a millisecond timestamp into a human-readable "Xs ago" / "Xm ago" label */
private fun formatTimeAgo(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
