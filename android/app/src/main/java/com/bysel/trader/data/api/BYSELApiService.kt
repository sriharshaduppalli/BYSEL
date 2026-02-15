package com.bysel.trader.data.api

import com.bysel.trader.data.models.*
import retrofit2.http.*

interface BYSELApiService {
    // ==================== QUOTES ====================
    @GET("/quotes")
    suspend fun getQuotes(@Query("symbols") symbols: String): List<Quote>

    @GET("/quotes/{symbol}")
    suspend fun getQuote(@Path("symbol") symbol: String): Quote

    @GET("/quotes/all")
    suspend fun getAllQuotes(): List<Quote>

    // ==================== HOLDINGS ====================
    @GET("/holdings")
    suspend fun getHoldings(): List<Holding>

    @GET("/holdings/{symbol}")
    suspend fun getHolding(@Path("symbol") symbol: String): Holding

    // ==================== TRADING OPERATIONS ====================
    @POST("/order")
    suspend fun placeOrder(@Body order: Order): OrderResponse

    @POST("/trade/buy")
    suspend fun buyStock(@Body order: Order): OrderResponse

    @POST("/trade/sell")
    suspend fun sellStock(@Body order: Order): OrderResponse

    @GET("/trades/history")
    suspend fun getTradeHistory(): List<TradeHistory>

    @GET("/trades/history/{symbol}")
    suspend fun getTradeHistoryForSymbol(@Path("symbol") symbol: String): List<TradeHistory>

    // ==================== PORTFOLIO ====================
    @GET("/portfolio")
    suspend fun getPortfolio(): PortfolioSummary

    @GET("/portfolio/value")
    suspend fun getPortfolioValue(): PortfolioValue

    // ==================== ALERTS ====================
    @GET("/alerts")
    suspend fun getAlerts(): List<Alert>

    @GET("/alerts/active")
    suspend fun getActiveAlerts(): List<Alert>

    @POST("/alerts")
    suspend fun createAlert(@Body alert: Alert): Alert

    @PUT("/alerts/{id}")
    suspend fun updateAlert(@Path("id") id: Int, @Body alert: Alert): Alert

    @DELETE("/alert/{id}")
    suspend fun deleteAlert(@Path("id") alertId: Int): AlertResponse

    // ==================== HEALTH ====================
    @GET("/health")
    suspend fun healthCheck(): Map<String, String>
}

// Trading and Portfolio data classes
data class TradeHistory(
    val id: Int,
    val symbol: String,
    val side: String, // BUY or SELL
    val quantity: Int,
    val price: Double,
    val total: Double,
    val timestamp: Long
)

data class PortfolioSummary(
    val totalValue: Double,
    val totalInvested: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,
    val holdingsCount: Int
)

data class PortfolioValue(
    val value: Double,
    val invested: Double,
    val pnl: Double,
    val pnlPercent: Double
)

data class AlertResponse(
    val status: String,
    val message: String,
    val id: Int? = null
)
