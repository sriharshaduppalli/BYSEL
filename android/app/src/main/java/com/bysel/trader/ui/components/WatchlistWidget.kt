package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun WatchlistWidget(
    isPinned: Boolean,
    quotes: List<Quote>,
    onPinClick: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppTheme.current.card)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Market Watch",
                    fontSize = 20.sp,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = if (quotes.isEmpty()) "No live symbols on the board yet." else "Fast access to the symbols shaping this session.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
            }
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin Watchlist" else "Pin Watchlist",
                    tint = if (isPinned) LocalAppTheme.current.positive else LocalAppTheme.current.textSecondary
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (quotes.isEmpty()) {
                Text(
                    text = "Pin symbols or refresh Home to populate a live market watch board.",
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            } else {
                quotes.take(4).forEachIndexed { index, quote ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuoteClick(quote) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quote.symbol,
                                fontSize = 15.sp,
                                color = LocalAppTheme.current.text,
                            )
                            Text(
                                text = "₹${String.format("%.2f", quote.last)}",
                                fontSize = 12.sp,
                                color = LocalAppTheme.current.textSecondary,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildString {
                                    if (quote.pctChange > 0) append("+")
                                    append(String.format("%.2f", quote.pctChange))
                                    append("%")
                                },
                                fontSize = 13.sp,
                                color = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                            )
                            TextButton(onClick = { onQuoteClick(quote) }) {
                                Text("Open")
                            }
                        }
                    }
                    if (index < quotes.take(4).lastIndex) {
                        HorizontalDivider(color = LocalAppTheme.current.textSecondary.copy(alpha = 0.16f))
                    }
                }
            }
        }
    }
}
