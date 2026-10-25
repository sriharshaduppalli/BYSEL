package com.bysel.trader.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.ui.components.CandlestickChart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.sin

/**
 * The Options "Chart practice" tab crashed in 4.0.33 when the chart got a tiny
 * height, and the Futures full-screen view hid its buttons under the chart.
 * These keep both screens composable in cramped and large-font layouts.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class FoCandlePracticeLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun candles(n: Int): List<HistoryCandle> {
        val start = 1_760_000_000L
        return (0 until n).map { i ->
            val base = 24_500.0 + sin(i / 4.0) * 60.0
            HistoryCandle(
                timestamp = start + i * 900L,
                open = base,
                high = base + 25.0,
                low = base - 25.0,
                close = base + (if (i % 2 == 0) 12.0 else -9.0),
                volume = 1_000L + i,
            )
        }
    }

    @Test
    fun practiceScreen_shortHeight_stillShowsTransportButtons() {
        rule.setContent {
            Box(Modifier.width(360.dp).height(300.dp)) {
                FoCandlePractice(
                    symbol = "NIFTY",
                    expiry = "2026-10-29",
                    candles = candles(60),
                    loading = false,
                    iv = 0.16,
                    onReload = {},
                    onSymbol = {},
                    asScreen = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(hasText("Play") or hasText("Pause") or hasText("Check for bars")).assertExists()
        rule.onNodeWithText("Chart").assertIsDisplayed()
    }

    @Test
    fun practiceScreen_largeFontScale_composesWithoutThrowing() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 1.3f)) {
                Box(Modifier.width(360.dp).height(560.dp)) {
                    FoCandlePractice(
                        symbol = "RELIANCE",
                        expiry = "2026-10-29",
                        candles = candles(40),
                        loading = false,
                        iv = 0.2,
                        onReload = {},
                        onSymbol = {},
                        asScreen = true,
                        future = true,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(hasText("Play") or hasText("Pause") or hasText("Check for bars")).assertExists()
    }

    @Test
    fun candlestickChart_zeroHeight_doesNotCrash() {
        rule.setContent {
            Box(Modifier.width(360.dp).height(0.dp)) {
                CandlestickChart(history = candles(30), expandPlot = true)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun candlestickChart_singleBar_drawsAxisLabelsSafely() {
        rule.setContent {
            Box(Modifier.width(120.dp).height(40.dp)) {
                CandlestickChart(history = candles(1), rangeLabel = "5D")
            }
        }
        rule.waitForIdle()
    }
}
