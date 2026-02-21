package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.HeatmapSector
import com.bysel.trader.data.models.HeatmapStock
import com.bysel.trader.data.models.MarketHeatmap
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun HeatmapScreen(
    heatmap: MarketHeatmap?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStockClick: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        if (heatmap == null) onRefresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        // Header with market mood
        HeatmapHeader(heatmap)

        if (isLoading && heatmap == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading market data...", color = LocalAppTheme.current.textSecondary, fontSize = 14.sp)
                }
            }
        } else if (heatmap != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Market Breadth Card
                item {
                    MarketBreadthCard(heatmap)
                }

                // Sector cards
                items(heatmap.sectors) { sector ->
                    SectorHeatmapCard(sector, onStockClick)
                }
            }
        }
    }
}

@Composable
private fun HeatmapHeader(heatmap: MarketHeatmap?) {
    val moodColors = when (heatmap?.mood) {
        "EUPHORIC" -> listOf(Color(0xFF00C853), Color(0xFF1B5E20))
        "BULLISH" -> listOf(Color(0xFF43A047), Color(0xFF1B5E20))
        "NEUTRAL" -> listOf(Color(0xFFFFB300), Color(0xFF795548))
        "BEARISH" -> listOf(Color(0xFFE53935), Color(0xFF880E4F))
        "FEARFUL" -> listOf(Color(0xFFB71C1C), Color(0xFF4A148C))
        else -> listOf(Color(0xFF1A237E), Color(0xFF7C4DFF))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(colors = moodColors)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = null,
                        tint = LocalAppTheme.current.text,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Smart Sentiment Heatmap",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (heatmap != null) {
                            Text(
                                "Market Mood: ${heatmap.moodEmoji} ${heatmap.mood}",
                                color = LocalAppTheme.current.text.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            if (heatmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    heatmap.moodDescription,
                    color = LocalAppTheme.current.text.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MarketBreadthCard(heatmap: MarketHeatmap) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Market Breadth",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Advance/Decline bar
            val total = heatmap.marketBreadth.total.toFloat().coerceAtLeast(1f)
            val advancePct = heatmap.marketBreadth.advances / total
            val declinePct = heatmap.marketBreadth.declines / total

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (advancePct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(advancePct.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(Color(0xFF00C853))
                    )
                }
                if (declinePct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(declinePct.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(Color(0xFFE53935))
                    )
                }
                val unchangedPct = 1f - advancePct - declinePct
                if (unchangedPct > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(unchangedPct)
                            .fillMaxHeight()
                            .background(LocalAppTheme.current.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BreadthLabel("Advances", heatmap.marketBreadth.advances, Color(0xFF00C853))
                BreadthLabel("Declines", heatmap.marketBreadth.declines, Color(0xFFE53935))
                BreadthLabel("Unchanged", heatmap.marketBreadth.unchanged, LocalAppTheme.current.textSecondary)
            }

            // Best and worst sectors
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val bestChange = heatmap.bestSector.change
                val worstChange = heatmap.worstSector.change
                Column {
                    Text("Best Sector", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                    Text(
                        "${heatmap.bestSector.name} (${String.format("%+.2f", bestChange)}%)",
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Worst Sector", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                    Text(
                        "${heatmap.worstSector.name} (${String.format("%+.2f", worstChange)}%)",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadthLabel(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            label,
            color = LocalAppTheme.current.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SectorHeatmapCard(sector: HeatmapSector, onStockClick: (String) -> Unit) {
    val sectorColor = when (sector.intensity) {
        "strong_positive" -> Color(0xFF00C853)
        "positive" -> Color(0xFF43A047)
        "neutral" -> Color(0xFFFFB300)
        "negative" -> Color(0xFFE53935)
        "strong_negative" -> Color(0xFFB71C1C)
        else -> LocalAppTheme.current.textSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Sector header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(sectorColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        sector.name,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Text(
                    "${String.format("%+.2f", sector.avgChange)}%",
                    color = sectorColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Advances/Declines mini bar
            Text(
                "↑${sector.advances} ↓${sector.declines} (${sector.totalStocks} stocks)",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stock tiles grid (heatmap visualization)
            val chunked = sector.stocks.chunked(4)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { stock ->
                        StockHeatTile(
                            stock = stock,
                            modifier = Modifier.weight(1f),
                            onClick = { onStockClick(stock.symbol) }
                        )
                    }
                    // Fill remaining
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StockHeatTile(
    stock: HeatmapStock,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when (stock.intensity) {
        "strong_positive" -> Color(0xFF00C853)
        "positive" -> Color(0xFF2E7D32)
        "slight_positive" -> Color(0xFF1B5E20).copy(alpha = 0.7f)
        "slight_negative" -> Color(0xFF4E342E).copy(alpha = 0.7f)
        "negative" -> Color(0xFFC62828)
        "strong_negative" -> Color(0xFFB71C1C)
        else -> Color(0xFF424242)
    }

    Card(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stock.symbol,
                color = LocalAppTheme.current.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${String.format("%+.1f", stock.pctChange)}%",
                color = LocalAppTheme.current.text.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
