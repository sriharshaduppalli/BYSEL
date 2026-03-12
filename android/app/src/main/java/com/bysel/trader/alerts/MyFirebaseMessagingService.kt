package com.bysel.trader.alerts

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val TAG = "BYSEL-FCM"

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token locally for future server registration
        Log.d(TAG, "New FCM token: $token")
        // TODO: Register token with server for push notifications
        // Call backend endpoint: POST /auth/register-fcm-token with { "token": token }
        // This enables server-side push delivery for price alerts and portfolio updates
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        try {
            // Prefer data payload for structured alerts
            val data = message.data
            if (data.isNotEmpty()) {
                val symbol = data["symbol"] ?: ""
                val price = data["price"]?.toDoubleOrNull()
                val alertId = data["alertId"]?.toIntOrNull() ?: 0
                val alertType = data["alertType"] ?: ""
                val threshold = data["threshold"]?.toDoubleOrNull() ?: 0.0

                if (symbol.isNotBlank() && price != null) {
                    // Build a lightweight Alert-like object for display
                    val alert = com.bysel.trader.data.models.Alert(id = alertId, symbol = symbol, thresholdPrice = threshold, alertType = alertType)
                    AlertsManager(applicationContext).sendPriceAlert(alert, price)
                    return
                }
            }

            // Fallback to notification payload
            message.notification?.let { notif ->
                val alert = com.bysel.trader.data.models.Alert(id = 0, symbol = notif.title ?: "BYSEL", thresholdPrice = 0.0, alertType = "")
                AlertsManager(applicationContext).sendPriceAlert(alert, notif.body?.toDoubleOrNull() ?: 0.0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle FCM message", e)
        }
    }
}
