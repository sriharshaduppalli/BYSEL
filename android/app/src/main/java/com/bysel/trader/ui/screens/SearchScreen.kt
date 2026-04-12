package com.bysel.trader.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.SignalLabBucketFeed
import com.bysel.trader.data.models.StockSearchResult
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SEARCH_RECENTS_PREF = "bysel_search"
private const val SEARCH_RECENTS_KEY = "recent_symbols"

private data class SearchShortcut(
    val title: String,
    val caption: String,
    val tab: Int,
    val keywords: List<String>,
)

@Composable
fun SearchScreen(
    quotes: List<Quote>,
    watchlistSymbols: List<String>,
    backendBuckets: List<SignalLabBucketFeed>,
    searchResults: List<StockSearchResult>,
    isSearching: Boolean,
    onSearchQuery: (String) -> Unit,
    onClearSearch: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onSymbolClick: (String) -> Unit,
    onRouteClick: (Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(SEARCH_RECENTS_PREF, Context.MODE_PRIVATE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var recentSymbols by remember { mutableStateOf(loadRecentSymbols(prefs)) }

    val shortcuts = remember {
        listOf(
            SearchShortcut(
                title = "Home",
                caption = "Market cockpit, movers, and portfolio pulse",
                tab = 0,
                keywords = listOf("home", "dashboard", "market", "pulse"),
            ),
            SearchShortcut(
                title = "Trade Hub",
                caption = "Spot, advanced orders, options, and futures radar",
                tab = 2,
                keywords = listOf("trade", "spot", "orders", "futures", "options"),
            ),
            SearchShortcut(
                title = "Portfolio",
                caption = "Holdings, health score, and PnL context",
                tab = 3,
                keywords = listOf("portfolio", "holdings", "pnl", "returns"),
            ),
            SearchShortcut(
                title = "Heatmap",
                caption = "Sector leadership, breadth, and hot or cold pockets",
                tab = 4,
                keywords = listOf("heatmap", "sector", "breadth", "leadership", "market map"),
            ),
            SearchShortcut(
                title = "Signal Lab",
                caption = "Filter breakouts, volume spikes, dividend, and upside buckets",
                tab = 20,
                keywords = listOf("signal", "screener", "breakout", "dividend", "volume", "upside", "results week", "institutional conviction"),
            ),
            SearchShortcut(
                title = "Smart Money",
                caption = "Track legendary investor disclosed holdings and strategies",
                tab = 21,
                keywords = listOf("smart money", "investor", "portfolio", "legendary", "jhunjhunwala", "damani", "holdings"),
            ),
            SearchShortcut(
                title = "Alerts",
                caption = "Price triggers and monitored setups",
                tab = 7,
                keywords = listOf("alert", "trigger", "watch", "price"),
            ),
            SearchShortcut(
                title = "Advanced Orders",
                caption = "Basket, trigger, charges, and risk checks",
                tab = 16,
                keywords = listOf("advanced", "basket", "trigger", "charges", "calculator", "margin"),
            ),
            SearchShortcut(
                title = "Charges Calculator",
                caption = "Estimate brokerage and net trade impact before execution",
                tab = 16,
                keywords = listOf("calculator", "brokerage", "charges", "cost", "impact", "estimate"),
            ),
            SearchShortcut(
                title = "Derivatives",
                caption = "Option chain, Greeks, and payoff preview",
                tab = 17,
                keywords = listOf("derivatives", "options", "greeks", "payoff", "chain"),
            ),
            SearchShortcut(
                title = "Wealth OS",
                caption = "Family wealth, goals, and net worth view",
                tab = 18,
                keywords = listOf("wealth", "goals", "family", "net worth"),
            ),
            SearchShortcut(
                title = "Copilot Center",
                caption = "Guidance, trace lookup, and trust tools",
                tab = 19,
                keywords = listOf("copilot", "help", "trace", "support", "trust"),
            ),
            SearchShortcut(
                title = "Trust Desk",
                caption = "Trace-based support, rejection help, and trust diagnostics",
                tab = 19,
                keywords = listOf("trust", "trace", "support", "error", "rejection", "diagnostics"),
            ),
        )
    }

    val watchlistQuotes = remember(quotes, watchlistSymbols) {
        watchlistSymbols.mapNotNull { symbol ->
            quotes.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
        }.take(6)
    }
    val topMovers = remember(quotes) {
        quotes.sortedByDescending { abs(it.pctChange) }.take(6)
    }
    val signalBuckets = remember(quotes) { buildSignalLabBuckets(quotes) }
    val upCount = remember(quotes) { quotes.count { it.pctChange >= 0.0 } }
    val downCount = remember(quotes) { quotes.count { it.pctChange < 0.0 } }

    val normalizedQuery = searchQuery.trim()
    val matchingShortcuts = remember(normalizedQuery, shortcuts) {
        if (normalizedQuery.isBlank()) {
            shortcuts
        } else {
            shortcuts.filter { shortcut ->
                shortcut.title.contains(normalizedQuery, ignoreCase = true) ||
                    shortcut.caption.contains(normalizedQuery, ignoreCase = true) ||
                    shortcut.keywords.any {
                        it.contains(normalizedQuery, ignoreCase = true) ||
                            normalizedQuery.contains(it, ignoreCase = true)
                    }
            }
        }
    }
    val matchingSignalBuckets = remember(normalizedQuery, signalBuckets) {
        if (normalizedQuery.isBlank()) {
            signalBuckets
        } else {
            signalBuckets.filter { bucket -> signalLabMatchesQuery(normalizedQuery, bucket) }
        }
    }
    val matchingBackendSignalBuckets = remember(normalizedQuery, backendBuckets) {
        if (normalizedQuery.isBlank()) {
            backendBuckets
        } else {
            backendBuckets
                .map { bucket ->
                    val matchingCandidates = bucket.candidates.filter { candidate ->
                        candidate.symbol.contains(normalizedQuery, ignoreCase = true) ||
                            candidate.companyName.contains(normalizedQuery, ignoreCase = true) ||
                            candidate.thesis.contains(normalizedQuery, ignoreCase = true) ||
                            candidate.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
                    }

                    val bucketMatch = bucket.title.contains(normalizedQuery, ignoreCase = true) ||
                        bucket.thesis.contains(normalizedQuery, ignoreCase = true) ||
                        bucket.bucketId.contains(normalizedQuery, ignoreCase = true) ||
                        bucket.notes.any { it.contains(normalizedQuery, ignoreCase = true) }

                    if (bucketMatch) {
                        bucket
                    } else {
                        bucket.copy(candidates = matchingCandidates)
                    }
                }
                .filter { it.candidates.isNotEmpty() }
        }
    }
    val exactSymbolCandidate = normalizedQuery.uppercase()
        .takeIf { it.isNotBlank() && it.length <= 15 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' } }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            onClearSearch()
            showSuggestions = false
            return@LaunchedEffect
        }
        showSuggestions = true
        delay(100)
        onSearchQuery(searchQuery)
    }

    fun recordRecentSymbol(symbol: String) {
        val updated = (listOf(symbol.uppercase()) + recentSymbols.filterNot { it.equals(symbol, ignoreCase = true) })
            .take(8)
        recentSymbols = updated
        prefs.edit().putString(SEARCH_RECENTS_KEY, updated.joinToString("|")) .apply()
    }

    fun openSymbol(symbol: String) {
        recordRecentSymbol(symbol)
        onSymbolClick(symbol)
    }

    fun openQuote(quote: Quote) {
        recordRecentSymbol(quote.symbol)
        onQuoteClick(quote)
    }

    Scaffold(
        containerColor = theme.surface,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SearchHeroCard(
                    quoteCount = quotes.size,
                    watchlistCount = watchlistSymbols.size,
                    upCount = upCount,
                    downCount = downCount,
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search stocks, breakouts, dividends, derivatives, wealth...",
                            color = theme.textSecondary,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = theme.textSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                onClearSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = theme.textSecondary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appOutlinedTextFieldColors(containerColor = theme.card),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                )
            }

            if (normalizedQuery.isBlank()) {
                if (recentSymbols.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Recent Views",
                            subtitle = "Jump back into symbols you opened recently.",
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSymbols, key = { it }) { symbol ->
                                AssistChip(
                                    onClick = { openSymbol(symbol) },
                                    label = { Text(symbol) },
                                )
                            }
                        }
                    }
                }

                item {
                    SearchSectionHeader(
                        title = "Quick Destinations",
                        subtitle = "Search is now a route into trade, derivatives, wealth, alerts, and support.",
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shortcuts, key = { it.title }) { shortcut ->
                            SearchShortcutCard(shortcut = shortcut, onOpen = { onRouteClick(shortcut.tab) })
                        }
                    }
                }

                if (signalBuckets.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Signal Lab",
                            subtitle = "Breakout, dividend, upside, rebound, and volume ideas pulled from the live board.",
                        )
                    }
                    item {
                        SignalLabRail(
                            buckets = signalBuckets,
                            onOpenQuote = { quote -> openQuote(quote) },
                        )
                    }
                }

                if (backendBuckets.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Signal Lab Phase-2",
                            subtitle = "Results Week and Institutional Conviction buckets streamed from backend intelligence.",
                        )
                    }
                    item {
                        BackendSignalLabRail(
                            buckets = backendBuckets,
                            onOpenSymbol = { symbol -> openSymbol(symbol) },
                        )
                    }
                }

                if (watchlistQuotes.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Your Radar",
                            subtitle = "Watchlist names and tracked symbols with the highest chance of immediate follow-through.",
                        )
                    }
                    items(watchlistQuotes, key = { it.symbol }) { quote ->
                        DiscoveryQuoteCard(
                            quote = quote,
                            subtitle = "Watchlist",
                            onOpen = { openQuote(quote) },
                        )
                    }
                }

                item {
                    SearchSectionHeader(
                        title = "Trending Now",
                        subtitle = "The largest live movers from the current board.",
                    )
                }
                items(topMovers, key = { it.symbol }) { quote ->
                    DiscoveryQuoteCard(
                        quote = quote,
                        subtitle = if (quote.pctChange >= 0.0) "Momentum" else "Pressure",
                        onOpen = { openQuote(quote) },
                    )
                }
            } else {
                if (isSearching) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = theme.primary,
                            )
                            Text(
                                text = "Searching across instruments and destinations...",
                                fontSize = 12.sp,
                                color = theme.textSecondary,
                            )
                        }
                    }
                }

                if (matchingShortcuts.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Destinations",
                            subtitle = "Relevant app surfaces for the query you typed.",
                        )
                    }
                    items(matchingShortcuts, key = { it.title }) { shortcut ->
                        SearchShortcutRow(shortcut = shortcut, onOpen = { onRouteClick(shortcut.tab) })
                    }
                }

                if (matchingSignalBuckets.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Signal Lab",
                            subtitle = "Setup-style queries now route into breakout, yield, upside, and rebound buckets.",
                        )
                    }
                    item {
                        SignalLabRail(
                            buckets = matchingSignalBuckets,
                            onOpenQuote = { quote -> openQuote(quote) },
                        )
                    }
                }

                if (matchingBackendSignalBuckets.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Signal Lab Phase-2",
                            subtitle = "Results Week and Institutional Conviction matches from backend buckets.",
                        )
                    }
                    item {
                        BackendSignalLabRail(
                            buckets = matchingBackendSignalBuckets,
                            onOpenSymbol = { symbol -> openSymbol(symbol) },
                        )
                    }
                }

                if (searchResults.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Instruments",
                            subtitle = "${searchResults.size} live match${if (searchResults.size == 1) "" else "es"} ready to open.",
                        )
                    }
                    items(searchResults, key = { it.symbol }) { result ->
                        val existingQuote = quotes.firstOrNull { it.symbol.equals(result.symbol, ignoreCase = true) }
                        SearchResultCard(
                            result = result,
                            quote = existingQuote,
                            onOpen = {
                                if (existingQuote != null) openQuote(existingQuote) else openSymbol(result.symbol)
                            },
                        )
                    }
                }

                val showSymbolFallback = exactSymbolCandidate != null &&
                    searchResults.none { it.symbol.equals(exactSymbolCandidate, ignoreCase = true) }

                if (showSymbolFallback) {
                    val directSymbol = exactSymbolCandidate.orEmpty()
                    item {
                        SearchSectionHeader(
                            title = "Direct Open",
                            subtitle = "Open a symbol directly when you already know the ticker.",
                        )
                    }
                    item {
                        SearchShortcutRow(
                            shortcut = SearchShortcut(
                                title = directSymbol,
                                caption = "Open symbol detail directly",
                                tab = 9,
                                keywords = emptyList(),
                            ),
                            actionText = "Open Symbol",
                            onOpen = { openSymbol(directSymbol) },
                        )
                    }
                }

                if (searchResults.isEmpty() && matchingShortcuts.isEmpty() && !isSearching && showSuggestions) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No direct match for \"$normalizedQuery\"",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.text,
                                )
                                Text(
                                    text = "Try a symbol, company, breakout, dividend, heatmap, wealth, or Copilot.",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary,
                                    modifier = Modifier.padding(top = 8.dp),
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
private fun SearchHeroCard(
    quoteCount: Int,
    watchlistCount: Int,
    upCount: Int,
    downCount: Int,
) {
    val theme = LocalAppTheme.current
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
                            theme.primary.copy(alpha = 0.24f),
                            theme.surface,
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Universal Discovery",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = "Stocks, signals, trade routes, derivatives, wealth, and support surfaces from one entry point.",
                fontSize = 13.sp,
                color = theme.textSecondary,
                lineHeight = 19.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("$quoteCount live quotes") })
                AssistChip(onClick = {}, label = { Text("$watchlistCount watchlist") })
                AssistChip(onClick = {}, label = { Text("$upCount up / $downCount down") })
            }
        }
    }
}

