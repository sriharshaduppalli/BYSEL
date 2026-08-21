package com.bysel.trader.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerReachabilityTest {

    @Before
    fun reset() {
        ServerReachability.resetForTests()
    }

    @Test
    fun startsCold() {
        assertTrue(ServerReachability.isLikelyColdStart())
        assertFalse(ServerReachability.isLikelyWarm())
    }

    @Test
    fun fastSuccessMarksWarm() {
        ServerReachability.markSuccess(250)
        assertTrue(ServerReachability.isLikelyWarm())
        assertTrue(ServerReachability.hadFastSuccessRecently())
        assertFalse(ServerReachability.isLikelyColdStart())
    }

    @Test
    fun slowSuccessStillWarmButNotFast() {
        ServerReachability.markSuccess(8_000)
        assertTrue(ServerReachability.isLikelyWarm())
        assertFalse(ServerReachability.hadFastSuccessRecently())
    }
}
