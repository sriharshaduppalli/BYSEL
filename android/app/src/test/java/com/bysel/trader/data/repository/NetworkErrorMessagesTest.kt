package com.bysel.trader.data.repository

import com.bysel.trader.data.api.ServerReachability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorMessagesTest {

    @Before
    fun resetReachability() {
        ServerReachability.resetForTests()
    }

    @Test
    fun unknownHostIsOffline() {
        val msg = NetworkErrorMessages.forHoldings(UnknownHostException("Unable to resolve host"))
        assertTrue(msg.contains("No internet", ignoreCase = true))
    }

    @Test
    fun noRouteIsOffline() {
        val msg = NetworkErrorMessages.forHoldings(NoRouteToHostException("No route to host"))
        assertTrue(msg.contains("No internet", ignoreCase = true))
    }

    @Test
    fun timeoutIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(SocketTimeoutException("timeout"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("waking up", ignoreCase = true) || msg.contains("too long", ignoreCase = true))
        assertTrue(msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun timeoutIsNotLabeledColdStartWhenHostAlreadyWarm() {
        ServerReachability.markSuccess(400)
        val msg = NetworkErrorMessages.forHoldings(SocketTimeoutException("timeout"))
        assertFalse(msg.contains("waking up", ignoreCase = true))
        assertTrue(msg.contains("too long", ignoreCase = true))
    }

    @Test
    fun http5xxIsNotLabeledColdStartWhenHostAlreadyWarm() {
        ServerReachability.markSuccess(500)
        val msg = NetworkErrorMessages.forHoldings(RuntimeException("HTTP 503"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertFalse(msg.contains("waking up", ignoreCase = true))
        assertTrue(msg.contains("unavailable", ignoreCase = true) || msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun connectFailureIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(ConnectException("Failed to connect to render"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("waking up", ignoreCase = true) || msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun httpBusyIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(RuntimeException("HTTP 429 Too Many Requests"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("retry", ignoreCase = true))
        assertFalse(msg.contains("No internet", ignoreCase = true))
    }

    @Test
    fun http5xxIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(RuntimeException("HTTP 503"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("waking up", ignoreCase = true) || msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun unauthorizedIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(RuntimeException("HTTP 401"))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun emptyPayloadIsNotOffline() {
        val msg = NetworkErrorMessages.forHoldings(RuntimeException(""))
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("retry", ignoreCase = true))
    }

    @Test
    fun marketTimeoutIsNotOfflineAndNotPortfolio() {
        val msg = NetworkErrorMessages.forMarket(
            SocketTimeoutException("timeout"),
            "Failed to refresh quotes",
        )
        assertFalse(msg.contains("No internet", ignoreCase = true))
        assertFalse(msg.contains("portfolio", ignoreCase = true))
        assertTrue(msg.contains("cached", ignoreCase = true) || msg.contains("refresh", ignoreCase = true))
    }

    @Test
    fun marketOfflineKeepsLastSavedCopy() {
        val msg = NetworkErrorMessages.forMarket(
            UnknownHostException("Unable to resolve host"),
            "Failed to refresh quotes",
        )
        assertTrue(msg.contains("No internet", ignoreCase = true))
        assertTrue(msg.contains("last saved", ignoreCase = true))
        assertFalse(msg.contains("portfolio", ignoreCase = true))
    }
}
