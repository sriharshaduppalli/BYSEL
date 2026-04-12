package com.bysel.trader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.components.ConfidenceCard
import com.bysel.trader.ui.components.PredictionReasoningCard
import com.bysel.trader.ui.components.EventRiskCard
import com.bysel.trader.ui.components.SentimentCard
import com.bysel.trader.ui.components.QueryUnderstandingCard
import com.bysel.trader.ui.components.ModernTabNavigation
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.data.models.SentimentBreakdown

/**
 * Premium Stock Detail Screen
 * Comprehensive stock analysis and trading recommendation view
 */
@Composable
fun PremiumStockDetailScreen(
    symbol: String = "RELIANCE",
    onNavigateBack: () -> Unit = {},
    onBuyClick: (String) -> Unit = {}
) {
    val theme = LocalAppTheme.current
    var selectedAnalysisTab by remember { mutableStateOf(0) }
    var isWatchlistAdded by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = theme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        symbol,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = theme.text)
                    }
                },
                actions = {
                    IconButton(onClick = { isWatchlistAdded = !isWatchlistAdded }) {
                        Icon(
                            if (isWatchlistAdded) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                            "Add to Watchlist",
                            tint = if (isWatchlistAdded) Color.Red else theme.textSecondary
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, "Share", tint = theme.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.card,
                    scrolledContainerColor = theme.card
                ),
                modifier = Modifier.shadow(4.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = theme.primary,
                contentColor = theme.text
            ) {
                Icon(Icons.Default.ShoppingCart, "Buy")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { StockPriceHeader(symbol) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { RecommendationSummary() }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { AnalysisTabs(selectedAnalysisTab) { selectedAnalysisTab = it } }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            
            when (selectedAnalysisTab) {
                0 -> item { TradingLevelsSection() }
                1 -> item { RiskAnalysisSection() }
                2 -> item { TechnicalAnalysisSection() }
                3 -> item { AiAnalysisSection(symbol) }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
    
    // Buy/Trade Bottom Sheet - TODO: Implement with ModalBottomSheet
    // if (showBottomSheet) {
    //     ModalBottomSheet(
    //         onDismissRequest = { showBottomSheet = false }
    //     ) {
    //         TradeSetupBottomSheet(symbol, onBuyClick, { showBottomSheet = false })
    //     }
    // }
}

/**
 * Stock Price Header - Main price display and key metrics
 */
@Composable
fun StockPriceHeader(symbol: String) {
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
            // Company Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Reliance Industries",
                        fontSize = 14.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        "Energy · NIFTY 50",
                        fontSize = 11.sp,
                        color = theme.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.positive.copy(alpha = 0.1f)),
                    color = theme.positive.copy(alpha = 0.1f)
                ) {
                    Text(
                        "BUY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.positive,
                        modifier = Modifier.padding(6.dp, 4.dp)
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = theme.primary.copy(alpha = 0.1f))
            
            // Price Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "₹2,850.50",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                    Text(
                        "Current Price",
                        fontSize = 11.sp,
                        color = theme.textSecondary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "+₹104.75",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.positive
                    )
                    Text(
                        "+3.80%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.positive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Key Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceStatBox("52W High", "₹3,120", Modifier.weight(1f))
                PriceStatBox("52W Low", "₹2,150", Modifier.weight(1f))
                PriceStatBox("Volume", "2.5B", Modifier.weight(1f))
            }
        }
    }
}

/**
 * Recommendation Summary Badge
 */
@Composable
fun RecommendationSummary() {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = theme.primary.copy(alpha = 0.08f)
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
                    "Action Confidence",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
                Text(
                    "78% Buy Signal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primary
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Target Prices",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
                Text(
                    "1D: ₹2,950 | 1M: ₹3,100",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
            }
        }
    }
}

/**
 * Analysis Tabs Navigation
 */
