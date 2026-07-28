
package com.bysel.trader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.PinnedStocksStore
import com.bysel.trader.data.PinnedWidgetsStore
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.MarketMoverQuote
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
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
            _widgetOrder.value = listOf("portfolio", "news", "watchlist")
            PinnedWidgetsStore.setPortfolioPinned(context, false)
            PinnedWidgetsStore.setNewsPinned(context, true)
            PinnedWidgetsStore.setWatchlistPinned(context, true)
            PinnedWidgetsStore.setWidgetOrder(context, listOf("portfolio", "news", "watchlist"))
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

    init {
        loadPinnedStocks()
        loadPinnedWidgets()
        loadWidgetOrder()
        refreshMarketNews()
        refreshMarketMovers()
    }

    fun refreshMarketNews() {
        viewModelScope.launch {
            _newsLoading.value = true
            when (val response = repository.getMarketNews(limit = 10)) {
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

    fun refreshMarketMovers() {
        viewModelScope.launch {
            _moversLoading.value = true
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
