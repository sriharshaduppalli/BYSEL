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
