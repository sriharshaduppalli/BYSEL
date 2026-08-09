package com.bysel.trader.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Buy / Sell CTA with a light press “depth” cue (scale + slight Y nudge).
 *
 * Material-path stand-in for the Styles examples (shadow/translate on pressed) —
 * no experimental Style API; colors come from [AppTheme].
 */
@Composable
fun TradeActionButton(
    onClick: () -> Unit,
    isBuy: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(100),
        label = "tradeBtnScale",
    )
    val translateY by animateFloatAsState(
        targetValue = if (pressed && enabled) 2f else 0f,
        animationSpec = tween(100),
        label = "tradeBtnY",
    )
    val fill = if (isBuy) theme.positive else theme.negative

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translateY
            },
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = if (isBuy) theme.onPositive else theme.onNegative,
            disabledContainerColor = theme.mutedSurface,
            disabledContentColor = theme.textSecondary,
        ),
        content = content,
    )
}
