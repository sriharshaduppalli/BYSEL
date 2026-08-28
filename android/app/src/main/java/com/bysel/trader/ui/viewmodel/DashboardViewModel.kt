
package com.bysel.trader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.PinnedStocksStore
import com.bysel.trader.data.PinnedWidgetsStore
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.IntradayTipsResponse
import com.bysel.trader.data.models.InvestorTipsResponse
import com.bysel.trader.data.models.MarketMoverQuote
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.ui.components.localInvestorTips
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TradingRepository(BYSELDatabase.getInstance(app))

    private val _widgetOrder = MutableStateFlow<List<String>>(listOf("portfolio", "news", "watchlist"))
    val widgetOrder: StateFlow<List<String>> = _widgetOrder.asStateFlow()

    private val _watchlistPinned = MutableStateFlow(false)
    val watchlistPinned: StateFlow<Boolean> = _watchlistPinned.asStateFlow()

    fun resetDashboardLayout() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            _portfolioPinned.value = false
            _newsPinned.value = true
            _watchlistPinned.value = true
            // Visible Your Space widgets first so ↑↓ is not a no-op against a hidden Portfolio.
            _widgetOrder.value = listOf("news", "watchlist", "portfolio")
            PinnedWidgetsStore.setPortfolioPinned(context, false)
            PinnedWidgetsStore.setNewsPinned(context, true)
            PinnedWidgetsStore.setWatchlistPinned(context, true)
            PinnedWidgetsStore.setWidgetOrder(context, listOf("news", "watchlist", "portfolio"))
        }
    }

    private fun loadWidgetOrder() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val savedOrder = PinnedWidgetsStore.getWidgetOrder(context).first()
            val filtered = savedOrder.filter { it == "portfolio" || it == "news" || it == "watchlist" }
            _widgetOrder.value = if (filtered.isNotEmpty()) filtered else listOf("portfolio", "news", "watchlist")
        }
    }
    private val _pinnedStocks = MutableStateFlow<Set<String>>(emptySet())
    val pinnedStocks: StateFlow<Set<String>> = _pinnedStocks.asStateFlow()

    private val _portfolioPinned = MutableStateFlow(false)
    val portfolioPinned: StateFlow<Boolean> = _portfolioPinned.asStateFlow()

    private val _newsPinned = MutableStateFlow(false)
    val newsPinned: StateFlow<Boolean> = _newsPinned.asStateFlow()

    private val _marketNews = MutableStateFlow<List<MarketNewsHeadline>>(emptyList())
    val marketNews: StateFlow<List<MarketNewsHeadline>> = _marketNews.asStateFlow()

    private val _newsSymbols = MutableStateFlow<List<String>>(emptyList())
    val newsSymbols: StateFlow<List<String>> = _newsSymbols.asStateFlow()

    private val _newsLoading = MutableStateFlow(false)
    val newsLoading: StateFlow<Boolean> = _newsLoading.asStateFlow()

    private val _newsError = MutableStateFlow<String?>(null)
    val newsError: StateFlow<String?> = _newsError.asStateFlow()

    private val _momentumLeaders = MutableStateFlow<List<MarketMoverQuote>>(emptyList())
    val momentumLeaders: StateFlow<List<MarketMoverQuote>> = _momentumLeaders.asStateFlow()

    private val _pressureZone = MutableStateFlow<List<MarketMoverQuote>>(emptyList())
    val pressureZone: StateFlow<List<MarketMoverQuote>> = _pressureZone.asStateFlow()

    private val _moversUniverseSize = MutableStateFlow(0)
    val moversUniverseSize: StateFlow<Int> = _moversUniverseSize.asStateFlow()

    private val _moversLoading = MutableStateFlow(false)
    val moversLoading: StateFlow<Boolean> = _moversLoading.asStateFlow()

    private val _practiceIdeas = MutableStateFlow<List<com.bysel.trader.data.models.PracticeIdea>>(emptyList())
    val practiceIdeas: StateFlow<List<com.bysel.trader.data.models.PracticeIdea>> = _practiceIdeas.asStateFlow()

    private val _practiceIdeasLoading = MutableStateFlow(false)
    val practiceIdeasLoading: StateFlow<Boolean> = _practiceIdeasLoading.asStateFlow()

    private val _practiceIdeasDisclaimer = MutableStateFlow("")
    val practiceIdeasDisclaimer: StateFlow<String> = _practiceIdeasDisclaimer.asStateFlow()

    private val _intradayTips = MutableStateFlow<IntradayTipsResponse?>(null)
    val intradayTips: StateFlow<IntradayTipsResponse?> = _intradayTips.asStateFlow()

    private val _intradayTipsLoading = MutableStateFlow(false)
    val intradayTipsLoading: StateFlow<Boolean> = _intradayTipsLoading.asStateFlow()

    private val _investorTipTopic = MutableStateFlow("long_term")
    val investorTipTopic: StateFlow<String> = _investorTipTopic.asStateFlow()

    private val _investorTips = MutableStateFlow(localInvestorTips("long_term"))
    val investorTips: StateFlow<InvestorTipsResponse> = _investorTips.asStateFlow()

    private val _investorTipsLoading = MutableStateFlow(false)
    val investorTipsLoading: StateFlow<Boolean> = _investorTipsLoading.asStateFlow()

    /** Last symbols used for Home news so pull-to-refresh stays personalized. */
    private var lastNewsSymbols: List<String> = emptyList()
    private var lastAdvanceShare: Double? = null

    init {
        loadPinnedStocks()
        loadPinnedWidgets()
        loadWidgetOrder()
        // Stagger Home secondary fetches so they don't stampede wallet/holdings on cold start.
        viewModelScope.launch {
            delay(1_200) // let first Home frame + priority quotes win
            refreshMarketNews()
            delay(1_200)
            refreshMarketMovers()
            delay(400)
            refreshIntradayTips()
            delay(400)
            refreshInvestorTips()
            delay(800)
            refreshPracticeIdeas()
        }
    }

    /**
     * Refresh Home market news.
     * Prefer the caller's watchlist + holdings (capped server-side at 12);
     * empty list falls back to liquid megacaps on the backend.
     */
    fun refreshMarketNews(symbols: List<String> = lastNewsSymbols) {
        val normalized = symbols
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && !it.startsWith("^") }
            .distinct()
            .take(12)
        lastNewsSymbols = normalized
        viewModelScope.launch {
            val hasHeadlines = _marketNews.value.isNotEmpty()
            if (!hasHeadlines) _newsLoading.value = true
            when (val response = repository.getMarketNews(symbols = normalized, limit = 10)) {
                is Result.Success -> {
                    _marketNews.value = response.data.headlines
                    _newsSymbols.value = response.data.symbolsConsidered
                    _newsError.value = null
                }

                is Result.Error -> {
                    // Keep prior headlines on timeout; only show error if the feed is empty.
                    if (_marketNews.value.isEmpty()) {
                        _newsError.value = response.message
                        _newsSymbols.value = emptyList()
                    } else {
                        _newsError.value = null
                    }
                }

                Result.Loading -> Unit
            }
            _newsLoading.value = false
        }
    }

    fun refreshMarketMovers(staggerMs: Long = 0L, showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (staggerMs > 0L) {
                kotlinx.coroutines.delay(staggerMs)
            }
            if (showSpinner) _moversLoading.value = true
            when (val response = repository.getMarketMovers(limit = 8)) {
                is Result.Success -> {
                    _momentumLeaders.value = response.data.gainers
                    _pressureZone.value = response.data.losers
                    _moversUniverseSize.value = response.data.universeSize
                }
                is Result.Error -> {
                    // Keep prior movers on failure; Home can fall back to local quotes.
                }
                Result.Loading -> Unit
            }
            _moversLoading.value = false
        }
    }

    fun refreshPracticeIdeas(limit: Int = 6) {
        viewModelScope.launch {
            _practiceIdeasLoading.value = true
            when (val response = repository.getPracticeIdeas(limit = limit)) {
                is Result.Success -> {
                    _practiceIdeas.value = response.data.ideas
                    _practiceIdeasDisclaimer.value = response.data.disclaimer
                }
                is Result.Error -> {
                    // Keep prior cards on failure so Home still feels useful offline-ish.
                }
                Result.Loading -> Unit
            }
            _practiceIdeasLoading.value = false
        }
    }

    fun refreshIntradayTips(limit: Int = 4, advanceShare: Double? = lastAdvanceShare) {
        lastAdvanceShare = advanceShare
        viewModelScope.launch {
            _intradayTipsLoading.value = true
            when (val response = repository.getIntradayTips(limit = limit, advanceShare = advanceShare)) {
                is Result.Success -> _intradayTips.value = response.data
                is Result.Error -> {
                    if (_intradayTips.value == null) {
                        // Keep null so UI can show local session-phase fallback.
                    }
                }
                Result.Loading -> Unit
            }
            _intradayTipsLoading.value = false
        }
    }

    fun selectInvestorTipTopic(topic: String) {
        val normalized = topic.trim().lowercase().ifBlank { "long_term" }
        if (_investorTipTopic.value == normalized && _investorTips.value.tips.isNotEmpty()) {
            return
        }
        _investorTipTopic.value = normalized
        _investorTips.value = localInvestorTips(normalized)
        refreshInvestorTips(topic = normalized)
    }

    fun refreshInvestorTips(topic: String = _investorTipTopic.value, limit: Int = 4) {
        val normalized = topic.trim().lowercase().ifBlank { "long_term" }
        _investorTipTopic.value = normalized
        viewModelScope.launch {
            _investorTipsLoading.value = true
            when (val response = repository.getInvestorTips(topic = normalized, limit = limit)) {
                is Result.Success -> _investorTips.value = response.data
                is Result.Error -> {
                    if (_investorTips.value.tips.isEmpty() || _investorTips.value.topic != normalized) {
                        _investorTips.value = localInvestorTips(normalized, limit)
                    }
                }
                Result.Loading -> Unit
            }
            _investorTipsLoading.value = false
        }
    }

    fun moveWidgetUp(widget: String) {
        val idx = _widgetOrder.value.indexOf(widget)
        if (idx > 0) {
            val newOrder = _widgetOrder.value.toMutableList().apply {
                add(idx - 1, removeAt(idx))
            }
            _widgetOrder.value = newOrder
            saveWidgetOrder(newOrder)
        }
    }

    fun moveWidgetDown(widget: String) {
        val idx = _widgetOrder.value.indexOf(widget)
        if (idx >= 0 && idx < _widgetOrder.value.size - 1) {
            val newOrder = _widgetOrder.value.toMutableList().apply {
                add(idx + 1, removeAt(idx))
            }
            _widgetOrder.value = newOrder
            saveWidgetOrder(newOrder)
        }
    }

    private fun saveWidgetOrder(order: List<String>) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            PinnedWidgetsStore.setWidgetOrder(context, order)
        }
    }

    private fun loadPinnedStocks() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            _pinnedStocks.value = PinnedStocksStore.getPinnedStocks(context).first()
        }
    }

    private fun loadPinnedWidgets() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            _portfolioPinned.value = PinnedWidgetsStore.isPortfolioPinned(context).first()
            _newsPinned.value = PinnedWidgetsStore.isNewsPinned(context).first()
            _watchlistPinned.value = PinnedWidgetsStore.isWatchlistPinned(context).first()
        }
    }
    fun toggleWatchlistPin() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val newValue = !_watchlistPinned.value
            _watchlistPinned.value = newValue
            PinnedWidgetsStore.setWatchlistPinned(context, newValue)
        }
    }

    fun togglePin(symbol: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val current = _pinnedStocks.value.toMutableSet()
            if (current.contains(symbol)) current.remove(symbol) else current.add(symbol)
            _pinnedStocks.value = current
            PinnedStocksStore.setPinnedStocks(context, current)
        }
    }
    fun togglePortfolioPin() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val newValue = !_portfolioPinned.value
            _portfolioPinned.value = newValue
            PinnedWidgetsStore.setPortfolioPinned(context, newValue)
        }
    }

    fun toggleNewsPin() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val newValue = !_newsPinned.value
            _newsPinned.value = newValue
            PinnedWidgetsStore.setNewsPinned(context, newValue)
        }
    }
}
