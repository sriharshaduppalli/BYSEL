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
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun WatchlistWidget(
    isPinned: Boolean,
    quotes: List<Quote>,
    onPinClick: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onTradeClick: ((Quote) -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    var sort by rememberSaveable { mutableStateOf(WatchlistSortMode.MOVE.name) }
    val selectedSort = remember(sort) {
        runCatching { WatchlistSortMode.valueOf(sort) }.getOrDefault(WatchlistSortMode.MOVE)
    }
    val sortedQuotes = remember(quotes, selectedSort) {
        quotes.sortedByWatchlistMode(selectedSort).take(6)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.card)
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
                    text = "My Watchlist",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(
                    text = if (quotes.isEmpty()) {
                        "Add names from Search or Trade — full NSE catalog."
                    } else {
                        "${quotes.size} tracked · ${selectedSort.label.lowercase()}"
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

        if (quotes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (sortedQuotes.isEmpty()) {
                Text(
                    text = "Your watchlist is empty. Search any listed NSE stock, tap Watch, then return here.",
                    fontSize = 14.sp,
                    color = theme.textSecondary,
                )
            } else {
                sortedQuotes.forEachIndexed { index, quote ->
                    WatchRow(
                        quote = quote,
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
            Text(
                text = quote.symbol,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.text,
            )
            Text(
                text = formatInr(quote.last, decimals = 2),
                fontSize = 12.sp,
                color = theme.textSecondary,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Open", fontSize = 11.sp, color = theme.primary)
                }
                if (onTrade != null) {
                    TextButton(
                        onClick = onTrade,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Trade", fontSize = 11.sp, color = theme.positive)
                    }
                }
            }
        }
    }
}
