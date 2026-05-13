package com.bysel.trader.ui.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun appOutlinedTextFieldColors(
    containerColor: Color = LocalAppTheme.current.card,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalAppTheme.current.text,
    unfocusedTextColor = LocalAppTheme.current.text,
    disabledTextColor = LocalAppTheme.current.textSecondary,
    focusedLabelColor = LocalAppTheme.current.primary,
    unfocusedLabelColor = LocalAppTheme.current.textSecondary,
    disabledLabelColor = LocalAppTheme.current.textSecondary,
    focusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    unfocusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    focusedBorderColor = LocalAppTheme.current.primary,
    unfocusedBorderColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.65f),
    disabledBorderColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.35f),
    cursorColor = LocalAppTheme.current.primary,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
)

@Composable
fun appFilledTextFieldColors(
    containerColor: Color = LocalAppTheme.current.card,
): TextFieldColors = TextFieldDefaults.colors(
    focusedTextColor = LocalAppTheme.current.text,
    unfocusedTextColor = LocalAppTheme.current.text,
    disabledTextColor = LocalAppTheme.current.textSecondary,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
    focusedLabelColor = LocalAppTheme.current.primary,
    unfocusedLabelColor = LocalAppTheme.current.textSecondary,
    disabledLabelColor = LocalAppTheme.current.textSecondary,
    focusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    unfocusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    focusedIndicatorColor = LocalAppTheme.current.primary,
    unfocusedIndicatorColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.65f),
    disabledIndicatorColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.35f),
    cursorColor = LocalAppTheme.current.primary,
)