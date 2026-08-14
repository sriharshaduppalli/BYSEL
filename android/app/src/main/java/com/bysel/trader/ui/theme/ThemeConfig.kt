package com.bysel.trader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver

/** Canonical default theme id stored in prefs / shown in the picker. */
const val DEFAULT_THEME_ID = "Default"

/** Ordered picker labels. Dynamic = Material You (wallpaper) on Android 12+. */
val allThemes = listOf(
    DEFAULT_THEME_ID,
    "Light",
    "Aurora",
    "Slate",
    "Ocean",
    "Forest",
    "Sunset",
    "Amoled",
    "Royal",
    "Cyberpunk",
    "Monochrome",
    "Dynamic",
)

val LocalAppTheme = compositionLocalOf { getTheme(DEFAULT_THEME_ID) }

/**
 * Normalize any stored / toggled theme string to a canonical [allThemes] id.
 * Fixes legacy prefs like "default" and unknown values collapsing to Default.
 */
fun normalizeThemeId(themeName: String?): String {
    val raw = themeName?.trim().orEmpty()
    if (raw.isEmpty()) return DEFAULT_THEME_ID
    return allThemes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_THEME_ID
}

fun isLightThemeId(themeName: String?): Boolean =
    normalizeThemeId(themeName).equals("Light", ignoreCase = true)

fun isDynamicThemeId(themeName: String?): Boolean =
    normalizeThemeId(themeName).equals("Dynamic", ignoreCase = true)

// Contrast-tuned palettes: body text ≥ ~4.5:1 on surface/card; PnL colors readable on cards.
// Comfort pass: slightly lifted cards, softer secondaries, less neon on long-session themes.
object ThemeColors {
    object Default {
        val primary = Color(0xFF42A5F5)
        val surface = Color(0xFF0B0C0E)
        val card = Color(0xFF2A2D33)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF5252)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF9EACB4)
    }

    object Ocean {
        val primary = Color(0xFF26C6DA)
        val surface = Color(0xFF071018)
        val card = Color(0xFF123044)
        val positive = Color(0xFF1DE9B6)
        val negative = Color(0xFFFF8A65)
        val text = Color(0xFFE0F7FA)
        val textSecondary = Color(0xFF8BC8D4)
    }

    object Forest {
        // Card kept dark (not saturated green) so green/red PnL and secondary text stay readable.
        val primary = Color(0xFF66BB6A)
        val surface = Color(0xFF08140E)
        val card = Color(0xFF163024)
        val positive = Color(0xFF4CD964)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFE8F5E9)
        val textSecondary = Color(0xFF8FBF94)
    }

    object Sunset {
        val primary = Color(0xFFF08C2E)
        val surface = Color(0xFF140C00)
        val card = Color(0xFF301C08)
        val positive = Color(0xFF66BB6A)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFFFF3E0)
        val textSecondary = Color(0xFFE0B070)
    }

    object Cyberpunk {
        val primary = Color(0xFFCE93D8)
        val surface = Color(0xFF070A1A)
        val card = Color(0xFF1A223F)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFEF5350)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF7BCAD6)
    }

    object Amoled {
        val primary = Color(0xFFB388FF)
        val surface = Color(0xFF050505)
        val card = Color(0xFF161616)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF5252)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF9EACB4)
    }

    object Light {
        val primary = Color(0xFF1565C0)
        val surface = Color(0xFFE4E8F0)
        val card = Color(0xFFFFFFFF)
        val positive = Color(0xFF2E7D32)
        val negative = Color(0xFFC62828)
        val text = Color(0xFF121212)
        val textSecondary = Color(0xFF4A4F55)
    }

    object Royal {
        val primary = Color(0xFFBA68C8)
        val surface = Color(0xFF120816)
        val card = Color(0xFF2A1A38)
        val positive = Color(0xFF81C784)
        val negative = Color(0xFFFF80AB)
        val text = Color(0xFFF8EAFB)
        val textSecondary = Color(0xFFC49AD0)
    }

    object Monochrome {
        // Keep grayscale chrome, but PnL must remain clearly readable.
        val primary = Color(0xFFCFCFCF)
        val surface = Color(0xFF0A0A0A)
        val card = Color(0xFF1C1C1C)
        val positive = Color(0xFF69F0AE)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFFA8A8A8)
    }

    /** Cool teal→indigo comfort skin — long-session friendly, not neon. */
    object Aurora {
        val primary = Color(0xFF4DB6AC)
        val surface = Color(0xFF07141A)
        val card = Color(0xFF12262E)
        val positive = Color(0xFF26C6A0)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFE8F5F3)
        val textSecondary = Color(0xFF8FB8B4)
    }

    /** Soft blue-gray professional skin for reading-heavy sessions. */
    object Slate {
        val primary = Color(0xFF90CAF9)
        val surface = Color(0xFF0B1118)
        val card = Color(0xFF172029)
        val positive = Color(0xFF66BB6A)
        val negative = Color(0xFFEF9A9A)
        val text = Color(0xFFE8EEF4)
        val textSecondary = Color(0xFF9AABBA)
    }
}

