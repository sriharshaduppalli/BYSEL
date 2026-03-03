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

            for (a in alerts) {
                try {
                    val r = repo.getQuote(a.symbol)
                    if (r is com.bysel.trader.data.repository.Result.Success) {
                        val price = r.data.last
                        when (a.alertType.uppercase()) {
                            "ABOVE" -> if (price >= a.thresholdPrice) alertsManager.sendPriceAlert(a, price)
                            "BELOW" -> if (price <= a.thresholdPrice) alertsManager.sendPriceAlert(a, price)
                            else -> {}
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
