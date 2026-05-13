package com.bysel.trader.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.MarketHeatmap
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.SignalLabBucketFeed
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
    var selectedTimeframeName by rememberSaveable { mutableStateOf(SignalLabTimeframe.INTRADAY.name) }
    val selectedTimeframe = remember(selectedTimeframeName) {
        runCatching { SignalLabTimeframe.valueOf(selectedTimeframeName) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    onTimeframeSelected = { timeframe -> selectedTimeframeName = timeframe.name },
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = theme.card),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No live setups in this filter",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.text,
                            )
                            Text(
                                text = "Try switching sector or timeframe. Signal Lab only shows buckets with active candidates.",
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
            }

            if (scopedBackendBuckets.isNotEmpty()) {
                item {
                    Text(
                        text = "Signal Lab Phase-2",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                    )
                }

                items(scopedBackendBuckets, key = { it.bucketId }) { bucket ->
                    BackendSignalBucketCard(
                        bucket = bucket,
                        onOpenSymbol = onOpenSymbol,
                    )
                }
            }

            items(signalBuckets, key = { it.title }) { bucket ->
                SignalBucketDetailCard(
                    bucket = bucket,
                    onOpenSymbol = onOpenSymbol,
                )
            }
        }
    }
}

@Composable
private fun BackendSignalBucketCard(
    bucket: SignalLabBucketFeed,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bucket.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = bucket.thesis,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bucket.proxy) {
                        AssistChip(onClick = {}, label = { Text("Proxy") })
                    }
                    AssistChip(onClick = {}, label = { Text("${bucket.candidates.size}") })
                }
            }

            if (bucket.notes.isNotEmpty()) {
                Text(
                    text = bucket.notes.first(),
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    lineHeight = 16.sp,
                )
            }

            bucket.candidates.take(5).forEach { candidate ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.symbol,
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = candidate.companyName,
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = candidate.thesis,
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatSignalChange(candidate.pctChange),
                            color = if (candidate.pctChange >= 0) theme.positive else theme.negative,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${candidate.confidence}% confidence",
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                        )
                        Button(onClick = { onOpenSymbol(candidate.symbol) }) {
                            Text("Open")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Signal Lab",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = selectedTimeframe.summary,
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("$quoteCount scoped quotes") })
                AssistChip(onClick = {}, label = { Text("$bucketCount live buckets") })
                AssistChip(onClick = {}, label = { Text(selectedSector) })
            }
            FilledTonalButton(onClick = onRefresh) {
                Text("Refresh Signals")
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
}

@Composable
private fun SignalBucketDetailCard(
    bucket: SignalLabBucket,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bucket.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = bucket.thesis,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${bucket.quotes.size}") },
                )
            }

            Text(
                text = signalLabLeadSummary(bucket),
                fontSize = 12.sp,
                color = theme.text,
                lineHeight = 18.sp,
            )

            bucket.quotes.take(5).forEach { quote ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = quote.symbol,
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${formatSignalCurrency(quote.last)} • ${formatSignalChange(quote.pctChange)}",
                            color = if (quote.pctChange >= 0) theme.positive else theme.negative,
                            fontSize = 12.sp,
                        )
                    }
                    Button(onClick = { onOpenSymbol(quote.symbol) }) {
                        Text("Open")
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
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