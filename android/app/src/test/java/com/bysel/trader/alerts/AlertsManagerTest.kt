package com.bysel.trader.alerts

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Test
import org.robolectric.annotation.Config

@Config(manifest = Config.NONE)
class AlertsManagerTest {

    @Test
    fun sendPriceAlert_doesNotThrow() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val mgr = AlertsManager(ctx)
        val alert = com.bysel.trader.data.models.Alert(id = 123, symbol = "TEST", thresholdPrice = 100.0, alertType = "ABOVE")
        // Should not throw when sending a notification (Robolectric provides a shadowed NotificationManager)
        mgr.sendPriceAlert(alert, 123.45)
        Assert.assertTrue(true)
    }
}
