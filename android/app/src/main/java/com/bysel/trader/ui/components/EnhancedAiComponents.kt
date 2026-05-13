package com.bysel.trader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.SentimentBreakdown
import com.bysel.trader.data.models.QueryUnderstanding

/**
 * Enhanced AI Analysis UI Components - Level 2
 * 
 * Displays:
 * 1. Confidence breakdown with visual indicators
 * 2. Reasoning explanation for predictions
 * 3. Event risk factors
 * 4. Sentiment analysis with weighted scoring
 * 5. Query understanding feedback
 * 6. Profit signal card with entry/target/stop-loss
 */

// ──────────────────────────────────────────────────────────────
// PROFIT SIGNAL CARD — Entry / Target / Stop-Loss / Risk-Reward
// ──────────────────────────────────────────────────────────────

data class ProfitSignal(
    val symbol: String,
    val signal: String,       // "BUY", "SELL", "HOLD"
    val entry: Double?,
    val target: Double?,
    val stopLoss: Double?,
    val confidence: Int?,
    val timeframe: String?,
)

object ProfitSignalExtractor {
    private val ENTRY_RE = Regex("""(?i)(?:entry|buy(?:\s+at)?|enter(?:\s+at)?|accumulate(?:\s+(?:near|around|at))?)[:\s]*₹?\s*([\d,]+(?:\.\d+)?)""")
    private val TARGET_RE = Regex("""(?i)(?:target|upside|tp|take[\s-]?profit|price[\s-]?target)[:\s]*₹?\s*([\d,]+(?:\.\d+)?)""")
    private val SL_RE = Regex("""(?i)(?:stop[\s-]?loss|sl|stop|downside[\s-]?risk|risk[\s-]?at)[:\s]*₹?\s*([\d,]+(?:\.\d+)?)""")
    private val SIGNAL_RE = Regex("""(?i)(STRONG[\s_]?BUY|BUY|STRONG[\s_]?SELL|SELL|HOLD|ACCUMULATE|NEUTRAL)""")
    private val CONFIDENCE_RE = Regex("""(?i)(?:confidence|conviction)[:\s]*(\d{1,3})%?""")
    private val TIMEFRAME_RE = Regex("""(?i)(?:timeframe|horizon|term|holding[\s-]?period)[:\s]*(short[\s-]?term|medium[\s-]?term|long[\s-]?term|\d+\s*(?:day|week|month|year)s?)""")
    private val SYMBOL_RE = Regex("""(?i)(?:for|analysis of|stock|symbol)[:\s]*([A-Z]{2,15})""")

    fun extract(text: String, contextSymbol: String? = null): ProfitSignal? {
        val entry = ENTRY_RE.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val target = TARGET_RE.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val stopLoss = SL_RE.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // Need at least entry+target or target+stopLoss to show a useful card
        if (entry == null && target == null && stopLoss == null) return null

        val signal = SIGNAL_RE.find(text)?.groupValues?.get(1)?.uppercase()?.replace("_", " ") ?: "HOLD"
        val confidence = CONFIDENCE_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val timeframe = TIMEFRAME_RE.find(text)?.groupValues?.get(1)
        val symbol = contextSymbol ?: SYMBOL_RE.find(text)?.groupValues?.get(1) ?: ""

        return ProfitSignal(symbol, signal, entry, target, stopLoss, confidence, timeframe)
    }
}

