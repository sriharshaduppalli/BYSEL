package com.bysel.trader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight tonal helpers so static skins map closer to Material 3 roles
 * (containers / elevated surfaces) without pulling an extra MCU dependency.
 */
internal fun Color.blendToward(other: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = alpha + (other.alpha - alpha) * t,
    )
}

internal fun Color.toneTowardWhite(amount: Float): Color = blendToward(Color.White, amount)

internal fun Color.toneTowardBlack(amount: Float): Color = blendToward(Color.Black, amount)

/** Container fill that sits behind chips / selected rows. */
fun AppTheme.primaryContainerColor(): Color =
    if (isLight) {
        primary.copy(alpha = 0.14f).compositeOver(card)
    } else {
        primary.copy(alpha = 0.22f).compositeOver(card)
    }

fun AppTheme.onPrimaryContainerColor(): Color =
    if (isLight) primary.toneTowardBlack(0.35f) else primary.toneTowardWhite(0.55f)

/** Nested panel one step above [card] — never the same fill as the page or the card. */
fun AppTheme.surfaceElevatedColor(): Color =
    if (isLight) {
        Color(0xFFF3F5F8)
    } else {
        text.copy(alpha = 0.08f).compositeOver(card)
    }

/**
 * Soften custom PnL hues so they sit better next to a Material You primary
 * (wallpaper-derived Dynamic themes).
 */
fun harmonizeAccent(accent: Color, toward: Color, amount: Float = 0.18f): Color =
    accent.blendToward(toward, amount.coerceIn(0f, 0.4f))

/** Pick readable content color for an arbitrary fill. */
fun contentColorForFill(fill: Color): Color =
    if (fill.luminance() > 0.55f) Color(0xFF121212) else Color.White

internal fun Color.ensureContrastOn(background: Color, preferLight: Boolean): Color {
    val ratio = contrastRatio(this, background)
    if (ratio >= 4.5f) return this
    return if (preferLight || background.luminance() < 0.5f) {
        toneTowardWhite(0.35f)
    } else {
        toneTowardBlack(0.35f)
    }
}

private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = max(a.luminance(), b.luminance()) + 0.05f
    val l2 = min(a.luminance(), b.luminance()) + 0.05f
    return l1 / l2
}
