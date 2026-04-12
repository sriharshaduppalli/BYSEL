package com.bysel.trader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.components.*
import com.bysel.trader.ui.theme.LocalAppTheme
import kotlin.math.abs

/**
 * Premium Trader Dashboard
 * Modern UI inspired by Zerodha, IndMoney, and Univest
 */
@Composable
fun PremiumDashboardScreen(
    onNavigateToRecommendations: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onSymbolClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    var selectedSection by remember { mutableStateOf(0) }
    
    Scaffold(
        containerColor = theme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Trading Dashboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.card,
                    scrolledContainerColor = theme.card
                ),
                modifier = Modifier.shadow(4.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Portfolio Summary Card
            PortfolioSummaryCard()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Key Metrics Bar
            KeyMetricsBar()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Navigation Tabs
            DashboardTabs(
                selectedTab = selectedSection,
                onTabChange = { selectedSection = it }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content based on selected tab
            when (selectedSection) {
                0 -> TodaysTopMoversSection(onSymbolClick)
                1 -> PortfolioHoldingsSection(onNavigateToPortfolio)
                2 -> WatchlistSection(onNavigateToWatchlist)
                3 -> PerformanceAnalyticsSection()
            }
        }
    }
}

/**
 * Portfolio Summary Card - Shows overall portfolio health
 */
@Composable
fun PortfolioSummaryCard() {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)),
        color = theme.card
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Portfolio Value",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        "₹5,24,850",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.positive.copy(alpha = 0.1f)),
                    color = theme.positive.copy(alpha = 0.1f)
                ) {
                    Text(
                        "+₹12,450\n+2.43%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.positive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar showing portfolio allocation
            PremiumProgressBar(
                progress = 0.75f,
                label = "Invested"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Key stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill("Holdings", "24", Modifier.weight(1f))
                StatPill("Sectors", "9", Modifier.weight(1f))
                StatPill("Avg Return", "12.5%", Modifier.weight(1f))
            }
        }
    }
}

/**
 * Key Metrics Bar - Quick overview of market status
 */
@Composable
fun KeyMetricsBar() {
    val theme = LocalAppTheme.current
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(4) { index ->
            when (index) {
                0 -> MetricPill("NIFTY50", "24,850", true)
                1 -> MetricPill("SENSEX", "81,250", true)
                2 -> MetricPill("BANKNIFTY", "52,380", false)
                3 -> MetricPill("MIDCAP", "13,920", true)
            }
        }
    }
}

/**
 * Dashboard Tab Navigation
 */
@Composable
fun DashboardTabs(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Today's Moves", "Holdings", "Watchlist", "Analytics")
    
    ModernTabNavigation(
        tabs = tabs,
        selectedTabIndex = selectedTab,
        onTabSelected = onTabChange,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

/**
 * Today's Top Movers Section
 */
@Composable
fun TodaysTopMoversSection(onSymbolClick: (String) -> Unit) {
    val theme = LocalAppTheme.current
    
    val topMovers = listOf(
        TopMover("RELIANCE", 2850.50, 2950.25, 104.75),
        TopMover("TCS", 3720.25, 3800.00, 79.75),
        TopMover("INFY", 1285.45, 1320.50, 35.05),
        TopMover("HDFC", 2145.80, 2180.00, 34.20),
        TopMover("WIPRO", 925.30, 945.50, 20.20)
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(topMovers) { mover ->
            TopMoverCard(
                symbol = mover.symbol,
                currentPrice = mover.currentPrice,
                dayHigh = mover.dayHigh,
                change = mover.change,
                onSymbolClick = onSymbolClick
            )
        }
    }
}

/**
 * Top Mover Card Component
 */
@Composable
fun TopMoverCard(
    symbol: String,
    currentPrice: Double,
    dayHigh: Double,
    change: Double,
    onSymbolClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    val changePercent = (change / currentPrice * 100)
    val isPositive = change >= 0
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSymbolClick(symbol) }
            .shadow(2.dp, RoundedCornerShape(14.dp)),
        color = if (isPositive) theme.positive.copy(alpha = 0.08f)
        else theme.negative.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
                Text(
                    "₹${String.format("%.2f", currentPrice)}",
                    fontSize = 12.sp,
                    color = theme.textSecondary
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.2f", change)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) theme.positive else theme.negative
                )
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.2f", changePercent)}%",
                    fontSize = 11.sp,
                    color = if (isPositive) theme.positive else theme.negative
                )
            }
        }
    }
}

/**
 * Portfolio Holdings Section
 */
