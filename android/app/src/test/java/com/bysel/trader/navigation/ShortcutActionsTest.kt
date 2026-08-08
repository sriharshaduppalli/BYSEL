package com.bysel.trader.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutActionsTest {
    @Test
    fun tabForAction_mapsKnownShortcuts() {
        assertEquals(3, ShortcutActions.tabForAction(ShortcutActions.OPEN_PORTFOLIO))
        assertEquals(2, ShortcutActions.tabForAction(ShortcutActions.BUY_STOCK))
        assertEquals(4, ShortcutActions.tabForAction(ShortcutActions.MARKET_STATUS))
        assertEquals(7, ShortcutActions.tabForAction(ShortcutActions.PRICE_ALERTS))
    }

    @Test
    fun tabForAction_defaultsHome() {
        assertEquals(0, ShortcutActions.tabForAction(null))
        assertEquals(0, ShortcutActions.tabForAction(""))
        assertEquals(0, ShortcutActions.tabForAction("unknown"))
    }
}
