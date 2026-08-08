package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
            data = try {
                viewModel.fetchEarningsCalendar()
            } catch (_: Exception) {
                null
            }
            if (data == null) errorMsg = "Could not load earnings data. Check network and retry."
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
            IconButton(onClick = { load() }, enabled = !isLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = appTheme.primary)
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appTheme.primary)
                }
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(errorMsg.orEmpty(), color = appTheme.textSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { load() }, enabled = !isLoading) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val entries = data?.resolvedEntries().orEmpty()
                val disclaimerNote = data?.disclaimer
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No upcoming earnings found.", color = appTheme.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!disclaimerNote.isNullOrBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFF9800).copy(alpha = 0.14f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        disclaimerNote,
                                        fontSize = 11.sp,
                                        color = appTheme.textSecondary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                "${entries.size} companies tracked",
                                fontSize = 12.sp,
                                color = appTheme.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(entries, key = { it.symbol }) { entry ->
                            EarningsCard(entry = entry, appTheme = appTheme)
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningsCard(
    entry: EarningsEntry,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    val dateLabel = entry.displayDate()
    val epsEstimate = entry.displayEpsEstimate()
    val epsActual = entry.displayEpsActual()
    val trailingPe = entry.displayTrailingPe()
    val forwardPe = entry.displayForwardPe()

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.symbol, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.text)
                    entry.name?.takeIf { it.isNotBlank() }?.let { company ->
                        Text(company, fontSize = 11.sp, color = appTheme.textSecondary, maxLines = 1)
                    }
                }
                if (!dateLabel.isNullOrBlank()) {
                    Surface(
                        color = appTheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (entry.estimated) "Est. $dateLabel" else dateLabel,
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
                    label = "EPS Fwd",
                    value = epsEstimate?.let { String.format("%.2f", it) } ?: "N/A",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "EPS Trail",
                    value = epsActual?.let { String.format("%.2f", it) } ?: "N/A",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "Trailing P/E",
                    value = trailingPe?.let { String.format("%.1f", it) } ?: "N/A",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                EarningsMetric(
                    label = "Rev growth",
                    value = entry.revenueGrowth?.let { String.format("%.1f%%", it) }
                        ?: forwardPe?.let { String.format("%.1f", it) }
                        ?: "N/A",
                    color = appTheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            entry.sector?.takeIf { it.isNotBlank() }?.let { sector ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(sector, fontSize = 11.sp, color = appTheme.textSecondary)
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
