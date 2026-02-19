package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.Alert
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun QuoteCard(quote: Quote, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = quote.symbol,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "₹${quote.last}",
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
            Text(
                text = "${if (quote.pctChange > 0) "+" else ""}${quote.pctChange}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
            )
        }
    }
}

@Composable
fun HoldingCard(holding: Holding) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = holding.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = "Qty: ${holding.qty}",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                }
                Text(
                    text = "₹${holding.pnl}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (holding.pnl > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Avg: ₹${holding.avgPrice}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary
                )
                Text(
                    text = "Last: ₹${holding.last}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert, onDelete: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${alert.symbol} ${alert.alertType}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "₹${alert.thresholdPrice}",
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .width(60.dp)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
            ) {
                Text("Delete", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = LocalAppTheme.current.primary)
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Error",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.negative
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = LocalAppTheme.current.textSecondary,
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text("Retry")
            }
        }
    }
}
