package com.bysel.trader.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Theme switches glide instead of snapping, but must settle on the exact target
 * palette so every derived role (onPrimary, cardOutline, Material scheme) is exact.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeMotionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun animatedTheme_passesThroughIntermediateColors_thenSettlesOnTarget() {
        val from = getTheme("Default")
        val to = getTheme("Forest")
        var target by mutableStateOf(from)
        var shown: AppTheme? = null

        rule.mainClock.autoAdvance = false
        rule.setContent { shown = rememberAnimatedAppTheme(target) }
        rule.waitForIdle()
        assertSame(from, shown)

        target = to
        // Recompose to launch the animations, then tick ~10 frames and recompose again.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(160)
        rule.waitForIdle()
        val midway = shown!!
        assertNotEquals(from.primary, midway.primary)
        assertNotEquals(to.primary, midway.primary)
        assertEquals(to.name, midway.name)

        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertSame(to, shown)
    }

    @Test
    fun overlayTransition_pushLandsOnTop() {
        assertEquals(1f, overlayScreenTransition(push = true).targetContentZIndex, 0f)
        assertEquals(0f, overlayScreenTransition(push = false).targetContentZIndex, 0f)
    }
}
