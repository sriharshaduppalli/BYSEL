package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote

@Composable
fun StockDetailScreen(
    quote: Quote?,
    onBackPress: () -> Unit,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit
) {
    if (quote == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Text("Stock not found", color = Color.White)
        }
        return
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
            IconButton(onClick = onBackPress) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = quote.symbol,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Price Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Current Price",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (quote.pctChange > 0) Color(0xFF1B5E20).copy(alpha = 0.3f)
                                else Color(0xFFB71C1C).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (quote.pctChange > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                    }
                }
            }

            // Stock Details Section
            Text(
                text = "Stock Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            DetailRow(label = "Today's Open", value = "₹${String.format("%.2f", quote.last)}")
            DetailRow(label = "Day High", value = "₹${String.format("%.2f", quote.last * 1.02)}")
            DetailRow(label = "Day Low", value = "₹${String.format("%.2f", quote.last * 0.98)}")
            DetailRow(label = "52 Week High", value = "₹${String.format("%.2f", quote.last * 1.15)}")
            DetailRow(label = "52 Week Low", value = "₹${String.format("%.2f", quote.last * 0.85)}")

            Spacer(modifier = Modifier.height(20.dp))

            // Trading Volume Section
            Text(
                text = "Volume & Trade Data",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            DetailRow(label = "Volume", value = "${(Math.random() * 10000000).toInt()} shares")
            DetailRow(label = "Avg Volume", value = "${(Math.random() * 8000000).toInt()} shares")
            DetailRow(label = "Market Cap", value = "₹${(Math.random() * 500000).toInt()} Cr")

            Spacer(modifier = Modifier.height(20.dp))

            // Valuation Section
            Text(
                text = "Valuation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            DetailRow(label = "P/E Ratio", value = "${String.format("%.2f", 15.5 + Math.random() * 10)}")
            DetailRow(label = "Dividend Yield", value = "${String.format("%.2f", 2.5 + Math.random() * 3)}%")
            DetailRow(label = "Book Value", value = "₹${String.format("%.2f", quote.last * 0.8)}")

            Spacer(modifier = Modifier.height(30.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B050)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Buy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sell", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
