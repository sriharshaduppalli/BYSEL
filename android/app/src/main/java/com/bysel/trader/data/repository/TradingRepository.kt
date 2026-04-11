package com.bysel.trader.data.repository

import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.PortfolioSummary
import com.bysel.trader.data.api.PortfolioValue
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.live.LiveMarketDataClient
import com.bysel.trader.data.api.TradeHistory
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

open class TradingRepository(private val database: BYSELDatabase) {

    private companion object {
        const val IDEMPOTENCY_WINDOW_MILLIS = 10_000L
    }

    private fun normalizeSymbol(value: String): String = value.trim().uppercase()

    private fun normalizeSide(value: String): String = value.trim().uppercase()

    private fun buildIdempotencyKey(order: Order): String {
        val windowBucket = System.currentTimeMillis() / IDEMPOTENCY_WINDOW_MILLIS
        val seed = "${normalizeSymbol(order.symbol)}|${normalizeSide(order.side)}|${order.qty}|$windowBucket"
        return "ord-${UUID.nameUUIDFromBytes(seed.toByteArray())}"
    }

    private fun buildTraceId(): String = "trc-${UUID.randomUUID()}"

        suspend fun setDemoHoldings(holdings: List<Holding>) {
            // Overwrite holdings in local DB for demo
            database.holdingDao().clearAll()
            database.holdingDao().insertHoldings(holdings)
        }
    private val apiService: BYSELApiService = RetrofitClient.apiService
    private val liveMarketDataClient = LiveMarketDataClient(apiService)

