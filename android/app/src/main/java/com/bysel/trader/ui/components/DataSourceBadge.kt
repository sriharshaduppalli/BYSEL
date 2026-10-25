package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * What a number on an F&O screen actually is.
 *
 * BYSEL has no exchange feed. A chain is either the public NSE board (delayed),
 * a teaching board built from a delayed spot, or a teaching board whose spot
 * is itself illustrative. A learner should see which one they are looking at
 * on every F&O screen, the same way the Trade tab shows quote freshness.
 */
enum class FoDataKind { DELAYED, TEACHING, ILLUSTRATIVE, REPLAY }

data class FoDataLabel(val kind: FoDataKind, val title: String, val detail: String)

fun foDataLabel(source: String?, generatedAt: String?, notes: List<String>): FoDataLabel {
    val src = source.orEmpty().trim().lowercase()
    val illustrative = notes.any { it.contains("illustrative", ignoreCase = true) }
    val age = generatedAgeText(generatedAt)
    return when {
        src == "synthetic" && illustrative -> FoDataLabel(
            FoDataKind.ILLUSTRATIVE,
            "Teaching board · spot illustrative",
            "No delayed print was available, so the spot and every strike here are classroom numbers.",
        )
        src == "synthetic" || src.isBlank() -> FoDataLabel(
            FoDataKind.TEACHING,
            "Teaching board · spot delayed",
            "Strikes, premiums and OI are modelled from a delayed spot. They are not exchange prints.",
        )
        else -> FoDataLabel(
            FoDataKind.DELAYED,
            "NSE board · delayed" + (age?.let { " · $it" } ?: ""),
            "Public exchange board, not a streaming feed. Expect a lag against your broker.",
        )
    }
}

val replayDataLabel = FoDataLabel(
    FoDataKind.REPLAY,
    "Replay · saved 15-minute bars",
    "You are stepping through saved bars from a delayed source. Nothing on this chart is live.",
)

@Composable
fun DataSourceBadge(
    label: FoDataLabel,
    modifier: Modifier = Modifier,
    showDetail: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val accent = when (label.kind) {
        FoDataKind.DELAYED -> theme.primary
        FoDataKind.TEACHING, FoDataKind.REPLAY -> theme.caution
        FoDataKind.ILLUSTRATIVE -> theme.negative
    }
    val icon = when (label.kind) {
        FoDataKind.DELAYED -> Icons.Filled.Schedule
        FoDataKind.REPLAY -> Icons.Filled.History
        FoDataKind.TEACHING, FoDataKind.ILLUSTRATIVE -> Icons.Filled.School
    }
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
            Text(
                label.title,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDetail) {
            Text(
                label.detail,
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun generatedAgeText(generatedAt: String?): String? {
    val raw = generatedAt?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val millis = try {
        Instant.parse(if (raw.endsWith("Z") || raw.contains("+")) raw else raw + "Z").toEpochMilli()
    } catch (_: DateTimeParseException) {
        return null
    }
    val ageSec = ((System.currentTimeMillis() - millis) / 1000L).coerceAtLeast(0L)
    return when {
        ageSec < 60L -> "just now"
        ageSec < 3600L -> "${ageSec / 60L}m ago"
        ageSec < 86_400L -> "${ageSec / 3600L}h ago"
        else -> "${ageSec / 86_400L}d ago"
    }
}
