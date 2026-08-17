package com.bysel.trader.data.auth

/**
 * Pure refresh/logout rules so unit tests can lock the session policy without OkHttp.
 *
 * Stay-signed-in is the refresh token (days). Access tokens are short-lived; a 401
 * from /quotes or another public market call must not rotate tokens or log the user out.
 */
object AuthRefreshPolicy {
    const val EXPIRY_BUFFER_MS = 5 * 60 * 1000L
    const val CLOCK_SKEW_MS = 60_000L

    private val publicAuthPaths = setOf(
        "/auth/register",
        "/auth/login",
        "/auth/refresh",
        "/auth/send-otp",
        "/auth/verify-otp",
        "/auth/firebase-phone",
        "/auth/password-reset/request",
        "/auth/password-reset/confirm",
    )

    private val credentialChallengePaths = setOf(
        "/auth/change-password",
        "/auth/delete-account",
    )

    fun normalizePath(path: String): String {
        val trimmed = path.trim().ifBlank { "/" }
        val withoutQuery = trimmed.substringBefore('?')
        return if (withoutQuery.startsWith("/")) withoutQuery else "/$withoutQuery"
    }

    fun isPublicAuthPath(path: String): Boolean = normalizePath(path) in publicAuthPaths

    fun isPublicUnauthenticatedPath(path: String): Boolean {
        val normalized = normalizePath(path).lowercase()
        if (normalized in publicAuthPaths) return true
        return normalized == "/health" ||
            normalized == "/warmup" ||
            normalized == "/ready" ||
            normalized.startsWith("/quotes") ||
            normalized.startsWith("/market/") ||
            normalized.startsWith("/ws/")
    }

    fun shouldAttachAuthHeaders(path: String): Boolean {
        return !isPublicUnauthenticatedPath(path)
    }

    fun isCredentialChallenge(path: String, detail: String?): Boolean {
        if (normalizePath(path) !in credentialChallengePaths) return false
        val normalized = detail.orEmpty().lowercase()
        return normalized.contains("password") ||
            normalized.contains("credential") ||
            normalized.contains("incorrect")
    }

    /**
     * Authenticator should refresh only for protected-route 401s that look like
     * an expired/invalid access token — never for Yahoo/market 401s.
     */
    fun shouldAttemptRefresh(path: String, statusCode: Int, detail: String?): Boolean {
        if (statusCode != 401) return false
        if (isPublicUnauthenticatedPath(path)) return false
        if (isCredentialChallenge(path, detail)) return false
        return true
    }

    fun isBenignRotation(detail: String?): Boolean {
        val normalized = detail.orEmpty().lowercase()
        return normalized.contains("already rotated") ||
            normalized.contains("replay") ||
            normalized.contains("recovered")
    }

    /**
     * Wipe the local session only when the server has ended this refresh family.
     * A single 401, invalid-token race, or flaky market call must not log the user out.
     */
    fun shouldClearSession(code: Int, detail: String?): Boolean {
        if (code != 401 && code != 403) return false
        if (isBenignRotation(detail)) return false
        val normalized = detail.orEmpty().lowercase()
        return normalized.contains("reuse detected") ||
            normalized.contains("session invalidated") ||
            normalized.contains("refresh token expired") ||
            normalized.contains("refresh token revoked") ||
            normalized.contains("logged out from all")
    }

    fun decodeAccessTokenExpiryMs(accessToken: String?): Long? {
        if (accessToken.isNullOrBlank()) return null
        val payloadPart = accessToken.substringBefore('.', missingDelimiterValue = "")
        if (payloadPart.isBlank()) return null
        return try {
            val json = String(decodeBase64Url(payloadPart), Charsets.UTF_8)
            val match = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json) ?: return null
            val expSeconds = match.groupValues[1].toLong()
            if (expSeconds > 0L) expSeconds * 1000L else null
        } catch (_: Exception) {
            null
        }
    }

    fun isAccessTokenExpiringSoon(expiryMs: Long, nowMs: Long): Boolean {
        if (expiryMs <= 0L) return false
        return nowMs + CLOCK_SKEW_MS >= expiryMs - EXPIRY_BUFFER_MS
    }

    /** URL-safe Base64 without java.util.Base64 (API 26+) so this runs on minSdk 24 and JVM tests. */
    internal fun decodeBase64Url(input: String): ByteArray {
        val filtered = input.trim().replace("=", "")
        val output = ArrayList<Byte>(filtered.length)
        var buffer = 0
        var bits = 0
        for (ch in filtered) {
            val index = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a' + 26
                in '0'..'9' -> ch - '0' + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> continue
            }
            buffer = (buffer shl 6) or index
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }
}
