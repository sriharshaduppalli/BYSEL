package com.bysel.trader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.pinnedStocksDataStore by preferencesDataStore(name = "pinned_stocks")

object PinnedStocksStore {
    private val PINNED_KEY = stringSetPreferencesKey("pinned_symbols")

    fun getPinnedStocks(context: Context): Flow<Set<String>> =
        context.pinnedStocksDataStore.data.map { prefs ->
            prefs[PINNED_KEY] ?: emptySet()
        }

    suspend fun setPinnedStocks(context: Context, symbols: Set<String>) {
        context.pinnedStocksDataStore.edit { prefs ->
            prefs[PINNED_KEY] = symbols
        }
    }
}
