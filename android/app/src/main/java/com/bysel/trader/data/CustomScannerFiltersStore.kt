package com.bysel.trader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class CustomScannerFilters(
    val minScore: Int? = null,
    val rsi: String? = null,
    val dma: String? = null,
    val minVolume: Double? = null,
    val maxPe: Double? = null,
    val minChange: Double? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(minScore, rsi, dma, minVolume, maxPe, minChange).size
}

object CustomScannerFiltersStore {
    private const val DATASTORE_NAME = "custom_scanner_filters"
    private val Context.customScannerDataStore by preferencesDataStore(DATASTORE_NAME)
    private val KEY = stringPreferencesKey("filters_v1")

    suspend fun read(context: Context): CustomScannerFilters {
        return try {
            context.applicationContext.customScannerDataStore.data.map { prefs ->
                decode(prefs[KEY])
            }.firstOrNull() ?: CustomScannerFilters()
        } catch (_: Exception) {
            CustomScannerFilters()
        }
    }

    suspend fun write(context: Context, filters: CustomScannerFilters) {
        try {
            context.applicationContext.customScannerDataStore.edit { prefs ->
                prefs[KEY] = encode(filters)
            }
        } catch (_: Exception) {
            // Local chips are best-effort; in-memory state still applies.
        }
    }

    private fun encode(filters: CustomScannerFilters): String {
        val root = JSONObject()
        filters.minScore?.let { root.put("minScore", it) }
        filters.rsi?.let { root.put("rsi", it) }
        filters.dma?.let { root.put("dma", it) }
        filters.minVolume?.let { root.put("minVolume", it) }
        filters.maxPe?.let { root.put("maxPe", it) }
        filters.minChange?.let { root.put("minChange", it) }
        return root.toString()
    }

    private fun decode(raw: String?): CustomScannerFilters {
        if (raw.isNullOrBlank()) return CustomScannerFilters()
        return runCatching {
            val root = JSONObject(raw)
            CustomScannerFilters(
                minScore = root.optIntOrNull("minScore"),
                rsi = root.optStringOrNull("rsi"),
                dma = root.optStringOrNull("dma"),
                minVolume = root.optDoubleOrNull("minVolume"),
                maxPe = root.optDoubleOrNull("maxPe"),
                minChange = root.optDoubleOrNull("minChange"),
            )
        }.getOrDefault(CustomScannerFilters())
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
