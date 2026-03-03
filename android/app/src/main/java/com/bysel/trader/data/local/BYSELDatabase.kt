package com.bysel.trader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bysel.trader.data.models.*

@Database(
    entities = [Quote::class, Holding::class, Alert::class, com.bysel.trader.data.models.HistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class BYSELDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun holdingDao(): HoldingDao
    abstract fun alertDao(): AlertDao
    abstract fun historyDao(): HistoryDao

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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
            private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add nullable columns with sensible defaults (NULL)
                db.execSQL("ALTER TABLE quotes ADD COLUMN open REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN prevClose REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN high REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN low REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN volume INTEGER")
                db.execSQL("ALTER TABLE quotes ADD COLUMN avgVolume INTEGER")
                db.execSQL("ALTER TABLE quotes ADD COLUMN marketCap INTEGER")
                db.execSQL("ALTER TABLE quotes ADD COLUMN trailingPE REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN eps REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN fiftyTwoWeekHigh REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN fiftyTwoWeekLow REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN targetMeanPrice REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN bid REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN ask REAL")
                db.execSQL("ALTER TABLE quotes ADD COLUMN dividendYield REAL")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create history table for OHLCV candles
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        symbol TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        open REAL NOT NULL,
                        high REAL NOT NULL,
                        low REAL NOT NULL,
                        close REAL NOT NULL,
                        volume INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_symbol_timestamp ON history(symbol, timestamp)")
            }
        }
    }
}