    // ==================== QUOTES ====================
    open fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
        try {
            emit(Result.Loading)
            val symbolString = symbols.joinToString(",")
            val quotes = apiService.getQuotes(symbolString)
            database.quoteDao().insertQuotes(quotes)
            emit(Result.Success(quotes))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    fun getAllQuotesFromApi(): Flow<Result<List<Quote>>> = flow {
        try {
            emit(Result.Loading)
            val quotes = apiService.getAllQuotes()
            database.quoteDao().insertQuotes(quotes)
            emit(Result.Success(quotes))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    fun streamLiveQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
        emit(Result.Loading)
        try {
            liveMarketDataClient.streamQuotes(symbols).collect { quotes ->
                if (quotes.isNotEmpty()) {
                    database.quoteDao().insertQuotes(quotes)
                    emit(Result.Success(quotes))
                }
            }
        } catch (_: Exception) {
            liveMarketDataClient.pollQuotes(symbols).collect { quotes ->
                if (quotes.isNotEmpty()) {
                    database.quoteDao().insertQuotes(quotes)
                    emit(Result.Success(quotes))
                }
            }
        }
    }

    fun getCachedQuotes(symbols: List<String>): Flow<List<Quote>> {
        return database.quoteDao().getQuotesBySymbols(symbols)
    }

    fun getAllQuotes(): Flow<List<Quote>> {
        return database.quoteDao().getAllQuotes()
    }

    fun getQuotesPage(page: Int, pageSize: Int): Flow<List<Quote>> {
        val offset = page * pageSize
        return database.quoteDao().getQuotesPaged(pageSize, offset)
    }

    suspend fun getQuote(symbol: String): Result<Quote> {
        return try {
            val quote = apiService.getQuote(symbol)
            database.quoteDao().insertQuote(quote)
            Result.Success(quote)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getQuoteHistory(symbol: String, period: String = "1mo", interval: String = "1d"): Result<List<HistoryCandle>> {
        return try {
            val history = apiService.getQuoteHistory(symbol, period, interval)
            // persist fetched history to local DB
            try {
                val entities = history.map { h ->
                    com.bysel.trader.data.models.HistoryEntity(
                        symbol = symbol,
                        timestamp = h.timestamp,
                        open = h.open,
                        high = h.high,
                        low = h.low,
                        close = h.close,
                        volume = h.volume
                    )
                }
                database.historyDao().deleteHistoryForSymbol(symbol)
                database.historyDao().insertCandles(entities)
            } catch (_: Exception) {
                // ignore persistence errors
            }
            Result.Success(history)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // Return locally cached history as a Flow
    fun getCachedHistory(symbol: String) = kotlinx.coroutines.flow.flow {
        try {
            val rows = database.historyDao().getHistoryForSymbol(symbol)
            val candles = rows.map { r -> HistoryCandle(timestamp = r.timestamp, open = r.open, high = r.high, low = r.low, close = r.close, volume = r.volume) }
            emit(candles)
        } catch (e: Exception) {
            emit(emptyList<HistoryCandle>())
        }
    }

    suspend fun saveHistory(symbol: String, candles: List<HistoryCandle>) {
        try {
            val entities = candles.map { h ->
                com.bysel.trader.data.models.HistoryEntity(
                    symbol = symbol,
                    timestamp = h.timestamp,
                    open = h.open,
                    high = h.high,
                    low = h.low,
                    close = h.close,
                    volume = h.volume
                )
            }
            database.historyDao().deleteHistoryForSymbol(symbol)
            database.historyDao().insertCandles(entities)
        } catch (_: Exception) {
            // ignore
        }
    }

    // ==================== HOLDINGS ====================
    fun getHoldings(): Flow<Result<List<Holding>>> = flow {
        try {
            emit(Result.Loading)
            val holdings = apiService.getHoldings()
            database.holdingDao().insertHoldings(holdings)
            emit(Result.Success(holdings))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    fun getCachedHoldings(): Flow<List<Holding>> {
        return database.holdingDao().getAllHoldings()
    }

    suspend fun getHolding(symbol: String): Result<Holding> {
        return try {
            val holding = apiService.getHolding(symbol)
            database.holdingDao().insertHolding(holding)
            Result.Success(holding)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== TRADING OPERATIONS ====================
    suspend fun placeOrder(order: Order): Result<OrderResponse> {
        return try {
            val normalizedOrder = order.copy(
                symbol = normalizeSymbol(order.symbol),
                side = normalizeSide(order.side),
            )
            val idempotencyKey = normalizedOrder.idempotencyKey ?: buildIdempotencyKey(normalizedOrder)
            val traceId = buildTraceId()
            val response = apiService.placeOrder(
                order = normalizedOrder.copy(idempotencyKey = idempotencyKey),
                idempotencyKey = idempotencyKey,
                traceId = traceId,
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun buyStock(symbol: String, quantity: Int): Result<OrderResponse> {
        return try {
            val order = Order(symbol = normalizeSymbol(symbol), qty = quantity, side = "BUY")
            val idempotencyKey = order.idempotencyKey ?: buildIdempotencyKey(order)
            val traceId = buildTraceId()
            val response = apiService.buyStock(
                order = order.copy(idempotencyKey = idempotencyKey),
                idempotencyKey = idempotencyKey,
                traceId = traceId,
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun sellStock(symbol: String, quantity: Int): Result<OrderResponse> {
        return try {
            val order = Order(symbol = normalizeSymbol(symbol), qty = quantity, side = "SELL")
            val idempotencyKey = order.idempotencyKey ?: buildIdempotencyKey(order)
            val traceId = buildTraceId()
            val response = apiService.sellStock(
                order = order.copy(idempotencyKey = idempotencyKey),
                idempotencyKey = idempotencyKey,
                traceId = traceId,
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    fun getTradeHistory(): Flow<Result<List<TradeHistory>>> = flow {
        try {
            emit(Result.Loading)
            val history = apiService.getTradeHistory()
            emit(Result.Success(history))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    fun getTradeHistoryForSymbol(symbol: String): Flow<Result<List<TradeHistory>>> = flow {
        try {
            emit(Result.Loading)
            val history = apiService.getTradeHistoryForSymbol(symbol)
            emit(Result.Success(history))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    // ==================== PORTFOLIO ====================
    suspend fun getPortfolioSummary(): Result<PortfolioSummary> {
        return try {
            val summary = apiService.getPortfolio()
            Result.Success(summary)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getPortfolioValue(): Result<PortfolioValue> {
        return try {
            val value = apiService.getPortfolioValue()
            Result.Success(value)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== ALERTS ====================
    suspend fun createAlert(alert: Alert): Result<Alert> {
        return try {
            val createdAlert = apiService.createAlert(alert)
            database.alertDao().insertAlert(createdAlert)
            Result.Success(createdAlert)
        } catch (e: Exception) {
            database.alertDao().insertAlert(alert)
            Result.Error(e.message ?: "Unknown error")
        }
    }

    fun getActiveAlerts(): Flow<List<Alert>> {
        return database.alertDao().getActiveAlerts()
    }

    suspend fun deleteAlert(alertId: Int): Result<Unit> {
        return try {
            apiService.deleteAlert(alertId)
            database.alertDao().deactivateAlert(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            database.alertDao().deactivateAlert(alertId)
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deactivateAlert(alertId: Int): Result<Unit> {
        return try {
            // Deactivate without deleting - useful for triggered alerts
            database.alertDao().deactivateAlert(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getAllAlerts(): Result<List<Alert>> {
        return try {
            val alerts = apiService.getAlerts()
            Result.Success(alerts)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== SEARCH ====================
    suspend fun searchStocks(query: String): Result<List<StockSearchResult>> {
        return try {
            val results = apiService.searchStocks(query)
            Result.Success(results)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
    // ==================== WALLET ====================
    suspend fun getWallet(): Result<WalletBalance> {
        return try {
            val wallet = apiService.getWallet()
            Result.Success(wallet)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun addFunds(amount: Double): Result<WalletResponse> {
        return try {
            val response = apiService.addFunds(WalletTransaction(amount = amount))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun withdrawFunds(amount: Double): Result<WalletResponse> {
        return try {
            val response = apiService.withdrawFunds(WalletTransaction(amount = amount))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== MARKET STATUS ====================
    suspend fun getMarketStatus(): Result<MarketStatus> {
        return try {
            val status = apiService.getMarketStatus()
            Result.Success(status)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getMarketNews(symbols: List<String> = emptyList(), limit: Int = 5): Result<MarketNewsResponse> {
        return try {
            val symbolQuery = symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct().joinToString(",").takeIf { it.isNotBlank() }
            val response = apiService.getMarketNews(symbols = symbolQuery, limit = limit)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getAllSymbols(): Result<List<StockSearchResult>> {
        return try {
            val symbols = apiService.getAllSymbols()
            Result.Success(symbols)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== AI STOCK ASSISTANT ====================
    suspend fun aiAsk(query: String): Result<AiAssistantResponse> {
        return try {
            val response = apiService.aiAsk(AiQuery(query = query))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun aiAnalyze(symbol: String): Result<StockAnalysis> {
        return try {
            val analysis = apiService.aiAnalyze(symbol)
            Result.Success(analysis)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun aiPredict(symbol: String): Result<StockPredictionResponse> {
        return try {
            val prediction = apiService.aiPredict(symbol)
            Result.Success(prediction)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== PORTFOLIO HEALTH ====================
    suspend fun getPortfolioHealth(): Result<PortfolioHealthScore> {
        return try {
            val health = apiService.getPortfolioHealth()
            Result.Success(health)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== PHASE 1: MF, SIP, IPO, ETF ====================
    suspend fun getMutualFunds(
        category: String? = null,
        query: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        limit: Int? = null,
    ): Result<List<MutualFund>> {
        return try {
            val funds = apiService.getMutualFunds(
                category = category,
                query = query,
                sortBy = sortBy,
                sortOrder = sortOrder,
                limit = limit,
            )
            Result.Success(funds)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun compareMutualFunds(schemeCodes: List<String>): Result<MutualFundCompareResponse> {
        val normalized = schemeCodes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.size < 2) {
            return Result.Error("Select at least 2 funds to compare")
        }
        return try {
            val response = apiService.compareMutualFunds(normalized.joinToString(","))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun recommendMutualFunds(
        riskProfile: String,
        goal: String? = null,
        horizonYears: Int = 5,
        limit: Int = 5,
    ): Result<MutualFundRecommendationResponse> {
        return try {
            val response = apiService.recommendMutualFunds(
                riskProfile = riskProfile,
                goal = goal,
                horizonYears = horizonYears,
                limit = limit,
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getMutualFundDetail(schemeCode: String): Result<MutualFund> {
        return try {
            val fund = apiService.getMutualFundDetail(schemeCode)
            Result.Success(fund)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createSipPlan(request: SipPlanRequest): Result<SipPlan> {
        return try {
            val plan = apiService.createSipPlan(request)
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSipPlans(): Result<List<SipPlan>> {
        return try {
            val plans = apiService.getSipPlans()
            Result.Success(plans)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateSipPlan(sipId: String, request: SipPlanUpdateRequest): Result<SipPlan> {
        return try {
            val plan = apiService.updateSipPlan(sipId, request)
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun pauseSipPlan(sipId: String): Result<SipPlan> {
        return try {
            val plan = apiService.pauseSipPlan(sipId)
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun resumeSipPlan(sipId: String): Result<SipPlan> {
        return try {
            val plan = apiService.resumeSipPlan(sipId)
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getIpoListings(status: String? = null): Result<List<IPOListing>> {
        return try {
            val listings = apiService.getIpoListings(status)
            Result.Success(listings)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getIpoDetail(ipoId: String): Result<IPOListing> {
        return try {
            val listing = apiService.getIpoDetail(ipoId)
            Result.Success(listing)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun applyIpo(request: IPOApplicationRequest): Result<IPOApplicationResponse> {
        return try {
            val response = apiService.applyIpo(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getMyIpoApplications(): Result<List<IPOApplication>> {
        return try {
            val applications = apiService.getMyIpoApplications()
            Result.Success(applications)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getEtfInstruments(category: String? = null, query: String? = null): Result<List<ETFInstrument>> {
        return try {
            val etfs = apiService.getEtfInstruments(category = category, query = query)
            Result.Success(etfs)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== MARKET HEATMAP ====================
    suspend fun getMarketHeatmap(): Result<MarketHeatmap> {
        return try {
            val heatmap = apiService.getMarketHeatmap()
            Result.Success(heatmap)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSectorDetail(sectorName: String): Result<HeatmapSector> {
        return try {
            val sector = apiService.getSectorDetail(sectorName)
            Result.Success(sector)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== ADVANCED ORDER ENGINE ====================
    suspend fun placeAdvancedOrder(request: AdvancedOrderRequest): Result<AdvancedOrderResponse> {
        return try {
            val response = apiService.placeAdvancedOrder(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createTriggerOrder(request: AdvancedOrderRequest): Result<TriggerOrderSummary> {
        return try {
            val response = apiService.createTriggerOrder(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getTriggerOrders(): Result<List<TriggerOrderSummary>> {
        return try {
            val response = apiService.getTriggerOrders()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun evaluateTriggerOrders(symbols: List<String> = emptyList()): Result<TriggerEvaluationResponse> {
        return try {
            val symbolQuery = symbols.joinToString(",").takeIf { it.isNotBlank() }
            val response = apiService.evaluateTriggers(symbolQuery)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createBasketOrder(request: BasketOrderRequest): Result<BasketOrderResponse> {
        return try {
            val response = apiService.createBasketOrder(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getBasketOrders(): Result<List<BasketOrderResponse>> {
        return try {
            val response = apiService.getBasketOrders()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun executeBasketOrder(basketId: Int): Result<BasketOrderResponse> {
        return try {
            val response = apiService.executeBasketOrder(basketId)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== DERIVATIVES INTELLIGENCE ====================
    suspend fun getOptionChain(symbol: String, expiry: String): Result<OptionChainResponse> {
        return try {
            val response = apiService.getOptionChain(symbol = symbol, expiry = expiry)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun previewStrategy(request: StrategyPreviewRequest): Result<StrategyPreviewResponse> {
        return try {
            val response = apiService.previewStrategy(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== WEALTH OS ====================
    suspend fun addFamilyMember(request: FamilyMemberRequest): Result<FamilyMemberSummary> {
        return try {
            val response = apiService.addFamilyMember(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getFamilyDashboard(): Result<FamilyDashboardResponse> {
        return try {
            val response = apiService.getFamilyDashboard()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createGoal(request: GoalPlanRequest): Result<GoalPlanResponse> {
        return try {
            val response = apiService.createGoal(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getGoals(): Result<List<GoalPlanResponse>> {
        return try {
            val response = apiService.getGoals()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun linkGoalInvestment(goalId: Int, request: GoalLinkRequest): Result<GoalPlanResponse> {
        return try {
            val response = apiService.linkGoalInvestment(goalId, request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== AI COPILOT ====================
    suspend fun getPreTradeEstimate(
        order: AdvancedOrderRequest,
        walletBalance: Double? = null,
        marketOpen: Boolean? = null,
    ): Result<PreTradeEstimateResponse> {
        return try {
            val response = apiService.getPreTradeEstimate(
                PreTradeEstimateRequest(
                    order = order,
                    walletBalance = walletBalance,
                    marketOpen = marketOpen,
                )
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun preTradeCopilot(
        order: AdvancedOrderRequest,
        walletBalance: Double? = null,
        marketOpen: Boolean? = null,
    ): Result<CopilotSignal> {
        return try {
            val response = apiService.getPreTradeCopilot(
                CopilotPreTradeRequest(
                    order = order,
                    walletBalance = walletBalance,
                    marketOpen = marketOpen,
                )
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun postTradeCopilot(orderId: Int, note: String? = null): Result<CopilotPostTradeResponse> {
        return try {
            val response = apiService.getPostTradeCopilot(CopilotPostTradeRequest(orderId = orderId, note = note))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun portfolioCopilotActions(): Result<CopilotPortfolioActionsResponse> {
        return try {
            val response = apiService.getPortfolioCopilotActions()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getOrderByTrace(traceId: String): Result<OrderTraceLookupResponse> {
        return try {
            val response = apiService.getOrderByTrace(traceId.trim())
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
