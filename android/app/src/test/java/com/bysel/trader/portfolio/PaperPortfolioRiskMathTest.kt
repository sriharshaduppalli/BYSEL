package com.bysel.trader.portfolio

import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPortfolioRiskMathTest {

    private fun holding(symbol: String, qty: Int, avg: Double, last: Double) = Holding(
        symbol = symbol,
        qty = qty,
        avgPrice = avg,
        last = last,
        pnl = (last - avg) * qty,
    )

    @Test
    fun emptyBookHasNoFakeDrawdownOrVol() {
        val payload = PaperPortfolioRiskMath.empty()
        assertTrue(payload.empty)
        assertFalse(payload.volatility.available)
        assertFalse(payload.maxDrawdown.available)
        assertTrue(payload.volatility.note.contains("history", ignoreCase = true))
        assertEquals(0.0, payload.whatIf.niftyDown5, 0.001)
        assertTrue(payload.whatIf.label.contains("not a forecast", ignoreCase = true))
    }

    @Test
    fun singleNameIsFullyConcentrated() {
        val weights = PaperPortfolioRiskMath.positionWeights(listOf(10_000.0))
        val conc = PaperPortfolioRiskMath.concentrationFromWeights(weights, listOf("MOSCHIP"))
        assertEquals(100.0, conc.top1Pct, 0.001)
        assertEquals(100.0, conc.top5Pct, 0.001)
        assertEquals("MOSCHIP", conc.top1Symbol)
        assertEquals(100, conc.gauge)
    }

    @Test
    fun fiveEqualNamesTop1Is20Top5Is100() {
        val weights = PaperPortfolioRiskMath.positionWeights(List(5) { 100.0 })
        val conc = PaperPortfolioRiskMath.concentrationFromWeights(
            weights,
            listOf("A", "B", "C", "D", "E"),
        )
        assertEquals(20.0, conc.top1Pct, 0.001)
        assertEquals(100.0, conc.top5Pct, 0.001)
        assertEquals(20, conc.gauge)
    }

    @Test
    fun top5CapsAtBookWhenFewerThanFive() {
        val weights = PaperPortfolioRiskMath.positionWeights(listOf(70.0, 30.0))
        val conc = PaperPortfolioRiskMath.concentrationFromWeights(weights, listOf("RELIANCE", "TCS"))
        assertEquals(70.0, conc.top1Pct, 0.001)
        assertEquals(100.0, conc.top5Pct, 0.001)
    }

    @Test
    fun whatIfNiftyShocksUseBetaOne() {
        assertEquals(-5_000.0, PaperPortfolioRiskMath.niftyWhatIf(100_000.0, -5.0), 0.001)
        assertEquals(-10_000.0, PaperPortfolioRiskMath.niftyWhatIf(100_000.0, -10.0), 0.001)
        assertEquals(0.0, PaperPortfolioRiskMath.niftyWhatIf(0.0, -5.0), 0.001)
    }

    @Test
    fun valueWeightedScoreSkipsMissing() {
        val skipped = PaperPortfolioRiskMath.valueWeightedScore(listOf(1000.0, 1000.0), listOf(80.0, null))
        assertEquals(80, skipped.valueWeighted)
        assertEquals(1, skipped.scoredCount)
        assertEquals(1, skipped.missingCount)
        assertEquals(50.0, skipped.coveredValuePct, 0.001)

        val blended = PaperPortfolioRiskMath.valueWeightedScore(listOf(2000.0, 1000.0), listOf(80.0, 50.0))
        assertEquals(70, blended.valueWeighted)
    }

    @Test
    fun sectorSpreadOneBucketIsZeroEqualTwoIsFifty() {
        val one = PaperPortfolioRiskMath.sectorMix(listOf(100.0), listOf("Energy"))
        val spreadOne = PaperPortfolioRiskMath.sectorSpreadFromMix(one)
        assertEquals(0, spreadOne.gauge)
        assertEquals(1, spreadOne.sectorCount)
        assertEquals(1.0, PaperPortfolioRiskMath.herfindahlHhi(listOf(100.0)), 0.001)

        val two = PaperPortfolioRiskMath.sectorMix(listOf(50.0, 50.0), listOf("Energy", "IT"))
        val spreadTwo = PaperPortfolioRiskMath.sectorSpreadFromMix(two)
        assertEquals(50, spreadTwo.gauge)
        assertEquals(2, spreadTwo.sectorCount)
    }

    @Test
    fun dayPnlFromPrevCloseAndPctChange() {
        val fromClose = PaperPortfolioRiskMath.dayPnlRupees(qty = 10, last = 110.0, prevClose = 100.0)
        assertTrue(fromClose.second)
        assertEquals(100.0, fromClose.first, 0.001)

        val implied = PaperPortfolioRiskMath.dayPnlRupees(qty = 10, last = 110.0, pctChange = 10.0)
        assertTrue(implied.second)
        assertEquals(100.0, implied.first, 0.001)

        val missing = PaperPortfolioRiskMath.dayPnlRupees(qty = 10, last = 110.0)
        assertFalse(missing.second)
        assertEquals(0.0, missing.first, 0.001)
    }

    @Test
    fun buildSnapshotConcentrationAndWhatIf() {
        val payload = PaperPortfolioRiskMath.fromHoldings(
            holdings = listOf(
                holding("RELIANCE", 10, 1000.0, 1400.0),
                holding("TCS", 5, 3000.0, 3600.0),
                holding("MOSCHIP", 20, 100.0, 100.0),
            ),
            quotes = listOf(
                Quote(symbol = "RELIANCE", last = 1400.0, pctChange = -2.0, prevClose = 1428.57),
                Quote(symbol = "TCS", last = 3600.0, pctChange = 1.0, prevClose = 3564.36),
            ),
            scores = mapOf("RELIANCE" to 70, "TCS" to 80),
        )
        assertFalse(payload.empty)
        assertEquals(34000.0, payload.totalValue, 0.001)
        assertEquals("TCS", payload.concentration.top1Symbol)
        assertEquals(52.94, payload.concentration.top1Pct, 0.001)
        assertEquals(-1700.0, payload.whatIf.niftyDown5, 0.001)
        assertEquals(-3400.0, payload.whatIf.niftyDown10, 0.001)
        assertEquals(76, payload.byselScore.valueWeighted)
        assertEquals(1, payload.byselScore.missingCount)
        assertFalse(payload.maxDrawdown.available)
        val names = payload.sectors.map { it.name }.toSet()
        assertTrue(names.contains("Energy"))
        assertTrue(names.contains("IT"))
        assertTrue(names.contains("Semiconductor"))
        assertTrue(payload.whatIf.label.contains("illustration", ignoreCase = true))
    }

    @Test
    fun emptyHoldingsSnapshot() {
        val payload = PaperPortfolioRiskMath.fromHoldings(emptyList(), emptyList())
        assertTrue(payload.empty)
        assertTrue(payload.message.contains("practice buy", ignoreCase = true))
        assertNull(payload.byselScore.valueWeighted)
    }

    @Test
    fun importedNameDoesNotDoubleCountPaperSymbol() {
        val paper = listOf(holding("RELIANCE", 2, 1000.0, 1400.0))
        val imported = PaperPortfolioRiskMath.importedAsHoldings(
            rows = listOf(
                com.bysel.trader.data.importbook.ImportedHolding("RELIANCE", 50, 1200.0),
                com.bysel.trader.data.importbook.ImportedHolding("INFY", 10, 1500.0),
            ),
            quotes = listOf(Quote(symbol = "INFY", last = 1600.0, pctChange = 1.0)),
        )
        val (merged, overlap) = PaperPortfolioRiskMath.mergePaperAndImported(paper, imported)
        assertEquals(1, overlap)
        assertEquals(2, merged.size)
        assertEquals("INFY", merged.last().symbol)
        assertEquals(1600.0, merged.last().last, 0.001)
        assertEquals(0.0, PaperPortfolioRiskMath.fromHoldings(
            holdings = listOf(holding("CASONLY", 5, 0.0, 100.0)),
            quotes = listOf(Quote(symbol = "CASONLY", last = 100.0)),
        ).totalPnl, 0.001)
    }
}
