package com.bysel.trader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentColorForFillTest {
    @Test
    fun pastelNegativeGetsDarkLabel() {
        // Default / Amoled loss pink used as a filled Delete/Sell button.
        val label = contentColorForFill(Color(0xFFEF9A9A))
        assertEquals(Color(0xFF121212), label)
    }

    @Test
    fun deepRedGetsWhiteLabel() {
        val label = contentColorForFill(Color(0xFFC62828))
        assertEquals(Color.White, label)
    }
}
