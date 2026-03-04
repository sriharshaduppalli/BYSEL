package com.bysel.trader.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages biometric authentication (fingerprint/face unlock) for the app
 * Supports Android 6.0+ with graceful fallback
 */
class BiometricAuthManager(private val context: Context) {

    companion object {
        private const val PREF_KEY_BIOMETRIC_ENABLED = "biometric_auth_enabled"
        private const val PREF_NAME = "security_prefs"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Check if user has enabled biometric auth in app settings
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(PREF_KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Enable or disable biometric auth in app settings
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Show biometric authentication prompt
     * @param activity The activity to show the prompt in
     * @param title Title of the prompt dialog
     * @param subtitle Subtitle text (optional)
     * @param description Description text (optional)
     * @param onSuccess Callback when authentication succeeds
     * @param onError Callback when authentication fails or is cancelled
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Unlock to continue",
        description: String = "Use your fingerprint or face to unlock",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // User provided valid biometric but it wasn't recognized
                    // Don't call onError here - let them retry
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Quick authentication for app unlock (simplified version)
     */
    fun authenticateForAppUnlock(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        authenticate(
            activity = activity,
            title = "Unlock BYSEL",
            subtitle = "Authenticate to access your portfolio",
            description = "Use biometric or device credentials",
            onSuccess = onSuccess,
            onError = { errorCode, _ ->
                // User cancelled (error code 13) or locked out
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED) {
                    onCancel()
                } else {
                    // Other errors (e.g., too many attempts) - still call onCancel
                    onCancel()
                }
            }
        )
    }

    /**
     * Authentication for confirming sensitive operations (e.g., large trades)
     */
    fun authenticateForTransaction(
        activity: FragmentActivity,
        amount: Double,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        authenticate(
            activity = activity,
            title = "Confirm Transaction",
            subtitle = "Trade amount: ₹${String.format("%.2f", amount)}",
            description = "Authenticate to confirm this order",
            onSuccess = onSuccess,
            onError = { _, _ -> onCancel() }
        )
    }
}

/**
 * Status of biometric authentication availability
 */
enum class BiometricStatus {
    AVAILABLE,               // Biometric auth is available and enrolled
    NO_HARDWARE,            // Device doesn't have biometric hardware
    HARDWARE_UNAVAILABLE,   // Hardware is temporarily unavailable
    NONE_ENROLLED,          // No biometric credentials enrolled
    SECURITY_UPDATE_REQUIRED, // Device needs security update
    UNSUPPORTED,            // Biometric auth not supported
    UNKNOWN                 // Unknown status
}

/**
 * Extension function to get user-friendly message for biometric status
 */
fun BiometricStatus.getMessage(): String {
    return when (this) {
        BiometricStatus.AVAILABLE -> "Biometric authentication is available"
        BiometricStatus.NO_HARDWARE -> "This device doesn't support biometric authentication"
        BiometricStatus.HARDWARE_UNAVAILABLE -> "Biometric hardware is temporarily unavailable"
        BiometricStatus.NONE_ENROLLED -> "No fingerprint or face enrolled. Please set up biometric authentication in device settings"
        BiometricStatus.SECURITY_UPDATE_REQUIRED -> "Security update required to use biometric authentication"
        BiometricStatus.UNSUPPORTED -> "Biometric authentication is not supported on this device"
        BiometricStatus.UNKNOWN -> "Biometric authentication status is unknown"
    }
}
