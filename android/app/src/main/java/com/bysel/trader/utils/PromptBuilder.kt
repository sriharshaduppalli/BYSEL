package com.bysel.trader.utils

import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.Quote

object PromptBuilder {
    fun buildPrompt(
        userQuery: String,
        holdingsSummary: String,
        wallet: Double,
        portfolioScore: Int?,
        selectedQuote: Quote?,
        recentHistory: List<HistoryCandle>
    ): String {
        val contextParts = mutableListOf<String>()
        if (holdingsSummary.isNotBlank()) contextParts.add("holdings=$holdingsSummary")
        contextParts.add("wallet=$wallet")
        portfolioScore?.let { contextParts.add("portfolioScore=$it") }

        selectedQuote?.let { q ->
            contextParts.add("symbol=${q.symbol}")
            contextParts.add("price=${q.last}")
            contextParts.add("pctChange=${q.pctChange}")
            q.trailingPE?.let { contextParts.add("pe=${it}") }
            q.volume?.let { contextParts.add("volume=${it}") }
            q.marketCap?.let { contextParts.add("marketCap=${it}") }
        }

        if (recentHistory.isNotEmpty()) {
            val lastN = recentHistory.takeLast(10)
            val closes = lastN.map { it.close }
            val avgClose = closes.average()
            val variance = closes.map { (it - avgClose) * (it - avgClose) }.average()
            val volatility = kotlin.math.sqrt(variance)
            contextParts.add("history_count=${lastN.size}")
            contextParts.add("history_avg=${String.format("%.2f", avgClose)}")
            contextParts.add("history_vol=${String.format("%.4f", volatility)}")
            val closesShort = lastN.joinToString(",") { String.format("%.2f", it.close) }
            contextParts.add("history_closes=[$closesShort]")
        }

        return buildString {
            append("user_query:${userQuery}")
            if (contextParts.isNotEmpty()) {
                append(" | context:")
                append(contextParts.joinToString(","))
            }
        }
    }
}
