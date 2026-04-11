package com.bysel.trader.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import retrofit2.HttpException

class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.startsWith("/auth/")) return null
        if (responseCount(response) >= 2) return null

        return synchronized(this) {
            val latestToken = AuthSessionManager.getAccessToken()
            val currentAuth = response.request.header("Authorization")
            if (!latestToken.isNullOrBlank() && currentAuth != "Bearer $latestToken") {
                return@synchronized rebuildWithAuth(response.request, latestToken)
            }

            val refreshToken = AuthSessionManager.getRefreshToken() ?: return@synchronized null

            try {
                val refreshed = runBlocking { AuthTokenRefresher.refresh(refreshToken) }
                AuthSessionManager.saveSession(
                    accessToken = refreshed.access_token,
                    refreshToken = refreshed.refresh_token,
                    userId = refreshed.user_id
                )
                rebuildWithAuth(response.request, refreshed.access_token)
            } catch (httpException: HttpException) {
                if (httpException.code() == 401 || httpException.code() == 403) {
                    AuthSessionManager.clearSession()
                }
                null
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
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
