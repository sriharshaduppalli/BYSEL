package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.PortfolioHealthScore
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
                .background(Color(0xFF0D0D0D))
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Refresh", fontSize = 12.sp)
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
                    UpgradedQuoteCard(quote) { onQuoteClick(quote) }
                }
            }
        }
    }
}

@Composable
fun UpgradedQuoteCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (quote.pctChange > 0) Color(0xFF1B5E20).copy(alpha = 0.3f)
                                else Color(0xFFB71C1C).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (quote.pctChange > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (quote.pctChange > 0) Color(0xFF00E676) else Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (quote.pctChange > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                    }
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("View Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    portfolioHealth: PortfolioHealthScore?,
    healthLoading: Boolean,
    onRefresh: () -> Unit,
    onRefreshHealth: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onErrorDismiss: () -> Unit
) {
    LaunchedEffect(holdings) {
        if (holdings.isNotEmpty() && portfolioHealth == null) {
            onRefreshHealth()
        }
    }

    if (isLoading && holdings.isEmpty()) {
        LoadingScreen()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Refresh", fontSize = 12.sp)
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

            if (holdings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color(0xFF2A2A2A),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No holdings yet",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = "Start trading to build your portfolio",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // Portfolio Health Score Card
                    if (portfolioHealth != null || healthLoading) {
                        item {
                            PortfolioHealthCard(
                                health = portfolioHealth,
                                isLoading = healthLoading,
                                onRefresh = onRefreshHealth
                            )
                        }
                    }

                    items(holdings) { holding ->
                        UpgradedPortfolioHoldingItem(
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
fun UpgradedPortfolioHoldingItem(
    holding: Holding,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = holding.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.last)}",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "${if (holding.pnl > 0) "+" else ""}₹${String.format("%.2f", holding.pnl)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (holding.pnl > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFF2A2A2A)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Quantity",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "${holding.qty}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Avg Cost",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.avgPrice)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Current Value",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.qty * holding.last)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBuy,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B050)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Buy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSell,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sell", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PortfolioHealthCard(
    health: PortfolioHealthScore?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        if (isLoading && health == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing portfolio health...", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else if (health != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.HealthAndSafety,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Portfolio Health",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    // Grade badge
                    val gradeColor = when {
                        health.overallScore >= 75 -> Color(0xFF00C853)
                        health.overallScore >= 55 -> Color(0xFFFFB300)
                        health.overallScore >= 35 -> Color(0xFFFF9100)
                        else -> Color(0xFFE53935)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(gradeColor, gradeColor.copy(alpha = 0.3f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            health.grade,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Score bar
                val scoreColor = when {
                    health.overallScore >= 75 -> Color(0xFF00C853)
                    health.overallScore >= 55 -> Color(0xFFFFB300)
                    health.overallScore >= 35 -> Color(0xFFFF9100)
                    else -> Color(0xFFE53935)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { health.overallScore / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = scoreColor,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${health.overallScore}/100",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary
                Text(
                    health.summary,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                // Risk level
                Spacer(modifier = Modifier.height(8.dp))
                val riskColor = when (health.riskLevel) {
                    "low" -> Color(0xFF00C853)
                    "moderate" -> Color(0xFFFFB300)
                    "high" -> Color(0xFFFF9100)
                    else -> Color(0xFFE53935)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Risk: ${health.riskLevel.uppercase()}",
                        color = riskColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "${health.stockCount} stocks, ${health.sectorCount} sectors",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                // Suggestions (show first 3)
                if (health.suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Suggestions",
                        color = Color(0xFF7C4DFF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    health.suggestions.take(3).forEach { suggestion ->
                        Text(
                            suggestion,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
