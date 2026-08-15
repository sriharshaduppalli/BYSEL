package com.bysel.trader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.normalizeStockNoteSymbol
import com.bysel.trader.data.repository.StockNotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StockNotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = StockNotesRepository(app)

    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    init {
        viewModelScope.launch {
            AuthSessionManager.sessionState.collectLatest {
                launch { runCatching { repository.syncFromBackend() } }
                repository.notesFlow().collect { _notes.value = it }
            }
        }
    }

    fun noteText(symbol: String, snapshot: Map<String, String> = _notes.value): String {
        val key = normalizeStockNoteSymbol(symbol)
        return snapshot[key].orEmpty()
    }

    fun hasNote(symbol: String, snapshot: Map<String, String> = _notes.value): Boolean =
        noteText(symbol, snapshot).isNotBlank()

    fun save(symbol: String, text: String) {
        viewModelScope.launch { repository.save(symbol, text) }
    }

    fun clear(symbol: String) {
        viewModelScope.launch { repository.clear(symbol) }
    }
}
