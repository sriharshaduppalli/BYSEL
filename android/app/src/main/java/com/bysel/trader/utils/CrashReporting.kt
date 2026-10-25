package com.bysel.trader.utils

import android.content.Context

/**
 * Preference stub. Crashlytics is not in this release because Play rejects the
 * bundle while Firebase Sessions is merged in. The switch is still stored so
 * the choice survives once reporting is wired back.
 */
object CrashReporting {
    private const val PREFS = "bysel_settings"
    private const val KEY_ENABLED = "crash_reports_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun apply(context: Context) = Unit

    fun screen(name: String) = Unit

    fun record(error: Throwable, note: String? = null) = Unit
}