@Composable
private fun SignalLabRail(
    buckets: List<SignalLabBucket>,
    onOpenQuote: (Quote) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(buckets, key = { it.title }) { bucket ->
            SignalLabCard(
                bucket = bucket,
                onOpenQuote = onOpenQuote,
            )
        }
    }
}

@Composable
private fun SignalLabCard(
    bucket: SignalLabBucket,
    onOpenQuote: (Quote) -> Unit,
) {
    val leadQuote = bucket.quotes.firstOrNull()
    Card(
        modifier = Modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = bucket.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
            )
            Text(
                text = bucket.thesis,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                lineHeight = 18.sp,
            )
            AssistChip(
                onClick = {},
                label = {
                    Text("${bucket.quotes.size} live setup${if (bucket.quotes.size == 1) "" else "s"}")
                },
            )
            Text(
                text = signalLabLeadSummary(bucket),
                fontSize = 12.sp,
                color = LocalAppTheme.current.text,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bucket.quotes.take(3).forEach { quote ->
                    AssistChip(
                        onClick = { onOpenQuote(quote) },
                        label = { Text(quote.symbol) },
                    )
                }
            }
            Button(
                onClick = { leadQuote?.let(onOpenQuote) },
                enabled = leadQuote != null,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (leadQuote != null) "Open ${leadQuote.symbol}" else "Open")
            }
        }
    }
}

