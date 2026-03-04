package com.bysel.trader.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// CompositionLocal for app theme - accessible from any composable
val LocalAppTheme = compositionLocalOf { getTheme("default") }

// Define color palettes
object ThemeColors {
    // Default Dark Theme
    object Default {
        val primary = Color(0xFF1E88E5)
        val surface = Color(0xFF0D0D0D)
        val card = Color(0xFF1A1A1A)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF5252)
        val text = Color.White
        val textSecondary = Color.Gray
    }

    // Ocean Theme
    object Ocean {
        val primary = Color(0xFF00BCD4)
        val surface = Color(0xFF0A1628)
        val card = Color(0xFF0F2A40)
        val positive = Color(0xFF1DE9B6)
        val negative = Color(0xFFFF6E40)
        val text = Color(0xFFE0F7FA)
        val textSecondary = Color(0xFF80DEEA)
    }

    // Forest Theme
    object Forest {
        val primary = Color(0xFF4CAF50)
        val surface = Color(0xFF0D2818)
        val card = Color(0xFF1B5E20)
        val positive = Color(0xFF66BB6A)
        val negative = Color(0xFFEF5350)
        val text = Color(0xFFC8E6C9)
        val textSecondary = Color(0xFF81C784)
    }

    // Sunset Theme
    object Sunset {
        val primary = Color(0xFFFF6F00)
        val surface = Color(0xFF1A0F00)
        val card = Color(0xFF331A00)
        val positive = Color(0xFFFFB74D)
        val negative = Color(0xFFEF5350)
        val text = Color(0xFFFFE0B2)
        val textSecondary = Color(0xFFFFCC80)
    }

    // Cyberpunk Theme
    object Cyberpunk {
        val primary = Color(0xFFFF00FF)
        val surface = Color(0xFF0A0E27)
        val card = Color(0xFF16213E)
        val positive = Color(0xFF00FF88)
        val negative = Color(0xFFFF0080)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF00FFFF)
    }

    // Amoled Theme (Pure black for OLED)
    object Amoled {
        val primary = Color(0xFF7C4DFF)
        val surface = Color(0xFF000000)
        val card = Color(0xFF0A0A0A)
        val positive = Color(0xFF00E676)
        val negative = Color(0xFFFF1744)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFFB0BEC5)
    }

    // Light Theme
    object Light {
        val primary = Color(0xFF1976D2)
        val surface = Color(0xFFFAFAFA)
        val card = Color(0xFFFFFFFF)
        val positive = Color(0xFF4CAF50)
        val negative = Color(0xFFF44336)
        val text = Color(0xFF212121)
        val textSecondary = Color(0xFF757575)
    }

    // Royal Purple Theme
    object Royal {
        val primary = Color(0xFF9C27B0)
        val surface = Color(0xFF1A0D1F)
        val card = Color(0xFF2D1B3D)
        val positive = Color(0xFFCE93D8)
        val negative = Color(0xFFE91E63)
        val text = Color(0xFFF3E5F5)
        val textSecondary = Color(0xFFBA68C8)
    }

    // Monochrome Theme
    object Monochrome {
        val primary = Color(0xFFFFFFFF)
        val surface = Color(0xFF0D0D0D)
        val card = Color(0xFF1F1F1F)
        val positive = Color(0xFFEEEEEE)
        val negative = Color(0xFF424242)
        val text = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF9E9E9E)
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
    val name: String
)

fun getTheme(themeName: String): AppTheme {
    return when (themeName.lowercase()) {
        "ocean" -> AppTheme(
            ThemeColors.Ocean.primary,
            ThemeColors.Ocean.surface,
            ThemeColors.Ocean.card,
            ThemeColors.Ocean.positive,
            ThemeColors.Ocean.negative,
            ThemeColors.Ocean.text,
            ThemeColors.Ocean.textSecondary,
            "Ocean"
        )
        "forest" -> AppTheme(
            ThemeColors.Forest.primary,
            ThemeColors.Forest.surface,
            ThemeColors.Forest.card,
            ThemeColors.Forest.positive,
            ThemeColors.Forest.negative,
            ThemeColors.Forest.text,
            ThemeColors.Forest.textSecondary,
            "Forest"
        )
        "sunset" -> AppTheme(
            ThemeColors.Sunset.primary,
            ThemeColors.Sunset.surface,
            ThemeColors.Sunset.card,
            ThemeColors.Sunset.positive,
            ThemeColors.Sunset.negative,
            ThemeColors.Sunset.text,
            ThemeColors.Sunset.textSecondary,
            "Sunset"
        )
        "cyberpunk" -> AppTheme(
            ThemeColors.Cyberpunk.primary,
            ThemeColors.Cyberpunk.surface,
            ThemeColors.Cyberpunk.card,
            ThemeColors.Cyberpunk.positive,
            ThemeColors.Cyberpunk.negative,
            ThemeColors.Cyberpunk.text,
            ThemeColors.Cyberpunk.textSecondary,
            "Cyberpunk"
        )
        "amoled" -> AppTheme(
            ThemeColors.Amoled.primary,
            ThemeColors.Amoled.surface,
            ThemeColors.Amoled.card,
            ThemeColors.Amoled.positive,
            ThemeColors.Amoled.negative,
            ThemeColors.Amoled.text,
            ThemeColors.Amoled.textSecondary,
            "Amoled"
        )
        "light" -> AppTheme(
            ThemeColors.Light.primary,
            ThemeColors.Light.surface,
            ThemeColors.Light.card,
            ThemeColors.Light.positive,
            ThemeColors.Light.negative,
            ThemeColors.Light.text,
            ThemeColors.Light.textSecondary,
            "Light"
        )
        "royal" -> AppTheme(
            ThemeColors.Royal.primary,
            ThemeColors.Royal.surface,
            ThemeColors.Royal.card,
            ThemeColors.Royal.positive,
            ThemeColors.Royal.negative,
            ThemeColors.Royal.text,
            ThemeColors.Royal.textSecondary,
            "Royal"
        )
        "monochrome" -> AppTheme(
            ThemeColors.Monochrome.primary,
            ThemeColors.Monochrome.surface,
            ThemeColors.Monochrome.card,
            ThemeColors.Monochrome.positive,
            ThemeColors.Monochrome.negative,
            ThemeColors.Monochrome.text,
            ThemeColors.Monochrome.textSecondary,
            "Monochrome"
        )
        else -> AppTheme(
            ThemeColors.Default.primary,
            ThemeColors.Default.surface,
            ThemeColors.Default.card,
            ThemeColors.Default.positive,
            ThemeColors.Default.negative,
            ThemeColors.Default.text,
            ThemeColors.Default.textSecondary,
            "Default"
        )
    }
}

val allThemes = listOf("Default", "Amoled", "Light", "Ocean", "Forest", "Sunset", "Royal", "Cyberpunk", "Monochrome")
