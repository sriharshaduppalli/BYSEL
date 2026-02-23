package com.bysel.trader.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Clean, minimal TradingViewModel that exposes the state and actions used
 * across the app. This intentionally keeps implementations simple and
 * defensive so the app can build while backend behaviour is handled by
 * the repository.
 */
class TradingViewModel(
    application: Application,
    private val repository: TradingRepository
) : AndroidViewModel(application) {

    // --- AI Trade Coach State ---
    private val _tradeCoachTip = MutableStateFlow<String?>(null)
    val tradeCoachTip: StateFlow<String?> = _tradeCoachTip.asStateFlow()
    fun clearTradeCoachTip() { _tradeCoachTip.value = null }

    // --- Achievements ---
    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()
    private val achievementPrefs = getApplication<Application>()
        .getSharedPreferences("bysel_achievements", Context.MODE_PRIVATE)

    // --- Core state flows ---
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

    private val _walletBalance = MutableStateFlow(0.0)
    val walletBalance: StateFlow<Double> = _walletBalance.asStateFlow()

    private val _marketStatus = MutableStateFlow<MarketStatus?>(null)
    val marketStatus: StateFlow<MarketStatus?> = _marketStatus.asStateFlow()

    // Single-quote detail
    private val _selectedQuote = MutableStateFlow<Quote?>(null)
    val selectedQuote: StateFlow<Quote?> = _selectedQuote.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    // AI assistant
    private val _aiResponse = MutableStateFlow<AiAssistantResponse?>(null)
    val aiResponse: StateFlow<AiAssistantResponse?> = _aiResponse.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // portfolio/health/heatmap
    private val _portfolioHealth = MutableStateFlow<PortfolioHealthScore?>(null)
    val portfolioHealth: StateFlow<PortfolioHealthScore?> = _portfolioHealth.asStateFlow()
    private val _healthLoading = MutableStateFlow(false)
    val healthLoading: StateFlow<Boolean> = _healthLoading.asStateFlow()

    private val _marketHeatmap = MutableStateFlow<MarketHeatmap?>(null)
    val marketHeatmap: StateFlow<MarketHeatmap?> = _marketHeatmap.asStateFlow()
    private val _heatmapLoading = MutableStateFlow(false)
    val heatmapLoading: StateFlow<Boolean> = _heatmapLoading.asStateFlow()

    private var autoRefreshJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 15_000L
    private val defaultSymbols = listOf("RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN")

    init {
        loadAchievements()
        // conservative initial refreshes (non-blocking)
        refreshWallet()
        refreshMarketStatus()
        refreshHoldings()
        refreshQuotes()
    }

    private fun loadAchievements() {
        val unlocked = achievementPrefs.getStringSet("unlocked", emptySet()) ?: emptySet()
        _achievements.value = defaultAchievementsFromCode().map {
            if (unlocked.contains(it.id)) it.copy(unlocked = true) else it
        }
    }

    private fun defaultAchievementsFromCode() = listOf(
        Achievement("first_trade", "First Trade!", "Complete your first trade."),
        Achievement("portfolio_10k", "Portfolio 10K", "Reach ₹10,000 portfolio value."),
        Achievement("profit_1k", "Profit Maker", "Earn ₹1,000 in profit."),
        Achievement("streak_5", "5-Day Streak", "Trade 5 days in a row.")
    )

    // --- Demo account helper used by MainActivity ---
    fun initDemoAccount() {
        viewModelScope.launch {
            // If a real wallet already exists (backend/persisted), do not overwrite it with demo funds
            when (val r = repository.getWallet()) {
                is Result.Success -> {
                    // If the user already has a balance, skip demo initialization
                    if (r.data.balance > 0.0) return@launch
                }
                is Result.Error -> {
                    // If error fetching wallet, we proceed to demo initialization as a fallback
                }
                else -> { /* proceed to demo */ }
            }

            _walletBalance.value = 100000.0
            val demoHoldings = listOf(
                Holding(symbol = "RELIANCE", qty = 10, avgPrice = 2500.0, last = 2550.0, pnl = 500.0),
                Holding(symbol = "TCS", qty = 5, avgPrice = 3500.0, last = 3550.0, pnl = 250.0),
                Holding(symbol = "SBIN", qty = 20, avgPrice = 600.0, last = 610.0, pnl = 200.0)
            )
            repository.setDemoHoldings(demoHoldings)
            refreshHoldings()
        }
    }

    private fun unlockAchievement(id: String) {
        val now = System.currentTimeMillis()
        val unlocked = achievementPrefs.getStringSet("unlocked", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (unlocked.add(id)) {
            achievementPrefs.edit().putStringSet("unlocked", unlocked).apply()
            loadAchievements()
        }
    }

    // --- Quotes / holdings / wallet ---
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
                if (result is Result.Success) _quotes.value = result.data
            }
        }
    }

    fun refreshHoldings() {
        viewModelScope.launch {
            repository.getHoldings().collectLatest { result ->
                if (result is Result.Success) _holdings.value = result.data
            }
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            when (val r = repository.getWallet()) {
                is Result.Success -> _walletBalance.value = r.data.balance
                is Result.Error -> { /* ignore */ }
                else -> { }
            }
        }
    }

    fun refreshMarketStatus() {
        viewModelScope.launch {
            when (val r = repository.getMarketStatus()) {
                is Result.Success -> _marketStatus.value = r.data
                is Result.Error -> { /* ignore */ }
                else -> { }
            }
        }
    }

    // --- Search ---
    fun searchStocks(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _isSearching.value = true
            when (val r = repository.searchStocks(query)) {
                is Result.Success -> _searchResults.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _isSearching.value = false
        }
    }

    fun clearSearchResults() { _searchResults.value = emptyList() }

    // --- Single quote ---
    fun setSelectedQuote(quote: Quote) { _selectedQuote.value = quote }

    fun fetchAndSelectQuote(symbol: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            when (val r = repository.getQuote(symbol)) {
                is Result.Success -> _selectedQuote.value = r.data
                is Result.Error -> _error.value = r.message
                else -> { }
            }
            _detailLoading.value = false
        }
    }

    // --- Orders / alerts / funds ---
    fun placeOrder(symbol: String, quantity: Int, side: String) {
        viewModelScope.launch {
            when (val r = repository.placeOrder(Order(symbol = symbol, qty = quantity, side = side))) {
                is Result.Success -> {
                    if (r.data.status == "error") _error.value = r.data.message else {
                        _error.value = null
                        refreshHoldings(); refreshWallet(); unlockAchievement("first_trade")
                        fetchTradeCoachTip(symbol, quantity, side)
                    }
                }
                is Result.Error -> _error.value = r.message
                else -> { }
            }
        }
    }

    private fun fetchTradeCoachTip(symbol: String, quantity: Int, side: String) {
        viewModelScope.launch {
            when (val r = repository.aiAsk("trade_coach:symbol=$symbol,qty=$quantity,side=$side")) {
                is Result.Success -> _tradeCoachTip.value = r.data.answer
                else -> _tradeCoachTip.value = "Tip: Review your trade strategy."
            }
        }
    }

    fun addFunds(amount: Double) {
        viewModelScope.launch {
            when (val r = repository.addFunds(amount)) {
                is Result.Success -> if (r.data.status == "ok") _walletBalance.value = r.data.balance else _error.value = r.data.message
                is Result.Error -> _error.value = r.message
                else -> { }
            }
        }
    }

    fun createAlert(symbol: String, thresholdPrice: Double, alertType: String) {
        viewModelScope.launch {
            val a = Alert(symbol = symbol, thresholdPrice = thresholdPrice, alertType = alertType)
            when (val r = repository.createAlert(a)) { is Result.Error -> _error.value = r.message; else -> {} }
        }
    }

    fun deleteAlert(alertId: Int) {
        viewModelScope.launch {
            when (val r = repository.deleteAlert(alertId)) { is Result.Error -> _error.value = r.message; else -> {} }
        }
    }

    fun clearError() { _error.value = null }

    // --- AI assistant ---
    fun askAi(query: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _chatHistory.value = _chatHistory.value + ChatMessage(query, isUser = true)
            when (val r = repository.aiAsk(query)) {
                is Result.Success -> {
                    _aiResponse.value = r.data
                    _chatHistory.value = _chatHistory.value + ChatMessage(r.data.answer, isUser = false, suggestions = r.data.suggestions)
                }
                is Result.Error -> _chatHistory.value = _chatHistory.value + ChatMessage("Sorry, I couldn't process that.", isUser = false)
                else -> {}
            }
            _aiLoading.value = false
        }
    }

    fun clearChatHistory() { _chatHistory.value = emptyList(); _aiResponse.value = null }

    // --- Analysis / predictions ---
    fun analyzeStock(symbol: String) {
        viewModelScope.launch {
            _healthLoading.value = true
            when (val r = repository.aiAnalyze(symbol)) {
                is Result.Success -> _stockAnalysis.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _healthLoading.value = false
        }
    }

    // lightweight placeholders for types referenced earlier
    private val _stockAnalysis = MutableStateFlow<StockAnalysis?>(null)
    val stockAnalysis: StateFlow<StockAnalysis?> = _stockAnalysis.asStateFlow()

    private val _stockPrediction = MutableStateFlow<StockPredictionResponse?>(null)
    val stockPrediction: StateFlow<StockPredictionResponse?> = _stockPrediction.asStateFlow()

    fun predictStock(symbol: String) {
        viewModelScope.launch {
            _healthLoading.value = true
            when (val r = repository.aiPredict(symbol)) {
                is Result.Success -> _stockPrediction.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _healthLoading.value = false
        }
    }

    fun loadPortfolioHealth() {
        viewModelScope.launch {
            _healthLoading.value = true
            when (val r = repository.getPortfolioHealth()) {
                is Result.Success -> _portfolioHealth.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _healthLoading.value = false
        }
    }

    fun loadMarketHeatmap() {
        viewModelScope.launch {
            _heatmapLoading.value = true
            when (val r = repository.getMarketHeatmap()) {
                is Result.Success -> _marketHeatmap.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _heatmapLoading.value = false
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

// Factory for TradingViewModel
class TradingViewModelFactory(private val repository: TradingRepository) : ViewModelProvider.Factory {
    lateinit var application: Application
    fun initApplication(app: Application) { application = app }
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
