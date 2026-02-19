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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TradingViewModel(private val repository: TradingRepository) : ViewModel() {

    private val _quotes = MutableStateFlow<List<Quote>>(emptyList())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()

    private val _holdings = MutableStateFlow<List<Holding>>(emptyList())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Wallet
    private val _walletBalance = MutableStateFlow(0.0)
    val walletBalance: StateFlow<Double> = _walletBalance.asStateFlow()

    // Market status
    private val _marketStatus = MutableStateFlow<MarketStatus?>(null)
    val marketStatus: StateFlow<MarketStatus?> = _marketStatus.asStateFlow()

    // Auto-refresh polling
    private var autoRefreshJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 15_000L // 15 seconds

    private val defaultSymbols = listOf(
        "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN",
        "WIPRO", "ICICIBANK", "KOTAKBANK", "HINDUNILVR", "ITC",
        "BHARTIARTL", "LT", "AXISBANK", "BAJFINANCE", "TATAMOTORS",
        "SUNPHARMA", "TITAN", "MARUTI", "HCLTECH", "TATASTEEL"
    )

    init {
        refreshQuotes()
        refreshHoldings()
        observeAlerts()
        refreshWallet()
        refreshMarketStatus()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL)
                // Silently refresh quotes, holdings, wallet, and market status
                refreshQuotesSilent()
                refreshHoldingsSilent()
                refreshWallet()
                refreshMarketStatus()
            }
        }
    }

    /** Refresh quotes without showing loading spinner */
    private fun refreshQuotesSilent() {
        viewModelScope.launch {
            repository.getQuotes(defaultSymbols).collectLatest { result ->
                when (result) {
                    is Result.Success -> _quotes.value = result.data
                    else -> {} // Silently ignore errors during auto-refresh
                }
            }
        }
    }

    /** Refresh holdings without showing loading spinner */
    private fun refreshHoldingsSilent() {
        viewModelScope.launch {
            repository.getHoldings().collectLatest { result ->
                when (result) {
                    is Result.Success -> _holdings.value = result.data
                    else -> {}
                }
            }
        }
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

    fun loadAllQuotes() {
        viewModelScope.launch {
            repository.getAllQuotesFromApi().collectLatest { result ->
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

    fun searchStocks(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val result = repository.searchStocks(query)
            when (result) {
                is Result.Success -> {
                    _searchResults.value = result.data
                    _isSearching.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _isSearching.value = false
                }
                else -> {}
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // --- Single stock detail ---
    private val _selectedQuote = MutableStateFlow<Quote?>(null)
    val selectedQuote: StateFlow<Quote?> = _selectedQuote.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    fun setSelectedQuote(quote: Quote) {
        _selectedQuote.value = quote
    }

    fun fetchAndSelectQuote(symbol: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            val result = repository.getQuote(symbol)
            when (result) {
                is Result.Success -> {
                    _selectedQuote.value = result.data
                    _detailLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _detailLoading.value = false
                }
                else -> {}
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
                    // Check if backend returned an error status (market closed, insufficient funds)
                    if (result.data.status == "error") {
                        _error.value = result.data.message
                    } else {
                        _error.value = null
                        refreshHoldings()
                        refreshWallet()
                    }
                }
                is Result.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            val result = repository.getWallet()
            when (result) {
                is Result.Success -> _walletBalance.value = result.data.balance
                is Result.Error -> { /* silently ignore wallet errors */ }
                else -> {}
            }
        }
    }

    fun refreshMarketStatus() {
        viewModelScope.launch {
            val result = repository.getMarketStatus()
            when (result) {
                is Result.Success -> _marketStatus.value = result.data
                is Result.Error -> { /* silently ignore */ }
                else -> {}
            }
        }
    }

    fun addFunds(amount: Double) {
        viewModelScope.launch {
            val result = repository.addFunds(amount)
            when (result) {
                is Result.Success -> {
                    if (result.data.status == "ok") {
                        _walletBalance.value = result.data.balance
                        _error.value = null
                    } else {
                        _error.value = result.data.message
                    }
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

    // ==================== AI ASSISTANT ====================
    private val _aiResponse = MutableStateFlow<AiAssistantResponse?>(null)
    val aiResponse: StateFlow<AiAssistantResponse?> = _aiResponse.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    fun askAi(query: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            // Add user message
            _chatHistory.value = _chatHistory.value + ChatMessage(query, isUser = true)
            val result = repository.aiAsk(query)
            when (result) {
                is Result.Success -> {
                    _aiResponse.value = result.data
                    _chatHistory.value = _chatHistory.value + ChatMessage(
                        result.data.answer,
                        isUser = false,
                        suggestions = result.data.suggestions
                    )
                    _aiLoading.value = false
                }
                is Result.Error -> {
                    _chatHistory.value = _chatHistory.value + ChatMessage(
                        "Sorry, I couldn't process that. Please try again.",
                        isUser = false
                    )
                    _aiLoading.value = false
                }
                else -> {}
            }
        }
    }

    fun clearChatHistory() {
        _chatHistory.value = emptyList()
        _aiResponse.value = null
    }

    // ==================== STOCK ANALYSIS & PREDICTION ====================
    private val _stockAnalysis = MutableStateFlow<StockAnalysis?>(null)
    val stockAnalysis: StateFlow<StockAnalysis?> = _stockAnalysis.asStateFlow()

    private val _stockPrediction = MutableStateFlow<StockPredictionResponse?>(null)
    val stockPrediction: StateFlow<StockPredictionResponse?> = _stockPrediction.asStateFlow()

    private val _analysisLoading = MutableStateFlow(false)
    val analysisLoading: StateFlow<Boolean> = _analysisLoading.asStateFlow()

    fun analyzeStock(symbol: String) {
        viewModelScope.launch {
            _analysisLoading.value = true
            val result = repository.aiAnalyze(symbol)
            when (result) {
                is Result.Success -> {
                    _stockAnalysis.value = result.data
                    _analysisLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _analysisLoading.value = false
                }
                else -> {}
            }
        }
    }

    fun predictStock(symbol: String) {
        viewModelScope.launch {
            _analysisLoading.value = true
            val result = repository.aiPredict(symbol)
            when (result) {
                is Result.Success -> {
                    _stockPrediction.value = result.data
                    _analysisLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _analysisLoading.value = false
                }
                else -> {}
            }
        }
    }

    // ==================== PORTFOLIO HEALTH ====================
    private val _portfolioHealth = MutableStateFlow<PortfolioHealthScore?>(null)
    val portfolioHealth: StateFlow<PortfolioHealthScore?> = _portfolioHealth.asStateFlow()

    private val _healthLoading = MutableStateFlow(false)
    val healthLoading: StateFlow<Boolean> = _healthLoading.asStateFlow()

    fun loadPortfolioHealth() {
        viewModelScope.launch {
            _healthLoading.value = true
            val result = repository.getPortfolioHealth()
            when (result) {
                is Result.Success -> {
                    _portfolioHealth.value = result.data
                    _healthLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _healthLoading.value = false
                }
                else -> {}
            }
        }
    }

    // ==================== MARKET HEATMAP ====================
    private val _marketHeatmap = MutableStateFlow<MarketHeatmap?>(null)
    val marketHeatmap: StateFlow<MarketHeatmap?> = _marketHeatmap.asStateFlow()

    private val _heatmapLoading = MutableStateFlow(false)
    val heatmapLoading: StateFlow<Boolean> = _heatmapLoading.asStateFlow()

    fun loadMarketHeatmap() {
        viewModelScope.launch {
            _heatmapLoading.value = true
            val result = repository.getMarketHeatmap()
            when (result) {
                is Result.Success -> {
                    _marketHeatmap.value = result.data
                    _heatmapLoading.value = false
                }
                is Result.Error -> {
                    _error.value = result.message
                    _heatmapLoading.value = false
                }
                else -> {}
            }
        }
    }
}

// Chat message for AI Assistant
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class TradingViewModelFactory(private val repository: TradingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
