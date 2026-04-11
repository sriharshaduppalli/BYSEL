package com.bysel.trader.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("last")
    val last: Double = 0.0,
    @SerializedName("pctChange")
    val pctChange: Double = 0.0,
    // additional fields commonly returned by market data APIs (Yahoo/others)
    @SerializedName("open")
    val open: Double? = null,
    @SerializedName("prevClose")
    val prevClose: Double? = null,
    @SerializedName("high")
    val dayHigh: Double? = null,
    @SerializedName("low")
    val dayLow: Double? = null,
    @SerializedName("volume")
    val volume: Long? = null,
    @SerializedName("avgVolume")
    val avgVolume: Long? = null,
    @SerializedName("marketCap")
    val marketCap: Long? = null,
    @SerializedName("trailingPE")
    val trailingPE: Double? = null,
    @SerializedName("eps")
    val eps: Double? = null,
    @SerializedName("fiftyTwoWeekHigh")
    val fiftyTwoWeekHigh: Double? = null,
    @SerializedName("fiftyTwoWeekLow")
    val fiftyTwoWeekLow: Double? = null,
    @SerializedName("targetMeanPrice")
    val targetMeanPrice: Double? = null,
    @SerializedName("bid")
    val bid: Double? = null,
    @SerializedName("ask")
    val ask: Double? = null,
    @SerializedName("dividendYield")
    val dividendYield: Double? = null,
    @SerializedName("fiftyDayAverage")
    val fiftyDayAverage: Double? = null,
    @SerializedName("twoHundredDayAverage")
    val twoHundredDayAverage: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
 

@Entity(tableName = "holdings")
data class Holding(
    @PrimaryKey
    val symbol: String,
    val qty: Int,
    val avgPrice: Double,
    val last: Double,
    val pnl: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val symbol: String,
    val thresholdPrice: Double,
    val alertType: String, // "ABOVE" or "BELOW"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class Order(
    val symbol: String,
    val qty: Int,
    val side: String, // "BUY" or "SELL"
    val idempotencyKey: String? = null,
)

data class OrderResponse(
    val status: String,
    val order: Order,
    val message: String? = null,
    val orderId: Int? = null,
    val executedPrice: Double? = null,
    val total: Double? = null,
    val orderStatus: String? = null,
    val traceId: String? = null,
    val idempotencyKey: String? = null,
    val isDuplicate: Boolean = false,
    val errorCode: String? = null,
)

// ==================== WALLET & MARKET STATUS ====================

data class WalletBalance(
    val balance: Double
)

data class WalletTransaction(
    val amount: Double
)

data class WalletResponse(
    val status: String,
    val balance: Double,
    val message: String? = null
)

data class MarketStatus(
    val isOpen: Boolean,
    val message: String,
    val nextOpen: String? = null,
    val nextClose: String? = null
)

data class MarketNewsHeadline(
    val symbol: String = "",
    val title: String = "",
    val source: String = "",
    val publishedAt: String = "",
    val publishedLabel: String = "",
    val link: String = ""
)

data class MarketNewsResponse(
    val headlines: List<MarketNewsHeadline> = emptyList(),
    val symbolsConsidered: List<String> = emptyList(),
    val generatedAt: String = ""
)

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val matchType: String = ""
)

// ==================== AI & ANALYTICS MODELS ====================

data class AiQuery(
    val query: String
)

data class AiAssistantResponse(
    val type: String = "",
    val answer: String = "",
    val symbol: String? = null,
    val score: Int? = null,
    val signal: String? = null,
    val suggestions: List<String> = emptyList(),
    val data: Map<String, Any>? = null,
    val stocks: List<Map<String, Any>>? = null,
    val source: String = "rule-engine",
    // Enhanced AI features (Level 2)
    val enhancedFeatures: EnhancedFeatures? = null,
    val apiVersion: String = "v1"
)

data class PricePrediction(
    val horizon: String = "",
    val days: Int = 0,
    val predictedPrice: Double = 0.0,
    val currentPrice: Double = 0.0,
    val changePercent: Double = 0.0,
    val confidenceHigh: Double = 0.0,
    val confidenceLow: Double = 0.0,
    val direction: String = ""
)