@Composable
fun PortfolioHoldingsSection(onNavigateToPortfolio: () -> Unit) {
    val theme = LocalAppTheme.current
    
    val holdings = listOf(
        Holding("RELIANCE", "5 Shares", 2850.50, 14252.50, 2.71),
        Holding("TCS", "3 Shares", 3720.25, 11160.75, 1.39),
        Holding("HDFCBANK", "8 Shares", 1745.80, 13966.40, 2.66),
        Holding("INFY", "10 Shares", 1285.45, 12854.50, 2.45)
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(holdings) { holding ->
            HoldingCard(holding)
        }
        
        item {
            Button(
                onClick = onNavigateToPortfolio,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                Text("View Full Portfolio", color = theme.text)
            }
        }
    }
}

/**
 * Holding Card Component
 */
@Composable
fun HoldingCard(holding: Holding) {
    val theme = LocalAppTheme.current
    val isPositive = holding.returnPercent >= 0
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
        color = theme.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    holding.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
                Text(
                    holding.quantity,
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "₹${String.format("%.2f", holding.currentValue)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.2f", holding.returnPercent)}%",
                    fontSize = 11.sp,
                    color = if (isPositive) theme.positive else theme.negative
                )
            }
        }
    }
}

/**
 * Watchlist Section
 */
@Composable
fun WatchlistSection(onNavigateToWatchlist: () -> Unit) {
    val theme = LocalAppTheme.current
    
    val watchlistItems = listOf(
        WatchlistItem("MARUTI", 5850.00, 5920.00, 70.00),
        WatchlistItem("BAJAJFINSV", 1520.50, 1580.00, 59.50),
        WatchlistItem("SBILIFE", 2140.00, 2200.00, 60.00),
        WatchlistItem("LTIM", 4280.50, 4350.00, 69.50)
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(watchlistItems) { item ->
            WatchlistItemCard(item)
        }
        
        item {
            Button(
                onClick = onNavigateToWatchlist,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                Text("View All Watchlist", color = theme.text)
            }
        }
    }
}

/**
 * Watchlist Item Card
 */
@Composable
fun WatchlistItemCard(item: WatchlistItem) {
    val theme = LocalAppTheme.current
    
    AnimatedPriceTicker(
        symbol = item.symbol,
        price = item.currentPrice,
        change = item.dayHigh - item.currentPrice,
        changePercent = ((item.dayHigh - item.currentPrice) / item.currentPrice * 100)
    )
}

/**
 * Performance Analytics Section
 */
@Composable
fun PerformanceAnalyticsSection() {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            AnalyticsCard(
                title = "Win Rate",
                value = "72%",
                subtitle = "Based on 25 trades",
                color = theme.positive
            )
        }
        
        item {
            AnalyticsCard(
                title = "Average Return",
                value = "4.2%",
                subtitle = "Per trade",
                color = theme.primary
            )
        }
        
        item {
            AnalyticsCard(
                title = "Sharpe Ratio",
                value = "1.85",
                subtitle = "Risk-adjusted returns",
                color = theme.positive
            )
        }
        
        item {
            AnalyticsCard(
                title = "Max Drawdown",
                value = "-8.5%",
                subtitle = "Portfolio decline",
                color = theme.negative
            )
        }
    }
}

/**
 * Analytics Card Component
 */
@Composable
fun AnalyticsCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    title,
                    fontSize = 12.sp,
                    color = theme.textSecondary
                )
                Text(
                    value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    "✓",
                    fontSize = 28.sp,
                    color = color,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * Stat Pill Component
 */
@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.primary.copy(alpha = 0.08f)),
        color = theme.primary.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = theme.textSecondary)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.primary)
        }
    }
}

/**
 * Metric Pill for Index/Market Data
 */
@Composable
fun MetricPill(label: String, value: String, isPositive: Boolean) {
    val theme = LocalAppTheme.current
    val color = if (isPositive) theme.positive else theme.negative
    
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f)),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = theme.textSecondary)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.text)
            Text(
                "${if (isPositive) "+" else "-"}1.2%",
                fontSize = 9.sp,
                color = color
            )
        }
    }
}

// Data classes
data class TopMover(val symbol: String, val currentPrice: Double, val dayHigh: Double, val change: Double)
data class Holding(val symbol: String, val quantity: String, val pricePerUnit: Double, val currentValue: Double, val returnPercent: Double)
data class WatchlistItem(val symbol: String, val currentPrice: Double, val dayHigh: Double, val recommendedPrice: Double)
