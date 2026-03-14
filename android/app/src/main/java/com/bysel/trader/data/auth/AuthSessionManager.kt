package com.bysel.trader.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthSessionManager {
    private const val PREFS_NAME = "bysel_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val _sessionState = MutableStateFlow(false)
    val sessionState: StateFlow<Boolean> = _sessionState.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    _sessionState.value = hasSession()
                }
            }
        }
    }

    fun saveSession(accessToken: String?, refreshToken: String?, userId: Int?) {
        val sharedPrefs = prefs ?: return
        with(sharedPrefs.edit()) {
            if (accessToken.isNullOrBlank()) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken.isNullOrBlank()) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, refreshToken)
            if (userId == null) remove(KEY_USER_ID) else putInt(KEY_USER_ID, userId)
            apply()
        }
        _sessionState.value = hasSession()
    }

    fun clearSession() {
        val sharedPrefs = prefs ?: return
        sharedPrefs.edit().clear().apply()
        _sessionState.value = false
    }

    fun getAccessToken(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH_TOKEN, null)

    fun getUserId(): Int? {
        val sharedPrefs = prefs ?: return null
        return if (sharedPrefs.contains(KEY_USER_ID)) sharedPrefs.getInt(KEY_USER_ID, -1).takeIf { it > 0 } else null
    }

    fun hasSession(): Boolean {
        return !getAccessToken().isNullOrBlank() || !getRefreshToken().isNullOrBlank()
    }
}
