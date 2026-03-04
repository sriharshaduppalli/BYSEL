package com.bysel.trader.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bysel.trader.R
import com.bysel.trader.data.models.Alert
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "bysel_alerts"
private const val CHANNEL_NAME = "BYSEL Alerts"
private const val NOTIF_ID_BASE = 1000

class AlertsManager(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannelIfNeeded()
        scheduleBackgroundAlertWorker()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Price alerts and notifications from BYSEL"
            nm.createNotificationChannel(ch)
        }
    }

    fun sendPriceAlert(alert: Alert, price: Double) {
        val title = "Price alert: ${alert.symbol}"
        val body = when (alert.alertType.uppercase()) {
            "ABOVE" -> "${alert.symbol} crossed above ${alert.thresholdPrice} (now ${String.format("%.2f", price)})"
            "BELOW" -> "${alert.symbol} dropped below ${alert.thresholdPrice} (now ${String.format("%.2f", price)})"
            else -> "${alert.symbol} alert: ${String.format("%.2f", price)}"
        }

        // Intent to open the app when tapping the notification
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(context, alert.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BASE + (alert.id % 1000), notif)
    }

    private fun scheduleBackgroundAlertWorker() {
        try {
            // Poll every 15 minutes as a fallback (minimum interval enforced by WorkManager)
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
