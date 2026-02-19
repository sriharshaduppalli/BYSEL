package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Alert
import com.bysel.trader.ui.components.AlertCard
import com.bysel.trader.ui.components.LoadingScreen
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun AlertsScreen(
    alerts: List<Alert>,
    isLoading: Boolean,
    onCreateAlert: (String, Double, String) -> Unit,
    onDeleteAlert: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Price Alerts",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text
            )
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text("+ New Alert")
            }
        }

        if (isLoading) {
            LoadingScreen()
        } else if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No alerts set",
                    fontSize = 16.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(alerts) { alert ->
                    AlertCard(alert) { onDeleteAlert(alert.id) }
                }
            }
        }
    }

    if (showDialog) {
        CreateAlertDialog(
            onDismiss = { showDialog = false },
            onConfirm = { symbol, price, type ->
                onCreateAlert(symbol, price, type)
                showDialog = false
            }
        )
    }
}

@Composable
fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf("ABOVE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Price Alert", color = LocalAppTheme.current.text) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppTheme.current.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol (e.g., TCS)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF333333),
                        unfocusedContainerColor = Color(0xFF333333),
                        focusedTextColor = LocalAppTheme.current.text,
                        unfocusedTextColor = LocalAppTheme.current.text
                    )
                )

                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Threshold Price") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF333333),
                        unfocusedContainerColor = Color(0xFF333333),
                        focusedTextColor = LocalAppTheme.current.text,
                        unfocusedTextColor = LocalAppTheme.current.text
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { alertType = "ABOVE" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alertType == "ABOVE") LocalAppTheme.current.primary else Color(0xFF333333)
                        )
                    ) {
                        Text("Above")
                    }
                    Button(
                        onClick = { alertType = "BELOW" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alertType == "BELOW") LocalAppTheme.current.primary else Color(0xFF333333)
                        )
                    ) {
                        Text("Below")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (symbol.isNotEmpty() && price.isNotEmpty()) {
                        onConfirm(symbol, price.toDoubleOrNull() ?: 0.0, alertType)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = LocalAppTheme.current.card
    )
}