@Composable
fun ProfitSignalCard(
    signal: ProfitSignal,
    onBuy: (() -> Unit)? = null,
    onSetAlert: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBullish = signal.signal.contains("BUY") || signal.signal.contains("ACCUMULATE")
    val accentColor = if (isBullish) Color(0xFF00C853) else Color(0xFFE53935)
    val riskReward = if (signal.entry != null && signal.target != null && signal.stopLoss != null && signal.entry != signal.stopLoss) {
        kotlin.math.abs(signal.target - signal.entry) / kotlin.math.abs(signal.entry - signal.stopLoss)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (isBullish) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (signal.symbol.isNotBlank()) "${signal.signal} ${signal.symbol}" else signal.signal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = accentColor
                    )
                }
                signal.confidence?.let {
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${it}% confidence",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Entry / Target / Stop-Loss row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                signal.entry?.let {
                    PriceLabel(label = "Entry", price = it, color = Color(0xFF1976D2))
                }
                signal.target?.let {
                    PriceLabel(label = "Target", price = it, color = Color(0xFF00C853))
                }
                signal.stopLoss?.let {
                    PriceLabel(label = "Stop Loss", price = it, color = Color(0xFFE53935))
                }
            }

            // Risk-Reward + Timeframe
            if (riskReward != null || signal.timeframe != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    riskReward?.let {
                        val rrColor = if (it >= 2.0) Color(0xFF00C853) else if (it >= 1.0) Color(0xFFFFC107) else Color(0xFFE53935)
                        Text(
                            text = "R:R ${String.format("%.1f", it)}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = rrColor
                        )
                    }
                    signal.timeframe?.let {
                        Text(
                            text = "⏱ $it",
                            fontSize = 11.sp,
                            color = Color(0xFF666666)
                        )
                    }
                    // Potential profit/loss
                    if (signal.entry != null && signal.target != null) {
                        val pctGain = ((signal.target - signal.entry) / signal.entry) * 100.0
                        Text(
                            text = "Potential: ${if (pctGain > 0) "+" else ""}${String.format("%.1f", pctGain)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pctGain > 0) Color(0xFF00C853) else Color(0xFFE53935)
                        )
                    }
                }
            }

            // Action buttons
            if (onBuy != null || onSetAlert != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBuy != null && isBullish) {
                        Button(
                            onClick = onBuy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Buy ${signal.symbol}", fontSize = 12.sp, color = Color.White)
                        }
                    }
                    if (onSetAlert != null) {
                        OutlinedButton(
                            onClick = onSetAlert,
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Set Alert", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLabel(label: String, price: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF888888)
        )
        Text(
            text = "₹${String.format("%,.1f", price)}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ──────────────────────────────────────────────────────────────
// CONFIDENCE METER WITH DETAILED BREAKDOWN
// ──────────────────────────────────────────────────────────────

@Composable
fun ConfidenceCard(
    overallConfidence: Double,
    confidenceLevel: String,
    factors: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with confidence level badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model Confidence",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A)
                )
                
                ConfidenceBadge(
                    confidence = overallConfidence,
                    level = confidenceLevel
                )
            }
            
            // Confidence gauge
            ConfidenceGauge(overallConfidence)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Factor breakdown
            Text(
                text = "Confidence Factors",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ConfidenceFactorsList(factors)
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Double, level: String) {
    val bgColor = when {
        confidence >= 75 -> Color(0xFFD4EDDA)  // Light green
        confidence >= 60 -> Color(0xFFFFF3CD)  // Light yellow
        else -> Color(0xFFF8D7DA)  // Light red
    }
    val textColor = when {
        confidence >= 75 -> Color(0xFF155724)
        confidence >= 60 -> Color(0xFF856404)
        else -> Color(0xFF721C24)
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = "${confidence.toInt()}% $level",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ConfidenceGauge(confidence: Double) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (confidence / 100.0).toFloat().coerceIn(0f, 1f))
                    .background(
                        color = when {
                            confidence >= 75.0 -> Color(0xFF28A745)
                            confidence >= 60.0 -> Color(0xFFFFC107)
                            else -> Color(0xFFDC3545)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(
                        color = Color(0xFFE9ECEF),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("50%", fontSize = 10.sp, color = Color(0xFF999999))
            Text("75%", fontSize = 10.sp, color = Color(0xFF999999))
            Text("95%", fontSize = 10.sp, color = Color(0xFF999999))
        }
    }
}

@Composable
private fun ConfidenceFactorsList(factors: Map<String, Any>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        factors.forEach { (factorName, factorData) ->
            if (factorData is Map<*, *>) {
                val score = (factorData["score"] as? Number)?.toDouble() ?: 0.0
                val weight = (factorData["weight"] as? Number)?.toDouble() ?: 0.0
                val reasoning = factorData["reasoning"] as? String ?: ""
                
                ConfidenceFactorRow(factorName, score, weight, reasoning)
            }
        }
    }
}

@Composable
private fun ConfidenceFactorRow(
    name: String,
    score: Double,
    weight: Double,
    reasoning: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name.replace(Regex("([A-Z])"), " $1").trim(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${score.toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0066CC)
            )
        }
        
        if (reasoning.isNotEmpty()) {
            Text(
                text = reasoning,
                fontSize = 10.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}


// ──────────────────────────────────────────────────────────────
// PREDICTION REASONING CARD
// ──────────────────────────────────────────────────────────────

@Composable
fun PredictionReasoningCard(
    symbol: String,
    signal: String,
    whyConfident: String,
    caveats: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = getSignalBackgroundColor(signal).copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, getSignalBackgroundColor(signal))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Signal with reasoning
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getSignalIcon(signal),
                    contentDescription = signal,
                    tint = getSignalBackgroundColor(signal),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = signal,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = getSignalBackgroundColor(signal)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Reasoning explanation
            Text(
                text = "Why we're ${if (signal.contains("BUY")) "bullish" else "bearish"}:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF666666)
            )
            Text(
                text = whyConfident,
                fontSize = 13.sp,
                color = Color(0xFF333333),
                modifier = Modifier.padding(vertical = 8.dp),
                lineHeight = 18.sp
            )
            
            if (caveats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Important caveats:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF666666)
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    caveats.forEach { caveat ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = caveat.take(2),  // Emoji
                                fontSize = 12.sp
                            )
                            Text(
                                text = caveat.drop(2),
                                fontSize = 11.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getSignalIcon(signal: String) = when {
    signal.contains("STRONG_BUY") || signal.contains("BUY") -> Icons.Default.TrendingUp
    signal.contains("SELL") -> Icons.Default.TrendingDown
    else -> Icons.Default.Remove
}

private fun getSignalBackgroundColor(signal: String) = when {
    signal.contains("STRONG_BUY") -> Color(0xFF28A745)
    signal.contains("BUY") -> Color(0xFF20C997)
    signal.contains("STRONG_SELL") -> Color(0xFFDC3545)
    signal.contains("SELL") -> Color(0xFFFD7E14)
    else -> Color(0xFF6C757D)
}


// ──────────────────────────────────────────────────────────────
// EVENT RISK FACTORS DISPLAY
// ──────────────────────────────────────────────────────────────

@Composable
fun EventRiskCard(
    baseConfidence: Double,
    adjustedConfidence: Double,
    adjustmentFactor: Double,
    eventRisks: List<String>,
    modifier: Modifier = Modifier
) {
    if (eventRisks.isEmpty()) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Risk Factor",
                    tint = Color(0xFF856404),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Upcoming Events May Impact Prediction",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF856404)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Confidence adjustment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Base Confidence", fontSize = 11.sp, color = Color(0xFF666666))
                    Text("${baseConfidence.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text("Adjusted", fontSize = 11.sp, color = Color(0xFF666666))
                    Text("${adjustedConfidence.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Risk factors list
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                eventRisks.forEach { risk ->
                    RiskFactorItem(risk)
                }
            }
        }
    }
}

@Composable
private fun RiskFactorItem(risk: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(risk.take(1), fontSize = 12.sp)  // Emoji
        Text(
            text = risk.drop(1),
            fontSize = 11.sp,
            color = Color(0xFF333333),
            modifier = Modifier.weight(1f)
        )
    }
}


