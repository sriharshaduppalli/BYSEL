package com.bysel.trader.alerts

import android.util.Log
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.models.FcmTokenRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BYSEL-FCM"

/**
 * Registers the device FCM token with the BYSEL backend so price-alert
 * pushes can be delivered when the server evaluates a threshold.
 */
object FcmTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentToken() {
        if (!AuthSessionManager.hasSession()) return
        scope.launch { registerCurrentTokenSuspend() }
    }

    fun registerToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank() || !AuthSessionManager.hasSession()) return
        scope.launch { uploadToken(trimmed) }
    }

    suspend fun unregisterCurrentToken() {
        if (!AuthSessionManager.hasSession()) return
        val token = currentTokenOrEmpty()
        if (token.isBlank()) return
        runCatching {
            RetrofitClient.authApiService.unregisterFcmToken(FcmTokenRequest(token = token))
        }.onFailure { Log.w(TAG, "FCM token unregister failed", it) }
    }

    private suspend fun registerCurrentTokenSuspend() {
        val token = currentTokenOrEmpty()
        if (token.isBlank()) return
        uploadToken(token)
    }

    private suspend fun currentTokenOrEmpty(): String = withContext(Dispatchers.IO) {
        runCatching {
            Tasks.await(FirebaseMessaging.getInstance().token)?.trim().orEmpty()
        }.getOrElse { err ->
            Log.w(TAG, "Could not read FCM token", err)
            ""
        }
    }

    private suspend fun uploadToken(token: String) {
        runCatching {
            RetrofitClient.authApiService.registerFcmToken(
                FcmTokenRequest(token = token, platform = "android"),
            )
        }.onFailure { Log.w(TAG, "FCM token register failed", it) }
    }
}
