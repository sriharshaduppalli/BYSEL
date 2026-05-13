package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.api.EarningsCalendarResponse
import com.bysel.trader.data.api.EarningsEntry
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel
import kotlinx.coroutines.launch

@Composable
fun EarningsCalendarScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
) {
    val appTheme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<EarningsCalendarResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            data = viewModel.fetchEarningsCalendar()
            if (data == null) errorMsg = "Could not load earnings data."
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appTheme.text)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Earnings Calendar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.text)
                Text("Upcoming quarterly results", fontSize = 12.sp, color = appTheme.textSecondary)
            }
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = appTheme.primary)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appTheme.primary)
            }
            return@Column
        }

        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = appTheme.textSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMsg!!, color = appTheme.textSecondary, fontSize = 14.sp)
                }
            }
            return@Column
        }

        val entries = data?.earnings ?: emptyList()
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No upcoming earnings found.", color = appTheme.textSecondary, fontSize = 14.sp)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${entries.size} companies — next 30 days",
                    fontSize = 12.sp,
                    color = appTheme.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(entries) { entry ->
                EarningsCard(entry = entry, appTheme = appTheme)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EarningsCard(
    entry: EarningsEntry,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = appTheme.card),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.symbol, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.text)
                entry.earningsDate?.let { date ->
                    Surface(
                        color = appTheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            date.take(10),
                            fontSize = 11.sp,
                            color = appTheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = appTheme.surface, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                EarningsMetric(
                    label = "EPS Est.",
                    value = entry.epsEstimate?.let { String.format("%.2f", it) } ?: "—",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "EPS Actual",
                    value = entry.epsActual?.let { String.format("%.2f", it) } ?: "—",
                    color = when {
                        entry.epsActual == null || entry.epsEstimate == null -> appTheme.textSecondary
                        entry.epsActual >= entry.epsEstimate -> Color(0xFF4CAF50)
                        else -> Color(0xFFE53935)
                    },
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "Trailing P/E",
                    value = entry.trailingPE?.let { String.format("%.1f", it) } ?: "—",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "Forward P/E",
                    value = entry.forwardPE?.let { String.format("%.1f", it) } ?: "—",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EarningsMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = LocalAppTheme.current.textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
