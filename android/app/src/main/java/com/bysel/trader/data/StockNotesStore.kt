package com.bysel.trader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bysel.trader.data.models.StockNoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

object StockNotesStore {
    private const val DATASTORE_NAME = "stock_notes_prefs"
    private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

    fun notesFlow(context: Context, userKey: String): Flow<Map<String, StockNoteRecord>> =
        context.dataStore.data.map { prefs ->
            parseNotes(prefs[keyFor(userKey)].orEmpty())
        }

    suspend fun replaceAll(context: Context, userKey: String, notes: Map<String, StockNoteRecord>) {
        context.dataStore.edit { prefs ->
            prefs[keyFor(userKey)] = serializeNotes(notes)
        }
    }

    suspend fun upsert(context: Context, userKey: String, symbol: String, record: StockNoteRecord) {
        context.dataStore.edit { prefs ->
            val current = parseNotes(prefs[keyFor(userKey)].orEmpty()).toMutableMap()
            if (record.text.isBlank()) {
                current.remove(symbol)
            } else {
                current[symbol] = record
            }
            prefs[keyFor(userKey)] = serializeNotes(current)
        }
    }

    suspend fun remove(context: Context, userKey: String, symbol: String) {
        context.dataStore.edit { prefs ->
            val current = parseNotes(prefs[keyFor(userKey)].orEmpty()).toMutableMap()
            current.remove(symbol)
            prefs[keyFor(userKey)] = serializeNotes(current)
        }
    }

    private fun keyFor(userKey: String) = stringPreferencesKey("notes_$userKey")

    private fun parseNotes(raw: String): Map<String, StockNoteRecord> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val symbol = keys.next()
                    val node = root.optJSONObject(symbol) ?: continue
                    val text = node.optString("text").trim()
                    if (text.isBlank()) continue
                    put(
                        symbol,
                        StockNoteRecord(
                            text = text,
                            updatedAt = node.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun serializeNotes(notes: Map<String, StockNoteRecord>): String {
        val root = JSONObject()
        notes.forEach { (symbol, record) ->
            if (record.text.isBlank()) return@forEach
            root.put(
                symbol,
                JSONObject()
                    .put("text", record.text)
                    .put("updatedAt", record.updatedAt),
            )
        }
        return root.toString()
    }
}
