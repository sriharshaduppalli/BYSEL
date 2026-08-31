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
 * Keys are per [WatchlistSymbols.userKey] plus a device last-known copy.
 * Reads union every stored owner so an empty anon/new-user key after update
 * or session expiry cannot hide names still on disk.
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
        val keyed = readAllKeyedLists(prefs)
        val resolved = WatchlistSymbols.recoverFromKeyedStores(
            currentUserId = userId,
            keyedLists = keyed,
            legacy = readLegacyStringSet(prefs),
            lastGood = lastGood,
        )
        val key = WatchlistSymbols.userKey(userId)
        val owned = keyed[key].orEmpty()
        val device = keyed[WatchlistSymbols.DEVICE_KEY].orEmpty()
        if (resolved.isNotEmpty() && (owned.isEmpty() || device.isEmpty())) {
            // Promote orphaned u_* / anon / device copies onto the active keys.
            persistOwnedAndDevice(prefs, key, resolved)
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
        val sharedPrefs = prefs(context)
        if (toSave.isEmpty()) {
            // Explicit user clear — drop every owner key so recover cannot resurrect.
            clearAllWatchlistPrefs(sharedPrefs)
            return emptyList()
        }
        persistOwnedAndDevice(sharedPrefs, WatchlistSymbols.userKey(userId), toSave)
        return toSave
    }

    suspend fun readDataStore(context: Context, userId: Int?): List<String> {
        val app = context.applicationContext
        return try {
            app.watchlistDataStore.data.map { prefs ->
                val keyed = prefs.asMap().mapNotNull { (prefKey, value) ->
                    val name = prefKey.name
                    if (!name.startsWith("watchlist_") || value !is String) return@mapNotNull null
                    name.removePrefix("watchlist_") to WatchlistSymbols.decode(value)
                }.toMap()
                WatchlistSymbols.recoverFromKeyedStores(
                    currentUserId = userId,
                    keyedLists = keyed,
                )
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
                    prefs.asMap().keys
                        .filter { it.name.startsWith("watchlist_") }
                        .forEach { prefs.remove(it) }
                } else {
                    prefs[dataStoreKey(key)] = encoded
                    prefs[dataStoreKey(WatchlistSymbols.DEVICE_KEY)] = encoded
                }
            }
        } catch (_: Exception) {
            // Prefs already hold the list; DataStore is a mirror.
        }
    }

    private fun persistOwnedAndDevice(
        prefs: SharedPreferences,
        userKey: String,
        symbols: List<String>,
    ) {
        persistPrefs(prefs, userKey, symbols)
        persistPrefs(prefs, WatchlistSymbols.DEVICE_KEY, symbols)
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

    private fun readAllKeyedLists(prefs: SharedPreferences): Map<String, List<String>> {
        val keyed = linkedMapOf<String, List<String>>()
        for ((storedKey, value) in prefs.all) {
            if (storedKey.startsWith("symbols_") && value is String) {
                keyed[storedKey.removePrefix("symbols_")] = WatchlistSymbols.decode(value)
            }
        }
        return keyed
    }

    private fun clearAllWatchlistPrefs(prefs: SharedPreferences) {
        val editor = prefs.edit()
        editor.remove(LEGACY_SET_KEY)
        for (storedKey in prefs.all.keys) {
            if (storedKey.startsWith("symbols_")) {
                editor.remove(storedKey)
            }
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
