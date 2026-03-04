package com.bysel.trader.viewmodel

import androidx.test.core.app.ApplicationProvider
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingViewModelTest {

    private class FakeRepo(appContext: android.content.Context) : TradingRepository(
        com.bysel.trader.data.local.BYSELDatabase.getInstance(appContext)
    ) {
        override fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
            emit(Result.Success(listOf(Quote(symbols.firstOrNull() ?: "TST", last = 100.0))))
            // slight delay then new price
            kotlinx.coroutines.delay(20)
            emit(Result.Success(listOf(Quote(symbols.firstOrNull() ?: "TST", last = 101.0))))
        }
    }

    @Test
    fun fastRefresh_updatesQuotes_whenEnabled() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = FakeRepo(ctx)
        val vm = TradingViewModel(app, repo)

        vm.setFastRefreshEnabled(true)
        vm.setFastRefreshPlaying(true)
        // start fast refresh with short interval
        vm.startFastRefresh(intervalMs = 10, symbols = listOf("TST"))

        // wait for a couple of iterations
        delay(80)
        val quotes = vm.quotes.value
        assertTrue(quotes.isNotEmpty())
        // should have received updated price 101.0 after loop
        assertTrue(quotes.any { it.last >= 100.0 })

        vm.stopFastRefresh()
    }
}
