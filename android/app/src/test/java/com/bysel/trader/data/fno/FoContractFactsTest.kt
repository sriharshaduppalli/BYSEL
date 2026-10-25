package com.bysel.trader.data.fno

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Mirrors backend/tests/test_fo_contracts.py so both sides teach the same contract. */
class FoContractFactsTest {

    @Test
    fun indexLotsFollowNseCircular176Of2025() {
        assertEquals(65, FoContractFacts.lotSize("NIFTY", 25_000.0))
        assertEquals(30, FoContractFacts.lotSize("banknifty", 56_000.0))
        assertEquals(60, FoContractFacts.lotSize("FINNIFTY", 26_000.0))
        assertEquals(120, FoContractFacts.lotSize("MIDCPNIFTY", 13_000.0))
    }

    @Test
    fun unknownStockGetsRoundedTeachingLot() {
        assertEquals(50, FoContractFacts.lotSize("XYZ", 2_400.0))
        assertEquals(100, FoContractFacts.lotSize("XYZ", 0.0))
    }

    @Test
    fun niftyWeeklyExpiryIsTuesday() {
        assertEquals(LocalDate.of(2026, 10, 27), FoContractFacts.nextWeeklyExpiry(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 27), FoContractFacts.nextWeeklyExpiry(LocalDate.of(2026, 10, 27)))
        assertEquals(LocalDate.of(2026, 11, 3), FoContractFacts.nextWeeklyExpiry(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun monthlyExpiryIsLastTuesday() {
        assertEquals(LocalDate.of(2026, 10, 27), FoContractFacts.monthlyExpiry(YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 11, 24), FoContractFacts.monthlyExpiry(YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2026, 12, 29), FoContractFacts.monthlyExpiry(YearMonth.of(2026, 12)))
    }

    @Test
    fun stockRollsToNextMonthAfterExpiry() {
        assertEquals(LocalDate.of(2026, 11, 24), FoContractFacts.nextExpiryFor("RELIANCE", LocalDate.of(2026, 10, 28)))
        assertEquals(LocalDate.of(2026, 11, 3), FoContractFacts.nextExpiryFor("NIFTY", LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun monthlyCycleIsThreeConsecutiveMonths() {
        val cycle = FoContractFacts.monthlyCycle(3, LocalDate.of(2026, 10, 25))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 29)),
            cycle,
        )
    }
}
