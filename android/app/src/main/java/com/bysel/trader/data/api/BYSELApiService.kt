package com.bysel.trader.data.api

import com.bysel.trader.data.models.*
import retrofit2.http.*

interface BYSELApiService {
    // ==================== AUTH ====================
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Map<String, String>

    @POST("/auth/logout-all")
    suspend fun logoutAllDevices(): Map<String, String>

    @GET("/auth/sessions")
    suspend fun getActiveSessions(): AuthSessionsResponse

    @DELETE("/auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: Int): Map<String, String>

    // ==================== QUOTES ====================
    @GET("/quotes")
    suspend fun getQuotes(@Query("symbols") symbols: String): List<Quote>

    @GET("/quotes/{symbol}")
    suspend fun getQuote(@Path("symbol") symbol: String): Quote

    @GET("/quotes/{symbol}/history")
    suspend fun getQuoteHistory(
        @Path("symbol") symbol: String,
        @Query("period") period: String = "1mo",
        @Query("interval") interval: String = "1d"
    ): List<HistoryCandle>

    @GET("/quotes/all")
    suspend fun getAllQuotes(): List<Quote>

    // ==================== SEARCH ====================
    @GET("/search")
    suspend fun searchStocks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50
    ): List<StockSearchResult>

    @GET("/symbols")
    suspend fun getAllSymbols(): List<StockSearchResult>

    @GET("/symbols/count")
    suspend fun getSymbolsCount(): Map<String, Any>

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

    // ==================== MUTUAL FUNDS & SIP ====================
    @GET("/mutual-funds")
    suspend fun getMutualFunds(
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("limit") limit: Int? = null
    ): List<MutualFund>

    @GET("/mutual-funds/compare")
    suspend fun compareMutualFunds(@Query("schemeCodes") schemeCodes: String): MutualFundCompareResponse

    @GET("/mutual-funds/recommend")
    suspend fun recommendMutualFunds(
        @Query("riskProfile") riskProfile: String,
        @Query("goal") goal: String? = null,
        @Query("horizonYears") horizonYears: Int = 5,
        @Query("limit") limit: Int = 5
    ): MutualFundRecommendationResponse

    @GET("/mutual-funds/{schemeCode}")
    suspend fun getMutualFundDetail(@Path("schemeCode") schemeCode: String): MutualFund

    @POST("/sip/plans")
    suspend fun createSipPlan(@Body request: SipPlanRequest): SipPlan

    @GET("/sip/plans")
    suspend fun getSipPlans(): List<SipPlan>

    @PUT("/sip/plans/{sipId}")
    suspend fun updateSipPlan(
        @Path("sipId") sipId: String,
        @Body request: SipPlanUpdateRequest
    ): SipPlan

    @POST("/sip/plans/{sipId}/pause")
    suspend fun pauseSipPlan(@Path("sipId") sipId: String): SipPlan

    @POST("/sip/plans/{sipId}/resume")
    suspend fun resumeSipPlan(@Path("sipId") sipId: String): SipPlan

    // ==================== IPO ====================
    @GET("/ipos")
    suspend fun getIpoListings(@Query("status") status: String? = null): List<IPOListing>

    @GET("/ipos/{ipoId}")
    suspend fun getIpoDetail(@Path("ipoId") ipoId: String): IPOListing

    @POST("/ipos/apply")
    suspend fun applyIpo(@Body request: IPOApplicationRequest): IPOApplicationResponse

    @GET("/ipos/my-applications")
    suspend fun getMyIpoApplications(): List<IPOApplication>

    // ==================== ETF ====================
    @GET("/etfs")
    suspend fun getEtfInstruments(
        @Query("category") category: String? = null,
        @Query("q") query: String? = null
    ): List<ETFInstrument>

    // ==================== HEALTH ====================
    @GET("/health")
    suspend fun healthCheck(): Map<String, String>

    // ==================== WALLET ====================
    @GET("/wallet")
    suspend fun getWallet(): WalletBalance

    @POST("/wallet/add")
    suspend fun addFunds(@Body txn: WalletTransaction): WalletResponse

    @POST("/wallet/withdraw")
    suspend fun withdrawFunds(@Body txn: WalletTransaction): WalletResponse

    // ==================== MARKET STATUS ====================
    @GET("/market/status")
    suspend fun getMarketStatus(): MarketStatus

    // ==================== AI STOCK ASSISTANT ====================
    @POST("/ai/ask")
    suspend fun aiAsk(@Body query: AiQuery): AiAssistantResponse

    @GET("/ai/analyze/{symbol}")
    suspend fun aiAnalyze(@Path("symbol") symbol: String): StockAnalysis

    @GET("/ai/predict/{symbol}")
    suspend fun aiPredict(@Path("symbol") symbol: String): StockPredictionResponse

    // ==================== PORTFOLIO HEALTH ====================
    @GET("/portfolio/health")
    suspend fun getPortfolioHealth(): PortfolioHealthScore

    // ==================== MARKET HEATMAP ====================
    @GET("/market/heatmap")
    suspend fun getMarketHeatmap(): MarketHeatmap

    @GET("/market/sector/{sectorName}")
    suspend fun getSectorDetail(@Path("sectorName") sectorName: String): HeatmapSector
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
