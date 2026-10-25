package com.bysel.trader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.api.SentimentScoreResponse
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.blendToward

@Composable
fun SentimentBar(
    sentiment: SentimentScoreResponse?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targetFraction = ((sentiment?.scoreBar ?: 50.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else targetFraction,
        animationSpec = tween(durationMillis = 800),
        label = "sentiment_bar"
    )

    val theme = LocalAppTheme.current
    // Bearish -> neutral -> bullish follows the active skin's PnL palette.
    val barColor = when {
        targetFraction >= 0.65f -> theme.positive
        targetFraction >= 0.55f -> theme.positive.blendToward(theme.caution, 0.4f)
        targetFraction >= 0.45f -> theme.caution
        targetFraction >= 0.35f -> theme.negative.blendToward(theme.caution, 0.4f)
        else -> theme.negative
    }

    val levelLabel = sentiment?.level ?: "Neutral"
    val strengthLabel = sentiment?.strength ?: ""
    val scoreText = sentiment?.let { if (it.score >= 0) "+${it.score}" else "${it.score}" } ?: "--"
    val headlineCount = sentiment?.headlineCount ?: 0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "News Sentiment",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = scoreText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                )
                if (strengthLabel.isNotEmpty()) {
                    Text(
                        text = "· $strengthLabel",
                        fontSize = 11.sp,
                        color = theme.textSecondary.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Track bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(theme.mutedSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(theme.negative, theme.caution, theme.positive)
                        )
                    )
            )
            // Thumb marker
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (animatedFraction * 100).coerceIn(0f, 96f).dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(theme.text)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Bearish", fontSize = 9.sp, color = theme.textSecondary.copy(alpha = 0.8f))
            Text(
                text = "$levelLabel · $headlineCount headlines",
                fontSize = 10.sp,
                color = theme.textSecondary,
            )
            Text("Bullish", fontSize = 9.sp, color = theme.textSecondary.copy(alpha = 0.8f))
        }

        // Summary line
        if (!sentiment?.summary.isNullOrBlank()) {
            Text(
                text = sentiment!!.summary,
                fontSize = 10.sp,
                color = theme.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
            )
        }
    }
}
