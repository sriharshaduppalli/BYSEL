package com.bysel.trader.data.repository

import com.bysel.trader.data.api.ServerReachability
import retrofit2.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Honest network copy. "No internet" only for real connectivity failures —
 * never timeout, 429, 5xx, 401, or an empty payload.
 */
object NetworkErrorMessages {

    fun isExplicitOffline(e: Exception): Boolean {
        val raw = e.message.orEmpty()
        return e is UnknownHostException ||
            e is NoRouteToHostException ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true) ||
            raw.contains("ENETUNREACH", ignoreCase = true) ||
            raw.contains("EHOSTUNREACH", ignoreCase = true) ||
            (raw.contains("offline", ignoreCase = true) && raw.contains("network", ignoreCase = true))
    }

    fun isRateLimited(e: Exception): Boolean {
        if (e is HttpException && e.code() == 429) return true
        val raw = e.message.orEmpty()
        return raw.contains("429") ||
            raw.contains("too many requests", ignoreCase = true) ||
            raw.contains("rate limit", ignoreCase = true)
    }

    fun forException(e: Exception, fallback: String): String {
        val raw = e.message.orEmpty()
        val httpCode = httpCodeOf(e, raw)
        return when {
            isRateLimited(e) || httpCode == 429 ->
                "$fallback Server is busy (too many requests)."
            isTimeout(e, raw) ->
                timeoutCopy("Tap to retry — this is not an offline problem.")
            isConnectFailure(e, raw) ->
                connectCopy()
            httpCode == 401 || httpCode == 403 ->
                "$fallback Sign-in may have expired."
            isServerError(httpCode, raw) ->
                serverErrorCopy()
            isExplicitOffline(e) ->
                "No internet connection. Check your network and retry."
            raw.startsWith("HTTP ", ignoreCase = true) -> fallback
            raw.isNotBlank() && raw.length < 120 && !looksLikeOfflineMislabel(raw) ->
                "$fallback ($raw)"
            else -> fallback
        }
    }

    fun forHoldings(e: Exception): String {
        val raw = e.message.orEmpty()
        val httpCode = httpCodeOf(e, raw)
        return when {
            isRateLimited(e) || httpCode == 429 ->
                "Couldn't load portfolio — tap to retry. Server is busy (too many requests)."
            isTimeout(e, raw) ->
                timeoutCopy("Tap to retry — this is not an offline problem.")
            isConnectFailure(e, raw) ->
                connectCopy()
            httpCode == 401 || httpCode == 403 ->
                "Couldn't load portfolio — tap to retry. Sign-in may have expired."
            isServerError(httpCode, raw) ->
                serverErrorCopy()
            isExplicitOffline(e) ->
                "No internet connection. Check your network and retry."
            else -> forException(e, "Couldn't load portfolio — tap to retry")
        }
    }

    fun forMarket(e: Exception, fallback: String): String {
        val raw = e.message.orEmpty()
        val httpCode = httpCodeOf(e, raw)
        return when {
            isRateLimited(e) || httpCode == 429 ->
                "Market data is busy right now. Last prices stay on screen — try again in a few seconds."
            isTimeout(e, raw) ->
                "Market data is taking longer than usual. Pull to refresh — cached prices stay visible."
            isServerError(httpCode, raw) ->
                "Market server is temporarily unavailable. Retry in a moment."
            isExplicitOffline(e) ->
                "No internet connection. Showing last saved data when available."
            else -> forException(e, fallback)
        }
    }

    fun isTransientQuoteMessage(message: String?): Boolean {
        val m = message.orEmpty().lowercase()
        if (m.isBlank()) return false
        return m.contains("timeout") ||
            m.contains("timed out") ||
            m.contains("too long") ||
            m.contains("taking longer") ||
            m.contains("waking up") ||
            m.contains("too many requests") ||
            m.contains("busy right now") ||
            m.contains("temporarily unavailable") ||
            m.contains("failed to connect") ||
            m.contains("cannot reach") ||
            m.contains("quote refresh returned no prices")
    }

    fun timeoutCopy(suffix: String): String {
        return if (ServerReachability.isLikelyWarm()) {
            "Server took too long. $suffix"
        } else {
            "Server is waking up. $suffix"
        }
    }

    fun connectCopy(): String {
        return if (ServerReachability.isLikelyWarm()) {
            "Cannot reach BYSEL servers. Check your internet and tap to retry."
        } else {
            "Server is waking up. Tap to retry."
        }
    }

    fun serverErrorCopy(): String {
        return if (ServerReachability.isLikelyColdStart()) {
            "Server is waking up. Tap to retry."
        } else {
            "Server is temporarily unavailable. Tap to retry."
        }
    }

    private fun httpCodeOf(e: Exception, raw: String): Int? {
        return (e as? HttpException)?.code()
            ?: Regex("(?i)HTTP\\s+(\\d{3})").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun isTimeout(e: Exception, raw: String): Boolean {
        return e is SocketTimeoutException ||
            raw.contains("timeout", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true)
    }

    private fun isConnectFailure(e: Exception, raw: String): Boolean {
        return e is ConnectException ||
            raw.contains("failed to connect", ignoreCase = true) ||
            raw.contains("connection refused", ignoreCase = true)
    }

    private fun isServerError(httpCode: Int?, raw: String): Boolean {
        return (httpCode != null && httpCode in 500..599) ||
            raw.contains("503") || raw.contains("502") || raw.contains("504")
    }

    private fun looksLikeOfflineMislabel(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("no internet") || lower.contains("offline")
    }
}
