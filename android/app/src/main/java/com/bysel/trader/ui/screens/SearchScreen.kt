package com.bysel.trader.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.SignalLabBucketFeed
import com.bysel.trader.data.models.StockSearchResult
import com.bysel.trader.ui.components.StockNotesIcon
import com.bysel.trader.ui.components.appOutlinedTextFieldColors
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.ui.format.formatVolumeCompact
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.MarqueeText
import com.bysel.trader.ui.theme.PriceChangeLine
import com.bysel.trader.ui.theme.ScreenHeader
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SEARCH_RECENTS_PREF = "bysel_search"
private const val SEARCH_RECENTS_KEY = "recent_symbols"

private data class SearchJump(
    val title: String,
    val tab: Int,
    val keywords: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    quotes: List<Quote>,
    watchlistSymbols: List<String>,
    @Suppress("UNUSED_PARAMETER") backendBuckets: List<SignalLabBucketFeed>,
    searchResults: List<StockSearchResult>,
    isSearching: Boolean,
    onSearchQuery: (String) -> Unit,
    onClearSearch: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onSymbolClick: (String) -> Unit,
    onRouteClick: (Int) -> Unit,
    onAddToWatchlist: ((String) -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(SEARCH_RECENTS_PREF, Context.MODE_PRIVATE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var recentSymbols by remember { mutableStateOf(loadRecentSymbols(prefs)) }

    val jumps = remember {
        listOf(
            SearchJump("Trade", 2, listOf("trade", "spot", "wallet", "paper")),
            SearchJump("Heatmap", 4, listOf("heatmap", "sector", "breadth", "tqi")),
            SearchJump("Signal Lab", 20, listOf("signal", "screener", "breakout")),
            SearchJump("Watchlist", 25, listOf("watchlist", "watch")),
            SearchJump("AI", 1, listOf("ai", "assistant", "chat", "coach")),
            SearchJump("Risk Lab", 22, listOf("risk", "var", "monte")),
            SearchJump("Journal", 24, listOf("journal", "review")),
            SearchJump("Alerts", 7, listOf("alert", "trigger", "price")),
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

    val normalizedQuery = searchQuery.trim()
    val looksLikeTicker = remember(normalizedQuery) {
        val q = normalizedQuery.uppercase()
        q.isNotBlank() && q.length in 1..15 && q.all { it.isLetterOrDigit() || it == '-' || it == '.' }
    }
    val matchingJumps = remember(normalizedQuery, jumps) {
        if (normalizedQuery.isBlank()) emptyList()
        else jumps.filter { jump ->
            jump.title.contains(normalizedQuery, ignoreCase = true) ||
                jump.keywords.any { it.equals(normalizedQuery, ignoreCase = true) || it.startsWith(normalizedQuery, ignoreCase = true) }
        }.take(4)
    }
    val exactSymbolCandidate = normalizedQuery.uppercase()
        .takeIf { looksLikeTicker && it.isNotBlank() }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            onClearSearch()
            return@LaunchedEffect
        }
        delay(280)
        onSearchQuery(searchQuery)
    }

    fun recordRecentSymbol(symbol: String) {
        val updated = (listOf(symbol.uppercase()) + recentSymbols.filterNot { it.equals(symbol, ignoreCase = true) })
            .take(8)
        recentSymbols = updated
        prefs.edit().putString(SEARCH_RECENTS_KEY, updated.joinToString("|")).apply()
    }

    fun openSymbol(symbol: String) {
        recordRecentSymbol(symbol)
        onSymbolClick(symbol)
    }

    fun openQuote(quote: Quote) {
        recordRecentSymbol(quote.symbol)
        onQuoteClick(quote)
    }

    Scaffold(containerColor = theme.surface) { paddingValues ->
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Search Stocks",
                    subtitle = "Full NSE listed catalog (~2,400+). Add to watchlist or open detail.",
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Company or symbol (e.g. INFY, Reliance)", color = theme.textSecondary)
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
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                )
            }

            if (normalizedQuery.isBlank()) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        jumps.forEach { jump ->
                            AssistChip(
                                onClick = { onRouteClick(jump.tab) },
                                label = { Text(jump.title) },
                            )
                        }
                    }
                }

                if (recentSymbols.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Recent",
                            subtitle = "Symbols you opened recently.",
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

                if (watchlistQuotes.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Your watchlist",
                            subtitle = "Tracked names with quotes on the live board.",
                        )
                    }
                    items(watchlistQuotes, key = { "wl-${it.symbol}" }) { quote ->
                        DiscoveryQuoteCard(
                            quote = quote,
                            subtitle = "Watchlist",
                            onOpen = { openQuote(quote) },
                            onWatch = null,
                            isWatchlisted = true,
                        )
                    }
                }

                if (topMovers.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Movers on live board",
                            subtitle = "Largest moves among currently loaded quotes — not the full exchange.",
                        )
                    }
                    items(topMovers, key = { "mv-${it.symbol}" }) { quote ->
                        val watched = watchlistSymbols.any { it.equals(quote.symbol, ignoreCase = true) }
                        DiscoveryQuoteCard(
                            quote = quote,
                            subtitle = if (quote.pctChange >= 0.0) "Gainer" else "Loser",
                            onOpen = { openQuote(quote) },
                            onWatch = onAddToWatchlist?.takeUnless { watched }?.let { add ->
                                { add(quote.symbol) }
                            },
                            isWatchlisted = watched,
                        )
                    }
                }
            } else {
                // Stock matches first — this is the primary job of Search.
                if (isSearching && searchResults.isEmpty()) {
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
                                text = "Searching the NSE catalog…",
                                fontSize = 13.sp,
                                color = theme.textSecondary,
                            )
                        }
                    }
                }

                if (searchResults.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Stocks",
                            subtitle = "${searchResults.size} match${if (searchResults.size == 1) "" else "es"} in the listed universe.",
                        )
                    }
                    items(searchResults, key = { it.symbol }) { result ->
                        val existingQuote = quotes.firstOrNull { it.symbol.equals(result.symbol, ignoreCase = true) }
                        val alreadyWatched = watchlistSymbols.any { it.equals(result.symbol, ignoreCase = true) }
                        SearchResultCard(
                            result = result,
                            quote = existingQuote,
                            onOpen = {
                                if (existingQuote != null) openQuote(existingQuote) else openSymbol(result.symbol)
                            },
                            onAddToWatchlist = onAddToWatchlist?.takeUnless { alreadyWatched }?.let { add ->
                                { add(result.symbol) }
                            },
                            isWatchlisted = alreadyWatched,
                        )
                    }
                }

                val showSymbolFallback = exactSymbolCandidate != null &&
                    searchResults.none { it.symbol.equals(exactSymbolCandidate, ignoreCase = true) } &&
                    !isSearching

                if (showSymbolFallback) {
                    val directSymbol = exactSymbolCandidate.orEmpty()
                    item {
                        SearchSectionHeader(
                            title = "Open ticker",
                            subtitle = "No catalog hit yet — open this symbol directly.",
                        )
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = theme.card),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(directSymbol, fontWeight = FontWeight.Bold, color = theme.text, fontSize = 16.sp)
                                    Text("Open symbol detail", fontSize = 12.sp, color = theme.textSecondary)
                                }
                                Button(onClick = { openSymbol(directSymbol) }, shape = RoundedCornerShape(12.dp)) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }

                if (matchingJumps.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "App shortcuts",
                            subtitle = "Optional jumps — stock matches stay above.",
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matchingJumps, key = { it.title }) { jump ->
                                AssistChip(
                                    onClick = { onRouteClick(jump.tab) },
                                    label = { Text(jump.title) },
                                )
                            }
                        }
                    }
                }

                if (
                    searchResults.isEmpty() &&
                    matchingJumps.isEmpty() &&
                    !showSymbolFallback &&
                    !isSearching
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No match for \"$normalizedQuery\"",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.text,
                                )
                                Text(
                                    text = "Try a ticker (TCS) or company name (Tata Consultancy).",
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
private fun SearchSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppTheme.current.text,
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = LocalAppTheme.current.textSecondary,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun DiscoveryQuoteCard(
    quote: Quote,
    subtitle: String,
    onOpen: () -> Unit,
    onWatch: (() -> Unit)?,
    isWatchlisted: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
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
                    text = quote.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text,
                )
                MarqueeText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppTheme.current.textSecondary,
                )
                PriceChangeLine(
                    last = quote.last,
                    pctChange = quote.pctChange,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                quote.volume?.let { vol ->
                    Text(
                        text = "Vol ${formatVolumeCompact(vol)}",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    isWatchlisted -> {
                        Text("Watching", fontSize = 11.sp, color = LocalAppTheme.current.positive, fontWeight = FontWeight.SemiBold)
                    }
                    onWatch != null -> {
                        OutlinedButton(
                            onClick = onWatch,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("Watch", fontSize = 12.sp) }
                    }
                }
                Button(onClick = onOpen, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(34.dp)) {
                    Text("Open", fontSize = 12.sp)
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
    onAddToWatchlist: (() -> Unit)? = null,
    isWatchlisted: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.symbol,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text,
                    )
                    StockNotesIcon(symbol = result.symbol)
                }
                MarqueeText(
                    text = result.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (quote != null) {
                    PriceChangeLine(
                        last = quote.last,
                        pctChange = quote.pctChange,
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "Tap Open for live quote",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    isWatchlisted -> {
                        Text("Watching", fontSize = 11.sp, color = LocalAppTheme.current.positive, fontWeight = FontWeight.SemiBold)
                    }
                    onAddToWatchlist != null -> {
                        OutlinedButton(
                            onClick = onAddToWatchlist,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("Watch", fontSize = 12.sp) }
                    }
                }
                Button(onClick = onOpen, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(34.dp)) {
                    Text("Open", fontSize = 12.sp)
                }
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
