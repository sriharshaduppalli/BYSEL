package com.bysel.trader.ui.screens

import com.bysel.trader.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.fno.FoContractFacts
import com.bysel.trader.data.models.HistoryCandle
import com.bysel.trader.data.models.SavedPaperPractice
import com.bysel.trader.data.models.StrategyPayoffPoint
import com.bysel.trader.portfolio.PaperFoMark
import androidx.compose.ui.Alignment
import com.bysel.trader.ui.components.PriceHistoryChart
import com.bysel.trader.ui.theme.LocalAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

private data class CandleSim(
    val right: String,
    val strike: Double,
    val entryPremium: Double,
    val entrySpot: Double,
)

@Composable
fun FoCandlePractice(
    symbol: String,
    expiry: String,
    candles: List<HistoryCandle>,
    loading: Boolean,
    iv: Double,
    onReload: () -> Unit,
    onSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    asScreen: Boolean = false,
    future: Boolean = false,
    bookNote: String? = null,
    saved: SavedPaperPractice? = null,
    onSession: (SavedPaperPractice?) -> Unit = {},
    onBook: (right: String, strike: Double, price: Double, lots: Int) -> Unit = { _, _, _, _ -> },
    onFlatten: (right: String, strike: Double, price: Double, lots: Int) -> Unit = { _, _, _, _ -> },
    onExtend: () -> Unit = {},
    optionsContent: @Composable () -> Unit = {},
    /** Exchange lot from the loaded chain/board; falls back to the local table. */
    lotSize: Int? = null,
) {
    val theme = LocalAppTheme.current
    var cursor by remember(symbol) { mutableIntStateOf(0) }
    var playing by remember(symbol) { mutableStateOf(false) }
    var speed by remember(symbol) { mutableIntStateOf(2) }
    var finished by remember(symbol) { mutableStateOf(false) }
    var sim by remember(symbol) { mutableStateOf<CandleSim?>(null) }
    var closedSummary by remember(symbol) { mutableStateOf<String?>(null) }
    var fullscreen by remember(symbol) { mutableStateOf(false) }
    var showOptions by remember(symbol) { mutableStateOf(false) }
    var strikeOffset by remember(symbol) { mutableIntStateOf(0) }
    var lots by remember(symbol) { mutableIntStateOf((saved?.lots ?: 1).coerceIn(1, 5)) }
    var booked by remember(symbol) { mutableStateOf(saved?.booked == true) }
    var primed by remember(symbol) { mutableStateOf(false) }
    var restored by remember(symbol) { mutableStateOf(false) }
    BackHandler(enabled = fullscreen) {
        playing = false
        fullscreen = false
    }
    LaunchedEffect(symbol, candles.size) {
        if (candles.isEmpty()) return@LaunchedEffect
        val savedMatches = saved != null &&
            (saved.symbol.isNullOrBlank() || saved.symbol.equals(symbol, ignoreCase = true))
        if (!restored && saved != null && savedMatches) {
            sim = CandleSim(saved.right, saved.strike, saved.entryPremium, saved.entrySpot)
            cursor = saved.cursor.coerceIn(0, candles.lastIndex)
            speed = saved.speed.coerceIn(1, 4)
            lots = saved.lots.coerceIn(1, 5)
            booked = saved.booked
            restored = true
            primed = true
            return@LaunchedEffect
        }
        if (!primed) {
            cursor = openingCursor(candles.size)
            primed = true
            restored = true
        } else if (finished && cursor < candles.lastIndex) {
            finished = false
            playing = true
        }
    }
    LaunchedEffect(restored, sim, cursor, lots, speed, booked) {
        if (!restored) return@LaunchedEffect
        val open = sim
        onSession(
            if (open == null) {
                null
            } else {
                SavedPaperPractice(
                    right = open.right,
                    strike = open.strike,
                    entryPremium = open.entryPremium,
                    entrySpot = open.entrySpot,
                    lots = lots.coerceIn(1, 5),
                    cursor = cursor,
                    speed = speed,
                    booked = booked,
                    iv = if (iv > 0.0) iv else 0.16,
                    symbol = symbol.trim().uppercase(),
                )
            },
        )
    }
    LaunchedEffect(playing, speed, symbol, candles.size) {
        if (!playing || candles.size < 2) return@LaunchedEffect
        val stepMs = when (speed) {
            4 -> 160L
            2 -> 350L
            else -> 700L
        }
        val last = candles.lastIndex
        while (isActive) {
            delay(stepMs)
            if (!isActive) break
            val next = cursor + 1
            if (next > last) {
                finished = true
                playing = false
                onExtend()
                break
            }
            cursor = next
        }
    }
    val safeCursor = cursor.coerceIn(0, (candles.size - 1).coerceAtLeast(0))
    val shown = if (candles.isEmpty()) emptyList() else candles.take(safeCursor + 1)
    val bar = candles.getOrNull(safeCursor)
    val spot = bar?.close ?: 0.0
    val atmStrike = if (spot > 0.0) practiceStrike(symbol, spot) else 0.0
    val step = if (spot > 0.0) strikeStep(symbol, spot) else 0.0
    val strike = if (atmStrike > 0.0) atmStrike + strikeOffset * step else 0.0
    val days = if (bar == null) 7.0 else daysAhead(bar.timestamp, expiry)
    val vol = if (iv > 0.0) iv else 0.16
    val lot = lotSize?.takeIf { it > 0 } ?: practiceLot(symbol, spot)
    val callNow = if (spot > 0.0) PaperFoMark.premium(spot, strike, days, vol, call = true) else 0.0
    val putNow = if (spot > 0.0) PaperFoMark.premium(spot, strike, days, vol, call = false) else 0.0

    if (asScreen) {
        var showChain by remember(symbol) { mutableStateOf(false) }
        var screenStarted by remember(symbol) { mutableStateOf(false) }
        LaunchedEffect(symbol, candles.size) {
            if (!screenStarted && candles.size > 1 && !finished) {
                screenStarted = true
                playing = true
            }
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(theme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showChain,
                        onClick = { showChain = false },
                        label = { Text(stringResource(R.string.practice_chart), maxLines = 1) },
                    )
                    FilterChip(
                        selected = showChain,
                        onClick = { showChain = true },
                        label = { Text(stringResource(R.string.practice_options), maxLines = 1) },
                    )
                }
                Text(
                    "${symbol.uppercase()} · lot $lot",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (!showChain) {
                PracticeSymbolPicker(
                    symbol = symbol,
                    enabled = sim == null,
                    onSymbol = {
                        playing = false
                        onSymbol(it)
                    },
                )
                com.bysel.trader.ui.components.DataSourceBadge(label = com.bysel.trader.ui.components.replayDataLabel)
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.primary)
            }
            if (shown.isEmpty() && !loading && !showChain) {
                Text(
                    "No 15-minute candles for ${symbol.uppercase()} yet. Gold Mini is not listed until a 15-minute series exists.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onReload) { Text(stringResource(R.string.practice_load_candles)) }
            }
            if (shown.isNotEmpty() && !showChain) {
                PriceHistoryChart(
                    history = shown,
                    symbol = symbol,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 180.dp),
                    isDarkTheme = !theme.isLight,
                    rangeLabel = "5D",
                    expandPlot = true,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Bar ${safeCursor + 1} of ${candles.size} · close ₹${String.format("%.2f", spot)}",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (candles.size > 1) {
                        Slider(
                            value = safeCursor.toFloat(),
                            onValueChange = {
                                playing = false
                                finished = false
                                cursor = it.toInt()
                            },
                            valueRange = 0f..(candles.size - 1).toFloat(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = {
                            if (playing) {
                                playing = false
                            } else if (candles.size > 1 && safeCursor >= candles.lastIndex) {
                                onExtend()
                            } else if (candles.size > 1) {
                                finished = false
                                playing = true
                            }
                        }, enabled = candles.size > 1) {
                            Text(stringResource(if (playing) R.string.practice_pause else if (finished) R.string.practice_check_bars else R.string.practice_play), maxLines = 1)
                        }
                        if (finished) {
                            OutlinedButton(onClick = {
                                cursor = openingCursor(candles.size)
                                finished = false
                                playing = candles.size > 1
                            }) { Text(stringResource(R.string.practice_replay), maxLines = 1) }
                        }
                        listOf(1, 2, 4).forEach { pace ->
                            if (pace == speed) {
                                Button(onClick = { speed = pace }) { Text("${pace}×", maxLines = 1) }
                            } else {
                                OutlinedButton(onClick = { speed = pace }) { Text("${pace}×", maxLines = 1) }
                            }
                        }
                    }
                    PracticeOrderControls(
                        future = future,
                        spot = spot,
                        strike = strike,
                        atmStrike = atmStrike,
                        step = step,
                        strikeOffset = strikeOffset,
                        onStrikeOffset = { strikeOffset = it },
                        callNow = callNow,
                        putNow = putNow,
                        lots = lots,
                        lot = lot,
                        simOpen = sim != null,
                        onLots = { lots = it },
                        onOpen = { right, premium ->
                            closedSummary = null
                            booked = false
                            sim = CandleSim(right, if (future) 0.0 else strike, premium, spot)
                        },
                    )
                    sim?.let { open ->
                        PracticeResult(
                            open = open,
                            spot = spot,
                            days = days,
                            vol = vol,
                            lot = lot,
                            lots = lots,
                            expiry = expiry,
                            bookNote = bookNote,
                            booked = booked,
                            onClose = { summary, price ->
                                if (booked) onFlatten(open.right, open.strike, price, lots)
                                booked = false
                                closedSummary = summary
                                sim = null
                            },
                            onBook = { right, strike, price, bookedLots ->
                                booked = true
                                onBook(right, strike, price, bookedLots)
                            },
                        )
                    }
                    closedSummary?.let { summary ->
                        Text(summary, color = theme.text, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
            if (showChain) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    optionsContent()
                }
            }
        }
        return
    }

    Card(colors = CardDefaults.cardColors(containerColor = theme.card)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (future) "Futures candle practice" else "Candle practice",
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                if (future) {
                    "Long gains if the next bars rise. Short gains if they fall. P&L is the move times the lots you pick."
                } else {
                    "Buy a call if the next bars look higher. Buy a put if they look lower. Book this fill writes the lots you pick into Portfolio → F&O."
                },
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.primary)
            }
            if (shown.isEmpty() && !loading) {
                Text(
                    "No 15-minute candles for ${symbol.uppercase()} yet.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onReload) { Text(stringResource(R.string.practice_load_candles)) }
            }
            PracticeSymbolPicker(
                symbol = symbol,
                enabled = sim == null,
                onSymbol = {
                    playing = false
                    onSymbol(it)
                },
            )
            Text(
                "${symbol.uppercase()} · 1 lot = $lot units" +
                    (if (spot > 0.0) " · ₹${String.format("%,.0f", spot * lot)} notional" else "") +
                    (if (expiry.isNotBlank()) " · expiry $expiry" else ""),
                color = theme.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = {
                    if (candles.size > 1 && (finished || safeCursor >= candles.lastIndex)) {
                        cursor = openingCursor(candles.size)
                        finished = false
                    }
                    showOptions = false
                    fullscreen = true
                    playing = candles.size > 1
                },
                enabled = shown.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.practice_full_screen), maxLines = 1) }
        }
    }
    if (fullscreen) {
        Dialog(
            onDismissRequest = {
                playing = false
                fullscreen = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = {
                        playing = false
                        fullscreen = false
                    }) { Text(stringResource(R.string.practice_exit_full_screen), maxLines = 1) }
                    if (!future) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showOptions,
                                onClick = { showOptions = false },
                                label = { Text(stringResource(R.string.practice_chart), maxLines = 1) },
                            )
                            FilterChip(
                                selected = showOptions,
                                onClick = { showOptions = true },
                                label = { Text(stringResource(R.string.practice_options), maxLines = 1) },
                            )
                        }
                    } else {
                        Text(symbol.uppercase(), color = theme.text, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.primary)
                }
                if (shown.isEmpty() && !loading) {
                    Text(
                        "No 15-minute candles for ${symbol.uppercase()} yet.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                    OutlinedButton(onClick = onReload) { Text(stringResource(R.string.practice_load_candles)) }
                }
                if (shown.isNotEmpty() && !showOptions) {
                    PriceHistoryChart(
                        history = shown,
                        symbol = symbol,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isDarkTheme = !theme.isLight,
                        rangeLabel = "5D",
                        expandPlot = true,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Bar ${safeCursor + 1} of ${candles.size} · close ₹${String.format("%.2f", spot)}",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (candles.size > 1) {
                            Slider(
                                value = safeCursor.toFloat(),
                                onValueChange = {
                                    playing = false
                                    finished = false
                                    cursor = it.toInt()
                                },
                                valueRange = 0f..(candles.size - 1).toFloat(),
                            )
                        }
                        Text(
                            when {
                                playing -> "Graph moving · ${speed}×"
                                finished -> "Caught up to the last saved 15-minute bar."
                                else -> "Graph paused."
                            },
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = {
                                if (playing) {
                                    playing = false
                                } else if (candles.size > 1 && safeCursor >= candles.lastIndex) {
                                    onExtend()
                                } else if (candles.size > 1) {
                                    finished = false
                                    playing = true
                                }
                            }, enabled = candles.size > 1) {
                                Text(stringResource(if (playing) R.string.practice_pause else if (finished) R.string.practice_check_bars else R.string.practice_play), maxLines = 1)
                            }
                            if (finished) {
                                OutlinedButton(onClick = {
                                    cursor = openingCursor(candles.size)
                                    finished = false
                                    playing = candles.size > 1
                                }) { Text(stringResource(R.string.practice_replay), maxLines = 1) }
                            }
                            listOf(1, 2, 4).forEach { pace ->
                                if (pace == speed) {
                                    Button(onClick = { speed = pace }) { Text("${pace}×", maxLines = 1) }
                                } else {
                                    OutlinedButton(onClick = { speed = pace }) { Text("${pace}×", maxLines = 1) }
                                }
                            }
                        }
                        PracticeOrderControls(
                            future = future,
                            spot = spot,
                            strike = strike,
                            atmStrike = atmStrike,
                            step = step,
                            strikeOffset = strikeOffset,
                            onStrikeOffset = { strikeOffset = it },
                            callNow = callNow,
                            putNow = putNow,
                            lots = lots,
                            lot = lot,
                            simOpen = sim != null,
                            onLots = { lots = it },
                            onOpen = { right, premium ->
                                closedSummary = null
                                booked = false
                                sim = CandleSim(right, if (future) 0.0 else strike, premium, spot)
                            },
                        )
                        sim?.let { open ->
                            PracticeResult(
                                open = open,
                                spot = spot,
                                days = days,
                                vol = vol,
                                lot = lot,
                                lots = lots,
                                expiry = expiry,
                                bookNote = bookNote,
                                booked = booked,
                                onClose = { summary, price ->
                                    if (booked) onFlatten(open.right, open.strike, price, lots)
                                    booked = false
                                    closedSummary = summary
                                    sim = null
                                },
                                onBook = { right, strike, price, bookedLots ->
                                    booked = true
                                    onBook(right, strike, price, bookedLots)
                                },
                            )
                        }
                        closedSummary?.let { summary ->
                            Text(summary, color = theme.text, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
                if (showOptions) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        optionsContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeSymbolPicker(
    symbol: String,
    enabled: Boolean,
    onSymbol: (String) -> Unit,
) {
    val current = symbol.trim().uppercase()
    val picks = remember(current) {
        if (current in FoContractFacts.PRACTICE_PICKS) FoContractFacts.PRACTICE_PICKS
        else listOf(current) + FoContractFacts.PRACTICE_PICKS
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        picks.forEach { pick ->
            FilterChip(
                selected = pick == current,
                onClick = { if (pick != current) onSymbol(pick) },
                enabled = enabled || pick == current,
                label = { Text(pick, maxLines = 1, fontSize = 11.sp) },
            )
        }
    }
}

@Composable
private fun PracticeOrderControls(
    future: Boolean,
    spot: Double,
    strike: Double,
    atmStrike: Double,
    step: Double,
    strikeOffset: Int,
    onStrikeOffset: (Int) -> Unit,
    callNow: Double,
    putNow: Double,
    lots: Int,
    lot: Int,
    simOpen: Boolean,
    onLots: (Int) -> Unit,
    onOpen: (right: String, premium: Double) -> Unit,
) {
    val theme = LocalAppTheme.current
    if (!future && spot > 0.0 && step > 0.0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (-2..2).forEach { offset ->
                val price = atmStrike + offset * step
                FilterChip(
                    selected = strikeOffset == offset,
                    onClick = { if (!simOpen) onStrikeOffset(offset) },
                    enabled = !simOpen,
                    label = {
                        Text(
                            if (offset == 0) "ATM ${String.format("%.0f", price)}" else String.format("%.0f", price),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
    if (!future) {
        Text(
            "Call ₹${String.format("%.2f", callNow)} · put ₹${String.format("%.2f", putNow)} · strike ₹${String.format("%.0f", strike)}",
            color = theme.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..5).forEach { count ->
            FilterChip(
                selected = lots == count,
                onClick = { if (!simOpen) onLots(count) },
                enabled = !simOpen,
                label = { Text(if (count == 1) "1 lot" else "$count lots", maxLines = 1) },
            )
        }
    }
    if (lot > 0) {
        Text(
            "1 lot = $lot units" +
                if (spot > 0.0) " · $lots lot${if (lots == 1) "" else "s"} controls ₹${String.format("%,.0f", spot * lot * lots)}" else "",
            color = theme.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (future) {
            Button(
                onClick = { onOpen("LONG", spot) },
                enabled = spot > 0.0 && !simOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Long", maxLines = 1) }
            Button(
                onClick = { onOpen("SHORT", spot) },
                enabled = spot > 0.0 && !simOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Short", maxLines = 1) }
        } else {
            Button(
                onClick = { onOpen("CALL", callNow) },
                enabled = spot > 0.0 && callNow > 0.0 && !simOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Buy call", maxLines = 1) }
            Button(
                onClick = { onOpen("PUT", putNow) },
                enabled = spot > 0.0 && putNow > 0.0 && !simOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Buy put", maxLines = 1) }
        }
    }
}

@Composable
private fun PracticeResult(
    open: CandleSim,
    spot: Double,
    days: Double,
    vol: Double,
    lot: Int,
    lots: Int,
    expiry: String,
    bookNote: String?,
    booked: Boolean,
    onClose: (String, Double) -> Unit,
    onBook: (right: String, strike: Double, price: Double, lots: Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val contracts = lot * lots.coerceIn(1, 5)
    val futureSide = open.right == "LONG" || open.right == "SHORT"
    val mark = if (futureSide) spot else PaperFoMark.premium(spot, open.strike, days, vol, open.right == "CALL")
    val pnl = when (open.right) {
        "LONG" -> (spot - open.entrySpot) * contracts
        "SHORT" -> (open.entrySpot - spot) * contracts
        else -> (mark - open.entryPremium) * contracts
    }
    val maxLoss = if (futureSide) null else open.entryPremium * contracts
    val lotLabel = if (lots == 1) "1 lot" else "$lots lots"
    val headline = when (open.right) {
        "LONG" -> "Long from ₹${String.format("%.2f", open.entrySpot)}. This bar ₹${String.format("%.2f", spot)}. $lotLabel P&L ₹${String.format("%.0f", pnl)}."
        "SHORT" -> "Short from ₹${String.format("%.2f", open.entrySpot)}. This bar ₹${String.format("%.2f", spot)}. $lotLabel P&L ₹${String.format("%.0f", pnl)}."
        "PUT" -> "Put bought at ₹${String.format("%.2f", open.entryPremium)}. This bar ₹${String.format("%.2f", mark)}. $lotLabel P&L ₹${String.format("%.0f", pnl)}."
        else -> "Call bought at ₹${String.format("%.2f", open.entryPremium)}. This bar ₹${String.format("%.2f", mark)}. $lotLabel P&L ₹${String.format("%.0f", pnl)}."
    }
    Text(
        headline,
        color = if (pnl >= 0) theme.positive else theme.negative,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    if (maxLoss != null) {
        Text(
            "Most you can lose is ₹${String.format("%.0f", maxLoss)}, the premium for $lotLabel.",
            color = theme.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Text(
            "This premium is a model from the candle close. It is not a traded option price.",
            color = theme.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
    if (open.right == "SHORT" && !booked) {
        Text(
            "Booking a short opens it in Portfolio. Cash is not credited, and the loss is not capped.",
            color = theme.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                val price = if (futureSide) open.entrySpot else open.entryPremium
                onBook(open.right, open.strike, price, lots)
            },
            enabled = !booked && expiry.isNotBlank() && (futureSide || open.entryPremium > 0.0),
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(if (booked) R.string.practice_booked else R.string.practice_book), maxLines = 1) }
        OutlinedButton(
            onClick = {
                val flattenPrice = if (futureSide) spot else mark
                onClose(
                    "Closed the ${open.right.lowercase()} . $lotLabel result ₹${String.format("%.0f", pnl)}.",
                    flattenPrice,
                )
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(if (booked) R.string.practice_close_booked else R.string.practice_close), maxLines = 1) }
    }
    if (!bookNote.isNullOrBlank()) {
        Text(bookNote, color = theme.text, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun PayoffCurveChart(
    points: List<StrategyPayoffPoint>,
    spot: Double,
) {
    val theme = LocalAppTheme.current
    if (points.size < 2) return
    val minSpot = points.minOf { it.spot }
    val maxSpot = points.maxOf { it.spot }
    val minPay = points.minOf { it.payoff }
    val maxPay = points.maxOf { it.payoff }
    val spanX = (maxSpot - minSpot).takeIf { it > 0.0 } ?: 1.0
    val spanY = (maxPay - minPay).takeIf { abs(it) > 0.0 } ?: 1.0
    Text(
        "Payoff at expiry",
        color = theme.text,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.spot - minSpot) / spanX).toFloat() * size.width
            val y = size.height - (((point.payoff - minPay) / spanY).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val zeroY = size.height - (((0.0 - minPay) / spanY).toFloat() * size.height)
        drawLine(
            color = theme.textSecondary,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
        )
        if (spot in minSpot..maxSpot) {
            val spotX = ((spot - minSpot) / spanX).toFloat() * size.width
            drawLine(
                color = theme.primary,
                start = Offset(spotX, 0f),
                end = Offset(spotX, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawPath(
            path = path,
            color = theme.text,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    Text(
        "Horizontal line is zero. Vertical line is today's spot. The curve is the recipe at expiry.",
        color = theme.textSecondary,
        fontSize = 10.sp,
    )
}

private fun openingCursor(size: Int): Int {
    if (size <= 1) return 0
    return (size / 5).coerceIn(8, 24).coerceAtMost(size - 1)
}

private fun strikeStep(symbol: String, spot: Double): Double {
    when (symbol.trim().uppercase()) {
        "GOLDM", "BANKNIFTY", "NIFTYBANK", "SENSEX" -> return 100.0
        "NIFTY", "NIFTY50", "FINNIFTY" -> return 50.0
        "MIDCPNIFTY" -> return 25.0
    }
    return when {
        spot >= 1000.0 -> 50.0
        spot >= 300.0 -> 20.0
        else -> 10.0
    }
}

private fun practiceStrike(symbol: String, spot: Double): Double {
    val step = strikeStep(symbol, spot)
    return round(spot / step) * step
}

private fun practiceLot(symbol: String, spot: Double): Int = FoContractFacts.lotSize(symbol, spot)

private fun daysAhead(timestamp: Long, expiry: String): Double {
    val expiryDate = runCatching { LocalDate.parse(expiry.trim().take(10)) }.getOrNull() ?: return 7.0
    val ms = when {
        timestamp <= 0L -> 0L
        timestamp < 10_000_000_000L -> timestamp * 1000L
        else -> timestamp
    }
    val barDate = if (ms > 0L) {
        Instant.ofEpochMilli(ms).atZone(IST).toLocalDate()
    } else {
        LocalDate.now(IST)
    }
    return ChronoUnit.DAYS.between(barDate, expiryDate).toDouble().coerceAtLeast(1.0)
}