data class StockAnalysis(
    val symbol: String = "",
    val name: String = "",
    val currentPrice: Double = 0.0,
    val sector: String = "",
    val industry: String = "",
    val score: Int = 0,
    val scoreBreakdown: Map<String, Int> = emptyMap(),
    val signal: String = "",
    val summary: String = "",
    val technical: Map<String, Any> = emptyMap(),
    val fundamental: Map<String, Any> = emptyMap(),
    val predictions: List<PricePrediction> = emptyList(),
    val modelAccuracy: Double = 0.0,
    val disclaimer: String = "",
    val lastUpdated: String = ""
)

data class StockPredictionResponse(
    val symbol: String = "",
    val currentPrice: Double = 0.0,
    val predictions: List<PricePrediction> = emptyList(),
    val signal: String = "",
    val modelAccuracy: Double = 0.0,
    val lastUpdated: String = "",
    val disclaimer: String = ""
)

data class StockRecommendation(
    val symbol: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val sector: String = "",
    val signal: String = "",
    val overallScore: Int = 0,
    val oneDayScore: Double = 0.0,
    val oneMonthScore: Double = 0.0,
    val threeMonthScore: Double = 0.0,
    val oneDayTarget: Double = 0.0,
    val oneMonthTarget: Double = 0.0,
    val threeMonthTarget: Double = 0.0,
    val rsi: Double = 0.0,
    val modelAccuracy: Double = 0.0
)

data class StockRecommendationsResponse(
    val recommendations: Map<String, List<StockRecommendation>> = emptyMap(),
    val allScored: List<StockRecommendation> = emptyList(),
    val modelAccuracy: Map<String, Double> = emptyMap(),
    val disclaimer: String = "",
    val generatedAt: String = ""
)

