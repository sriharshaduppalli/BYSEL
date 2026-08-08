package com.bysel.trader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.MarketStatus
import com.bysel.trader.data.models.StockSearchResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.components.filterDecimalInput
import com.bysel.trader.ui.components.filterDigitsOnly
import com.bysel.trader.ui.components.InfoChip
import com.bysel.trader.ui.theme.AnimatedAmountText
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.ScreenHeader
import com.bysel.trader.ui.theme.TickPriceText
import com.bysel.trader.ui.theme.MarqueeText
import com.bysel.trader.ui.theme.TradeActionButton
import com.bysel.trader.ui.theme.animatedChangeColor
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import com.bysel.trader.ui.components.OrderRejectionBanner
import com.bysel.trader.ui.components.RejectionCategory
import com.bysel.trader.ui.components.resolveRejection
import com.bysel.trader.ui.components.WatchlistSortMode
import com.bysel.trader.ui.components.sortedByWatchlistMode
import com.bysel.trader.viewmodel.TradingViewModel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private fun formatVolume(v: Long?): String {
    if (v == null) return "N/A"
    return when {
        v >= 1_000_000 -> String.format("%.2fM", v / 1_000_000.0)
        v >= 1_000 -> String.format("%.1fK", v / 1_000.0)
        else -> v.toString()
    }
}

private fun formatCurrency(value: Double): String = formatInr(value)

private data class WatchlistInsight(
    val quote: Quote,
    val momentum: String,
    val risk: String,
    val confidence: Int,
    val rationale: String,
    val flags: List<String>
)

private fun computeWatchlistInsight(quote: Quote): WatchlistInsight {
    val spreadPct = if (quote.bid != null && quote.ask != null && quote.last > 0.0) {
        ((quote.ask - quote.bid) / quote.last) * 100.0
    } else {
        0.0
    }
    val intradayRangePct = if (quote.dayHigh != null && quote.dayLow != null && quote.last > 0.0) {
        ((quote.dayHigh - quote.dayLow) / quote.last) * 100.0
    } else {
        0.0
    }
    val volumeRatio = if (quote.volume != null && quote.avgVolume != null && quote.avgVolume > 0) {
        quote.volume.toDouble() / quote.avgVolume
    } else {
        null
    }

    val momentum = when {
        quote.pctChange >= 1.25 -> "Bullish"
        quote.pctChange <= -1.25 -> "Bearish"
        else -> "Sideways"
    }

    var riskScore = 0
    if (intradayRangePct >= 3.5) riskScore += 2 else if (intradayRangePct >= 2.0) riskScore += 1
    if (abs(quote.pctChange) >= 3.0) riskScore += 2 else if (abs(quote.pctChange) >= 1.5) riskScore += 1
    if (spreadPct >= 0.45) riskScore += 2 else if (spreadPct >= 0.2) riskScore += 1

    val risk = when {
        riskScore >= 4 -> "High"
        riskScore >= 2 -> "Medium"
        else -> "Low"
    }

    val confidenceRaw = 58 +
        (abs(quote.pctChange) * 6.0).roundToInt() +
        (if ((volumeRatio ?: 0.0) >= 1.4) 8 else 0) -
        (if (spreadPct >= 0.45) 8 else 0)
    val confidence = confidenceRaw.coerceIn(35, 92)

    val rationale = when (momentum) {
        "Bullish" -> "Strength with ${formatSignedPct(quote.pctChange)} move${if (volumeRatio != null) " and ${String.format("%.1f", volumeRatio)}x volume" else ""}."
        "Bearish" -> "Pressure with ${formatSignedPct(quote.pctChange)} decline; protect downside before averaging."
        else -> "Range-bound action; prefer staged entries near support levels."
    }

    val flags = mutableListOf<String>()
    if (abs(quote.pctChange) >= 2.0) flags.add("Move ${formatSignedPct(quote.pctChange)}")
    if (intradayRangePct >= 3.0) flags.add("Range ${String.format("%.1f", intradayRangePct)}%")
    if (volumeRatio != null && volumeRatio >= 1.5) flags.add("${String.format("%.1f", volumeRatio)}x vol")
    if (spreadPct >= 0.35) flags.add("Wide spread")
    if (flags.isEmpty()) flags.add("Stable")

    return WatchlistInsight(
        quote = quote,
        momentum = momentum,
        risk = risk,
        confidence = confidence,
        rationale = rationale,
        flags = flags
    )
}

private data class TradeWorkspaceTab(
    val title: String,
    val caption: String,
)

private val TRADE_WORKSPACE_TABS = listOf(
    TradeWorkspaceTab("Spot", "Equities"),
    TradeWorkspaceTab("Advanced", "Triggers & baskets"),
    TradeWorkspaceTab("Options", "Chain & Greeks"),
    TradeWorkspaceTab("Futures", "Radar"),
)

