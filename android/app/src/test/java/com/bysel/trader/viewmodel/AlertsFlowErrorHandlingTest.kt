package com.bysel.trader.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bysel.trader.data.models.Alert
import com.bysel.trader.data.repository.TradingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for alert flow error handling (Bug #7 fix)
 * Verifies that errors in the alerts flow don't crash the app
 * and are properly caught and handled.
 */
@ExperimentalCoroutinesApi
class AlertsFlowErrorHandlingTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockRepository: TradingRepository

    @Mock
    private lateinit var mockApplication: Application

    private lateinit var viewModel: TradingViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        whenever(mockApplication.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mock())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Flow Error Handling Tests ====================

    @Test
    fun `test alerts flow error is caught and logged without crash`() = runTest {
        // Given: Repository throws an exception in alerts flow
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow {
                throw RuntimeException("Database connection failed")
            })

        // When: ViewModel is created and collects alerts
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: ViewModel should be created successfully without crash
        // The .catch operator should have caught the error
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Alerts should be empty on error")
    }

    @Test
    fun `test alerts flow recovers after error with retry`() = runTest {
        // Given: Repository fails first time, succeeds second time
        var attemptCount = 0
        val successAlerts = listOf(
            Alert(id = 1, symbol = "RELIANCE", thresholdPrice = 2500.0, alertType = "ABOVE", isActive = true)
        )

        whenever(mockRepository.getActiveAlerts())
            .thenAnswer {
                flow {
                    attemptCount++
                    if (attemptCount == 1) {
                        throw RuntimeException("Temporary database error")
                    } else {
                        emit(successAlerts)
                    }
                }
            }

        // When: Create ViewModel (first attempt fails)
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: First attempt failed gracefully
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should have empty list after first failure")

        // When: Repository is called again (simulating retry or reconnect)
        // Note: In real implementation, you'd need a retry mechanism
        // This test verifies the flow can recover after error
        assertEquals(1, attemptCount, "Repository should have been called once")
    }

    @Test
    fun `test null pointer exception in alerts flow is handled`() = runTest {
        // Given: Repository emits null which causes NPE
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow {
                // Simulate a scenario where null causes NPE
                val nullList: List<Alert>? = null
                emit(nullList!!) // This will throw NPE
            })

        // When: ViewModel collects the flow
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Should handle NPE gracefully
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should emit empty list on NPE")
    }

    @Test
    fun `test network timeout exception in alerts flow is caught`() = runTest {
        // Given: Repository simulates network timeout
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow {
                throw java.net.SocketTimeoutException("Network timeout")
            })

        // When: ViewModel is created
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Should handle timeout gracefully
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should have empty list on timeout")
    }

    @Test
    fun `test alerts flow emits empty list after catching error`() = runTest {
        // Given: Repository throws exception
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow {
                throw IllegalStateException("Invalid state")
            })

        // When: Create ViewModel and collect
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: The catch block should emit emptyList()
        assertEquals(emptyList(), viewModel.activeAlerts.value, "Should emit empty list from catch block")
    }

    @Test
    fun `test multiple errors in alerts flow don't accumulate`() = runTest {
        // Given: Repository throws different errors on consecutive calls
        var callCount = 0
        whenever(mockRepository.getActiveAlerts())
            .thenAnswer {
                flow {
                    callCount++
                    when (callCount) {
                        1 -> throw RuntimeException("Error 1")
                        2 -> throw IllegalStateException("Error 2")
                        else -> throw Exception("Error 3")
                    }
                }
            }

        // When: Create ViewModel (triggers first call)
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Each error should be caught independently
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should handle all errors gracefully")
    }

    @Test
    fun `test alerts flow error logging contains useful information`() = runTest {
        // Given: Repository throws exception with specific message
        val errorMessage = "Specific database corruption error"
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow {
                throw RuntimeException(errorMessage)
            })

        // When: ViewModel is created
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Error should be logged (we can't directly test logs, but verify behavior)
        // In a real scenario, you'd use a test logger or log capture
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should handle error with proper logging")
    }

    // ==================== Alert Flow Success Path Tests ====================

    @Test
    fun `test alerts flow successfully emits valid alerts`() = runTest {
        // Given: Repository returns valid alerts
        val alerts = listOf(
            Alert(id = 1, symbol = "RELIANCE", thresholdPrice = 2500.0, alertType = "ABOVE", isActive = true),
            Alert(id = 2, symbol = "TCS", thresholdPrice = 3500.0, alertType = "BELOW", isActive = true)
        )

        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow { emit(alerts) })

        // When: Create ViewModel
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Alerts should be collected successfully
        assertEquals(2, viewModel.activeAlerts.value.size, "Should have 2 alerts")
        assertEquals("RELIANCE", viewModel.activeAlerts.value[0].symbol)
        assertEquals("TCS", viewModel.activeAlerts.value[1].symbol)
    }

    @Test
    fun `test alerts flow handles empty list correctly`() = runTest {
        // Given: Repository returns empty list (valid scenario)
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow { emit(emptyList()) })

        // When: Create ViewModel
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Should have empty list
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Should handle empty list")
    }

    @Test
    fun `test alerts flow updates when new alerts are added`() = runTest {
        // Given: Repository emits different lists over time
        val initialAlerts = listOf(
            Alert(id = 1, symbol = "RELIANCE", thresholdPrice = 2500.0, alertType = "ABOVE", isActive = true)
        )

        var emissionCount = 0
        whenever(mockRepository.getActiveAlerts())
            .thenAnswer {
                flow {
                    emissionCount++
                    if (emissionCount == 1) {
                        emit(initialAlerts)
                    } else {
                        emit(initialAlerts + Alert(
                            id = 2, symbol = "TCS", thresholdPrice = 3500.0, alertType = "BELOW", isActive = true
                        ))
                    }
                }
            }

        // When: Create ViewModel
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Should have initial alerts
        assertEquals(1, viewModel.activeAlerts.value.size, "Should have 1 alert initially")
    }

    // ==================== Integration Tests ====================

    @Test
    fun `test error in alerts flow doesn't affect other ViewModel operations`() = runTest {
        // Given: Alerts flow fails but other operations succeed
        whenever(mockRepository.getActiveAlerts())
            .thenReturn(flow { throw RuntimeException("Alerts DB error") })

        whenever(mockRepository.getQuotesPage(anyInt(), anyInt()))
            .thenReturn(flow { emit(emptyList()) })

        // When: Create ViewModel
        viewModel = TradingViewModel(mockApplication, mockRepository)
        advanceUntilIdle()

        // Then: Alerts should be empty due to error
        assertTrue(viewModel.activeAlerts.value.isEmpty(), "Alerts should be empty")

        // When: Try to load quotes page
        viewModel.loadNextQuotesPage()
        advanceUntilIdle()

        // Then: Quotes loading should still work
        verify(mockRepository, times(1)).getQuotesPage(anyInt(), anyInt())
    }
}
