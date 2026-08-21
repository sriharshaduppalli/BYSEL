package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bysel.trader.data.CustomScannerFilters
import com.bysel.trader.data.WatchlistSymbols
import com.bysel.trader.data.models.QualityScreenResult
import com.bysel.trader.data.models.ScannerAnomaly
import com.bysel.trader.data.models.ScannerPillar
import com.bysel.trader.data.models.ScannerRow
import com.bysel.trader.data.models.ScoreHistoryResponse
import com.bysel.trader.ui.theme.AppTheme
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
    QUALITY_SCREEN("quality_screen", "Quality screen"),
    CUSTOM("custom", "Custom"),
    FNO("fno", "F&O"),
}

private enum class SwingSetupFilter(val key: String, val title: String) {
    ALL("all", "All"),
    PULLBACK("pullback", "Pullback"),
    BREAKOUT("breakout", "Breakout"),
}

@Composable
fun ScannerScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit,
    onOpenSymbol: (ScannerRow) -> Unit,
    onOpenPaperGym: () -> Unit,
    onOpenOptionsGym: () -> Unit = onOpenPaperGym,
    onOpenFuturesGym: () -> Unit = onOpenPaperGym,
) {
    val theme = LocalAppTheme.current
    val payload by viewModel.marketScanner.collectAsStateWithLifecycle()
    val loading by viewModel.scannerLoading.collectAsStateWithLifecycle()
    val error by viewModel.scannerError.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val customFilters by viewModel.customScannerFilters.collectAsStateWithLifecycle()
    val sectorFocus by viewModel.scannerSectorFocus.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf(ScannerModeChip.LONG_TERM.name) }
    var setupFilterKey by rememberSaveable { mutableStateOf(SwingSetupFilter.ALL.name) }
    val selected = runCatching { ScannerModeChip.valueOf(selectedKey) }
        .getOrDefault(ScannerModeChip.LONG_TERM)
    val setupFilter = runCatching { SwingSetupFilter.valueOf(setupFilterKey) }
        .getOrDefault(SwingSetupFilter.ALL)

    LaunchedEffect(Unit) {
        viewModel.ensureCustomScannerFiltersLoaded()
    }

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
                FnoPaperHubCard(
                    onOpenOptionsGym = onOpenOptionsGym,
                    onOpenFuturesGym = onOpenFuturesGym,
                )
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
                val rawRows = payload?.rows.orEmpty()
                val focusSymbols = sectorFocus?.symbols.orEmpty().toSet()
                val focusedRaw = if (focusSymbols.isEmpty()) {
                    rawRows
                } else {
                    rawRows.filter { it.symbol.trim().uppercase() in focusSymbols }
                }
                val rows = when (selected) {
                    ScannerModeChip.SWING -> {
                        val typed = when (setupFilter) {
                            SwingSetupFilter.ALL -> focusedRaw
                            else -> focusedRaw.filter {
                                it.setup?.displayType.equals(setupFilter.key, ignoreCase = true)
                            }
                        }
                        typed.take(15)
                    }
                    ScannerModeChip.CUSTOM -> focusedRaw
                        .filter { it.matchesCustom(customFilters) }
                        .sortedByDescending { it.displayScore }
                    else -> focusedRaw
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (sectorFocus != null) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.clearScannerSectorFocus() },
                                label = {
                                    Text(
                                        "${sectorFocus?.sector} · ${focusSymbols.size} heatmap names  ·  tap to clear"
                                    )
                                },
                            )
                        }
                    }
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

                    if (selected == ScannerModeChip.CUSTOM) {
                        item {
                            CustomFilterChips(
                                filters = customFilters,
                                onMinScore = viewModel::toggleCustomScannerMinScore,
                                onRsi = viewModel::toggleCustomScannerRsi,
                                onDma = viewModel::toggleCustomScannerDma,
                                onVolume = viewModel::toggleCustomScannerVolume,
                                onMaxPe = viewModel::toggleCustomScannerMaxPe,
                                onMinChange = viewModel::toggleCustomScannerMinChange,
                                onClear = viewModel::clearCustomScannerFilters,
                            )
                        }
                    }

                    if (selected == ScannerModeChip.SWING) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(SwingSetupFilter.entries.toList(), key = { it.name }) { chip ->
                                    FilterChip(
                                        selected = setupFilter == chip,
                                        onClick = { setupFilterKey = chip.name },
                                        label = { Text(chip.title) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                                            selectedLabelColor = theme.text,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (rows.isEmpty() && !loading) {
                        item {
                            Text(
                                when (selected) {
                                    ScannerModeChip.SWING ->
                                        "No paper swing setups in this batch from the fields we have."
                                    ScannerModeChip.CUSTOM ->
                                        "No names match these chips from fields we have. Clear a chip or wait for RSI/DMA/PE on the quote."
                                    ScannerModeChip.QUALITY_SCREEN ->
                                        "No name in this NIFTY 50 + watchlist batch passed enough Yahoo-backed checks. Skipped 5-year / promoter / pledge rules do not fail a name. Refresh after quoteSummary fills PEG, ROE, OPM."
                                    else ->
                                        "No quoted names in this batch. Pull to refresh after quotes warm."
                                },
                                color = theme.textSecondary,
                            )
                        }
                    }

                    if (rows.isEmpty() && focusSymbols.isNotEmpty()) {
                        item {
                            Text(
                                "Those heatmap names are not in this scanner bucket yet. Open a name for live quote + score when available.",
                                fontSize = 12.sp,
                                color = theme.textSecondary,
                            )
                        }
                        items(focusSymbols.toList(), key = { it }) { symbol ->
                            FilterChip(
                                selected = false,
                                onClick = { onOpenSymbol(ScannerRow(symbol = symbol)) },
                                label = { Text(symbol) },
                            )
                        }
                    }

                    items(rows, key = { it.symbol }) { row ->
                        if (selected == ScannerModeChip.SWING) {
                            SwingSetupCard(
                                row = row,
                                watched = watchlist.any { WatchlistSymbols.matches(it, row.symbol) },
                                onClick = { onOpenSymbol(row) },
                                onWatchlist = { viewModel.addToWatchlist(row.symbol) },
                            )
                        } else {
                            LongTermScannerRow(
                                row = row,
                                showQualityScreen = selected == ScannerModeChip.QUALITY_SCREEN,
                                onClick = { onOpenSymbol(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FnoPaperHubCard(
    onOpenOptionsGym: () -> Unit,
    onOpenFuturesGym: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = byselCardColors(),
            elevation = byselCardElevation(),
            border = byselCardBorder(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("F&O paper gym is ready", fontWeight = FontWeight.SemiBold, color = theme.text)
                Text(
                    "This scanner tab does not rank F&O contracts yet. Use Trade → Options to read a chain, " +
                        "or Trade → Futures to preview lot size and margin. Start with NIFTY or BANKNIFTY.",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
        com.bysel.trader.ui.components.FnoLiteracyPrimer(
            mode = com.bysel.trader.ui.components.FnoLiteracyMode.SCANNER,
            initiallyExpanded = true,
            onOpenOptions = onOpenOptionsGym,
            onOpenFutures = onOpenFuturesGym,
        )
    }
}

@Composable
private fun LongTermScannerRow(
    row: ScannerRow,
    onClick: () -> Unit,
    showQualityScreen: Boolean = false,
) {
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
                    color = scoreBandColor(row.byselScore ?: row.overall, theme),
                )
            }
            ByselScoreStrip(row = row, compact = true)
            AnomalyBadgeRow(row.detectedAnomalies())
            if (showQualityScreen) {
                QualityScreenChecklist(row.qualityScreen)
            }
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
fun SwingSetupCard(
    row: ScannerRow,
    watched: Boolean,
    onClick: () -> Unit,
    onWatchlist: () -> Unit,
) {
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
                    Text(
                    setup?.displayType?.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                    } ?: "Setup",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                IconButton(onClick = onWatchlist) {
                    Icon(
                        imageVector = if (watched) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (watched) "On watchlist" else "Add to watchlist",
                        tint = if (watched) Color(0xFFFFD54F) else theme.textSecondary,
                    )
                }
            }
            ByselScoreStrip(row = row, compact = true)
            AnomalyBadgeRow(row.detectedAnomalies())
            if (setup != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PracticeLevel("Entry zone", setup.entry)
                    PracticeLevel("SL", setup.stop)
                    PracticeLevel("T1", setup.displayT1)
                    PracticeLevel("T2", setup.t2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        setup.riskReward?.let { "R:R ${String.format(Locale.US, "%.1f", it)}" } ?: "R:R —",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.text,
                    )
                    Text(
                        "Momentum ${setup.momentumScore ?: row.momentum ?: "—"}",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
                Text(setup.note, fontSize = 11.sp, color = theme.textSecondary)
                Text(
                    setup.winRateNote.ifBlank { "Historical win rate n/a until we have journal data" },
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
            Text(
                "Paper — not advice. Practice levels only.",
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
                color = scoreBandColor(row.byselScore ?: row.overall, theme),
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
            ScorePill("Q", row.quality, theme)
            ScorePill("V", row.displayValuation, theme)
            ScorePill("T", row.trend, theme)
            ScorePill("M", row.momentum, theme)
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
fun ByselExplainabilityCard(
    row: ScannerRow,
    history: ScoreHistoryResponse? = null,
) {
    val theme = LocalAppTheme.current
    var expanded by rememberSaveable(row.symbol) { mutableStateOf(true) }
    Card(
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Why this score?", fontWeight = FontWeight.SemiBold, color = theme.text)
            AnomalyBadgeRow(row.detectedAnomalies())
            ByselScoreStrip(row = row, compact = false)
            Text(
                row.displaySummary.ifBlank {
                    "Educational readout from available Yahoo fields only. Not investment advice."
                },
                fontSize = 13.sp,
                color = theme.text,
            )
            Text(
                "Not investment advice. Labels are educational — never Strong Buy / Buy / Hold / Avoid.",
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pillar breakdown",
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = theme.textSecondary,
                )
            }
            if (expanded) {
                PillarBreakdown(title = "Quality", pillar = row.pillars?.quality, fallbackScore = row.quality)
                PillarBreakdown(title = "Valuation", pillar = row.pillars?.valuation, fallbackScore = row.displayValuation)
                PillarBreakdown(title = "Trend", pillar = row.pillars?.trend, fallbackScore = row.trend)
                PillarBreakdown(title = "Momentum", pillar = row.pillars?.momentum, fallbackScore = row.momentum)
            }
            ScoreHistoryStrip(history = history, symbol = row.symbol)
        }
    }
}

@Composable
private fun PillarBreakdown(
    title: String,
    pillar: ScannerPillar?,
    fallbackScore: Int?,
) {
    val theme = LocalAppTheme.current
    val score = pillar?.score ?: fallbackScore
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, color = theme.text, modifier = Modifier.weight(1f))
            Text(
                score?.toString() ?: "—",
                fontWeight = FontWeight.Bold,
                color = scoreBandColor(score, theme),
            )
        }
        LinearProgressIndicator(
            progress = { ((score ?: 0) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = scoreBandColor(score, theme),
            trackColor = theme.textSecondary.copy(alpha = 0.16f),
        )
        val top = pillar?.topMetrics.orEmpty().take(3)
        if (top.isEmpty()) {
            Text("No contributing metrics in this snapshot.", fontSize = 11.sp, color = theme.textSecondary)
        } else {
            top.forEach { metric ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metric.label.ifBlank { metric.id },
                        fontSize = 12.sp,
                        color = theme.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        metric.score?.toString() ?: "—",
                        fontSize = 12.sp,
                        color = scoreBandColor(metric.score, theme),
                    )
                }
                LinearProgressIndicator(
                    progress = { ((metric.score ?: 0) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = scoreBandColor(metric.score, theme),
                    trackColor = theme.textSecondary.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun ScoreHistoryStrip(history: ScoreHistoryResponse?, symbol: String) {
    val theme = LocalAppTheme.current
    val points = history?.takeIf { it.symbol.equals(symbol, ignoreCase = true) }?.points.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Score history", fontWeight = FontWeight.Medium, color = theme.text)
        if (points.size < 2) {
            Text(
                history?.note?.ifBlank { null }
                    ?: "Score history starts after the first daily snapshot. 30/90-day trend is pending.",
                fontSize = 12.sp,
                color = theme.textSecondary,
            )
        } else {
            val latest = points.last()
            val previous = points[points.lastIndex - 1]
            val latestScore = latest.byselScore
            val previousScore = previous.byselScore
            if (latestScore != null && previousScore != null) {
                val delta = latestScore - previousScore
                val deltaLabel = if (delta > 0) "+$delta" else "$delta"
                Text(
                    "Changed $deltaLabel since ${previous.date} (education only — not a forecast).",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (delta >= 0) theme.positive else theme.negative,
                )
            }
            val window = history?.days ?: 90
            Text("$window-day snapshots (education only)", fontSize = 11.sp, color = theme.textSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(points.takeLast(14), key = { it.date }) { point ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(point.date.takeLast(5), fontSize = 10.sp, color = theme.textSecondary)
                        Text(
                            point.byselScore?.toString() ?: "—",
                            fontWeight = FontWeight.Bold,
                            color = scoreBandColor(point.byselScore, theme),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, value: Int?, theme: AppTheme) {
    val text = value?.toString() ?: "—"
    Text(
        "$label $text",
        modifier = Modifier
            .background(
                color = scoreBandColor(value, theme).copy(alpha = 0.16f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (value == null) theme.textSecondary else theme.text,
        style = MaterialTheme.typography.labelSmall,
    )
}

fun scoreBandColor(score: Int?, theme: AppTheme): Color {
    if (score == null) return theme.textSecondary
    return when {
        score >= 80 -> theme.positive
        score >= 65 -> Color(0xFF81C784)
        score >= 50 -> Color(0xFFFFC107)
        else -> Color(0xFFFF7043)
    }
}

private fun formatLast(value: Double): String = "₹${String.format(Locale.US, "%,.2f", value)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFilterChips(
    filters: CustomScannerFilters,
    onMinScore: (Int) -> Unit,
    onRsi: (String) -> Unit,
    onDma: (String) -> Unit,
    onVolume: (Double) -> Unit,
    onMaxPe: (Double) -> Unit,
    onMinChange: (Double) -> Unit,
    onClear: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (filters.activeCount == 0) {
                "Tap chips we can compute. Saved on this device."
            } else {
                "${filters.activeCount} chip${if (filters.activeCount == 1) "" else "s"} saved on this device."
            },
            fontSize = 12.sp,
            color = theme.textSecondary,
        )
        ChipGroup("Min BYSEL score") {
            FilterChip(selected = filters.minScore == 50, onClick = { onMinScore(50) }, label = { Text("50+") })
            FilterChip(selected = filters.minScore == 65, onClick = { onMinScore(65) }, label = { Text("65+") })
            FilterChip(selected = filters.minScore == 80, onClick = { onMinScore(80) }, label = { Text("80+") })
        }
        ChipGroup("RSI") {
            FilterChip(selected = filters.rsi == "40-65", onClick = { onRsi("40-65") }, label = { Text("40–65") })
            FilterChip(selected = filters.rsi == "30-70", onClick = { onRsi("30-70") }, label = { Text("30–70") })
            FilterChip(selected = filters.rsi == "<30", onClick = { onRsi("<30") }, label = { Text("<30") })
            FilterChip(selected = filters.rsi == ">70", onClick = { onRsi(">70") }, label = { Text(">70") })
        }
        ChipGroup("Price vs DMA") {
            FilterChip(selected = filters.dma == "50", onClick = { onDma("50") }, label = { Text("Above 50") })
            FilterChip(selected = filters.dma == "200", onClick = { onDma("200") }, label = { Text("Above 200") })
            FilterChip(selected = filters.dma == "both", onClick = { onDma("both") }, label = { Text("Above both") })
        }
        ChipGroup("Volume vs avg") {
            FilterChip(selected = filters.minVolume == 1.0, onClick = { onVolume(1.0) }, label = { Text("≥1×") })
            FilterChip(selected = filters.minVolume == 1.5, onClick = { onVolume(1.5) }, label = { Text("≥1.5×") })
            FilterChip(selected = filters.minVolume == 2.0, onClick = { onVolume(2.0) }, label = { Text("≥2×") })
        }
        ChipGroup("PE max") {
            FilterChip(selected = filters.maxPe == 20.0, onClick = { onMaxPe(20.0) }, label = { Text("≤20") })
            FilterChip(selected = filters.maxPe == 25.0, onClick = { onMaxPe(25.0) }, label = { Text("≤25") })
            FilterChip(selected = filters.maxPe == 30.0, onClick = { onMaxPe(30.0) }, label = { Text("≤30") })
        }
        ChipGroup("Day change") {
            FilterChip(selected = filters.minChange == 0.0, onClick = { onMinChange(0.0) }, label = { Text("≥0%") })
            FilterChip(selected = filters.minChange == 1.0, onClick = { onMinChange(1.0) }, label = { Text("≥1%") })
            FilterChip(selected = filters.minChange == 2.0, onClick = { onMinChange(2.0) }, label = { Text("≥2%") })
        }
        if (filters.activeCount > 0) {
            FilterChip(selected = false, onClick = onClear, label = { Text("Clear chips") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = theme.text)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityScreenChecklist(result: QualityScreenResult?) {
    if (result == null) return
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            result.summary.ifBlank { "${result.passed} passed · ${result.failed} failed · ${result.skipped} skipped" },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = theme.text,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            result.checks.forEach { check ->
                val tint = when (check.status) {
                    "pass" -> theme.positive
                    "fail" -> theme.negative
                    else -> theme.textSecondary
                }
                val mark = when (check.status) {
                    "pass" -> "✓"
                    "fail" -> "✗"
                    else -> "—"
                }
                Text(
                    "$mark ${check.label}",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    color = tint,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnomalyBadgeRow(anomalies: List<ScannerAnomaly>) {
    if (anomalies.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        anomalies.forEach { anomaly ->
            Text(
                listOf(anomaly.label, anomaly.detail).filter { it.isNotBlank() }.joinToString(" · "),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF7043).copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF7043),
            )
        }
    }
}

private fun ScannerRow.matchesCustom(filters: CustomScannerFilters): Boolean {
    if (filters.minScore != null && displayScore < filters.minScore) return false
    if (filters.rsi != null) {
        val rsi = metrics.rsi ?: return false
        when (filters.rsi) {
            "40-65" -> if (rsi < 40.0 || rsi > 65.0) return false
            "30-70" -> if (rsi < 30.0 || rsi > 70.0) return false
            "<30" -> if (rsi >= 30.0) return false
            ">70" -> if (rsi <= 70.0) return false
        }
    }
    if (filters.dma != null) {
        val dma50 = metrics.fiftyDayAverage
        val dma200 = metrics.twoHundredDayAverage
        when (filters.dma) {
            "50" -> if (dma50 == null || last <= dma50) return false
            "200" -> if (dma200 == null || last <= dma200) return false
            "both" -> if (dma50 == null || dma200 == null || last <= dma50 || last <= dma200) return false
        }
    }
    if (filters.minVolume != null) {
        val vol = metrics.volumeRatio ?: return false
        if (vol < filters.minVolume!!) return false
    }
    if (filters.maxPe != null) {
        val pe = metrics.pe ?: return false
        if (pe > filters.maxPe!!) return false
    }
    if (filters.minChange != null && pctChange < filters.minChange!!) return false
    return true
}
