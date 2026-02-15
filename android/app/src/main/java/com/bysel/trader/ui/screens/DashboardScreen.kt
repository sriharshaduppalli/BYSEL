package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote

@Composable
fun DashboardScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit
) {
    if (isLoading && holdings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.Blue)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "Portfolio Overview",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            // Portfolio Summary Card
            item {
                PortfolioSummaryCard(holdings, quotes)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Top Gainers
            item {
                Text(
                    text = "Top Gainers",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            items(quotes.sortedByDescending { it.pctChange }.take(3)) { quote ->
                GainerLosersCard(quote, isGainer = true) {
                    onTradeClick(quote.symbol)
                }
            }

            item {
                Text(
                    text = "Top Losers",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 0.dp)
                )
            }

            // Top Losers
            items(quotes.sortedBy { it.pctChange }.take(3)) { quote ->
                GainerLosersCard(quote, isGainer = false) {
                    onTradeClick(quote.symbol)
                }
            }

            if (error != null) {
                item {
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
            }
        }
    }
}

@Composable
fun PortfolioSummaryCard(holdings: List<Holding>, quotes: List<Quote>) {
    val totalValue = holdings.sumOf { it.qty * it.last }
    val totalInvested = holdings.sumOf { it.qty * it.avgPrice }
    val totalPnL = totalValue - totalInvested
    val totalPnLPercent = if (totalInvested > 0) (totalPnL / totalInvested) * 100 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Total Portfolio Value",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = "₹${String.format("%.2f", totalValue)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Invested",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalInvested)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "P&L",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalPnL)} (${String.format("%.2f", totalPnLPercent)}%)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalPnL >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color(0xFF2A2A2A)
            )

            Text(
                text = "Holdings: ${holdings.size} stocks",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun GainerLosersCard(quote: Quote, isGainer: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .background(
                        color = if (isGainer) Color(0xFF1B5E20).copy(alpha = 0.3f)
                        else Color(0xFFB71C1C).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGainer) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (isGainer) Color(0xFF00E676) else Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGainer) Color(0xFF00E676) else Color(0xFFFF5252)
                )
            }
        }
    }
}
