package com.bysel.trader.data.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class TokenRefreshAuthenticator : Authenticator {
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

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        // Only skip refresh for unauthenticated auth endpoints. Protected ones like
        // /auth/me and /auth/sessions must refresh on 401 or profile stays empty.
        if (path in publicAuthPaths) return null
        if (responseCount(response) >= 2) return null

        // Wrong-password / credential failures are also 401. Refreshing the access token
        // would mask the real error and retry a doomed request.
        if (isCredentialFailure(response)) return null

        val failedAccessToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val latestToken = AuthSessionManager.getAccessToken()
        if (!latestToken.isNullOrBlank() && latestToken != failedAccessToken) {
            return rebuildWithAuth(response.request, latestToken)
        }

        val refreshedAccess = AuthTokenRefresher.refreshBlocking(failedAccessToken = failedAccessToken)
            ?: return null

        return rebuildWithAuth(response.request, refreshedAccess)
    }

    private fun isCredentialFailure(response: Response): Boolean {
        if (response.code != 401) return false
        val path = response.request.url.encodedPath
        if (path != "/auth/change-password" && path != "/auth/delete-account") {
            return false
        }
        val detail = runCatching {
            response.peekBody(2_048).string()
                .let { JSONObject(it).optString("detail") }
                .lowercase()
        }.getOrNull().orEmpty()
        return detail.contains("password") ||
            detail.contains("credential") ||
            detail.contains("incorrect")
    }

    private fun rebuildWithAuth(request: Request, accessToken: String): Request {
        val builder = request.newBuilder()
            .header("Authorization", "Bearer $accessToken")

        AuthSessionManager.getUserId()?.let { uid ->
            builder.header("user_id", uid.toString())
            builder.header("user-id", uid.toString())
        }
        return builder.build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
