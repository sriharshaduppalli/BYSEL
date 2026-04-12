package com.bysel.trader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.components.*
import com.bysel.trader.ui.theme.LocalAppTheme

/**
 * Premium Recommendations Screen
 * Modern, competitive with IndMoney, Zerodha, Univest
 */
@Composable
fun PremiumRecommendationsScreen(
    onSymbolClick: (String) -> Unit,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    var selectedTimeframe by remember { mutableStateOf("1dayBuy") }
    var selectedTab by remember { mutableStateOf(0) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedStockForDetails by remember { mutableStateOf<String?>(null) }
    
    // Mock recommendations data (in production, from API)
    val recommendations = listOf(
        RecommendationItem(
            symbol = "RELIANCE",
            companyName = "Reliance Industries",
            currentPrice = 2850.50,
            recommendation = "BUY",
            confidence = 78,
            oneDayTarget = 2950.0,
            oneMonthTarget = 3150.0,
            riskScore = 35,
            sector = "Energy"
        ),
        RecommendationItem(
            symbol = "TCS",
            companyName = "Tata Consultancy Services",
            currentPrice = 3720.25,
            recommendation = "STRONG_BUY",
            confidence = 85,
            oneDayTarget = 3850.0,
            oneMonthTarget = 4100.0,
            riskScore = 28,
            sector = "IT"
        ),
        RecommendationItem(
            symbol = "HDFCBANK",
            companyName = "HDFC Bank",
            currentPrice = 1745.80,
            recommendation = "BUY",
            confidence = 72,
            oneDayTarget = 1820.0,
            oneMonthTarget = 1950.0,
            riskScore = 32,
            sector = "Banking"
        ),
        RecommendationItem(
            symbol = "INFY",
            companyName = "Infosys",
            currentPrice = 1285.45,
            recommendation = "HOLD",
            confidence = 65,
            oneDayTarget = 1310.0,
            oneMonthTarget = 1380.0,
            riskScore = 42,
            sector = "IT"
        ),
        RecommendationItem(
            symbol = "WIPRO",
            companyName = "Wipro",
            currentPrice = 925.30,
            recommendation = "BUY",
            confidence = 71,
            oneDayTarget = 980.0,
            oneMonthTarget = 1050.0,
            riskScore = 38,
            sector = "IT"
        )
    )
    
    val tabs = listOf("1-Day Buy", "1-Month", "3-Month", "Sector Rotation")
    
    Scaffold(
        containerColor = theme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Smart Recommendations",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = theme.primary)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Settings, "Settings", tint = theme.primary)
                    }
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
            // Stats Row - Key Metrics
            PremiumStatsRow()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Navigation (Advanced)
            AdvancedTabNavigator(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content
            when (selectedTab) {
                0 -> {
                    RecommendationsList(
                        recommendations = recommendations.take(3),
                        onStockClick = { onSymbolClick(it) },
                        onDetailsClick = { 
                            selectedStockForDetails = it
                            showBottomSheet = true 
                        }
                    )
                }
                1 -> {
                    RecommendationsList(
                        recommendations = recommendations,
                        onStockClick = { onSymbolClick(it) },
                        onDetailsClick = { 
                            selectedStockForDetails = it
                            showBottomSheet = true 
                        }
                    )
                }
                2 -> {
                    RecommendationsList(
                        recommendations = recommendations,
                        onStockClick = { onSymbolClick(it) },
                        onDetailsClick = { 
                            selectedStockForDetails = it
                            showBottomSheet = true 
                        }
                    )
                }
                3 -> {
                    SectorRotationView()
                }
            }
        }
    }
    
    // Bottom Sheet for Stock Details
    if (showBottomSheet && selectedStockForDetails != null) {
        PremiumBottomSheet(
            isVisible = showBottomSheet,
            onDismiss = { showBottomSheet = false },
            title = "$selectedStockForDetails - Full Analysis"
        ) {
            StockDetailBottomSheetContent(selectedStockForDetails ?: "")
        }
    }
}

/**
 * Premium Stats Row showing key metrics
 */
