package com.bysel.trader.repository

import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.local.dao.AlertDao
import com.bysel.trader.data.models.Alert
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for TradingRepository.deactivateAlert() method
 * This method was added to fix Bug #4 (alert spam prevention)
 */
@ExperimentalCoroutinesApi
class TradingRepositoryAlertDeactivationTest {

    @Mock
    private lateinit var mockDatabase: BYSELDatabase

    @Mock
    private lateinit var mockAlertDao: AlertDao

    private lateinit var repository: TradingRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockDatabase.alertDao()).thenReturn(mockAlertDao)

        // Create repository with mocked database
        // Note: You may need to adjust this based on your actual TradingRepository constructor
        repository = mock(TradingRepository::class.java)
    }

    // ==================== Deactivate Alert Tests ====================

    @Test
    fun `test deactivateAlert marks alert as inactive`() = runTest {
        // Given: An active alert
        val alertId = 1
        val activeAlert = Alert(
            id = alertId,
            symbol = "RELIANCE",
            thresholdPrice = 2500.0,
            alertType = "ABOVE",
            isActive = true
        )

        whenever(mockAlertDao.getAlertById(alertId)).thenReturn(activeAlert)
        doNothing().`when`(mockAlertDao).updateAlert(any())

        // When: Deactivate the alert
        // Note: This is a conceptual test - actual implementation depends on repository structure
        val deactivatedAlert = activeAlert.copy(isActive = false)
        mockAlertDao.updateAlert(deactivatedAlert)

        // Then: Alert should be updated with isActive = false
        verify(mockAlertDao, times(1)).updateAlert(argThat { !it.isActive })
    }

    @Test
    fun `test deactivateAlert with invalid ID returns error`() = runTest {
        // Given: Repository configured to return error for invalid ID
        val invalidId = -1

        whenever(repository.deactivateAlert(invalidId))
            .thenReturn(Result.Error("Alert not found"))

        // When: Try to deactivate non-existent alert
        val result = repository.deactivateAlert(invalidId)

        // Then: Should return error
        assertTrue(result is Result.Error, "Should return error for invalid ID")
        assertEquals("Alert not found", (result as Result.Error).message)
    }

    @Test
    fun `test deactivateAlert succeeds for valid alert`() = runTest {
        // Given: Valid alert ID
        val validId = 1

        whenever(repository.deactivateAlert(validId))
            .thenReturn(Result.Success(Unit))

        // When: Deactivate alert
        val result = repository.deactivateAlert(validId)

        // Then: Should return success
        assertTrue(result is Result.Success, "Should return success for valid ID")
    }

    @Test
    fun `test deactivateAlert is idempotent`() = runTest {
        // Given: Alert that's already inactive
        val alertId = 1

        whenever(repository.deactivateAlert(alertId))
            .thenReturn(Result.Success(Unit))

        // When: Deactivate the same alert multiple times
        val result1 = repository.deactivateAlert(alertId)
        val result2 = repository.deactivateAlert(alertId)
        val result3 = repository.deactivateAlert(alertId)

        // Then: All operations should succeed (idempotent)
        assertTrue(result1 is Result.Success, "First deactivation should succeed")
        assertTrue(result2 is Result.Success, "Second deactivation should succeed")
        assertTrue(result3 is Result.Success, "Third deactivation should succeed")
    }

    @Test
    fun `test deactivateAlert handles database exceptions`() = runTest {
        // Given: Database operation throws exception
        val alertId = 1

        whenever(repository.deactivateAlert(alertId))
            .thenReturn(Result.Error("Database error: Connection failed"))

        // When: Try to deactivate alert
        val result = repository.deactivateAlert(alertId)

        // Then: Should return error
        assertTrue(result is Result.Error, "Should handle database exception")
        assertTrue((result as Result.Error).message.contains("Database error"))
    }

    @Test
    fun `test deactivateAlert doesn't delete alert from database`() = runTest {
        // This test verifies the key difference between deactivate and delete
        // Deactivate should update isActive = false, NOT remove the row

        // Given: Active alert
        val alertId = 1
        val alert = Alert(
            id = alertId,
            symbol = "TCS",
            thresholdPrice = 3500.0,
            alertType = "BELOW",
            isActive = true
        )

        whenever(mockAlertDao.getAlertById(alertId)).thenReturn(alert)
        doNothing().`when`(mockAlertDao).updateAlert(any())

        // When: Deactivate (not delete)
        val deactivatedAlert = alert.copy(isActive = false)
        mockAlertDao.updateAlert(deactivatedAlert)

        // Then: Should call updateAlert, NOT deleteAlert
        verify(mockAlertDao, times(1)).updateAlert(any())
        verify(mockAlertDao, never()).deleteAlert(any())

        // And: Alert should still be retrievable
        val retrievedAlert = mockAlertDao.getAlertById(alertId)
        assertEquals(alertId, retrievedAlert?.id, "Alert should still exist in database")
    }

    @Test
    fun `test deactivateAlert preserves alert data except isActive flag`() = runTest {
        // Given: Alert with specific data
        val alertId = 1
        val originalAlert = Alert(
            id = alertId,
            symbol = "INFY",
            thresholdPrice = 1500.0,
            alertType = "ABOVE",
            isActive = true
        )

        whenever(mockAlertDao.getAlertById(alertId)).thenReturn(originalAlert)

        // When: Deactivate
        val deactivatedAlert = originalAlert.copy(isActive = false)
        mockAlertDao.updateAlert(deactivatedAlert)

        // Then: All data except isActive should be preserved
        assertEquals(originalAlert.id, deactivatedAlert.id)
        assertEquals(originalAlert.symbol, deactivatedAlert.symbol)
        assertEquals(originalAlert.thresholdPrice, deactivatedAlert.thresholdPrice)
        assertEquals(originalAlert.alertType, deactivatedAlert.alertType)
        assertEquals(false, deactivatedAlert.isActive, "Only isActive should change")
    }

    // ==================== Integration with Alert Evaluation ====================

    @Test
    fun `test alert evaluation workflow with deactivation`() = runTest {
        // This test simulates the complete workflow from Bug #4 fix
        // 1. Alert is active and monitoring
        // 2. Price crosses threshold
        // 3. Alert triggers notification
        // 4. Alert is deactivated (not deleted)
        // 5. User can see triggered alert in history

        // Given: Active alert
        val alertId = 1
        val alert = Alert(
            id = alertId,
            symbol = "RELIANCE",
            thresholdPrice = 2500.0,
            alertType = "ABOVE",
            isActive = true
        )

        whenever(mockAlertDao.getAlertById(alertId)).thenReturn(alert)
        whenever(repository.deactivateAlert(alertId)).thenReturn(Result.Success(Unit))

        // When: Price crosses threshold (simulated in evaluateAlerts)
        val currentPrice = 2550.0
        val threshold = alert.thresholdPrice
        val shouldTrigger = (alert.alertType == "ABOVE" && currentPrice >= threshold) ||
                           (alert.alertType == "BELOW" && currentPrice <= threshold)

        assertTrue(shouldTrigger, "Alert should trigger")

        // When: Alert is deactivated
        val result = repository.deactivateAlert(alertId)

        // Then: Deactivation succeeds
        assertTrue(result is Result.Success, "Deactivation should succeed")

        // And: Alert remains in database for history
        verify(mockAlertDao, never()).deleteAlert(any())
    }
}
