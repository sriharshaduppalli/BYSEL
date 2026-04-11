package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun NewsWidget(
    isPinned: Boolean,
    headlines: List<MarketNewsHeadline>,
    trackedSymbols: List<String>,
    isLoading: Boolean,
    error: String?,
    onPinClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppTheme.current.card)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Market News",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                )
                val subtitle = if (trackedSymbols.isNotEmpty()) {
                    "Tracking ${trackedSymbols.joinToString(", ")}"
                } else {
                    "Top live headlines from tracked market leaders"
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh News",
                        tint = LocalAppTheme.current.primary
                    )
                }
                IconButton(onClick = onPinClick) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isPinned) "Unpin News" else "Pin News",
                        tint = if (isPinned) LocalAppTheme.current.positive else LocalAppTheme.current.textSecondary
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            when {
                isLoading && headlines.isEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalAppTheme.current.primary
                        )
                        Text(
                            text = "Refreshing market headlines...",
                            fontSize = 14.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    }
                }

                error != null && headlines.isEmpty() -> {
                    Text(
                        text = error,
                        fontSize = 14.sp,
                        color = LocalAppTheme.current.negative
                    )
                }

                headlines.isEmpty() -> {
                    Text(
                        text = "Live headline context is unavailable right now.",
                        fontSize = 14.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                }

                else -> {
                    headlines.forEachIndexed { index, headline ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = headline.symbol,
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = headline.title,
                            fontSize = 14.sp,
                            color = LocalAppTheme.current.text,
                            lineHeight = 20.sp
                        )
                        val meta = listOf(headline.source, headline.publishedLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                fontSize = 12.sp,
                                color = LocalAppTheme.current.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
