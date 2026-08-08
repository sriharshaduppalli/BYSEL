package com.bysel.trader.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.bysel.trader.BuildConfig

data class SavedPasswordCredential(
    val id: String,
    val password: String,
)

/**
 * Credential Manager helpers for password save / one-tap restore.
 * Passkeys and Sign in with Google stay out of scope until backend support lands.
 */
object CredentialHelper {
    private const val TAG = "CredentialHelper"

    suspend fun savePassword(activity: Activity, id: String, password: String): Boolean {
        val trimmedId = id.trim()
        if (trimmedId.isBlank() || password.isBlank()) return false
        return try {
            val manager = CredentialManager.create(activity)
            manager.createCredential(
                context = activity,
                request = CreatePasswordRequest(id = trimmedId, password = password),
            )
            true
        } catch (_: CreateCredentialCancellationException) {
            false
        } catch (e: CreateCredentialException) {
            debugLog("savePassword skipped: ${e.message}")
            false
        } catch (e: Exception) {
            debugLog("savePassword failed: ${e.message}")
            false
        }
    }

    /**
     * @param preferImmediatelyAvailable When true (cold-start autofill), return quickly if no
     * local credential exists instead of waiting on remote password-manager discovery.
     * When false (user tapped "Use saved password"), allow the full account selector UI.
     */
    suspend fun loadPassword(
        context: Context,
        preferImmediatelyAvailable: Boolean = true,
    ): SavedPasswordCredential? {
        return try {
            val manager = CredentialManager.create(context)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetPasswordOption())
                .setPreferImmediatelyAvailableCredentials(preferImmediatelyAvailable)
                .build()
            val response = manager.getCredential(
                context = context,
                request = request,
            )
            val credential = response.credential
            if (credential is PasswordCredential) {
                SavedPasswordCredential(credential.id, credential.password)
            } else {
                null
            }
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            debugLog("loadPassword skipped: ${e.message}")
            null
        } catch (e: Exception) {
            debugLog("loadPassword failed: ${e.message}")
            null
        }
    }

    /**
     * Notify credential providers the user signed out so the next getCredential
     * shows the full account chooser instead of prioritizing a stale session.
     */
    suspend fun clearCredentialState(context: Context) {
        try {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Best-effort — local session is already cleared.
        } catch (e: Exception) {
            debugLog("clearCredentialState failed: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
