package com.bysel.trader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val bodyLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Prices / PnL / wallet amounts — monospace + tabular figures so digits line up in lists.
 * (Work with fonts guide: purposeful fontFamily; avoids bundling a full Google Font for now.)
 */
val ByselNumericType = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
)

fun TextStyle.asNumeric(): TextStyle = this.merge(ByselNumericType)

/**
 * App-wide type ramp. Prefer [androidx.compose.material3.MaterialTheme.typography]
 * over one-off `fontSize` / `FontWeight` at call sites.
 *
 * UI copy uses [FontFamily.SansSerif]. Money figures should use [asNumeric] / [ByselNumericType].
 * Paragraph styling: headings → [LineBreak.Heading]; body → [LineBreak.Paragraph].
 */
val ByselTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        lineBreak = LineBreak.Heading,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
        lineBreak = LineBreak.Heading,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        lineBreak = LineBreak.Heading,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineBreak = LineBreak.Heading,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        lineBreak = LineBreak.Heading,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineBreak = LineBreak.Heading,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineBreak = LineBreak.Paragraph,
        lineHeightStyle = bodyLineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        lineBreak = LineBreak.Paragraph,
        lineHeightStyle = bodyLineHeightStyle,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        lineBreak = LineBreak.Paragraph,
        lineHeightStyle = bodyLineHeightStyle,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineBreak = LineBreak.Simple,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineBreak = LineBreak.Simple,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        lineBreak = LineBreak.Simple,
    ),
)
