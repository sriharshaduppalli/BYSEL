package com.bysel.trader.data.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        val detail = peekErrorDetail(response)
        if (!AuthRefreshPolicy.shouldAttemptRefresh(path, response.code, detail)) {
            return null
        }
        if (responseCount(response) >= 2) return null

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

    private fun peekErrorDetail(response: Response): String? {
        return runCatching {
            response.peekBody(2_048).string()
                .let { JSONObject(it).optString("detail") }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
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
