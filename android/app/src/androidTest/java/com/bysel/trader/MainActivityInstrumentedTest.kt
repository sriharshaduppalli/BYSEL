package com.bysel.trader

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test
    fun launchActivity_notNull() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.use {
            it.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }
}
