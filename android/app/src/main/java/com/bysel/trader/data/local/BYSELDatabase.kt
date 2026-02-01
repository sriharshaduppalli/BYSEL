package com.bysel.trader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bysel.trader.data.models.*

@Database(
    entities = [Quote::class, Holding::class, Alert::class],
    version = 1,
    exportSchema = false
)
abstract class BYSELDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun holdingDao(): HoldingDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var instance: BYSELDatabase? = null

        fun getInstance(context: Context): BYSELDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BYSELDatabase::class.java,
                    "bysel_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