@Composable
private fun BackendSignalLabRail(
    buckets: List<SignalLabBucketFeed>,
    onOpenSymbol: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(buckets, key = { it.bucketId }) { bucket ->
            BackendSignalLabCard(
                bucket = bucket,
                onOpenSymbol = onOpenSymbol,
            )
        }
    }
}

@Composable
private fun BackendSignalLabCard(
    bucket: SignalLabBucketFeed,
    onOpenSymbol: (String) -> Unit,
) {
    val leadCandidate = bucket.candidates.firstOrNull()
    Card(
        modifier = Modifier.width(300.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = bucket.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
            )
            Text(
                text = bucket.thesis,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text("${bucket.candidates.size} setup${if (bucket.candidates.size == 1) "" else "s"}")
                    },
                )
                if (bucket.proxy) {
                    AssistChip(onClick = {}, label = { Text("Proxy") })
                }
            }

            leadCandidate?.let { candidate ->
                Text(
                    text = "${candidate.symbol} ${formatSignedPercent(candidate.pctChange)} • ${candidate.confidence}% confidence",
                    fontSize = 12.sp,
                    color = if (candidate.pctChange >= 0.0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                )
                Text(
                    text = candidate.thesis,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.text,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bucket.candidates.take(3).forEach { candidate ->
                    AssistChip(
                        onClick = { onOpenSymbol(candidate.symbol) },
                        label = { Text(candidate.symbol) },
                    )
                }
            }

            if (bucket.notes.isNotEmpty()) {
                Text(
                    text = bucket.notes.first(),
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = { leadCandidate?.let { onOpenSymbol(it.symbol) } },
                enabled = leadCandidate != null,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (leadCandidate != null) "Open ${leadCandidate.symbol}" else "Open")
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, subtitle: String) {
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
private fun SearchShortcutCard(shortcut: SearchShortcut, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.width(240.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = shortcut.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
            )
            Text(
                text = shortcut.caption,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                lineHeight = 18.sp,
            )
            FilledTonalButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun SearchShortcutRow(
    shortcut: SearchShortcut,
    onOpen: () -> Unit,
    actionText: String = "Open",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = shortcut.caption,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    lineHeight = 18.sp,
                )
            }
            Button(onClick = onOpen, shape = RoundedCornerShape(12.dp)) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun DiscoveryQuoteCard(
    quote: Quote,
    subtitle: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
                Text(
                    text = formatCurrency(quote.last),
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.text,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedPercent(quote.pctChange),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                )
                Text(
                    text = quote.volume?.let { formatCompactVolume(it) } ?: "N/A vol",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = onOpen,
                    modifier = Modifier.padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: StockSearchResult,
    quote: Quote?,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.symbol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = result.name,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = quote?.let { "${formatCurrency(it.last)} • ${formatSignedPercent(it.pctChange)}" } ?: "Open to fetch the latest quote context",
                    fontSize = 12.sp,
                    color = quote?.let { if (it.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative }
                        ?: LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Open")
            }
        }
    }
}

private fun loadRecentSymbols(prefs: android.content.SharedPreferences): List<String> {
    return prefs.getString(SEARCH_RECENTS_KEY, "")
        .orEmpty()
        .split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(8)
}

private fun formatCurrency(value: Double): String = "₹${String.format("%.2f", value)}"

private fun formatSignedPercent(value: Double): String = buildString {
    if (value > 0) append("+")
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