data class AppTheme(
    val primary: Color,
    val surface: Color,
    val card: Color,
    val positive: Color,
    val negative: Color,
    val text: Color,
    val textSecondary: Color,
    val name: String,
) {
    /** Label / icon color that stays readable on [primary] buttons. */
    val onPrimary: Color
        get() = if (primary.luminance() > 0.55f) Color(0xFF121212) else Color.White

    /** Label color on [positive] fill (Buy / gains CTAs). */
    val onPositive: Color
        get() = if (positive.luminance() > 0.55f) Color(0xFF121212) else Color.White

    /** Label color on [negative] fill (Sell / loss CTAs). */
    val onNegative: Color
        get() = if (negative.luminance() > 0.55f) Color(0xFF121212) else Color.White

    /** Hairline card outline — tinted on dark skins so edges are not a flat white stroke. */
    val cardOutline: Color
        get() = if (isLight) {
            Color.Black.copy(alpha = 0.14f)
        } else {
            primary.copy(alpha = 0.28f).compositeOver(Color.White.copy(alpha = 0.10f).compositeOver(card))
        }

    /** Subtle chip / inactive control fill that works on light and dark surfaces. */
    val mutedSurface: Color
        get() = text.copy(alpha = if (surface.luminance() > 0.5f) 0.08f else 0.12f)
            .compositeOver(card)

    val isLight: Boolean
        get() = surface.luminance() > 0.5f

    /** Horizontal header gradient that stays on-brand for every palette. */
    val headerGradientColors: List<Color>
        get() = listOf(
            primary,
            primary.copy(alpha = 0.72f).compositeOver(surface),
        )

    fun tintedSurface(base: Color, alpha: Float = 0.14f): Color =
        base.copy(alpha = alpha).compositeOver(surface)

    /** M3-style primary container for chips / selected rows. */
    val primaryContainer: Color
        get() = primaryContainerColor()

    val onPrimaryContainer: Color
        get() = onPrimaryContainerColor()

    /** Nested panel / elevated surface above [card]. */
    val surfaceElevated: Color
        get() = surfaceElevatedColor()

    /** Soft PnL chip wash that follows the active skin instead of hardcoded greens/reds. */
    fun pnlWash(isPositive: Boolean, alpha: Float = 0.18f): Color =
        (if (isPositive) positive else negative).copy(alpha = alpha)
}

