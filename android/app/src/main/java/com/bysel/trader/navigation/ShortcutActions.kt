package com.bysel.trader.navigation

/**
 * Maps launcher shortcut / notification extras to primary tab indices used by [com.bysel.trader.MainActivity].
 */
object ShortcutActions {
    const val EXTRA_ACTION = "shortcut_action"
    const val EXTRA_ALERT_SYMBOL = "alert_symbol"

    const val OPEN_PORTFOLIO = "open_portfolio"
    const val BUY_STOCK = "buy_stock"
    const val MARKET_STATUS = "market_status"
    const val PRICE_ALERTS = "price_alerts"

    /** Tab ids aligned with BYSELApp navigation (0–4 pager; 7 = Alerts via More). */
    fun tabForAction(action: String?): Int = when (action) {
        OPEN_PORTFOLIO -> 3
        BUY_STOCK -> 2
        MARKET_STATUS -> 4
        PRICE_ALERTS -> 7
        else -> 0
    }
}
