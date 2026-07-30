package com.bysel.trader.data.auth

import android.util.Log
import com.bysel.trader.BuildConfig
import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.RequestMetadataInterceptor
import com.bysel.trader.data.models.AuthResponse
import com.bysel.trader.data.models.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single-flight token refresh shared by OkHttp authenticator and proactive resume.
 * Prevents concurrent /auth/refresh calls from rotating the same refresh token twice
 * and wiping a still-valid session on the loser's 401.
 */
object AuthTokenRefresher {
    private const val TAG = "AuthTokenRefresher"

    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RequestMetadataInterceptor())
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val refreshService: BYSELApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.MARKET_REST_URL)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BYSELApiService::class.java)
    }

    private val lock = Any()

    /**
     * @param failedAccessToken When refreshing after a 401, pass the access token that failed
     * so a peer refresh that already updated the session can short-circuit without a second network call.
     * @return Fresh access token, or null if refresh could not produce one.
     */
    fun refreshBlocking(failedAccessToken: String? = null): String? {
        synchronized(lock) {
            val latestAccess = AuthSessionManager.getAccessToken()
            if (
                !latestAccess.isNullOrBlank() &&
                failedAccessToken != null &&
                latestAccess != failedAccessToken
            ) {
                return latestAccess
            }

            if (
                failedAccessToken == null &&
                !AuthSessionManager.isAccessTokenExpiringSoon()
            ) {
                return latestAccess
            }

            val refreshToken = AuthSessionManager.getRefreshToken() ?: return null

            return try {
                val refreshed = runBlocking {
                    refreshService.refreshToken(RefreshTokenRequest(refreshToken = refreshToken))
                }
                persist(refreshed)
                refreshed.access_token
            } catch (httpException: HttpException) {
                handleHttpFailure(httpException, refreshToken)
            } catch (error: Exception) {
                Log.w(TAG, "refresh failed (non-auth)", error)
                null
            }
        }
    }

    /** Convenience for coroutine callers (foreground resume). */
    suspend fun refreshIfNeeded(): String? = refreshBlocking(failedAccessToken = null)

    private fun persist(refreshed: AuthResponse) {
        AuthSessionManager.saveSession(
            accessToken = refreshed.access_token,
            refreshToken = refreshed.refresh_token,
            userId = refreshed.user_id,
            accessTokenTtlSeconds = refreshed.accessTtlSeconds(),
        )
    }

    private fun handleHttpFailure(httpException: HttpException, attemptedRefreshToken: String): String? {
        val detail = extractErrorDetail(httpException)
        val code = httpException.code()
        Log.w(TAG, "refresh HTTP $code detail=$detail")

        // Another in-flight refresh already rotated this token and saved new credentials.
        val currentRefresh = AuthSessionManager.getRefreshToken()
        val currentAccess = AuthSessionManager.getAccessToken()
        if (
            code == 401 &&
            isBenignRotation(detail) &&
            !currentRefresh.isNullOrBlank() &&
            currentRefresh != attemptedRefreshToken &&
            !currentAccess.isNullOrBlank()
        ) {
            return currentAccess
        }

        if (shouldClearSession(code, detail)) {
            Log.w(TAG, "clearing session after definitive refresh failure: $detail")
            AuthSessionManager.clearSession()
        }
        return null
    }

    private fun shouldClearSession(code: Int, detail: String?): Boolean {
        if (code != 401 && code != 403) return false
        if (isBenignRotation(detail)) return false
        val normalized = detail.orEmpty().lowercase()
        // Only wipe local session on definitive server revoke/expiry signals.
        // Unknown 401s (including race leftovers) must not force sign-out.
        return normalized.contains("expired") ||
            normalized.contains("revoked") ||
            normalized.contains("reuse detected") ||
            normalized.contains("session invalidated") ||
            normalized.contains("invalid refresh") ||
            normalized.contains("invalid credentials")
    }

    private fun isBenignRotation(detail: String?): Boolean {
        val normalized = detail.orEmpty().lowercase()
        return normalized.contains("already rotated") ||
            normalized.contains("replay")
    }

    private fun extractErrorDetail(httpException: HttpException): String? {
        return try {
            val raw = httpException.response()?.errorBody()?.use(ResponseBody::string).orEmpty()
            if (raw.isBlank()) return httpException.message()
            val json = JSONObject(raw)
            when {
                json.has("detail") -> {
                    val detail = json.get("detail")
                    if (detail is String) detail else detail.toString()
                }
                json.has("message") -> json.optString("message")
                else -> raw
            }
        } catch (_: Exception) {
            httpException.message()
        }
    }
}
