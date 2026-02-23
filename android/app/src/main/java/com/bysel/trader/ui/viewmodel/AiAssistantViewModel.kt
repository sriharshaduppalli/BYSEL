package com.bysel.trader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.models.AiQuery
import com.bysel.trader.data.models.AiAssistantResponse
import com.bysel.trader.viewmodel.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiAssistantViewModel(private val api: BYSELApiService) : ViewModel() {
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun sendQuery(query: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response: AiAssistantResponse = api.aiAsk(AiQuery(query))
                val newMessage = ChatMessage(
                    text = response.answer,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                val userMessage = ChatMessage(
                    text = query,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                _chatHistory.value = _chatHistory.value + userMessage + newMessage
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Error: ${e.message}",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                _chatHistory.value = _chatHistory.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
    }
}
