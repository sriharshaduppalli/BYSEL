package com.bysel.trader.alerts

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.Alert
import com.bysel.trader.data.repository.TradingRepository
import kotlinx.coroutines.flow.first

private const val WORK_TAG = "bysel_alerts_poll"
private const val TAG = "BYSEL-Worker"

class AlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            val db = BYSELDatabase.getInstance(applicationContext)
            val repo = TradingRepository(db)
            val alerts = db.alertDao().getActiveAlerts().first()

            val alertsManager = AlertsManager(applicationContext)

            if (alerts.isEmpty()) return Result.success()

            // One batched request for every watched symbol rather than one request per alert.
            val symbols = alerts.map { it.symbol }.distinct()
            val quoteResult = repo.getQuotes(symbols).first { it !is com.bysel.trader.data.repository.Result.Loading }
            if (quoteResult !is com.bysel.trader.data.repository.Result.Success) {
                Log.w(TAG, "Alert poll could not fetch quotes for ${symbols.size} symbol(s)")
                return Result.retry()
            }
            val quoteBySymbol = quoteResult.data.associateBy { it.symbol.uppercase() }

            for (a in alerts) {
                val quote = quoteBySymbol[a.symbol.trim().uppercase()] ?: continue
                val price = quote.last
                try {
                    var triggered = false
                    when (a.alertType.uppercase()) {
                        "ABOVE" -> if (price >= a.thresholdPrice) {
                            alertsManager.sendPriceAlert(a, price)
                            triggered = true
                        }
                        "BELOW" -> if (price <= a.thresholdPrice) {
                            alertsManager.sendPriceAlert(a, price)
                            triggered = true
                        }
                        "PCT_UP" -> if (quote.pctChange >= a.thresholdPrice) {
                            alertsManager.sendPriceAlert(
                                a,
                                price,
                                "Session is up ${"%.2f".format(java.util.Locale.US, quote.pctChange)}%",
                            )
                            triggered = true
                        }
                        "PCT_DOWN" -> if (quote.pctChange <= -kotlin.math.abs(a.thresholdPrice)) {
                            alertsManager.sendPriceAlert(
                                a,
                                price,
                                "Session is down ${"%.2f".format(java.util.Locale.US, quote.pctChange)}%",
                            )
                            triggered = true
                        }
                        "VOLUME" -> if ((quote.volume ?: 0L) >= a.thresholdPrice.toLong()) {
                            alertsManager.sendPriceAlert(a, price, "Volume reached ${quote.volume} shares")
                            triggered = true
                        }
                        else -> {}
                    }
                    if (triggered) {
                        try {
                            repo.deactivateAlert(a.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to deactivate alert ${a.id}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking alert ${a.id} ${a.symbol}", e)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
