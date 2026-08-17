package com.bysel.trader.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Device-local watchlist. SharedPreferences string (not StringSet) is the
 * synchronous cold-start source of truth; DataStore is a durable mirror.
 *
 * Keys are per [WatchlistSymbols.userKey] so anon vs logged-in do not clobber
 * each other — but an empty key never overwrites a last-good list.
 */
object WatchlistStore {
    private const val PREFS = "bysel_watchlist"
    private const val LEGACY_SET_KEY = "symbols"
    private const val DATASTORE_NAME = "bysel_watchlist_prefs"
    private val Context.watchlistDataStore by preferencesDataStore(DATASTORE_NAME)

    private fun prefsKey(userKey: String) = "symbols_$userKey"

    private fun dataStoreKey(userKey: String) = stringPreferencesKey("watchlist_$userKey")

    fun readSync(
        context: Context,
        userId: Int?,
        lastGood: List<String> = emptyList(),
    ): List<String> {
        val prefs = prefs(context)
        val key = WatchlistSymbols.userKey(userId)
        val owned = WatchlistSymbols.decode(prefs.getString(prefsKey(key), null))
        val legacy = readLegacyStringSet(prefs)
        val anon = if (key != "anon") {
            WatchlistSymbols.decode(prefs.getString(prefsKey("anon"), null))
        } else {
            emptyList()
        }
        val fromDisk = when {
            owned.isNotEmpty() -> owned
            legacy.isNotEmpty() -> legacy
            anon.isNotEmpty() -> anon
            else -> emptyList()
        }
        val resolved = WatchlistSymbols.coalesce(fromDisk, lastGood, allowEmpty = false)
        if (resolved.isNotEmpty() && owned.isEmpty()) {
            // Migrate legacy / inherit anon into the current user key without waiting.
            persistPrefs(prefs, key, resolved)
        }
        return resolved
    }

    fun writeSync(
        context: Context,
        userId: Int?,
        symbols: List<String>,
        lastGood: List<String>,
        allowEmpty: Boolean,
    ): List<String> {
        val toSave = WatchlistSymbols.coalesce(symbols, lastGood, allowEmpty)
        if (toSave.isEmpty() && !allowEmpty) {
            return WatchlistSymbols.normalizeAll(lastGood)
        }
        persistPrefs(prefs(context), WatchlistSymbols.userKey(userId), toSave)
        return toSave
    }

    suspend fun readDataStore(context: Context, userId: Int?): List<String> {
        val key = WatchlistSymbols.userKey(userId)
        val app = context.applicationContext
        return try {
            app.watchlistDataStore.data.map { prefs ->
                WatchlistSymbols.decode(prefs[dataStoreKey(key)])
            }.firstOrNull().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun writeDataStore(context: Context, userId: Int?, symbols: List<String>) {
        val key = WatchlistSymbols.userKey(userId)
        val encoded = WatchlistSymbols.encode(symbols)
        try {
            context.applicationContext.watchlistDataStore.edit { prefs ->
                if (encoded.isBlank()) {
                    prefs.remove(dataStoreKey(key))
                } else {
                    prefs[dataStoreKey(key)] = encoded
                }
            }
        } catch (_: Exception) {
            // Prefs already hold the list; DataStore is a mirror.
        }
    }

    private fun persistPrefs(prefs: SharedPreferences, userKey: String, symbols: List<String>) {
        val encoded = WatchlistSymbols.encode(symbols)
        val editor = prefs.edit().putString(prefsKey(userKey), encoded)
        if (symbols.isNotEmpty()) {
            // Drop the StringSet once we have a durable string copy.
            editor.remove(LEGACY_SET_KEY)
        }
        editor.commit()
    }

    private fun readLegacyStringSet(prefs: SharedPreferences): List<String> {
        val stored = prefs.getStringSet(LEGACY_SET_KEY, null) ?: return emptyList()
        // Copy immediately — Android StringSet is the live internal instance.
        return WatchlistSymbols.normalizeAll(HashSet(stored))
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
