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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.repository.AuthRepository
import com.bysel.trader.data.repository.Result
import com.bysel.trader.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit
) {
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val appTheme = LocalAppTheme.current

    var isLoginMode by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(true) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

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
            text = if (isLoginMode) "Sign in with username or email" else "Create your account",
            color = appTheme.textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

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

        if (!message.isNullOrBlank()) {
            Text(
                text = message.orEmpty(),
                color = if (messageIsError) MaterialTheme.colorScheme.error else appTheme.positive,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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

        Spacer(modifier = Modifier.height(10.dp))
        if (isLoginMode) {
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

                debugCode?.let { code ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Debug reset code: $code",
                        color = appTheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
