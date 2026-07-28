package com.bysel.trader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Warns when on-screen prices may no longer be live.
 *
 * A trading app must never let a stale price look current, so this sits above the tab
 * content and is visible on every screen. It stays hidden while data is fresh, and
 * distinguishes "market closed" (expected staleness) from a genuine feed problem.
 */
private const val STALE_AFTER_MS = 45_000L

@Composable
fun MarketDataStatusBanner(
    lastQuoteUpdateAt: Long,
    isMarketOpen: Boolean?,
    modifier: Modifier = Modifier,
) {
    // Ticks only while this banner is composed; drives the "Xs ago" age readout.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            now = System.currentTimeMillis()
        }
    }

    val ageMs = if (lastQuoteUpdateAt <= 0L) Long.MAX_VALUE else now - lastQuoteUpdateAt
    val isStale = ageMs > STALE_AFTER_MS

    // When the exchange is shut, last traded prices are correct rather than degraded,
    // so there is nothing to warn about.
    val marketClosed = isMarketOpen == false
    val visible = isStale && !marketClosed

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        val neverLoaded = lastQuoteUpdateAt <= 0L
        val accent = if (neverLoaded) Color(0xFFE53935) else Color(0xFFFF8F00)
        val message = if (neverLoaded) {
            "Live prices unavailable — check your connection before trading"
        } else {
            "Prices may be delayed — last updated ${formatAge(ageMs)} ago"
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (neverLoaded) Icons.Filled.CloudOff else Icons.Filled.Schedule,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = message,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
    }
}

private fun formatAge(ageMs: Long): String {
    val seconds = ageMs / 1000L
    return when {
        seconds < 90L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        else -> "${seconds / 3600L}h"
    }
}
