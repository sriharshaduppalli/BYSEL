package com.bysel.trader.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language choice for menus and practice screens.
 *
 * Stored in SharedPreferences and applied in MainActivity.attachBaseContext, so
 * it works on every Android version without the per-app language service.
 * "system" means follow the phone. The activity recreates when the choice
 * changes.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val PREFS = "bysel_settings"
    private const val KEY = "app_language"

    /** code → label shown in the picker, in the language itself. */
    val choices: List<Pair<String, String>> = listOf(
        SYSTEM to "",
        "en" to "English",
        "hi" to "हिन्दी",
        "te" to "తెలుగు",
    )

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** Context whose resources resolve in the chosen language, or [base] when following the phone. */
    fun wrap(base: Context): Context {
        val code = current(base)
        if (code == SYSTEM || code.isBlank()) return base
        val locale = Locale.forLanguageTag(code)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
