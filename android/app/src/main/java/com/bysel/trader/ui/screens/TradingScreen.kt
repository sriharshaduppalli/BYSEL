package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.MarketStatus
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private fun formatVolume(v: Long?): String {
    if (v == null) return "N/A"
    return when {
        v >= 1_000_000 -> String.format("%.2fM", v / 1_000_000.0)
        v >= 1_000 -> String.format("%.1fK", v / 1_000.0)
        else -> v.toString()
    }
}

private fun formatSignedPct(value: Double): String {
    return "${if (value >= 0) "+" else ""}${String.format("%.2f", value)}%"
}

private fun formatCurrency(value: Double): String {
    return "₹${String.format("%,.2f", value)}"
}

private data class WatchlistInsight(
    val quote: Quote,
    val momentum: String,
    val risk: String,
    val confidence: Int,
    val rationale: String,
    val flags: List<String>
)

private fun computeWatchlistInsight(quote: Quote): WatchlistInsight {
    val spreadPct = if (quote.bid != null && quote.ask != null && quote.last > 0.0) {
        ((quote.ask - quote.bid) / quote.last) * 100.0
    } else {
        0.0
    }
    val intradayRangePct = if (quote.dayHigh != null && quote.dayLow != null && quote.last > 0.0) {
        ((quote.dayHigh - quote.dayLow) / quote.last) * 100.0
    } else {
        0.0
    }
    val volumeRatio = if (quote.volume != null && quote.avgVolume != null && quote.avgVolume > 0) {
        quote.volume.toDouble() / quote.avgVolume
    } else {
        null
    }

    val momentum = when {
        quote.pctChange >= 1.25 -> "Bullish"
        quote.pctChange <= -1.25 -> "Bearish"
        else -> "Sideways"
    }

    var riskScore = 0
    if (intradayRangePct >= 3.5) riskScore += 2 else if (intradayRangePct >= 2.0) riskScore += 1
    if (abs(quote.pctChange) >= 3.0) riskScore += 2 else if (abs(quote.pctChange) >= 1.5) riskScore += 1
    if (spreadPct >= 0.45) riskScore += 2 else if (spreadPct >= 0.2) riskScore += 1

    val risk = when {
        riskScore >= 4 -> "High"
        riskScore >= 2 -> "Medium"
        else -> "Low"
    }

    val confidenceRaw = 58 +
        (abs(quote.pctChange) * 6.0).roundToInt() +
        (if ((volumeRatio ?: 0.0) >= 1.4) 8 else 0) -
        (if (spreadPct >= 0.45) 8 else 0)
    val confidence = confidenceRaw.coerceIn(35, 92)

    val rationale = when (momentum) {
        "Bullish" -> "Strength with ${formatSignedPct(quote.pctChange)} move${if (volumeRatio != null) " and ${String.format("%.1f", volumeRatio)}x volume" else ""}."
        "Bearish" -> "Pressure with ${formatSignedPct(quote.pctChange)} decline; protect downside before averaging."
        else -> "Range-bound action; prefer staged entries near support levels."
    }

    val flags = mutableListOf<String>()
    if (abs(quote.pctChange) >= 2.0) flags.add("Move ${formatSignedPct(quote.pctChange)}")
    if (intradayRangePct >= 3.0) flags.add("Range ${String.format("%.1f", intradayRangePct)}%")
    if (volumeRatio != null && volumeRatio >= 1.5) flags.add("${String.format("%.1f", volumeRatio)}x vol")
    if (spreadPct >= 0.35) flags.add("Wide spread")
    if (flags.isEmpty()) flags.add("Stable")

    return WatchlistInsight(
        quote = quote,
        momentum = momentum,
        risk = risk,
        confidence = confidence,
        rationale = rationale,
        flags = flags
    )
}

