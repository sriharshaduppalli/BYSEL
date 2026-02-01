package com.bysel.trader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.models.*
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TradingViewModel(private val repository: TradingRepository) : ViewModel() {

    private val _quotes = MutableStateFlow<List<Quote>>(emptyList())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()

    private val _holdings = MutableStateFlow<List<Holding>>(emptyList())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val defaultSymbols = listOf("RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN")

    init {
        refreshQuotes()
        refreshHoldings()
        observeAlerts()
    }

    fun refreshQuotes() {
        viewModelScope.launch {
            repository.getQuotes(defaultSymbols).collectLatest { result ->
                when (result) {
                    is Result.Loading -> _isLoading.value = true
                    is Result.Success -> {
                        _quotes.value = result.data
                        _isLoading.value = false
                    }
                    is Result.Error -> {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun refreshHoldings() {
        viewModelScope.launch {
            repository.getHoldings().collectLatest { result ->
                when (result) {
                    is Result.Loading -> _isLoading.value = true
                    is Result.Success -> {
                        _holdings.value = result.data
                        _isLoading.value = false
                    }
                    is Result.Error -> {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            repository.getActiveAlerts().collectLatest {
                _alerts.value = it
            }
        }
    }

    fun placeOrder(symbol: String, quantity: Int, side: String) {
        viewModelScope.launch {
            val order = Order(symbol = symbol, qty = quantity, side = side)
            val result = repository.placeOrder(order)
            when (result) {
                is Result.Success -> {
                    _error.value = null
                    refreshHoldings()
                }
                is Result.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun createAlert(symbol: String, thresholdPrice: Double, alertType: String) {
        viewModelScope.launch {
            val alert = Alert(
                symbol = symbol,
                thresholdPrice = thresholdPrice,
                alertType = alertType
            )
            val result = repository.createAlert(alert)
            when (result) {
                is Result.Success -> _error.value = null
                is Result.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun deleteAlert(alertId: Int) {
        viewModelScope.launch {
            val result = repository.deleteAlert(alertId)
            when (result) {
                is Result.Success -> _error.value = null
                is Result.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class TradingViewModelFactory(private val repository: TradingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
