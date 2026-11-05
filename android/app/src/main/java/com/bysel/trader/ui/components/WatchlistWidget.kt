package com.bysel.trader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.ui.format.formatVolumeCompact
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.TickPriceText
import com.bysel.trader.ui.theme.byselSectionSurface

@Composable
fun WatchlistWidget(
    isPinned: Boolean,
    quotes: List<Quote>,
    onPinClick: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onTradeClick: ((Quote) -> Unit)? = null,
    trackedCount: Int = quotes.size,
    title: String = "My Watchlist",
) {
    val theme = LocalAppTheme.current
    var sort by rememberSaveable { mutableStateOf(WatchlistSortMode.MOVE.name) }
    var columns by rememberSaveable { mutableStateOf("range,hl,vol") }
    val columnSet = remember(columns) { columns.split(',').filter { it.isNotBlank() }.toSet() }
    val selectedSort = remember(sort) {
        runCatching { WatchlistSortMode.valueOf(sort) }.getOrDefault(WatchlistSortMode.MOVE)
    }
    val sortedQuotes = remember(quotes, selectedSort) {
        quotes.sortedByWatchlistMode(selectedSort).take(20)
    }
    val waitingForQuotes = trackedCount > 0 && quotes.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .byselSectionSurface(RoundedCornerShape(12.dp))
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(
                    text = when {
                        trackedCount <= 0 -> "Add names from Search or Trade — full NSE catalog."
                        waitingForQuotes -> "$trackedCount tracked · waiting for quotes"
                        selectedSort == WatchlistSortMode.GAINERS && sortedQuotes.isEmpty() ->
                            "$trackedCount tracked · no gainers this session"
                        selectedSort == WatchlistSortMode.LOSERS && sortedQuotes.isEmpty() ->
                            "$trackedCount tracked · no losers this session"
                        else -> "${quotes.size} tracked · ${selectedSort.label.lowercase()}"
                    },
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin Watchlist" else "Pin Watchlist",
                    tint = if (isPinned) theme.positive else theme.textSecondary,
                )
            }
        }

        if (trackedCount > 0) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .exclusiveHorizontalScroll(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(WatchlistSortMode.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == selectedSort,
                        onClick = { sort = option.name },
                        label = { Text(option.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = theme.text,
                            containerColor = theme.surface,
                            labelColor = theme.textSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = option == selectedSort,
                            borderColor = theme.textSecondary.copy(alpha = 0.2f),
                            selectedBorderColor = theme.primary.copy(alpha = 0.45f),
                        ),
                    )
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .exclusiveHorizontalScroll(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("range" to "Range", "hl" to "High / low", "vol" to "Volume"), key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = key in columnSet,
                        onClick = {
                            val next = columnSet.toMutableSet()
                            if (key in next) next.remove(key) else next.add(key)
                            columns = listOf("range", "hl", "vol").filter { it in next }.joinToString(",")
                        },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (waitingForQuotes) {
                Text(
                    text = "Quotes for your watchlist are still loading. Pull Home to refresh.",
                    fontSize = 14.sp,
                    color = theme.textSecondary,
                )
            } else if (quotes.isEmpty()) {
                Text(
                    text = "Your watchlist is empty. Search any listed NSE stock, tap Watch, then return here.",
                    fontSize = 14.sp,
                    color = theme.textSecondary,
                )
            } else if (sortedQuotes.isEmpty()) {
                Text(
                    text = when (selectedSort) {
                        WatchlistSortMode.GAINERS -> "None of your names are up this session."
                        WatchlistSortMode.LOSERS -> "None of your names are down this session."
                        else -> "Nothing to show for this sort."
                    },
                    fontSize = 14.sp,
                    color = theme.textSecondary,
                )
            } else {
                sortedQuotes.forEachIndexed { index, quote ->
                    WatchRow(
                        quote = quote,
                        showRange = "range" in columnSet,
                        showHighLow = "hl" in columnSet,
                        showVolumeColumn = "vol" in columnSet || selectedSort == WatchlistSortMode.VOLUME,
                        onOpen = { onQuoteClick(quote) },
                        onTrade = onTradeClick?.let { handler -> { handler(quote) } },
                    )
                    if (index < sortedQuotes.lastIndex) {
                        HorizontalDivider(
                            color = theme.textSecondary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchRow(
    quote: Quote,
    onOpen: () -> Unit,
    onTrade: (() -> Unit)?,
    showRange: Boolean,
    showHighLow: Boolean,
    showVolumeColumn: Boolean,
) {
    val theme = LocalAppTheme.current
    val accent by animateColorAsState(
        targetValue = if (quote.pctChange >= 0) theme.positive else theme.negative,
        animationSpec = tween(280),
        label = "watchAccent",
    )
    val meter by animateFloatAsState(
        targetValue = (kotlin.math.abs(quote.pctChange) / 4.0).toFloat().coerceIn(0.08f, 1f),
        animationSpec = tween(360),
        label = "watchMeter",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = 0.85f)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quote.symbol,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                StockNotesIcon(symbol = quote.symbol)
            }
            TickPriceText(
                price = quote.last,
                text = formatInr(quote.last, decimals = 2),
                style = MaterialTheme.typography.titleMedium,
                color = theme.text,
                fontWeight = FontWeight.Bold,
            )
            if (showRange) {
                SessionRangeBar(quote = quote)
            }
            val high = quote.dayHigh?.takeIf { it > 0.0 }?.let { formatInr(it, decimals = 2) } ?: "—"
            val low = quote.dayLow?.takeIf { it > 0.0 }?.let { formatInr(it, decimals = 2) } ?: "—"
            val vol = formatVolumeCompact(quote.volume)
            val board = buildList {
                if (showHighLow) add("H $high   L $low")
                if (showVolumeColumn) add("Vol $vol")
            }
            if (board.isNotEmpty()) {
                Text(
                    text = board.joinToString("   "),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = theme.textSecondary,
                )
            }
            Text(
                text = "Delayed print",
                fontSize = 10.sp,
                color = theme.textSecondary.copy(alpha = 0.7f),
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.textSecondary.copy(alpha = 0.14f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(meter)
                        .background(accent.copy(alpha = 0.7f)),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatSignedPct(quote.pctChange),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
            if (onTrade != null) {
                TextButton(
                    onClick = onTrade,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Practice", fontSize = 11.sp, color = theme.positive)
                }
            }
        }
    }
}

@Composable
private fun SessionRangeBar(quote: Quote) {
    val low = quote.dayLow
    val high = quote.dayHigh
    val last = quote.last
    if (low == null || high == null || high <= low || last <= 0.0) return
    val fraction = ((last - low) / (high - low)).toFloat().coerceIn(0f, 1f)
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth(0.72f)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(theme.textSecondary.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(theme.primary.copy(alpha = 0.75f)),
        )
    }
}