@Composable
fun AnalysisTabs(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Levels", "Risk", "Technical", "AI Analysis")
    
    ModernTabNavigation(
        tabs = tabs,
        selectedTabIndex = selectedTab,
        onTabSelected = onTabChange,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

/**
 * Trading Levels Section - Entry, SL, TP
 */
@Composable
fun TradingLevelsSection() {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Entry Level
        LevelCard(
            title = "Entry Level",
            price = "₹2,850 - ₹2,900",
            description = "Buy on dips within this range",
            color = theme.primary
        )
        
        // Stop Loss
        LevelCard(
            title = "Stop Loss",
            price = "₹2,780",
            description = "Hard stop-loss for risk management",
            color = theme.negative
        )
        
        // Take Profit Levels
        LevelCard(
            title = "Target 1 (TP1)",
            price = "₹2,950 (+3.5%)",
            description = "Book 50% profit here",
            color = theme.positive
        )
        
        LevelCard(
            title = "Target 2 (TP2)",
            price = "₹3,100 (+8.8%)",
            description = "Book remaining 50% here",
            color = theme.positive
        )
        
        // Risk/Reward
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)),
            color = theme.card
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Risk/Reward Ratio",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RRBox("Risk", "₹70", "2.5%", theme.negative, Modifier.weight(1f))
                    RRBox("Reward", "₹250", "8.8%", theme.positive, Modifier.weight(1f))
                    RRBox("Ratio", "1:3.6", "-", theme.primary, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Risk Analysis Section
 */
@Composable
fun RiskAnalysisSection() {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RiskMetricCard(
                title = "Volatility Score",
                value = "42/100",
                description = "Moderate volatility",
                color = theme.textSecondary
            )
        }
        
        item {
            RiskMetricCard(
                title = "Drawdown Risk",
                value = "8.5%",
                description = "Maximum expected drawdown",
                color = theme.negative
            )
        }
        
        item {
            RiskMetricCard(
                title = "Recovery Time",
                value = "12-14 days",
                description = "Expected recovery period",
                color = theme.primary
            )
        }
        
        item {
            RiskMetricCard(
                title = "Position Size",
                value = "3.5% Portfolio",
                description = "Recommended allocation",
                color = theme.primary
            )
        }
        
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)),
                color = theme.card
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Risk Factors",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    
                    listOf(
                        "Global crude prices impacting margins",
                        "Rupee volatility (USD/INR)",
                        "Earnings revision risk",
                        "Q3 results scheduled: Jan 28"
                    ).forEach { risk ->
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = theme.negative.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(risk, fontSize = 12.sp, color = theme.text)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Technical Analysis Section
 */
@Composable
fun TechnicalAnalysisSection() {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TechnicalIndicatorCard(
                name = "RSI (14)",
                value = "65",
                status = "Bullish",
                description = "Above 50 suggests uptrend",
                color = theme.positive
            )
        }
        
        item {
            TechnicalIndicatorCard(
                name = "MACD",
                value = "Positive",
                status = "Buy Signal",
                description = "Golden cross detected",
                color = theme.positive
            )
        }
        
        item {
            TechnicalIndicatorCard(
                name = "Moving Average (20)",
                value = "₹2,820",
                status = "Above",
                description = "Price trading above MA",
                color = theme.positive
            )
        }
        
        item {
            TechnicalIndicatorCard(
                name = "Support",
                value = "₹2,780",
                status = "Strong",
                description = "Previous swing low",
                color = theme.primary
            )
        }
    }
}

/**
 * News and Events Section
 */
@Composable
fun NewsAndEventsSection() {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NewsEventCard(
                date = "Jan 28",
                title = "Q3 FY24 Results Expected",
                impact = "High",
                impactColor = theme.negative
            )
        }
        
        item {
            NewsEventCard(
                date = "Jan 15",
                title = "Global Oil Prices Hit 6-Month High",
                impact = "Positive",
                impactColor = theme.positive
            )
        }
        
        item {
            NewsEventCard(
                date = "Jan 10",
                title = "Major Stake Purchase Announcement",
                impact = "Positive",
                impactColor = theme.positive
            )
        }
        
        item {
            NewsEventCard(
                date = "Jan 5",
                title = "Regulatory Update: New Tax Policy",
                impact = "Neutral",
                impactColor = theme.textSecondary
            )
        }
    }
}

/**
 * AI Analysis Section - Enhanced AI Features
 */
