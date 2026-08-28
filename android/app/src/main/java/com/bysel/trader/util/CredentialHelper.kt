package com.bysel.trader.util

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import com.bysel.trader.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class SavedPasswordCredential(
    val id: String,
    val password: String,
)

/**
 * Credential Manager helpers for password save / one-tap restore and Play
 * Restore Credentials (silent sign-in after a new-phone transfer).
 */
object CredentialHelper {
    private const val TAG = "CredentialHelper"
    private const val RESTORE_RP_ID = "bysel.app"
    private const val RESTORE_RP_NAME = "BYSEL"

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
     * Store a restore key so Google can transfer signed-in identity to a new phone.
     * The key is the server-issued restore token, not the JWT.
     */
    suspend fun saveRestoreCredential(
        context: Context,
        restoreToken: String,
        accountLabel: String,
    ): Boolean {
        val token = restoreToken.trim()
        if (token.isBlank()) return false
        val requestJson = buildCreateRestoreJson(
            restoreToken = token,
            accountLabel = accountLabel.ifBlank { "BYSEL" },
        )
        val manager = CredentialManager.create(context)
        return try {
            manager.createCredential(
                context = context,
                request = CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = true),
            )
            true
        } catch (_: E2eeUnavailableException) {
            try {
                manager.createCredential(
                    context = context,
                    request = CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = false),
                )
                true
            } catch (e: Exception) {
                debugLog("saveRestoreCredential local-only failed: ${e.message}")
                false
            }
        } catch (_: CreateCredentialCancellationException) {
            false
        } catch (e: CreateCredentialException) {
            debugLog("saveRestoreCredential skipped: ${e.message}")
            false
        } catch (e: Exception) {
            debugLog("saveRestoreCredential failed: ${e.message}")
            false
        }
    }

    suspend fun loadRestoreCredential(context: Context): String? {
        return try {
            val manager = CredentialManager.create(context)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetRestoreCredentialOption(buildGetRestoreJson()))
                .setPreferImmediatelyAvailableCredentials(true)
                .build()
            val response = manager.getCredential(context = context, request = request)
            extractRestoreToken(response.credential)
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            debugLog("loadRestoreCredential skipped: ${e.message}")
            null
        } catch (e: Exception) {
            debugLog("loadRestoreCredential failed: ${e.message}")
            null
        }
    }

    /**
     * Notify credential providers the user signed out so the next getCredential
     * shows the full account chooser instead of prioritizing a stale session.
     */
    suspend fun clearCredentialState(context: Context) {
        val manager = CredentialManager.create(context)
        try {
            manager.clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL),
            )
        } catch (_: ClearCredentialException) {
            // Best-effort — local session is already cleared.
        } catch (e: Exception) {
            debugLog("clearRestoreCredential failed: ${e.message}")
        }
        try {
            manager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Best-effort — local session is already cleared.
        } catch (e: Exception) {
            debugLog("clearCredentialState failed: ${e.message}")
        }
    }

    private fun buildCreateRestoreJson(restoreToken: String, accountLabel: String): String {
        val userIdB64 = Base64.encodeToString(
            restoreToken.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return JSONObject().apply {
            put("challenge", randomChallenge())
            put("rp", JSONObject().apply {
                put("name", RESTORE_RP_NAME)
                put("id", RESTORE_RP_ID)
            })
            put("user", JSONObject().apply {
                put("id", userIdB64)
                put("name", accountLabel)
                put("displayName", accountLabel)
            })
            put("pubKeyCredParams", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -7)
                })
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -257)
                })
            })
            put("timeout", 1_800_000)
            put("attestation", "none")
            put("excludeCredentials", JSONArray())
            put("authenticatorSelection", JSONObject().apply {
                put("residentKey", "required")
                put("userVerification", "required")
            })
        }.toString()
    }

    private fun buildGetRestoreJson(): String {
        return JSONObject().apply {
            put("challenge", randomChallenge())
            put("rpId", RESTORE_RP_ID)
            put("timeout", 1_800_000)
            put("userVerification", "required")
        }.toString()
    }

    private fun extractRestoreToken(credential: androidx.credentials.Credential): String? {
        val json = when (credential) {
            is RestoreCredential -> credential.authenticationResponseJson
            is CustomCredential -> {
                if (credential.type == RestoreCredential.TYPE_RESTORE_CREDENTIAL) {
                    credential.data.getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON")
                        ?: credential.data.getString("androidx.credentials.BUNDLE_KEY_GET_CREDENTIAL_RESPONSE")
                } else {
                    null
                }
            }
            else -> null
        } ?: return null
        return parseRestoreTokenFromAssertion(json)
    }

    internal fun parseRestoreTokenFromAssertion(authenticationJson: String): String? {
        return try {
            val root = JSONObject(authenticationJson)
            val response = root.optJSONObject("response") ?: JSONObject()
            val handle = response.optString("userHandle").ifBlank {
                root.optJSONObject("user")?.optString("id").orEmpty()
            }
            decodeUserHandle(handle)
        } catch (e: Exception) {
            debugLog("parseRestoreToken failed: ${e.message}")
            null
        }
    }

    private fun decodeUserHandle(handle: String): String? {
        val trimmed = handle.trim()
        if (trimmed.isBlank()) return null
        val decoded = runCatching {
            val padded = trimmed + "=".repeat((4 - trimmed.length % 4) % 4)
            String(
                Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
        }.getOrNull()
        return when {
            !decoded.isNullOrBlank() && decoded.length >= 16 -> decoded
            trimmed.length >= 16 -> trimmed
            else -> null
        }
    }

    private fun randomChallenge(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
