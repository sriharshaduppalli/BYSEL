package com.bysel.trader.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Stable "style layer" helpers — Material tokens + [AppTheme] semantics.
 *
 * Maps Styles Do's/Don'ts onto the Material path (experimental `Style` API deferred):
 * - Visuals via theme tokens / these helpers; behaviors stay on Modifier / callbacks
 * - Read [LocalAppTheme] at use site (don't bake colors into remembered Style-like objects)
 * - Prefer helpers on components; don't style entire screens with a single "style" bag
 * - Screen-level chrome uses [ScreenHeader]; layout screens keep Modifier for placement
 */

/** Card fill that follows the active [AppTheme] palette. */
@Composable
fun byselCardColors() = CardDefaults.cardColors(
    containerColor = LocalAppTheme.current.card,
    contentColor = LocalAppTheme.current.text,
)

/** Green / red / muted for price & PnL deltas. */
fun AppTheme.colorForChange(change: Double): Color = when {
    change > 0.0 -> positive
    change < 0.0 -> negative
    else -> textSecondary
}

/**
 * Consistent tab/screen title + supporting line used across Home, Trade, Search, More.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val titleStyle: TextStyle =
        if (compact) MaterialTheme.typography.headlineMedium
        else MaterialTheme.typography.headlineLarge
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = theme.text,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Compact in-card section title (watchlist, quote rows, etc.). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppTheme.current.text,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

/** Secondary caption under a section title. */
@Composable
fun SectionCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppTheme.current.textSecondary,
    )
}

/** Signed % / PnL line using theme positive/negative. */
@Composable
fun ChangeText(
    change: Double,
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = LocalAppTheme.current.colorForChange(change),
    )
}

/** Standard horizontal padding for main tab content. */
fun Modifier.screenContentPadding(): Modifier = this.padding(horizontal = 16.dp)