fun getTheme(themeName: String): AppTheme {
    return when (normalizeThemeId(themeName).lowercase()) {
        "ocean" -> AppTheme(
            ThemeColors.Ocean.primary,
            ThemeColors.Ocean.surface,
            ThemeColors.Ocean.card,
            ThemeColors.Ocean.positive,
            ThemeColors.Ocean.negative,
            ThemeColors.Ocean.text,
            ThemeColors.Ocean.textSecondary,
            "Ocean",
        )
        "forest" -> AppTheme(
            ThemeColors.Forest.primary,
            ThemeColors.Forest.surface,
            ThemeColors.Forest.card,
            ThemeColors.Forest.positive,
            ThemeColors.Forest.negative,
            ThemeColors.Forest.text,
            ThemeColors.Forest.textSecondary,
            "Forest",
        )
        "sunset" -> AppTheme(
            ThemeColors.Sunset.primary,
            ThemeColors.Sunset.surface,
            ThemeColors.Sunset.card,
            ThemeColors.Sunset.positive,
            ThemeColors.Sunset.negative,
            ThemeColors.Sunset.text,
            ThemeColors.Sunset.textSecondary,
            "Sunset",
        )
        "cyberpunk" -> AppTheme(
            ThemeColors.Cyberpunk.primary,
            ThemeColors.Cyberpunk.surface,
            ThemeColors.Cyberpunk.card,
            ThemeColors.Cyberpunk.positive,
            ThemeColors.Cyberpunk.negative,
            ThemeColors.Cyberpunk.text,
            ThemeColors.Cyberpunk.textSecondary,
            "Cyberpunk",
        )
        "amoled" -> AppTheme(
            ThemeColors.Amoled.primary,
            ThemeColors.Amoled.surface,
            ThemeColors.Amoled.card,
            ThemeColors.Amoled.positive,
            ThemeColors.Amoled.negative,
            ThemeColors.Amoled.text,
            ThemeColors.Amoled.textSecondary,
            "Amoled",
        )
        "light" -> AppTheme(
            ThemeColors.Light.primary,
            ThemeColors.Light.surface,
            ThemeColors.Light.card,
            ThemeColors.Light.positive,
            ThemeColors.Light.negative,
            ThemeColors.Light.text,
            ThemeColors.Light.textSecondary,
            "Light",
        )
        "royal" -> AppTheme(
            ThemeColors.Royal.primary,
            ThemeColors.Royal.surface,
            ThemeColors.Royal.card,
            ThemeColors.Royal.positive,
            ThemeColors.Royal.negative,
            ThemeColors.Royal.text,
            ThemeColors.Royal.textSecondary,
            "Royal",
        )
        "monochrome" -> AppTheme(
            ThemeColors.Monochrome.primary,
            ThemeColors.Monochrome.surface,
            ThemeColors.Monochrome.card,
            ThemeColors.Monochrome.positive,
            ThemeColors.Monochrome.negative,
            ThemeColors.Monochrome.text,
            ThemeColors.Monochrome.textSecondary,
            "Monochrome",
        )
        "aurora" -> AppTheme(
            ThemeColors.Aurora.primary,
            ThemeColors.Aurora.surface,
            ThemeColors.Aurora.card,
            ThemeColors.Aurora.positive,
            ThemeColors.Aurora.negative,
            ThemeColors.Aurora.text,
            ThemeColors.Aurora.textSecondary,
            "Aurora",
        )
        "slate" -> AppTheme(
            ThemeColors.Slate.primary,
            ThemeColors.Slate.surface,
            ThemeColors.Slate.card,
            ThemeColors.Slate.positive,
            ThemeColors.Slate.negative,
            ThemeColors.Slate.text,
            ThemeColors.Slate.textSecondary,
            "Slate",
        )
        "dynamic" -> AppTheme(
            // Fallback only — MainActivity replaces this from the live ColorScheme.
            ThemeColors.Default.primary,
            ThemeColors.Default.surface,
            ThemeColors.Default.card,
            ThemeColors.Default.positive,
            ThemeColors.Default.negative,
            ThemeColors.Default.text,
            ThemeColors.Default.textSecondary,
            "Dynamic",
        )
        else -> AppTheme(
            ThemeColors.Default.primary,
            ThemeColors.Default.surface,
            ThemeColors.Default.card,
            ThemeColors.Default.positive,
            ThemeColors.Default.negative,
            ThemeColors.Default.text,
            ThemeColors.Default.textSecondary,
            DEFAULT_THEME_ID,
        )
    }
}