@Composable
fun TradingScreen(
    isLoading: Boolean,
    error: String?,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onAddFunds: (Double, String) -> Unit,
    onErrorDismiss: () -> Unit,
    viewModel: com.bysel.trader.viewmodel.TradingViewModel
) {
    // Start fast-refresh while this screen is visible; stop on dispose
    DisposableEffect(viewModel) {
        viewModel.startFastRefresh()
        onDispose { viewModel.stopFastRefresh() }
    }
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var newWatchSymbol by remember { mutableStateOf("") }
    val watchlistSymbols by viewModel.watchlist.collectAsState()
    val liveQuotes by viewModel.quotes.collectAsState()
    val lastQuoteUpdateAt by viewModel.lastQuoteUpdateAt.collectAsState()
    val statusNow by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val quoteFreshnessLabel = remember(lastQuoteUpdateAt, statusNow) {
        if (lastQuoteUpdateAt <= 0L) {
            "Syncing..."
        } else {
            val ageSec = ((statusNow - lastQuoteUpdateAt) / 1000L).coerceAtLeast(0L)
            when {
                ageSec <= 4L -> "Live • just now"
                ageSec < 60L -> "Live • ${ageSec}s ago"
                ageSec < 600L -> "Delayed • ${ageSec / 60}m ago"
                else -> "Stale • ${ageSec / 60}m ago"
            }
        }
    }
    val quoteFreshnessColor = if (lastQuoteUpdateAt <= 0L) {
        Color(0xFFFFC107)
    } else {
        val ageSec = ((statusNow - lastQuoteUpdateAt) / 1000L).coerceAtLeast(0L)
        when {
            ageSec <= 4L -> LocalAppTheme.current.positive
            ageSec < 60L -> Color(0xFF64B5F6)
            ageSec < 600L -> Color(0xFFFFC107)
            else -> LocalAppTheme.current.negative
        }
    }
    val watchlistInsights = remember(watchlistSymbols, liveQuotes) {
        val quoteMap = liveQuotes.associateBy { it.symbol.uppercase() }
        watchlistSymbols
            .mapNotNull { symbol -> quoteMap[symbol.uppercase()]?.let { computeWatchlistInsight(it) } }
            .sortedByDescending { it.confidence }
    }
    // AI Trade Coach Dialog
    val tradeCoachTip by viewModel.tradeCoachTip.collectAsState()
    if (tradeCoachTip != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearTradeCoachTip() },
            title = { Text("Coach says…", fontWeight = FontWeight.Bold) },
            text = { Text(tradeCoachTip ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.clearTradeCoachTip() }) {
                    Text("Got it!")
                }
            }
        )
    }
    var selectedQuote by remember { mutableStateOf<Quote?>(null) }
    var showAddFundsDialog by remember { mutableStateOf(false) }

    if (selectedQuote != null) {
        TradeDialog(
            quote = selectedQuote!!,
            walletBalance = walletBalance,
            marketStatus = marketStatus,
            onDismiss = {
                selectedQuote = null
                viewModel.clearPreTradeCopilotSignal()
            },
            onBuy = { qty -> onBuy(selectedQuote!!.symbol, qty) },
            onSell = { qty -> onSell(selectedQuote!!.symbol, qty) },
            viewModel = viewModel,
        )
    }

    if (showAddFundsDialog) {
        AddFundsDialog(
            onDismiss = { showAddFundsDialog = false },
            onAdd = { amount, upiProvider ->
                onAddFunds(amount, upiProvider)
                showAddFundsDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        // Watchlist header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row {
                watchlistSymbols.take(4).forEach { s ->
                    AssistChip(onClick = { viewModel.fetchAndSelectQuote(s) }, label = { Text(s) }, modifier = Modifier.padding(end = 8.dp))
                }
                if (watchlistSymbols.size > 4) {
                    AssistChip(onClick = { }, label = { Text("+${watchlistSymbols.size - 4}") })
                }
            }
            Button(onClick = { showAddWatchlistDialog = true }, modifier = Modifier.height(36.dp)) { Text("+ Watchlist") }
        }

        if (watchlistSymbols.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watchlist Intelligence",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalAppTheme.current.text
                        )
                        Text(
                            text = "${watchlistInsights.size}/${watchlistSymbols.size} live",
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (watchlistInsights.isEmpty()) {
                        Text(
                            text = "Pull to refresh to compute watchlist signals.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    } else {
                        watchlistInsights.take(3).forEach { insight ->
                            WatchlistInsightRow(
                                insight = insight,
                                onOpenTrade = { selectedQuote = insight.quote }
                            )
                        }
                    }
                }
            }
        }

        if (showAddWatchlistDialog) {
            AlertDialog(onDismissRequest = { showAddWatchlistDialog = false }, confirmButton = {
                TextButton(onClick = {
                    if (newWatchSymbol.isNotBlank()) {
                        viewModel.addToWatchlist(newWatchSymbol)
                        newWatchSymbol = ""
                    }
                    showAddWatchlistDialog = false
                }) { Text("Add") }
            }, title = { Text("Add to Watchlist") }, text = {
                OutlinedTextField(value = newWatchSymbol, onValueChange = { newWatchSymbol = it }, label = { Text("Symbol") })
            }, dismissButton = { TextButton(onClick = { showAddWatchlistDialog = false }) { Text("Cancel") } })
        }
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Trading Market",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = quoteFreshnessLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = quoteFreshnessColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Refresh", fontSize = 12.sp)
            }
        }

        // Market Status Banner
        if (marketStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (marketStatus.isOpen) Color(0xFF1B5E20).copy(alpha = 0.3f)
                    else Color(0xFFB71C1C).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (marketStatus.isOpen) "\u2B24" else "\u2B24",
                            fontSize = 10.sp,
                            color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = marketStatus.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (marketStatus.isOpen) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                        )
                    }
                }
            }
        }

        // Wallet Balance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "Wallet Balance",
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                        Text(
                            text = "\u20B9${String.format("%,.2f", walletBalance)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text
                        )
                    }
                }
                Button(
                    onClick = { showAddFundsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("+ Add Funds", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (error != null) {
            TraceAwareErrorSnackbar(
                error = error,
                onDismiss = onErrorDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        if (isLoading) {
                // show skeleton list placeholders for better cold-start UX
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    repeat(6) {
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), shape = RoundedCornerShape(12.dp)) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(16.dp)) {
                                // simple gray blocks as skeleton
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(16.dp)
                                        .background(Color.Gray.copy(alpha = 0.2f)))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(14.dp)
                                        .background(Color.Gray.copy(alpha = 0.15f)))
                                }
                            }
                        }
                    }
                }
        } else {
            // Use paged quotes for large lists and load more when scrolled
            val paged by viewModel.pagedQuotes.collectAsState()
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                enabled = true
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                itemsIndexed(paged) { index: Int, quote: Quote ->
                    TradingQuoteCard(quote) { selectedQuote = quote }
                    // prefetch next page when reaching near the end
                    if (index >= paged.size - 5) {
                        // launch in composition-safe scope
                        LaunchedEffect(index) { viewModel.loadNextQuotesPage() }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun TradingQuoteCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        fontSize = 13.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.End) {
                        Text(text = "O: ₹${String.format("%.2f", quote.open ?: quote.prevClose ?: quote.last)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "H/L: ₹${String.format("%.2f", quote.dayHigh ?: quote.last)}/${String.format("%.2f", quote.dayLow ?: quote.last)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Vol: ${formatVolume(quote.volume)}", fontSize = 11.sp, color = LocalAppTheme.current.textSecondary)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B050))
                ) {
                    Text("Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.negative)
                ) {
                    Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WatchlistInsightRow(
    insight: WatchlistInsight,
    onOpenTrade: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.quote.symbol,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "Conf ${insight.confidence}",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.momentum,
                    fontSize = 12.sp,
                    color = when (insight.momentum) {
                        "Bullish" -> LocalAppTheme.current.positive
                        "Bearish" -> LocalAppTheme.current.negative
                        else -> LocalAppTheme.current.textSecondary
                    }
                )
                Text(
                    text = "Risk ${insight.risk}",
                    fontSize = 12.sp,
                    color = when (insight.risk) {
                        "High" -> LocalAppTheme.current.negative
                        "Medium" -> Color(0xFFFFB300)
                        else -> LocalAppTheme.current.positive
                    }
                )
                Text(
                    text = formatSignedPct(insight.quote.pctChange),
                    fontSize = 12.sp,
                    color = if (insight.quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }

            Text(
                text = insight.rationale,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.flags.take(2).joinToString(" • "),
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary
                )
                TextButton(onClick = onOpenTrade) {
                    Text("Trade")
                }
            }
        }
    }
}

@Composable
fun TradeDialog(
    quote: Quote,
    walletBalance: Double,
    marketStatus: MarketStatus?,
    onDismiss: () -> Unit,
    onBuy: (Int) -> Unit,
    onSell: (Int) -> Unit,
    viewModel: com.bysel.trader.viewmodel.TradingViewModel,
) {
    var quantity by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var limitPriceInput by remember { mutableStateOf(String.format("%.2f", quote.last)) }
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsState()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsState()
    val copilotLoading by viewModel.copilotLoading.collectAsState()

    val qty = quantity.toIntOrNull() ?: 0
    val limitPrice = limitPriceInput.toDoubleOrNull() ?: 0.0
    val localExecutionPrice = if (orderType == "LIMIT" && limitPrice > 0.0) limitPrice else quote.last
    val localTradeValue = qty * localExecutionPrice
    val localBrokerage = localTradeValue * 0.0003
    val localExchangeFee = localTradeValue * 0.00034
    val localGst = (localBrokerage + localExchangeFee) * 0.18
    val localStampDuty = if (tradeType == "BUY") localTradeValue * 0.00015 else 0.0
    val localTotalCharges = localBrokerage + localExchangeFee + localGst + localStampDuty
    val localNetAmount = if (tradeType == "BUY") localTradeValue + localTotalCharges else localTradeValue - localTotalCharges
    val localCanAfford = walletBalance >= localNetAmount
    val isMarketOpen = marketStatus?.isOpen ?: false
    val intradayRangePct = if (quote.dayHigh != null && quote.dayLow != null && quote.last > 0.0) {
        ((quote.dayHigh - quote.dayLow) / quote.last) * 100.0
    } else {
        0.0
    }
    val spreadPct = if (quote.bid != null && quote.ask != null && quote.last > 0.0) {
        ((quote.ask - quote.bid) / quote.last) * 100.0
    } else {
        0.0
    }
    val priceGapPct = if (quote.last > 0.0) ((localExecutionPrice - quote.last) / quote.last) * 100.0 else 0.0
    val limitInvalid = orderType == "LIMIT" && limitPrice <= 0.0
    val limitDeviationWarning = orderType == "LIMIT" && abs(priceGapPct) > 3.0
    val limitDeviationHardBlock = orderType == "LIMIT" && abs(priceGapPct) > 8.0
    val localWalletUtilizationPct = if (tradeType == "BUY" && walletBalance > 0.0) {
        ((localNetAmount / walletBalance) * 100.0).coerceAtLeast(0.0)
    } else {
        0.0
    }
    val localImpactTag = when {
        localTradeValue >= 300_000 || qty >= 350 -> "High impact"
        localTradeValue >= 120_000 || qty >= 150 -> "Medium impact"
        else -> "Low impact"
    }
    val executionPrice = preTradeEstimate?.executionPrice ?: localExecutionPrice
    val tradeValue = preTradeEstimate?.tradeValue ?: localTradeValue
    val totalCharges = preTradeEstimate?.charges?.totalCharges ?: localTotalCharges
    val netAmount = preTradeEstimate?.netAmount ?: localNetAmount
    val canAfford = preTradeEstimate?.canAfford ?: localCanAfford
    val walletUtilizationPct = preTradeEstimate?.walletUtilizationPct ?: localWalletUtilizationPct
    val impactTag = preTradeEstimate?.impactTag ?: localImpactTag
    val effectiveSignal = preTradeEstimate?.signal ?: preTradeSignal
    val estimateWarnings = preTradeEstimate?.warnings ?: emptyList()
    val copilotBlocksTrade = effectiveSignal?.verdict?.equals("BLOCK", ignoreCase = true) == true

    DisposableEffect(quote.symbol) {
        onDispose {
            viewModel.clearPreTradeCopilotSignal()
        }
    }

    LaunchedEffect(quote.symbol, qty, tradeType, orderType, limitPriceInput, isMarketOpen) {
        if (qty <= 0 || !isMarketOpen || limitInvalid || limitDeviationHardBlock) {
            viewModel.clearPreTradeCopilotSignal()
            return@LaunchedEffect
        }

        delay(250)
        viewModel.fetchPreTradeEstimate(
            com.bysel.trader.data.models.AdvancedOrderRequest(
                symbol = quote.symbol,
                qty = qty,
                side = tradeType,
                orderType = orderType,
                validity = "DAY",
                limitPrice = if (orderType == "LIMIT") limitPrice else null,
                triggerPrice = null,
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        modifier = Modifier.height(650.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tradeType} ${quote.symbol}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = LocalAppTheme.current.text)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!isMarketOpen) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFB71C1C).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⚠ ${marketStatus?.message ?: "Market is closed"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wallet Balance", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
                        Text(
                            text = formatCurrency(walletBalance),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C4DFF)
                        )
                    }
                }

                Text(
                    text = "Current Price: ₹${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Day Range: ₹${String.format("%.2f", quote.dayLow ?: quote.last)} - ₹${String.format("%.2f", quote.dayHigh ?: quote.last)}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { tradeType = "BUY" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tradeType == "BUY") Color(0xFF00B050) else Color(0xFF2A2A2A)
                        )
                    ) {
                        Text("Buy", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { tradeType = "SELL" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tradeType == "SELL") LocalAppTheme.current.negative else Color(0xFF2A2A2A)
                        )
                    ) {
                        Text("Sell", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Order Type",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { orderType = "MARKET" },
                        label = { Text("Market") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (orderType == "MARKET") LocalAppTheme.current.primary.copy(alpha = 0.25f) else Color(0xFF2A2A2A)
                        )
                    )
                    AssistChip(
                        onClick = { orderType = "LIMIT" },
                        label = { Text("Limit") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (orderType == "LIMIT") LocalAppTheme.current.primary.copy(alpha = 0.25f) else Color(0xFF2A2A2A)
                        )
                    )
                }

                if (orderType == "LIMIT") {
                    OutlinedTextField(
                        value = limitPriceInput,
                        onValueChange = { limitPriceInput = it },
                        label = { Text("Limit Price", color = LocalAppTheme.current.textSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppTheme.current.text,
                            unfocusedTextColor = LocalAppTheme.current.textSecondary,
                            focusedBorderColor = LocalAppTheme.current.primary,
                            unfocusedBorderColor = Color(0xFF2A2A2A)
                        )
                    )
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity", color = LocalAppTheme.current.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppTheme.current.text,
                        unfocusedTextColor = LocalAppTheme.current.textSecondary,
                        focusedBorderColor = LocalAppTheme.current.primary,
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 5, 10, 25).forEach { quickQty ->
                        AssistChip(
                            onClick = { quantity = quickQty.toString() },
                            label = { Text("$quickQty") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2A2A2A))
                        )
                    }
                }

                if (qty > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Estimated Summary",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalAppTheme.current.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TradeSummaryLine("Execution", "₹${String.format("%.2f", executionPrice)}")
                            TradeSummaryLine("Trade Value", formatCurrency(tradeValue))
                            TradeSummaryLine("Est. Charges", formatCurrency(totalCharges))
                            preTradeEstimate?.let { estimate ->
                                TradeSummaryLine("Brokerage", formatCurrency(estimate.charges.brokerage))
                                TradeSummaryLine("Exchange Fee", formatCurrency(estimate.charges.exchangeFee))
                                TradeSummaryLine("GST", formatCurrency(estimate.charges.gst))
                                if (tradeType == "BUY") {
                                    TradeSummaryLine("Stamp Duty", formatCurrency(estimate.charges.stampDuty))
                                }
                            }
                            TradeSummaryLine(
                                if (tradeType == "BUY") "Est. Debit" else "Est. Credit",
                                formatCurrency(netAmount),
                                valueColor = if (tradeType == "BUY") LocalAppTheme.current.text else LocalAppTheme.current.positive
                            )
                            if (tradeType == "BUY") {
                                TradeSummaryLine("Wallet Utilization", "${String.format("%.1f", walletUtilizationPct)}%")
                            }
                            TradeSummaryLine("Impact", impactTag)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Copilot Pre-Trade Check",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalAppTheme.current.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            if (copilotLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = LocalAppTheme.current.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Analyzing order risk...",
                                        fontSize = 11.sp,
                                        color = LocalAppTheme.current.textSecondary
                                    )
                                }
                            } else {
                                effectiveSignal?.let { signal ->
                                    val verdictColor = when (signal.verdict.uppercase()) {
                                        "GO" -> LocalAppTheme.current.positive
                                        "CAUTION" -> Color(0xFFFFC107)
                                        "BLOCK" -> LocalAppTheme.current.negative
                                        else -> LocalAppTheme.current.textSecondary
                                    }
                                    TradeSummaryLine(
                                        "Verdict",
                                        "${signal.verdict} (${signal.confidence}%)",
                                        valueColor = verdictColor
                                    )
                                    signal.flags.take(2).forEach { flag ->
                                        Text(
                                            text = "• $flag",
                                            fontSize = 11.sp,
                                            color = LocalAppTheme.current.negative,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    signal.guidance.take(2).forEach { tip ->
                                        Text(
                                            text = "• $tip",
                                            fontSize = 11.sp,
                                            color = LocalAppTheme.current.textSecondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                } ?: Text(
                                    text = "Waiting for order inputs...",
                                    fontSize = 11.sp,
                                    color = LocalAppTheme.current.textSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Impact cues: range ${String.format("%.1f", intradayRangePct)}% • spread ${String.format("%.2f", spreadPct)}%",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    estimateWarnings.take(2).forEach { warning ->
                        Text(
                            text = "⚠ $warning",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (estimateWarnings.isEmpty() && tradeType == "BUY" && !canAfford) {
                        Text(
                            text = "⚠ Insufficient funds! Need ${formatCurrency((netAmount - walletBalance).coerceAtLeast(0.0))} more",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (estimateWarnings.isEmpty() && limitDeviationWarning) {
                        Text(
                            text = "⚠ Limit is ${String.format("%.2f", abs(priceGapPct))}% away from market price.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (estimateWarnings.isEmpty() && spreadPct >= 0.45) {
                        Text(
                            text = "⚠ Wide spread detected. Consider limit order for better execution control.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Text(
                        text = "Execution estimates are indicative; final fill and charges can vary with live liquidity.",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    if (copilotBlocksTrade) {
                        Text(
                            text = "Copilot has blocked this order. Adjust inputs and retry.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.negative,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (limitInvalid) {
                    Text(
                        text = "Enter a valid limit price to continue.",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.negative
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qty > 0) {
                        if (tradeType == "BUY") {
                            onBuy(qty)
                        } else {
                            onSell(qty)
                        }
                        onDismiss()
                    }
                },
                enabled = qty > 0 && isMarketOpen && !limitInvalid && !limitDeviationHardBlock && !copilotBlocksTrade && (tradeType == "SELL" || canAfford),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeType == "BUY") Color(0xFF00B050) else LocalAppTheme.current.negative,
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Text(if (orderType == "MARKET") tradeType else "LIMIT $tradeType", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppTheme.current.primary)
            }
        }
    )
}

@Composable
private fun TradeSummaryLine(
    label: String,
    value: String,
    valueColor: Color = LocalAppTheme.current.text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
        Text(value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AddFundsDialog(
    onDismiss: () -> Unit,
    onAdd: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val presetAmounts = listOf(10000.0, 25000.0, 50000.0, 100000.0)
    var selectedUpi by remember { mutableStateOf("") }
    val upiProviders = listOf(
        "GPay" to "com.google.android.apps.nbu.paisa.user",
        "PhonePe" to "com.phonepe.app",
        "Amazon Pay" to "in.amazon.mShop.android.shopping"
    )
    var selectedUpiPackage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text("Add Funds to Wallet", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (\u20B9)", color = LocalAppTheme.current.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppTheme.current.text,
                        unfocusedTextColor = LocalAppTheme.current.textSecondary,
                        focusedBorderColor = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color(0xFF2A2A2A)
                    )
                )

                Text("Quick Add", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { preset ->
                        Button(
                            onClick = { amount = preset.toInt().toString() },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("\u20B9${preset.toInt()/1000}K", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Select UPI Provider", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    upiProviders.forEach { (name, pkg) ->
                        Button(
                            onClick = {
                                selectedUpi = name
                                selectedUpiPackage = pkg
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedUpi == name) Color(0xFF7C4DFF) else Color(0xFF2A2A2A)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(name, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedUpi.isNotEmpty() && selectedUpiPackage.isNotEmpty()) {
                        onAdd(amt, selectedUpiPackage)
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedUpi.isNotEmpty() && selectedUpiPackage.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Add Funds", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppTheme.current.primary)
            }
        }
    )
}
