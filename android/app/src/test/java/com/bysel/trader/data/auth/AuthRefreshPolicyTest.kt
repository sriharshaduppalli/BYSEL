package com.bysel.trader.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRefreshPolicyTest {

    @Test
    fun quotesPathDoesNotAttachAuthOrRefresh() {
        assertTrue(AuthRefreshPolicy.isPublicUnauthenticatedPath("/quotes"))
        assertTrue(AuthRefreshPolicy.isPublicUnauthenticatedPath("/quotes/RELIANCE"))
        assertFalse(AuthRefreshPolicy.shouldAttachAuthHeaders("/quotes"))
        assertFalse(
            AuthRefreshPolicy.shouldAttemptRefresh("/quotes", 401, "Token expired")
        )
    }

    @Test
    fun heatmapAndWsQuotesArePublic() {
        assertTrue(AuthRefreshPolicy.isPublicUnauthenticatedPath("/market/heatmap"))
        assertTrue(AuthRefreshPolicy.isPublicUnauthenticatedPath("/ws/quotes"))
        assertFalse(AuthRefreshPolicy.shouldAttemptRefresh("/market/heatmap", 401, null))
    }

    @Test
    fun holdings401AttemptsRefreshButDoesNotClearSession() {
        assertTrue(AuthRefreshPolicy.shouldAttachAuthHeaders("/holdings"))
        assertTrue(
            AuthRefreshPolicy.shouldAttemptRefresh("/holdings", 401, "Token expired")
        )
        assertFalse(
            AuthRefreshPolicy.shouldClearSession(401, "Token expired")
        )
        assertFalse(
            AuthRefreshPolicy.shouldClearSession(401, "Invalid token signature")
        )
        assertFalse(
            AuthRefreshPolicy.shouldClearSession(401, "Invalid refresh token")
        )
    }

    @Test
    fun alreadyRotatedRefreshDoesNotLogOut() {
        assertTrue(AuthRefreshPolicy.isBenignRotation("Refresh token already rotated"))
        assertFalse(AuthRefreshPolicy.shouldClearSession(401, "Refresh token already rotated"))
    }

    @Test
    fun definitiveRefreshFailuresDoLogOut() {
        assertTrue(AuthRefreshPolicy.shouldClearSession(401, "Refresh token reuse detected"))
        assertTrue(AuthRefreshPolicy.shouldClearSession(401, "Session invalidated"))
        assertTrue(AuthRefreshPolicy.shouldClearSession(401, "Refresh token expired"))
        assertTrue(AuthRefreshPolicy.shouldClearSession(401, "Refresh token revoked"))
    }

    @Test
    fun deleteAccountPassword401DoesNotRefresh() {
        assertFalse(
            AuthRefreshPolicy.shouldAttemptRefresh(
                "/auth/delete-account",
                401,
                "Invalid password",
            )
        )
    }

    @Test
    fun profile401StillRefreshes() {
        assertTrue(AuthRefreshPolicy.shouldAttemptRefresh("/auth/me", 401, "Token expired"))
        assertFalse(AuthRefreshPolicy.isPublicUnauthenticatedPath("/auth/me"))
    }

    @Test
    fun accessTokenExpiryIsReadFromJwtPayload() {
        val payload = """{"uid":1,"typ":"access","exp":1700000000}"""
        val encoded = encodeBase64Url(payload.toByteArray())
        val token = "$encoded.signature"
        assertEquals(1_700_000_000_000L, AuthRefreshPolicy.decodeAccessTokenExpiryMs(token))
    }

    @Test
    fun malformedAccessTokenHasNoExpiry() {
        assertNull(AuthRefreshPolicy.decodeAccessTokenExpiryMs("not-a-token"))
        assertNull(AuthRefreshPolicy.decodeAccessTokenExpiryMs(null))
    }

    @Test
    fun expiryBufferTreatsTokenAsExpiringSoon() {
        val expiryMs = 1_000_000L
        val fiveMinBefore = expiryMs - AuthRefreshPolicy.EXPIRY_BUFFER_MS
        assertTrue(
            AuthRefreshPolicy.isAccessTokenExpiringSoon(
                expiryMs,
                fiveMinBefore - AuthRefreshPolicy.CLOCK_SKEW_MS,
            )
        )
        assertFalse(
            AuthRefreshPolicy.isAccessTokenExpiringSoon(
                expiryMs,
                expiryMs - AuthRefreshPolicy.EXPIRY_BUFFER_MS - AuthRefreshPolicy.CLOCK_SKEW_MS - 1,
            )
        )
    }

    private fun encodeBase64Url(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 6) {
                bits -= 6
                out.append(alphabet[(buffer shr bits) and 0x3F])
            }
        }
        if (bits > 0) {
            out.append(alphabet[(buffer shl (6 - bits)) and 0x3F])
        }
        return out.toString()
    }
}
