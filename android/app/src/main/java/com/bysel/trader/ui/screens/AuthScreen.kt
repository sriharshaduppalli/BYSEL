package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.repository.AuthRepository
import com.bysel.trader.data.repository.Result
import com.bysel.trader.ui.theme.LocalAppTheme
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit
) {
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val appTheme = LocalAppTheme.current
    val context = LocalContext.current

    var isLoginMode by remember { mutableStateOf(true) }
    var isOtpMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sendingOtp by remember { mutableStateOf(false) }
    var verifyingOtp by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(true) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Firebase Phone Auth state
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var firebaseVerificationId by remember { mutableStateOf<String?>(null) }
    var firebaseResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpCountdown by remember { mutableStateOf(0) }

    // Activity (needed to launch User Consent intent)
    val activity = LocalContext.current as? Activity

    // Consent launcher to receive the single-SMS consent UI result
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val sms: String? = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            sms?.let {
                val regex = Regex("(?i)BYSEL.*?(?:verification code|OTP|code).*?(\\d{6})")
                val match = regex.find(it)
                val otp = match?.groupValues?.get(1)
                if (otp != null) {
                    otpCode = otp
                    message = "OTP auto-filled from SMS"
                    messageIsError = false
                }
            }
        }
    }

    // Register the SMS User Consent receiver. This does not require SMS permissions.
    DisposableEffect(Unit) {
        val smsRetrieverReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                    val extras = intent.extras
                    val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
                    when (status?.statusCode) {
                        CommonStatusCodes.SUCCESS -> {
                            val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                            try {
                                consentIntent?.let { consentLauncher.launch(it) }
                            } catch (e: ActivityNotFoundException) {
                                // Activity not found; ignore
                            }
                        }
                        CommonStatusCodes.TIMEOUT -> {
                            // timed out waiting for message
                        }
                    }
                }
            }
        }

        // Register receiver on the Activity so we can launch the consent UI
        // RECEIVER_NOT_EXPORTED is required on API 33+ (targetSdk 36) for non-system broadcasts
        activity?.let {
            ContextCompat.registerReceiver(
                it,
                smsRetrieverReceiver,
                IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        onDispose {
            try {
                activity?.unregisterReceiver(smsRetrieverReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            authRepository = authRepository,
            onDismiss = { showForgotPasswordDialog = false },
            onPasswordResetSuccess = { identifier, successMessage ->
                showForgotPasswordDialog = false
                isLoginMode = true
                username = identifier
                password = ""
                passwordVisible = false
                message = successMessage
                messageIsError = false
            }
        )
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appTheme.text,
        unfocusedTextColor = appTheme.text,
        disabledTextColor = appTheme.textSecondary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = appTheme.primary,
        unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.6f),
        focusedLabelColor = appTheme.primary,
        unfocusedLabelColor = appTheme.textSecondary,
        cursorColor = appTheme.primary,
        focusedTrailingIconColor = appTheme.textSecondary,
        unfocusedTrailingIconColor = appTheme.textSecondary,
        disabledTrailingIconColor = appTheme.textSecondary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BYSEL",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = appTheme.text
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isLoginMode) {
                if (isOtpMode) "Sign in with OTP" else "Sign in with username or email"
            } else "Create your account",
            color = appTheme.textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Only show username/email field when NOT in OTP mode
        if (!isOtpMode) {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username or Email") },
                isError = !message.isNullOrBlank() && username.trim().isEmpty(),
                enabled = !loading,
                singleLine = true,
                colors = textFieldColors
            )

            if (!isLoginMode) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    isError = !message.isNullOrBlank() && email.trim().isEmpty(),
                    enabled = !loading,
                    singleLine = true,
                    colors = textFieldColors
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        if (isLoginMode && isOtpMode) {
            OutlinedTextField(
                value = mobileNumber,
                onValueChange = {
                    mobileNumber = it
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mobile number") },
                isError = !message.isNullOrBlank() && mobileNumber.trim().isEmpty(),
                enabled = !sendingOtp && !verifyingOtp,
                singleLine = true,
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (otpSent) {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = {
                        otpCode = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OTP code") },
                    isError = !message.isNullOrBlank() && otpCode.trim().isEmpty(),
                    enabled = !sendingOtp && !verifyingOtp,
                    singleLine = true,
                    colors = textFieldColors
                )

                Text(
                    text = "Enter the 6-digit code sent to your phone by Firebase.",
                    color = appTheme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    if (sendingOtp) return@Button

                    val rawNumber = mobileNumber.trim()
                    if (rawNumber.isEmpty()) {
                        messageIsError = true
                        message = "Mobile number is required"
                        return@Button
                    }

                    // Normalize to E.164 format for Firebase
                    val phoneE164 = when {
                        rawNumber.startsWith("+") && rawNumber.length >= 10 -> rawNumber
                        rawNumber.startsWith("91") && rawNumber.length == 12 -> "+$rawNumber"
                        rawNumber.length == 10 && rawNumber.all { it.isDigit() } -> "+91$rawNumber"
                        else -> {
                            sendingOtp = false
                            messageIsError = true
                            message = "Invalid phone format. Use 10 digits or +91XXXXXXXXXX"
                            return@Button
                        }
                    }

                    sendingOtp = true
                    message = null

                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                            // Auto-verification (instant verification or auto-retrieval)
                            Log.d("AuthScreen", "Firebase auto-verified phone")
                            scope.launch {
                                verifyingOtp = true
                                sendingOtp = false
                                try {
                                    val authResult = firebaseAuth.signInWithCredential(credential).await()
                                    val idToken = authResult.user?.getIdToken(false)?.await()?.token
                                    if (idToken != null) {
                                        val result = authRepository.firebasePhoneAuth(idToken)
                                        verifyingOtp = false
                                        when (result) {
                                            is Result.Success -> onAuthenticated()
                                            is Result.Error -> {
                                                messageIsError = true
                                                message = result.message
                                            }
                                            else -> Unit
                                        }
                                    } else {
                                        verifyingOtp = false
                                        messageIsError = true
                                        message = "Failed to get authentication token"
                                    }
                                } catch (e: Exception) {
                                    verifyingOtp = false
                                    messageIsError = true
                                    message = "Auto-verification failed: ${e.localizedMessage}"
                                }
                            }
                        }

                        override fun onVerificationFailed(e: FirebaseException) {
                            sendingOtp = false
                            messageIsError = true
                            message = e.localizedMessage ?: "Phone verification failed"
                            Log.e("AuthScreen", "Firebase verification failed", e)
                        }

                        override fun onCodeSent(
                            verificationId: String,
                            token: PhoneAuthProvider.ForceResendingToken
                        ) {
                            sendingOtp = false
                            firebaseVerificationId = verificationId
                            firebaseResendToken = token
                            otpSent = true
                            otpCountdown = 60
                            messageIsError = false
                            message = "OTP sent to $phoneE164"
                        }
                    }

                    val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
                        .setPhoneNumber(phoneE164)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setCallbacks(callbacks)

                    if (activity != null) {
                        optionsBuilder.setActivity(activity)
                    }

                    // Use resend token if available (for resend)
                    firebaseResendToken?.let { optionsBuilder.setForceResendingToken(it) }

                    try {
                        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
                    } catch (e: Exception) {
                        sendingOtp = false
                        messageIsError = true
                        message = "Failed to start verification: ${e.localizedMessage}"
                        Log.e("AuthScreen", "PhoneAuthProvider.verifyPhoneNumber error", e)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !sendingOtp && !verifyingOtp
            ) {
                if (sendingOtp) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
                } else {
                    Text(if (otpSent) "Resend OTP" else "Send OTP")
                }
            }

            if (otpSent) {
                // OTP countdown timer
                LaunchedEffect(otpCountdown) {
                    if (otpCountdown > 0) {
                        delay(1000L)
                        otpCountdown--
                    }
                }
                if (otpCountdown > 0) {
                    Text(
                        text = "Code expires in ${otpCountdown / 60}:${String.format("%02d", otpCountdown % 60)}",
                        color = if (otpCountdown < 15) MaterialTheme.colorScheme.error else appTheme.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (verifyingOtp) return@Button
                        if (otpCode.trim().isEmpty()) {
                            messageIsError = true
                            message = "Enter the OTP code"
                            return@Button
                        }
                        if (otpCode.trim().length != 6 || !otpCode.trim().all { it.isDigit() }) {
                            messageIsError = true
                            message = "OTP must be exactly 6 digits"
                            return@Button
                        }
                        val vId = firebaseVerificationId
                        if (vId == null) {
                            messageIsError = true
                            message = "Please request a new OTP first"
                            return@Button
                        }

                        verifyingOtp = true
                        message = null

                        val credential = PhoneAuthProvider.getCredential(vId, otpCode.trim())
                        scope.launch {
                            try {
                                val authResult = firebaseAuth.signInWithCredential(credential).await()
                                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                                if (idToken != null) {
                                    val result = authRepository.firebasePhoneAuth(idToken)
                                    verifyingOtp = false
                                    when (result) {
                                        is Result.Success -> onAuthenticated()
                                        is Result.Error -> {
                                            messageIsError = true
                                            message = result.message
                                        }
                                        else -> Unit
                                    }
                                } else {
                                    verifyingOtp = false
                                    messageIsError = true
                                    message = "Failed to get authentication token"
                                }
                            } catch (e: Exception) {
                                verifyingOtp = false
                                messageIsError = true
                                message = "Verification failed: ${e.localizedMessage}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !sendingOtp && !verifyingOtp
                ) {
                    if (verifyingOtp) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
                    } else {
                        Text("Verify OTP")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                isError = !message.isNullOrBlank() && password.isEmpty(),
                enabled = !loading,
                singleLine = true,
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!message.isNullOrBlank()) {
            Text(
                text = message.orEmpty(),
                color = if (messageIsError) MaterialTheme.colorScheme.error else appTheme.positive,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Only show login/register button when NOT in OTP mode
        if (!isOtpMode) {
            Button(
                onClick = {
                    if (loading) return@Button
                    val trimmedUsername = username.trim()
                    val trimmedEmail = email.trim()
                    val hasValidEmail = trimmedEmail.contains("@") && trimmedEmail.contains(".")

                    if (trimmedUsername.isEmpty()) {
                        messageIsError = true
                        message = "Username or email is required"
                        return@Button
                    }
                    if (!isLoginMode && trimmedEmail.isEmpty()) {
                        messageIsError = true
                        message = "Email is required"
                        return@Button
                    }
                    if (!isLoginMode && !hasValidEmail) {
                        messageIsError = true
                        message = "Please enter a valid email address"
                        return@Button
                    }
                    if (password.isEmpty()) {
                        messageIsError = true
                        message = "Password is required"
                        return@Button
                    }
                    if (!isLoginMode && password.length < 6) {
                        messageIsError = true
                        message = "Password must be at least 6 characters"
                        return@Button
                    }

                    loading = true
                    message = null

                    scope.launch {
                        val result = if (isLoginMode) {
                            authRepository.login(trimmedUsername, password)
                        } else {
                            authRepository.register(trimmedUsername, trimmedEmail, password)
                        }

                        loading = false
                        when (result) {
                            is Result.Success -> onAuthenticated()
                            is Result.Error -> {
                                messageIsError = true
                                message = result.message
                            }
                            else -> Unit
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.height(20.dp)
                    )
                } else {
                    Text(if (isLoginMode) "Login" else "Register")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (isLoginMode) {
            TextButton(
                onClick = {
                    if (loading) return@TextButton
                    isOtpMode = !isOtpMode
                    message = null
                    messageIsError = true
                    otpSent = false
                    otpCode = ""
                    mobileNumber = ""
                    password = ""
                },
                enabled = !loading,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    if (isOtpMode) "Sign in with password" else "Sign in with OTP",
                    color = appTheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!isOtpMode) {
                TextButton(
                    onClick = {
                        if (!loading) {
                            showForgotPasswordDialog = true
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Forgot password?", color = appTheme.primary)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        TextButton(
            onClick = {
                if (loading) return@TextButton
                isLoginMode = !isLoginMode
                message = null
                messageIsError = true
                passwordVisible = false
            },
            enabled = !loading
        ) {
            Text(
                if (isLoginMode) "New user? Register" else "Already registered? Login",
                color = appTheme.primary
            )
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
    onPasswordResetSuccess: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appTheme = LocalAppTheme.current

    var identifier by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var awaitingResetConfirmation by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var debugCode by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!loading) onDismiss()
        },
        containerColor = appTheme.card,
        title = {
            Text(
                text = if (awaitingResetConfirmation) "Set a new password" else "Forgot password",
                color = appTheme.text,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                if (!awaitingResetConfirmation) {
                    Text(
                        text = "Enter your username or registered email. We will send a reset code if the account exists.",
                        color = appTheme.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = {
                            identifier = it
                            feedback = null
                        },
                        label = { Text("Username or Email") },
                        enabled = !loading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appTheme.text,
                            unfocusedTextColor = appTheme.text,
                            disabledTextColor = appTheme.textSecondary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = appTheme.primary,
                            unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.6f),
                            focusedLabelColor = appTheme.primary,
                            unfocusedLabelColor = appTheme.textSecondary,
                            cursorColor = appTheme.primary,
                        )
                    )
                } else {
                    Text(
                        text = "Enter the reset code and choose a new password.",
                        color = appTheme.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetCode,
                        onValueChange = {
                            resetCode = it.uppercase()
                            feedback = null
                        },
                        label = { Text("Reset Code") },
                        enabled = !loading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appTheme.text,
                            unfocusedTextColor = appTheme.text,
                            disabledTextColor = appTheme.textSecondary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = appTheme.primary,
                            unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.6f),
                            focusedLabelColor = appTheme.primary,
                            unfocusedLabelColor = appTheme.textSecondary,
                            cursorColor = appTheme.primary,
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            feedback = null
                        },
                        label = { Text("New Password") },
                        enabled = !loading,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appTheme.text,
                            unfocusedTextColor = appTheme.text,
                            disabledTextColor = appTheme.textSecondary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = appTheme.primary,
                            unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.6f),
                            focusedLabelColor = appTheme.primary,
                            unfocusedLabelColor = appTheme.textSecondary,
                            cursorColor = appTheme.primary,
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            feedback = null
                        },
                        label = { Text("Confirm New Password") },
                        enabled = !loading,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appTheme.text,
                            unfocusedTextColor = appTheme.text,
                            disabledTextColor = appTheme.textSecondary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = appTheme.primary,
                            unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.6f),
                            focusedLabelColor = appTheme.primary,
                            unfocusedLabelColor = appTheme.textSecondary,
                            cursorColor = appTheme.primary,
                        )
                    )
                }

                if (com.bysel.trader.BuildConfig.DEBUG) {
                    debugCode?.let { code ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Debug reset code: $code",
                            color = appTheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (!feedback.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = feedback.orEmpty(),
                        color = if (feedbackIsError) MaterialTheme.colorScheme.error else appTheme.positive,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loading) return@Button

                    if (!awaitingResetConfirmation) {
                        val normalizedIdentifier = identifier.trim()
                        if (normalizedIdentifier.isEmpty()) {
                            feedbackIsError = true
                            feedback = "Username or email is required"
                            return@Button
                        }

                        loading = true
                        feedback = null
                        scope.launch {
                            when (val result = authRepository.requestPasswordReset(normalizedIdentifier)) {
                                is Result.Success -> {
                                    val response = result.data
                                    debugCode = response.resetCode
                                    resetCode = response.resetCode ?: resetCode
                                    feedbackIsError = false
                                    feedback = response.message
                                    if (response.delivery != "support") {
                                        awaitingResetConfirmation = true
                                    }
                                }
                                is Result.Error -> {
                                    feedbackIsError = true
                                    feedback = result.message
                                }
                                else -> Unit
                            }
                            loading = false
                        }
                    } else {
                        val normalizedCode = resetCode.trim().uppercase()
                        if (normalizedCode.isEmpty()) {
                            feedbackIsError = true
                            feedback = "Reset code is required"
                            return@Button
                        }
                        if (newPassword.length < 6) {
                            feedbackIsError = true
                            feedback = "Password must be at least 6 characters"
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            feedbackIsError = true
                            feedback = "Passwords do not match"
                            return@Button
                        }

                        loading = true
                        feedback = null
                        scope.launch {
                            when (val result = authRepository.confirmPasswordReset(normalizedCode, newPassword)) {
                                is Result.Success -> {
                                    onPasswordResetSuccess(identifier.trim(), result.data.message)
                                }
                                is Result.Error -> {
                                    feedbackIsError = true
                                    feedback = result.message
                                }
                                else -> Unit
                            }
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (awaitingResetConfirmation) "Update Password" else "Send Code")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!loading) onDismiss()
                },
                enabled = !loading,
            ) {
                Text("Cancel", color = appTheme.textSecondary)
            }
        }
    )
}
