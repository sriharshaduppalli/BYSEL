package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.api.TradeHistory
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun OrderHistoryScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<TradeHistory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                rows = viewModel.fetchTradeHistory()
            } catch (ex: Exception) {
                error = ex.message ?: "Could not load order history"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Order history", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.text)
                Text("Paper fills only — not a live broker book", fontSize = 12.sp, color = theme.textSecondary)
            }
            IconButton(onClick = { reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = theme.primary)
            }
        }

        when {
            loading && rows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.primary)
                }
            }
            error != null && rows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "Unavailable", color = theme.text)
                }
            }
            rows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No paper orders yet. Place a practice trade from Trade → Spot.",
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
                        OrderHistoryRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryRow(row: TradeHistory) {
    val theme = LocalAppTheme.current
    val buy = row.side.equals("BUY", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.symbol, color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    row.side.uppercase(Locale.getDefault()),
                    color = if (buy) theme.positive else theme.negative,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Text(
                "${row.quantity} qty @ ${formatInr(row.price)} · ${formatInr(row.total)}",
                color = theme.textSecondary,
                fontSize = 13.sp,
            )
            Text(formatOrderTime(row.timestamp), color = theme.textSecondary, fontSize = 11.sp)
        }
    }
}

private fun formatOrderTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Time unavailable"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMs))
}
