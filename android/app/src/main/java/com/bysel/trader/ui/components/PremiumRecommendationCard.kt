package com.bysel.trader.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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


/**
 * Premium stock recommendation card with glassmorphism design
 * Shows: Stock details, SL/TP levels, risk metrics, confidence score
 */
@Composable
fun PremiumRecommendationCard(
    symbol: String,
    companyName: String,
    currentPrice: Double,
    entrySignal: String,  // STRONG_BUY, BUY, HOLD, SELL
    signalConfidence: Int,  // 0-100
    stopLoss: Double,
    takeProfit1: Double,
    takeProfit2: Double,
    takeProfit3: Double,
    riskRewardRatio: Double,
    volatility: Double,  // Annualized %
    positionSize: Double,  // % of portfolio
    maxLossPerTrade: Double,
    onAnalyzeClick: () -> Unit,
    onTradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    var expandedTP by remember { mutableStateOf(false) }
    
    // Risk/reward visualization
    val riskScore = (maxLossPerTrade / currentPrice * 100).coerceIn(0.0, 20.0)
    val rewardScore = ((takeProfit2 - currentPrice) / currentPrice * 100).coerceIn(0.0, 30.0)
    
    // Signal color mapping
    val signalColor = when (entrySignal) {
        "STRONG_BUY" -> Color(0xFF00C853)
        "BUY" -> Color(0xFF4CAF50)
        "HOLD" -> Color(0xFFFFC107)
        "SELL" -> Color(0xFFFF5722)
        else -> theme.primary
    }
    
    // Confidence color (0-100 scale)
    val confidenceColor = when {
        signalConfidence >= 80 -> Color(0xFF00C853)
        signalConfidence >= 60 -> Color(0xFF4CAF50)
        signalConfidence >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFFFF5722)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onAnalyzeClick),
        colors = CardDefaults.cardColors(
            containerColor = theme.card.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Stock info + Signal badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = symbol,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.text
                    )
                    Text(
                        text = companyName.take(30),
                        fontSize = 13.sp,
                        color = theme.textSecondary,
                        maxLines = 1
                    )
                }
                
                // Signal badge with gradient
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp)),
                    color = signalColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = entrySignal.replace("_", " "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = signalColor
                        )
                        Text(
                            text = "↑ $signalConfidence%",
                            fontSize = 10.sp,
                            color = signalColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Price section with change
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                theme.primary.copy(alpha = 0.08f),
                                theme.primary.copy(alpha = 0.02f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Current Price", fontSize = 11.sp, color = theme.textSecondary)
                    Text(
                        "₹${"%.2f".format(currentPrice)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                }
                Divider(modifier = Modifier.height(40.dp).width(1.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("1M Target", fontSize = 11.sp, color = theme.textSecondary)
                    Text(
                        "₹${"%.2f".format(takeProfit2)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "+${((takeProfit2 - currentPrice) / currentPrice * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            // Risk/Reward Visualization (horizontal bar)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Risk/Reward Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.text)
                    Text("R:R ${String.format("%.2f", riskRewardRatio)}", fontSize = 11.sp, color = theme.textSecondary)
                }
                
                // Risk/Reward bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Risk bar (red, left)
                    Surface(
                        modifier = Modifier
                            .weight(riskScore.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFEF5350).copy(alpha = 0.8f)
                    ) {}
                    
                    // Center indicator
                    Surface(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight(),
                        color = theme.textSecondary
                    ) {}
                    
                    // Reward bar (green, right)
                    Surface(
                        modifier = Modifier
                            .weight(rewardScore.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF66BB6A).copy(alpha = 0.8f)
                    ) {}
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-${"%.1f".format(riskScore)}%", fontSize = 10.sp, color = Color(0xFFEF5350))
                    Text("+${"%.1f".format(rewardScore)}%", fontSize = 10.sp, color = Color(0xFF66BB6A))
                }
            }
            
            // Trade Levels Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expandedTP = !expandedTP },
                color = theme.card.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stop Loss
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛑 Stop Loss", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.text)
                        Text("₹${"%.2f".format(stopLoss)}", fontSize = 12.sp, color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                    }
                    
                    // TP1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📈 Target 1", fontSize = 12.sp, color = theme.textSecondary)
                        Text("₹${"%.2f".format(takeProfit1)}", fontSize = 12.sp, color = Color(0xFF8BC34A))
                    }
                    
                    // Expandable TP2 & TP3
                    AnimatedVisibility(visible = expandedTP) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Divider(color = theme.textSecondary.copy(alpha = 0.2f), thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("📊 Target 2", fontSize = 12.sp, color = theme.textSecondary)
                                Text("₹${"%.2f".format(takeProfit2)}", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🎯 Target 3", fontSize = 12.sp, color = theme.textSecondary)
                                Text("₹${"%.2f".format(takeProfit3)}", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            // Confidence & Metrics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                confidenceColor.copy(alpha = 0.1f),
                                confidenceColor.copy(alpha = 0.02f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Confidence score
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Confidence", fontSize = 10.sp, color = theme.textSecondary)
                    Text(
                        "$signalConfidence%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = confidenceColor
                    )
                }
                Divider(modifier = Modifier.width(1.dp).height(50.dp))
                // Position size
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Position", fontSize = 10.sp, color = theme.textSecondary)
                    Text(
                        "${positionSize.toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.primary
                    )
                }
                Divider(modifier = Modifier.width(1.dp).height(50.dp))
                // Volatility
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Volatility", fontSize = 10.sp, color = theme.textSecondary)
                    Text(
                        "${volatility.toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.textSecondary
                    )
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAnalyzeClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = theme.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(theme.primary.copy(alpha = 0.5f), theme.primary.copy(alpha = 0.3f))
                        )
                    )
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analyze", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = onTradeClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = signalColor.copy(alpha = 0.9f)
                    )
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(entrySignal.take(3), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
