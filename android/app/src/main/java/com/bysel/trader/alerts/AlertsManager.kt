package com.bysel.trader.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.bysel.trader.R
import com.bysel.trader.data.models.Alert
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "bysel_alerts"
private const val CHANNEL_NAME = "BYSEL Alerts"
private const val NOTIF_ID_BASE = 1000
private const val TEST_NOTIF_ID = 1999
const val PREFS_ALERTS = "bysel_settings"
const val PREF_PRICE_ALERT_NOTIFICATIONS = "price_alert_notifications_enabled"

class AlertsManager(private val context: Context) {

    private val nmCompat = NotificationManagerCompat.from(context)
    private val prefs = context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)

    init {
        createChannelIfNeeded()
        scheduleBackgroundAlertWorker()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Price alerts and notifications from BYSEL"
            nm.createNotificationChannel(ch)
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

        val title = "Price alert: ${alert.symbol}"
        val body = when (alert.alertType.uppercase()) {
            "ABOVE" -> "${alert.symbol} crossed above ${alert.thresholdPrice} (now ${String.format("%.2f", price)})"
            "BELOW" -> "${alert.symbol} dropped below ${alert.thresholdPrice} (now ${String.format("%.2f", price)})"
            else -> "${alert.symbol} alert: ${String.format("%.2f", price)}"
        }
        notify(NOTIF_ID_BASE + (alert.id % 1000), title, body)
    }

    /** Used from Settings so users can verify permission + channel wiring. */
    fun sendTestNotification(): Boolean {
        if (!hasPostNotificationPermission() || !areSystemNotificationsEnabled()) return false
        notify(
            TEST_NOTIF_ID,
            "BYSEL notifications work",
            "You will get a similar banner when a price alert triggers.",
        )
        return true
    }

    private fun notify(id: Int, title: String, body: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nmCompat.notify(id, notif)
        } catch (_: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS
        }
    }

    private fun scheduleBackgroundAlertWorker() {
        try {
            val work = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .addTag("bysel_alerts_poll")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("bysel_alerts_poll", ExistingPeriodicWorkPolicy.KEEP, work)
        } catch (_: Exception) {
            // ignore if WorkManager not available at runtime
        }
    }
}
