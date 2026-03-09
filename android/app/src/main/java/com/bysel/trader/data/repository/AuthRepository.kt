package com.bysel.trader.data.repository

import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.models.AuthResponse
import com.bysel.trader.data.models.AuthSessionItem
import com.bysel.trader.data.models.LoginRequest
import com.bysel.trader.data.models.LogoutRequest
import com.bysel.trader.data.models.RefreshTokenRequest
import com.bysel.trader.data.models.RegisterRequest
import org.json.JSONObject
import retrofit2.HttpException

class AuthRepository(
    private val apiService: BYSELApiService = RetrofitClient.apiService
) {
    private fun toAuthErrorMessage(exception: Exception, fallback: String): String {
        if (exception is HttpException) {
            val detail = runCatching {
                exception.response()
                    ?.errorBody()
                    ?.string()
                    ?.let { body -> JSONObject(body).optString("detail") }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

            return when (exception.code()) {
                401 -> detail
                    ?: "Invalid username or password. If this is your first login, please register first."
                else -> detail ?: (exception.message ?: fallback)
            }
        }

        return exception.message ?: fallback
    }

    suspend fun register(username: String, email: String, password: String): Result<AuthResponse> {
        val normalizedUsername = username.trim()
        val normalizedEmail = email.trim()
        return try {
            val response = apiService.register(
                RegisterRequest(
                    username = normalizedUsername,
                    email = normalizedEmail,
                    password = password,
                )
            )
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Registration failed"))
        }
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        val normalizedUsername = username.trim()
        val trimmedPassword = password.trim()
        return try {
            val response = apiService.login(LoginRequest(username = normalizedUsername, password = password))
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (firstAttemptError: Exception) {
            if (trimmedPassword != password) {
                try {
                    val retryResponse = apiService.login(
                        LoginRequest(
                            username = normalizedUsername,
                            password = trimmedPassword,
                        )
                    )
                    AuthSessionManager.saveSession(
                        accessToken = retryResponse.access_token,
                        refreshToken = retryResponse.refresh_token,
                        userId = retryResponse.user_id
                    )
                    return Result.Success(retryResponse)
                } catch (_: Exception) {
                    // Fall through to canonical error for the original login attempt.
                }
            }

            Result.Error(toAuthErrorMessage(firstAttemptError, "Login failed"))
        }
    }

    suspend fun refreshSession(): Result<AuthResponse> {
        val refreshToken = AuthSessionManager.getRefreshToken()
            ?: return Result.Error("No refresh token found")

        return try {
            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken = refreshToken))
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            AuthSessionManager.clearSession()
            Result.Error(toAuthErrorMessage(e, "Session refresh failed"))
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            AuthSessionManager.getRefreshToken()?.let {
                apiService.logout(LogoutRequest(refreshToken = it))
            }
            AuthSessionManager.clearSession()
            Result.Success(Unit)
        } catch (_: Exception) {
            AuthSessionManager.clearSession()
            Result.Success(Unit)
        }
    }

    suspend fun logoutAllDevices(): Result<Unit> {
        return try {
            apiService.logoutAllDevices()
            AuthSessionManager.clearSession()
            Result.Success(Unit)
        } catch (_: Exception) {
            AuthSessionManager.clearSession()
            Result.Success(Unit)
        }
    }

    suspend fun getActiveSessions(): Result<List<AuthSessionItem>> {
        return try {
            val response = apiService.getActiveSessions()
            Result.Success(response.sessions)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Failed to load sessions"))
        }
    }

    suspend fun revokeSession(sessionId: Int): Result<Unit> {
        return try {
            apiService.revokeSession(sessionId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Failed to revoke session"))
        }
    }
}
