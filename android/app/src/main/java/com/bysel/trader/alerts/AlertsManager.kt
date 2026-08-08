package com.bysel.trader.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bysel.trader.MainActivity
import com.bysel.trader.R
import com.bysel.trader.data.models.Alert
import com.bysel.trader.navigation.ShortcutActions
import java.util.concurrent.TimeUnit

private const val CHANNEL_PRICE_ALERTS = "bysel_price_alerts"
private const val CHANNEL_PRACTICE = "bysel_practice"
private const val CHANNEL_LEGACY = "bysel_alerts"
private const val GROUP_PRICE_ALERTS = "bysel_price_alerts_group"
private const val NOTIF_ID_BASE = 10_000
private const val TEST_NOTIF_ID = 19_999
private const val PRICE_SUMMARY_ID = 19_998
const val PREFS_ALERTS = "bysel_settings"
const val PREF_PRICE_ALERT_NOTIFICATIONS = "price_alert_notifications_enabled"

class AlertsManager(private val context: Context) {

    private val nmCompat = NotificationManagerCompat.from(context)
    private val prefs = context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
    private val accentColor: Int by lazy {
        ContextCompat.getColor(context, R.color.notification_accent)
    }

    init {
        createChannelsIfNeeded()
        scheduleBackgroundAlertWorker()
    }

    private fun createChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRICE_ALERTS,
                "Price alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Triggered when a watched stock crosses your price level"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRACTICE,
                "Practice & system",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Test notifications and practice reminders"
            },
        )
        // Keep legacy channel for users who already customized it.
        if (nm.getNotificationChannel(CHANNEL_LEGACY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LEGACY,
                    "BYSEL Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun arePriceAlertNotificationsEnabled(): Boolean =
        prefs.getBoolean(PREF_PRICE_ALERT_NOTIFICATIONS, true)

    fun setPriceAlertNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_PRICE_ALERT_NOTIFICATIONS, enabled).apply()
    }

    fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun areSystemNotificationsEnabled(): Boolean = nmCompat.areNotificationsEnabled()

    fun canDeliverNotifications(): Boolean =
        arePriceAlertNotificationsEnabled() &&
            hasPostNotificationPermission() &&
            areSystemNotificationsEnabled()

    fun notificationStatusLabel(): String = when {
        !arePriceAlertNotificationsEnabled() -> "Price alert notifications are off"
        !hasPostNotificationPermission() -> "Permission needed to show alerts"
        !areSystemNotificationsEnabled() -> "Notifications blocked in system settings"
        else -> "Price alerts can notify on this device"
    }

    fun sendPriceAlert(alert: Alert, price: Double) {
        if (!canDeliverNotifications()) return

        val symbol = alert.symbol.trim().uppercase()
        val threshold = String.format("%.2f", alert.thresholdPrice)
        val now = String.format("%.2f", price)
        // Title = most important scan line (≤30 chars); keep app name out of the title.
        val title = symbol.take(30)
        val body = when (alert.alertType.uppercase()) {
            "ABOVE" -> "Crossed above ₹$threshold · now ₹$now"
            "BELOW" -> "Dropped below ₹$threshold · now ₹$now"
            else -> "Alert level hit · now ₹$now"
        }
        notify(
            id = NOTIF_ID_BASE + (alert.id and 0x0FFF),
            title = title,
            body = body,
            channelId = CHANNEL_PRICE_ALERTS,
            shortcutAction = ShortcutActions.PRICE_ALERTS,
            symbol = symbol,
            category = NotificationCompat.CATEGORY_STATUS,
            groupKey = GROUP_PRICE_ALERTS,
            postGroupSummary = true,
        )
    }

    /** Used from Settings so users can verify permission + channel wiring. */
    fun sendTestNotification(): Boolean {
        if (!hasPostNotificationPermission() || !areSystemNotificationsEnabled()) return false
        notify(
            id = TEST_NOTIF_ID,
            title = "Notifications work",
            body = "You’ll get a similar banner when a price alert triggers.",
            channelId = CHANNEL_PRACTICE,
            shortcutAction = ShortcutActions.PRICE_ALERTS,
            symbol = null,
            category = NotificationCompat.CATEGORY_STATUS,
            groupKey = null,
            postGroupSummary = false,
        )
        return true
    }

    private fun notify(
        id: Int,
        title: String,
        body: String,
        channelId: String,
        shortcutAction: String,
        symbol: String?,
        category: String,
        groupKey: String?,
        postGroupSummary: Boolean,
    ) {
        val contentPending = activityPending(id, shortcutAction, symbol)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setShowWhen(true)

        if (!groupKey.isNullOrBlank()) {
            builder.setGroup(groupKey)
        }

        try {
            nmCompat.notify(id, builder.build())
            if (postGroupSummary && !groupKey.isNullOrBlank()) {
                postPriceAlertSummary(groupKey)
            }
        } catch (_: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS
        }
    }

    private fun postPriceAlertSummary(groupKey: String) {
        val summary = NotificationCompat.Builder(context, CHANNEL_PRICE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle("Price alerts")
            .setContentText("Market level crossed")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText("Price alerts"),
            )
            .setContentIntent(
                activityPending(PRICE_SUMMARY_ID, ShortcutActions.PRICE_ALERTS, symbol = null),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nmCompat.notify(PRICE_SUMMARY_ID, summary)
        } catch (_: SecurityException) {
        }
    }

    private fun activityPending(
        requestCode: Int,
        shortcutAction: String,
        symbol: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ShortcutActions.EXTRA_ACTION, shortcutAction)
            if (!symbol.isNullOrBlank()) {
                putExtra(ShortcutActions.EXTRA_ALERT_SYMBOL, symbol.trim().uppercase())
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleBackgroundAlertWorker() {
        try {
            val work = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .addTag("bysel_alerts_poll")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("bysel_alerts_poll", ExistingPeriodicWorkPolicy.KEEP, work)
        } catch (_: Exception) {
        }
    }
}
