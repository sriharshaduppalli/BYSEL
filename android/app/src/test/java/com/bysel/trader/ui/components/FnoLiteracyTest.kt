package com.bysel.trader.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FnoLiteracyTest {

    @Test
    fun optionTapeAvoidsFormulaStrip() {
        val text = optionTapePlainEnglish(1.35, 0.18, 0.04)
        assertTrue(text.contains("18%"))
        assertTrue(text.contains("puts are outstanding", ignoreCase = true))
        assertFalse(text.contains("PCR"))
        assertFalse(text.contains("P−C"))
        assertFalse(text.contains("ATM IV"))
    }

    @Test
    fun moneynessUsesWordsNotLetters() {
        assertTrue(callPlainEnglish(24_500.0, 24_500.0).contains("at spot"))
        assertTrue(callPlainEnglish(24_500.0, 25_000.0).contains("needs a rise"))
        assertTrue(putPlainEnglish(24_500.0, 24_000.0).contains("needs a fall"))
    }
}
