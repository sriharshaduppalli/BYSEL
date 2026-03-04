package com.bysel.trader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bysel.trader.data.models.HistoryEntity

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<HistoryEntity>)

    @Query("SELECT * FROM history WHERE symbol = :symbol ORDER BY timestamp ASC")
    suspend fun getHistoryForSymbol(symbol: String): List<HistoryEntity>

    @Query("DELETE FROM history WHERE symbol = :symbol")
    suspend fun deleteHistoryForSymbol(symbol: String)
}
