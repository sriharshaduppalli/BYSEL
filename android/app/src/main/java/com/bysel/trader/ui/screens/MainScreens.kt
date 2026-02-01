package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.ui.components.QuoteCard
import com.bysel.trader.ui.components.ErrorScreen
import com.bysel.trader.ui.components.LoadingScreen

@Composable
fun WatchlistScreen(
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onErrorDismiss: () -> Unit
) {
    if (isLoading && quotes.isEmpty()) {
        LoadingScreen()
    } else if (error != null && quotes.isEmpty()) {
        ErrorScreen(error) { onRefresh() }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Watchlist",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text("Refresh")
                }
            }

            if (error != null) {
                Snackbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = onErrorDismiss) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(quotes) { quote ->
                    QuoteCard(quote) { onQuoteClick(quote) }
                }
            }
        }
    }
}

@Composable
fun PortfolioScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onErrorDismiss: () -> Unit
) {
    if (isLoading && holdings.isEmpty()) {
        LoadingScreen()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Portfolio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text("Refresh")
                }
            }

            if (holdings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No holdings yet",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(holdings) { holding ->
                        PortfolioHoldingItem(
                            holding = holding,
                            onBuy = { onBuy(holding.symbol, 1) },
                            onSell = { onSell(holding.symbol, 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioHoldingItem(
    holding: Holding,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = holding.symbol,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${if (holding.pnl > 0) "+" else ""}${holding.pnl}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (holding.pnl > 0) Color.Green else Color.Red
                )
            }
            Text(
                text = "Qty: ${holding.qty} | Avg: ₹${holding.avgPrice}",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBuy,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Buy", fontSize = 12.sp)
                }
                Button(
                    onClick = onSell,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Sell", fontSize = 12.sp)
                }
            }
        }
    }
}
