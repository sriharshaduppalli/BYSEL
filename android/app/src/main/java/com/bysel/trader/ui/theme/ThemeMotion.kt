package com.bysel.trader.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * User-facing motion helpers (stable Compose animation APIs).
 * Goal: make price/wallet/AI feedback readable — not decorative chrome.
 */

/**
 * Live price that briefly flashes green/red when the tick changes so users notice updates
 * without staring at the number.
 *
 * Flash intensity is drawn in [drawBehind] (draw phase) so tick animations avoid
 * recomposing every frame — same phase-shifting idea as Styles, without the experimental API.
 */
@Composable
fun TickPriceText(
    price: Double,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = LocalAppTheme.current.text,
    fontWeight: FontWeight? = FontWeight.SemiBold,
) {
    val theme = LocalAppTheme.current
    var previous by remember { mutableDoubleStateOf(price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(price) {
        val delta = price - previous
        if (abs(delta) >= 1e-9) {
            flashColor = if (delta > 0.0) theme.positive else theme.negative
            previous = price
            flash.snapTo(1f)
            flash.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            )
        } else {
            previous = price
        }
    }

    Text(
        text = text,
        modifier = modifier
            .drawBehind {
                val intensity = flash.value
                if (intensity > 0.01f) {
                    val radius = 4.dp.toPx()
                    drawRoundRect(
                        color = flashColor.copy(alpha = 0.22f * intensity),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = style.asNumeric(),
        color = color,
        fontWeight = fontWeight,
    )
}

/**
 * Counts wallet / portfolio amounts toward the new value so top-ups and fills feel confirmed.
 */
@Composable
fun AnimatedAmountText(
    amount: Double,
    formatter: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = LocalAppTheme.current.text,
    fontWeight: FontWeight? = FontWeight.Bold,
) {
    val animated by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "animatedAmount",
    )
    Text(
        text = formatter(animated.toDouble()),
        modifier = modifier,
        style = style.asNumeric(),
        color = color,
        fontWeight = fontWeight,
    )
}

/** Soft color shift for % change labels when the sign/magnitude updates. */
@Composable
fun animatedChangeColor(change: Double): Color {
    val theme = LocalAppTheme.current
    val target = theme.colorForChange(change)
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(280),
        label = "changeColor",
    )
    return animated
}

/**
 * Cross-fades every palette color when the user picks a new theme, so the skin
 * glides instead of snapping. Returns the target theme unchanged once settled.
 */
@Composable
fun rememberAnimatedAppTheme(target: AppTheme): AppTheme {
    val spec = tween<Color>(durationMillis = 420, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec, label = "themePrimary")
    val surface by animateColorAsState(target.surface, spec, label = "themeSurface")
    val card by animateColorAsState(target.card, spec, label = "themeCard")
    val positive by animateColorAsState(target.positive, spec, label = "themePositive")
    val negative by animateColorAsState(target.negative, spec, label = "themeNegative")
    val text by animateColorAsState(target.text, spec, label = "themeText")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "themeTextSecondary")
    val caution by animateColorAsState(target.caution, spec, label = "themeCaution")
    val settled = primary == target.primary && surface == target.surface && card == target.card &&
        positive == target.positive && negative == target.negative && text == target.text &&
        textSecondary == target.textSecondary && caution == target.caution
    if (settled) return target
    return AppTheme(
        primary = primary,
        surface = surface,
        card = card,
        positive = positive,
        negative = negative,
        text = text,
        textSecondary = textSecondary,
        name = target.name,
        caution = caution,
    )
}

/**
 * Push / pop motion for full-screen overlays (Search, Settings, Stock detail…).
 * Pushing slides the new screen in from the right over a fading root; popping
 * slides it back out. Short enough to never delay a tap.
 */
fun overlayScreenTransition(push: Boolean): ContentTransform {
    val transform = if (push) {
        (slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeIn(animationSpec = tween(220))) togetherWith
            fadeOut(animationSpec = tween(160))
    } else {
        fadeIn(animationSpec = tween(200)) togetherWith
            (slideOutHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 6 } +
                fadeOut(animationSpec = tween(180)))
    }
    transform.targetContentZIndex = if (push) 1f else 0f
    return transform
}

/** Three bouncing dots for AI / loading affordances. */
@Composable
fun PulsingDots(
    modifier: Modifier = Modifier,
    color: Color = LocalAppTheme.current.primary,
) {
    val transition = rememberInfiniteTransition(label = "pulsingDots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.75f)),
            )
        }
    }
}
