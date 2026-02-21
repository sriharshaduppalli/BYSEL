package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun WatchlistWidget(isPinned: Boolean, onPinClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppTheme.current.card)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Watchlist",
                fontSize = 20.sp,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(20.dp)
            )
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin Watchlist" else "Pin Watchlist",
                    tint = if (isPinned) LocalAppTheme.current.positive else LocalAppTheme.current.textSecondary
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Placeholder for watchlist items
            Text(
                text = "- TCS: ₹3,500.00\n- INFY: ₹1,600.00\n- RELIANCE: ₹2,800.00",
                fontSize = 14.sp,
                color = LocalAppTheme.current.textSecondary
            )
        }
    }
}
