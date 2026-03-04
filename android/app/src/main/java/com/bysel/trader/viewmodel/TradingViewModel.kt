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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import com.bysel.trader.utils.PromptBuilder
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.bysel.trader.alerts.AlertsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

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

    // Watchlist stored in SharedPreferences for quick cold-start access
    private val watchlistPrefs = getApplication<Application>()
        .getSharedPreferences("bysel_watchlist", Context.MODE_PRIVATE)
    private val _watchlist = MutableStateFlow<List<String>>(emptyList())
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()

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

    // Fast-refresh controls and safety settings
    private val _fastRefreshPlaying = MutableStateFlow(true)
    val fastRefreshPlaying: StateFlow<Boolean> = _fastRefreshPlaying.asStateFlow()

    // safety toggles: require device charging and require unmetered network
    private val settingsPrefs = getApplication<Application>().getSharedPreferences("bysel_settings", Context.MODE_PRIVATE)
    private val _requireCharging = MutableStateFlow(settingsPrefs.getBoolean("fast_refresh_require_charging", false))
    private val _requireUnmetered = MutableStateFlow(settingsPrefs.getBoolean("fast_refresh_require_unmetered", false))
    val requireCharging: StateFlow<Boolean> = _requireCharging.asStateFlow()
    val requireUnmetered: StateFlow<Boolean> = _requireUnmetered.asStateFlow()

    // Alerts manager instance (initialized directly to avoid lateinit)
    private val alertsManager: AlertsManager = AlertsManager(getApplication())
    // initialize fast refresh enabled from settings
    private val _fastRefreshEnabled = MutableStateFlow(settingsPrefs.getBoolean("fast_refresh_enabled", false))
    val fastRefreshEnabled: StateFlow<Boolean> = _fastRefreshEnabled.asStateFlow()

    // Single-quote detail
    private val _selectedQuote = MutableStateFlow<Quote?>(null)
    val selectedQuote: StateFlow<Quote?> = _selectedQuote.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    // Historical OHLCV for selected symbol (used for charting)
    private val _quoteHistory = MutableStateFlow<List<HistoryCandle>>(emptyList())
    val quoteHistory: StateFlow<List<HistoryCandle>> = _quoteHistory.asStateFlow()

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

    // Phase 1 products
    private val _mutualFunds = MutableStateFlow<List<MutualFund>>(emptyList())
    val mutualFunds: StateFlow<List<MutualFund>> = _mutualFunds.asStateFlow()

    private val _ipoListings = MutableStateFlow<List<IPOListing>>(emptyList())
    val ipoListings: StateFlow<List<IPOListing>> = _ipoListings.asStateFlow()

    private val _myIpoApplications = MutableStateFlow<List<IPOApplication>>(emptyList())
    val myIpoApplications: StateFlow<List<IPOApplication>> = _myIpoApplications.asStateFlow()

    private val _etfInstruments = MutableStateFlow<List<ETFInstrument>>(emptyList())
    val etfInstruments: StateFlow<List<ETFInstrument>> = _etfInstruments.asStateFlow()

    private val _sipPlans = MutableStateFlow<List<SipPlan>>(emptyList())
    val sipPlans: StateFlow<List<SipPlan>> = _sipPlans.asStateFlow()

    private val _productsLoading = MutableStateFlow(false)
    val productsLoading: StateFlow<Boolean> = _productsLoading.asStateFlow()

    private val _productActionMessage = MutableStateFlow<String?>(null)
    val productActionMessage: StateFlow<String?> = _productActionMessage.asStateFlow()

    private var autoRefreshJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 15_000L
    private val FAST_REFRESH_INTERVAL = 1_000L
    private val defaultSymbols = listOf("RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN")
    // Paging state for quotes list
    private val _pagedQuotes = MutableStateFlow<List<Quote>>(emptyList())
    val pagedQuotes: StateFlow<List<Quote>> = _pagedQuotes.asStateFlow()
    private var currentPage = 0
    private val pageSize = 50
    // Thread-safe pagination loading flag
    private val _loadingPage = MutableStateFlow(false)

    init {
        loadAchievements()
        // observe active alerts from DB
        viewModelScope.launch {
            repository.getActiveAlerts()
                .catch { e ->
                    android.util.Log.e("TradingViewModel", "Error collecting alerts", e)
                    emit(emptyList()) // Emit empty list on error to prevent crash
                }
                .collectLatest { list -> _alerts.value = list }
        }
        // conservative initial refreshes (non-blocking)
        // Load cached quotes immediately to improve cold-start UX
        viewModelScope.launch {
            try {
                // prefer watchlist symbols if available
                val wl = watchlistPrefs.getStringSet("symbols", emptySet())?.toList() ?: emptyList()
                val symbolsToLoad = if (wl.isNotEmpty()) wl else defaultSymbols
                repository.getCachedQuotes(symbolsToLoad).collectLatest { cached ->
                    if (cached.isNotEmpty()) _quotes.value = cached
                }
            } catch (_: Exception) { }
        }

        // restore watchlist into state
        _watchlist.value = watchlistPrefs.getStringSet("symbols", emptySet())?.toList() ?: emptyList()
        refreshWallet()
        refreshMarketStatus()
        refreshHoldings()
        refreshQuotes()
    }

    

    fun addToWatchlist(symbol: String) {
        val normalized = symbol.trim().uppercase()
        val current = watchlistPrefs.getStringSet("symbols", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (current.add(normalized)) {
            watchlistPrefs.edit().putStringSet("symbols", current).apply()
            _watchlist.value = current.toList()
        }
    }

    fun removeFromWatchlist(symbol: String) {
        val normalized = symbol.trim().uppercase()
        val current = watchlistPrefs.getStringSet("symbols", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(normalized)) {
            watchlistPrefs.edit().putStringSet("symbols", current).apply()
            _watchlist.value = current.toList()
        }
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
        val unlocked = achievementPrefs.getStringSet("unlocked", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (unlocked.add(id)) {
            achievementPrefs.edit().putStringSet("unlocked", unlocked).apply()
            loadAchievements()
        }
    }

    // --- Quotes / holdings / wallet ---
    fun refreshQuotes() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getQuotes(defaultSymbols).collect { result ->
                when (result) {
                    is Result.Loading -> _isLoading.value = true
                    is Result.Success -> {
                        _quotes.value = result.data
                        _isLoading.value = false
                        // Reset paging after success
                        currentPage = 0
                        loadNextQuotesPage()
                    }
                    is Result.Error -> {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun loadNextQuotesPage() {
        // Thread-safe check-and-set using StateFlow
        if (_loadingPage.value) return
        _loadingPage.value = true
        viewModelScope.launch {
            try {
                repository.getQuotesPage(currentPage, pageSize).collect { page ->
                    if (page.isNotEmpty()) {
                        val current = _pagedQuotes.value.toMutableList()
                        // append only new symbols to avoid duplicates caused by overlapping
                        // or re-emitted pages from the DB/repository
                        val toAdd = page.filter { p -> current.none { it.symbol == p.symbol } }
                        if (toAdd.isNotEmpty()) {
                            current.addAll(toAdd)
                            _pagedQuotes.value = current
                            currentPage += 1
                        }
                    }
                }
            } finally {
                _loadingPage.value = false
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
    private var searchJob: kotlinx.coroutines.Job? = null
    // LRU cache with max size to prevent unbounded growth
    private val searchCache = object : LinkedHashMap<String, List<StockSearchResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<StockSearchResult>>): Boolean {
            return size > 50 // Keep max 50 cached searches
        }
    }
    fun searchStocks(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // debounce
            _isSearching.value = true
            try {
                val cached = searchCache[query]
                if (cached != null) {
                    _searchResults.value = cached
                } else {
                    when (val r = repository.searchStocks(query)) {
                        is Result.Success -> {
                            _searchResults.value = r.data
                            searchCache[query] = r.data
                        }
                        is Result.Error -> _error.value = r.message
                        else -> {}
                    }
                }
            } catch (e: Exception) { _error.value = e.message }
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
            // fetch recent history for charting
            fetchQuoteHistory(symbol)
        }
    }

    fun fetchQuoteHistory(symbol: String, period: String = "1mo", interval: String = "1d") {
        viewModelScope.launch {
            // Emit cached history first (if any), then refresh from API and persist
            try {
                repository.getCachedHistory(symbol).collectLatest { cached ->
                    if (cached.isNotEmpty()) _quoteHistory.value = cached
                }
            } catch (_: Exception) {
                // ignore cache read errors
            }

            when (val r = repository.getQuoteHistory(symbol, period, interval)) {
                is Result.Success -> _quoteHistory.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
        }
    }

    /**
     * Start a fast refresh loop that fetches quotes every [intervalMs] milliseconds.
     * This is intended for live-updating UI when the user is actively viewing
     * dashboard or detail screens. It will only perform network refreshes while
     * the market appears open (`marketStatus.isOpen == true`).
     */
    fun startFastRefresh(intervalMs: Long = FAST_REFRESH_INTERVAL, symbols: List<String> = defaultSymbols) {
        // avoid starting multiple jobs
        if (autoRefreshJob?.isActive == true) return
        // respect global enabled flag
        if (!_fastRefreshEnabled.value) return
        autoRefreshJob = viewModelScope.launch {
            try {
                repository.streamLiveQuotes(symbols).collectLatest { result ->
                    if (!_fastRefreshPlaying.value) return@collectLatest
                    if (_requireCharging.value && !isDeviceCharging()) return@collectLatest
                    if (_requireUnmetered.value && !isOnUnmeteredNetwork()) return@collectLatest
                    val isMarketOpen = _marketStatus.value?.isOpen ?: true
                    if (!isMarketOpen) return@collectLatest

                    when (result) {
                        is Result.Success -> {
                            _quotes.value = result.data
                            evaluateAlerts(result.data)
                        }
                        is Result.Error -> _error.value = result.message
                        else -> {}
                    }
                }
            } catch (_: Exception) {
                // ignore transient stream interruptions
            }
        }
    }

    /** Stop the fast-refresh loop. Call when the view is hidden. */
    fun stopFastRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun setFastRefreshEnabled(enabled: Boolean) {
        _fastRefreshEnabled.value = enabled
        settingsPrefs.edit().putBoolean("fast_refresh_enabled", enabled).apply()
        if (!enabled) stopFastRefresh()
    }

    fun setFastRefreshPlaying(play: Boolean) {
        _fastRefreshPlaying.value = play
    }

    fun setRequireCharging(require: Boolean) {
        _requireCharging.value = require
        settingsPrefs.edit().putBoolean("fast_refresh_require_charging", require).apply()
    }

    fun setRequireUnmetered(require: Boolean) {
        _requireUnmetered.value = require
        settingsPrefs.edit().putBoolean("fast_refresh_require_unmetered", require).apply()
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = getApplication<Application>().registerReceiver(null, filter)
            if (batteryStatus == null) {
                android.util.Log.w("TradingViewModel", "Could not read battery status")
                return true // Default to true to avoid blocking refresh
            }
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || 
            status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            android.util.Log.e("TradingViewModel", "Error checking charging status", e)
            true // Default to true on error to avoid blocking
        }
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        return try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        } catch (e: Exception) { false }
    }

    private fun evaluateAlerts(quotesNow: List<Quote>) {
        val activeAlerts = _alerts.value.filter { it.isActive }
        if (activeAlerts.isEmpty()) return
        val map = quotesNow.associateBy { it.symbol }
        for (a in activeAlerts) {
            val q = map[a.symbol] ?: continue
            val price = q.last
            var alertTriggered = false
            when (a.alertType.uppercase()) {
                "ABOVE" -> if (price >= a.thresholdPrice) {
                    alertsManager.sendPriceAlert(a, price)
                    alertTriggered = true
                }
                "BELOW" -> if (price <= a.thresholdPrice) {
                    alertsManager.sendPriceAlert(a, price)
                    alertTriggered = true
                }
            }
            // Deactivate alert after triggering to prevent spam
            if (alertTriggered) {
                viewModelScope.launch {
                    try {
                        repository.deactivateAlert(a.id)
                    } catch (e: Exception) {
                        // Log error but don't fail the whole evaluation
                        android.util.Log.e("TradingViewModel", "Error deactivating alert ${a.id}", e)
                    }
                }
            }
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
            // Gather latest quote data to give the AI more context
            val quoteResult = repository.getQuote(symbol)
            // build prompt using PromptBuilder including recent history if available
            val holdingsSummary = _holdings.value.joinToString(separator = ";") { h -> "${h.symbol}:${h.qty}@${h.last}" }
            val wallet = _walletBalance.value
            val portfolioScore = _portfolioHealth.value?.overallScore

            val recentHistory = if (_selectedQuote.value?.symbol == symbol) _quoteHistory.value else emptyList()
            val baseQuery = "trade_coach:symbol=$symbol,qty=$quantity,side=$side"
            val prompt = PromptBuilder.buildPrompt(baseQuery, holdingsSummary, wallet, portfolioScore, quoteResult.let { if (it is Result.Success) it.data else null }, recentHistory)

            when (val r = repository.aiAsk(prompt)) {
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
            // Build context: holdings, wallet balance, portfolio health where available
            val holdingsSummary = _holdings.value.joinToString(separator = ";") { h ->
                "${h.symbol}:${h.qty}@${h.last}"
            }
            val wallet = _walletBalance.value
            val portfolio = _portfolioHealth.value
            val contextParts = mutableListOf<String>()
            if (holdingsSummary.isNotBlank()) contextParts.add("holdings=$holdingsSummary")
            contextParts.add("wallet=$wallet")
            portfolio?.let { contextParts.add("portfolioScore=${it.overallScore}") }

            // Add selected quote and recent history summary to context when available
            val symbol = _selectedQuote.value?.symbol
            symbol?.let { contextParts.add("symbol=$it") }
            _selectedQuote.value?.let { q ->
                contextParts.add("price=${q.last}")
                q.pctChange.let { contextParts.add("pctChange=${it}") }
            }

            // gather recent history (prefer in-memory state, fallback to cached DB)
            val recentHistory = mutableListOf<HistoryCandle>()
            if (_quoteHistory.value.isNotEmpty()) {
                recentHistory.addAll(_quoteHistory.value)
            } else if (symbol != null) {
                try {
                    repository.getCachedHistory(symbol).collectLatest { cached ->
                        if (cached.isNotEmpty()) {
                            recentHistory.clear()
                            recentHistory.addAll(cached)
                        }
                    }
                } catch (_: Exception) { /* ignore */ }
            }

            if (recentHistory.isNotEmpty()) {
                val lastN = recentHistory.takeLast(10)
                val closes = lastN.map { it.close }
                val avgClose = closes.average()
                val variance = closes.map { (it - avgClose) * (it - avgClose) }.average()
                val volatility = kotlin.math.sqrt(variance)
                contextParts.add("history_count=${lastN.size}")
                contextParts.add("history_avg=${String.format("%.2f", avgClose)}")
                contextParts.add("history_vol=${String.format("%.4f", volatility)}")
                // small inline list of recent closes for AI context (comma-separated)
                val closesShort = lastN.joinToString(",") { String.format("%.2f", it.close) }
                contextParts.add("history_closes=[$closesShort]")
            }

            val prompt = PromptBuilder.buildPrompt(query, holdingsSummary, wallet, portfolio?.overallScore, _selectedQuote.value, recentHistory)

            when (val r = repository.aiAsk(prompt)) {
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
            // Build contextual analyze prompt including holdings and wallet
            val holdingsSummary = _holdings.value.joinToString(separator = ";") { h ->
                "${h.symbol}:${h.qty}@${h.last}"
            }
            val wallet = _walletBalance.value
            val portfolio = _portfolioHealth.value

            // include recent history for symbol in analysis
            val recentHistory = if (_selectedQuote.value?.symbol == symbol) _quoteHistory.value else emptyList()
            val prompt = PromptBuilder.buildPrompt("analyze_stock:symbol=$symbol,wallet=$wallet", holdingsSummary, wallet, portfolio?.overallScore, _selectedQuote.value, recentHistory)

            when (val r = repository.aiAsk(prompt)) {
                is Result.Success -> {
                    // Try to map returned data to StockAnalysis if available
                    val resp = r.data
                    val dataMap = resp.data
                    if (dataMap is Map<*, *>) {
                        try {
                            val map = dataMap as Map<*, *>
                            val symbolS = map["symbol"] as? String ?: symbol
                            val nameS = map["name"] as? String ?: ""
                            val currentPrice = (map["currentPrice"] as? Number)?.toDouble() ?: 0.0
                            val sector = map["sector"] as? String ?: ""
                            val industry = map["industry"] as? String ?: ""
                            val score = (map["score"] as? Number)?.toInt() ?: 0
                            val sbAny = map["scoreBreakdown"] as? Map<*, *>
                            val scoreBreakdown = sbAny?.mapNotNull { (k, v) ->
                                if (k is String && v is Number) k to v.toInt() else null
                            }?.toMap() ?: emptyMap()

                            val sa = StockAnalysis(
                                symbol = symbolS,
                                name = nameS,
                                currentPrice = currentPrice,
                                sector = sector,
                                industry = industry,
                                score = score,
                                scoreBreakdown = scoreBreakdown,
                                signal = map["signal"] as? String ?: "",
                                summary = map["summary"] as? String ?: ""
                            )
                            _stockAnalysis.value = sa
                        } catch (e: Exception) {
                            _error.value = "AI response parsing error"
                        }
                    } else {
                        _error.value = "No analysis data returned"
                    }
                }
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

    fun loadMutualFunds(category: String? = null, query: String? = null) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getMutualFunds(category = category, query = query)) {
                is Result.Success -> _mutualFunds.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun loadIpoListings(status: String? = null) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getIpoListings(status = status)) {
                is Result.Success -> _ipoListings.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun loadMyIpoApplications() {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getMyIpoApplications()) {
                is Result.Success -> _myIpoApplications.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun loadEtfs(category: String? = null, query: String? = null) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getEtfInstruments(category = category, query = query)) {
                is Result.Success -> _etfInstruments.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun loadSipPlans() {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getSipPlans()) {
                is Result.Success -> _sipPlans.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun updateSipPlan(sipId: String, amount: Double, frequency: String, dayOfMonth: Int) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.updateSipPlan(
                sipId,
                SipPlanUpdateRequest(
                    amount = amount,
                    frequency = frequency,
                    dayOfMonth = dayOfMonth
                )
            )) {
                is Result.Success -> {
                    _productActionMessage.value = "SIP updated"
                    loadSipPlans()
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun pauseSipPlan(sipId: String) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.pauseSipPlan(sipId)) {
                is Result.Success -> {
                    _productActionMessage.value = "SIP paused"
                    loadSipPlans()
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun resumeSipPlan(sipId: String) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.resumeSipPlan(sipId)) {
                is Result.Success -> {
                    _productActionMessage.value = "SIP resumed"
                    loadSipPlans()
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun createSipForFund(schemeCode: String, amount: Double, frequency: String = "MONTHLY", dayOfMonth: Int = 5) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.createSipPlan(
                SipPlanRequest(
                    schemeCode = schemeCode,
                    amount = amount,
                    frequency = frequency,
                    dayOfMonth = dayOfMonth
                )
            )) {
                is Result.Success -> {
                    _productActionMessage.value = "SIP created successfully"
                    loadSipPlans()
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun applyForIpo(ipo: IPOListing, lots: Int = 1, upiId: String = "demo@upi") {
        viewModelScope.launch {
            _productsLoading.value = true
            val bid = ipo.priceBandMax ?: ipo.priceBandMin ?: 0.0
            when (val r = repository.applyIpo(
                IPOApplicationRequest(
                    ipoId = ipo.ipoId,
                    lots = lots,
                    bidPrice = bid,
                    upiId = upiId
                )
            )) {
                is Result.Success -> _productActionMessage.value = "IPO application submitted"
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun clearProductActionMessage() {
        _productActionMessage.value = null
    }

    // Fix Bug #1: Properly clean up resources to prevent memory leaks
    override fun onCleared() {
        super.onCleared()
        stopFastRefresh()
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
        if (modelClass == TradingViewModel::class.java) {
            @Suppress("UNCHECKED_CAST")
            return TradingViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
