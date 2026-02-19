package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.MarketStatus

@Composable
fun TradingScreen(
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onAddFunds: (Double) -> Unit,
    onErrorDismiss: () -> Unit
) {
    var selectedQuote by remember { mutableStateOf<Quote?>(null) }
    var showAddFundsDialog by remember { mutableStateOf(false) }

    if (selectedQuote != null) {
        TradeDialog(
            quote = selectedQuote!!,
            walletBalance = walletBalance,
            marketStatus = marketStatus,
            onDismiss = { selectedQuote = null },
            onBuy = { quantity ->
                onBuy(selectedQuote!!.symbol, quantity)
                selectedQuote = null
            },
            onSell = { quantity ->
                onSell(selectedQuote!!.symbol, quantity)
                selectedQuote = null
            }
        )
    }

    if (showAddFundsDialog) {
        AddFundsDialog(
            onDismiss = { showAddFundsDialog = false },
            onAdd = { amount ->
                onAddFunds(amount)
                showAddFundsDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trading Market",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Refresh", fontSize = 12.sp)
            }
        }

        // Market Status Banner
        if (marketStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (marketStatus.isOpen) Color(0xFF1B5E20).copy(alpha = 0.3f)
                    else Color(0xFFB71C1C).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (marketStatus.isOpen) "\u2B24" else "\u2B24",
                            fontSize = 10.sp,
                            color = if (marketStatus.isOpen) Color(0xFF00E676) else Color(0xFFFF5252),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = marketStatus.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (marketStatus.isOpen) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                    }
                }
            }
        }

        // Wallet Balance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "Wallet Balance",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "\u20B9${String.format("%,.2f", walletBalance)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Button(
                    onClick = { showAddFundsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("+ Add Funds", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (error != null) {
            Snackbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onErrorDismiss) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.Blue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(quotes) { quote ->
                    TradingQuoteCard(quote) {
                        selectedQuote = quote
                    }
                }
            }
        }
    }
}

@Composable
fun TradingQuoteCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (quote.pctChange > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B050))
                ) {
                    Text("Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TradeDialog(
    quote: Quote,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf("BUY") }

    val qty = quantity.toIntOrNull() ?: 0
    val totalCost = qty * quote.last
    val canAfford = walletBalance >= totalCost
    val isMarketOpen = marketStatus?.isOpen ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        modifier = Modifier.height(560.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tradeType} ${quote.symbol}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Market Status Warning
                if (!isMarketOpen) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFB71C1C).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "\u26A0 ${marketStatus?.message ?: "Market is closed"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF5252),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Wallet Balance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wallet Balance", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "\u20B9${String.format("%,.2f", walletBalance)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C4DFF)
                        )
                    }
                }

                Text(
                    text = "Current Price: \u20B9${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { tradeType = "BUY" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tradeType == "BUY") Color(0xFF00B050) else Color(0xFF2A2A2A)
                        )
                    ) {
                        Text("Buy", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { tradeType = "SELL" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tradeType == "SELL") Color(0xFFFF5252) else Color(0xFF2A2A2A)
                        )
                    ) {
                        Text("Sell", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray,
                        focusedBorderColor = Color.Blue,
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    )
                )

                if (qty > 0) {
                    Text(
                        text = "Total: \u20B9${String.format("%,.2f", totalCost)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Insufficient funds warning for BUY
                    if (tradeType == "BUY" && !canAfford) {
                        Text(
                            text = "\u26A0 Insufficient funds! Need \u20B9${String.format("%,.2f", totalCost - walletBalance)} more",
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qty > 0) {
                        if (tradeType == "BUY") {
                            onBuy(qty)
                        } else {
                            onSell(qty)
                        }
                    }
                },
                enabled = qty > 0 && isMarketOpen && (tradeType == "SELL" || canAfford),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "BUY") Color(0xFF00B050) else Color(0xFFFF5252),
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Text(tradeType, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Blue)
            }
        }
    )
}

@Composable
fun AddFundsDialog(
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val presetAmounts = listOf(10000.0, 25000.0, 50000.0, 100000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text("Add Funds to Wallet", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (\u20B9)", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray,
                        focusedBorderColor = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    )
                )

                Text("Quick Add", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { preset ->
                        Button(
                            onClick = { amount = preset.toInt().toString() },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("\u20B9${preset.toInt()/1000}K", fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) onAdd(amt)
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Add Funds", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Blue)
            }
        }
    )
}
