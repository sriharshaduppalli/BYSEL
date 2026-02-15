package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote

@Composable
fun TradingScreen(
    quotes: List<Quote>,
    isLoading: Boolean,
    error: String?,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onErrorDismiss: () -> Unit
) {
    var selectedQuote by remember { mutableStateOf<Quote?>(null) }

    if (selectedQuote != null) {
        TradeDialog(
            quote = selectedQuote!!,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
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
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf("BUY") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        modifier = Modifier.height(500.dp),
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
                Text(
                    text = "Current Price: ₹${String.format("%.2f", quote.last)}",
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
                        .padding(bottom = 20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray,
                        focusedBorderColor = Color.Blue,
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    )
                )

                if (quantity.isNotEmpty()) {
                    val total = quantity.toIntOrNull()?.let { it * quote.last } ?: 0.0
                    Text(
                        text = "Total: ₹${String.format("%.2f", total)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantity.toIntOrNull() ?: 0
                    if (qty > 0) {
                        if (tradeType == "BUY") {
                            onBuy(qty)
                        } else {
                            onSell(qty)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "BUY") Color(0xFF00B050) else Color(0xFFFF5252)
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
