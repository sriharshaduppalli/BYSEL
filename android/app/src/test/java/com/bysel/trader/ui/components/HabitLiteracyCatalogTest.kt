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
    }
}
