package com.bysel.trader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bysel.trader.ui.theme.LocalAppTheme

/**
 * A read-only status badge.
 *
 * Visuals from theme tokens; no click/gesture logic (use AssistChip when a tap does something).
 * Optional [containerColor]/[contentColor] override defaults at the call site — prefer theme.
 */
@Composable
fun InfoChip(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    label: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    val background = containerColor ?: theme.card
    val foreground = contentColor ?: theme.text
    Surface(
        modifier = modifier
            .wrapContentWidth()
            .defaultMinSize(minHeight = 32.dp, minWidth = 40.dp)
            // Merge the label into a single read-only node so screen readers announce the
            // badge as one piece of text rather than an actionable element.
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = background,
        border = if (containerColor == null) {
            BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.35f))
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(color = foreground)
            ) {
                label()
            }
        }
    }
}