@Composable
fun PremiumStatsRow() {
    val theme = LocalAppTheme.current
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(4) { index ->
            when (index) {
                0 -> AnimatedMetricCard(
                    label = "Win Rate",
                    value = "72",
                    unit = "%",
                    isPositive = true
                )
                1 -> AnimatedMetricCard(
                    label = "Sharpe Ratio",
                    value = "1.85",
                    isPositive = true
                )
                2 -> AnimatedMetricCard(
                    label = "Avg Return",
                    value = "4.2",
                    unit = "%",
                    isPositive = true
                )
                3 -> AnimatedMetricCard(
                    label = "Risk Score",
                    value = "35",
                    unit = "/100",
                    isPositive = false
                )
            }
        }
    }
}

/**
 * Recommendations List with Premium Cards
 */
@Composable
fun RecommendationsList(
    recommendations: List<RecommendationItem>,
    onStockClick: (String) -> Unit,
    onDetailsClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(recommendations) { rec ->
            PremiumStockRecommendationCard(
                symbol = rec.symbol,
                companyName = rec.companyName,
                currentPrice = rec.currentPrice,
                recommendation = rec.recommendation,
                confidence = rec.confidence,
                oneDayTarget = rec.oneDayTarget,
                oneMonthTarget = rec.oneMonthTarget,
                riskScore = rec.riskScore,
                onClick = { onStockClick(rec.symbol) }
            )
        }
    }
}

/**
 * Sector Rotation View
 */
@Composable
fun SectorRotationView() {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accumulate Sectors
        item {
            Text(
                text = "Accumulate (High Opportunity)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.positive,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        
        items(3) { index ->
            val sectors = listOf("IT", "Pharma", "Auto")
            val scores = listOf(82.5, 78.3, 75.2)
            SectorCard(sectors[index], scores[index], true)
        }
        
        // Hold Sectors
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Hold (Neutral)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        
        items(2) { index ->
            val sectors = listOf("Banking", "Finance")
            val scores = listOf(65.2, 62.8)
            SectorCard(sectors[index], scores[index], null)
        }
        
        // Reduce Sectors
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Reduce (Caution)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.negative,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        
        items(2) { index ->
            val sectors = listOf("Energy", "Metals")
            val scores = listOf(42.1, 38.7)
            SectorCard(sectors[index], scores[index], false)
        }
    }
}

/**
 * Sector Card Component
 */
@Composable
fun SectorCard(name: String, score: Double, isAccumulate: Boolean?) {
    val theme = LocalAppTheme.current
    
    val bgColor = when {
        isAccumulate == true -> theme.positive.copy(alpha = 0.1f)
        isAccumulate == false -> theme.negative.copy(alpha = 0.1f)
        else -> theme.primary.copy(alpha = 0.1f)
    }
    
    val textColor = when {
        isAccumulate == true -> theme.positive
        isAccumulate == false -> theme.negative
        else -> theme.primary
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = bgColor
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
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
                Text(
                    text = "Score: $score/100",
                    fontSize = 12.sp,
                    color = theme.textSecondary
                )
            }
            
            // Score Badge
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(textColor.copy(alpha = 0.2f)),
                color = textColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${score.toInt()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Stock Detail Bottom Sheet Content
 */
@Composable
fun StockDetailBottomSheetContent(symbol: String) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Key Metrics Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricBox("Entry", "₹2850", theme.primary, Modifier.weight(1f))
            MetricBox("1D High", "₹2950", theme.positive, Modifier.weight(1f))
            MetricBox("1D Low", "₹2780", theme.negative, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Risk/Reward Analysis
        Text(
            text = "Risk/Reward Analysis",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = theme.text,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Stop Loss", fontSize = 11.sp, color = theme.textSecondary)
                Text("₹2780", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Risk/Reward", fontSize = 11.sp, color = theme.textSecondary)
                Text("1:2.4", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.positive)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Position Size", fontSize = 11.sp, color = theme.textSecondary)
                Text("3.5%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.primary)
            }
        }
    }
}

/**
 * Helper Metric Box for displaying key values
 */
@Composable
fun MetricBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f)),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = theme.textSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * Data class for recommendation item
 */
data class RecommendationItem(
    val symbol: String,
    val companyName: String,
    val currentPrice: Double,
    val recommendation: String,
    val confidence: Int,
    val oneDayTarget: Double,
    val oneMonthTarget: Double,
    val riskScore: Int,
    val sector: String
)