@Composable
fun TradingScreen(
    isLoading: Boolean,
    error: String?,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onAddFunds: (Double, String) -> Unit,
    onAddPracticeCredit: (Double) -> Unit = {},
    onErrorDismiss: () -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    openAddFundsRequest: Boolean = false,
    onOpenAddFundsConsumed: () -> Unit = {},
    isActive: Boolean = true,
    viewModel: com.bysel.trader.viewmodel.TradingViewModel
) {
    // Only warm the full quote universe while Trade is the active pager page
    // (adjacent pages stay composed via beyondBoundsPageCount).
    DisposableEffect(viewModel, isActive) {
        if (isActive) {
            viewModel.startFastRefresh()
            viewModel.loadAllQuotes()
        }
        onDispose {
            if (isActive) viewModel.stopFastRefresh()
        }
    }
    // Prefetch browse catalog as soon as Trade is visible (not only when + Add opens).
    LaunchedEffect(isActive) {
        if (isActive) viewModel.ensureSymbolCatalogLoaded()
    }
    val liveQuotes by viewModel.quotes.collectAsStateWithLifecycle()
    val watchlistSymbols by viewModel.watchlist.collectAsStateWithLifecycle()
    val selectedQuote by viewModel.selectedQuote.collectAsStateWithLifecycle()
    // AI Trade Coach Dialog
    val tradeCoachTip by viewModel.tradeCoachTip.collectAsStateWithLifecycle()
    if (tradeCoachTip != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearTradeCoachTip() },
            title = { Text("Coach says…", fontWeight = FontWeight.Bold) },
            text = { Text(tradeCoachTip ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.clearTradeCoachTip() }) {
                    Text("Got it!")
                }
            }
        )
    }
    var showAddFundsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openAddFundsRequest) {
        if (openAddFundsRequest) {
            showAddFundsDialog = true
            onOpenAddFundsConsumed()
        }
    }
    var selectedWorkspaceIndex by remember { mutableIntStateOf(0) }
    var activeTradeSymbol by remember { mutableStateOf<String?>(null) }

    fun openTradeSheet(quote: Quote) {
        activeTradeSymbol = quote.symbol.uppercase()
        viewModel.setSelectedQuote(quote)
    }

    fun openTradeSheet(symbol: String) {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) {
            return
        }

        activeTradeSymbol = normalizedSymbol
        val existingQuote = liveQuotes.firstOrNull { it.symbol.equals(normalizedSymbol, ignoreCase = true) }
        if (existingQuote != null) {
            viewModel.setSelectedQuote(existingQuote)
        } else {
            viewModel.fetchAndSelectQuote(normalizedSymbol)
        }
    }

    if (activeTradeSymbol != null && selectedQuote?.symbol?.equals(activeTradeSymbol, ignoreCase = true) == true) {
        TradeBottomSheet(
            quote = selectedQuote!!,
            walletBalance = walletBalance,
            marketStatus = marketStatus,
            onDismiss = {
                activeTradeSymbol = null
                viewModel.clearPreTradeCopilotSignal()
            },
            onBuy = { qty -> onBuy(selectedQuote!!.symbol, qty) },
            onSell = { qty -> onSell(selectedQuote!!.symbol, qty) },
            onTraceSupportLookup = onTraceSupportLookup,
            viewModel = viewModel,
        )
    }

    if (showAddFundsDialog) {
        AddFundsDialog(
            onDismiss = { showAddFundsDialog = false },
            onAddPracticeCredit = { amount ->
                onAddPracticeCredit(amount)
                showAddFundsDialog = false
            },
            onAddViaUpi = { amount, upiProvider ->
                onAddFunds(amount, upiProvider)
                showAddFundsDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = byselCardColors(),
            shape = MaterialTheme.shapes.medium,
        ) {
            ScreenHeader(
                title = "Trade",
                subtitle = "Watchlist-first paper trading. Advanced tools stay one tap away.",
                compact = true,
                modifier = Modifier.padding(14.dp),
                trailing = {
                    // Practice wallet lives on the Trade header — not next to watchlist +Add.
                    AssistChip(
                        onClick = { showAddFundsDialog = true },
                        label = {
                            if (walletBalance > 0.0) {
                                AnimatedAmountText(
                                    amount = walletBalance,
                                    formatter = { "₹${String.format("%,.0f", it)}" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalAppTheme.current.text,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text(
                                    "Add credit",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.AccountBalanceWallet,
                                contentDescription = "Practice wallet",
                                modifier = Modifier.size(16.dp),
                                tint = LocalAppTheme.current.primary,
                            )
                        },
                    )
                },
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedWorkspaceIndex,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 12.dp,
            containerColor = LocalAppTheme.current.surface,
            contentColor = LocalAppTheme.current.text,
            divider = {}
        ) {
            TRADE_WORKSPACE_TABS.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedWorkspaceIndex == index,
                    onClick = { selectedWorkspaceIndex = index },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tab.title, fontWeight = FontWeight.SemiBold)
                            Text(tab.caption, fontSize = 10.sp)
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selectedWorkspaceIndex,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 16 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 20 })
                },
                label = "tradeWorkspace",
            ) { workspace ->
                when (workspace) {
                    0 -> SpotTradingWorkspace(
                        isLoading = isLoading,
                        error = error,
                        marketStatus = marketStatus,
                        onRefresh = onRefresh,
                        onErrorDismiss = onErrorDismiss,
                        onTraceSupportLookup = onTraceSupportLookup,
                        onSelectQuote = { openTradeSheet(it) },
                        onOpenSymbol = { symbol -> openTradeSheet(symbol) },
                        onOpenAdvancedWorkspace = { selectedWorkspaceIndex = 1 },
                        onOpenDerivativesWorkspace = { selectedWorkspaceIndex = 2 },
                        viewModel = viewModel,
                    )
                    1 -> AdvancedOrdersScreen(viewModel)
                    2 -> DerivativesIntelligenceScreen(viewModel)
                    else -> FuturesRadarScreen(
                        viewModel = viewModel,
                        quotes = liveQuotes,
                        marketStatus = marketStatus,
                        watchlistSymbols = watchlistSymbols,
                        onOpenSpotTrade = { openTradeSheet(it) },
                        onOpenOptions = { selectedWorkspaceIndex = 2 },
                        onOpenAdvanced = { selectedWorkspaceIndex = 1 },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotTradingWorkspace(
    isLoading: Boolean,
    error: String?,
    marketStatus: MarketStatus?,
    onRefresh: () -> Unit,
    onErrorDismiss: () -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    onSelectQuote: (Quote) -> Unit,
    onOpenSymbol: (String) -> Unit,
    onOpenAdvancedWorkspace: () -> Unit,
    onOpenDerivativesWorkspace: () -> Unit,
    viewModel: TradingViewModel,
) {
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var watchSearchQuery by remember { mutableStateOf("") }
    var boardModeWatchlist by remember { mutableStateOf(true) }
    var sortModeName by rememberSaveable { mutableStateOf(WatchlistSortMode.MOVE.name) }
    val sortMode = remember(sortModeName) {
        runCatching { WatchlistSortMode.valueOf(sortModeName) }.getOrDefault(WatchlistSortMode.MOVE)
    }
    val watchlistSymbols by viewModel.watchlist.collectAsStateWithLifecycle()
    val activeWatchlistSymbols = remember(watchlistSymbols) {
        watchlistSymbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
    }
    val symbolCatalog by viewModel.symbolCatalog.collectAsStateWithLifecycle()
    val symbolCatalogLoading by viewModel.symbolCatalogLoading.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val liveQuotes by viewModel.quotes.collectAsStateWithLifecycle()
    val liveQuoteMap = remember(liveQuotes) { liveQuotes.associateBy { it.symbol.uppercase() } }
    val watchlistQuotes = remember(activeWatchlistSymbols, liveQuotes, sortMode) {
        activeWatchlistSymbols
            .mapNotNull { sym -> liveQuoteMap[sym] }
            .sortedByWatchlistMode(sortMode)
    }
    val lastQuoteUpdateAt by viewModel.lastQuoteUpdateAt.collectAsStateWithLifecycle()
    val streamHealth by viewModel.streamHealth.collectAsStateWithLifecycle()
    val statusNow by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val quoteFreshnessLabel = remember(lastQuoteUpdateAt, statusNow) {
        if (lastQuoteUpdateAt <= 0L) {
            "Syncing..."
        } else {
            val ageSec = ((statusNow - lastQuoteUpdateAt) / 1000L).coerceAtLeast(0L)
            when {
                ageSec <= 4L -> "Live • just now"
                ageSec < 60L -> "Live • ${ageSec}s ago"
                ageSec < 600L -> "Delayed • ${ageSec / 60}m ago"
                else -> "Stale • ${ageSec / 60}m ago"
            }
        }
    }
    val quoteFreshnessColor = if (lastQuoteUpdateAt <= 0L) {
        Color(0xFFFFC107)
    } else {
        val ageSec = ((statusNow - lastQuoteUpdateAt) / 1000L).coerceAtLeast(0L)
        when {
            ageSec <= 4L -> LocalAppTheme.current.positive
            ageSec < 60L -> Color(0xFF64B5F6)
            ageSec < 600L -> Color(0xFFFFC107)
            else -> LocalAppTheme.current.negative
        }
    }
    val watchlistInsights = remember(activeWatchlistSymbols, liveQuotes) {
        activeWatchlistSymbols
            .mapNotNull { symbol -> liveQuoteMap[symbol.uppercase()]?.let { computeWatchlistInsight(it) } }
            .sortedByDescending { it.confidence }
    }

    LaunchedEffect(showAddWatchlistDialog) {
        if (showAddWatchlistDialog) {
            viewModel.ensureSymbolCatalogLoaded()
        } else {
            viewModel.clearSearchResults()
        }
    }

    // Live /search while typing — catalog-only filter fails when /symbols didn't load
    // (or for names ranked better by the search API, e.g. "kaynes" → KAYNES).
    LaunchedEffect(showAddWatchlistDialog, watchSearchQuery) {
        if (!showAddWatchlistDialog) return@LaunchedEffect
        val q = watchSearchQuery.trim()
        if (q.isBlank()) {
            viewModel.clearSearchResults()
        } else {
            viewModel.searchStocks(q)
        }
    }

    val popularWatchlistPicks = remember {
        listOf(
            "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
            "SBIN", "BHARTIARTL", "ITC", "LT", "AXISBANK",
            "BAJFINANCE", "HINDUNILVR", "KOTAKBANK", "ASIANPAINT", "MARUTI",
            "WIPRO", "TITAN", "NTPC", "POWERGRID", "ULTRACEMCO",
        )
    }

    val popularFallback = remember(popularWatchlistPicks) {
        popularWatchlistPicks.map { sym ->
            StockSearchResult(symbol = sym, name = sym, matchType = "popular")
        }
    }

    val browsableStocks = remember(watchSearchQuery, symbolCatalog, searchResults, popularFallback) {
        val query = watchSearchQuery.trim()
        if (query.isBlank()) {
            if (symbolCatalog.isEmpty()) {
                // Never leave the sheet empty while /symbols loads or fails.
                popularFallback
            } else {
                val popularSet = popularWatchlistPicks.toSet()
                val popular = popularWatchlistPicks.mapNotNull { sym ->
                    symbolCatalog.firstOrNull { it.symbol.equals(sym, ignoreCase = true) }
                }
                val rest = symbolCatalog.filterNot { it.symbol.uppercase() in popularSet }
                popular + rest
            }
        } else {
            mergeWatchlistSearchResults(
                query = query,
                catalog = symbolCatalog,
                apiResults = searchResults,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Watchlist",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = if (activeWatchlistSymbols.isEmpty()) {
                        "Search any listed NSE name to start tracking"
                    } else {
                        "${activeWatchlistSymbols.size} tracked · tap a name to trade"
                    },
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
            }
            Button(
                onClick = { showAddWatchlistDialog = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text("+ Add", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = boardModeWatchlist,
                onClick = { boardModeWatchlist = true },
                label = { Text("My list") },
            )
            FilterChip(
                selected = !boardModeWatchlist,
                onClick = { boardModeWatchlist = false },
                label = { Text("Live board") },
            )
            Spacer(modifier = Modifier.weight(1f))
            StreamHealthPill(health = streamHealth)
        }

        if (boardModeWatchlist && activeWatchlistSymbols.isNotEmpty()) {
            // FlowRow keeps every sort chip visible (wrap) instead of hiding them in a LazyRow.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WatchlistSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortModeName = mode.name },
                        label = { Text(mode.label, fontSize = 11.sp) },
                    )
                }
            }
        }

        if (boardModeWatchlist && watchlistInsights.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Watchlist signals",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    watchlistInsights.take(2).forEach { insight ->
                        WatchlistInsightRow(
                            insight = insight,
                            onOpenTrade = { onSelectQuote(insight.quote) }
                        )
                    }
                }
            }
        }

        if (showAddWatchlistDialog) {
            AddToWatchlistSheet(
                query = watchSearchQuery,
                onQueryChange = { watchSearchQuery = it },
                stocks = browsableStocks,
                catalogLoading = symbolCatalogLoading,
                searching = isSearching,
                catalogSize = symbolCatalog.size,
                watchlistSymbols = activeWatchlistSymbols,
                onAdd = { symbol -> viewModel.addToWatchlist(symbol) },
                onRetryCatalog = { viewModel.ensureSymbolCatalogLoaded(force = true) },
                onDismiss = {
                    showAddWatchlistDialog = false
                    watchSearchQuery = ""
                    viewModel.clearSearchResults()
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (boardModeWatchlist) "My list (tracked quotes)" else "Live board (loaded quotes)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = if (boardModeWatchlist) {
                        "${sortMode.label} · $quoteFreshnessLabel"
                    } else {
                        "Not the full NSE catalog — use Search / + Add for any listed name · $quoteFreshnessLabel"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = quoteFreshnessColor,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenAdvancedWorkspace) { Text("Tools", fontSize = 11.sp) }
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("Refresh", fontSize = 12.sp)
                }
            }
        }

        if (marketStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (marketStatus.isOpen) Color(0xFF1B5E20).copy(alpha = 0.3f)
                    else LocalAppTheme.current.primary.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "\u2B24",
                        fontSize = 10.sp,
                        color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.primary,
                        modifier = Modifier.padding(end = 8.dp, top = 3.dp),
                    )
                    Text(
                        text = if (marketStatus.isOpen) {
                            marketStatus.message
                        } else {
                            "NSE closed · Paper trading still available"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.text,
                        modifier = Modifier.weight(1f),
                        softWrap = true,
                        maxLines = 3,
                        overflow = TextOverflow.Clip,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        if (error != null) {
            TraceAwareErrorSnackbar(
                error = error,
                onDismiss = onErrorDismiss,
                onTraceAction = onTraceSupportLookup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        if (isLoading && watchlistQuotes.isEmpty() && !boardModeWatchlist) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                repeat(6) {
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(16.dp)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(16.dp)
                                    .background(Color.Gray.copy(alpha = 0.2f)))
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .background(Color.Gray.copy(alpha = 0.15f)))
                            }
                        }
                    }
                }
            }
        } else if (boardModeWatchlist) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                enabled = true,
            ) {
                if (activeWatchlistSymbols.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Your watchlist is empty",
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add names from the full NSE catalog, then paper-trade from here.",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showAddWatchlistDialog = true }) {
                            Text("Search & add stocks")
                        }
                        TextButton(onClick = { boardModeWatchlist = false }) {
                            Text("Open live board instead")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        items(watchlistQuotes, key = { it.symbol }) { quote ->
                            val swipeDismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.removeFromWatchlist(quote.symbol)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = swipeDismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .background(
                                                LocalAppTheme.current.negative.copy(alpha = 0.85f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(end = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Remove", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                            ) {
                                TradingQuoteCard(quote) { onSelectQuote(quote) }
                            }
                        }
                        val missingQuotes = activeWatchlistSymbols.filter { liveQuoteMap[it] == null }
                        if (missingQuotes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Fetching live quotes for ${missingQuotes.joinToString()}",
                                    fontSize = 12.sp,
                                    color = LocalAppTheme.current.textSecondary,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val pagingItems = viewModel.quotesPagingFlow.collectAsLazyPagingItems()
            val watchlistSet = activeWatchlistSymbols.toSet()
            PullToRefreshBox(
                isRefreshing = isLoading || pagingItems.loadState.refresh is androidx.paging.LoadState.Loading,
                onRefresh = {
                    onRefresh()
                    pagingItems.refresh()
                },
                enabled = true
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.symbol },
                    ) { index ->
                        val quote = pagingItems[index] ?: return@items
                        val isWatchlisted = quote.symbol.uppercase() in watchlistSet
                        if (isWatchlisted) {
                            val swipeDismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.removeFromWatchlist(quote.symbol)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = swipeDismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .background(
                                                LocalAppTheme.current.negative.copy(alpha = 0.85f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(end = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Remove", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                            ) {
                                TradingQuoteCard(quote) { onSelectQuote(quote) }
                            }
                        } else {
                            TradingQuoteCard(quote) { onSelectQuote(quote) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuturesRadarScreen(
    viewModel: TradingViewModel,
    quotes: List<Quote>,
    marketStatus: MarketStatus?,
    watchlistSymbols: List<String>,
    onOpenSpotTrade: (Quote) -> Unit,
    onOpenOptions: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val futuresContracts by viewModel.futuresContracts.collectAsStateWithLifecycle()
    val futuresTicketPreview by viewModel.futuresTicketPreview.collectAsStateWithLifecycle()
    val loading by viewModel.derivativesLoading.collectAsStateWithLifecycle()

    val candidateSymbols = remember(quotes, watchlistSymbols) {
        val watchlistOrder = watchlistSymbols.map { it.uppercase() }
        val rankedQuotes = quotes
            .sortedWith(
                compareByDescending<Quote> { kotlin.math.abs(it.pctChange) }
                    .thenByDescending { it.volume ?: 0L }
            )

        val watchlistCandidates = watchlistOrder.mapNotNull { symbol ->
            rankedQuotes.firstOrNull { it.symbol.uppercase() == symbol }
        }
        (watchlistCandidates + rankedQuotes)
            .distinctBy { it.symbol.uppercase() }
            .take(6)
    }

    val defaultUnderlying = remember(candidateSymbols) {
        candidateSymbols.firstOrNull()?.symbol ?: "NIFTY"
    }
    var underlyingInput by remember(defaultUnderlying) { mutableStateOf(defaultUnderlying) }
    var lotsInput by remember { mutableStateOf("1") }
    var selectedExpiry by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(defaultUnderlying) {
        if (futuresContracts == null && defaultUnderlying.isNotBlank()) {
            underlyingInput = defaultUnderlying
            viewModel.loadFuturesContracts(defaultUnderlying)
        }
    }

    LaunchedEffect(futuresContracts?.generatedAt) {
        val firstExpiry = futuresContracts?.contracts?.firstOrNull()?.expiry
        if (!firstExpiry.isNullOrBlank() && selectedExpiry.isNullOrBlank()) {
            selectedExpiry = firstExpiry
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Futures Radar", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(
                        "Load live futures contracts for an underlying, compare expiry-level liquidity and margin, then preview lot-based ticket risk before execution.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    marketStatus?.let {
                        Text(
                            text = if (it.isOpen) "Market live: ${it.message}" else "Market status: ${it.message}",
                            color = if (it.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Futures Contract Loader", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = underlyingInput,
                            onValueChange = { underlyingInput = it.uppercase() },
                            modifier = Modifier.weight(1f),
                            label = { Text("Underlying") },
                            placeholder = { Text("INFY") },
                            colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = lotsInput,
                            onValueChange = { lotsInput = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.width(100.dp),
                            label = { Text("Lots") },
                            colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
                            singleLine = true,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.loadFuturesContracts(underlyingInput) }) {
                            Text("Load Contracts")
                        }
                        OutlinedButton(onClick = onOpenOptions) { Text("Options") }
                        OutlinedButton(onClick = onOpenAdvanced) { Text("Advanced") }
                    }
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Text("Live Contract Radar", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
        }

        val activeContracts = futuresContracts?.contracts ?: emptyList()
        if (activeContracts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No futures contracts loaded yet.", color = LocalAppTheme.current.text)
                        Text("Load an underlying (for example RELIANCE, TCS, INFY) to fetch expiry contracts and preview margins.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(activeContracts, key = { it.contractSymbol }) { contract ->
                val linkedQuote = candidateSymbols.firstOrNull { quote ->
                    quote.symbol.uppercase() == futuresContracts?.symbol?.uppercase()
                }
                val parsedLots = lotsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(contract.contractSymbol, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatCurrency(contract.last)} • ${formatSignedPct(contract.pctChange)}",
                                    color = if (contract.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                                    fontSize = 12.sp,
                                )
                            }
                            AssistChip(
                                onClick = { selectedExpiry = contract.expiry },
                                label = {
                                    Text(
                                        if (selectedExpiry == contract.expiry) "Selected ${contract.expiry}"
                                        else "Expiry ${contract.expiry}"
                                    )
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoChip(label = { Text("Lot ${contract.lotSize}") })
                            InfoChip(label = { Text("OI ${contract.oi}") })
                            InfoChip(label = { Text("Basis ${formatCurrency(contract.basis)}") })
                            InfoChip(label = { Text("Mgn/Lot ${formatCurrency(contract.marginPerLot)}") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    selectedExpiry = contract.expiry
                                    viewModel.previewFuturesTicket(
                                        symbol = futuresContracts?.symbol ?: underlyingInput,
                                        expiry = contract.expiry,
                                        side = "BUY",
                                        lots = parsedLots,
                                    )
                                }
                            ) {
                                Text("Preview Buy")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedExpiry = contract.expiry
                                    viewModel.previewFuturesTicket(
                                        symbol = futuresContracts?.symbol ?: underlyingInput,
                                        expiry = contract.expiry,
                                        side = "SELL",
                                        lots = parsedLots,
                                    )
                                }
                            ) {
                                Text("Preview Sell")
                            }
                            if (linkedQuote != null) {
                                OutlinedButton(onClick = { onOpenSpotTrade(linkedQuote) }) {
                                    Text("Open Spot")
                                }
                            }
                        }
                    }
                }
            }
        }

        futuresTicketPreview?.let { preview ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Futures Ticket Preview", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text(preview.contractSymbol, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
                        Text(
                            "${preview.side} ${preview.lots} lot(s) = ${preview.quantity} qty @ ${formatCurrency(preview.referencePrice)}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        Text("Notional: ${formatCurrency(preview.notionalValue)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Estimated Margin: ${formatCurrency(preview.estimatedMargin)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Estimated Charges: ${formatCurrency(preview.estimatedCharges)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Max Loss Buffer: ${formatCurrency(preview.maxLossBuffer)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenAdvanced) { Text("Route To Advanced") }
                            OutlinedButton(onClick = onOpenOptions) { Text("Open Options") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TradingQuoteCard(
    quote: Quote,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        colors = byselCardColors(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.text,
                    )
                    TickPriceText(
                        price = quote.last,
                        text = "₹${String.format("%.2f", quote.last)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = null,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = animatedChangeColor(quote.pctChange),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.End) {
                        Text(text = "O: ₹${String.format("%.2f", quote.open ?: quote.prevClose ?: quote.last)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "H/L: ₹${String.format("%.2f", quote.dayHigh ?: quote.last)}/${String.format("%.2f", quote.dayLow ?: quote.last)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Vol: ${formatVolume(quote.volume)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TradeActionButton(
                    onClick = onClick,
                    isBuy = true,
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                ) {
                    Text("Buy", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                TradeActionButton(
                    onClick = onClick,
                    isBuy = false,
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                ) {
                    Text("Sell", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WatchlistInsightRow(
    insight: WatchlistInsight,
    onOpenTrade: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.quote.symbol,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "Conf ${insight.confidence}",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.momentum,
                    fontSize = 12.sp,
                    color = when (insight.momentum) {
                        "Bullish" -> LocalAppTheme.current.positive
                        "Bearish" -> LocalAppTheme.current.negative
                        else -> LocalAppTheme.current.textSecondary
                    }
                )
                Text(
                    text = "Risk ${insight.risk}",
                    fontSize = 12.sp,
                    color = when (insight.risk) {
                        "High" -> LocalAppTheme.current.negative
                        "Medium" -> Color(0xFFFFB300)
                        else -> LocalAppTheme.current.positive
                    }
                )
                Text(
                    text = formatSignedPct(insight.quote.pctChange),
                    fontSize = 12.sp,
                    color = if (insight.quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }

            Text(
                text = insight.rationale,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.flags.take(2).joinToString(" • "),
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary
                )
                TextButton(onClick = onOpenTrade) {
                    Text("Trade")
                }
            }
        }
    }
}

/** Merge API /search hits (ranked) with local catalog substring matches. */
private fun mergeWatchlistSearchResults(
    query: String,
    catalog: List<StockSearchResult>,
    apiResults: List<StockSearchResult>,
): List<StockSearchResult> {
    val fromCatalog = catalog.filter { stock ->
        stock.symbol.contains(query, ignoreCase = true) ||
            stock.name.contains(query, ignoreCase = true)
    }
    val seen = LinkedHashSet<String>()
    val merged = ArrayList<StockSearchResult>(apiResults.size + fromCatalog.size)
    for (stock in apiResults + fromCatalog) {
        val key = stock.symbol.trim().uppercase()
        if (key.isBlank() || !seen.add(key)) continue
        merged.add(stock)
    }
    return merged
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToWatchlistSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    stocks: List<StockSearchResult>,
    catalogLoading: Boolean,
    searching: Boolean,
    catalogSize: Int,
    watchlistSymbols: List<String>,
    onAdd: (String) -> Unit,
    onRetryCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme = LocalAppTheme.current
    val queryBlank = query.isBlank()
    val showSearchLoading = searching && stocks.isEmpty() && !queryBlank

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Add to Watchlist",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = when {
                    catalogSize > 0 ->
                        "Browse $catalogSize listed stocks · type a company name or symbol"
                    catalogLoading ->
                        "Search by name anytime · full list loading in background"
                    else ->
                        "Type a company name or symbol to search (e.g. Kaynes, RELIANCE)"
                },
                fontSize = 12.sp,
                color = theme.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            if (catalogLoading && catalogSize == 0) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    color = theme.primary,
                    trackColor = theme.textSecondary.copy(alpha = 0.2f),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Try Kaynes, Reliance, Infosys…", color = theme.textSecondary)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = theme.textSecondary)
                },
                trailingIcon = {
                    when {
                        searching -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp),
                                strokeWidth = 2.dp,
                                color = theme.primary,
                            )
                        }
                        query.isNotEmpty() -> {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = theme.textSecondary)
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
                colors = appOutlinedTextFieldColors(containerColor = theme.surface),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                showSearchLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = theme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Searching…",
                            color = theme.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                stocks.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (queryBlank) {
                                "Stock list unavailable. Retry catalog load, or type a name to search."
                            } else {
                                "No matches for \"$query\". Try the company name (e.g. Kaynes Technology)."
                            },
                            fontSize = 13.sp,
                            color = theme.textSecondary,
                        )
                        if (queryBlank || catalogSize == 0) {
                            TextButton(onClick = onRetryCatalog) {
                                Text("Retry stock list")
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = if (queryBlank) {
                            "Popular names first · scroll for the full list (${stocks.size})"
                        } else {
                            "${stocks.size} match${if (stocks.size == 1) "" else "es"}" +
                                if (searching) " · updating…" else ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    // Box+fillMaxSize avoids ModalBottomSheet weight/measure quirks that
                    // can clip the list to a few rows (or none) on some devices.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 28.dp),
                        ) {
                            items(stocks, key = { it.symbol }) { result ->
                                val already = watchlistSymbols.any {
                                    it.equals(result.symbol, ignoreCase = true)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !already) { onAdd(result.symbol) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        MarqueeText(
                                            text = result.name.ifBlank { result.symbol },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = theme.text,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = result.symbol,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = theme.textSecondary,
                                        )
                                    }
                                    Text(
                                        text = if (already) "Added" else "Add",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (already) theme.positive else theme.primary,
                                        modifier = Modifier.padding(start = 12.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeBottomSheet(
    quote: Quote,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    viewModel: TradingViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalAppTheme.current.card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        LocalAppTheme.current.textSecondary.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }
    ) {
        TradeBottomSheetContent(
            quote = quote,
            walletBalance = walletBalance,
            marketStatus = marketStatus,
            onDismiss = onDismiss,
            onBuy = { qty -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBuy(qty) },
            onSell = { qty -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSell(qty) },
            onTraceSupportLookup = onTraceSupportLookup,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun TradeBottomSheetContent(
    quote: Quote,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    viewModel: TradingViewModel,
) {
    var quantity by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var limitPriceInput by remember { mutableStateOf(String.format("%.2f", quote.last)) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val history by viewModel.quoteHistory.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsStateWithLifecycle()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsStateWithLifecycle()
    val lastExecutedOrder by viewModel.lastExecutedOrder.collectAsStateWithLifecycle()
    val postTradeReview by viewModel.copilotPostTradeReview.collectAsStateWithLifecycle()
    val productActionMessage by viewModel.productActionMessage.collectAsStateWithLifecycle()
    val copilotPortfolioActions by viewModel.copilotPortfolioActions.collectAsStateWithLifecycle()
    val lastOrderTraceId by viewModel.lastOrderTraceId.collectAsStateWithLifecycle()
    val orderExecutionLoading by viewModel.orderExecutionLoading.collectAsStateWithLifecycle()
    val copilotLoading by viewModel.copilotLoading.collectAsStateWithLifecycle()
    val lastError by viewModel.error.collectAsStateWithLifecycle()

    val qty = quantity.toIntOrNull() ?: 0
    val limitPrice = limitPriceInput.toDoubleOrNull() ?: 0.0
    val localExecutionPrice = if (orderType == "LIMIT" && limitPrice > 0.0) limitPrice else quote.last
    val localTradeValue = qty * localExecutionPrice
    val localBrokerage = localTradeValue * 0.0003
    val localExchangeFee = localTradeValue * 0.00034
    val localGst = (localBrokerage + localExchangeFee) * 0.18
    val localStampDuty = if (tradeType == "BUY") localTradeValue * 0.00015 else 0.0
    val localTotalCharges = localBrokerage + localExchangeFee + localGst + localStampDuty
    val localNetAmount = if (tradeType == "BUY") localTradeValue + localTotalCharges else localTradeValue - localTotalCharges
    val localCanAfford = walletBalance >= localNetAmount
    val isMarketOpen = marketStatus?.isOpen ?: false
    val intradayRangePct = if (quote.dayHigh != null && quote.dayLow != null && quote.last > 0.0) {
        ((quote.dayHigh - quote.dayLow) / quote.last) * 100.0
    } else 0.0
    val spreadPct = if (quote.bid != null && quote.ask != null && quote.last > 0.0) {
        ((quote.ask - quote.bid) / quote.last) * 100.0
    } else 0.0
    val priceGapPct = if (quote.last > 0.0) ((localExecutionPrice - quote.last) / quote.last) * 100.0 else 0.0
    val limitInvalid = orderType == "LIMIT" && limitPrice <= 0.0
    val limitDeviationWarning = orderType == "LIMIT" && abs(priceGapPct) > 3.0
    val limitDeviationHardBlock = orderType == "LIMIT" && abs(priceGapPct) > 8.0
    val localWalletUtilizationPct = if (tradeType == "BUY" && walletBalance > 0.0) {
        ((localNetAmount / walletBalance) * 100.0).coerceAtLeast(0.0)
    } else 0.0
    val localImpactTag = when {
        localTradeValue >= 300_000 || qty >= 350 -> "High impact"
        localTradeValue >= 120_000 || qty >= 150 -> "Medium impact"
        else -> "Low impact"
    }
    val executionPrice = preTradeEstimate?.executionPrice ?: localExecutionPrice
    val tradeValue = preTradeEstimate?.tradeValue ?: localTradeValue
    val totalCharges = preTradeEstimate?.charges?.totalCharges ?: localTotalCharges
    val netAmount = preTradeEstimate?.netAmount ?: localNetAmount
    val canAfford = preTradeEstimate?.canAfford ?: localCanAfford
    val walletUtilizationPct = preTradeEstimate?.walletUtilizationPct ?: localWalletUtilizationPct
    val impactTag = preTradeEstimate?.impactTag ?: localImpactTag
    val effectiveSignal = preTradeEstimate?.signal ?: preTradeSignal
    val estimateWarnings = preTradeEstimate?.warnings ?: emptyList()
    val copilotBlocksTrade = effectiveSignal?.verdict?.equals("BLOCK", ignoreCase = true) == true
    val currentHolding = holdings.firstOrNull { it.symbol.equals(quote.symbol, ignoreCase = true) }
    val currentHoldingPnl = currentHolding?.let { (quote.last - it.avgPrice) * it.qty }
    val lastExecutedForSymbol = lastExecutedOrder?.takeIf {
        it.order.symbol.equals(quote.symbol, ignoreCase = true)
    }

    // Detect if the last error has a structured rejection resolution for this symbol's context
    val rejectionBannerError = remember(lastError, qty) {
        if (qty > 0 && lastError != null && resolveRejection(lastError) != null) lastError else null
    }

    DisposableEffect(quote.symbol) {
        onDispose { viewModel.clearPreTradeCopilotSignal() }
    }

    LaunchedEffect(quote.symbol, qty, tradeType, orderType, limitPriceInput, isMarketOpen) {
        if (qty <= 0 || limitInvalid || limitDeviationHardBlock) {
            viewModel.clearPreTradeCopilotSignal()
            return@LaunchedEffect
        }
        delay(250)
        viewModel.fetchPreTradeEstimate(
            com.bysel.trader.data.models.AdvancedOrderRequest(
                symbol = quote.symbol,
                qty = qty,
                side = tradeType,
                orderType = orderType,
                validity = "DAY",
                limitPrice = if (orderType == "LIMIT") limitPrice else null,
                triggerPrice = null,
            )
        )
    }

    LaunchedEffect(quote.symbol) {
        if (copilotPortfolioActions == null) {
            viewModel.loadPortfolioCopilotActions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = quote.symbol,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TickPriceText(
                        price = quote.last,
                        text = "₹${String.format("%.2f", quote.last)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppTheme.current.text,
                    )
                    Text(
                        text = "${if (quote.pctChange >= 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = animatedChangeColor(quote.pctChange),
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = LocalAppTheme.current.textSecondary)
            }
        }

        // After-hours paper trading is allowed — fill uses last session quote.
        if (!isMarketOpen) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "NSE closed · Paper fills use last session price." +
                        (marketStatus?.message?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    softWrap = true,
                    maxLines = 4,
                    overflow = TextOverflow.Clip,
                    lineHeight = 16.sp,
                )
            }
        }

        // Wallet balance chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = LocalAppTheme.current.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wallet", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
            }
            AnimatedAmountText(
                amount = walletBalance,
                formatter = { formatCurrency(it) },
                style = MaterialTheme.typography.labelLarge,
                color = LocalAppTheme.current.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Price info
        Text(
            text = "Day Range: ₹${String.format("%.2f", quote.dayLow ?: quote.last)} – ₹${String.format("%.2f", quote.dayHigh ?: quote.last)}",
            fontSize = 12.sp,
            color = LocalAppTheme.current.textSecondary,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Chart-Linked Context",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (history.isNotEmpty()) {
                    PriceHistoryChart(
                        history = history.takeLast(30),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                } else {
                    Text(
                        text = "Loading recent price structure for ${quote.symbol}...",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TradeSummaryLine(
                    label = "Intraday move",
                    value = formatSignedPct(quote.pctChange),
                    valueColor = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
                TradeSummaryLine(
                    label = "Spread",
                    value = "${String.format("%.2f", spreadPct)}%"
                )
                TradeSummaryLine(
                    label = "Range",
                    value = "${String.format("%.1f", intradayRangePct)}%"
                )
                currentHolding?.let { holding ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = LocalAppTheme.current.textSecondary.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "Position Snapshot",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TradeSummaryLine(label = "Holding", value = "${holding.qty} qty")
                    TradeSummaryLine(label = "Average", value = formatCurrency(holding.avgPrice))
                    TradeSummaryLine(
                        label = "Live PnL",
                        value = formatCurrency(currentHoldingPnl ?: 0.0),
                        valueColor = if ((currentHoldingPnl ?: 0.0) >= 0.0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                }
            }
        }

        // Buy / Sell toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { tradeType = "BUY" },
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "BUY") LocalAppTheme.current.positive else LocalAppTheme.current.mutedSurface,
                    contentColor = if (tradeType == "BUY") Color.White else LocalAppTheme.current.text
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Buy", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { tradeType = "SELL" },
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "SELL") LocalAppTheme.current.negative else LocalAppTheme.current.mutedSurface,
                    contentColor = if (tradeType == "SELL") Color.White else LocalAppTheme.current.text
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Sell", fontWeight = FontWeight.Bold) }
        }

        // Order type
        Text("Order Type", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("MARKET", "LIMIT").forEach { otype ->
                AssistChip(
                    onClick = { orderType = otype },
                    label = { Text(otype.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (orderType == otype) LocalAppTheme.current.primary.copy(alpha = 0.25f) else LocalAppTheme.current.mutedSurface,
                        labelColor = LocalAppTheme.current.text
                    )
                )
            }
        }

        if (orderType == "LIMIT") {
            OutlinedTextField(
                value = limitPriceInput,
                onValueChange = { limitPriceInput = filterDecimalInput(it) },
                label = { Text("Limit Price", color = LocalAppTheme.current.textSecondary) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
            )
        }

        // Quantity
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = filterDigitsOnly(it) },
            label = { Text("Quantity", color = LocalAppTheme.current.textSecondary) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 5, 10, 25).forEach { quickQty ->
                AssistChip(
                    onClick = { quantity = quickQty.toString() },
                    label = { Text("$quickQty") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = LocalAppTheme.current.mutedSurface,
                        labelColor = LocalAppTheme.current.text
                    )
                )
            }
        }

        if (qty > 0) {
            // Charges breakdown card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Order Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalAppTheme.current.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    TradeSummaryLine("Execution", "₹${String.format("%.2f", executionPrice)}")
                    TradeSummaryLine("Trade Value", formatCurrency(tradeValue))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = LocalAppTheme.current.textSecondary.copy(alpha = 0.15f))
                    // Charges breakdown
                    Text("Charges Breakdown", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    preTradeEstimate?.let { est ->
                        TradeSummaryLine("Brokerage", formatCurrency(est.charges.brokerage))
                        TradeSummaryLine("Exchange Fee", formatCurrency(est.charges.exchangeFee))
                        TradeSummaryLine("GST (18%)", formatCurrency(est.charges.gst))
                        if (tradeType == "BUY") TradeSummaryLine("Stamp Duty", formatCurrency(est.charges.stampDuty))
                    } ?: run {
                        TradeSummaryLine("Brokerage (~0.03%)", formatCurrency(localBrokerage))
                        TradeSummaryLine("Exchange Fee (~0.034%)", formatCurrency(localExchangeFee))
                        TradeSummaryLine("GST (18%)", formatCurrency(localGst))
                        if (tradeType == "BUY") TradeSummaryLine("Stamp Duty (~0.015%)", formatCurrency(localStampDuty))
                    }
                    TradeSummaryLine("Total Charges", formatCurrency(totalCharges))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = LocalAppTheme.current.textSecondary.copy(alpha = 0.15f))
                    TradeSummaryLine(
                        if (tradeType == "BUY") "Total Debit" else "Total Credit",
                        formatCurrency(netAmount),
                        valueColor = if (tradeType == "BUY") LocalAppTheme.current.negative else LocalAppTheme.current.positive
                    )
                    if (tradeType == "BUY") {
                        // Wallet utilization progress bar
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Wallet Used", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                            Text("${String.format("%.1f", walletUtilizationPct)}%", fontSize = 11.sp, color = when {
                                walletUtilizationPct >= 90 -> LocalAppTheme.current.negative
                                walletUtilizationPct >= 60 -> Color(0xFFFF8F00)
                                else -> LocalAppTheme.current.positive
                            })
                        }
                        LinearProgressIndicator(
                            progress = { (walletUtilizationPct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
                            color = when {
                                walletUtilizationPct >= 90 -> LocalAppTheme.current.negative
                                walletUtilizationPct >= 60 -> Color(0xFFFF8F00)
                                else -> LocalAppTheme.current.positive
                            },
                            trackColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.15f),
                        )
                    }
                    TradeSummaryLine("Impact", impactTag)
                }
            }

            // Copilot check card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Copilot Pre-Trade Check", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalAppTheme.current.text)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (copilotLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = LocalAppTheme.current.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing order risk...", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                        }
                    } else {
                        effectiveSignal?.let { signal ->
                            val verdictColor = when (signal.verdict.uppercase()) {
                                "GO" -> LocalAppTheme.current.positive
                                "CAUTION" -> Color(0xFFFFC107)
                                "BLOCK" -> LocalAppTheme.current.negative
                                else -> LocalAppTheme.current.textSecondary
                            }
                            TradeSummaryLine("Verdict", "${signal.verdict} (${signal.confidence}%)", valueColor = verdictColor)
                            signal.flags.take(2).forEach { flag ->
                                Text("• $flag", fontSize = 11.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 2.dp))
                            }
                            signal.guidance.take(2).forEach { tip ->
                                Text("• $tip", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                        } ?: Text("Waiting for order inputs...", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                    }
                }
            }

            TicketTrustToolsCard(
                portfolioActions = copilotPortfolioActions,
                lastOrderTraceId = lastOrderTraceId,
                hasEstimate = preTradeEstimate != null,
                onRefreshGuidance = {
                    viewModel.loadPortfolioCopilotActions()
                    if (qty > 0 && !limitInvalid && !limitDeviationHardBlock) {
                        viewModel.fetchPreTradeEstimate(
                            com.bysel.trader.data.models.AdvancedOrderRequest(
                                symbol = quote.symbol,
                                qty = qty,
                                side = tradeType,
                                orderType = orderType,
                                validity = "DAY",
                                limitPrice = if (orderType == "LIMIT") limitPrice else null,
                                triggerPrice = null,
                            )
                        )
                    }
                },
                onTraceSupportLookup = onTraceSupportLookup,
            )

            // Rejection banner if last error applies
            rejectionBannerError?.let { errMsg ->
                OrderRejectionBanner(
                    rawMessage = errMsg,
                    onPrimaryCta = { viewModel.clearError() },
                    modifier = Modifier.padding(bottom = 10.dp),
                    onSecondaryCta = { resolution ->
                        when (resolution.category) {
                            RejectionCategory.FUNDS, RejectionCategory.QUANTITY ->
                                quantity = ((qty / 2).coerceAtLeast(1)).toString()
                            RejectionCategory.PRICE -> orderType = "MARKET"
                            else -> Unit
                        }
                        viewModel.clearError()
                    }
                )
            }

            // Warnings
            Text(
                "Impact cues: range ${String.format("%.1f", intradayRangePct)}% • spread ${String.format("%.2f", spreadPct)}%",
                fontSize = 11.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(bottom = 4.dp)
            )
            estimateWarnings.take(2).forEach { warning ->
                Text("⚠ $warning", fontSize = 12.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 4.dp))
            }
            if (estimateWarnings.isEmpty() && tradeType == "BUY" && !canAfford) {
                Text(
                    "⚠ Insufficient funds. Need ${formatCurrency((netAmount - walletBalance).coerceAtLeast(0.0))} more.",
                    fontSize = 12.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (estimateWarnings.isEmpty() && limitDeviationWarning) {
                Text("⚠ Limit is ${String.format("%.2f", abs(priceGapPct))}% away from market price.", fontSize = 12.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 4.dp))
            }
            if (estimateWarnings.isEmpty() && spreadPct >= 0.45) {
                Text("⚠ Wide spread detected. Consider limit order.", fontSize = 12.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 4.dp))
            }
            Text("Estimates are indicative; final fill and charges may vary.", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            if (copilotBlocksTrade) {
                Text("Copilot has blocked this order. Adjust inputs and retry.", fontSize = 12.sp, color = LocalAppTheme.current.negative, modifier = Modifier.padding(top = 4.dp))
            }
        }

        AnimatedVisibility(
            visible = lastExecutedForSymbol != null,
            enter = expandVertically() + fadeIn(tween(220)),
            exit = shrinkVertically() + fadeOut(tween(160)),
        ) {
            val executed = lastExecutedForSymbol ?: return@AnimatedVisibility
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Execution Feedback",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    productActionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    TradeSummaryLine(
                        label = "Last order",
                        value = "${executed.order.side} ${executed.order.qty} • ${executed.orderStatus ?: executed.status.uppercase()}"
                    )
                    executed.executedPrice?.let { executedPrice ->
                        TradeSummaryLine(label = "Executed", value = formatCurrency(executedPrice))
                    }
                    executed.total?.let { total ->
                        TradeSummaryLine(label = "Notional", value = formatCurrency(total))
                    }
                    lastOrderTraceId?.takeIf { it.isNotBlank() }?.let { traceId ->
                        TradeSummaryLine(label = "Trace", value = traceId)
                        onTraceSupportLookup?.let { lookup ->
                            OutlinedButton(
                                onClick = { lookup(traceId) },
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text("Open Trace Support")
                            }
                        }
                    }
                    postTradeReview?.let { review ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = LocalAppTheme.current.textSecondary.copy(alpha = 0.15f)
                        )
                        Text(
                            text = review.summary,
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.text,
                        )
                        TradeSummaryLine(
                            label = "P&L now",
                            value = formatCurrency(review.pnlNow),
                            valueColor = if (review.pnlNow >= 0.0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                        )
                        review.coaching.take(2).forEach { tip ->
                            Text(
                                text = "• $tip",
                                fontSize = 11.sp,
                                color = LocalAppTheme.current.textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (limitInvalid) {
            Text("Enter a valid limit price to continue.", fontSize = 12.sp, color = LocalAppTheme.current.negative)
        }

        // Confirm / Cancel
        Spacer(modifier = Modifier.height(16.dp))
        if (orderExecutionLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalAppTheme.current.primary
                )
                Text(
                    text = "Submitting order and refreshing execution context...",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Close") }
            TradeActionButton(
                onClick = {
                    if (qty > 0) {
                        showConfirmDialog = true
                    }
                },
                isBuy = tradeType == "BUY",
                enabled = qty > 0 && !limitInvalid && !limitDeviationHardBlock && !copilotBlocksTrade && !orderExecutionLoading && (tradeType == "SELL" || canAfford),
                modifier = Modifier.weight(2f),
                height = 48.dp,
            ) {
                if (orderExecutionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        if (orderType == "MARKET") tradeType else "LIMIT $tradeType",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    // Order Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    "Confirm $tradeType Order",
                    fontWeight = FontWeight.Bold,
                    color = if (tradeType == "BUY") LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${quote.symbol} • $qty share${if (qty == 1) "" else "s"}")
                    Text("Order type: ${if (orderType == "MARKET") "Market" else "Limit @ ₹${String.format("%.2f", limitPrice)}"}")
                    Text("Est. value: ₹${String.format("%,.2f", tradeValue)}")
                    Text("Est. charges: ₹${String.format("%.2f", totalCharges)}")
                    Text(
                        "Net amount: ₹${String.format("%,.2f", netAmount)}",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TradeActionButton(
                    onClick = {
                        showConfirmDialog = false
                        if (tradeType == "BUY") onBuy(qty) else onSell(qty)
                    },
                    isBuy = tradeType == "BUY",
                    height = 40.dp,
                ) {
                    Text("Confirm $tradeType", fontWeight = FontWeight.Bold)
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
private fun TicketTrustToolsCard(
    portfolioActions: com.bysel.trader.data.models.CopilotPortfolioActionsResponse?,
    lastOrderTraceId: String?,
    hasEstimate: Boolean,
    onRefreshGuidance: () -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Trust & Support",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                )
                InfoChip(
                    label = { Text(if (hasEstimate) "Estimate synced" else "Live quote synced") },
                )
            }

            portfolioActions?.let { actions ->
                Text(
                    text = "${actions.priority}: ${actions.rationale}",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
                actions.actions.take(2).forEach { action ->
                    Text(
                        text = "• $action",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.text,
                    )
                }
            } ?: Text(
                text = "Copilot portfolio guidance will appear here as account context refreshes.",
                fontSize = 11.sp,
                color = LocalAppTheme.current.textSecondary,
            )

            lastOrderTraceId?.takeIf { it.isNotBlank() }?.let { traceId ->
                Text(
                    text = "Latest support trace: $traceId",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshGuidance) {
                    Text("Refresh Guidance")
                }
                if (onTraceSupportLookup != null && !lastOrderTraceId.isNullOrBlank()) {
                    FilledTonalButton(onClick = { onTraceSupportLookup(lastOrderTraceId) }) {
                        Text("Open Trace")
                    }
                }
            }
        }
    }
}

// Keep legacy TradeDialog as a bridge for any external callers
@Composable
fun TradeDialog(
    quote: Quote,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit,
    viewModel: TradingViewModel,
) = TradeBottomSheet(
    quote = quote,
    walletBalance = walletBalance,
    marketStatus = marketStatus,
    onDismiss = onDismiss,
    onBuy = onBuy,
    onSell = onSell,
    onTraceSupportLookup = null,
    viewModel = viewModel,
)

@Composable
private fun TradeSummaryLine(
    label: String,
    value: String,
    valueColor: Color = LocalAppTheme.current.text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
        Text(value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StreamHealthPill(health: TradingViewModel.StreamHealth) {
    val infiniteTransition = rememberInfiniteTransition(label = "streamPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    val dotColor = when (health) {
        TradingViewModel.StreamHealth.LIVE -> Color(0xFF00C853)
        TradingViewModel.StreamHealth.RECONNECTING -> Color(0xFFFF8F00)
        TradingViewModel.StreamHealth.OFFLINE -> Color(0xFF757575)
    }
    val label = when (health) {
        TradingViewModel.StreamHealth.LIVE -> "Live"
        TradingViewModel.StreamHealth.RECONNECTING -> "Reconnecting"
        TradingViewModel.StreamHealth.OFFLINE -> "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color = dotColor.copy(alpha = if (health == TradingViewModel.StreamHealth.LIVE) alpha else 1f),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = dotColor)
    }
}

@Composable
fun AddFundsDialog(
    onDismiss: () -> Unit,
    onAddPracticeCredit: (Double) -> Unit,
    onAddViaUpi: (Double, String) -> Unit = { _, _ -> },
) {
    var amount by remember { mutableStateOf("") }
    val presetAmounts = listOf(10000.0, 25000.0, 50000.0, 100000.0)
    val parsedAmount = amount.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text("Add practice credit", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "BYSEL uses paper money only. Credit your simulation wallet instantly — no UPI or real payment.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = filterDecimalInput(it, maxFractionDigits = 0) },
                    label = { Text("Amount (\u20B9)", color = LocalAppTheme.current.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
                )

                Text("Quick Add", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { preset ->
                        Button(
                            onClick = { amount = preset.toInt().toString() },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalAppTheme.current.mutedSurface,
                                contentColor = LocalAppTheme.current.text
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("\u20B9${preset.toInt()/1000}K", fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmount > 0) onAddPracticeCredit(parsedAmount)
                },
                enabled = parsedAmount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text("Add practice credit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppTheme.current.primary)
            }
        }
    )
}