@Composable
fun AiAnalysisSection(symbol: String) {
    val theme = LocalAppTheme.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Query Understanding
        item {
            // QueryUnderstandingCard(
            //     queryUnderstanding = QueryUnderstanding(
            //         intents = listOf("price_prediction", "risk_analysis"),
            //         timeframe = TimeframeAnalysis(phrase = "medium-term", days = 90),
            //         confidence = 0.85
            //     )
            // )
        }
        
        // Confidence Breakdown
        item {
            ConfidenceCard(
                overallConfidence = 78.0,
                confidenceLevel = "High",
                factors = mapOf(
                    "technical" to mapOf(
                        "score" to 85.0,
                        "weight" to 0.4,
                        "reasoning" to "Strong technical indicators with golden cross"
                    ),
                    "fundamental" to mapOf(
                        "score" to 72.0,
                        "weight" to 0.3,
                        "reasoning" to "Solid balance sheet and earnings growth"
                    ),
                    "sentiment" to mapOf(
                        "score" to 80.0,
                        "weight" to 0.2,
                        "reasoning" to "Positive news coverage and analyst upgrades"
                    ),
                    "risk" to mapOf(
                        "score" to 65.0,
                        "weight" to 0.1,
                        "reasoning" to "Moderate volatility with upcoming earnings"
                    )
                )
            )
        }
        
        // Prediction Reasoning
        item {
            PredictionReasoningCard(
                symbol = symbol,
                signal = "STRONG_BUY",
                whyConfident = "Multiple technical indicators align for upward momentum. RSI shows room for growth, MACD indicates bullish crossover, and price action confirms support levels. Fundamental analysis reveals strong earnings potential with positive industry trends.",
                caveats = listOf(
                    "⚠️ Earnings report due next week may cause volatility",
                    "📉 Global market conditions could impact energy sector",
                    "💰 Consider position sizing due to moderate volatility"
                )
            )
        }
        
        // Event Risk Analysis
        item {
            EventRiskCard(
                baseConfidence = 82.0,
                adjustedConfidence = 78.0,
                adjustmentFactor = -4.0,
                eventRisks = listOf(
                    "📊 Q3 earnings announcement in 5 days",
                    "🛢️ OPEC meeting next week may affect oil prices",
                    "💼 Board meeting scheduled for end of month"
                )
            )
        }
        
        // Sentiment Analysis
        // item {
        //     SentimentCard(
        //         overallSentiment = "positive",
        //         score = 0.65,
        //         strength = "strong",
        //         breakdown = SentimentBreakdown(
        //             positiveCount = 45,
        //             neutralCount = 12,
        //             negativeCount = 8
        //         ),
        //         interpretation = "Strong positive sentiment driven by recent analyst upgrades and positive earnings outlook. News coverage is predominantly bullish with focus on growth potential."
        //     )
        // }
    }
}

/**
 * Helper Composables
 */

@Composable
fun LevelCard(
    title: String,
    price: String,
    description: String,
    color: Color
) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, color = theme.textSecondary)
                Text(price, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.text)
                Text(description, fontSize = 10.sp, color = theme.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                color = color.copy(alpha = 0.15f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RRBox(label: String, value: String, percentage: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f)),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = Color.Gray)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            if (percentage != "-") {
                Text(percentage, fontSize = 9.sp, color = color)
            }
        }
    }
}

@Composable
fun PriceStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
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
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.text)
        }
    }
}

@Composable
fun RiskMetricCard(title: String, value: String, description: String, color: Color) {
    val theme = LocalAppTheme.current
    
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
                Text(title, fontSize = 11.sp, color = theme.textSecondary)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                Text(description, fontSize = 10.sp, color = theme.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun TechnicalIndicatorCard(name: String, value: String, status: String, description: String, color: Color) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 11.sp, color = theme.textSecondary)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.text)
                Text(description, fontSize = 10.sp, color = theme.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    status,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(6.dp, 4.dp)
                )
            }
        }
    }
}

@Composable
fun NewsEventCard(date: String, title: String, impact: String, impactColor: Color) {
    val theme = LocalAppTheme.current
    
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
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(date, fontSize = 10.sp, color = theme.textSecondary)
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = theme.text, modifier = Modifier.padding(top = 4.dp))
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(impactColor.copy(alpha = 0.15f)),
                color = impactColor.copy(alpha = 0.15f)
            ) {
                Text(
                    impact,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = impactColor,
                    modifier = Modifier.padding(6.dp, 3.dp)
                )
            }
        }
    }
}

/**
 * Trade Setup Bottom Sheet Content
 */
@Composable
fun TradeSetupBottomSheet(
    symbol: String,
    onBuyClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Trade Type Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TradeTypeButton("BUY", true, Modifier.weight(1f))
            TradeTypeButton("SELL", false, Modifier.weight(1f))
        }
        
        // Quantity Input
        Column {
            Text("Quantity", fontSize = 12.sp, color = theme.textSecondary)
            TextField(
                value = "10",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = theme.card,
                    unfocusedContainerColor = theme.card
                )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Order Summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total Amount", fontSize = 12.sp, color = theme.textSecondary)
            Text("₹28,505", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.text)
        }
        
        // Action Buttons
        Button(
            onClick = {
                onBuyClick(symbol)
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
        ) {
            Text("Place Order", color = theme.text)
        }
        
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun TradeTypeButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val color = if (label == "BUY") theme.positive else theme.negative
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.15f)
                else theme.card
            ),
        color = if (isSelected) color.copy(alpha = 0.15f) else theme.card
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) color else theme.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}
