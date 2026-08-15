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

    @POST("/auth/password-reset/request")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequestBody): PasswordResetRequestResponse

    @POST("/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body request: PasswordResetConfirmRequest): PasswordResetConfirmResponse

    @POST("/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): AuthResponse

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Map<String, String>

    @POST("/auth/logout-all")
    suspend fun logoutAllDevices(): Map<String, String>

    @GET("/auth/sessions")
    suspend fun getActiveSessions(): AuthSessionsResponse

    @GET("/auth/me")
    suspend fun getCurrentUserProfile(): CurrentUserProfile

    @DELETE("/auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: Int): Map<String, String>

    @POST("/auth/send-otp")
    suspend fun sendOTP(@Body request: SendOTPRequest): OTPResponse

    @POST("/auth/verify-otp")
    suspend fun verifyOTP(@Body request: VerifyOTPRequest): AuthResponse

    @POST("/auth/firebase-phone")
    suspend fun firebasePhoneAuth(@Body request: FirebasePhoneAuthRequest): AuthResponse

    @POST("/auth/delete-account")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Map<String, String>

    @POST("/auth/register-fcm-token")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): Map<String, String>

    @POST("/auth/unregister-fcm-token")
    suspend fun unregisterFcmToken(@Body request: FcmTokenRequest): Map<String, String>

    // Production serves GET/PATCH under /auth/me (legacy /auth/profile may 404).
    @GET("/auth/me")
    suspend fun getProfile(): UserProfile

    @PATCH("/auth/me")
    suspend fun updateProfile(@Body request: UserProfileUpdateRequest): UserProfile

    @GET("/auth/profile")
    suspend fun getProfileLegacy(): UserProfile

    @PATCH("/auth/profile")
    suspend fun updateProfileLegacy(@Body request: UserProfileUpdateRequest): UserProfile

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
    suspend fun placeOrder(
        @Body order: Order,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null,
        @Header("X-Trace-Id") traceId: String? = null,
    ): OrderResponse

    @POST("/trade/buy")
    suspend fun buyStock(
        @Body order: Order,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null,
        @Header("X-Trace-Id") traceId: String? = null,
    ): OrderResponse

    @POST("/trade/sell")
    suspend fun sellStock(
        @Body order: Order,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null,
        @Header("X-Trace-Id") traceId: String? = null,
    ): OrderResponse

    @POST("/orders/pre-trade-estimate")
    suspend fun getPreTradeEstimate(@Body request: PreTradeEstimateRequest): PreTradeEstimateResponse

    @POST("/orders/advanced")
    suspend fun placeAdvancedOrder(@Body order: AdvancedOrderRequest): AdvancedOrderResponse

    @POST("/orders/triggers")
    suspend fun createTriggerOrder(@Body order: AdvancedOrderRequest): TriggerOrderSummary

    @GET("/orders/triggers")
    suspend fun getTriggerOrders(): List<TriggerOrderSummary>

    @POST("/orders/triggers/evaluate")
    suspend fun evaluateTriggers(@Query("symbols") symbols: String? = null): TriggerEvaluationResponse

    @POST("/orders/baskets")
    suspend fun createBasketOrder(@Body request: BasketOrderRequest): BasketOrderResponse

    @GET("/orders/baskets")
    suspend fun getBasketOrders(): List<BasketOrderResponse>

    @POST("/orders/baskets/{basketId}/execute")
    suspend fun executeBasketOrder(@Path("basketId") basketId: Int): BasketOrderResponse

    @GET("/trades/history")
    suspend fun getTradeHistory(): List<TradeHistory>

    @GET("/trades/history/{symbol}")
    suspend fun getTradeHistoryForSymbol(@Path("symbol") symbol: String): List<TradeHistory>

    @GET("/orders/trace/{traceId}")
    suspend fun getOrderByTrace(@Path("traceId") traceId: String): OrderTraceLookupResponse

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

    // ==================== STOCK NOTES ====================
    @GET("/stock-notes")
    suspend fun getStockNotes(): StockNotesListResponse

    @GET("/stock-notes/{symbol}")
    suspend fun getStockNote(@Path("symbol") symbol: String): StockNoteDto

    @PUT("/stock-notes")
    suspend fun upsertStockNote(@Body body: StockNoteUpsertRequest): StockNoteDto

    @DELETE("/stock-notes/{symbol}")
    suspend fun deleteStockNote(@Path("symbol") symbol: String): StockNoteDeleteResponse

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

    @GET("/warmup")
    suspend fun warmup(): Map<String, Any>

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

    @GET("/market/intraday-tips")
    suspend fun getIntradayTips(
        @Query("limit") limit: Int = 3,
        @Query("advanceShare") advanceShare: Double? = null,
    ): IntradayTipsResponse

    @GET("/market/investor-tips")
    suspend fun getInvestorTips(
        @Query("topic") topic: String = "long_term",
        @Query("limit") limit: Int = 3,
    ): InvestorTipsResponse

    @GET("/market/news")
    suspend fun getMarketNews(
        @Query("symbols") symbols: String? = null,
        @Query("limit") limit: Int = 5
    ): MarketNewsResponse

    @GET("/market/movers")
    suspend fun getMarketMovers(
        @Query("limit") limit: Int = 10
    ): MarketMoversResponse

    // ==================== AI STOCK ASSISTANT ====================
    @POST("/ai/ask")
    suspend fun aiAsk(@Body query: AiQuery): AiAssistantResponse

    @POST("/ai/feedback")
    suspend fun submitAiFeedback(@Body body: AiFeedbackRequest): Map<String, Any>

    @GET("/ai/analyze/{symbol}")
    suspend fun aiAnalyze(@Path("symbol") symbol: String): StockAnalysis

    @GET("/ai/analyze-fast/{symbol}")
    suspend fun aiAnalyzeFast(@Path("symbol") symbol: String): StockAnalysis

    @GET("/ai/predict/{symbol}")
    suspend fun aiPredict(@Path("symbol") symbol: String): StockPredictionResponse

    @GET("/ai/recommendations")
    suspend fun getStockRecommendations(@Query("limit") limit: Int = 10): StockRecommendationsResponse

    @GET("/ai/practice-ideas")
    suspend fun getPracticeIdeas(@Query("limit") limit: Int = 6): PracticeIdeasResponse

    // ==================== ENHANCED AI ANALYSIS (LEVEL 2) ====================
    @POST("/api/ai/v2/analyze-with-explanation")
    suspend fun aiAnalyzeEnhanced(
        @Query("symbol") symbol: String,
        @Query("query") query: String? = null
    ): EnhancedStockAnalysisResponse

    @POST("/api/ai/v2/analyze-query-intent")
    suspend fun aiAnalyzeQueryIntent(@Body query: Map<String, String>): Map<String, Any>

    @POST("/api/ai/v2/confidence-breakdown")
    suspend fun aiGetConfidenceBreakdown(
        @Query("symbol") symbol: String,
        @Query("prediction") prediction: Map<String, Any>? = null,
        @Query("model_accuracy") modelAccuracy: Double? = 65.0
    ): ConfidenceBreakdownResponse

    @GET("/api/ai/v2/daily-brief")
    suspend fun getAiDailyBrief(): AiDailyBriefResponse

    @POST("/api/ai/v2/parse-trade-intent")
    suspend fun parseTradeIntent(@Body body: Map<String, String>): TradeIntentResponse

    @GET("/api/ai/v2/sentiment/{symbol}")
    suspend fun getSentimentScore(@Path("symbol") symbol: String): SentimentScoreResponse

    @GET("/api/ai/v2/patterns/{symbol}")
    suspend fun getChartPatterns(
        @Path("symbol") symbol: String,
        @Query("period") period: String = "3mo"
    ): ChartPatternsResponse

    @GET("/api/ai/v2/portfolio/risk-analysis")
    suspend fun getPortfolioRisk(
        @Query("symbols") symbols: String,
        @Query("weights") weights: String = ""
    ): PortfolioRiskResponse

    @GET("/api/ai/v2/earnings-calendar")
    suspend fun getEarningsCalendar(
        @Query("symbols") symbols: String = ""
    ): EarningsCalendarResponse

    @POST("/api/ai/v2/journal/log")
    suspend fun logTrade(@Body entry: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @GET("/api/ai/v2/journal/entries")
    suspend fun getJournalEntries(
        @Query("limit") limit: Int = 50,
        @Query("symbol") symbol: String? = null
    ): Map<String, Any>

    @GET("/api/ai/v2/journal/insights")
    suspend fun getJournalInsights(): Map<String, Any>

    // ==================== PORTFOLIO HEALTH ====================
    @GET("/portfolio/health")
    suspend fun getPortfolioHealth(): PortfolioHealthScore

    // ==================== MARKET HEATMAP ====================
    @GET("/market/heatmap")
    suspend fun getMarketHeatmap(): MarketHeatmap

    @GET("/market/sector/{sectorName}")
    suspend fun getSectorDetail(@Path("sectorName") sectorName: String): HeatmapSector

    @GET("/market/signal-lab/buckets")
    suspend fun getSignalLabBuckets(
        @Query("limitPerBucket") limitPerBucket: Int = 8,
        @Query("forceRefresh") forceRefresh: Boolean = false,
    ): SignalLabBucketsResponse

    // ==================== DERIVATIVES INTELLIGENCE ====================
    @GET("/derivatives/option-chain")
    suspend fun getOptionChain(
        @Query("symbol") symbol: String,
        @Query("expiry") expiry: String
    ): OptionChainResponse

    @POST("/derivatives/strategy/preview")
    suspend fun previewStrategy(@Body request: StrategyPreviewRequest): StrategyPreviewResponse

    @GET("/derivatives/futures/contracts")
    suspend fun getFuturesContracts(@Query("symbol") symbol: String): FuturesContractsResponse

    @POST("/derivatives/futures/ticket/preview")
    suspend fun previewFuturesTicket(@Body request: FuturesTicketPreviewRequest): FuturesTicketPreviewResponse

    // ==================== WEALTH OS ====================
    @POST("/wealth/family/members")
    suspend fun addFamilyMember(@Body request: FamilyMemberRequest): FamilyMemberSummary

    @GET("/wealth/family/dashboard")
    suspend fun getFamilyDashboard(): FamilyDashboardResponse

    @POST("/wealth/goals")
    suspend fun createGoal(@Body request: GoalPlanRequest): GoalPlanResponse

    @GET("/wealth/goals")
    suspend fun getGoals(): List<GoalPlanResponse>

    @POST("/wealth/goals/{goalId}/link-investments")
    suspend fun linkGoalInvestment(
        @Path("goalId") goalId: Int,
        @Body request: GoalLinkRequest
    ): GoalPlanResponse

    // ==================== AI COPILOT ====================
    @POST("/ai/copilot/pre-trade-check")
    suspend fun getPreTradeCopilot(@Body request: CopilotPreTradeRequest): CopilotSignal

    @POST("/ai/copilot/post-trade-review")
    suspend fun getPostTradeCopilot(@Body request: CopilotPostTradeRequest): CopilotPostTradeResponse

    @GET("/ai/copilot/portfolio-actions")
    suspend fun getPortfolioCopilotActions(): CopilotPortfolioActionsResponse

    // ==================== INVESTOR PORTFOLIOS ====================
    @GET("/investor-portfolios")
    suspend fun getInvestorPortfolios(): List<InvestorPortfolio>

    @GET("/investor-portfolios/insights")
    suspend fun getInvestorPortfolioInsights(
        @Query("maxChangesPerInvestor") maxChangesPerInvestor: Int = 3,
        @Query("ideaLimit") ideaLimit: Int = 8,
    ): InvestorPortfolioInsightsResponse

    @GET("/investor-portfolios/{investorId}")
    suspend fun getInvestorPortfolio(@Path("investorId") investorId: String): InvestorPortfolio
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

data class AiDailyBriefResponse(
    val session: String,
    val timestamp: String,
    val greeting: String,
    val overallSentiment: String,
    val outlook: String,
    val topMovers: List<Map<String, Any>> = emptyList(),
    val headlineCount: Int = 0,
    val headlines: List<Map<String, Any>> = emptyList(),
)

data class TradeIntentResponse(
    val intent: String,
    val symbol: String?,
    val qty: Int?,
    val price: Double?,
    val confidence: Double,
    val raw: String,
)

data class SentimentScoreResponse(
    val symbol: String,
    val score: Int,
    val scoreBar: Double,
    val level: String,
    val strength: String,
    val headlineCount: Int,
    val summary: String,
)

data class ChartPattern(
    val pattern: String,
    val type: String,
    val confidence: Int,
    val signal: String,
    val description: String,
    val startIdx: Int,
    val endIdx: Int,
    val historicalSuccessRate: Int,
)

data class ChartPatternsResponse(
    val symbol: String,
    val period: String,
    val patterns: List<ChartPattern>,
    val patternCount: Int,
    val generatedAt: String,
)

data class PortfolioRiskMetrics(
    val var95: Double = 0.0,
    val var99: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val annualizedReturn: Double = 0.0,
    val annualizedVolatility: Double = 0.0,
)

data class MonteCarloResult(
    val horizonDays: Int = 30,
    val simulations: Int = 500,
    val p5: Double = 0.0,
    val p50: Double = 0.0,
    val p95: Double = 0.0,
)

data class PortfolioRiskResponse(
    val symbols: List<String> = emptyList(),
    val weights: List<Double> = emptyList(),
    // Nested shape (preferred). Flat fields below keep older payloads parseable.
    val metrics: PortfolioRiskMetrics? = null,
    val var95: Double? = null,
    val var99: Double? = null,
    val maxDrawdown: Double? = null,
    val sharpeRatio: Double? = null,
    val annualizedReturn: Double? = null,
    val annualizedVolatility: Double? = null,
    val monteCarlo: MonteCarloResult? = null,
    val monteCarloMedian: Double? = null,
    val monteCarloP5: Double? = null,
    val monteCarloP95: Double? = null,
    val correlationMatrix: List<List<Double>> = emptyList(),
    val riskLevel: String? = null,
    val demoBasket: Boolean = false,
    val disclaimer: String? = null,
) {
    fun resolvedMetrics(): PortfolioRiskMetrics = metrics ?: PortfolioRiskMetrics(
        var95 = var95 ?: 0.0,
        var99 = var99 ?: 0.0,
        maxDrawdown = maxDrawdown ?: 0.0,
        sharpeRatio = sharpeRatio ?: 0.0,
        annualizedReturn = annualizedReturn ?: 0.0,
        annualizedVolatility = annualizedVolatility ?: 0.0,
    )

    fun resolvedMonteCarloP5(): Double = monteCarlo?.p5 ?: monteCarloP5 ?: 0.0
    fun resolvedMonteCarloMedian(): Double = monteCarlo?.p50 ?: monteCarloMedian ?: 0.0
    fun resolvedMonteCarloP95(): Double = monteCarlo?.p95 ?: monteCarloP95 ?: 0.0
}

data class EarningsEntry(
    @com.google.gson.annotations.SerializedName("symbol")
    val symbol: String = "",
    @com.google.gson.annotations.SerializedName("name")
    val name: String? = null,
    // Backend (ai_v2) fields
    @com.google.gson.annotations.SerializedName("nextEarningsDate")
    val nextEarningsDate: String? = null,
    @com.google.gson.annotations.SerializedName("epsTrailing")
    val epsTrailing: Double? = null,
    @com.google.gson.annotations.SerializedName("epsForward")
    val epsForward: Double? = null,
    @com.google.gson.annotations.SerializedName("revenueGrowth")
    val revenueGrowth: Double? = null,
    @com.google.gson.annotations.SerializedName("pe")
    val pe: Double? = null,
    @com.google.gson.annotations.SerializedName("sector")
    val sector: String? = null,
    @com.google.gson.annotations.SerializedName("estimated")
    val estimated: Boolean = false,
    // Legacy / alternate field names
    @com.google.gson.annotations.SerializedName("earningsDate")
    val earningsDate: String? = null,
    @com.google.gson.annotations.SerializedName("epsEstimate")
    val epsEstimate: Double? = null,
    @com.google.gson.annotations.SerializedName("epsActual")
    val epsActual: Double? = null,
    @com.google.gson.annotations.SerializedName("revenueEstimate")
    val revenueEstimate: Long? = null,
    @com.google.gson.annotations.SerializedName("trailingPE")
    val trailingPE: Double? = null,
    @com.google.gson.annotations.SerializedName("forwardPE")
    val forwardPE: Double? = null,
) {
    fun displayDate(): String? {
        val raw = (nextEarningsDate ?: earningsDate)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = Regex("""datetime\.date\((\d+),\s*(\d+),\s*(\d+)\)""").find(raw)
        if (match != null) {
            val (year, month, day) = match.destructured
            return String.format("%s-%02d-%02d", year, month.toInt(), day.toInt())
        }
        return raw.take(10)
    }

    fun displayEpsEstimate(): Double? = epsForward ?: epsEstimate
    fun displayEpsActual(): Double? = epsTrailing ?: epsActual
    fun displayTrailingPe(): Double? = pe ?: trailingPE
    fun displayForwardPe(): Double? = forwardPE
}

data class EarningsCalendarResponse(
    // Backend returns `items`; older clients expected `earnings`.
    @com.google.gson.annotations.SerializedName("items")
    val items: List<EarningsEntry>? = null,
    @com.google.gson.annotations.SerializedName("earnings")
    val earnings: List<EarningsEntry>? = null,
    @com.google.gson.annotations.SerializedName("count")
    val count: Int = 0,
    @com.google.gson.annotations.SerializedName("generatedAt")
    val generatedAt: String = "",
    @com.google.gson.annotations.SerializedName("disclaimer")
    val disclaimer: String? = null,
) {
    fun resolvedEntries(): List<EarningsEntry> = items ?: earnings ?: emptyList()
}
