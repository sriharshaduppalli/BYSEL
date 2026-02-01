package com.bysel.trader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bysel.trader.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<Quote>)

    @Query("SELECT * FROM quotes WHERE symbol IN (:symbols)")
    fun getQuotesBySymbols(symbols: List<String>): Flow<List<Quote>>

    @Query("SELECT * FROM quotes")
    fun getAllQuotes(): Flow<List<Quote>>

    @Query("DELETE FROM quotes")
    suspend fun clearAll()
}

@Dao
interface HoldingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoldings(holdings: List<Holding>)

    @Query("SELECT * FROM holdings")
    fun getAllHoldings(): Flow<List<Holding>>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol")
    fun getHoldingBySymbol(symbol: String): Flow<Holding?>

    @Query("DELETE FROM holdings")
    suspend fun clearAll()
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert)

    @Query("SELECT * FROM alerts WHERE isActive = 1")
    fun getActiveAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts")
    fun getAllAlerts(): Flow<List<Alert>>

    @Delete
    suspend fun deleteAlert(alert: Alert)

    @Query("UPDATE alerts SET isActive = 0 WHERE id = :alertId")
    suspend fun deactivateAlert(alertId: Int)
}