data class HistoryCandle(
    val timestamp: Long = 0L,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val close: Double = 0.0,
    val volume: Long = 0
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val symbol: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class PortfolioHealthScore(
    val overallScore: Int = 0,
    val grade: String = "",
    val breakdown: Map<String, Map<String, Any>> = emptyMap(),
    val sectorAllocation: Map<String, Map<String, Any>> = emptyMap(),
    val riskLevel: String = "",
    val suggestions: List<String> = emptyList(),
    val summary: String = "",
    val totalValue: Double = 0.0,
    val totalInvested: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val stockCount: Int = 0,
    val sectorCount: Int = 0,
    val lastUpdated: String = ""
)

data class HeatmapStock(
    val symbol: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val pctChange: Double = 0.0,
    val intensity: String = ""
)

data class HeatmapSector(
    val name: String = "",
    val stocks: List<HeatmapStock> = emptyList(),
    val avgChange: Double = 0.0,
    val advances: Int = 0,
    val declines: Int = 0,
    val unchanged: Int = 0,
    val totalStocks: Int = 0,
    val intensity: String = "",
    val topGainer: HeatmapStock? = null,
    val topLoser: HeatmapStock? = null
)

data class MarketBreadth(
    val advances: Int = 0,
    val declines: Int = 0,
    val unchanged: Int = 0,
    val total: Int = 0,
    val advanceRatio: Double = 0.0
)

data class MarketHeatmap(
    val sectors: List<HeatmapSector> = emptyList(),
    val marketBreadth: MarketBreadth = MarketBreadth(),
    val mood: String = "",
    val moodEmoji: String = "",
    val moodDescription: String = "",
    val bestSector: SectorSummary = SectorSummary(),
    val worstSector: SectorSummary = SectorSummary(),
    val lastUpdated: String = ""
)

data class SectorSummary(
    val name: String = "",
    val change: Double = 0.0
)

data class MutualFund(
    val schemeCode: String,
    val schemeName: String,
    val category: String,
    val nav: Double,
    val navDate: String,
    val returns1Y: Double? = null,
    val returns3Y: Double? = null,
    val returns5Y: Double? = null,
    val fundHouse: String? = null,
    val riskLevel: String? = null
)

data class MutualFundCompareResponse(
    val funds: List<MutualFund>,
    val bestReturns1YSchemeCode: String? = null,
    val bestReturns3YSchemeCode: String? = null,
    val bestReturns5YSchemeCode: String? = null,
    val lowestRiskSchemeCode: String? = null,
    val summary: String
)

data class MutualFundRecommendationItem(
    val schemeCode: String,
    val schemeName: String,
    val category: String,
    val nav: Double,
    val navDate: String,
    val fundHouse: String? = null,
    val riskLevel: String? = null,
    val suitabilityScore: Double,
    val rationale: String
)

data class MutualFundRecommendationResponse(
    val riskProfile: String,
    val goal: String? = null,
    val horizonYears: Int,
    val recommendations: List<MutualFundRecommendationItem>,
    val generatedAt: String
)

data class SipPlanRequest(
    val schemeCode: String,
    val amount: Double,
    val frequency: String = "MONTHLY",
    val dayOfMonth: Int = 5
)

data class SipPlanUpdateRequest(
    val amount: Double? = null,
    val frequency: String? = null,
    val dayOfMonth: Int? = null,
    val isActive: Boolean? = null
)

data class SipPlan(
    val id: String,
    val schemeCode: String,
    val schemeName: String,
    val amount: Double,
    val frequency: String,
    val nextInstallmentDate: String,
    val isActive: Boolean
)

data class IPOListing(
    val ipoId: String,
    val companyName: String,
    val symbol: String,
    val status: String,
    val issueOpenDate: String,
    val issueCloseDate: String,
    val listingDate: String? = null,
    val priceBandMin: Double? = null,
    val priceBandMax: Double? = null,
    val lotSize: Int? = null
)

data class IPOApplicationRequest(
    val ipoId: String,
    val lots: Int,
    val bidPrice: Double,
    val upiId: String
)

data class IPOApplicationResponse(
    val applicationId: String,
    val status: String,
    val message: String
)

data class IPOApplication(
    val applicationId: String,
    val ipoId: String,
    val companyName: String,
    val lots: Int,
    val bidPrice: Double,
    val upiId: String,
    val status: String,
    val appliedAt: String
)

data class ETFInstrument(
    val symbol: String,
    val name: String,
    val category: String,
    val last: Double,
    val pctChange: Double,
    val aumCr: Double? = null,
    val expenseRatio: Double? = null
)

data class AdvancedOrderRequest(
    val symbol: String,
    val qty: Int,
    val side: String,
    val orderType: String = "MARKET",
    val validity: String = "DAY",
    val limitPrice: Double? = null,
    val triggerPrice: Double? = null,
    val tag: String? = null
)

data class AdvancedOrderResponse(
    val status: String,
    val orderId: Int? = null,
    val order: AdvancedOrderRequest,
    val message: String,
    val executedPrice: Double? = null,
    val triggerStatus: String? = null,
    val riskFlags: List<String> = emptyList()
)

data class TriggerOrderSummary(
    val id: Int,
    val symbol: String,
    val qty: Int,
    val side: String,
    val orderType: String,
    val validity: String,
    val limitPrice: Double? = null,
    val triggerPrice: Double? = null,
    val status: String,
    val createdAt: String
)

data class TriggerEvaluationResponse(
    val status: String,
    val processedCount: Int,
    val processed: List<Map<String, Any>> = emptyList()
)

data class BasketOrderLegRequest(
    val symbol: String,
    val qty: Int,
    val side: String,
    val orderType: String = "MARKET",
    val validity: String = "DAY",
    val limitPrice: Double? = null,
    val triggerPrice: Double? = null,
    val tag: String? = null
)

data class BasketOrderRequest(
    val name: String,
    val legs: List<BasketOrderLegRequest>
)

data class BasketLegExecution(
    val symbol: String,
    val side: String,
    val qty: Int,
    val status: String,
    val message: String,
    val orderId: Int? = null
)

data class BasketOrderResponse(
    val basketId: Int,
    val name: String,
    val status: String,
    val message: String,
    val legResults: List<BasketLegExecution> = emptyList()
)

data class OptionContract(
    val strike: Double,
    val callLtp: Double,
    val putLtp: Double,
    val callOi: Int,
    val putOi: Int,
    val callOiChange: Int,
    val putOiChange: Int,
    val impliedVolatility: Double,
    val callDelta: Double,
    val putDelta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double
)

data class OptionChainResponse(
    val symbol: String,
    val expiry: String,
    val spot: Double,
    val generatedAt: String,
    val contracts: List<OptionContract>
)

data class StrategyLeg(
    val optionType: String,
    val side: String,
    val strike: Double,
    val premium: Double,
    val quantity: Int = 1,
    val lotSize: Int = 1
)

data class StrategyPreviewRequest(
    val symbol: String,
    val spot: Double,
    val legs: List<StrategyLeg>
)

data class StrategyPayoffPoint(
    val spot: Double,
    val payoff: Double
)

data class StrategyPreviewResponse(
    val symbol: String,
    val maxProfit: Double,
    val maxLoss: Double,
    val breakevenPoints: List<Double> = emptyList(),
    val marginEstimate: Double,
    val riskRewardRatio: Double,
    val payoffCurve: List<StrategyPayoffPoint> = emptyList(),
    val notes: List<String> = emptyList()
)

data class FuturesContract(
    val contractSymbol: String,
    val expiry: String,
    val lotSize: Int,
    val last: Double,
    val pctChange: Double,
    val oi: Int,
    val oiChange: Int,
    val volume: Int,
    val basis: Double,
    val marginPct: Double,
    val marginPerLot: Double,
)

data class FuturesContractsResponse(
    val symbol: String,
    val spot: Double,
    val generatedAt: String,
    val contracts: List<FuturesContract> = emptyList(),
    val notes: List<String> = emptyList(),
)

data class FuturesTicketPreviewRequest(
    val symbol: String,
    val expiry: String,
    val side: String,
    val lots: Int,
    val orderType: String = "MARKET",
    val limitPrice: Double? = null,
)

data class FuturesTicketPreviewResponse(
    val contractSymbol: String,
    val symbol: String,
    val expiry: String,
    val side: String,
    val lots: Int,
    val lotSize: Int,
    val quantity: Int,
    val referencePrice: Double,
    val notionalValue: Double,
    val estimatedMargin: Double,
    val estimatedCharges: Double,
    val maxLossBuffer: Double,
    val notes: List<String> = emptyList(),
)

data class FamilyMemberRequest(
    val name: String,
    val relation: String,
    val equityValue: Double = 0.0,
    val mutualFundValue: Double = 0.0,
    val usValue: Double = 0.0,
    val cashValue: Double = 0.0,
    val liabilitiesValue: Double = 0.0
)

data class FamilyMemberSummary(
    val id: Int,
    val name: String,
    val relation: String,
    val netWorth: Double,
    val totalAssets: Double,
    val liabilitiesValue: Double
)

data class FamilyDashboardResponse(
    val userId: Int,
    val consolidatedNetWorth: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val allocation: Map<String, Double> = emptyMap(),
    val members: List<FamilyMemberSummary> = emptyList()
)

data class GoalPlanRequest(
    val goalName: String,
    val targetAmount: Double,
    val targetDate: String,
    val monthlyContribution: Double = 0.0,
    val riskProfile: String = "MODERATE"
)

data class GoalLinkRequest(
    val instruments: List<String>,
    val incrementAmount: Double = 0.0
)

data class GoalPlanResponse(
    val id: Int,
    val goalName: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val targetDate: String,
    val monthlyContribution: Double,
    val progressPercent: Double,
    val riskProfile: String,
    val linkedInstruments: List<String> = emptyList()
)

data class CopilotSignal(
    val verdict: String,
    val confidence: Int,
    val flags: List<String> = emptyList(),
    val guidance: List<String> = emptyList()
)

data class PreTradeChargeBreakdown(
    val brokerage: Double = 0.0,
    val exchangeFee: Double = 0.0,
    val gst: Double = 0.0,
    val stampDuty: Double = 0.0,
    val totalCharges: Double = 0.0,
)

data class PreTradeEstimateRequest(
    val order: AdvancedOrderRequest,
    val walletBalance: Double? = null,
    val marketOpen: Boolean? = null,
)

data class PreTradeEstimateResponse(
    val symbol: String,
    val side: String,
    val qty: Int,
    val orderType: String,
    val executionPrice: Double,
    val livePrice: Double,
    val tradeValue: Double,
    val charges: PreTradeChargeBreakdown = PreTradeChargeBreakdown(),
    val netAmount: Double,
    val walletBalance: Double,
    val walletUtilizationPct: Double,
    val canAfford: Boolean,
    val impactTag: String,
    val warnings: List<String> = emptyList(),
    val signal: CopilotSignal,
)

data class CopilotPreTradeRequest(
    val order: AdvancedOrderRequest,
    val walletBalance: Double? = null,
    val marketOpen: Boolean? = null
)

data class CopilotPostTradeRequest(
    val orderId: Int,
    val note: String? = null
)

data class CopilotPostTradeResponse(
    val summary: String,
    val pnlNow: Double,
    val coaching: List<String> = emptyList()
)

data class CopilotPortfolioActionsResponse(
    val actions: List<String> = emptyList(),
    val priority: String,
    val rationale: String
)

data class OrderTraceLookupResponse(
    val orderId: Int,
    val traceId: String,
    val symbol: String,
    val side: String,
    val quantity: Int,
    val orderType: String,
    val validity: String,
    val status: String,
    val executedPrice: Double,
    val total: Double,
    val idempotencyKey: String? = null,
    val createdAt: String,
    val message: String,
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class PasswordResetRequestBody(
    val identifier: String
)

data class PasswordResetConfirmRequest(
    val token: String,
    val newPassword: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class LogoutRequest(
    val refreshToken: String
)

data class SendOTPRequest(
    @SerializedName("mobile_number") val mobileNumber: String
)

data class VerifyOTPRequest(
    @SerializedName("mobile_number") val mobileNumber: String,
    val otp: String
)

data class OTPResponse(
    val status: String,
    val message: String,
    @SerializedName("otp_id") val otpId: String? = null,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int? = null
)

data class FirebasePhoneAuthRequest(
    @SerializedName("firebase_id_token") val firebaseIdToken: String
)

data class AuthResponse(
    val status: String,
    val user_id: Int,
    val access_token: String,
    val refresh_token: String
)

data class PasswordResetRequestResponse(
    val status: String,
    val message: String,
    val delivery: String? = null,
    @SerializedName("reset_code") val resetCode: String? = null,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int? = null,
)

data class PasswordResetConfirmResponse(
    val status: String,
    val message: String,
)

data class AuthSessionItem(
    val session_id: Int,
    val created_at: String,
    val expires_at: String,
    val last_used_at: String? = null,
    val client_ip: String? = null,
    val device_info: String? = null
)

data class AuthSessionsResponse(
    val status: String,
    val sessions: List<AuthSessionItem>
)

// ==================== INVESTOR PORTFOLIOS (SMART MONEY TRACKER) ====================

data class InvestorHolding(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("companyName") val companyName: String = "",
    @SerializedName("holdingPct") val holdingPct: Double = 0.0,
    @SerializedName("sector") val sector: String = "",
)

data class InvestorPortfolio(
    @SerializedName("id") val id: String = "",
    @SerializedName("investorName") val investorName: String = "",
    @SerializedName("displayTitle") val displayTitle: String = "",
    @SerializedName("style") val style: String = "",
    @SerializedName("aum") val aum: String = "",
    @SerializedName("bio") val bio: String = "",
    @SerializedName("holdings") val holdings: List<InvestorHolding> = emptyList(),
)

data class InvestorHoldingDeltaFeed(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("companyName") val companyName: String = "",
    @SerializedName("action") val action: String = "",
    @SerializedName("previousHoldingPct") val previousHoldingPct: Double = 0.0,
    @SerializedName("currentHoldingPct") val currentHoldingPct: Double = 0.0,
    @SerializedName("deltaPct") val deltaPct: Double = 0.0,
    @SerializedName("commentary") val commentary: String = "",
)

data class InvestorPortfolioChangeFeed(
    @SerializedName("investorId") val investorId: String = "",
    @SerializedName("investorName") val investorName: String = "",
    @SerializedName("style") val style: String = "",
    @SerializedName("quarterLabel") val quarterLabel: String = "",
    @SerializedName("changes") val changes: List<InvestorHoldingDeltaFeed> = emptyList(),
)

data class SmartMoneyIdeaFeedCard(
    @SerializedName("ideaId") val ideaId: String = "",
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("companyName") val companyName: String = "",
    @SerializedName("action") val action: String = "",
    @SerializedName("confidence") val confidence: Int = 0,
    @SerializedName("thesis") val thesis: String = "",
    @SerializedName("whyNow") val whyNow: String = "",
    @SerializedName("riskNote") val riskNote: String = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("backingInvestors") val backingInvestors: List<String> = emptyList(),
)

data class InvestorPortfolioInsightsResponse(
    @SerializedName("generatedAt") val generatedAt: String = "",
    @SerializedName("quarterLabel") val quarterLabel: String = "",
    @SerializedName("portfolioChanges") val portfolioChanges: List<InvestorPortfolioChangeFeed> = emptyList(),
    @SerializedName("ideas") val ideas: List<SmartMoneyIdeaFeedCard> = emptyList(),
)


// ==================== SIGNAL LAB PHASE-2 FEED ====================

data class SignalLabCandidateFeed(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("companyName") val companyName: String = "",
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("confidence") val confidence: Int = 0,
    @SerializedName("thesis") val thesis: String = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("pctChange") val pctChange: Double = 0.0,
    @SerializedName("volumeRatio") val volumeRatio: Double? = null,
)

data class SignalLabBucketFeed(
    @SerializedName("bucketId") val bucketId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("thesis") val thesis: String = "",
    @SerializedName("proxy") val proxy: Boolean = false,
    @SerializedName("generatedAt") val generatedAt: String = "",
    @SerializedName("candidates") val candidates: List<SignalLabCandidateFeed> = emptyList(),
    @SerializedName("notes") val notes: List<String> = emptyList(),
)

data class SignalLabBucketsResponse(
    @SerializedName("generatedAt") val generatedAt: String = "",
    @SerializedName("buckets") val buckets: List<SignalLabBucketFeed> = emptyList(),
)

// ==================== ENHANCED AI ANALYSIS MODELS (LEVEL 2) ====================

data class EnhancedStockAnalysisResponse(
    val symbol: String,
    val apiVersion: String = "v2",
    val timestamp: String,
    val baseAnalysis: StockAnalysis,
    val enhancedFeatures: EnhancedFeatures
)

data class EnhancedFeatures(
    val confidenceBreakdown: ConfidenceBreakdown,
    val predictionReasoning: PredictionReasoning,
    val eventRiskAnalysis: EventRiskAnalysis?,
    val sentimentAnalysis: SentimentAnalysis,
    val queryUnderstanding: QueryUnderstanding
)

data class ConfidenceBreakdown(
    val overallConfidence: Double,
    val confidenceLevel: String,
    val factors: Map<String, ConfidenceFactor>
)

data class ConfidenceFactor(
    val score: Double,
    val weight: Double,
    val reasoning: String
)

data class PredictionReasoning(
    val signal: String,
    val whyConfident: String,
    val caveats: List<String>
)

data class EventRiskAnalysis(
    val baseConfidence: Double,
    val adjustedConfidence: Double,
    val adjustmentFactor: Double,
    val eventRisks: List<String>
)

data class SentimentAnalysis(
    val overallSentiment: String,
    val score: Double,  // -1 to +1
    val strength: String,
    val breakdown: SentimentBreakdown,
    val interpretation: String
)

data class SentimentBreakdown(
    val positiveCount: Int,
    val neutralCount: Int,
    val negativeCount: Int
)

data class QueryUnderstanding(
    val intents: List<String>,
    val timeframe: TimeframeAnalysis,
    val confidence: Double
)

data class TimeframeAnalysis(
    val phrase: String,
    val days: Int?
)

// API Request/Response models for enhanced endpoints
data class EnhancedAnalysisRequest(
    val symbol: String,
    val query: String? = null
)

data class ConfidenceBreakdownResponse(
    val symbol: String,
    val confidenceBreakdown: ConfidenceBreakdown
)

data class EventRiskAnalysisResponse(
    val symbol: String,
    val eventRiskAnalysis: EventRiskAnalysis
)

data class SentimentAnalysisResponse(
    val symbol: String,
    val sentimentAnalysis: SentimentAnalysis
)
