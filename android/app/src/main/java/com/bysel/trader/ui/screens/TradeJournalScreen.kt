package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel
import kotlinx.coroutines.launch

@Composable
fun TradeJournalScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
) {
    val appTheme = LocalAppTheme.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var insights by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun load() {
        scope.launch {
            isLoading = true
            entries = viewModel.fetchJournalEntries()
            insights = viewModel.fetchJournalInsights()
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
                Text("Trade Journal", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.text)
                Text("Your trading behavior analysis", fontSize = 12.sp, color = appTheme.textSecondary)
            }
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = appTheme.primary)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = appTheme.card,
            contentColor = appTheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Insights", fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Trade Log", fontSize = 13.sp) }
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appTheme.primary)
            }
            return@Column
        }

        when (selectedTab) {
            0 -> JournalInsightsTab(insights = insights, appTheme = appTheme)
            1 -> JournalEntriesTab(entries = entries, appTheme = appTheme)
        }
    }
}

@Composable
private fun JournalInsightsTab(
    insights: Map<String, Any>?,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    if (insights == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No insights yet.", color = appTheme.textSecondary, fontSize = 14.sp)
        }
        return
    }

    val hasData = insights["hasEnoughData"] as? Boolean ?: false
    if (!hasData) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Book, contentDescription = null, tint = appTheme.textSecondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    insights["message"] as? String ?: "Need more trades to generate insights.",
                    color = appTheme.textSecondary,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    @Suppress("UNCHECKED_CAST")
    val insightsList = insights["insights"] as? List<Map<String, Any>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val topSymbols = insights["topSymbols"] as? List<Map<String, Any>> ?: emptyList()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip("Total Trades", "${insights["totalTrades"] ?: 0}", appTheme, Modifier.weight(1f))
                StatChip("Buys", "${insights["buys"] ?: 0}", appTheme, Modifier.weight(1f))
                StatChip("Sells", "${insights["sells"] ?: 0}", appTheme, Modifier.weight(1f))
            }
        }

        if (insightsList.isNotEmpty()) {
            item {
                Text("Behavioral Insights", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
            }
            items(insightsList) { insight ->
                InsightCard(insight = insight, appTheme = appTheme)
            }
        }

        if (topSymbols.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Most Traded", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = appTheme.card),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        topSymbols.forEachIndexed { idx, sym ->
                            if (idx > 0) HorizontalDivider(color = appTheme.surface, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${idx + 1}. ${sym["symbol"]}", fontSize = 13.sp, color = appTheme.text, fontWeight = FontWeight.SemiBold)
                                Text("${sym["trades"]} trades", fontSize = 12.sp, color = appTheme.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun InsightCard(
    insight: Map<String, Any>,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    val type = insight["type"] as? String ?: "info"
    val borderColor = when (type) {
        "warning" -> Color(0xFFFF9800)
        "error" -> Color(0xFFE53935)
        else -> Color(0xFF2196F3)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = appTheme.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(borderColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = borderColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(insight["title"] as? String ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(insight["detail"] as? String ?: "", fontSize = 12.sp, color = appTheme.textSecondary)
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    appTheme: com.bysel.trader.ui.theme.AppTheme,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = appTheme.card),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = appTheme.primary)
            Text(label, fontSize = 10.sp, color = appTheme.textSecondary)
        }
    }
}

@Composable
private fun JournalEntriesTab(
    entries: List<Map<String, Any>>,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Book, contentDescription = null, tint = appTheme.textSecondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No trades logged yet.", color = appTheme.textSecondary, fontSize = 14.sp)
                Text("Trades you execute will appear here.", color = appTheme.textSecondary, fontSize = 12.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(entries) { entry ->
            JournalEntryCard(entry = entry, appTheme = appTheme)
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun JournalEntryCard(
    entry: Map<String, Any>,
    appTheme: com.bysel.trader.ui.theme.AppTheme
) {
    val side = entry["side"] as? String ?: "BUY"
    val sideColor = if (side == "BUY") Color(0xFF4CAF50) else Color(0xFFE53935)
    @Suppress("UNCHECKED_CAST")
    val notes = entry["autoNotes"] as? List<String> ?: emptyList()
    val timestamp = (entry["timestamp"] as? String)?.take(16)?.replace("T", " ") ?: ""

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = sideColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(side, fontSize = 11.sp, color = sideColor, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(entry["symbol"] as? String ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
                }
                Text(timestamp, fontSize = 11.sp, color = appTheme.textSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Qty", fontSize = 10.sp, color = appTheme.textSecondary)
                    Text("${entry["qty"] ?: 0}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
                }
                Column {
                    Text("Price", fontSize = 10.sp, color = appTheme.textSecondary)
                    Text("₹${String.format("%.2f", (entry["price"] as? Double) ?: 0.0)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
                }
                Column {
                    Text("Total", fontSize = 10.sp, color = appTheme.textSecondary)
                    Text("₹${String.format("%.0f", (entry["total"] as? Double) ?: 0.0)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appTheme.text)
                }
            }

            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                notes.forEach { note ->
                    Text("• $note", fontSize = 11.sp, color = Color(0xFFFF9800))
                }
            }
        }
    }
}
