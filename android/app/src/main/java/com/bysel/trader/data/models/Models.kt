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
    val last: Double,
    @SerializedName("pctChange")
    val pctChange: Double,
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
    val side: String // "BUY" or "SELL"
)

data class OrderResponse(
    val status: String,
    val order: Order,
    val message: String? = null
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
