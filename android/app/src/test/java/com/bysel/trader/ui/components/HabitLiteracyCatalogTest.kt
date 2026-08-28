package com.bysel.trader.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitLiteracyCatalogTest {

    @Test
    fun sessionAndInvestorLinksAreLearnQueriesNotPrimers() {
        val session = HabitLiteracyCatalog.sessionLinks
        val investor = HabitLiteracyCatalog.investorLinks
        val blob = (session + investor).joinToString("\n") { "Learn: ${it.title}\n${it.learnQuery}" }
        assertTrue(session.size in 4..8)
        assertTrue(investor.size in 4..8)
        assertTrue(session.any { it.title.contains("Opening range", ignoreCase = true) })
        assertTrue(investor.any { it.title.contains("Long-term", ignoreCase = true) })
        assertFalse(blob.contains("NAV = (assets", ignoreCase = true))
        assertTrue(HabitLiteracyCatalog.investorLinksFor("sgb").single().id == "sgb")
        assertTrue(HabitLiteracyCatalog.investorLinksFor("fno").single().id == "fno")
        assertTrue(session.all { HabitLiteracyCatalog.isHabitLearnQuery(it.learnQuery) })
        assertTrue(investor.all { HabitLiteracyCatalog.isHabitLearnQuery(it.learnQuery) })
        assertTrue(session.any { it.learnQuery.contains("opening range", ignoreCase = true) })
        assertTrue(session.any { it.learnQuery.contains("midday chop", ignoreCase = true) })
        assertTrue(investor.any { it.learnQuery.contains("long-term investing", ignoreCase = true) })
        assertFalse(blob.contains("no buy or sell", ignoreCase = true))
    }

    @Test
    fun fnoScannerLinksAreLearnOnlyWithoutEquations() {
        val links = HabitLiteracyCatalog.fnoScannerLinks
        val blob = links.joinToString("\n") { "${it.title}\n${it.learnQuery}" }
        assertTrue(links.size >= 3)
        assertTrue(links.any { it.title.contains("Futures vs options", ignoreCase = true) })
        assertFalse(blob.contains("Notional =", ignoreCase = true))
        assertFalse(blob.contains("PCR =", ignoreCase = true))
        assertFalse(blob.contains("Delta", ignoreCase = true))
        assertFalse(blob.contains("SPAN", ignoreCase = true))
    }
}
