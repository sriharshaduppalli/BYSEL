package com.bysel.trader.utils

import android.content.Context
import com.bysel.trader.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reporting with a user switch.
 *
 * Only release builds send reports, and only while the user has not turned the
 * switch off in Settings. Reports carry stack traces and the screen tag; they
 * never carry the user's name, mobile number, or paper P&L.
 */
object CrashReporting {
    private const val PREFS = "bysel_settings"
    private const val KEY_ENABLED = "crash_reports_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context)
    }

    /** Called from Application.onCreate and again whenever the switch changes. */
    fun apply(context: Context) {
        val on = !BuildConfig.DEBUG && isEnabled(context)
        runCatching {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on
        }
    }

    /** Tag the last screen so a crash report says where the user was. */
    fun screen(name: String) {
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey("screen", name) }
    }

    /** Record a caught, non-fatal problem without crashing. */
    fun record(error: Throwable, note: String? = null) {
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            if (!note.isNullOrBlank()) c.log(note)
            c.recordException(error)
        }
    }
}
