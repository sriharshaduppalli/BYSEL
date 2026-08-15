package com.bysel.trader.data.repository

import android.content.Context
import com.bysel.trader.data.StockNotesStore
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.models.StockNoteRecord
import com.bysel.trader.data.models.StockNoteUpsertRequest
import com.bysel.trader.data.normalizeStockNoteSymbol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class StockNotesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = RetrofitClient.apiService

    fun notesFlow(): Flow<Map<String, String>> =
        StockNotesStore.notesFlow(appContext, currentUserKey()).map { records ->
            records.mapValues { it.value.text }
        }.distinctUntilChanged()

    suspend fun noteText(symbol: String): String {
        val key = normalizeStockNoteSymbol(symbol)
        if (key.isBlank()) return ""
        return StockNotesStore.notesFlow(appContext, currentUserKey()).first()[key]?.text.orEmpty()
    }

    suspend fun save(symbol: String, text: String) {
        val key = normalizeStockNoteSymbol(symbol)
        if (key.isBlank()) return
        val trimmed = text.trim().take(MAX_NOTE_CHARS)
        val record = StockNoteRecord(text = trimmed, updatedAt = System.currentTimeMillis())
        if (trimmed.isBlank()) {
            StockNotesStore.remove(appContext, currentUserKey(), key)
            runCatching { api.deleteStockNote(key) }
            return
        }
        StockNotesStore.upsert(appContext, currentUserKey(), key, record)
        runCatching { api.upsertStockNote(StockNoteUpsertRequest(symbol = key, text = trimmed)) }
    }

    suspend fun clear(symbol: String) = save(symbol, "")

    suspend fun syncFromBackend() {
        if (!AuthSessionManager.hasSession()) return
        val remote = runCatching { api.getStockNotes() }.getOrNull()?.notes ?: return
        val userKey = currentUserKey()
        val local = StockNotesStore.notesFlow(appContext, userKey).first().toMutableMap()
        remote.forEach { dto ->
            val symbol = normalizeStockNoteSymbol(dto.symbol)
            if (symbol.isBlank()) return@forEach
            val remoteText = dto.text.trim()
            val existing = local[symbol]
            val remoteNewer = existing == null || dto.updatedAt >= existing.updatedAt
            if (remoteText.isBlank()) {
                if (remoteNewer) local.remove(symbol)
            } else if (remoteNewer) {
                local[symbol] = StockNoteRecord(text = remoteText, updatedAt = dto.updatedAt)
            }
        }
        StockNotesStore.replaceAll(appContext, userKey, local)
        local.forEach { (symbol, record) ->
            val match = remote.firstOrNull { normalizeStockNoteSymbol(it.symbol) == symbol }
            val shouldPush = match == null || record.updatedAt > match.updatedAt
            if (shouldPush && record.text.isNotBlank()) {
                runCatching {
                    api.upsertStockNote(StockNoteUpsertRequest(symbol = symbol, text = record.text))
                }
            }
        }
    }

    private fun currentUserKey(): String {
        val userId = AuthSessionManager.getUserId()
        return if (userId != null && userId > 0) "u_$userId" else "anon"
    }

    companion object {
        const val MAX_NOTE_CHARS = 4000
    }
}
