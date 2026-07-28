package com.bysel.trader.data.repository

import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.models.AuthResponse
import com.bysel.trader.data.models.ChangePasswordRequest
import com.bysel.trader.data.models.PasswordResetConfirmRequest
import com.bysel.trader.data.models.PasswordResetConfirmResponse
import com.bysel.trader.data.models.PasswordResetRequestBody
import com.bysel.trader.data.models.PasswordResetRequestResponse
import com.bysel.trader.data.models.AuthSessionItem
import com.bysel.trader.data.models.LoginRequest
import com.bysel.trader.data.models.SendOTPRequest
import com.bysel.trader.data.models.VerifyOTPRequest
import com.bysel.trader.data.models.FirebasePhoneAuthRequest
import com.bysel.trader.data.models.DeleteAccountRequest
import com.bysel.trader.data.models.OTPResponse
import com.bysel.trader.data.models.LogoutRequest
import com.bysel.trader.data.models.RefreshTokenRequest
import com.bysel.trader.data.models.RegisterRequest
import com.bysel.trader.data.models.UserProfile
import com.bysel.trader.data.models.UserProfileUpdateRequest
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import retrofit2.HttpException

private const val TAG = "AuthRepository"

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
                    ?: "Invalid username/email or password. Please verify credentials and try again."
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
            if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                AuthSessionManager.clearSession()
            }
            Result.Error(toAuthErrorMessage(e, "Session refresh failed"))
        }
    }

    suspend fun requestPasswordReset(identifier: String): Result<PasswordResetRequestResponse> {
        val normalizedIdentifier = identifier.trim()
        return try {
            val response = apiService.requestPasswordReset(
                PasswordResetRequestBody(identifier = normalizedIdentifier)
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Password reset request failed"))
        }
    }

    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<PasswordResetConfirmResponse> {
        val normalizedToken = token.trim().uppercase()
        return try {
            val response = apiService.confirmPasswordReset(
                PasswordResetConfirmRequest(token = normalizedToken, newPassword = newPassword)
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Password reset failed"))
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<AuthResponse> {
        return try {
            val response = apiService.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                )
            )
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id,
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Password update failed"))
        }
    }

    /**
     * Clears the device session. The local session and the Firebase phone-auth session are
     * always cleared, even when the server call fails, so the user is never stuck signed in.
     * A failed server revoke is still reported so the UI can say the session may remain
     * active elsewhere.
     */
    suspend fun logout(): Result<Unit> {
        var serverError: String? = null
        try {
            AuthSessionManager.getRefreshToken()?.let {
                apiService.logout(LogoutRequest(refreshToken = it))
            }
        } catch (e: Exception) {
            serverError = toAuthErrorMessage(e, "Sign-out could not be confirmed with the server")
        }
        clearLocalIdentity()
        return serverError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    suspend fun logoutAllDevices(): Result<Unit> {
        var serverError: String? = null
        try {
            apiService.logoutAllDevices()
        } catch (e: Exception) {
            serverError = toAuthErrorMessage(e, "Could not sign out other devices")
        }
        clearLocalIdentity()
        return serverError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    /**
     * Firebase keeps its own signed-in user after phone auth. Leaving it behind means the next
     * OTP sign-in can silently reuse the previous number.
     */
    private fun clearLocalIdentity() {
        AuthSessionManager.clearSession()
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-out failed", e)
        }
    }

    suspend fun deleteAccount(password: String): Result<Unit> {
        return try {
            apiService.deleteAccount(DeleteAccountRequest(password = password))
            AuthSessionManager.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Account deletion failed"))
        }
    }

    suspend fun sendOtp(mobileNumber: String): Result<OTPResponse> {
        val normalizedMobile = mobileNumber.trim()
        return try {
            val response = apiService.sendOTP(SendOTPRequest(mobileNumber = normalizedMobile))
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "OTP request failed"))
        }
    }

    suspend fun verifyOtp(mobileNumber: String, otp: String): Result<AuthResponse> {
        val normalizedMobile = mobileNumber.trim()
        val normalizedOtp = otp.trim()
        return try {
            val response = apiService.verifyOTP(
                VerifyOTPRequest(
                    mobileNumber = normalizedMobile,
                    otp = normalizedOtp
                )
            )
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "OTP verification failed"))
        }
    }

    suspend fun firebasePhoneAuth(firebaseIdToken: String): Result<AuthResponse> {
        if (firebaseIdToken.isBlank()) {
            return Result.Error("Empty authentication token")
        }
        return try {
            val response = apiService.firebasePhoneAuth(
                FirebasePhoneAuthRequest(firebaseIdToken = firebaseIdToken)
            )
            AuthSessionManager.saveSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                userId = response.user_id
            )
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Phone authentication failed"))
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

    suspend fun getCurrentUserProfile(): Result<com.bysel.trader.data.models.CurrentUserProfile> {
        return try {
            val response = apiService.getCurrentUserProfile()
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(toAuthErrorMessage(e, "Failed to load profile"))
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

    suspend fun getProfile(): Result<UserProfile> {
        return try {
            Result.Success(apiService.getProfile())
        } catch (primary: Exception) {
            // Older local backends only expose /auth/profile.
            if (primary is HttpException && primary.code() == 404) {
                try {
                    Result.Success(apiService.getProfileLegacy())
                } catch (e: Exception) {
                    Result.Error(toAuthErrorMessage(e, "Failed to load profile"))
                }
            } else {
                Result.Error(toAuthErrorMessage(primary, "Failed to load profile"))
            }
        }
    }

    suspend fun updateProfile(
        username: String,
        email: String,
        mobileNumber: String?
    ): Result<UserProfile> {
        val normalizedUsername = username.trim()
        val normalizedEmail = email.trim()
        val normalizedMobile = mobileNumber?.trim()?.takeIf { it.isNotEmpty() }
        val body = UserProfileUpdateRequest(
            username = normalizedUsername,
            email = normalizedEmail,
            mobileNumber = normalizedMobile,
        )

        return try {
            Result.Success(apiService.updateProfile(body))
        } catch (primary: Exception) {
            if (primary is HttpException && primary.code() == 404) {
                try {
                    Result.Success(apiService.updateProfileLegacy(body))
                } catch (e: Exception) {
                    Result.Error(toAuthErrorMessage(e, "Failed to update profile"))
                }
            } else {
                Result.Error(toAuthErrorMessage(primary, "Failed to update profile"))
            }
        }
    }
}