/** Keep LocalAppTheme in sync with Material You when Dynamic is selected. */
fun ColorScheme.toAppTheme(name: String = "Dynamic"): AppTheme {
    val bg = background
    val body = onBackground
    val secondary = onSurfaceVariant
    val light = bg.luminance() > 0.5f
    val cardColor = if (light) {
        Color.White.blendToward(surface, 0.06f)
    } else {
        body.copy(alpha = 0.10f).compositeOver(bg)
    }
    // Keep PnL readable, but nudge toward wallpaper primary (Material You harmonization).
    val basePositive = if (light) Color(0xFF2E7D32) else Color(0xFF00E676)
    val baseNegative = if (light) Color(0xFFC62828) else Color(0xFFFF6E6E)
    return AppTheme(
        primary = primary,
        surface = bg,
        card = cardColor,
        positive = harmonizeAccent(basePositive, primary, 0.16f),
        negative = harmonizeAccent(baseNegative, primary, 0.10f),
        text = body,
        textSecondary = secondary,
        name = name,
    )
}

fun AppTheme.toMaterialColorScheme(): ColorScheme {
    val onPrimaryColor = onPrimary
    val outlineColor = textSecondary.copy(alpha = 0.65f)
    val onPos = onPositive
    val onNeg = onNegative
    val pContainer = primaryContainer
    val onPContainer = onPrimaryContainer
    val elevated = surfaceElevated
    val secondaryAccent = textSecondary.blendToward(primary, 0.35f)

    return if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            primaryContainer = pContainer,
            onPrimaryContainer = onPContainer,
            secondary = secondaryAccent,
            onSecondary = contentColorForFill(secondaryAccent),
            secondaryContainer = secondaryAccent.copy(alpha = 0.16f).compositeOver(card),
            onSecondaryContainer = text,
            tertiary = positive,
            onTertiary = onPos,
            tertiaryContainer = positive.copy(alpha = 0.14f).compositeOver(card),
            onTertiaryContainer = positive.toneTowardBlack(0.4f),
            background = surface,
            onBackground = text,
            surface = card,
            onSurface = text,
            surfaceVariant = elevated,
            onSurfaceVariant = textSecondary,
            outline = outlineColor,
            outlineVariant = if (isLight) Color.Black.copy(alpha = 0.14f) else cardOutline,
            error = negative,
            onError = onNeg,
            errorContainer = negative.copy(alpha = 0.12f).compositeOver(card),
            onErrorContainer = negative.toneTowardBlack(0.25f),
            inverseSurface = text,
            inverseOnSurface = surface,
            inversePrimary = primary.toneTowardWhite(0.2f),
            scrim = Color.Black,
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            primaryContainer = pContainer,
            onPrimaryContainer = onPContainer,
            secondary = secondaryAccent,
            onSecondary = contentColorForFill(secondaryAccent),
            secondaryContainer = secondaryAccent.copy(alpha = 0.18f).compositeOver(card),
            onSecondaryContainer = text,
            tertiary = positive,
            onTertiary = onPos,
            tertiaryContainer = positive.copy(alpha = 0.16f).compositeOver(card),
            onTertiaryContainer = positive.toneTowardWhite(0.55f),
            background = surface,
            onBackground = text,
            surface = card,
            onSurface = text,
            surfaceVariant = elevated,
            onSurfaceVariant = textSecondary,
            outline = outlineColor,
            outlineVariant = cardOutline,
            error = negative,
            onError = onNeg,
            errorContainer = negative.copy(alpha = 0.16f).compositeOver(card),
            onErrorContainer = negative.toneTowardWhite(0.45f),
            inverseSurface = text,
            inverseOnSurface = surface,
            inversePrimary = primary.toneTowardBlack(0.15f),
            scrim = Color.Black,
        )
    }
}

fun getMaterialColorScheme(themeName: String, context: android.content.Context): ColorScheme {
    return if (isDynamicThemeId(themeName)) {
        // Material You — wallpaper-derived tonal palette (Android 12+).
        val isLight = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_NO
        if (isLight) {
            dynamicLightColorScheme(context)
        } else {
            dynamicDarkColorScheme(context)
        }
    } else {
        getTheme(themeName).toMaterialColorScheme()
    }
}
