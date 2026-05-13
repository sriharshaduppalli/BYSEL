package com.bysel.trader.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.MarketStatus
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import com.bysel.trader.ui.components.OrderRejectionBanner
import com.bysel.trader.ui.components.resolveRejection
import com.bysel.trader.viewmodel.TradingViewModel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
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

private fun formatSignedPct(value: Double): String {
    return "${if (value >= 0) "+" else ""}${String.format("%.2f", value)}%"
}

private fun formatCurrency(value: Double): String {
    return "₹${String.format("%,.2f", value)}"
}

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

private data class WatchlistSubTab(
    val title: String,
    val symbols: List<String>,
)

private fun watchlistLeadChar(symbol: String): Char {
    val base = symbol.substringBefore(".").trim().uppercase()
    return base.firstOrNull { it.isLetterOrDigit() } ?: '#'
}

private fun buildWatchlistSubTabs(symbols: List<String>): List<WatchlistSubTab> {
    val cleaned = symbols
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    fun inRange(symbol: String, start: Char, end: Char): Boolean {
        val lead = watchlistLeadChar(symbol)
        if (lead.isDigit()) {
            return start == 'A'
        }
        return lead in start..end
    }

    return listOf(
        WatchlistSubTab("All", cleaned),
        WatchlistSubTab("A-E", cleaned.filter { inRange(it, 'A', 'E') }),
        WatchlistSubTab("F-J", cleaned.filter { inRange(it, 'F', 'J') }),
        WatchlistSubTab("K-O", cleaned.filter { inRange(it, 'K', 'O') }),
        WatchlistSubTab("P-T", cleaned.filter { inRange(it, 'P', 'T') }),
        WatchlistSubTab("U-Z", cleaned.filter { inRange(it, 'U', 'Z') }),
    )
}

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
    onErrorDismiss: () -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    viewModel: com.bysel.trader.viewmodel.TradingViewModel
) {
    // Start fast-refresh while this screen is visible; stop on dispose
    DisposableEffect(viewModel) {
        viewModel.startFastRefresh()
        onDispose { viewModel.stopFastRefresh() }
    }
    val liveQuotes by viewModel.quotes.collectAsState()
    val watchlistSymbols by viewModel.watchlist.collectAsState()
    val selectedQuote by viewModel.selectedQuote.collectAsState()
    // AI Trade Coach Dialog
    val tradeCoachTip by viewModel.tradeCoachTip.collectAsState()
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
            onAdd = { amount, upiProvider ->
                onAddFunds(amount, upiProvider)
                showAddFundsDialog = false
            }
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
            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Trade Hub",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = "Spot, advanced orders, options, and futures tools in one workspace.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
            }
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
            when (selectedWorkspaceIndex) {
                0 -> SpotTradingWorkspace(
                    isLoading = isLoading,
                    error = error,
                    walletBalance = walletBalance,
                    marketStatus = marketStatus,
                    onRefresh = onRefresh,
                    onShowAddFunds = { showAddFundsDialog = true },
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

@Composable
private fun SpotTradingWorkspace(
    isLoading: Boolean,
    error: String?,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onRefresh: () -> Unit,
    onShowAddFunds: () -> Unit,
    onErrorDismiss: () -> Unit,
    onTraceSupportLookup: ((String) -> Unit)? = null,
    onSelectQuote: (Quote) -> Unit,
    onOpenSymbol: (String) -> Unit,
    onOpenAdvancedWorkspace: () -> Unit,
    onOpenDerivativesWorkspace: () -> Unit,
    viewModel: TradingViewModel,
) {
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var newWatchSymbol by remember { mutableStateOf("") }
    var selectedWatchlistSubTab by remember { mutableIntStateOf(0) }
    val watchlistSymbols by viewModel.watchlist.collectAsState()
    val watchlistSubTabs = remember(watchlistSymbols) { buildWatchlistSubTabs(watchlistSymbols) }
    val activeWatchlistSymbols = watchlistSubTabs.getOrElse(selectedWatchlistSubTab) { watchlistSubTabs.first() }.symbols
    val liveQuotes by viewModel.quotes.collectAsState()
    val liveQuoteMap = remember(liveQuotes) { liveQuotes.associateBy { it.symbol.uppercase() } }
    val lastQuoteUpdateAt by viewModel.lastQuoteUpdateAt.collectAsState()
    val streamHealth by viewModel.streamHealth.collectAsState()
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
    LaunchedEffect(watchlistSubTabs.size) {
        if (selectedWatchlistSubTab >= watchlistSubTabs.size) {
            selectedWatchlistSubTab = 0
        }
    }

    val watchlistInsights = remember(activeWatchlistSymbols, liveQuotes) {
        activeWatchlistSymbols
            .mapNotNull { symbol -> liveQuoteMap[symbol.uppercase()]?.let { computeWatchlistInsight(it) } }
            .sortedByDescending { it.confidence }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Watchlists",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = "Browse tracked symbols in grouped sub-tabs",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
            }
            Button(onClick = { showAddWatchlistDialog = true }, modifier = Modifier.height(36.dp)) { Text("+ Watchlist") }
        }

        if (watchlistSymbols.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedWatchlistSubTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 12.dp,
                containerColor = LocalAppTheme.current.surface,
                contentColor = LocalAppTheme.current.text,
                divider = {}
            ) {
                watchlistSubTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedWatchlistSubTab == index,
                        onClick = { selectedWatchlistSubTab = index },
                        text = {
                            Text(
                                text = "${tab.title} (${tab.symbols.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(activeWatchlistSymbols) { symbol ->
                    AssistChip(
                        onClick = {
                            liveQuoteMap[symbol.uppercase()]?.let(onSelectQuote) ?: onOpenSymbol(symbol)
                        },
                        label = { Text(symbol) },
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "Wallet Balance",
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                        Text(
                            text = "₹${String.format("%,.2f", walletBalance)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text
                        )
                    }
                }
                Button(
                    onClick = onShowAddFunds,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("+ Add Funds", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (activeWatchlistSymbols.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watchlist Intelligence",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalAppTheme.current.text
                        )
                        Text(
                            text = "${watchlistInsights.size}/${activeWatchlistSymbols.size} live",
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (watchlistInsights.isEmpty()) {
                        Text(
                            text = "Pull to refresh to compute watchlist signals.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    } else {
                        watchlistInsights.take(3).forEach { insight ->
                            WatchlistInsightRow(
                                insight = insight,
                                onOpenTrade = { onSelectQuote(insight.quote) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddWatchlistDialog) {
            AlertDialog(onDismissRequest = { showAddWatchlistDialog = false }, confirmButton = {
                TextButton(onClick = {
                    if (newWatchSymbol.isNotBlank()) {
                        viewModel.addToWatchlist(newWatchSymbol)
                        newWatchSymbol = ""
                    }
                    showAddWatchlistDialog = false
                }) { Text("Add") }
            }, title = { Text("Add to Watchlist") }, text = {
                OutlinedTextField(
                    value = newWatchSymbol,
                    onValueChange = { newWatchSymbol = it },
                    label = { Text("Symbol (e.g., INFY, NSE:INFY, 500325.BO)") },
                    colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
                )
            }, dismissButton = { TextButton(onClick = { showAddWatchlistDialog = false }) { Text("Cancel") } })
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Decision Tools",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = "Use charges, margin, and options-risk context before placing market or limit orders.",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenAdvancedWorkspace) {
                        Text("Charges & Margin")
                    }
                    OutlinedButton(onClick = onOpenDerivativesWorkspace) {
                        Text("Options Risk")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Spot Market",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    StreamHealthPill(health = streamHealth)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = quoteFreshnessLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = quoteFreshnessColor,
                    )
                }
            }
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Refresh", fontSize = 12.sp)
            }
        }

        if (marketStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (marketStatus.isOpen) Color(0xFF1B5E20).copy(alpha = 0.3f)
                    else Color(0xFFB71C1C).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (marketStatus.isOpen) "\u2B24" else "\u2B24",
                            fontSize = 10.sp,
                            color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = marketStatus.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                        )
                    }
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

        if (isLoading) {
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
        } else {
            val paged by viewModel.pagedQuotes.collectAsState()
            val watchlistSet by viewModel.watchlist.collectAsState()
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                enabled = true
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(paged, key = { _, q -> q.symbol }) { index: Int, quote: Quote ->
                        val isWatchlisted = quote.symbol.uppercase() in watchlistSet.map { it.uppercase() }
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
                        if (index >= paged.size - 5) {
                            LaunchedEffect(index) { viewModel.loadNextQuotesPage() }
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
    val futuresContracts by viewModel.futuresContracts.collectAsState()
    val futuresTicketPreview by viewModel.futuresTicketPreview.collectAsState()
    val loading by viewModel.derivativesLoading.collectAsState()

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
                            AssistChip(onClick = {}, label = { Text("Lot ${contract.lotSize}") })
                            AssistChip(onClick = {}, label = { Text("OI ${contract.oi}") })
                            AssistChip(onClick = {}, label = { Text("Basis ${formatCurrency(contract.basis)}") })
                            AssistChip(onClick = {}, label = { Text("Mgn/Lot ${formatCurrency(contract.marginPerLot)}") })
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
fun TradingQuoteCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 13.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
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
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B050))
                ) {
                    Text("Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.negative)
                ) {
                    Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    val history by viewModel.quoteHistory.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsState()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsState()
    val lastExecutedOrder by viewModel.lastExecutedOrder.collectAsState()
    val postTradeReview by viewModel.copilotPostTradeReview.collectAsState()
    val productActionMessage by viewModel.productActionMessage.collectAsState()
    val copilotPortfolioActions by viewModel.copilotPortfolioActions.collectAsState()
    val lastOrderTraceId by viewModel.lastOrderTraceId.collectAsState()
    val orderExecutionLoading by viewModel.orderExecutionLoading.collectAsState()
    val copilotLoading by viewModel.copilotLoading.collectAsState()
    val lastError by viewModel.error.collectAsState()

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
        if (qty <= 0 || !isMarketOpen || limitInvalid || limitDeviationHardBlock) {
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
                Text(
                    text = "₹${String.format("%.2f", quote.last)}  ${if (quote.pctChange >= 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 13.sp,
                    color = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = LocalAppTheme.current.textSecondary)
            }
        }

        // Market closed warning
        if (!isMarketOpen) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "⚠ ${marketStatus?.message ?: "Market is closed"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.negative,
                    modifier = Modifier.padding(10.dp)
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
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wallet", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
            }
            Text(
                formatCurrency(walletBalance),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C4DFF)
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
                    containerColor = if (tradeType == "BUY") Color(0xFF00B050) else Color(0xFF2A2A2A)
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Buy", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { tradeType = "SELL" },
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "SELL") LocalAppTheme.current.negative else Color(0xFF2A2A2A)
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
                        containerColor = if (orderType == otype) LocalAppTheme.current.primary.copy(alpha = 0.25f) else Color(0xFF2A2A2A)
                    )
                )
            }
        }

        if (orderType == "LIMIT") {
            OutlinedTextField(
                value = limitPriceInput,
                onValueChange = { limitPriceInput = it },
                label = { Text("Limit Price", color = LocalAppTheme.current.textSecondary) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = appOutlinedTextFieldColors(containerColor = LocalAppTheme.current.surface),
            )
        }

        // Quantity
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Quantity", color = LocalAppTheme.current.textSecondary) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
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
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A2A2A))
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
                    if (qty > 0 && isMarketOpen && !limitInvalid && !limitDeviationHardBlock) {
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
                    modifier = Modifier.padding(bottom = 10.dp)
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

        if (lastExecutedForSymbol != null) {
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
                        value = "${lastExecutedForSymbol.order.side} ${lastExecutedForSymbol.order.qty} • ${lastExecutedForSymbol.orderStatus ?: lastExecutedForSymbol.status.uppercase()}"
                    )
                    lastExecutedForSymbol.executedPrice?.let { executedPrice ->
                        TradeSummaryLine(label = "Executed", value = formatCurrency(executedPrice))
                    }
                    lastExecutedForSymbol.total?.let { total ->
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
            Button(
                onClick = {
                    if (qty > 0) {
                        showConfirmDialog = true
                    }
                },
                enabled = qty > 0 && isMarketOpen && !limitInvalid && !limitDeviationHardBlock && !copilotBlocksTrade && !orderExecutionLoading && (tradeType == "SELL" || canAfford),
                modifier = Modifier.weight(2f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "BUY") Color(0xFF00B050) else LocalAppTheme.current.negative,
                    disabledContainerColor = Color(0xFF2A2A2A)
                ),
                shape = RoundedCornerShape(12.dp)
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
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
                    color = if (tradeType == "BUY") Color(0xFF00B050) else LocalAppTheme.current.negative
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
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (tradeType == "BUY") onBuy(qty) else onSell(qty)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tradeType == "BUY") Color(0xFF00B050) else LocalAppTheme.current.negative
                    )
                ) {
                    Text("Confirm $tradeType")
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
                AssistChip(
                    onClick = {},
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
    onAdd: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val presetAmounts = listOf(10000.0, 25000.0, 50000.0, 100000.0)
    var selectedUpi by remember { mutableStateOf("") }
    val upiProviders = listOf(
        "GPay" to "com.google.android.apps.nbu.paisa.user",
        "PhonePe" to "com.phonepe.app",
        "Amazon Pay" to "in.amazon.mShop.android.shopping"
    )
    var selectedUpiPackage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text("Add Funds to Wallet", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (\u20B9)", color = LocalAppTheme.current.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("\u20B9${preset.toInt()/1000}K", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Select UPI Provider", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    upiProviders.forEach { (name, pkg) ->
                        Button(
                            onClick = {
                                selectedUpi = name
                                selectedUpiPackage = pkg
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedUpi == name) Color(0xFF7C4DFF) else Color(0xFF2A2A2A)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(name, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedUpi.isNotEmpty() && selectedUpiPackage.isNotEmpty()) {
                        onAdd(amt, selectedUpiPackage)
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedUpi.isNotEmpty() && selectedUpiPackage.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Add Funds", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppTheme.current.primary)
            }
        }
    )
}
