package com.bysel.trader.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bysel.trader.navigation.ShortcutActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lightweight Compose smoke: proves UI-test infra + shortcut tab mapping stay wired.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutActionsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun priceAlertsShortcut_mapsToAlertsTabLabel() {
        val tab = ShortcutActions.tabForAction(ShortcutActions.PRICE_ALERTS)
        composeRule.setContent {
            Text(text = "tab=$tab")
        }
        composeRule.onNodeWithText("tab=7").assertIsDisplayed()
    }
}