// ──────────────────────────────────────────────────────────────
// SENTIMENT ANALYSIS DISPLAY
// ──────────────────────────────────────────────────────────────

@Composable
fun SentimentCard(
    overallSentiment: String,
    score: Double,  // -1 to +1
    strength: String,
    breakdown: SentimentBreakdown,
    interpretation: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "News Sentiment",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                SentimentBadge(overallSentiment, strength)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sentiment gauge
            SentimentGauge(score)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SentimentBreakdownItem(
                    label = "Positive",
                    count = breakdown.positiveCount,
                    color = Color(0xFF28A745)
                )
                SentimentBreakdownItem(
                    label = "Neutral",
                    count = breakdown.neutralCount,
                    color = Color(0xFF6C757D)
                )
                SentimentBreakdownItem(
                    label = "Negative",
                    count = breakdown.negativeCount,
                    color = Color(0xFFDC3545)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = interpretation,
                fontSize = 11.sp,
                color = Color(0xFF666666),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SentimentBadge(sentiment: String, strength: String) {
    val bgColor = when (sentiment) {
        "positive" -> Color(0xFFD4EDDA)
        "negative" -> Color(0xFFF8D7DA)
        else -> Color(0xFFE2E3E5)
    }
    val textColor = when (sentiment) {
        "positive" -> Color(0xFF155724)
        "negative" -> Color(0xFF721C24)
        else -> Color(0xFF383D41)
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "${sentiment.capitalize()} ($strength)",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SentimentGauge(score: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Negative side
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = 0.5f * (1f - (score / 2.0).toFloat().coerceIn(-1f, 0f)))
                .background(Color(0xFFDC3545), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
        )
        
        // Neutral center
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = 0.1f)
                .background(Color(0xFFE9ECEF))
        )
        
        // Positive side
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Color(0xFF28A745), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )
    }
}

@Composable
private fun SentimentBreakdownItem(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}


// ──────────────────────────────────────────────────────────────
// QUERY UNDERSTANDING FEEDBACK
// ──────────────────────────────────────────────────────────────

@Composable
fun QueryUnderstandingCard(
    queryUnderstanding: QueryUnderstanding,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Query understood",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Query Understood",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1565C0)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val intents = queryUnderstanding.intents.joinToString()
            val timeframe = queryUnderstanding.timeframe.phrase ?: "medium-term"
            val confidence = queryUnderstanding.confidence
            
            Text(
                text = "Analysis: $intents | Timeframe: $timeframe",
                fontSize = 11.sp,
                color = Color(0xFF0D47A1),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            if (confidence < 0.7) {
                Text(
                    text = "⚠️ Interpretation confidence: ${(confidence * 100).toInt()}% (ask more specific question for better results)",
                    fontSize = 10.sp,
                    color = Color(0xFF856404)
                )
            }
        }
    }
}
