package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Alert
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.TickPriceText
import com.bysel.trader.ui.theme.animatedChangeColor
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.colorForChange

/**
 * Shared market cards.
 *
 * Visuals come from [LocalAppTheme] / MaterialTheme (typography, shapes, card colors).
 * Interaction (click / delete) stays on callbacks + Material clickable surfaces — not in visuals.
 * Pass [modifier] for layout placement from the caller (padding, weight, test tags).
 */

@Composable
fun QuoteCard(
    quote: Quote,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val theme = LocalAppTheme.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = byselCardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = quote.symbol,
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.text,
                )
                TickPriceText(
                    price = quote.last,
                    text = "₹${quote.last}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                    fontWeight = null,
                )
            }
            Text(
                text = "${if (quote.pctChange > 0) "+" else ""}${quote.pctChange}%",
                style = MaterialTheme.typography.titleMedium,
                color = animatedChangeColor(quote.pctChange),
            )
        }
    }
}

@Composable
fun HoldingCard(
    holding: Holding,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = byselCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = holding.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.text,
                    )
                    Text(
                        text = "Qty: ${holding.qty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                    )
                }
                Text(
                    text = "₹${holding.pnl}",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.colorForChange(holding.pnl),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Avg: ₹${holding.avgPrice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                )
                Text(
                    text = "Last: ₹${holding.last}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                )
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = byselCardColors(),
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
                    text = "${alert.symbol} ${alert.alertType}",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                )
                Text(
                    text = "₹${alert.thresholdPrice}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                )
            }
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .width(60.dp)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.negative,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = LocalAppTheme.current.primary)
    }
}

@Composable
fun ErrorScreen(
    error: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                color = theme.negative,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
            ) {
                Text("Retry")
            }
        }
    }
}
