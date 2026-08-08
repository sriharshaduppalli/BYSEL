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

/** Ordered picker labels. Only one Default; Dynamic is a separate Material You option. */
val allThemes = listOf(
    DEFAULT_THEME_ID,
    "Amoled",
    "Light",
    "Ocean",
    "Forest",
    "Sunset",
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
object ThemeColors {
    object Default {
        val primary = Color(0xFF42A5F5)
        val surface = Color(0xFF0D0D0D)
        val card = Color(0xFF1A1A1A)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF5252)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFFB0BEC5)
    }

    object Ocean {
        val primary = Color(0xFF26C6DA)
        val surface = Color(0xFF071018)
        val card = Color(0xFF0E2436)
        val positive = Color(0xFF1DE9B6)
        val negative = Color(0xFFFF8A65)
        val text = Color(0xFFE0F7FA)
        val textSecondary = Color(0xFF9ADCE6)
    }

    object Forest {
        // Card kept dark (not saturated green) so green/red PnL and secondary text stay readable.
        val primary = Color(0xFF66BB6A)
        val surface = Color(0xFF08140E)
        val card = Color(0xFF12241A)
        val positive = Color(0xFF69F0AE)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFE8F5E9)
        val textSecondary = Color(0xFFA5D6A7)
    }

    object Sunset {
        val primary = Color(0xFFFF8F00)
        val surface = Color(0xFF140C00)
        val card = Color(0xFF2A1600)
        val positive = Color(0xFFFFD54F)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFFFF3E0)
        val textSecondary = Color(0xFFFFCC80)
    }

    object Cyberpunk {
        val primary = Color(0xFFE040FB)
        val surface = Color(0xFF070A1A)
        val card = Color(0xFF121A33)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF4081)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF80DEEA)
    }

    object Amoled {
        val primary = Color(0xFFB388FF)
        val surface = Color(0xFF000000)
        val card = Color(0xFF121212)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF5252)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFFB0BEC5)
    }

    object Light {
        val primary = Color(0xFF1565C0)
        val surface = Color(0xFFF5F7FA)
        val card = Color(0xFFFFFFFF)
        val positive = Color(0xFF2E7D32)
        val negative = Color(0xFFC62828)
        val text = Color(0xFF121212)
        val textSecondary = Color(0xFF5F6368)
    }

    object Royal {
        val primary = Color(0xFFCE93D8)
        val surface = Color(0xFF120816)
        val card = Color(0xFF241530)
        val positive = Color(0xFFB2FF59)
        val negative = Color(0xFFFF80AB)
        val text = Color(0xFFF8EAFB)
        val textSecondary = Color(0xFFD1A3DD)
    }

    object Monochrome {
        // Keep grayscale chrome, but PnL must remain clearly readable.
        val primary = Color(0xFFE0E0E0)
        val surface = Color(0xFF0A0A0A)
        val card = Color(0xFF1C1C1C)
        val positive = Color(0xFF69F0AE)
        val negative = Color(0xFFFF8A80)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFFBDBDBD)
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
    val cardColor = surface
    val body = onBackground
    val secondary = onSurfaceVariant
    return AppTheme(
        primary = primary,
        surface = bg,
        card = cardColor,
        positive = if (bg.luminance() > 0.5f) Color(0xFF2E7D32) else Color(0xFF00E676),
        negative = if (bg.luminance() > 0.5f) Color(0xFFC62828) else Color(0xFFFF5252),
        text = body,
        textSecondary = secondary,
        name = name,
    )
}

fun AppTheme.toMaterialColorScheme(): ColorScheme {
    val onPrimaryColor = onPrimary
    val outlineColor = textSecondary.copy(alpha = 0.65f)
    // Tertiary carries "up / positive" so Material components can tint gains without hardcoding.
    val onPositive = if (positive.luminance() > 0.55f) Color(0xFF121212) else Color.White
    val onNegative = if (negative.luminance() > 0.55f) Color(0xFF121212) else Color.White

    return if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            secondary = primary,
            onSecondary = onPrimaryColor,
            tertiary = positive,
            onTertiary = onPositive,
            background = surface,
            onBackground = text,
            surface = card,
            onSurface = text,
            surfaceVariant = surface,
            onSurfaceVariant = textSecondary,
            outline = outlineColor,
            error = negative,
            onError = onNegative,
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            secondary = primary,
            onSecondary = onPrimaryColor,
            tertiary = positive,
            onTertiary = onPositive,
            background = surface,
            onBackground = text,
            surface = card,
            onSurface = text,
            surfaceVariant = card,
            onSurfaceVariant = textSecondary,
            outline = outlineColor,
            error = negative,
            onError = onNegative,
        )
    }
}

fun getMaterialColorScheme(themeName: String, context: android.content.Context): ColorScheme {
    return if (isDynamicThemeId(themeName)) {
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
