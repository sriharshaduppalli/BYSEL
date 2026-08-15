package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.MarketHeatmap
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.SignalLabBucketFeed
import com.bysel.trader.data.models.SignalLabCandidateFeed
import com.bysel.trader.ui.components.InfoChip
import com.bysel.trader.ui.theme.LocalAppTheme

private enum class SignalLabTimeframe(val title: String, val summary: String) {
    INTRADAY(
        title = "Intraday",
        summary = "Focuses on live tape action, volume acceleration, and same-session setups.",
    ),
    SWING(
        title = "Swing",
        summary = "Focuses on multi-session setups like 52-week structure, yield, and target gaps.",
    ),
}

@Composable
fun SignalLabScreen(
    quotes: List<Quote>,
    heatmap: MarketHeatmap?,
    backendBuckets: List<SignalLabBucketFeed>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    var selectedTimeframeKey by rememberSaveable { mutableStateOf(SignalLabTimeframe.INTRADAY.name) }
    val selectedTimeframe = remember(selectedTimeframeKey) {
        runCatching { SignalLabTimeframe.valueOf(selectedTimeframeKey) }
            .getOrDefault(SignalLabTimeframe.INTRADAY)
    }

    val sectors = remember(heatmap) {
        listOf("All") + heatmap
            ?.sectors
            .orEmpty()
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedSector by rememberSaveable { mutableStateOf("All") }
    if (selectedSector !in sectors) selectedSector = "All"

    val scopedQuotes = remember(quotes, heatmap, selectedSector, selectedTimeframe) {
        val sectorScoped = quotes.filterBySector(heatmap = heatmap, selectedSector = selectedSector)
        sectorScoped.filterByTimeframe(selectedTimeframe)
    }
    val signalBuckets = remember(scopedQuotes) { buildSignalLabBuckets(scopedQuotes) }
    val symbolToSector = remember(heatmap) {
        heatmap
            ?.sectors
            .orEmpty()
            .flatMap { sector ->
                sector.stocks.map { stock -> stock.symbol.uppercase() to sector.name }
            }
            .toMap()
    }
    val scopedBackendBuckets = remember(backendBuckets, selectedSector, symbolToSector) {
        if (selectedSector == "All") {
            backendBuckets
        } else {
            backendBuckets
                .map { bucket ->
                    bucket.copy(
                        candidates = bucket.candidates.filter { candidate ->
                            symbolToSector[candidate.symbol.uppercase()]
                                ?.equals(selectedSector, ignoreCase = true) == true
                        }
                    )
                }
                .filter { it.candidates.isNotEmpty() }
        }
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SignalLabHeroCard(
                    quoteCount = scopedQuotes.size,
                    bucketCount = signalBuckets.size + scopedBackendBuckets.size,
                    selectedTimeframe = selectedTimeframe,
                    selectedSector = selectedSector,
                    onRefresh = onRefresh,
                )
            }

            item {
                SignalLabFilterCard(
                    timeframes = SignalLabTimeframe.entries,
                    selectedTimeframe = selectedTimeframe,
                    sectors = sectors,
                    selectedSector = selectedSector,
                    onTimeframeSelected = { timeframe -> selectedTimeframeKey = timeframe.name },
                    onSectorSelected = { sector -> selectedSector = sector },
                )
            }

            if (isLoading && signalBuckets.isEmpty() && scopedBackendBuckets.isEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(20.dp).height(20.dp),
                            color = theme.primary,
                        )
                        Text(
                            text = "Refreshing signal buckets...",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (signalBuckets.isEmpty() && scopedBackendBuckets.isEmpty() && !isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "No paper setups in this filter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.text,
                        )
                        Text(
                            text = "Signal Lab is an educational scanner for practice ideas — not live brokerage signals or advice. Try another sector or timeframe, or refresh after the IST session opens (9:15).",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            lineHeight = 18.sp,
                        )
                        FilledTonalButton(onClick = onRefresh) {
                            Text("Refresh Feed")
                        }
                    }
                }
            }

            if (scopedBackendBuckets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Signal Lab Phase-2",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.text,
                        )
                        Text(
                            text = "Server buckets from live quotes. Institutional Conviction is a proxy — not FII/DII filings.",
                            fontSize = 11.sp,
                            color = theme.textSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                }

                items(scopedBackendBuckets, key = { "backend-${it.bucketId}" }) { bucket ->
                    SignalBucketCarousel(
                        title = bucket.title,
                        thesis = bucket.thesis,
                        count = bucket.candidates.size,
                        badge = if (bucket.proxy) "Proxy" else null,
                        note = bucket.notes.firstOrNull(),
                    ) {
                        items(
                            bucket.candidates.take(8),
                            key = { "${bucket.bucketId}-${it.symbol}" },
                        ) { candidate ->
                            BackendCandidateCard(
                                candidate = candidate,
                                onOpen = { onOpenSymbol(candidate.symbol) },
                            )
                        }
                    }
                }
            }

            if (signalBuckets.isNotEmpty()) {
                item {
                    Text(
                        text = "Live playbooks",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                    )
                }

                items(signalBuckets, key = { "local-${it.title}" }) { bucket ->
                    SignalBucketCarousel(
                        title = bucket.title,
                        thesis = bucket.thesis,
                        count = bucket.quotes.size,
                        badge = null,
                        note = signalLabLeadSummary(bucket),
                    ) {
                        items(
                            bucket.quotes.take(8),
                            key = { "${bucket.title}-${it.symbol}" },
                        ) { quote ->
                            LocalCandidateCard(
                                quote = quote,
                                onOpen = { onOpenSymbol(quote.symbol) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalBucketCarousel(
    title: String,
    thesis: String,
    count: Int,
    badge: String?,
    note: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = thesis,
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!badge.isNullOrBlank()) {
                    InfoChip(label = { Text(badge) })
                }
                InfoChip(label = { Text("$count") })
            }
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun BackendCandidateCard(
    candidate: SignalLabCandidateFeed,
    onOpen: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .width(196.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = candidate.symbol,
                color = theme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = candidate.companyName.ifBlank { "Live setup" },
                color = theme.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSignalChange(candidate.pctChange),
                color = if (candidate.pctChange >= 0) theme.positive else theme.negative,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = candidate.thesis.ifBlank { "${candidate.confidence}% confidence" },
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(48.dp),
            )
            Text(
                text = "Open →",
                color = theme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LocalCandidateCard(
    quote: Quote,
    onOpen: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .width(196.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = quote.symbol,
                color = theme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSignalCurrency(quote.last),
                color = theme.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = formatSignalChange(quote.pctChange),
                color = if (quote.pctChange >= 0) theme.positive else theme.negative,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if ((quote.volume ?: 0L) > 0L) {
                    "Vol ${formatCompactVolume(quote.volume)}"
                } else {
                    "Tap for stock context"
                },
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(32.dp),
            )
            Text(
                text = "Open →",
                color = theme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SignalLabHeroCard(
    quoteCount: Int,
    bucketCount: Int,
    selectedTimeframe: SignalLabTimeframe,
    selectedSector: String,
    onRefresh: () -> Unit,
) {
    val theme = LocalAppTheme.current
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
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Signal Lab",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = theme.text,
        )
        Text(
            text = "Paper practice · not investment advice",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.primary,
        )
        Text(
            text = selectedTimeframe.summary,
            fontSize = 12.sp,
            color = theme.textSecondary,
            lineHeight = 18.sp,
        )
        Text(
            text = "Educational buckets from the tape (IST session). Swipe each playbook sideways. Not live brokerage signals.",
            fontSize = 11.sp,
            color = theme.textSecondary,
            lineHeight = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(label = { Text("$quoteCount scoped quotes") })
            InfoChip(label = { Text("$bucketCount paper buckets") })
            InfoChip(label = { Text(selectedSector) })
        }
        FilledTonalButton(onClick = onRefresh) {
            Text("Refresh Signals")
        }
    }
}

@Composable
private fun SignalLabFilterCard(
    timeframes: List<SignalLabTimeframe>,
    selectedTimeframe: SignalLabTimeframe,
    sectors: List<String>,
    selectedSector: String,
    onTimeframeSelected: (SignalLabTimeframe) -> Unit,
    onSectorSelected: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Filters",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(timeframes, key = { it.name }) { timeframe ->
                FilterChip(
                    selected = selectedTimeframe == timeframe,
                    onClick = { onTimeframeSelected(timeframe) },
                    label = { Text(timeframe.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = theme.text,
                    ),
                )
            }
        }

        if (sectors.isNotEmpty()) {
            Text(
                text = "Sector",
                fontSize = 12.sp,
                color = theme.textSecondary,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sectors, key = { it }) { sector ->
                    FilterChip(
                        selected = selectedSector == sector,
                        onClick = { onSectorSelected(sector) },
                        label = {
                            Text(
                                text = sector,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = theme.text,
                        ),
                    )
                }
            }
        }
    }
}

private fun List<Quote>.filterBySector(heatmap: MarketHeatmap?, selectedSector: String): List<Quote> {
    if (selectedSector == "All") return this
    val symbolSet = heatmap
        ?.sectors
        .orEmpty()
        .firstOrNull { sector -> sector.name.equals(selectedSector, ignoreCase = true) }
        ?.stocks
        .orEmpty()
        .map { stock -> stock.symbol.uppercase() }
        .toSet()

    if (symbolSet.isEmpty()) return emptyList()
    return filter { quote -> symbolSet.contains(quote.symbol.uppercase()) }
}

private fun List<Quote>.filterByTimeframe(timeframe: SignalLabTimeframe): List<Quote> {
    return when (timeframe) {
        SignalLabTimeframe.INTRADAY -> filter { quote ->
            val hasDayStructure = quote.dayHigh != null || quote.dayLow != null
            val hasLiquidity = (quote.volume ?: 0L) > 0L
            hasDayStructure || hasLiquidity
        }
        SignalLabTimeframe.SWING -> filter { quote ->
            quote.fiftyTwoWeekHigh != null ||
                quote.fiftyTwoWeekLow != null ||
                quote.targetMeanPrice != null ||
                quote.dividendYield != null
        }
    }
}

private fun formatSignalCurrency(value: Double): String = "₹${String.format("%.2f", value)}"

private fun formatSignalChange(value: Double): String = buildString {
    if (value > 0) append("+")
    append(String.format("%.2f", value))
    append("%")
}

private fun formatCompactVolume(volume: Long?): String {
    val v = volume ?: return "—"
    return when {
        v >= 10_000_000 -> String.format("%.1fCr", v / 10_000_000.0)
        v >= 100_000 -> String.format("%.1fL", v / 100_000.0)
        v >= 1_000 -> String.format("%.1fK", v / 1_000.0)
        else -> v.toString()
    }
}
