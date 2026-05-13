package com.bysel.trader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    cornerRadius: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2A2A2A),
            Color(0xFF3A3A3A),
            Color(0xFF2A2A2A),
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f),
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush)
    )
}

/** Skeleton card mimicking a single portfolio/stock row */
@Composable
fun ShimmerStockRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.width(90.dp), height = 16.dp)
            ShimmerBox(modifier = Modifier.width(140.dp), height = 12.dp)
        }
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShimmerBox(modifier = Modifier.width(70.dp), height = 16.dp)
            ShimmerBox(modifier = Modifier.width(50.dp), height = 12.dp)
        }
    }
}

/** Dashboard-style skeleton with summary card + stock rows */
@Composable
fun DashboardSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Summary card skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 100.dp, cornerRadius = 16.dp)
        // Market mood bar
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 48.dp, cornerRadius = 12.dp)
        // Stock rows
        repeat(5) {
            ShimmerStockRow()
        }
    }
}

/** Portfolio-style skeleton with stock rows */
@Composable
fun PortfolioSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Health card skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 80.dp, cornerRadius = 16.dp)
        repeat(6) {
            ShimmerStockRow()
        }
    }
}
