package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bysel.trader.data.models.ScannerRow
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.viewmodel.TradingViewModel
import java.util.Locale

private enum class ScannerModeChip(val apiMode: String, val title: String) {
    LONG_TERM("long_term", "Long-term"),
    SWING("swing", "Swing"),
    HIGH_QUALITY("high_quality", "High Quality"),
    MOMENTUM("momentum", "Momentum"),
    VALUE("value", "Value"),
    FNO("fno", "F&O"),
}

@Composable
fun ScannerScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
    onOpenSymbol: (ScannerRow) -> Unit,
    onOpenPaperGym: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val payload by viewModel.marketScanner.collectAsStateWithLifecycle()
    val loading by viewModel.scannerLoading.collectAsStateWithLifecycle()
    val error by viewModel.scannerError.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf(ScannerModeChip.LONG_TERM.name) }
    val selected = runCatching { ScannerModeChip.valueOf(selectedKey) }
        .getOrDefault(ScannerModeChip.LONG_TERM)

    LaunchedEffect(selected) {
        if (selected != ScannerModeChip.FNO) {
            viewModel.loadMarketScanner(selected.apiMode)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Scanner", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.text)
                Text(
                    "BYSEL Score · paper practice, not a broker",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }
            IconButton(
                onClick = {
                    if (selected != ScannerModeChip.FNO) {
                        viewModel.loadMarketScanner(selected.apiMode, force = true)
                    }
                },
                enabled = !loading && selected != ScannerModeChip.FNO,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = theme.primary)
            }
        }

        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ScannerModeChip.entries.toList(), key = { it.name }) { chip ->
                FilterChip(
                    selected = selected == chip,
                    onClick = { selectedKey = chip.name },
                    label = { Text(chip.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = theme.text,
                    ),
                )
            }
        }

        when {
            selected == ScannerModeChip.FNO -> {
                FnoComingSoonCard(onOpenPaperGym = onOpenPaperGym)
            }
            loading && payload == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.primary)
                }
            }
            error != null && payload == null -> {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "Scanner unavailable", color = theme.text)
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = { viewModel.loadMarketScanner(selected.apiMode, force = true) }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val education = payload?.education
                val rows = payload?.rows.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card(
                            colors = byselCardColors(),
                            elevation = byselCardElevation(),
                            border = byselCardBorder(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    education?.title ?: if (selected == ScannerModeChip.SWING) {
                                        "Swing — today's setups"
                                    } else {
                                        "Long-term — quality + fair value"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.text,
                                )
                                Text(
                                    education?.summary
                                        ?: "Heuristic shortlist from NIFTY 50. Missing Yahoo fields stay as —.",
                                    fontSize = 13.sp,
                                    color = theme.textSecondary,
                                )
                                education?.filters.orEmpty().forEach { filter ->
                                    Text(
                                        "${filter.label} · ${if (filter.applied) "applied when we have data" else "education only"} — ${filter.status}",
                                        fontSize = 11.sp,
                                        color = theme.textSecondary,
                                    )
                                }
                                Text(
                                    education?.scoreGuide ?: "",
                                    fontSize = 12.sp,
                                    color = theme.textSecondary,
                                )
                                Text(
                                    education?.riskNote ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.text,
                                )
                                Text(
                                    education?.disclaimer
                                        ?: "Not investment advice. Paper practice only. BYSEL is not a broker.",
                                    fontSize = 11.sp,
                                    color = theme.textSecondary,
                                )
                            }
                        }
                    }

                    if (rows.isEmpty() && !loading) {
                        item {
                            Text(
                                "No quoted names in this batch. Pull to refresh after quotes warm.",
                                color = theme.textSecondary,
                            )
                        }
                    }

                    items(rows, key = { it.symbol }) { row ->
                        if (selected == ScannerModeChip.SWING) {
                            SwingSetupCard(row = row, onClick = { onOpenSymbol(row) })
                        } else {
                            LongTermScannerRow(row = row, onClick = { onOpenSymbol(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FnoComingSoonCard(onOpenPaperGym: () -> Unit) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("F&O — coming soon", fontWeight = FontWeight.SemiBold, color = theme.text)
            Text(
                "Option chain, strategy builder, and payoff diagrams are not in this phase. " +
                    "Use Swing for cash setups or the paper Trade gym to practice size and risk.",
                fontSize = 13.sp,
                color = theme.textSecondary,
            )
            FilledTonalButton(onClick = onOpenPaperGym) {
                Text("Open paper Trade gym")
            }
        }
    }
}

@Composable
private fun LongTermScannerRow(row: ScannerRow, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.symbol, fontWeight = FontWeight.SemiBold, color = theme.text)
                    Text(
                        formatLast(row.last),
                        fontSize = 13.sp,
                        color = if (row.pctChange >= 0) theme.positive else theme.negative,
                    )
                }
                Text(
                    "${row.displayScore}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = theme.primary,
                )
            }
            ByselScoreStrip(row = row, compact = true)
            Text(
                row.why.ifBlank { "Limited Yahoo fields — scores use only what we have." },
                fontSize = 12.sp,
                color = theme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwingSetupCard(row: ScannerRow, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val setup = row.setup
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.symbol, fontWeight = FontWeight.SemiBold, color = theme.text)
                    Text(
                        setup?.title ?: row.why.ifBlank { "No clear setup from available fields" },
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
                Text("${row.displayScore}", fontWeight = FontWeight.ExtraBold, color = theme.primary)
            }
            ByselScoreStrip(row = row, compact = true)
            if (setup != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PracticeLevel("Entry", setup.entry)
                    PracticeLevel("SL", setup.stop)
                    PracticeLevel("Target", setup.target)
                }
                Text(setup.note, fontSize = 11.sp, color = theme.textSecondary)
            }
            Text(
                "Paper practice · risk about 1–2% of the practice book.",
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
        }
    }
}

@Composable
private fun PracticeLevel(label: String, value: Double?) {
    val theme = LocalAppTheme.current
    Column {
        Text(label, fontSize = 10.sp, color = theme.textSecondary)
        Text(value?.let { formatLast(it) } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = theme.text)
    }
}

@Composable
fun ByselScoreStrip(row: ScannerRow, compact: Boolean = false) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${row.displayScore}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 20.sp else 28.sp,
                color = theme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                row.convictionLabel.ifBlank { row.stance.firstOrNull().orEmpty() },
                fontSize = 12.sp,
                color = theme.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScorePill("Q", row.quality)
            ScorePill("V", row.displayValuation)
            ScorePill("T", row.trend)
            ScorePill("M", row.momentum)
        }
        if (!compact) {
            Text(
                row.displaySummary.ifBlank { row.riskLabel },
                fontSize = 12.sp,
                color = theme.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScorePill(label: String, value: Int?, muted: Boolean = false) {
    val theme = LocalAppTheme.current
    val text = value?.toString() ?: "—"
    Text(
        "$label $text",
        modifier = Modifier
            .background(
                color = if (muted) theme.card else theme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (muted) theme.textSecondary else theme.text,
        style = MaterialTheme.typography.labelSmall,
    )
}

private fun formatLast(value: Double): String = "₹${String.format(Locale.US, "%,.2f", value)}"
