
package com.bysel.trader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PinnedWidgetsStore {
    private const val DATASTORE_NAME = "pinned_widgets_prefs"
    private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

    private val PORTFOLIO_PINNED_KEY = booleanPreferencesKey("portfolio_pinned")
    private val NEWS_PINNED_KEY = booleanPreferencesKey("news_pinned")
    private val WATCHLIST_PINNED_KEY = booleanPreferencesKey("watchlist_pinned")
    private val WIDGET_ORDER_KEY = stringPreferencesKey("widget_order")
    private val INDEX_CHART_KEY = booleanPreferencesKey("index_chart_on")
    private val CHART_MOTION_KEY = booleanPreferencesKey("chart_motion_on")
    private val INDEX_SYMBOL_KEY = stringPreferencesKey("home_index_symbol")

    fun isPortfolioPinned(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[PORTFOLIO_PINNED_KEY] ?: false }

    suspend fun setPortfolioPinned(context: Context, pinned: Boolean) {
        context.dataStore.edit { it[PORTFOLIO_PINNED_KEY] = pinned }
    }

    fun isNewsPinned(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[NEWS_PINNED_KEY] ?: false }

    suspend fun setNewsPinned(context: Context, pinned: Boolean) {
        context.dataStore.edit { it[NEWS_PINNED_KEY] = pinned }
    }

    fun isWatchlistPinned(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[WATCHLIST_PINNED_KEY] ?: false }

    suspend fun setWatchlistPinned(context: Context, pinned: Boolean) {
        context.dataStore.edit { it[WATCHLIST_PINNED_KEY] = pinned }
    }

    fun getWidgetOrder(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[WIDGET_ORDER_KEY]?.split(",")?.filter { it.isNotBlank() } ?: listOf("portfolio", "news", "watchlist")
        }

    suspend fun setWidgetOrder(context: Context, order: List<String>) {
        context.dataStore.edit { it[WIDGET_ORDER_KEY] = order.joinToString(",") }
    }

    fun isIndexChartOn(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[INDEX_CHART_KEY] ?: true }

    suspend fun setIndexChartOn(context: Context, on: Boolean) {
        context.dataStore.edit { it[INDEX_CHART_KEY] = on }
    }

    fun isChartMotionOn(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[CHART_MOTION_KEY] ?: true }

    suspend fun setChartMotionOn(context: Context, on: Boolean) {
        context.dataStore.edit { it[CHART_MOTION_KEY] = on }
    }

    fun homeIndexSymbol(context: Context): Flow<String> =
        context.dataStore.data.map { prefs ->
            val saved = prefs[INDEX_SYMBOL_KEY]?.trim()?.uppercase().orEmpty()
            if (saved in HOME_INDEX_SYMBOLS) saved else "NIFTY50"
        }

    suspend fun setHomeIndexSymbol(context: Context, symbol: String) {
        val normalized = symbol.trim().uppercase()
        if (normalized !in HOME_INDEX_SYMBOLS) return
        context.dataStore.edit { it[INDEX_SYMBOL_KEY] = normalized }
    }

    val HOME_INDEX_SYMBOLS = setOf("NIFTY50", "SENSEX", "BANKNIFTY")
}