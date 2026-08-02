package com.bysel.trader.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

data class SavedPasswordCredential(
    val id: String,
    val password: String,
)

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
            Log.d(TAG, "savePassword skipped: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "savePassword failed: ${e.message}")
            false
        }
    }

    suspend fun loadPassword(context: Context): SavedPasswordCredential? {
        return try {
            val manager = CredentialManager.create(context)
            val response = manager.getCredential(
                context = context,
                request = GetCredentialRequest(listOf(GetPasswordOption())),
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
            Log.d(TAG, "loadPassword skipped: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "loadPassword failed: ${e.message}")
            null
        }
    }
}
