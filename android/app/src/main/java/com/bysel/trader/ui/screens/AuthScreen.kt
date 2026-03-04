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
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit
) {
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var isLoginMode by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BYSEL",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isLoginMode) "Sign in to continue" else "Create your account",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                message = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            isError = !message.isNullOrBlank() && username.trim().isEmpty(),
            enabled = !loading,
            singleLine = true
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
                singleLine = true
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
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!message.isNullOrBlank()) {
            Text(
                text = message.orEmpty(),
                color = MaterialTheme.colorScheme.error,
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
                    message = "Username is required"
                    return@Button
                }
                if (!isLoginMode && trimmedEmail.isEmpty()) {
                    message = "Email is required"
                    return@Button
                }
                if (!isLoginMode && !hasValidEmail) {
                    message = "Please enter a valid email address"
                    return@Button
                }
                if (password.isEmpty()) {
                    message = "Password is required"
                    return@Button
                }
                if (password.length < 6) {
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
                        is Result.Error -> message = result.message
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
        TextButton(
            onClick = {
                if (loading) return@TextButton
                isLoginMode = !isLoginMode
                message = null
                passwordVisible = false
            },
            enabled = !loading
        ) {
            Text(
                if (isLoginMode) "New user? Register" else "Already registered? Login",
                color = Color(0xFF7C4DFF)
            )
        }
    }
}
