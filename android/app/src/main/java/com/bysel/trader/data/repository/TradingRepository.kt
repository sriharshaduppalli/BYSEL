package com.bysel.trader.data.repository

import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TradingRepository(private val database: BYSELDatabase) {
    private val apiService: BYSELApiService = RetrofitClient.apiService

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

    fun getCachedQuotes(symbols: List<String>): Flow<List<Quote>> {
        return database.quoteDao().getQuotesBySymbols(symbols)
    }

    fun getAllQuotes(): Flow<List<Quote>> {
        return database.quoteDao().getAllQuotes()
    }

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

    suspend fun placeOrder(order: Order): Result<OrderResponse> {
        return try {
            val response = apiService.placeOrder(order)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createAlert(alert: Alert): Result<Unit> {
        return try {
            database.alertDao().insertAlert(alert)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    fun getActiveAlerts(): Flow<List<Alert>> {
        return database.alertDao().getActiveAlerts()
    }

    suspend fun deleteAlert(alertId: Int): Result<Unit> {
        return try {
            database.alertDao().deactivateAlert(alertId)
            Result.Success(Unit)
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
