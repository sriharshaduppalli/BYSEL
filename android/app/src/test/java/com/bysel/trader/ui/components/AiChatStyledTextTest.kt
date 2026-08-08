package com.bysel.trader.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatStyledTextTest {
    @Test
    fun styleAiChatText_stripsMarkdownStarsAndKeepsActionWords() {
        val styled = styleAiChatText(
            raw = "**Action:** TRIM (score -2)\n• Meaning: Lighten / reduce on strength (paper)",
            bodyColor = Color.White,
            mutedColor = Color.Gray,
            primary = Color.Cyan,
            positive = Color.Green,
            negative = Color.Red,
            caution = Color(0xFFFFB74D),
        )
        val plain = styled.text
        assertFalse(plain.contains("**"))
        assertTrue(plain.contains("Action:"))
        assertTrue(plain.contains("TRIM"))
        assertTrue(plain.contains("Meaning"))
    }
}
