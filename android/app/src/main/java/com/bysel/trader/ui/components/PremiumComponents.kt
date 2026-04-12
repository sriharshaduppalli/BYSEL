package com.bysel.trader.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay

/**
 * Premium Glassmorphism Card - Modern frosted glass effect
 * Ideal for recommendation cards with transparency and blur
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    val cardColor = theme.card.copy(alpha = 0.85f)
    val borderColor = theme.primary.copy(alpha = 0.2f)
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(24.dp),
    ) {
        content()
    }
}

/**
 * Premium Recommendation Card with Risk/Reward Visualization
 * Displays stock recommendation with all metrics investors need
 */
@Composable
fun PremiumStockRecommendationCard(
    symbol: String,
    companyName: String,
    currentPrice: Double,
    recommendation: String,
    confidence: Int,
    oneDayTarget: Double,
    oneMonthTarget: Double,
    riskScore: Int,
    onClick: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val isPositiveRecommendation = recommendation in listOf("BUY", "STRONG_BUY")
    val recommendationColor = if (isPositiveRecommendation) theme.positive else theme.negative
    
    GlassmorphismCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(220.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header: Symbol + Recommendation Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = symbol,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                    Text(
                        text = companyName,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        maxLines = 1
                    )
                }
                
                // Recommendation Badge with confidence
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(recommendationColor.copy(alpha = 0.2f)),
                    color = recommendationColor.copy(alpha = 0.2f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = recommendation,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = recommendationColor
                        )
                        Text(
                            text = "$confidence%",
                            fontSize = 10.sp,
                            color = recommendationColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Current Price + 1-Day Target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current",
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", currentPrice)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                }
                
                // Arrow + 1-Day Target
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (oneDayTarget > currentPrice) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = "Trend",
                        tint = if (oneDayTarget > currentPrice) theme.positive else theme.negative,
                        modifier = Modifier.size(20.dp)
                    )
                    val gain1d = ((oneDayTarget - currentPrice) / currentPrice * 100)
                    Text(
                        text = "${String.format("%.1f%%", gain1d)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (oneDayTarget > currentPrice) theme.positive else theme.negative
                    )
                }
                
                // Month Target
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "1M Target",
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.0f", oneMonthTarget)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primary
                    )
                    val gainMonth = ((oneMonthTarget - currentPrice) / currentPrice * 100)
                    Text(
                        text = "+${String.format("%.1f%%", gainMonth)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.positive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Risk Score Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Risk:",
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                
                // Risk progress bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(theme.textSecondary.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(riskScore / 100f)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = if (riskScore < 40) 
                                        listOf(theme.positive, theme.positive) 
                                    else if (riskScore < 70)
                                        listOf(Color(0xFFFFA726), Color(0xFFFFA726))
                                    else
                                        listOf(theme.negative, theme.negative)
                                )
                            )
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
                
                Text(
                    text = "$riskScore",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text
                )
            }
        }
    }
}

/**
 * Modern Tab Navigation with smooth animations
 * Replaces standard tab bar with modern design
 */
@Composable
fun ModernTabNavigation(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = theme.card,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTabIndex
                
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) },
                    color = if (isSelected) theme.primary else theme.surface,
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else theme.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedTabNavigator(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .background(theme.surface),
        color = theme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedTabIndex

                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onTabSelected(index) },
                    color = if (isSelected) Color.Transparent else theme.card,
                    shadowElevation = if (isSelected) 8.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected)
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            theme.primary.copy(alpha = 0.95f),
                                            theme.primary.copy(alpha = 0.65f)
                                        )
                                    )
                                else
                                    Brush.horizontalGradient(
                                        colors = listOf(theme.card, theme.card)
                                    )
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else theme.textSecondary
                        )
                    }
                }
            }
        }
    }
}


/**
 * Premium Bottom Sheet with smooth animations
 * For displaying detailed information about stocks
 */
@Composable
fun PremiumBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    
    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(theme.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent propagation
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle bar
                Surface(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = theme.textSecondary.copy(alpha = 0.5f)
                ) {}
                
                // Title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Animated Metric Display Card
 * Shows values with smooth animations
 */
@Composable
fun AnimatedMetricCard(
    label: String,
    value: String,
    unit: String = "",
    isPositive: Boolean? = null,
    icon: @Composable (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 30 })
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Text(
                text = label,
                fontSize = 11.sp,
                color = theme.textSecondary,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isPositive == true -> theme.positive
                        isPositive == false -> theme.negative
                        else -> theme.text
                    }
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Premium Progress Bar with gradient
 * Smooth animated progress indicator
 */
@Composable
fun PremiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    showPercentage: Boolean = true
) {
    val theme = LocalAppTheme.current
    
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(theme.surface)
                .border(1.dp, theme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(theme.primary, theme.primary.copy(alpha = 0.7f))
                        )
                    )
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        
        if (showPercentage) {
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = theme.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Stock Price Animation - Animated price ticker
 * Shows buying/selling pressure through animation
 */
@Composable
fun AnimatedPriceTicker(
    symbol: String,
    price: Double,
    change: Double,
    changePercent: Double,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val isPositive = changePercent >= 0
    val backgroundColor = (if (isPositive) theme.positive else theme.negative).copy(alpha = 0.1f)
    
    var animationValue by remember { mutableStateOf(0f) }
    
    LaunchedEffect(price) {
        animationValue = 1f
    }
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = symbol,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "₹${String.format("%.2f", price)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                    Text(
                        text = "${String.format("+%.2f", change)} (${String.format("%.2f", changePercent)}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositive) theme.positive else theme.negative
                    )
                }
                
                // Animated change indicator
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isPositive) theme.positive.copy(alpha = 0.2f)
                            else theme.negative.copy(alpha = 0.2f)
                        ),
                    color = if (isPositive) theme.positive.copy(alpha = 0.2f) else theme.negative.copy(alpha = 0.2f)
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = "Trend",
                        tint = if (isPositive) theme.positive else theme.negative,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
            }
        }
    }
}
