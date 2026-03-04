package com.bysel.trader.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bysel.trader.data.models.HistoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoryDaoTest {
    private lateinit var db: BYSELDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BYSELDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadHistory() = runBlockingTestHelper {
        val symbol = "TST"
        val rows = listOf(
            HistoryEntity(symbol = symbol, timestamp = 1L, open = 10.0, high = 12.0, low = 9.5, close = 11.0, volume = 1000),
            HistoryEntity(symbol = symbol, timestamp = 2L, open = 11.0, high = 13.0, low = 10.5, close = 12.0, volume = 1200)
        )
        historyDao.insertCandles(rows)
        val fetched = historyDao.getHistoryForSymbol(symbol)
        assertEquals(2, fetched.size)
        assertEquals(11.0, fetched[0].close, 0.001)
    }
}

// small helper to run suspending tests without adding full coroutine-test dependency here
fun runBlockingTestHelper(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
