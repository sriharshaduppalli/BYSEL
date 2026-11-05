package com.bysel.trader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.bysel.trader.R
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bysel.trader.ui.components.rememberHideOnScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bysel.trader.data.models.Alert
import com.bysel.trader.ui.components.AlertCard
import com.bysel.trader.ui.components.LoadingScreen
import com.bysel.trader.ui.components.SwipeToDismissItem
import com.bysel.trader.ui.components.appFilledTextFieldColors
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.OverlayBackButton

@Composable
fun AlertsScreen(
    alerts: List<Alert>,
    isLoading: Boolean,
    onCreateAlert: (String, Double, String) -> Unit,
    onDeleteAlert: (Int) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    val hideOnScroll = rememberHideOnScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .nestedScroll(hideOnScroll.connection)
            .animateContentSize()
    ) {
        AnimatedVisibility(
            visible = hideOnScroll.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                OverlayBackButton(onBack = onBack)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.more_alerts),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = stringResource(R.string.more_alerts_sub),
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text(stringResource(R.string.alerts_new))
            }
        }
        }
        if (!hideOnScroll.expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.alerts_new), fontSize = 12.sp)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LoadingScreen()
            }
        } else if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.alerts_empty),
                        fontSize = 16.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.alerts_create_first))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(items = alerts, key = { it.id }) { alert ->
                    SwipeToDismissItem(
                        item = alert,
                        onDismiss = { onDeleteAlert(it.id) },
                        enabled = true
                    ) {
                        AlertCard(alert) { onDeleteAlert(alert.id) }
                    }
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

    val parsed = price.replace(",", "").toDoubleOrNull()
    val priceInvalid = price.isNotEmpty() && (parsed == null || parsed <= 0.0)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = LocalAppTheme.current.card,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.alerts_create_title),
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.Bold,
                    )
                    TextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text(stringResource(R.string.alerts_symbol_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFilledTextFieldColors(containerColor = LocalAppTheme.current.surface),
                    )
                    TextField(
                        value = price,
                        onValueChange = { price = it },
                        label = {
                            Text(
                                when (alertType) {
                                    "PCT_UP", "PCT_DOWN" -> "Percent"
                                    "VOLUME" -> "Shares"
                                    else -> stringResource(R.string.threshold)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        isError = priceInvalid,
                        supportingText = if (priceInvalid) {
                            { Text(stringResource(R.string.alerts_price_hint)) }
                        } else {
                            null
                        },
                        colors = appFilledTextFieldColors(containerColor = LocalAppTheme.current.surface),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { alertType = "ABOVE" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alertType == "ABOVE") LocalAppTheme.current.primary else LocalAppTheme.current.mutedSurface,
                                contentColor = if (alertType == "ABOVE") LocalAppTheme.current.onPrimary else LocalAppTheme.current.text,
                            ),
                        ) {
                            Text(stringResource(R.string.above))
                        }
                        Button(
                            onClick = { alertType = "BELOW" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alertType == "BELOW") LocalAppTheme.current.primary else LocalAppTheme.current.mutedSurface,
                                contentColor = if (alertType == "BELOW") LocalAppTheme.current.onPrimary else LocalAppTheme.current.text,
                            ),
                        ) {
                            Text(stringResource(R.string.below))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("PCT_UP" to "% up", "PCT_DOWN" to "% down", "VOLUME" to "Volume").forEach { (type, label) ->
                            Button(
                                onClick = { alertType = type },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (alertType == type) LocalAppTheme.current.primary else LocalAppTheme.current.mutedSurface,
                                    contentColor = if (alertType == type) LocalAppTheme.current.onPrimary else LocalAppTheme.current.text,
                                ),
                            ) {
                                Text(label, maxLines = 1, fontSize = 12.sp)
                            }
                        }
                    }
                    Text(
                        "Percent uses the delayed session move. Volume uses the delayed share count.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp,
                    )
                    Button(
                        enabled = symbol.isNotBlank() && parsed != null && parsed > 0.0,
                        onClick = {
                            val value = parsed ?: return@Button
                            onConfirm(symbol.trim(), value, alertType)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.create))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
