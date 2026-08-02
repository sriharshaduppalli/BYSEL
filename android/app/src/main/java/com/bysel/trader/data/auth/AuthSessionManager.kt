package com.bysel.trader.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthSessionManager {
    private const val PREFS_NAME = "bysel_auth_encrypted"
    private const val LEGACY_PREFS_NAME = "bysel_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_MOBILE = "mobile_number"
    private const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry_ms"
    // Proactively refresh when less than 5 minutes remain
    private const val EXPIRY_BUFFER_MS = 5 * 60 * 1000L

    @Volatile
    private var prefs: SharedPreferences? = null
    private val _sessionState = MutableStateFlow(false)
    val sessionState: StateFlow<Boolean> = _sessionState.asStateFlow()

    data class CachedIdentity(
        val userId: Int?,
        val username: String?,
        val email: String?,
        val mobileNumber: String? = null,
    ) {
        fun displayLabel(): String {
            val name = username?.takeIf { it.isNotBlank() }
            val mail = email?.takeIf { it.isNotBlank() && !it.contains("@bysel.com", ignoreCase = true) }
            val mobile = mobileNumber?.takeIf { it.isNotBlank() }
            return when {
                name != null && mail != null && !name.equals(mail, ignoreCase = true) -> "$name · $mail"
                name != null -> name
                mail != null -> mail
                mobile != null -> mobile
                userId != null -> "User #$userId"
                else -> "View and edit profile"
            }
        }
    }

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = try {
                        val masterKey = MasterKey.Builder(context.applicationContext)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build()
                        EncryptedSharedPreferences.create(
                            context.applicationContext,
                            PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        )
                    } catch (e: Exception) {
                        Log.w("AuthSessionManager", "Encrypted prefs failed, falling back to standard", e)
                        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    }
                    migrateLegacyPrefs(context)
                    _sessionState.value = hasSession()
                }
            }
        }
    }

    private fun migrateLegacyPrefs(context: Context) {
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyToken = legacy.getString(KEY_ACCESS_TOKEN, null)
        if (legacyToken != null) {
            saveSession(
                accessToken = legacyToken,
                refreshToken = legacy.getString(KEY_REFRESH_TOKEN, null),
                userId = if (legacy.contains(KEY_USER_ID)) legacy.getInt(KEY_USER_ID, -1).takeIf { it > 0 } else null
            )
            legacy.edit().clear().apply()
        }
    }

    fun saveSession(accessToken: String?, refreshToken: String?, userId: Int?, accessTokenTtlSeconds: Int = 7200) {
        val sharedPrefs = prefs ?: return
        val expiryMs = if (!accessToken.isNullOrBlank()) System.currentTimeMillis() + (accessTokenTtlSeconds * 1000L) else 0L
        // commit() so concurrent refresh/auth threads always see the latest tokens.
        with(sharedPrefs.edit()) {
            if (accessToken.isNullOrBlank()) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken.isNullOrBlank()) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, refreshToken)
            if (userId == null) remove(KEY_USER_ID) else putInt(KEY_USER_ID, userId)
            if (expiryMs > 0L) putLong(KEY_ACCESS_TOKEN_EXPIRY, expiryMs) else remove(KEY_ACCESS_TOKEN_EXPIRY)
            commit()
        }
        _sessionState.value = hasSession()
    }

    fun saveProfileIdentity(
        username: String?,
        email: String?,
        userId: Int? = null,
        mobileNumber: String? = null,
    ) {
        val sharedPrefs = prefs ?: return
        with(sharedPrefs.edit()) {
            if (username.isNullOrBlank()) remove(KEY_USERNAME) else putString(KEY_USERNAME, username.trim())
            if (email.isNullOrBlank()) remove(KEY_EMAIL) else putString(KEY_EMAIL, email.trim().lowercase())
            if (mobileNumber != null) {
                if (mobileNumber.isBlank()) remove(KEY_MOBILE) else putString(KEY_MOBILE, mobileNumber.trim())
            }
            if (userId != null && userId > 0) putInt(KEY_USER_ID, userId)
            commit()
        }
    }

    fun getCachedIdentity(): CachedIdentity {
        return CachedIdentity(
            userId = getUserId(),
            username = prefs?.getString(KEY_USERNAME, null),
            email = prefs?.getString(KEY_EMAIL, null),
            mobileNumber = prefs?.getString(KEY_MOBILE, null),
        )
    }

    fun getCachedUsername(): String? = prefs?.getString(KEY_USERNAME, null)

    fun getCachedEmail(): String? = prefs?.getString(KEY_EMAIL, null)

    fun getCachedMobileNumber(): String? = prefs?.getString(KEY_MOBILE, null)

    fun clearSession() {
        val sharedPrefs = prefs ?: return
        sharedPrefs.edit().clear().commit()
        _sessionState.value = false
    }

    fun getAccessToken(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH_TOKEN, null)

    fun isAccessTokenExpiringSoon(): Boolean {
        val expiryMs = prefs?.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L) ?: return false
        if (expiryMs == 0L) return false
        return System.currentTimeMillis() >= expiryMs - EXPIRY_BUFFER_MS
    }

    fun getUserId(): Int? {
        val sharedPrefs = prefs ?: return null
        return if (sharedPrefs.contains(KEY_USER_ID)) sharedPrefs.getInt(KEY_USER_ID, -1).takeIf { it > 0 } else null
    }

    fun hasSession(): Boolean {
        return !getAccessToken().isNullOrBlank() || !getRefreshToken().isNullOrBlank()
    }
}
