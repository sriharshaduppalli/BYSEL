package com.bysel.trader.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.bysel.trader.ai.OnDeviceLlmManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bysel.trader.data.models.*
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.auth.AuthTokenRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import com.bysel.trader.utils.PromptBuilder
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.bysel.trader.alerts.AlertsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn

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

    private companion object {
        private val SECTOR_SUGGESTION_STOPWORDS = setOf(
            "TOP", "BEST", "STOCK", "STOCKS", "SECTOR", "THE", "AND", "FOR", "WITH",
            "BUY", "SELL", "HOLD", "ANALYZE", "COMPARE", "VS", "INDIA", "NSE", "BSE",
            "DEFENCE", "DEFENSE", "PHARMA", "BANK", "AUTO", "ENERGY", "FMCG", "METAL",
            "INFRA", "PSU", "REALTY", "RAILWAY", "IT", "LIVE", "QUOTE", "PRICE",
        )

        /** Keep Add-to-watchlist responsive; full /symbols can wait on cold start. */
        private const val CATALOG_LOAD_TIMEOUT_MS = 15_000L

        val TRACE_ID_PATTERN =
            Regex("(?i)(?:trace(?:\\s*id)?|traceId)\\s*[:=]\\s*([A-Za-z0-9._-]+)")
    }

    // --- Stream Health ---
    enum class StreamHealth { LIVE, RECONNECTING, OFFLINE }
    private val _streamHealth = MutableStateFlow(StreamHealth.OFFLINE)
    val streamHealth: StateFlow<StreamHealth> = _streamHealth.asStateFlow()

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
    private val _lastQuoteUpdateAt = MutableStateFlow(0L)
    val lastQuoteUpdateAt: StateFlow<Long> = _lastQuoteUpdateAt.asStateFlow()

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

    /** Full NSE/BSE searchable catalog (symbol + company name) for watchlist browse. */
    private val _symbolCatalog = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val symbolCatalog: StateFlow<List<StockSearchResult>> = _symbolCatalog.asStateFlow()

    private val _symbolCatalogLoading = MutableStateFlow(false)
    val symbolCatalogLoading: StateFlow<Boolean> = _symbolCatalogLoading.asStateFlow()

    private var symbolCatalogJob: Job? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** True while a quote network refresh is in flight — even if cached prices are already on screen. */
    private val _quotesRefreshing = MutableStateFlow(false)
    val quotesRefreshing: StateFlow<Boolean> = _quotesRefreshing.asStateFlow()

    /** True while holdings are fetching from the server (cache may already be visible). */
    private val _holdingsRefreshing = MutableStateFlow(false)
    val holdingsRefreshing: StateFlow<Boolean> = _holdingsRefreshing.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _marketError = MutableStateFlow<String?>(null)
    val marketError: StateFlow<String?> = _marketError.asStateFlow()

    private val _portfolioError = MutableStateFlow<String?>(null)
    val portfolioError: StateFlow<String?> = _portfolioError.asStateFlow()

    /** Orders, F&O tickets, alerts, wallet — not holdings/health. */
    private val _tradeError = MutableStateFlow<String?>(null)
    val tradeError: StateFlow<String?> = _tradeError.asStateFlow()

    /** Alias of [tradeError] for Trade-tab internals. */
    private val _error = _tradeError
    val error: StateFlow<String?> = _tradeError.asStateFlow()

    private val _lastOrderTraceId = MutableStateFlow<String?>(null)
    val lastOrderTraceId: StateFlow<String?> = _lastOrderTraceId.asStateFlow()

    private val _lastExecutedOrder = MutableStateFlow<OrderResponse?>(null)
    val lastExecutedOrder: StateFlow<OrderResponse?> = _lastExecutedOrder.asStateFlow()

    private val _orderExecutionLoading = MutableStateFlow(false)
    val orderExecutionLoading: StateFlow<Boolean> = _orderExecutionLoading.asStateFlow()

    private val walletCachePrefs = getApplication<Application>()
        .getSharedPreferences("bysel_wallet_cache", Context.MODE_PRIVATE)
    private val _walletBalance = MutableStateFlow(readCachedWalletBalance())
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
    private val _fastRefreshEnabled = MutableStateFlow(settingsPrefs.getBoolean("fast_refresh_enabled", true))
    val fastRefreshEnabled: StateFlow<Boolean> = _fastRefreshEnabled.asStateFlow()

    // Single-quote detail
    private val _selectedQuote = MutableStateFlow<Quote?>(null)
    val selectedQuote: StateFlow<Quote?> = _selectedQuote.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    // Historical OHLCV for selected symbol (used for charting)
    private val _quoteHistory = MutableStateFlow<List<HistoryCandle>>(emptyList())
    val quoteHistory: StateFlow<List<HistoryCandle>> = _quoteHistory.asStateFlow()
    private val _quoteHistoryLoading = MutableStateFlow(false)
    val quoteHistoryLoading: StateFlow<Boolean> = _quoteHistoryLoading.asStateFlow()

    private val _detailNews = MutableStateFlow<List<MarketNewsHeadline>>(emptyList())
    val detailNews: StateFlow<List<MarketNewsHeadline>> = _detailNews.asStateFlow()

    private val _detailNewsLoading = MutableStateFlow(false)
    val detailNewsLoading: StateFlow<Boolean> = _detailNewsLoading.asStateFlow()

    private val _detailNewsError = MutableStateFlow<String?>(null)
    val detailNewsError: StateFlow<String?> = _detailNewsError.asStateFlow()

    // AI assistant
    private val _aiResponse = MutableStateFlow<AiAssistantResponse?>(null)
    val aiResponse: StateFlow<AiAssistantResponse?> = _aiResponse.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()
    @Volatile private var lastAiSuccessAtMs: Long = 0L
    @Volatile private var lastAiWarmAtMs: Long = 0L
    private val AI_WARM_WINDOW_MS = 8 * 60_000L
    // Used only for telemetry / future UX; typing indicator no longer shows wake copy.
    private val _aiLikelyColdStart = MutableStateFlow(false)
    val aiLikelyColdStart: StateFlow<Boolean> = _aiLikelyColdStart.asStateFlow()

    private fun refreshAiColdStartFlag() {
        val now = System.currentTimeMillis()
        val recentlySucceeded = lastAiSuccessAtMs > 0L && (now - lastAiSuccessAtMs) < AI_WARM_WINDOW_MS
        val recentlyWarmed = lastAiWarmAtMs > 0L && (now - lastAiWarmAtMs) < AI_WARM_WINDOW_MS
        _aiLikelyColdStart.value = !(recentlySucceeded || recentlyWarmed)
    }

    /** Best-effort ping so the first real chat prompt does not pay cold-start alone. */
    fun warmAiBackend() {
        viewModelScope.launch {
            when (repository.warmAiBackend()) {
                is Result.Success -> {
                    lastAiWarmAtMs = System.currentTimeMillis()
                    refreshAiColdStartFlag()
                }
                else -> Unit
            }
        }
    }


    // portfolio/health/heatmap
    private val _portfolioHealth = MutableStateFlow<PortfolioHealthScore?>(null)
    val portfolioHealth: StateFlow<PortfolioHealthScore?> = _portfolioHealth.asStateFlow()
    private val _healthLoading = MutableStateFlow(false)
    val healthLoading: StateFlow<Boolean> = _healthLoading.asStateFlow()

    private val _marketHeatmap = MutableStateFlow<MarketHeatmap?>(null)
    val marketHeatmap: StateFlow<MarketHeatmap?> = _marketHeatmap.asStateFlow()
    private val _heatmapLoading = MutableStateFlow(false)
    val heatmapLoading: StateFlow<Boolean> = _heatmapLoading.asStateFlow()

    private val _signalLabBuckets = MutableStateFlow<List<SignalLabBucketFeed>>(emptyList())
    val signalLabBuckets: StateFlow<List<SignalLabBucketFeed>> = _signalLabBuckets.asStateFlow()
    private val _signalLabBucketsLoading = MutableStateFlow(false)
    val signalLabBucketsLoading: StateFlow<Boolean> = _signalLabBucketsLoading.asStateFlow()

    // Phase 1 products
    private val _mutualFunds = MutableStateFlow<List<MutualFund>>(emptyList())
    val mutualFunds: StateFlow<List<MutualFund>> = _mutualFunds.asStateFlow()

    private val _mutualFundCompare = MutableStateFlow<MutualFundCompareResponse?>(null)
    val mutualFundCompare: StateFlow<MutualFundCompareResponse?> = _mutualFundCompare.asStateFlow()

    private val _mutualFundRecommendations = MutableStateFlow<MutualFundRecommendationResponse?>(null)
    val mutualFundRecommendations: StateFlow<MutualFundRecommendationResponse?> = _mutualFundRecommendations.asStateFlow()

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

    /** Options / Futures form notices — never shown on Spot My list or the app-wide snackbar. */
    private val _derivativesError = MutableStateFlow<String?>(null)
    val derivativesError: StateFlow<String?> = _derivativesError.asStateFlow()

    fun clearDerivativesError() { _derivativesError.value = null }

    /** One-shot Trade workspace deep-link: 0 Spot, 1 Advanced, 2 Options, 3 Futures. */
    private val _pendingTradeWorkspace = MutableStateFlow<Int?>(null)
    val pendingTradeWorkspace: StateFlow<Int?> = _pendingTradeWorkspace.asStateFlow()

    fun requestTradeWorkspace(index: Int) {
        _pendingTradeWorkspace.value = index.coerceIn(0, 3)
    }

    fun clearPendingTradeWorkspace() {
        _pendingTradeWorkspace.value = null
    }

    // Advanced order engine / derivatives / wealth / copilot
    private val _advancedOrderResponse = MutableStateFlow<AdvancedOrderResponse?>(null)
    val advancedOrderResponse: StateFlow<AdvancedOrderResponse?> = _advancedOrderResponse.asStateFlow()

    private val _triggerOrders = MutableStateFlow<List<TriggerOrderSummary>>(emptyList())
    val triggerOrders: StateFlow<List<TriggerOrderSummary>> = _triggerOrders.asStateFlow()

    private val _triggerEvaluation = MutableStateFlow<TriggerEvaluationResponse?>(null)
    val triggerEvaluation: StateFlow<TriggerEvaluationResponse?> = _triggerEvaluation.asStateFlow()

    private val _basketOrders = MutableStateFlow<List<BasketOrderResponse>>(emptyList())
    val basketOrders: StateFlow<List<BasketOrderResponse>> = _basketOrders.asStateFlow()

    private val _optionChain = MutableStateFlow<OptionChainResponse?>(null)
    val optionChain: StateFlow<OptionChainResponse?> = _optionChain.asStateFlow()

    private val _strategyPreview = MutableStateFlow<StrategyPreviewResponse?>(null)
    val strategyPreview: StateFlow<StrategyPreviewResponse?> = _strategyPreview.asStateFlow()

    private val _futuresContracts = MutableStateFlow<FuturesContractsResponse?>(null)
    val futuresContracts: StateFlow<FuturesContractsResponse?> = _futuresContracts.asStateFlow()

    private val _futuresTicketPreview = MutableStateFlow<FuturesTicketPreviewResponse?>(null)
    val futuresTicketPreview: StateFlow<FuturesTicketPreviewResponse?> = _futuresTicketPreview.asStateFlow()

    private val _familyDashboard = MutableStateFlow<FamilyDashboardResponse?>(null)
    val familyDashboard: StateFlow<FamilyDashboardResponse?> = _familyDashboard.asStateFlow()

    private val _goalPlans = MutableStateFlow<List<GoalPlanResponse>>(emptyList())
    val goalPlans: StateFlow<List<GoalPlanResponse>> = _goalPlans.asStateFlow()

    private val _copilotPreTradeSignal = MutableStateFlow<CopilotSignal?>(null)
    val copilotPreTradeSignal: StateFlow<CopilotSignal?> = _copilotPreTradeSignal.asStateFlow()

    private val _preTradeEstimate = MutableStateFlow<PreTradeEstimateResponse?>(null)
    val preTradeEstimate: StateFlow<PreTradeEstimateResponse?> = _preTradeEstimate.asStateFlow()

    private val _copilotPostTradeReview = MutableStateFlow<CopilotPostTradeResponse?>(null)
    val copilotPostTradeReview: StateFlow<CopilotPostTradeResponse?> = _copilotPostTradeReview.asStateFlow()

    private val _copilotPortfolioActions = MutableStateFlow<CopilotPortfolioActionsResponse?>(null)
    val copilotPortfolioActions: StateFlow<CopilotPortfolioActionsResponse?> = _copilotPortfolioActions.asStateFlow()

    private val _orderTraceLookup = MutableStateFlow<OrderTraceLookupResponse?>(null)
    val orderTraceLookup: StateFlow<OrderTraceLookupResponse?> = _orderTraceLookup.asStateFlow()

    private val _advancedLoading = MutableStateFlow(false)
    val advancedLoading: StateFlow<Boolean> = _advancedLoading.asStateFlow()

    private val _derivativesLoading = MutableStateFlow(false)
    val derivativesLoading: StateFlow<Boolean> = _derivativesLoading.asStateFlow()

    private val _investorTips = MutableStateFlow(com.bysel.trader.ui.components.localInvestorTips("long_term"))
    val investorTips: StateFlow<InvestorTipsResponse> = _investorTips.asStateFlow()
    private val _investorTipsLoading = MutableStateFlow(false)
    val investorTipsLoading: StateFlow<Boolean> = _investorTipsLoading.asStateFlow()

    private val _wealthLoading = MutableStateFlow(false)
    val wealthLoading: StateFlow<Boolean> = _wealthLoading.asStateFlow()

    private val _copilotLoading = MutableStateFlow(false)
    val copilotLoading: StateFlow<Boolean> = _copilotLoading.asStateFlow()

    private var autoRefreshJob: Job? = null

    // --- Investor Portfolios (Smart Money Tracker) ---
    private val _investorPortfolios = MutableStateFlow<List<InvestorPortfolio>>(emptyList())
    val investorPortfolios: StateFlow<List<InvestorPortfolio>> = _investorPortfolios.asStateFlow()
    private val _investorPortfoliosLoading = MutableStateFlow(false)
    val investorPortfoliosLoading: StateFlow<Boolean> = _investorPortfoliosLoading.asStateFlow()
    private val _investorPortfolioChanges = MutableStateFlow<List<InvestorPortfolioChangeFeed>>(emptyList())
    val investorPortfolioChanges: StateFlow<List<InvestorPortfolioChangeFeed>> = _investorPortfolioChanges.asStateFlow()
    private val _smartMoneyIdeas = MutableStateFlow<List<SmartMoneyIdeaFeedCard>>(emptyList())
    val smartMoneyIdeas: StateFlow<List<SmartMoneyIdeaFeedCard>> = _smartMoneyIdeas.asStateFlow()
    private val _smartMoneyQuarterLabel = MutableStateFlow<String?>(null)
    val smartMoneyQuarterLabel: StateFlow<String?> = _smartMoneyQuarterLabel.asStateFlow()
    private val _investorInsightsLoading = MutableStateFlow(false)
    val investorInsightsLoading: StateFlow<Boolean> = _investorInsightsLoading.asStateFlow()

    private var investorPortfoliosJob: Job? = null
    private var investorInsightsJob: Job? = null

    private val AUTO_REFRESH_INTERVAL = 15_000L
    private val FAST_REFRESH_INTERVAL = 1_000L
    private val FOREGROUND_WARMUP_DEBOUNCE = 3_000L
    private val INITIAL_STATUS_WARMUP_DELAY = 150L
    private val INITIAL_HOLDINGS_WARMUP_DELAY = 350L
    private val QUOTE_STALE_THRESHOLD = 20_000L
    private val HOLDINGS_STALE_THRESHOLD = 45_000L
    private val WALLET_STALE_THRESHOLD = 30_000L
    private val QUOTE_REFRESH_DEBOUNCE = 1_250L
    private val ALL_QUOTES_WARMUP_INTERVAL = 10 * 60_000L
    private val SIGNAL_LAB_REFRESH_DEBOUNCE = 15_000L
    private val HEATMAP_REFRESH_DEBOUNCE = 4_000L
    private val HEATMAP_STALE_THRESHOLD = 45_000L
    private val RESUME_HEATMAP_DELAY = 500L
    private val WARM_BACKEND_BUDGET_MS = 12_000L
    private val KEEPALIVE_INTERVAL_MS = 10 * 60_000L
    private val TRIGGER_AUTO_EVAL_INTERVAL = 30_000L
    private var lastForegroundWarmupAt = 0L
    private var lastQuotesRefreshAt = 0L
    private var lastQuotesRefreshRequestAt = 0L
    private var lastAllQuotesWarmupAt = 0L
    private var lastSignalLabRefreshAt = 0L
    private var lastHeatmapRefreshAt = 0L  // Track heatmap cache timestamp
    private var lastHoldingsRefreshAt = 0L
    private var lastWalletRefreshAt = 0L
    private var lastTriggerAutoEvalAt = 0L
    private var triggerAutoEvalJob: Job? = null
    /** Bumped on local wallet mutations so in-flight getWallet cannot overwrite fresher UI state. */
    private var walletEpoch = 0
    private var walletMutationsInFlight = 0
    private var activeHistoryRequestKey: String? = null
    private var quotesRefreshJob: Job? = null
    private var holdingsRefreshJob: Job? = null
    private var heatmapJob: Job? = null
    private var keepaliveJob: Job? = null
    private val defaultSymbols = listOf(
        "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN",
        "ICICIBANK", "ITC", "LT", "KOTAKBANK", "HINDUNILVR",
        "BHARTIARTL", "AXISBANK", "BAJFINANCE", "ASIANPAINT", "MARUTI",
        "WIPRO", "NTPC", "POWERGRID", "ULTRACEMCO", "TITAN"
    )

    // Benchmark indices the Home strip needs on every cold start.
    private val indexSymbols = listOf("NIFTY50", "SENSEX", "BANKNIFTY")

    private fun trackedSymbols(additional: List<String> = emptyList()): List<String> {
        return (indexSymbols + defaultSymbols + _watchlist.value + _holdings.value.map { it.symbol } + additional)
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun overlayHoldingsFromQuotes(quotes: List<Quote>) {
        if (_holdings.value.isEmpty() || quotes.isEmpty()) return
        val bySymbol = quotes.associateBy { it.symbol.uppercase() }
        var changed = false
        val updated = _holdings.value.map { holding ->
            val quote = bySymbol[holding.symbol.uppercase()] ?: return@map holding
            if (quote.last <= 0.0) return@map holding
            val pnl = (quote.last - holding.avgPrice) * holding.qty
            if (holding.last == quote.last && holding.pnl == pnl) holding
            else {
                changed = true
                holding.copy(last = quote.last, pnl = pnl)
            }
        }
        if (changed) _holdings.value = updated
    }

    private fun normalizeWatchlistSymbol(raw: String): String {
        val cleaned = raw.trim().uppercase().replace(" ", "")
        if (cleaned.isBlank()) return ""

        val prefixed = when {
            cleaned.startsWith("NSE:") -> cleaned.removePrefix("NSE:")
            cleaned.startsWith("BSE:") -> "${cleaned.removePrefix("BSE:").removeSuffix(".BO")}.BO"
            else -> cleaned
        }

        if (prefixed.endsWith(".BO")) {
            val base = prefixed.removeSuffix(".BO")
            return if (base.isNotBlank()) "$base.BO" else ""
        }

        if (prefixed.endsWith(".NS")) {
            return prefixed.removeSuffix(".NS")
        }

        if (prefixed.length == 6 && prefixed.all { it.isDigit() }) {
            return "$prefixed.BO"
        }

        return prefixed
    }

    private fun readNormalizedWatchlist(): List<String> {
        val raw = watchlistPrefs.getStringSet("symbols", emptySet())?.toList() ?: emptyList()
        return raw
            .map { normalizeWatchlistSymbol(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private fun persistWatchlist(symbols: List<String>) {
        watchlistPrefs.edit().putStringSet("symbols", symbols.toSet()).apply()
        _watchlist.value = symbols
    }

    private fun markQuoteUpdate(nowMs: Long = System.currentTimeMillis()) {
        lastQuotesRefreshAt = nowMs
        _lastQuoteUpdateAt.value = nowMs
        _streamHealth.value = StreamHealth.LIVE
    }

    private fun syncSelectedQuoteFrom(quotes: List<Quote>) {
        val current = _selectedQuote.value ?: return
        val refreshed = quotes.firstOrNull { it.symbol.equals(current.symbol, ignoreCase = true) } ?: return
        _selectedQuote.value = refreshed
    }

    private fun resetStockDetailContext() {
        _quoteHistory.value = emptyList()
        _quoteHistoryLoading.value = false
        _detailNews.value = emptyList()
        _detailNewsLoading.value = false
        _detailNewsError.value = null
        _copilotPreTradeSignal.value = null
        _preTradeEstimate.value = null
    }

    private fun loadStockDetailContext(symbol: String) {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) return

        _pendingDetailSymbol.value = normalizedSymbol
        // Keep existing candles while the new window loads to avoid blank chart flashes.
        _detailNews.value = emptyList()
        _detailNewsLoading.value = false
        _detailNewsError.value = null
        _copilotPreTradeSignal.value = null
        _preTradeEstimate.value = null
        fetchQuoteHistory(normalizedSymbol)
        refreshDetailNews(normalizedSymbol)
    }

    // Paging state for quotes list (legacy append API kept for compatibility)
    private val _pagedQuotes = MutableStateFlow<List<Quote>>(emptyList())
    val pagedQuotes: StateFlow<List<Quote>> = _pagedQuotes.asStateFlow()
    private var currentPage = 0
    private val pageSize = 50
    // Thread-safe pagination loading flag
    private val _loadingPage = MutableStateFlow(false)

    /** Room-backed Paging 3 flow for the Trade quotes list. */
    val quotesPagingFlow = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            prefetchDistance = 10,
            enablePlaceholders = false,
            initialLoadSize = pageSize,
        ),
        pagingSourceFactory = { repository.quotesPagingSource() },
    ).flow.cachedIn(viewModelScope)

    init {
        loadAchievements()
        // On-device LLM is heavy — defer well past first Home paint / TTFD.
        viewModelScope.launch {
            kotlinx.coroutines.delay(6_000)
            if (OnDeviceLlmManager.isModelDownloaded(getApplication())) {
                OnDeviceLlmManager.initialize(getApplication())
            }
        }
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
        // Load cached quotes + holdings immediately so reopen is not blank while network wakes.
        viewModelScope.launch {
            try {
                val wl = readNormalizedWatchlist()
                val symbolsToLoad = homePrioritySymbols(wl)
                repository.getCachedQuotes(symbolsToLoad).collectLatest { cached ->
                    if (cached.isNotEmpty()) _quotes.value = cached
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val cachedHoldings = repository.getCachedHoldings().firstOrNull().orEmpty()
                if (cachedHoldings.isNotEmpty() && _holdings.value.isEmpty()) {
                    _holdings.value = cachedHoldings
                }
            } catch (_: Exception) { }
        }

        // restore watchlist into state
        val restoredWatchlist = readNormalizedWatchlist()
        _watchlist.value = restoredWatchlist
        watchlistPrefs.edit().putStringSet("symbols", restoredWatchlist.toSet()).apply()
        // Smaller first network burst (indices + watchlist + holdings), then expand.
        refreshQuotes(force = true, symbolsOverride = homePrioritySymbols(restoredWatchlist))
        viewModelScope.launch {
            kotlinx.coroutines.delay(1_200)
            refreshQuotes(force = false)
        }
        scheduleInitialWarmup()
    }

    /** Symbols needed for Home first paint — keep cold-start quote fan-out small. */
    private fun homePrioritySymbols(additional: List<String> = emptyList()): List<String> {
        val fromHoldings = _holdings.value.map { it.symbol.trim().uppercase() }.filter { it.isNotBlank() }
        val merged = (indexSymbols + _watchlist.value + additional + fromHoldings)
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (merged.size <= 8) {
            (merged + defaultSymbols.take(8)).distinct()
        } else {
            merged.take(24)
        }
    }

    private fun walletCacheKey(): String {
        val uid = AuthSessionManager.getUserId()
        return if (uid != null && uid > 0) "balance_$uid" else "balance_anon"
    }

    private fun readCachedWalletBalance(): Double {
        val raw = walletCachePrefs.getString(walletCacheKey(), null) ?: return 0.0
        return raw.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: 0.0
    }

    private fun persistCachedWalletBalance(balance: Double) {
        if (balance < 0.0) return
        walletCachePrefs.edit().putString(walletCacheKey(), balance.toString()).apply()
    }

    

    fun addToWatchlist(symbol: String) {
        val normalized = normalizeWatchlistSymbol(symbol)
        if (normalized.isBlank()) return

        val current = readNormalizedWatchlist().toMutableList()
        if (!current.contains(normalized)) {
            current.add(normalized)
            val updated = current.distinct().sorted()
            persistWatchlist(updated)
            refreshQuotes()
        }
    }

    fun removeFromWatchlist(symbol: String) {
        val normalized = normalizeWatchlistSymbol(symbol)
        if (normalized.isBlank()) return

        val base = normalized.removeSuffix(".BO").removeSuffix(".NS")
        val aliases = linkedSetOf(
            normalized,
            base,
            "$base.NS",
            "$base.BO",
        )
        val current = readNormalizedWatchlist()
        val updated = current.filterNot { it in aliases }
        if (updated.size != current.size) {
            persistWatchlist(updated)
            refreshQuotes()
        }
    }

    private fun loadAchievements() {
        val unlocked = achievementPrefs.getStringSet("unlocked", emptySet()) ?: emptySet()
        _achievements.value = defaultAchievementsFromCode().map {
            if (unlocked.contains(it.id)) it.copy(unlocked = true) else it
        }
    }

    /**
     * Only what the home screen needs to render. The full symbol universe used to be
     * fetched here too, which cost a whole-market quote request on every launch; the
     * trading screen now loads it on first visit instead.
     */
    private fun scheduleInitialWarmup() {
        viewModelScope.launch {
            // Wake market host first (short timeout), then pull user-critical data.
            launch { repository.warmMarketBackend() }
            kotlinx.coroutines.delay(INITIAL_STATUS_WARMUP_DELAY)
            refreshMarketStatus()
            refreshWallet()

            kotlinx.coroutines.delay(INITIAL_HOLDINGS_WARMUP_DELAY)
            refreshHoldings()
        }
        // AI warm stays on the AI tab — same host, so an early AI /health
        // would compete with wallet/holdings during cold start.
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
            persistCachedWalletBalance(100000.0)
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
    private fun mergeQuotesWithExisting(incoming: List<Quote>): List<Quote> {
        val merged = _quotes.value.associateBy { it.symbol.uppercase() }.toMutableMap()
        incoming.forEach { quote ->
            val key = quote.symbol.uppercase()
            merged[key] = quote.withLiquidityFrom(merged[key])
        }
        return merged.values.sortedBy { it.symbol }
    }

    fun refreshQuotes(force: Boolean = false, symbolsOverride: List<String>? = null) {
        val symbols = symbolsOverride?.takeIf { it.isNotEmpty() } ?: trackedSymbols()
        val now = System.currentTimeMillis()
        if (!force && now - lastQuotesRefreshRequestAt < QUOTE_REFRESH_DEBOUNCE) return
        lastQuotesRefreshRequestAt = now

        quotesRefreshJob?.cancel()
        quotesRefreshJob = viewModelScope.launch {
            _quotesRefreshing.value = true
            try {
                try {
                    val cached = repository.getCachedQuotes(symbols).firstOrNull().orEmpty()
                    if (cached.isNotEmpty()) {
                        _quotes.value = mergeQuotesWithExisting(cached)
                        overlayHoldingsFromQuotes(cached)
                        syncSelectedQuoteFrom(_quotes.value)
                    }
                } catch (_: Exception) {
                    // Cached quote read failures should not block live refresh.
                }

                _isLoading.value = _quotes.value.isEmpty()
                repository.getQuotes(symbols).collect { result ->
                    when (result) {
                        is Result.Loading -> _isLoading.value = _quotes.value.isEmpty()
                        is Result.Success -> {
                            _quotes.value = mergeQuotesWithExisting(result.data)
                            overlayHoldingsFromQuotes(result.data)
                            _isLoading.value = false
                            markQuoteUpdate()
                            syncSelectedQuoteFrom(_quotes.value)
                            maybeAutoEvaluateTriggers(_quotes.value.map { it.symbol })
                            // Reset paging after success
                            _pagedQuotes.value = emptyList()
                            currentPage = 0
                            loadNextQuotesPage()
                        }
                        is Result.Error -> {
                            // Keep showing cached quotes; only surface a hard error when Home
                            // has nothing to render (otherwise "timeout" sits on Home Layout).
                            if (_quotes.value.isEmpty()) {
                                _marketError.value = result.message
                            } else {
                                android.util.Log.w(
                                    "TradingViewModel",
                                    "Quote refresh soft-failed with cache present: ${result.message}"
                                )
                            }
                            _isLoading.value = false
                        }
                    }
                }
            } finally {
                _quotesRefreshing.value = false
                _isLoading.value = false
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

    fun loadAllQuotes(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAllQuotesWarmupAt < ALL_QUOTES_WARMUP_INTERVAL) return
        lastAllQuotesWarmupAt = now

        viewModelScope.launch {
            repository.getAllQuotesFromApi().collectLatest { result ->
                if (result is Result.Success && result.data.isNotEmpty()) {
                    _quotes.value = mergeQuotesWithExisting(result.data)
                    markQuoteUpdate()
                    syncSelectedQuoteFrom(_quotes.value)
                }
            }
        }
    }

    fun refreshHoldings() {
        holdingsRefreshJob?.cancel()
        holdingsRefreshJob = viewModelScope.launch {
            _holdingsRefreshing.value = true
            try {
                repository.getHoldings().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _holdings.value = result.data
                            overlayHoldingsFromQuotes(_quotes.value)
                            lastHoldingsRefreshAt = System.currentTimeMillis()
                        }
                        is Result.Error -> {
                            if (_holdings.value.isEmpty()) {
                                _portfolioError.value = result.message
                            }
                        }
                        else -> {}
                    }
                }
            } finally {
                _holdingsRefreshing.value = false
            }
        }
    }

    fun refreshPortfolio() {
        val holdingSymbols = _holdings.value.map { it.symbol }
        refreshHoldings()
        refreshQuotes(
            force = true,
            symbolsOverride = if (holdingSymbols.isEmpty()) null else holdingSymbols + indexSymbols,
        )
        loadPortfolioHealth()
    }

    fun loadInvestorPortfolios() {
        if (_investorPortfoliosLoading.value) return
        investorPortfoliosJob?.cancel()
        investorPortfoliosJob = viewModelScope.launch {
            _investorPortfoliosLoading.value = true
            when (val result = repository.getInvestorPortfolios()) {
                is Result.Success -> _investorPortfolios.value = result.data
                is Result.Error -> { /* Smart Money screen keeps last snapshot / empty state */ }
                else -> {}
            }
            _investorPortfoliosLoading.value = false
        }
    }

    fun loadInvestorPortfolioInsights(
        maxChangesPerInvestor: Int = 3,
        ideaLimit: Int = 8,
    ) {
        if (_investorInsightsLoading.value) return
        investorInsightsJob?.cancel()
        investorInsightsJob = viewModelScope.launch {
            _investorInsightsLoading.value = true
            when (
                val result = repository.getInvestorPortfolioInsights(
                    maxChangesPerInvestor = maxChangesPerInvestor,
                    ideaLimit = ideaLimit,
                )
            ) {
                is Result.Success -> {
                    _investorPortfolioChanges.value = result.data.portfolioChanges
                    _smartMoneyIdeas.value = result.data.ideas
                    _smartMoneyQuarterLabel.value = result.data.quarterLabel
                }
                is Result.Error -> { /* keep last insights; do not leak onto Home/Portfolio */ }
                else -> {}
            }
            _investorInsightsLoading.value = false
        }
    }


    fun refreshWallet(force: Boolean = true) {
        viewModelScope.launch {
            if (!force && System.currentTimeMillis() - lastWalletRefreshAt <= WALLET_STALE_THRESHOLD) {
                return@launch
            }
            // Avoid clobbering an optimistic top-up with a slower getWallet round-trip.
            if (walletMutationsInFlight > 0) return@launch
            val epochAtStart = walletEpoch
            when (val r = repository.getWallet()) {
                is Result.Success -> {
                    // Drop stale responses if the user topped up while this fetch was in flight.
                    if (epochAtStart != walletEpoch || walletMutationsInFlight > 0) return@launch
                    _walletBalance.value = r.data.balance
                    persistCachedWalletBalance(r.data.balance)
                    lastWalletRefreshAt = System.currentTimeMillis()
                }
                is Result.Error -> { /* keep cached balance on screen */ }
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
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        val cacheKey = normalizedQuery.lowercase()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // debounce
            _isSearching.value = true
            try {
                val cached = searchCache[cacheKey]
                if (cached != null) {
                    _searchResults.value = cached
                } else {
                    when (val r = repository.searchStocks(normalizedQuery)) {
                        is Result.Success -> {
                            _searchResults.value = r.data
                            searchCache[cacheKey] = r.data
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

    /**
     * Loads the full listed-symbol catalog once (cached in memory) so users can browse
     * by company name without knowing tickers.
     *
     * Bounded by [CATALOG_LOAD_TIMEOUT_MS] so Add-to-watchlist never sits on
     * "Loading catalog…" through a full OkHttp/Render cold-start window.
     * Typed search still works via /search even when this fails.
     */
    fun ensureSymbolCatalogLoaded(force: Boolean = false) {
        if (!force && _symbolCatalog.value.isNotEmpty()) return
        if (!force && symbolCatalogJob?.isActive == true) return
        if (force) {
            symbolCatalogJob?.cancel()
        }
        symbolCatalogJob = viewModelScope.launch {
            _symbolCatalogLoading.value = true
            try {
                val r = withTimeoutOrNull(CATALOG_LOAD_TIMEOUT_MS) {
                    repository.getAllSymbols()
                }
                when (r) {
                    is Result.Success -> {
                        // Rebuild via normalized() — never .copy() on Gson-deserialized rows.
                        // /symbols omits matchType; Gson null + non-null copy() crashed Trade browse.
                        _symbolCatalog.value = r.data
                            .map { it.normalized() }
                            .filter { it.symbol.isNotBlank() }
                            .distinctBy { it.symbol }
                            .sortedBy { it.name.lowercase() }
                    }
                    is Result.Error -> {
                        if (_symbolCatalog.value.isEmpty()) {
                            _error.value = r.message ?: "Could not load stock list"
                        }
                    }
                    null -> {
                        // Timed out — leave catalog empty; sheet falls back to typed /search.
                        if (_symbolCatalog.value.isEmpty()) {
                            android.util.Log.w(
                                "TradingViewModel",
                                "Symbol catalog load timed out after ${CATALOG_LOAD_TIMEOUT_MS}ms",
                            )
                        }
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_symbolCatalog.value.isEmpty()) {
                    _error.value = e.message ?: "Could not load stock list"
                }
            } finally {
                _symbolCatalogLoading.value = false
            }
        }
    }

    // --- Single quote ---
    fun setSelectedQuote(quote: Quote) {
        _selectedQuote.value = quote
        loadStockDetailContext(quote.symbol)
    }

    /** Symbol currently being opened for Stock Detail (AI Chart / search). */
    private val _pendingDetailSymbol = MutableStateFlow<String?>(null)

    /** In-memory per-window history so 5D/1M/3M/1Y switches stay seamless. */
    private val historyWindowCache = LinkedHashMap<String, List<HistoryCandle>>(12)

    fun fetchAndSelectQuote(symbol: String) {
        openStockDetail(symbol)
    }

    /**
     * Open Stock Detail with quote + 1M daily candles fetched together.
     * Used by AI "View chart" so the chart is ready when the screen paints.
     */
    fun openStockDetail(
        symbol: String,
        period: String = "1mo",
        interval: String = "1d",
    ) {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) {
            _error.value = "Symbol is required"
            return
        }
        if (!isPlausibleNseSymbol(normalizedSymbol)) {
            _error.value = "Couldn't open chart — \"$normalizedSymbol\" is not a stock symbol"
            return
        }

        val normalizedPeriod = period.trim().lowercase().ifBlank { "1mo" }
        val normalizedInterval = interval.trim().lowercase().ifBlank { "1d" }
        val requestKey = "$normalizedSymbol|$normalizedPeriod|$normalizedInterval"

        _pendingDetailSymbol.value = normalizedSymbol
        _detailLoading.value = true
        _quoteHistoryLoading.value = true
        activeHistoryRequestKey = requestKey
        // Keep prior quote only if same symbol; otherwise clear to avoid wrong-stock flash.
        if (_selectedQuote.value?.symbol?.uppercase() != normalizedSymbol) {
            _selectedQuote.value = null
            _quoteHistory.value = emptyList()
        }

        viewModelScope.launch {
            val quoteDeferred = async { repository.getQuote(normalizedSymbol) }
            val historyDeferred = async {
                repository.getQuoteHistory(normalizedSymbol, normalizedPeriod, normalizedInterval)
            }

            when (val quoteResult = quoteDeferred.await()) {
                is Result.Success -> {
                    if (_pendingDetailSymbol.value == normalizedSymbol) {
                        _selectedQuote.value = quoteResult.data
                        _error.value = null
                        refreshDetailNews(quoteResult.data.symbol)
                    }
                }
                is Result.Error -> {
                    if (_pendingDetailSymbol.value == normalizedSymbol) {
                        _error.value = quoteResult.message
                        _detailLoading.value = false
                        _quoteHistoryLoading.value = false
                    }
                    return@launch
                }
                else -> {
                    _detailLoading.value = false
                    _quoteHistoryLoading.value = false
                    return@launch
                }
            }

            // Show detail shell as soon as quote is ready; candles fill in next.
            if (_pendingDetailSymbol.value == normalizedSymbol) {
                _detailLoading.value = false
            }

            when (val historyResult = historyDeferred.await()) {
                is Result.Success -> {
                    if (
                        _pendingDetailSymbol.value == normalizedSymbol &&
                        activeHistoryRequestKey == requestKey
                    ) {
                        val cleaned = sanitizeHistoryCandles(historyResult.data)
                        rememberHistoryWindow(requestKey, cleaned)
                        _quoteHistory.value = cleaned
                        _error.value = null
                    }
                }
                is Result.Error -> {
                    if (
                        _pendingDetailSymbol.value == normalizedSymbol &&
                        activeHistoryRequestKey == requestKey
                    ) {
                        _error.value = historyResult.message
                    }
                }
                else -> {}
            }
            if (activeHistoryRequestKey == requestKey) {
                _quoteHistoryLoading.value = false
            }
        }
    }

    /** Reject prose words that chat parsers sometimes treat as tickers (e.g. OVERALL). */
    private fun isPlausibleNseSymbol(symbol: String): Boolean {
        if (symbol.length !in 2..20) return false
        if (!symbol.all { it.isLetterOrDigit() || it == '&' || it == '-' || it == '.' }) return false
        if (symbol in com.bysel.trader.utils.TradeIntentParser.KNOWN_FALSE_SYMBOLS) return false
        return true
    }

    private fun sanitizeHistoryCandles(candles: List<HistoryCandle>): List<HistoryCandle> {
        if (candles.isEmpty()) return candles
        // Ascending unique timestamps — required for chart libraries to paint correctly.
        return candles
            .asSequence()
            .filter { it.timestamp > 0L && it.high >= it.low && it.open > 0 && it.close > 0 }
            .sortedBy { it.timestamp }
            .distinctBy { it.timestamp }
            .toList()
    }

    private fun isHistoryForSymbol(symbol: String, requestKey: String): Boolean {
        if (activeHistoryRequestKey != requestKey) return false
        val pending = _pendingDetailSymbol.value
        val selected = _selectedQuote.value?.symbol?.uppercase()
        return pending == symbol || selected == symbol
    }

    fun fetchQuoteHistory(symbol: String, period: String = "1mo", interval: String = "1d") {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) return

        val normalizedPeriod = period.trim().lowercase().ifBlank { "1mo" }
        val normalizedInterval = interval.trim().lowercase().ifBlank { "1d" }
        val requestKey = "$normalizedSymbol|$normalizedPeriod|$normalizedInterval"
        activeHistoryRequestKey = requestKey
        _pendingDetailSymbol.value = normalizedSymbol

        // Instantly paint this window if we already fetched it this session.
        val warm = historyWindowCache[requestKey]
        if (warm != null && warm.isNotEmpty()) {
            _quoteHistory.value = warm
            _quoteHistoryLoading.value = true // soft refresh in background
        } else {
            _quoteHistory.value = emptyList()
            _quoteHistoryLoading.value = true
        }

        viewModelScope.launch {
            // Disk cache first (if any), then refresh from API
            try {
                repository.getCachedHistory(normalizedSymbol, normalizedPeriod, normalizedInterval).collectLatest { cached ->
                    if (isHistoryForSymbol(normalizedSymbol, requestKey) && cached.isNotEmpty()) {
                        val cleaned = sanitizeHistoryCandles(cached)
                        rememberHistoryWindow(requestKey, cleaned)
                        _quoteHistory.value = cleaned
                        _quoteHistoryLoading.value = false
                    }
                }
            } catch (_: Exception) {
                // ignore cache read errors
            }

            when (val r = repository.getQuoteHistory(normalizedSymbol, normalizedPeriod, normalizedInterval)) {
                is Result.Success -> {
                    if (isHistoryForSymbol(normalizedSymbol, requestKey)) {
                        val cleaned = sanitizeHistoryCandles(r.data)
                        rememberHistoryWindow(requestKey, cleaned)
                        _quoteHistory.value = cleaned
                        _error.value = null
                    }
                }
                is Result.Error -> {
                    if (isHistoryForSymbol(normalizedSymbol, requestKey)) {
                        // Keep warm candles if refresh failed.
                        if (_quoteHistory.value.isEmpty()) {
                            _error.value = r.message
                        }
                    }
                }
                else -> {}
            }
            if (activeHistoryRequestKey == requestKey) {
                _quoteHistoryLoading.value = false
            }
        }
    }

    private fun rememberHistoryWindow(requestKey: String, candles: List<HistoryCandle>) {
        if (candles.isEmpty()) return
        historyWindowCache[requestKey] = candles
        while (historyWindowCache.size > 16) {
            val oldest = historyWindowCache.keys.firstOrNull() ?: break
            historyWindowCache.remove(oldest)
        }
    }

    fun refreshDetailNews(symbol: String = _selectedQuote.value?.symbol.orEmpty()) {
        val normalizedSymbol = symbol.trim().uppercase()
        if (normalizedSymbol.isBlank()) return

        viewModelScope.launch {
            _detailNewsLoading.value = true
            when (val response = repository.getMarketNews(symbols = listOf(normalizedSymbol), limit = 4)) {
                is Result.Success -> {
                    if (_selectedQuote.value?.symbol?.uppercase() == normalizedSymbol) {
                        _detailNews.value = response.data.headlines
                        _detailNewsError.value = null
                    }
                }
                is Result.Error -> {
                    if (_selectedQuote.value?.symbol?.uppercase() == normalizedSymbol) {
                        _detailNewsError.value = response.message
                        if (_detailNews.value.isEmpty()) {
                            _detailNews.value = emptyList()
                        }
                    }
                }
                else -> {}
            }
            if (_selectedQuote.value?.symbol?.uppercase() == normalizedSymbol) {
                _detailNewsLoading.value = false
            }
        }
    }

    /**
     * Start a fast refresh loop that fetches quotes every [intervalMs] milliseconds.
     * This is intended for live-updating UI when the user is actively viewing
     * dashboard or detail screens. It will only perform network refreshes while
     * the market appears open (`marketStatus.isOpen == true`).
     */
    fun startFastRefresh(intervalMs: Long = FAST_REFRESH_INTERVAL, symbols: List<String>? = null) {
        val effectiveIntervalMs = intervalMs.coerceAtLeast(250L)
        val symbolsToTrack = symbols?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.distinct()
            ?: trackedSymbols()
        // avoid starting multiple jobs
        if (autoRefreshJob?.isActive == true) return
        // respect global enabled flag
        if (!_fastRefreshEnabled.value) return
        _streamHealth.value = StreamHealth.RECONNECTING
        autoRefreshJob = viewModelScope.launch {
            // Stale watchdog: if no update in 8 s while job is running, flag as RECONNECTING
            launch {
                while (isActive) {
                    kotlinx.coroutines.delay(8_000L)
                    val staleMs = System.currentTimeMillis() - _lastQuoteUpdateAt.value
                    if (staleMs > 8_000L && _streamHealth.value == StreamHealth.LIVE) {
                        _streamHealth.value = StreamHealth.RECONNECTING
                    }
                }
            }
            var lastStreamEmitAt = 0L
            try {
                repository.streamLiveQuotes(symbolsToTrack).collectLatest { result ->
                    if (!_fastRefreshPlaying.value) return@collectLatest
                    if (_requireCharging.value && !isDeviceCharging()) return@collectLatest
                    if (_requireUnmetered.value && !isOnUnmeteredNetwork()) return@collectLatest
                    val isMarketOpen = _marketStatus.value?.isOpen ?: true
                    if (!isMarketOpen) return@collectLatest

                    when (result) {
                        is Result.Success -> {
                            val now = System.currentTimeMillis()
                            if (now - lastStreamEmitAt < effectiveIntervalMs) {
                                return@collectLatest
                            }
                            lastStreamEmitAt = now
                            _quotes.value = mergeQuotesWithExisting(result.data)
                            overlayHoldingsFromQuotes(result.data)
                            markQuoteUpdate(now)
                            syncSelectedQuoteFrom(result.data)
                            evaluateAlerts(result.data)
                            maybeAutoEvaluateTriggers(result.data.map { it.symbol })
                        }
                        is Result.Error -> _marketError.value = result.message
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
        _streamHealth.value = StreamHealth.OFFLINE
    }

    fun onAppForegroundResume(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastForegroundWarmupAt < FOREGROUND_WARMUP_DEBOUNCE) {
            return
        }
        lastForegroundWarmupAt = now

        viewModelScope.launch {
            // Instant local seed if process was killed / state empty.
            if (_walletBalance.value <= 0.0) {
                val cached = readCachedWalletBalance()
                if (cached > 0.0) _walletBalance.value = cached
            }
            if (_holdings.value.isEmpty()) {
                try {
                    val cachedHoldings = repository.getCachedHoldings().firstOrNull().orEmpty()
                    if (cachedHoldings.isNotEmpty()) _holdings.value = cachedHoldings
                } catch (_: Exception) { }
            }

            // Auth refresh in parallel — do not block quotes/wallet on a long wake.
            launch(kotlinx.coroutines.Dispatchers.IO) {
                AuthTokenRefresher.refreshIfNeeded()
            }

            // Phase 1: wake host with a budget so user fetches are not starved forever.
            kotlinx.coroutines.withTimeoutOrNull(WARM_BACKEND_BUDGET_MS) {
                repository.warmMarketBackend()
            }

            // Phase 2: user-critical info first.
            refreshMarketStatus()
            val walletStale = System.currentTimeMillis() - lastWalletRefreshAt > WALLET_STALE_THRESHOLD
            if (force || walletStale) {
                refreshWallet(force = true)
            }

            val quotesAreStale = System.currentTimeMillis() - lastQuotesRefreshAt > QUOTE_STALE_THRESHOLD
            if (force || _quotes.value.isEmpty() || quotesAreStale) {
                refreshQuotes()
            }

            val holdingsStale = System.currentTimeMillis() - lastHoldingsRefreshAt > HOLDINGS_STALE_THRESHOLD
            if (force || _holdings.value.isEmpty() || holdingsStale) {
                refreshHoldings()
            }

            // Phase 3: heavier market surfaces after user data is in flight.
            kotlinx.coroutines.delay(RESUME_HEATMAP_DELAY)
            val heatmapStale = _marketHeatmap.value == null ||
                System.currentTimeMillis() - lastHeatmapRefreshAt > HEATMAP_STALE_THRESHOLD
            if (force || heatmapStale) {
                loadMarketHeatmap(force = false)
            }

            if (_fastRefreshEnabled.value) {
                startFastRefresh(symbols = trackedSymbols())
            }
            startKeepaliveLoop()
        }
    }

    fun onAppBackgroundPause() {
        stopFastRefresh()
        keepaliveJob?.cancel()
        keepaliveJob = null
        // Drop in-flight heatmap poll work so return-to-app is not stuck behind it.
        heatmapJob?.cancel()
        heatmapJob = null
        _heatmapLoading.value = false
    }

    private fun startKeepaliveLoop() {
        if (keepaliveJob?.isActive == true) return
        keepaliveJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(KEEPALIVE_INTERVAL_MS)
                runCatching { repository.warmMarketBackend() }
            }
        }
    }

    fun setFastRefreshEnabled(enabled: Boolean) {
        _fastRefreshEnabled.value = enabled
        settingsPrefs.edit().putBoolean("fast_refresh_enabled", enabled).apply()
        if (!enabled) {
            stopFastRefresh()
        } else {
            startFastRefresh(symbols = trackedSymbols())
        }
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

    private fun buildOrderErrorMessage(response: OrderResponse): String {
        val base = response.message?.trim().takeUnless { it.isNullOrBlank() }
            ?: "Order could not be placed"

        val action = when (response.errorCode?.trim()?.uppercase()) {
            "INSUFFICIENT_FUNDS" -> "Add funds or reduce quantity."
            "INSUFFICIENT_HOLDINGS" -> "Reduce sell quantity to available holdings."
            "INVALID_SYMBOL" -> "Check symbol and try again."
            "INVALID_QUANTITY" -> "Enter a quantity greater than zero."
            "INVALID_SIDE" -> "Use BUY or SELL only."
            "INVALID_IDEMPOTENCY_KEY" -> "Retry order once; avoid duplicate submissions."
            "IDEMPOTENCY_KEY_REUSED" -> "Use a fresh order request to avoid key conflicts."
            "MARKET_CLOSED" -> "Paper trading stays available after hours using last session prices."
            "PRICE_UNAVAILABLE" -> "Wait for live quote refresh and retry."
            "TRIGGER_NOT_MET" -> "Use a limit/trigger setup closer to market price."
            else -> null
        }

        // Keep Trace ID in _lastOrderTraceId only — do not append to user-visible error text.
        return listOfNotNull(base, action).joinToString("\n")
    }

    private fun extractTraceIdFromError(message: String?): String? {
        if (message.isNullOrBlank()) {
            return null
        }
        val match = TRACE_ID_PATTERN.find(message) ?: return null
        return match.groupValues.getOrNull(1)
            ?.trim()
            ?.trimEnd('.', ',', ';', ')', ']')
            ?.takeIf { it.isNotBlank() }
    }

    // --- Orders / alerts / funds ---
    fun placeOrder(symbol: String, quantity: Int, side: String) {
        viewModelScope.launch {
            _orderExecutionLoading.value = true
            try {
                when (val r = repository.placeOrder(Order(symbol = symbol, qty = quantity, side = side))) {
                    is Result.Success -> {
                        if (r.data.status == "error") {
                            val traceId = r.data.traceId?.trim()?.takeIf { it.isNotBlank() }
                                ?: extractTraceIdFromError(r.data.message)
                            _lastOrderTraceId.value = traceId
                            _lastExecutedOrder.value = null
                            _copilotPostTradeReview.value = null
                            _productActionMessage.value = null
                            _error.value = buildOrderErrorMessage(r.data)
                        } else {
                            _lastOrderTraceId.value = r.data.traceId?.trim()?.takeIf { it.isNotBlank() }
                            _lastExecutedOrder.value = r.data
                            // Persist trade journal for the authenticated user (best-effort).
                            launch {
                                try {
                                    val o = r.data.order
                                    com.bysel.trader.data.api.RetrofitClient.apiService.logTrade(
                                        mapOf(
                                            "symbol" to o.symbol,
                                            "side" to o.side,
                                            "qty" to o.qty,
                                            "price" to (r.data.executedPrice ?: 0.0),
                                            "orderId" to (r.data.orderId ?: 0),
                                            "userNote" to "auto: post-fill journal",
                                        )
                                    )
                                } catch (_: Exception) { }
                            }
                            _copilotPostTradeReview.value = null
                            _productActionMessage.value = r.data.message.takeIf { !it.isNullOrBlank() }
                                ?: "${side.uppercase()} order executed for ${symbol.uppercase()}"
                            _error.value = null
                            refreshHoldings(); refreshWallet(); unlockAchievement("first_trade")
                            fetchTradeCoachTip(symbol, quantity, side)
                            loadPortfolioCopilotActions()
                            r.data.orderId?.let { orderId -> fetchPostTradeCopilot(orderId) }
                        }
                    }
                    is Result.Error -> {
                        _lastOrderTraceId.value = extractTraceIdFromError(r.message)
                        _lastExecutedOrder.value = null
                        _copilotPostTradeReview.value = null
                        _productActionMessage.value = null
                        _error.value = r.message
                    }
                    else -> { }
                }
            } finally {
                _orderExecutionLoading.value = false
            }
        }
    }

    fun placeAdvancedOrder(
        symbol: String,
        quantity: Int,
        side: String,
        orderType: String = "MARKET",
        validity: String = "DAY",
        limitPrice: Double? = null,
        triggerPrice: Double? = null,
        tag: String? = null,
    ) {
        if (symbol.isBlank()) {
            _error.value = "Symbol is required"
            return
        }
        if (quantity <= 0) {
            _error.value = "Quantity must be greater than 0"
            return
        }

        val request = AdvancedOrderRequest(
            symbol = symbol.trim().uppercase(),
            qty = quantity,
            side = side.trim().uppercase(),
            orderType = orderType.trim().uppercase(),
            validity = validity.trim().uppercase(),
            limitPrice = limitPrice,
            triggerPrice = triggerPrice,
            tag = tag?.trim()?.takeIf { it.isNotBlank() },
        )

        viewModelScope.launch {
            _advancedLoading.value = true
            _copilotLoading.value = true
            when (
                val preTrade = repository.getPreTradeEstimate(
                    order = request,
                    walletBalance = _walletBalance.value,
                    marketOpen = _marketStatus.value?.isOpen,
                )
            ) {
                is Result.Success -> {
                    _preTradeEstimate.value = preTrade.data
                    _copilotPreTradeSignal.value = preTrade.data.signal
                }
                is Result.Error -> {
                    _preTradeEstimate.value = null
                    when (
                        val fallback = repository.preTradeCopilot(
                            order = request,
                            walletBalance = _walletBalance.value,
                            marketOpen = _marketStatus.value?.isOpen,
                        )
                    ) {
                        is Result.Success -> _copilotPreTradeSignal.value = fallback.data
                        is Result.Error -> _productActionMessage.value = "Copilot pre-check unavailable"
                        else -> {}
                    }
                }
                else -> {}
            }
            _copilotLoading.value = false

            when (val response = repository.placeAdvancedOrder(request)) {
                is Result.Success -> {
                    _advancedOrderResponse.value = response.data
                    _productActionMessage.value = response.data.message

                    if (!response.data.status.equals("error", ignoreCase = true)) {
                        _error.value = null
                        refreshHoldings()
                        refreshWallet()
                        refreshTriggerOrders()
                        refreshBasketOrders()
                        unlockAchievement("first_trade")
                        loadPortfolioCopilotActions()
                        response.data.orderId?.let { orderId ->
                            fetchPostTradeCopilot(orderId)
                        }
                    } else {
                        _error.value = response.data.message
                    }
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _advancedLoading.value = false
        }
    }

    fun createTriggerOrder(
        symbol: String,
        quantity: Int,
        side: String,
        triggerPrice: Double,
        orderType: String = "SL",
        validity: String = "DAY",
        limitPrice: Double? = null,
        tag: String? = null,
    ) {
        if (symbol.isBlank()) {
            _error.value = "Symbol is required"
            return
        }
        if (quantity <= 0) {
            _error.value = "Quantity must be greater than 0"
            return
        }
        if (triggerPrice <= 0.0) {
            _error.value = "Trigger price must be greater than 0"
            return
        }

        val request = AdvancedOrderRequest(
            symbol = symbol.trim().uppercase(),
            qty = quantity,
            side = side.trim().uppercase(),
            orderType = orderType.trim().uppercase(),
            validity = validity.trim().uppercase(),
            limitPrice = limitPrice,
            triggerPrice = triggerPrice,
            tag = tag?.trim()?.takeIf { it.isNotBlank() },
        )

        viewModelScope.launch {
            _advancedLoading.value = true
            when (val response = repository.createTriggerOrder(request)) {
                is Result.Success -> {
                    _productActionMessage.value = "Trigger queued: ${response.data.symbol} @ ${response.data.triggerPrice ?: triggerPrice}"
                    _error.value = null
                    refreshTriggerOrders()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _advancedLoading.value = false
        }
    }

    fun refreshTriggerOrders() {
        viewModelScope.launch {
            when (val response = repository.getTriggerOrders()) {
                is Result.Success -> _triggerOrders.value = response.data
                is Result.Error -> _error.value = response.message
                else -> {}
            }
        }
    }

    fun evaluateTriggerOrders(symbols: List<String> = emptyList()) {
        viewModelScope.launch {
            _advancedLoading.value = true
            when (val response = repository.evaluateTriggerOrders(symbols)) {
                is Result.Success -> {
                    _triggerEvaluation.value = response.data
                    _productActionMessage.value = "Trigger scan processed ${response.data.processedCount} orders"
                    lastTriggerAutoEvalAt = System.currentTimeMillis()
                    refreshTriggerOrders()
                    if (response.data.processedCount > 0) {
                        refreshHoldings()
                        refreshWallet()
                        loadPortfolioCopilotActions()
                    }
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _advancedLoading.value = false
        }
    }

    /** Quiet trigger scan on quote ticks — throttled, no Advanced spinner. */
    private fun maybeAutoEvaluateTriggers(symbols: List<String>) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerAutoEvalAt < TRIGGER_AUTO_EVAL_INTERVAL) return
        if (triggerAutoEvalJob?.isActive == true) return
        lastTriggerAutoEvalAt = now
        val scoped = symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        triggerAutoEvalJob = viewModelScope.launch {
            when (val response = repository.evaluateTriggerOrders(scoped)) {
                is Result.Success -> {
                    _triggerEvaluation.value = response.data
                    refreshTriggerOrders()
                    if (response.data.processedCount > 0) {
                        _productActionMessage.value =
                            "Auto-trigger: filled ${response.data.processedCount} order(s)"
                        refreshHoldings()
                        refreshWallet()
                        loadPortfolioCopilotActions()
                    }
                }
                else -> {}
            }
        }
    }

    fun createBasketOrder(name: String, legs: List<BasketOrderLegRequest>) {
        if (name.isBlank()) {
            _error.value = "Basket name is required"
            return
        }
        if (legs.isEmpty()) {
            _error.value = "Add at least one basket leg"
            return
        }

        viewModelScope.launch {
            _advancedLoading.value = true
            when (val response = repository.createBasketOrder(BasketOrderRequest(name = name.trim(), legs = legs))) {
                is Result.Success -> {
                    _productActionMessage.value = response.data.message
                    _error.value = null
                    refreshBasketOrders()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _advancedLoading.value = false
        }
    }

    fun refreshBasketOrders() {
        viewModelScope.launch {
            when (val response = repository.getBasketOrders()) {
                is Result.Success -> _basketOrders.value = response.data
                is Result.Error -> _error.value = response.message
                else -> {}
            }
        }
    }

    fun executeBasketOrder(basketId: Int) {
        if (basketId <= 0) {
            _error.value = "Invalid basket id"
            return
        }
        viewModelScope.launch {
            _advancedLoading.value = true
            when (val response = repository.executeBasketOrder(basketId)) {
                is Result.Success -> {
                    _productActionMessage.value = response.data.message
                    _error.value = null
                    refreshBasketOrders()
                    refreshHoldings()
                    refreshWallet()
                    loadPortfolioCopilotActions()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _advancedLoading.value = false
        }
    }

    fun loadOptionChain(symbol: String, expiry: String) {
        if (symbol.isBlank() || expiry.isBlank()) {
            _derivativesError.value = "Symbol and expiry are required"
            return
        }
        viewModelScope.launch {
            _derivativesLoading.value = true
            when (val response = repository.getOptionChain(symbol.trim().uppercase(), expiry.trim())) {
                is Result.Success -> {
                    _optionChain.value = response.data
                    _derivativesError.value = null
                }
                is Result.Error -> _derivativesError.value = response.message
                else -> {}
            }
            _derivativesLoading.value = false
        }
    }

    fun previewStrategy(symbol: String, spot: Double, legs: List<StrategyLeg>) {
        if (symbol.isBlank()) {
            _derivativesError.value = "Symbol is required"
            return
        }
        if (spot <= 0.0) {
            _derivativesError.value = "Spot must be greater than 0"
            return
        }
        if (legs.isEmpty()) {
            _derivativesError.value = "Add at least one strategy leg"
            return
        }

        viewModelScope.launch {
            _derivativesLoading.value = true
            when (val response = repository.previewStrategy(StrategyPreviewRequest(symbol = symbol.trim().uppercase(), spot = spot, legs = legs))) {
                is Result.Success -> {
                    _strategyPreview.value = response.data
                    _derivativesError.value = null
                }
                is Result.Error -> _derivativesError.value = response.message
                else -> {}
            }
            _derivativesLoading.value = false
        }
    }

    fun loadFuturesContracts(symbol: String) {
        if (symbol.isBlank()) {
            _derivativesError.value = "Underlying symbol is required"
            return
        }

        viewModelScope.launch {
            _derivativesLoading.value = true
            when (val response = repository.getFuturesContracts(symbol.trim().uppercase())) {
                is Result.Success -> {
                    _futuresContracts.value = response.data
                    _futuresTicketPreview.value = null
                    _derivativesError.value = null
                }
                is Result.Error -> _derivativesError.value = response.message
                else -> {}
            }
            _derivativesLoading.value = false
        }
    }

    fun previewFuturesTicket(
        symbol: String,
        expiry: String,
        side: String,
        lots: Int,
        orderType: String = "MARKET",
        limitPrice: Double? = null,
    ) {
        if (symbol.isBlank() || expiry.isBlank()) {
            _derivativesError.value = "Symbol and expiry are required"
            return
        }
        if (lots <= 0) {
            _derivativesError.value = "Lots must be greater than 0"
            return
        }

        val request = FuturesTicketPreviewRequest(
            symbol = symbol.trim().uppercase(),
            expiry = expiry.trim(),
            side = side.trim().uppercase(),
            lots = lots,
            orderType = orderType.trim().uppercase(),
            limitPrice = limitPrice,
        )

        viewModelScope.launch {
            _derivativesLoading.value = true
            when (val response = repository.previewFuturesTicket(request)) {
                is Result.Success -> {
                    _futuresTicketPreview.value = response.data
                    _derivativesError.value = null
                }
                is Result.Error -> _derivativesError.value = response.message
                else -> {}
            }
            _derivativesLoading.value = false
        }
    }

    fun clearFuturesTicketPreview() {
        _futuresTicketPreview.value = null
    }

    fun loadInvestorTips(topic: String, limit: Int = 2) {
        val normalized = topic.trim().lowercase().ifBlank { "long_term" }
        _investorTips.value = com.bysel.trader.ui.components.localInvestorTips(normalized, limit)
        viewModelScope.launch {
            _investorTipsLoading.value = true
            when (val response = repository.getInvestorTips(topic = normalized, limit = limit)) {
                is Result.Success -> _investorTips.value = response.data
                else -> {}
            }
            _investorTipsLoading.value = false
        }
    }

    /** One-tap paper place from Futures Radar preview (routes through Advanced order path). */
    fun placeFuturesTicketFromPreview() {
        val preview = _futuresTicketPreview.value
        if (preview == null) {
            _derivativesError.value = "Preview a futures ticket first"
            return
        }
        placeAdvancedOrder(
            symbol = preview.symbol,
            quantity = preview.quantity,
            side = preview.side,
            orderType = "MARKET",
            validity = "DAY",
            limitPrice = null,
            triggerPrice = null,
            tag = "FUT:${preview.contractSymbol}",
        )
    }

    fun addFamilyMember(
        name: String,
        relation: String,
        equityValue: Double = 0.0,
        mutualFundValue: Double = 0.0,
        usValue: Double = 0.0,
        cashValue: Double = 0.0,
        liabilitiesValue: Double = 0.0,
    ) {
        if (name.isBlank() || relation.isBlank()) {
            _error.value = "Name and relation are required"
            return
        }
        viewModelScope.launch {
            _wealthLoading.value = true
            when (
                val response = repository.addFamilyMember(
                    FamilyMemberRequest(
                        name = name.trim(),
                        relation = relation.trim(),
                        equityValue = equityValue,
                        mutualFundValue = mutualFundValue,
                        usValue = usValue,
                        cashValue = cashValue,
                        liabilitiesValue = liabilitiesValue,
                    )
                )
            ) {
                is Result.Success -> {
                    _productActionMessage.value = "Family member added: ${response.data.name}"
                    _error.value = null
                    loadFamilyDashboard()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _wealthLoading.value = false
        }
    }

    fun loadFamilyDashboard() {
        viewModelScope.launch {
            _wealthLoading.value = true
            when (val response = repository.getFamilyDashboard()) {
                is Result.Success -> {
                    _familyDashboard.value = response.data
                    _error.value = null
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _wealthLoading.value = false
        }
    }

    fun createGoalPlan(
        goalName: String,
        targetAmount: Double,
        targetDate: String,
        monthlyContribution: Double = 0.0,
        riskProfile: String = "MODERATE",
    ) {
        if (goalName.isBlank() || targetDate.isBlank()) {
            _error.value = "Goal name and target date are required"
            return
        }
        if (targetAmount <= 0.0) {
            _error.value = "Target amount must be greater than 0"
            return
        }

        viewModelScope.launch {
            _wealthLoading.value = true
            when (
                val response = repository.createGoal(
                    GoalPlanRequest(
                        goalName = goalName.trim(),
                        targetAmount = targetAmount,
                        targetDate = targetDate.trim(),
                        monthlyContribution = monthlyContribution,
                        riskProfile = riskProfile.trim().uppercase(),
                    )
                )
            ) {
                is Result.Success -> {
                    _productActionMessage.value = "Goal created: ${response.data.goalName}"
                    _error.value = null
                    loadGoalPlans()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _wealthLoading.value = false
        }
    }

    fun loadGoalPlans() {
        viewModelScope.launch {
            _wealthLoading.value = true
            when (val response = repository.getGoals()) {
                is Result.Success -> {
                    _goalPlans.value = response.data
                    _error.value = null
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _wealthLoading.value = false
        }
    }

    fun linkGoalInvestments(goalId: Int, instruments: List<String>, incrementAmount: Double = 0.0) {
        if (goalId <= 0) {
            _error.value = "Invalid goal id"
            return
        }
        val cleanedInstruments = instruments.map { it.trim().uppercase() }.filter { it.isNotBlank() }
        if (cleanedInstruments.isEmpty()) {
            _error.value = "Add at least one instrument"
            return
        }

        viewModelScope.launch {
            _wealthLoading.value = true
            when (
                val response = repository.linkGoalInvestment(
                    goalId = goalId,
                    request = GoalLinkRequest(
                        instruments = cleanedInstruments,
                        incrementAmount = incrementAmount,
                    )
                )
            ) {
                is Result.Success -> {
                    _productActionMessage.value = "Linked instruments to ${response.data.goalName}"
                    _error.value = null
                    loadGoalPlans()
                    loadPortfolioCopilotActions()
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _wealthLoading.value = false
        }
    }

    fun fetchPreTradeEstimate(order: AdvancedOrderRequest) {
        viewModelScope.launch {
            _copilotLoading.value = true
            when (
                val response = repository.getPreTradeEstimate(
                    order = order,
                    walletBalance = _walletBalance.value,
                    marketOpen = _marketStatus.value?.isOpen,
                )
            ) {
                is Result.Success -> {
                    _preTradeEstimate.value = response.data
                    _copilotPreTradeSignal.value = response.data.signal
                    _error.value = null
                }
                is Result.Error -> {
                    _preTradeEstimate.value = null
                    when (
                        val fallback = repository.preTradeCopilot(
                            order = order,
                            walletBalance = _walletBalance.value,
                            marketOpen = _marketStatus.value?.isOpen,
                        )
                    ) {
                        is Result.Success -> _copilotPreTradeSignal.value = fallback.data
                        else -> _copilotPreTradeSignal.value = null
                    }
                }
                else -> {}
            }
            _copilotLoading.value = false
        }
    }

    fun runPreTradeCopilot(order: AdvancedOrderRequest) {
        viewModelScope.launch {
            _copilotLoading.value = true
            when (
                val response = repository.preTradeCopilot(
                    order = order,
                    walletBalance = _walletBalance.value,
                    marketOpen = _marketStatus.value?.isOpen,
                )
            ) {
                is Result.Success -> {
                    _copilotPreTradeSignal.value = response.data
                    _error.value = null
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _copilotLoading.value = false
        }
    }

    fun clearPreTradeCopilotSignal() {
        _copilotPreTradeSignal.value = null
        _preTradeEstimate.value = null
    }

    fun fetchPostTradeCopilot(orderId: Int, note: String? = null) {
        if (orderId <= 0) {
            _error.value = "Invalid order id"
            return
        }
        viewModelScope.launch {
            _copilotLoading.value = true
            when (val response = repository.postTradeCopilot(orderId = orderId, note = note)) {
                is Result.Success -> {
                    _copilotPostTradeReview.value = response.data
                    _error.value = null
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _copilotLoading.value = false
        }
    }

    fun loadPortfolioCopilotActions() {
        viewModelScope.launch {
            _copilotLoading.value = true
            when (val response = repository.portfolioCopilotActions()) {
                is Result.Success -> {
                    _copilotPortfolioActions.value = response.data
                    _error.value = null
                }
                is Result.Error -> _error.value = response.message
                else -> {}
            }
            _copilotLoading.value = false
        }
    }

    fun lookupOrderByTrace(traceId: String) {
        val normalized = traceId.trim()
        if (normalized.isBlank()) {
            _error.value = "Trace ID is required"
            return
        }

        viewModelScope.launch {
            _copilotLoading.value = true
            when (val response = repository.getOrderByTrace(normalized)) {
                is Result.Success -> {
                    _orderTraceLookup.value = response.data
                    _error.value = null
                }
                is Result.Error -> {
                    _orderTraceLookup.value = null
                    _error.value = response.message
                }
                else -> {}
            }
            _copilotLoading.value = false
        }
    }

    fun clearOrderTraceLookup() {
        _orderTraceLookup.value = null
    }

    fun seedTraceLookup(traceId: String) {
        val normalized = traceId.trim()
        if (normalized.isBlank()) {
            return
        }
        _lastOrderTraceId.value = normalized
        _orderTraceLookup.value = null
    }

    fun clearAdvancedInsights() {
        _advancedOrderResponse.value = null
        _triggerEvaluation.value = null
        _strategyPreview.value = null
        _futuresTicketPreview.value = null
        _preTradeEstimate.value = null
        _copilotPreTradeSignal.value = null
        _copilotPostTradeReview.value = null
        _orderTraceLookup.value = null
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

            when (val r = repository.aiAsk(prompt, buildConversationHistory())) {
                is Result.Success -> _tradeCoachTip.value = r.data.answer
                else -> _tradeCoachTip.value = "Tip: Review your trade strategy."
            }
        }
    }

    fun addFunds(amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val previous = _walletBalance.value
            val optimistic = previous + amount
            val epoch = ++walletEpoch
            walletMutationsInFlight++
            // Instant UI sync across Home / Trade / sheet — don't wait on Render.
            _walletBalance.value = optimistic
            persistCachedWalletBalance(optimistic)
            lastWalletRefreshAt = System.currentTimeMillis()

            try {
                when (val r = repository.addFunds(amount)) {
                    is Result.Success -> {
                        if (epoch != walletEpoch) return@launch
                        val ok = r.data.status.equals("ok", ignoreCase = true)
                        if (ok) {
                            _walletBalance.value = r.data.balance
                            persistCachedWalletBalance(r.data.balance)
                            lastWalletRefreshAt = System.currentTimeMillis()
                            _error.value = null
                            _productActionMessage.value = r.data.message?.takeIf { it.isNotBlank() }
                                ?: "Practice credit added · ₹${String.format("%,.0f", r.data.balance)} available"
                        } else {
                            _walletBalance.value = previous
                            persistCachedWalletBalance(previous)
                            _error.value = r.data.message ?: "Could not add practice credit"
                        }
                    }
                    is Result.Error -> {
                        if (epoch != walletEpoch) return@launch
                        _walletBalance.value = previous
                        persistCachedWalletBalance(previous)
                        _error.value = r.message
                    }
                    else -> { }
                }
            } finally {
                walletMutationsInFlight--
            }
        }
    }

    fun createAlert(symbol: String, thresholdPrice: Double?, alertType: String = "ABOVE") {
        viewModelScope.launch {
            val normalizedSymbol = symbol.trim().uppercase()
            if (normalizedSymbol.isBlank()) {
                _error.value = "Cannot set alert — stock symbol missing"
                return@launch
            }
            val normalizedType = if (alertType.equals("BELOW", ignoreCase = true)) "BELOW" else "ABOVE"

            val price = thresholdPrice?.takeIf { it > 0.0 } ?: resolveAlertPrice(normalizedSymbol, normalizedType)
            if (price == null || price <= 0.0) {
                _error.value = "Cannot set alert for $normalizedSymbol — price unavailable"
                return@launch
            }

            val duplicate = _alerts.value.any {
                it.symbol.equals(normalizedSymbol, ignoreCase = true) &&
                    kotlin.math.abs(it.thresholdPrice - price) < 0.01 &&
                    it.alertType.equals(normalizedType, ignoreCase = true) &&
                    it.isActive
            }
            if (duplicate) {
                // Informational — never put this on shared _error (it was blocking My Watchlist).
                _error.value = null
                _productActionMessage.value =
                    "Alert already set for $normalizedSymbol at ₹${String.format("%.2f", price)}"
                return@launch
            }

            val a = Alert(symbol = normalizedSymbol, thresholdPrice = price, alertType = normalizedType)
            when (val r = repository.createAlert(a)) {
                is Result.Success -> {
                    _error.value = null
                    _productActionMessage.value = alertCreatedUserMessage(normalizedSymbol, normalizedType, price)
                }
                is Result.Error -> {
                    // Offline/local insert may still succeed inside repository — confirm via Room flow.
                    val saved = _alerts.value.any {
                        it.symbol.equals(normalizedSymbol, ignoreCase = true) &&
                            kotlin.math.abs(it.thresholdPrice - price) < 0.01 &&
                            it.alertType.equals(normalizedType, ignoreCase = true)
                    }
                    if (saved) {
                        _error.value = null
                        _productActionMessage.value =
                            alertCreatedUserMessage(normalizedSymbol, normalizedType, price)
                    } else {
                        _error.value = r.message ?: "Failed to create alert"
                        _productActionMessage.value = _error.value
                    }
                }
                else -> Unit
            }
        }
    }

    /** Alert still saves without notification permission — nudge only when banners can't fire. */
    private fun alertCreatedUserMessage(symbol: String, alertType: String, price: Double): String {
        val base = "Alert set: $symbol $alertType ₹${String.format("%.2f", price)}"
        return if (alertsManager.canDeliverNotifications()) {
            base
        } else {
            "$base — enable notifications in Settings for banners"
        }
    }

    private suspend fun resolveAlertPrice(symbol: String, alertType: String): Double? {
        val cached = _quotes.value.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }?.last
        if (cached != null && cached > 0.0) {
            return if (alertType == "BELOW") cached * 0.98 else cached * 1.02
        }
        return when (val r = repository.getQuote(symbol)) {
            is Result.Success -> {
                val last = r.data.last.takeIf { it > 0.0 } ?: return null
                if (alertType == "BELOW") last * 0.98 else last * 1.02
            }
            else -> null
        }
    }

    fun deleteAlert(alertId: Int) {
        viewModelScope.launch {
            when (val r = repository.deleteAlert(alertId)) { is Result.Error -> _error.value = r.message; else -> {} }
        }
    }

    fun clearError() {
        _marketError.value = null
        _portfolioError.value = null
        _tradeError.value = null
        _derivativesError.value = null
    }

    fun clearMarketError() { _marketError.value = null }

    fun clearPortfolioError() { _portfolioError.value = null }

    fun clearTradeError() { _tradeError.value = null }

    private fun isTransientMarketBusyMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("429") ||
            lower.contains("too many requests") ||
            lower.contains("rate limit") ||
            lower.contains("market data is busy") ||
            Regex("(?i)^HTTP\\s+\\d{3}").containsMatchIn(message)
    }

    // Converts recent ChatMessage history to ConversationTurn list for the backend NLP context
    private fun buildConversationHistory(limit: Int = 6): List<ConversationTurn> =
        _chatHistory.value.takeLast(limit).map { msg ->
            ConversationTurn(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.text.take(600)  // truncate very long AI responses
            )
        }

    private fun normalizeGreetingQuery(query: String): String =
        query.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun localSmallTalkReply(query: String): String? {
        val n = normalizeGreetingQuery(query)
        return when (n) {
            "hi", "hii", "hiii", "hello", "hey", "yo", "namaste", "namaskar",
            "hi there", "hello there", "hey there" ->
                "Hi! I am BYSEL AI. Ask me about stock prices, buy/sell signals, comparisons, or valuation."
            "thanks", "thank you", "thx", "ty" ->
                "You're welcome. Ask another stock question anytime."
            "bye", "goodbye" ->
                "Bye! Come back when you want a quick market check."
            "gm", "good morning" ->
                "Good morning! Ask for a price, signal, or valuation on any NSE stock."
            "gn", "good night" ->
                "Good night! I’ll be here for the next market session."
            "how are you", "how r u" ->
                "I’m ready. Ask about a stock, sector, or your portfolio."
            else -> null
        }
    }

    /** Theme/sector screens — must not inherit the selected quote ticker. */
    private fun isSectorThemeQuery(query: String): Boolean {
        val q = query.lowercase()
        if (Regex("""\b(defence|defense|pharma|banking|fmcg|realty|railway|infra|psu|metal|cement|auto|energy|it|telecom|insurance|nbfc|fintech|shipping|shipyard|ev)\b""").containsMatchIn(q) &&
            Regex("""\b(stocks?|sector|theme|names|companies|picks?|basket)\b""").containsMatchIn(q)
        ) {
            return true
        }
        return Regex(
            """\b(best|top|good)\s+(bank|pharma|auto|it|defence|defense|energy|fmcg|metal|infra|psu|realty|railway|cement)\b"""
        ).containsMatchIn(q) ||
            Regex("""\b(bank|pharma|auto|it|defence|defense|energy|fmcg|metal|infra|psu|realty|railway)\s+stocks?\b""")
                .containsMatchIn(q)
    }

    /**
     * Drop follow-ups that mention tickers absent from the sector answer
     * (e.g. "Should I buy INFY?" after a defence screen).
     */
    private fun filterSuggestionsForSector(
        query: String,
        answer: String,
        suggestions: List<String>,
    ): List<String> {
        val corpus = "$query\n$answer".uppercase()
        val tickerRe = Regex("""\b[A-Z]{2,15}\b""")
        val mentioned = tickerRe.findAll(corpus)
            .map { it.value }
            .filter { it !in SECTOR_SUGGESTION_STOPWORDS }
            .toSet()
        val cleaned = suggestions.map { it.trim() }.filter { it.isNotBlank() }.filter { tip ->
            val tipTickers = tickerRe.findAll(tip.uppercase()).map { it.value }.toList()
            tipTickers.isEmpty() || tipTickers.any { it in mentioned }
        }
        if (cleaned.isNotEmpty()) return cleaned.take(6)
        // Fallback: build from names actually listed in the answer.
        return mentioned
            .filter { it.length in 2..12 && it !in SECTOR_SUGGESTION_STOPWORDS }
            .take(3)
            .flatMap { listOf("Analyze $it", "Should I buy $it?") }
            .distinct()
            .take(6)
    }

    // --- AI assistant ---
    fun askAi(query: String) {
        val cleanedQuery = query.trim()
        if (cleanedQuery.isBlank()) return

        viewModelScope.launch {
            _aiLoading.value = true
            _chatHistory.value = _chatHistory.value + ChatMessage(cleanedQuery, isUser = true)

            // Instant local path for greetings / chitchat — no network, no wake banner.
            localSmallTalkReply(cleanedQuery)?.let { reply ->
                _chatHistory.value = _chatHistory.value + ChatMessage(
                    reply,
                    isUser = false,
                    source = "small-talk"
                )
                lastAiSuccessAtMs = System.currentTimeMillis()
                refreshAiColdStartFlag()
                _aiLoading.value = false
                return@launch
            }

            // Build context only for real market prompts.
            val holdingsSummary = _holdings.value.joinToString(separator = ";") { h ->
                "${h.symbol}:${h.qty}@${h.last}"
            }
            val wallet = _walletBalance.value
            val portfolio = _portfolioHealth.value
            val contextParts = mutableListOf<String>()
            if (holdingsSummary.isNotBlank()) contextParts.add("holdings=$holdingsSummary")
            contextParts.add("wallet=$wallet")
            portfolio?.let { contextParts.add("portfolioScore=${it.overallScore}") }

            // Sector/theme asks must not inherit the currently selected quote (e.g. INFY
            // while the user asked "defence stocks") — that leaks Buy/Alert CTAs.
            val sectorThemeAsk = isSectorThemeQuery(cleanedQuery)
            val symbol = _selectedQuote.value?.symbol?.takeUnless { sectorThemeAsk }
            symbol?.let { contextParts.add("symbol=$it") }
            if (!sectorThemeAsk) {
                _selectedQuote.value?.let { q ->
                    contextParts.add("price=${q.last}")
                    q.pctChange.let { contextParts.add("pctChange=${it}") }
                }
            }

            // Prefer in-memory candles only — never block the chat send on a DB/network history read.
            val recentHistory = _quoteHistory.value.takeLast(10)

            if (recentHistory.isNotEmpty()) {
                val closes = recentHistory.map { it.close }
                val avgClose = closes.average()
                val variance = closes.map { (it - avgClose) * (it - avgClose) }.average()
                val volatility = kotlin.math.sqrt(variance)
                contextParts.add("history_count=${recentHistory.size}")
                contextParts.add("history_avg=${String.format("%.2f", avgClose)}")
                contextParts.add("history_vol=${String.format("%.4f", volatility)}")
                val closesShort = recentHistory.joinToString(",") { String.format("%.2f", it.close) }
                contextParts.add("history_closes=[$closesShort]")
            }

            val prompt = PromptBuilder.buildPrompt(
                cleanedQuery,
                holdingsSummary,
                wallet,
                portfolio?.overallScore,
                if (sectorThemeAsk) null else _selectedQuote.value,
                if (sectorThemeAsk) emptyList() else recentHistory
            )

            // Prefer the server (better Indian-market routing). On-device Gemma is
            // a fallback when the network path fails — not the first reply.
            // "fast" = custom Indian Stock LLM first (with live enrich), paid LLMs only
            // as fallback; also skips a duplicate Yahoo rule-engine pre-pass.
            when (val r = repository.aiAsk(prompt, buildConversationHistory(), tier = "fast")) {
                is Result.Success -> {
                    lastAiSuccessAtMs = System.currentTimeMillis()
                    lastAiWarmAtMs = lastAiSuccessAtMs
                    refreshAiColdStartFlag()
                    _aiResponse.value = r.data
                    val replySymbol = when {
                        sectorThemeAsk -> null // keep CTAs on names from the sector answer
                        else -> r.data.symbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                            ?: symbol?.trim()?.uppercase()
                    }
                    val replyPrice = if (sectorThemeAsk) null else extractAiReferencePrice(r.data)
                    val replySuggestions = if (sectorThemeAsk) {
                        filterSuggestionsForSector(cleanedQuery, r.data.answer, r.data.suggestions)
                    } else {
                        r.data.suggestions
                    }
                    _chatHistory.value = _chatHistory.value + ChatMessage(
                        r.data.answer,
                        isUser = false,
                        suggestions = replySuggestions,
                        source = r.data.source,
                        confidence = r.data.confidence,
                        symbol = replySymbol,
                        signal = if (sectorThemeAsk) null else r.data.signal,
                        lastPrice = replyPrice,
                    )
                    // Optionally enrich the last bubble with v2 cards when a symbol is in focus.
                    if (shouldUseEnhancedAnalysis(cleanedQuery, symbol) && symbol != null) {
                        launch {
                            when (val enhanced = repository.aiAnalyzeEnhanced(symbol, cleanedQuery)) {
                                is Result.Success -> {
                                    val enhancedResponse = convertEnhancedToAiResponse(enhanced.data, cleanedQuery)
                                    _aiResponse.value = enhancedResponse
                                    val history = _chatHistory.value.toMutableList()
                                    val lastAssistantIdx = history.indexOfLast { !it.isUser }
                                    if (lastAssistantIdx >= 0) {
                                        val prev = history[lastAssistantIdx]
                                        history[lastAssistantIdx] = prev.copy(
                                            enhancedFeatures = enhanced.data.enhancedFeatures
                                                ?: prev.enhancedFeatures
                                        )
                                        _chatHistory.value = history
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                is Result.Error -> {
                    var answeredOnDevice = false
                    if (OnDeviceLlmManager.isReady()) {
                        val stockCtx = contextParts.joinToString("; ")
                        val devicePrompt = OnDeviceLlmManager.buildPrompt(cleanedQuery, stockCtx)
                        val answer = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            OnDeviceLlmManager.generateResponse(devicePrompt)
                        }
                        if (!answer.isNullOrBlank()) {
                            _chatHistory.value = _chatHistory.value + ChatMessage(
                                answer.trim(),
                                isUser = false,
                                source = "on-device"
                            )
                            answeredOnDevice = true
                            lastAiSuccessAtMs = System.currentTimeMillis()
                            refreshAiColdStartFlag()
                        }
                    }
                    if (!answeredOnDevice) {
                        _chatHistory.value = _chatHistory.value + ChatMessage(
                            r.message.ifBlank { "Sorry, I couldn't process that. Please try again." },
                            isUser = false,
                            source = "error"
                        )
                    }
                }
                else -> Unit
            }
            _aiLoading.value = false
        }
    }

    private fun shouldUseEnhancedAnalysis(query: String, symbol: String?): Boolean {
        if (symbol == null) return false
        val q = query.lowercase()
        // Focused follow-ups (news/quote/sentiment/TA) must keep their own answer
        // shape — do not overlay the full analysis card deck on every chip tap.
        val focusedFollowUp = Regex(
            """\b(news|headline|catalyst|sentiment|quote|ltp|price of|current price|what(?:'s| is) the price|technical analysis|support and resistance|practice levels)\b"""
        ).containsMatchIn(q)
        if (focusedFollowUp && !Regex("""\b(should i buy|should i sell)\b""").containsMatchIn(q)) {
            return false
        }
        return Regex("""\b(analyze|analysis of|should i buy|should i sell)\b""").containsMatchIn(q)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAiReferencePrice(response: AiAssistantResponse): Double? {
        fun asPositive(value: Any?): Double? = when (value) {
            is Number -> value.toDouble().takeIf { it >= 10.0 }
            is String -> value.replace(",", "").toDoubleOrNull()?.takeIf { it >= 10.0 }
            else -> null
        }

        // Top-level fields from /ai/ask (preferred).
        asPositive(response.currentPrice)?.let { return it }

        val data = response.data
        if (data != null) {
            asPositive(data["currentPrice"])?.let { return it }
            asPositive(data["current_price"])?.let { return it }
            asPositive(data["last"])?.let { return it }
            asPositive(data["price"])?.let { return it }
            val nested = data["quote"] as? Map<*, *>
            asPositive(nested?.get("last"))?.let { return it }
            asPositive(nested?.get("currentPrice"))?.let { return it }
        }

        // Fallback: parse "Price: 1334.8" / "Entry zone: …" from answer text.
        val answer = response.answer
        Regex("""(?i)(?:price|last)\s*[:\-]?\s*₹?\s*([\d,]{2,}(?:\.\d+)?)""")
            .find(answer)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { asPositive(it) }
            ?.let { return it }
        Regex("""(?i)entry\s*zone\s*[:\-]?\s*₹?\s*([\d,]{2,}(?:\.\d+)?)""")
            .find(answer)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { asPositive(it) }
            ?.let { return it }
        return null
    }

    private fun convertEnhancedToAiResponse(enhanced: EnhancedStockAnalysisResponse, originalQuery: String): AiAssistantResponse {
        // Extract the main answer from enhanced analysis
        val answer = enhanced.baseAnalysis.summary.ifBlank {
            "Based on my enhanced analysis of ${enhanced.symbol}, I recommend a ${enhanced.baseAnalysis.signal} position with ${enhanced.enhancedFeatures.confidenceBreakdown.confidenceLevel} confidence."
        }

        // Generate suggestions based on the analysis
        val suggestions = mutableListOf<String>()
        val signal = enhanced.baseAnalysis.signal

        when (signal.uppercase()) {
            "BUY", "STRONG_BUY" -> {
                suggestions.add("What are the key risks for ${enhanced.symbol}?")
                suggestions.add("When would be a good entry price for ${enhanced.symbol}?")
                suggestions.add("Compare ${enhanced.symbol} with similar stocks")
            }
            "SELL", "STRONG_SELL" -> {
                suggestions.add("What are the alternatives to ${enhanced.symbol}?")
                suggestions.add("Should I exit ${enhanced.symbol} completely?")
            }
            "HOLD" -> {
                suggestions.add("What catalysts could change ${enhanced.symbol}'s outlook?")
                suggestions.add("Monitor these key levels for ${enhanced.symbol}")
            }
        }

        suggestions.add("Get detailed technical analysis for ${enhanced.symbol}")
        suggestions.add("Check ${enhanced.symbol} news and sentiment")

        return AiAssistantResponse(
            type = "enhanced_analysis",
            answer = answer,
            symbol = enhanced.symbol,
            score = enhanced.baseAnalysis.score,
            signal = signal,
            suggestions = suggestions.take(5),
            enhancedFeatures = enhanced.enhancedFeatures,
            apiVersion = enhanced.apiVersion
        )
    }

    fun clearChatHistory() { _chatHistory.value = emptyList(); _aiResponse.value = null }

    fun optimizePortfolio() {
        val holdings = _holdings.value
        if (holdings.isEmpty()) {
            askAi("I have no holdings yet. Suggest a diversified portfolio for medium-term growth with entry prices and stop-loss levels.")
            return
        }
        val holdingsBlock = holdings.joinToString("\n") { h ->
            "  ${h.symbol}: ${h.qty} shares at ₹${String.format("%.2f", h.last)}"
        }
        val wallet = _walletBalance.value
        val query = buildString {
            append("Optimize my portfolio for maximum returns.\n\n")
            append("My current holdings:\n$holdingsBlock\n\n")
            append("Available cash: ₹${String.format("%.2f", wallet)}\n\n")
            append("For each holding tell me: HOLD, ADD MORE, or EXIT with reasoning.\n")
            append("Then suggest new stocks to buy with entry price, target, and stop-loss.\n")
            append("Prioritize risk-reward ratio above 2:1.\n")
            append("Include a rebalancing suggestion if any sector is overweight.")
        }
        askAi(query)
    }

    // --- Analysis / predictions ---
    fun analyzeStock(symbol: String) {
        viewModelScope.launch {
            // Build contextual analyze prompt including holdings and wallet
            val holdingsSummary = _holdings.value.joinToString(separator = ";") { h ->
                "${h.symbol}:${h.qty}@${h.last}"
            }
            val wallet = _walletBalance.value
            val portfolio = _portfolioHealth.value

            // include recent history for symbol in analysis
            val recentHistory = if (_selectedQuote.value?.symbol == symbol) _quoteHistory.value else emptyList()
            val prompt = PromptBuilder.buildPrompt("analyze_stock:symbol=$symbol,wallet=$wallet", holdingsSummary, wallet, portfolio?.overallScore, _selectedQuote.value, recentHistory)

            when (val r = repository.aiAsk(prompt, buildConversationHistory())) {
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
        }
    }

    // lightweight placeholders for types referenced earlier
    private val _stockAnalysis = MutableStateFlow<StockAnalysis?>(null)
    val stockAnalysis: StateFlow<StockAnalysis?> = _stockAnalysis.asStateFlow()

    private val _stockPrediction = MutableStateFlow<StockPredictionResponse?>(null)
    val stockPrediction: StateFlow<StockPredictionResponse?> = _stockPrediction.asStateFlow()

    fun predictStock(symbol: String) {
        viewModelScope.launch {
            when (val r = repository.aiPredict(symbol)) {
                is Result.Success -> _stockPrediction.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
        }
    }

    fun loadPortfolioHealth() {
        viewModelScope.launch {
            val showSpinner = _portfolioHealth.value == null
            if (showSpinner) _healthLoading.value = true
            when (val r = repository.getPortfolioHealth()) {
                is Result.Success -> {
                    _portfolioHealth.value = r.data
                    _portfolioError.value =
                        _portfolioError.value?.takeUnless { isTransientMarketBusyMessage(it) }
                }
                is Result.Error -> {
                    // Keep the last score. Never flash raw HTTP 429 on Portfolio.
                    if (_portfolioHealth.value == null && !isTransientMarketBusyMessage(r.message)) {
                        _portfolioError.value = r.message
                    }
                }
                else -> {}
            }
            _healthLoading.value = false
        }
    }

    fun loadMarketHeatmap(force: Boolean = false) {
        // After hours, freeze the last real snapshot so TQI doesn't drift on
        // keepalive / poll. Empty shells may still retry once to pick up persist.
        val current = _marketHeatmap.value
        val hasRealSnapshot = (current?.quotedCount ?: 0) > 0 ||
            (current?.marketBreadth?.total ?: 0) > 0
        val hasSemiconductor = current?.sectors?.any {
            it.name.equals("Semiconductor", ignoreCase = true)
        } == true
        val sessionClosed = !isNseMarketOpen() || current?.marketOpen == false
        if (!force && sessionClosed && hasRealSnapshot && hasSemiconductor) return

        val now = System.currentTimeMillis()
        if (!force && _marketHeatmap.value != null && (now - lastHeatmapRefreshAt) < HEATMAP_REFRESH_DEBOUNCE) {
            return
        }
        if (!force && heatmapJob?.isActive == true) return

        heatmapJob?.cancel()
        heatmapJob = viewModelScope.launch {
            val showSpinner = force || _marketHeatmap.value == null
            if (showSpinner) {
                _heatmapLoading.value = true
            }
            try {
                when (val r = repository.getMarketHeatmap(
                    wakeOnFailure = _marketHeatmap.value == null,
                )) {
                    is Result.Success -> {
                        val incoming = r.data
                        val existing = _marketHeatmap.value
                        val incomingEmpty =
                            incoming.quotedCount <= 0 && incoming.marketBreadth.total <= 0
                        val existingReal =
                            (existing?.quotedCount ?: 0) > 0 ||
                                (existing?.marketBreadth?.total ?: 0) > 0
                        if (!(incomingEmpty && existingReal)) {
                            _marketHeatmap.value = incoming
                        }
                        lastHeatmapRefreshAt = System.currentTimeMillis()
                    }
                    is Result.Error -> {
                        // Keep last good heatmap if we have one. Do not leak onto Portfolio/Home.
                    }
                    else -> {}
                }
            } finally {
                _heatmapLoading.value = false
            }
        }
    }

    private fun isNseMarketOpen(): Boolean {
        val ist = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val dow = ist.get(java.util.Calendar.DAY_OF_WEEK)
        if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) return false
        val timeInMin = ist.get(java.util.Calendar.HOUR_OF_DAY) * 60 + ist.get(java.util.Calendar.MINUTE)
        // From 3 Aug 2026: live window through F&O derivatives close 15:40 IST (CAS regime).
        val casGoLive = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, java.util.Calendar.AUGUST, 3, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val closeMin = if (!ist.before(casGoLive)) (15 * 60 + 40) else (15 * 60 + 30)
        return timeInMin in (9 * 60 + 15)..closeMin
    }

    fun loadSignalLabBuckets(force: Boolean = false, limitPerBucket: Int = 8) {
        val now = System.currentTimeMillis()
        if (!force && _signalLabBuckets.value.isNotEmpty() && (now - lastSignalLabRefreshAt) < SIGNAL_LAB_REFRESH_DEBOUNCE) {
            return
        }

        viewModelScope.launch {
            _signalLabBucketsLoading.value = true
            when (val r = repository.getSignalLabBuckets(
                limitPerBucket = limitPerBucket,
                forceRefresh = force,
            )) {
                is Result.Success -> {
                    _signalLabBuckets.value = r.data.buckets
                    lastSignalLabRefreshAt = System.currentTimeMillis()
                }
                is Result.Error -> { /* Signal Lab has its own empty state */ }
                else -> {}
            }
            _signalLabBucketsLoading.value = false
        }
    }

    fun loadMutualFunds(
        category: String? = null,
        query: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        limit: Int? = 500,
    ) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.getMutualFunds(
                category = category,
                query = query,
                sortBy = sortBy,
                sortOrder = sortOrder,
                limit = limit,
            )) {
                is Result.Success -> _mutualFunds.value = r.data
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun compareMutualFunds(schemeCodes: List<String>) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.compareMutualFunds(schemeCodes)) {
                is Result.Success -> {
                    _mutualFundCompare.value = r.data
                    _productActionMessage.value = "Mutual fund comparison ready"
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun clearMutualFundCompare() {
        _mutualFundCompare.value = null
    }

    fun loadMutualFundRecommendations(
        riskProfile: String,
        goal: String? = null,
        horizonYears: Int = 5,
        limit: Int = 5,
    ) {
        viewModelScope.launch {
            _productsLoading.value = true
            when (val r = repository.recommendMutualFunds(
                riskProfile = riskProfile,
                goal = goal,
                horizonYears = horizonYears,
                limit = limit,
            )) {
                is Result.Success -> {
                    _mutualFundRecommendations.value = r.data
                    _productActionMessage.value = "AI fit recommendations updated"
                }
                is Result.Error -> _error.value = r.message
                else -> {}
            }
            _productsLoading.value = false
        }
    }

    fun clearMutualFundRecommendations() {
        _mutualFundRecommendations.value = null
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
                is Result.Success -> {
                    _productActionMessage.value =
                        "Demo IPO application submitted — check My IPO Applications"
                    loadMyIpoApplications()
                }
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

    suspend fun fetchSentimentScore(symbol: String): com.bysel.trader.data.api.SentimentScoreResponse? =
        try { com.bysel.trader.data.api.RetrofitClient.apiService.getSentimentScore(symbol) } catch (_: Exception) { null }

    suspend fun fetchChartPatterns(symbol: String): com.bysel.trader.data.api.ChartPatternsResponse? =
        try { com.bysel.trader.data.api.RetrofitClient.apiService.getChartPatterns(symbol) } catch (_: Exception) { null }

    suspend fun fetchPortfolioRisk(): com.bysel.trader.data.api.PortfolioRiskResponse? {
        val held = holdings.value.filter { it.symbol.isNotBlank() && it.qty > 0 }
        // Empty symbols → backend demo basket + demoBasket=true disclaimer.
        val symbols = held.joinToString(",") { it.symbol.uppercase() }
        val totalValue = held.sumOf { it.qty * (if (it.last > 0) it.last else it.avgPrice) }
        val weights = if (held.isNotEmpty() && totalValue > 0) {
            held.joinToString(",") { h ->
                val v = h.qty * (if (h.last > 0) h.last else h.avgPrice)
                (v / totalValue).toString()
            }
        } else ""
        return try {
            com.bysel.trader.data.api.RetrofitClient.apiService.getPortfolioRisk(symbols, weights)
        } catch (e: Exception) {
            // Production used to 404 when raw yfinance history failed on Render.
            // Keep Risk Lab usable with an explicit educational shell until backend is healthy.
            android.util.Log.w("TradingViewModel", "fetchPortfolioRisk failed: ${e.message}")
            illustrativePortfolioRiskFallback(
                symbols = if (held.isNotEmpty()) held.map { it.symbol.uppercase() }
                else listOf("RELIANCE", "TCS", "INFY"),
            )
        }
    }

    private fun illustrativePortfolioRiskFallback(
        symbols: List<String>,
    ): com.bysel.trader.data.api.PortfolioRiskResponse {
        val n = symbols.size.coerceAtLeast(1)
        val equal = List(n) { 1.0 / n }
        val corr = List(n) { i ->
            List(n) { j -> if (i == j) 1.0 else 0.55 }
        }
        return com.bysel.trader.data.api.PortfolioRiskResponse(
            symbols = symbols,
            weights = equal,
            metrics = com.bysel.trader.data.api.PortfolioRiskMetrics(
                var95 = -1.8,
                var99 = -2.9,
                maxDrawdown = -12.5,
                sharpeRatio = 0.85,
                annualizedReturn = 14.2,
                annualizedVolatility = 18.0,
            ),
            var95 = -1.8,
            var99 = -2.9,
            maxDrawdown = -12.5,
            sharpeRatio = 0.85,
            annualizedReturn = 14.2,
            annualizedVolatility = 18.0,
            monteCarlo = com.bysel.trader.data.api.MonteCarloResult(
                horizonDays = 30,
                simulations = 500,
                p5 = -8.5,
                p50 = 2.1,
                p95 = 12.4,
            ),
            monteCarloP5 = -8.5,
            monteCarloMedian = 2.1,
            monteCarloP95 = 12.4,
            correlationMatrix = corr,
            riskLevel = "Medium",
            demoBasket = true,
            disclaimer = "Illustrative educational metrics — live risk service unavailable. Not your paper portfolio.",
        )
    }

    fun submitAiFeedback(query: String, answer: String, helpful: Boolean) {
        viewModelScope.launch {
            try {
                com.bysel.trader.data.api.RetrofitClient.aiApiService.submitAiFeedback(
                    com.bysel.trader.data.models.AiFeedbackRequest(
                        query = query,
                        answer = answer,
                        helpful = helpful,
                    )
                )
            } catch (_: Exception) { }
        }
    }

    suspend fun fetchEarningsCalendar(): com.bysel.trader.data.api.EarningsCalendarResponse? {
        val symbolList = watchlist.value
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .ifEmpty {
                listOf(
                    "RELIANCE", "TCS", "INFY", "HDFCBANK",
                    "ICICIBANK", "ITC", "WIPRO", "SBIN",
                )
            }
        val symbols = symbolList.joinToString(",")
        return try {
            com.bysel.trader.data.api.RetrofitClient.apiService.getEarningsCalendar(symbols)
        } catch (e: Exception) {
            // Release R8 previously stripped data.api DTOs → Gson parse failures looked like
            // "network" errors. Keep the screen usable with an educational shell.
            android.util.Log.w("TradingViewModel", "fetchEarningsCalendar failed: ${e.message}")
            illustrativeEarningsCalendarFallback(symbolList)
        }
    }

    private fun illustrativeEarningsCalendarFallback(
        symbols: List<String>,
    ): com.bysel.trader.data.api.EarningsCalendarResponse {
        val curated = mapOf(
            "RELIANCE" to ("2026-10-16" to "Energy"),
            "TCS" to ("2026-10-08" to "Technology"),
            "INFY" to ("2026-10-23" to "Technology"),
            "HDFCBANK" to ("2026-10-18" to "Financial Services"),
            "ICICIBANK" to ("2026-10-25" to "Financial Services"),
            "ITC" to ("2026-10-30" to "Consumer Defensive"),
            "WIPRO" to ("2026-10-15" to "Technology"),
            "SBIN" to ("2026-11-05" to "Financial Services"),
        )
        val items = symbols.map { sym ->
            val pair = curated[sym]
            com.bysel.trader.data.api.EarningsEntry(
                symbol = sym,
                name = sym,
                nextEarningsDate = pair?.first,
                sector = pair?.second,
                estimated = true,
            )
        }.sortedBy { it.nextEarningsDate ?: "9999" }
        return com.bysel.trader.data.api.EarningsCalendarResponse(
            items = items,
            count = items.size,
            generatedAt = java.time.LocalDate.now().toString(),
            disclaimer = "Educational earnings dates — live calendar unavailable. Not official company guidance.",
        )
    }

    suspend fun fetchJournalEntries(): List<Map<String, Any>> {
        return try {
            @Suppress("UNCHECKED_CAST")
            (com.bysel.trader.data.api.RetrofitClient.apiService.getJournalEntries()["entries"] as? List<Map<String, Any>>) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchJournalInsights(): Map<String, Any>? =
        try { com.bysel.trader.data.api.RetrofitClient.apiService.getJournalInsights() } catch (_: Exception) { null }

    /** Log a practice-ideas review into the trade journal (educational habit loop). */
    fun logPracticeReview(
        symbol: String,
        qty: Int,
        price: Double,
        userNote: String,
        setSl: Boolean,
        followedPlan: Boolean,
    ) {
        viewModelScope.launch {
            try {
                val note = buildString {
                    if (userNote.isNotBlank()) append(userNote.trim())
                    if (isNotEmpty()) append(" | ")
                    append(if (setSl) "Set SL: yes" else "Set SL: no")
                    append(if (followedPlan) " | Followed plan: yes" else " | Followed plan: no")
                    append(" | source=practice_ideas")
                }
                com.bysel.trader.data.api.RetrofitClient.apiService.logTrade(
                    mapOf(
                        "symbol" to symbol.uppercase(),
                        "side" to "BUY",
                        "qty" to qty.coerceAtLeast(1),
                        "price" to price.coerceAtLeast(0.0),
                        "userNote" to note,
                    )
                )
                _productActionMessage.value = "Practice review saved — keep the Idea → Trade → Review habit."
            } catch (e: Exception) {
                _productActionMessage.value = "Practice trade placed. Review note could not sync — try Trade Journal later."
            }
        }
    }

}

/** F&O ticket validation — must not appear on Spot My list or the app-wide snackbar. */
fun isDerivativesFormMessage(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("spot must") ||
        lower.contains("strategy leg") ||
        lower.contains("symbol and expiry") ||
        lower.contains("underlying symbol") ||
        lower.contains("lots must") ||
        lower.contains("preview a futures") ||
        lower.contains("add at least one strategy")
}

// Chat message for AI Assistant
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val enhancedFeatures: EnhancedFeatures? = null,
    val source: String = "",
    /** Optional model confidence 0–1 when returned by the backend. */
    val confidence: Double? = null,
    /** NSE symbol attached by the backend for actionable replies (Buy / Set Alert). */
    val symbol: String? = null,
    val signal: String? = null,
    /** Last/reference price from the analysis payload, used when Target is missing. */
    val lastPrice: Double? = null,
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
