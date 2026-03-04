package com.bysel.trader.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val refreshToken = AuthSessionManager.getRefreshToken() ?: return null

        return synchronized(this) {
            val latestToken = AuthSessionManager.getAccessToken()
            val currentAuth = response.request.header("Authorization")
            if (!latestToken.isNullOrBlank() && currentAuth != "Bearer $latestToken") {
                return@synchronized rebuildWithAuth(response.request, latestToken)
            }

            try {
                val refreshed = runBlocking { AuthTokenRefresher.refresh(refreshToken) }
                AuthSessionManager.saveSession(
                    accessToken = refreshed.access_token,
                    refreshToken = refreshed.refresh_token,
                    userId = refreshed.user_id
                )
                rebuildWithAuth(response.request, refreshed.access_token)
            } catch (_: Exception) {
                AuthSessionManager.clearSession()
                null
            }
        }
    }

    private fun rebuildWithAuth(request: Request, accessToken: String): Request {
        val builder = request.newBuilder()
            .header("Authorization", "Bearer $accessToken")

        AuthSessionManager.getUserId()?.let { builder.header("user_id", it.toString()) }
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
