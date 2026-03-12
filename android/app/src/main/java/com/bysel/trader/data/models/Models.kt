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
    val stocks: List<Map<String, Any>>? = null
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

data class LogoutRequest(
    val refreshToken: String
)

data class AuthResponse(
    val status: String,
    val user_id: Int,
    val access_token: String,
    val refresh_token: String
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
