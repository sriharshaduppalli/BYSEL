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

class AuthRepository(
    private val apiService: BYSELApiService = RetrofitClient.apiService
) {
    suspend fun register(username: String, email: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.register(RegisterRequest(username = username, email = email, password = password))
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Registration failed")
        }
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.login(LoginRequest(username = username, password = password))
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Login failed")
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
            Result.Error(e.message ?: "Session refresh failed")
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
            Result.Error(e.message ?: "Failed to load sessions")
        }
    }

    suspend fun revokeSession(sessionId: Int): Result<Unit> {
        return try {
            apiService.revokeSession(sessionId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to revoke session")
        }
    }
}
