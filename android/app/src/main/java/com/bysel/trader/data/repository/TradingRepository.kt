package com.bysel.trader.data.repository

import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.PortfolioSummary
import com.bysel.trader.data.api.PortfolioValue
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.api.TradeHistory
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TradingRepository(private val database: BYSELDatabase) {
    private val apiService: BYSELApiService = RetrofitClient.apiService

    // ==================== QUOTES ====================
    fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
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

    fun getCachedQuotes(symbols: List<String>): Flow<List<Quote>> {
        return database.quoteDao().getQuotesBySymbols(symbols)
    }

    fun getAllQuotes(): Flow<List<Quote>> {
        return database.quoteDao().getAllQuotes()
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
            val response = apiService.placeOrder(order)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun buyStock(symbol: String, quantity: Int): Result<OrderResponse> {
        return try {
            val order = Order(symbol = symbol, qty = quantity, side = "BUY")
            val response = apiService.buyStock(order)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun sellStock(symbol: String, quantity: Int): Result<OrderResponse> {
        return try {
            val order = Order(symbol = symbol, qty = quantity, side = "SELL")
            val response = apiService.sellStock(order)
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
}

sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
