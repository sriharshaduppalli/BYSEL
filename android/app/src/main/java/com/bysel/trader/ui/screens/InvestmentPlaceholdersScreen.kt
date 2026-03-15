package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.*
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel

@Composable
private fun LoadingOrEmpty(title: String, subtitle: String, loading: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = LocalAppTheme.current.primary)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = LocalAppTheme.current.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = LocalAppTheme.current.textSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ActionBanner(viewModel: TradingViewModel) {
    val msg by viewModel.productActionMessage.collectAsState()
    if (!msg.isNullOrBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.primary.copy(alpha = 0.12f))) {
            Text(msg.orEmpty(), color = LocalAppTheme.current.text, modifier = Modifier.padding(12.dp))
        }
        LaunchedEffect(msg) { viewModel.clearProductActionMessage() }
    }
}

@Composable
private fun investmentTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalAppTheme.current.text,
    unfocusedTextColor = LocalAppTheme.current.text,
    disabledTextColor = LocalAppTheme.current.textSecondary,
    focusedLabelColor = LocalAppTheme.current.primary,
    unfocusedLabelColor = LocalAppTheme.current.textSecondary,
    disabledLabelColor = LocalAppTheme.current.textSecondary,
    focusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    unfocusedPlaceholderColor = LocalAppTheme.current.textSecondary,
    focusedBorderColor = LocalAppTheme.current.primary,
    unfocusedBorderColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.6f),
    disabledBorderColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.3f),
    cursorColor = LocalAppTheme.current.primary,
    focusedContainerColor = LocalAppTheme.current.card,
    unfocusedContainerColor = LocalAppTheme.current.card,
    disabledContainerColor = LocalAppTheme.current.card,
)

@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = TextStyle(color = LocalAppTheme.current.text),
    colors: TextFieldColors = investmentTextFieldColors(),
    singleLine: Boolean = false,
) {
    M3OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        textStyle = textStyle,
        colors = colors,
        singleLine = singleLine,
    )
}

private fun formatInvestmentCurrency(value: Double): String {
    return "₹${String.format("%,.2f", value)}"
}

@Composable
private fun PreTradeEstimateCard(estimate: PreTradeEstimateResponse) {
    Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Server Pre-Trade Estimate", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
            Text(
                "${estimate.symbol} ${estimate.side} • ${estimate.orderType}",
                color = LocalAppTheme.current.text,
                fontSize = 13.sp,
            )
            Text(
                "Live ${formatInvestmentCurrency(estimate.livePrice)} • Exec ${formatInvestmentCurrency(estimate.executionPrice)}",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                "Trade ${formatInvestmentCurrency(estimate.tradeValue)} • Charges ${formatInvestmentCurrency(estimate.charges.totalCharges)}",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                "${if (estimate.side == "BUY") "Debit" else "Credit"} ${formatInvestmentCurrency(estimate.netAmount)} • Impact ${estimate.impactTag}",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            if (estimate.side == "BUY") {
                Text(
                    "Wallet utilization ${String.format("%.1f", estimate.walletUtilizationPct)}% • ${if (estimate.canAfford) "Affordable" else "Insufficient funds"}",
                    color = if (estimate.canAfford) LocalAppTheme.current.textSecondary else LocalAppTheme.current.negative,
                    fontSize = 12.sp,
                )
            }
            estimate.warnings.take(3).forEach { warning ->
                Text("• $warning", color = LocalAppTheme.current.negative, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PreTradeSignalCard(title: String, signal: CopilotSignal) {
    Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
            Text("${signal.verdict} • ${signal.confidence}% confidence", color = LocalAppTheme.current.text)
            if (signal.flags.isNotEmpty()) {
                Text("Flags: ${signal.flags.joinToString(", ")}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
            }
            signal.guidance.take(4).forEach {
                Text("• $it", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MutualFundsScreen(viewModel: TradingViewModel) {
    val funds by viewModel.mutualFunds.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    val recommendations by viewModel.mutualFundRecommendations.collectAsState()
    val compareResult by viewModel.mutualFundCompare.collectAsState()
    var selected by remember { mutableStateOf<MutualFund?>(null) }
    var sipTarget by remember { mutableStateOf<MutualFund?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var sortBy by remember { mutableStateOf("name") }
    var sortOrder by remember { mutableStateOf("asc") }
    var compareCodes by remember { mutableStateOf(setOf<String>()) }
    var compareHint by remember { mutableStateOf<String?>(null) }
    var recommendationRisk by remember { mutableStateOf("MODERATE") }
    var recommendationGoal by remember { mutableStateOf("growth") }
    var recommendationHorizonInput by remember { mutableStateOf("5") }

    LaunchedEffect(Unit) { viewModel.loadMutualFunds(limit = 1000) }

    val categoryOptions = remember(funds) {
        listOf("ALL") + funds.map { it.category.uppercase() }.distinct().sorted()
    }

    val filteredFunds = remember(funds, searchQuery, selectedCategory, sortBy, sortOrder) {
        val queryToken = searchQuery.trim().lowercase()
        val categoryToken = selectedCategory.uppercase()
        val shortlisted = funds.filter { fund ->
            val categoryMatch = categoryToken == "ALL" || fund.category.uppercase() == categoryToken
            val queryMatch = queryToken.isBlank() ||
                fund.schemeName.lowercase().contains(queryToken) ||
                fund.schemeCode.contains(queryToken) ||
                (fund.fundHouse?.lowercase()?.contains(queryToken) == true)
            categoryMatch && queryMatch
        }
        sortMutualFundsLocal(shortlisted, sortBy = sortBy, sortOrder = sortOrder)
    }

    if (selected != null) {
        MutualFundDetailScreen(
            fund = selected!!,
            onBack = { selected = null },
            onStartSip = { sipTarget = selected }
        )
    } else if (funds.isEmpty()) {
        LoadingOrEmpty("Mutual Funds", "No funds found yet.", loading)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Mutual Funds", color = LocalAppTheme.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item { ActionBanner(viewModel) }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search funds or fund house") },
                            placeholder = { Text("Type scheme name, code, or fund house") },
                            textStyle = TextStyle(color = LocalAppTheme.current.text),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LocalAppTheme.current.text,
                                unfocusedTextColor = LocalAppTheme.current.text,
                                focusedLabelColor = LocalAppTheme.current.primary,
                                unfocusedLabelColor = LocalAppTheme.current.textSecondary,
                                focusedPlaceholderColor = LocalAppTheme.current.textSecondary,
                                unfocusedPlaceholderColor = LocalAppTheme.current.textSecondary,
                                focusedBorderColor = LocalAppTheme.current.primary,
                                unfocusedBorderColor = LocalAppTheme.current.textSecondary,
                                cursorColor = LocalAppTheme.current.primary,
                            ),
                            singleLine = true,
                        )

                        Text("Category", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryOptions.forEach { option ->
                                TextButton(onClick = { selectedCategory = option }) {
                                    val selectedText = if (selectedCategory == option) "● $option" else option
                                    Text(selectedText)
                                }
                            }
                        }

                        Text("Sort", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("name", "nav", "risk", "category").forEach { option ->
                                TextButton(onClick = { sortBy = option }) {
                                    val selectedText = if (sortBy == option) "● ${option.uppercase()}" else option.uppercase()
                                    Text(selectedText)
                                }
                            }
                            TextButton(onClick = { sortOrder = if (sortOrder == "asc") "desc" else "asc" }) {
                                Text(if (sortOrder == "asc") "ASC ↑" else "DESC ↓")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    compareCodes = emptySet()
                                    compareHint = null
                                    viewModel.loadMutualFunds(
                                        category = selectedCategory.takeIf { it != "ALL" },
                                        query = searchQuery.takeIf { it.isNotBlank() },
                                        sortBy = sortBy,
                                        sortOrder = sortOrder,
                                        limit = 1000,
                                    )
                                }
                            ) { Text("Refresh Data") }
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategory = "ALL"
                                    sortBy = "name"
                                    sortOrder = "asc"
                                    compareCodes = emptySet()
                                    compareHint = null
                                    viewModel.loadMutualFunds(limit = 1000)
                                }
                            ) { Text("Reset") }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI Best-Fit Finder", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("LOW", "MODERATE", "HIGH").forEach { profile ->
                                TextButton(onClick = { recommendationRisk = profile }) {
                                    Text(if (recommendationRisk == profile) "● $profile" else profile)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = recommendationGoal,
                            onValueChange = { recommendationGoal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Goal (growth, tax, income, index)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = recommendationHorizonInput,
                            onValueChange = { recommendationHorizonInput = it.filter { ch -> ch.isDigit() }.take(2) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Horizon (years)") },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val horizon = recommendationHorizonInput.toIntOrNull()?.coerceIn(1, 30) ?: 5
                                viewModel.loadMutualFundRecommendations(
                                    riskProfile = recommendationRisk,
                                    goal = recommendationGoal.takeIf { it.isNotBlank() },
                                    horizonYears = horizon,
                                    limit = 5,
                                )
                            }
                        ) {
                            Text("Get AI Recommendations")
                        }

                        val topRecommendations = recommendations?.recommendations.orEmpty().take(3)
                        if (topRecommendations.isNotEmpty()) {
                            Text("Top Matches", color = LocalAppTheme.current.text, fontWeight = FontWeight.Medium)
                            topRecommendations.forEach { item ->
                                Text(
                                    "• ${item.schemeName} (${item.suitabilityScore}/100)",
                                    color = LocalAppTheme.current.text,
                                    fontSize = 12.sp
                                )
                                Text(item.rationale, color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Showing ${filteredFunds.size} funds",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Selected for compare: ${compareCodes.size} (choose 2 to 4)",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        compareHint?.let {
                            Text(it, color = LocalAppTheme.current.negative, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.compareMutualFunds(compareCodes.toList()) },
                                enabled = compareCodes.size in 2..4,
                            ) {
                                Text("Compare Selected")
                            }
                            TextButton(onClick = {
                                compareCodes = emptySet()
                                compareHint = null
                                viewModel.clearMutualFundCompare()
                            }) {
                                Text("Clear Compare")
                            }
                        }
                    }
                }
            }

            items(filteredFunds) { fund ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                    modifier = Modifier.fillMaxWidth().clickable { selected = fund }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fund.schemeName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("NAV ₹${fund.nav} • ${fund.category}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Risk: ${fund.riskLevel ?: "N/A"} • House: ${fund.fundHouse ?: "N/A"}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (compareCodes.contains(fund.schemeCode)) {
                                    compareCodes = compareCodes - fund.schemeCode
                                } else if (compareCodes.size < 4) {
                                    compareCodes = compareCodes + fund.schemeCode
                                } else {
                                    compareHint = "You can compare up to 4 funds"
                                }
                            }) {
                                val selectedText = if (compareCodes.contains(fund.schemeCode)) "✓ Selected" else "Select for Compare"
                                Text(selectedText)
                            }
                            TextButton(onClick = { sipTarget = fund }) { Text("Start SIP") }
                        }
                    }
                }
            }
        }
    }

    compareResult?.let { result ->
        val comparisonText = result.funds.joinToString("\n") { item ->
            val tags = mutableListOf<String>()
            if (result.bestReturns1YSchemeCode == item.schemeCode) tags.add("Best 1Y")
            if (result.bestReturns3YSchemeCode == item.schemeCode) tags.add("Best 3Y")
            if (result.bestReturns5YSchemeCode == item.schemeCode) tags.add("Best 5Y")
            if (result.lowestRiskSchemeCode == item.schemeCode) tags.add("Lowest Risk")
            val badge = if (tags.isNotEmpty()) " [${tags.joinToString(", ")}]" else ""
            "• ${item.schemeName}$badge"
        }

        AlertDialog(
            onDismissRequest = { viewModel.clearMutualFundCompare() },
            title = { Text("Mutual Fund Comparison") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.summary, fontSize = 13.sp)
                    Text(comparisonText, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMutualFundCompare() }) { Text("Close") }
            }
        )
    }

    sipTarget?.let { fund ->
        SipCreateDialog(
            fundName = fund.schemeName,
            onDismiss = { sipTarget = null },
            onCreate = { amount, frequency, day ->
                viewModel.createSipForFund(fund.schemeCode, amount, frequency, day)
                sipTarget = null
            }
        )
    }
}

private fun sortMutualFundsLocal(
    funds: List<MutualFund>,
    sortBy: String,
    sortOrder: String,
): List<MutualFund> {
    val key = sortBy.lowercase()
    val sorted = when (key) {
        "nav" -> funds.sortedBy { it.nav }
        "risk" -> funds.sortedBy { riskRank(it.riskLevel, it.category) }
        "category" -> funds.sortedBy { it.category }
        else -> funds.sortedBy { it.schemeName.lowercase() }
    }
    return if (sortOrder.lowercase() == "desc") sorted.reversed() else sorted
}

private fun riskRank(riskLevel: String?, category: String?): Int {
    return when ((riskLevel ?: "").uppercase()) {
        "LOW", "LOW_MODERATE" -> 1
        "MODERATE" -> 2
        "MODERATE_HIGH", "HIGH" -> 3
        "VERY_HIGH" -> 4
        else -> when ((category ?: "").uppercase()) {
            "DEBT" -> 1
            "HYBRID", "SOLUTION" -> 2
            "INDEX", "EQUITY" -> 3
            else -> 2
        }
    }
}

@Composable
private fun MutualFundDetailScreen(fund: MutualFund, onBack: () -> Unit, onStartSip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(fund.schemeName, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scheme Code: ${fund.schemeCode}")
                Text("Category: ${fund.category}")
                Text("NAV: ₹${fund.nav} (${fund.navDate})")
                Text("Returns: 1Y ${fund.returns1Y ?: "-"}% • 3Y ${fund.returns3Y ?: "-"}% • 5Y ${fund.returns5Y ?: "-"}%")
                Text("Fund House: ${fund.fundHouse ?: "N/A"}")
                Text("Risk: ${fund.riskLevel ?: "N/A"}")
            }
        }
        Button(onClick = onStartSip, modifier = Modifier.fillMaxWidth()) { Text("Start SIP") }
    }
}

@Composable
fun IpoListingsScreen(viewModel: TradingViewModel) {
    val ipos by viewModel.ipoListings.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    var selected by remember { mutableStateOf<IPOListing?>(null) }
    var applyTarget by remember { mutableStateOf<IPOListing?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIpoListings() }

    if (selected != null) {
        IpoDetailScreen(
            ipo = selected!!,
            onBack = { selected = null },
            onApply = { if (selected?.status.equals("OPEN", true)) applyTarget = selected }
        )
    } else if (ipos.isEmpty()) {
        LoadingOrEmpty("IPO Listings", "No IPOs available right now.", loading)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("IPO Listings", color = LocalAppTheme.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item { ActionBanner(viewModel) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.loadIpoListings() }) { Text("Refresh IPOs") }
                }
            }
            items(ipos) { ipo ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                    modifier = Modifier.fillMaxWidth().clickable { selected = ipo }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ipo.companyName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("${ipo.status} • ${ipo.issueOpenDate} to ${ipo.issueCloseDate}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Price band: ₹${ipo.priceBandMin ?: 0.0} - ₹${ipo.priceBandMax ?: 0.0} • Lot: ${ipo.lotSize ?: 0}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    applyTarget?.let { ipo ->
        IpoApplyDialog(
            ipo = ipo,
            onDismiss = { applyTarget = null },
            onApply = { lots, upi, bid ->
                viewModel.applyForIpo(ipo.copy(priceBandMax = bid), lots = lots, upiId = upi)
                applyTarget = null
            }
        )
    }
}

@Composable
private fun IpoDetailScreen(ipo: IPOListing, onBack: () -> Unit, onApply: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(ipo.companyName, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Symbol: ${ipo.symbol}")
                Text("Status: ${ipo.status}")
                Text("Issue Window: ${ipo.issueOpenDate} - ${ipo.issueCloseDate}")
                Text("Listing Date: ${ipo.listingDate ?: "TBA"}")
                Text("Price Band: ₹${ipo.priceBandMin ?: "-"} - ₹${ipo.priceBandMax ?: "-"}")
                Text("Lot Size: ${ipo.lotSize ?: "-"}")
            }
        }
        if (ipo.status.equals("OPEN", true)) {
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply IPO") }
        }
    }
}

@Composable
fun EtfScreen(viewModel: TradingViewModel) {
    val etfs by viewModel.etfInstruments.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    var selected by remember { mutableStateOf<ETFInstrument?>(null) }

    LaunchedEffect(Unit) { viewModel.loadEtfs() }

    if (selected != null) {
        EtfDetailScreen(etf = selected!!, onBack = { selected = null })
    } else if (etfs.isEmpty()) {
        LoadingOrEmpty("ETFs", "No ETFs available right now.", loading)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("ETFs", color = LocalAppTheme.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item { ActionBanner(viewModel) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.loadEtfs() }) { Text("Refresh ETFs") }
                }
            }
            items(etfs) { etf ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                    modifier = Modifier.fillMaxWidth().clickable { selected = etf }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(etf.name, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("${etf.symbol} • ₹${etf.last} (${etf.pctChange}%)", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("AUM: ₹${etf.aumCr ?: 0.0} Cr • Expense: ${etf.expenseRatio ?: 0.0}%", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EtfDetailScreen(etf: ETFInstrument, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(etf.name, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Symbol: ${etf.symbol}")
                Text("Category: ${etf.category}")
                Text("Price: ₹${etf.last}")
                Text("Change: ${etf.pctChange}%")
                Text("AUM: ₹${etf.aumCr ?: "-"} Cr")
                Text("Expense Ratio: ${etf.expenseRatio ?: "-"}%")
            }
        }
    }
}

@Composable
fun SipPlansScreen(viewModel: TradingViewModel) {
    val plans by viewModel.sipPlans.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    var editTarget by remember { mutableStateOf<com.bysel.trader.data.models.SipPlan?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSipPlans() }

    if (plans.isEmpty()) {
        LoadingOrEmpty("My SIPs", "No SIP plans created for this user yet.", loading)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("My SIPs", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
        item { ActionBanner(viewModel) }
        items(plans) { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plan.schemeName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text("₹${plan.amount} ${plan.frequency} • Next: ${plan.nextInstallmentDate}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text("Status: ${if (plan.isActive) "ACTIVE" else "PAUSED"}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editTarget = plan }) { Text("Edit") }
                        if (plan.isActive) {
                            Button(onClick = { viewModel.pauseSipPlan(plan.id) }) { Text("Pause") }
                        } else {
                            Button(onClick = { viewModel.resumeSipPlan(plan.id) }) { Text("Resume") }
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { plan ->
        EditSipDialog(
            sip = plan,
            onDismiss = { editTarget = null },
            onSave = { amount, frequency, day ->
                viewModel.updateSipPlan(plan.id, amount, frequency, day)
                editTarget = null
            }
        )
    }
}

@Composable
fun MyIpoApplicationsScreen(viewModel: TradingViewModel) {
    val applications by viewModel.myIpoApplications.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    var statusFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) { viewModel.loadMyIpoApplications() }

    val filtered = if (statusFilter == "ALL") {
        applications
    } else {
        applications.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    if (applications.isEmpty()) {
        LoadingOrEmpty("My IPO Applications", "No IPO applications submitted yet.", loading)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("My IPO Applications", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
        item { ActionBanner(viewModel) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.loadMyIpoApplications() }) { Text("Refresh Applications") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "PENDING", "ALLOTTED", "REJECTED").forEach { status ->
                    Button(onClick = { statusFilter = status }) { Text(status) }
                }
            }
        }
        items(filtered) { app ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.companyName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text("${app.ipoId} • ${app.status}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text("Lots: ${app.lots} • Bid: ₹${app.bidPrice}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text("UPI: ${app.upiId} • ${app.appliedAt}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EditSipDialog(
    sip: com.bysel.trader.data.models.SipPlan,
    onDismiss: () -> Unit,
    onSave: (amount: Double, frequency: String, day: Int) -> Unit
) {
    var amountText by remember { mutableStateOf(sip.amount.toString()) }
    var frequencyText by remember { mutableStateOf(sip.frequency) }
    var dayText by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit SIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sip.schemeName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") })
                OutlinedTextField(value = frequencyText, onValueChange = { frequencyText = it.uppercase() }, label = { Text("Frequency") })
                OutlinedTextField(value = dayText, onValueChange = { dayText = it }, label = { Text("Installment Day") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull() ?: return@Button
                val day = dayText.toIntOrNull() ?: 5
                onSave(amount, frequencyText, day)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SipCreateDialog(
    fundName: String,
    onDismiss: () -> Unit,
    onCreate: (amount: Double, frequency: String, dayOfMonth: Int) -> Unit
) {
    var amountText by remember { mutableStateOf("5000") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var dayText by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create SIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fundName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") })
                OutlinedTextField(value = frequency, onValueChange = { frequency = it.uppercase() }, label = { Text("Frequency (MONTHLY/QUARTERLY)") })
                OutlinedTextField(value = dayText, onValueChange = { dayText = it }, label = { Text("Installment Day") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                val day = dayText.toIntOrNull() ?: 5
                if (amount > 0) onCreate(amount, frequency, day)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IpoApplyDialog(
    ipo: IPOListing,
    onDismiss: () -> Unit,
    onApply: (lots: Int, upiId: String, bidPrice: Double) -> Unit
) {
    var lotsText by remember { mutableStateOf("1") }
    var upiText by remember { mutableStateOf("demo@upi") }
    var bidText by remember { mutableStateOf((ipo.priceBandMax ?: ipo.priceBandMin ?: 0.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply IPO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ipo.companyName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = lotsText, onValueChange = { lotsText = it }, label = { Text("Lots") })
                OutlinedTextField(value = bidText, onValueChange = { bidText = it }, label = { Text("Bid Price") })
                OutlinedTextField(value = upiText, onValueChange = { upiText = it }, label = { Text("UPI ID") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val lots = lotsText.toIntOrNull() ?: 1
                val bid = bidText.toDoubleOrNull() ?: 0.0
                if (lots > 0 && bid > 0) onApply(lots, upiText, bid)
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdvancedOrdersScreen(viewModel: TradingViewModel) {
    val loading by viewModel.advancedLoading.collectAsState()
    val advancedResponse by viewModel.advancedOrderResponse.collectAsState()
    val triggerOrders by viewModel.triggerOrders.collectAsState()
    val triggerEvaluation by viewModel.triggerEvaluation.collectAsState()
    val basketOrders by viewModel.basketOrders.collectAsState()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsState()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsState()

    var symbol by remember { mutableStateOf("RELIANCE") }
    var quantityInput by remember { mutableStateOf("1") }
    var side by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var validity by remember { mutableStateOf("DAY") }
    var limitPriceInput by remember { mutableStateOf("") }
    var triggerPriceInput by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("manual") }

    var basketName by remember { mutableStateOf("Momentum Basket") }
    var basketLegsInput by remember { mutableStateOf("RELIANCE:1:BUY\nTCS:1:BUY") }

    LaunchedEffect(Unit) {
        viewModel.clearPreTradeCopilotSignal()
        viewModel.refreshTriggerOrders()
        viewModel.refreshBasketOrders()
    }

    LaunchedEffect(symbol, quantityInput, side, orderType, validity, limitPriceInput, triggerPriceInput, tag) {
        viewModel.clearPreTradeCopilotSignal()
    }

    val quantity = quantityInput.toIntOrNull() ?: 0
    val limitPrice = limitPriceInput.toDoubleOrNull()
    val triggerPrice = triggerPriceInput.toDoubleOrNull()
    val effectiveSignal = preTradeEstimate?.signal ?: preTradeSignal
    val copilotBlocksTrade = effectiveSignal?.verdict?.equals("BLOCK", ignoreCase = true) == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Advanced Orders",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
        item { ActionBanner(viewModel) }

        if (loading) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                        Text("Processing advanced workflow...", color = LocalAppTheme.current.text)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order Ticket", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Symbol") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it.filter { ch -> ch.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Quantity") },
                        singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BUY", "SELL").forEach { option ->
                            TextButton(onClick = { side = option }) {
                                Text(if (side == option) "● $option" else option)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("MARKET", "LIMIT", "SL", "SLM").forEach { option ->
                            TextButton(onClick = { orderType = option }) {
                                Text(if (orderType == option) "● $option" else option)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DAY", "IOC", "GTC").forEach { option ->
                            TextButton(onClick = { validity = option }) {
                                Text(if (validity == option) "● $option" else option)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = limitPriceInput,
                        onValueChange = { limitPriceInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Limit Price (optional)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = triggerPriceInput,
                        onValueChange = { triggerPriceInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trigger Price (optional)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tag (optional)") },
                        singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.fetchPreTradeEstimate(
                                    AdvancedOrderRequest(
                                        symbol = symbol,
                                        qty = quantity,
                                        side = side,
                                        orderType = orderType,
                                        validity = validity,
                                        limitPrice = limitPrice,
                                        triggerPrice = triggerPrice,
                                        tag = tag.takeIf { it.isNotBlank() },
                                    )
                                )
                            },
                            enabled = quantity > 0 && symbol.isNotBlank(),
                        ) {
                            Text("Estimate + Check")
                        }
                        Button(
                            onClick = {
                                viewModel.placeAdvancedOrder(
                                    symbol = symbol,
                                    quantity = quantity,
                                    side = side,
                                    orderType = orderType,
                                    validity = validity,
                                    limitPrice = limitPrice,
                                    triggerPrice = triggerPrice,
                                    tag = tag,
                                )
                            },
                            enabled = quantity > 0 && symbol.isNotBlank() && !copilotBlocksTrade,
                        ) {
                            Text("Place")
                        }
                    }

                    TextButton(
                        onClick = {
                            if (triggerPrice != null) {
                                viewModel.createTriggerOrder(
                                    symbol = symbol,
                                    quantity = quantity,
                                    side = side,
                                    triggerPrice = triggerPrice,
                                    orderType = orderType,
                                    validity = validity,
                                    limitPrice = limitPrice,
                                    tag = tag,
                                )
                            }
                        },
                        enabled = quantity > 0 && symbol.isNotBlank() && triggerPrice != null && !copilotBlocksTrade,
                    ) {
                        Text("Queue Trigger Instead")
                    }
                }
            }
        }

        preTradeEstimate?.let { estimate ->
            item {
                PreTradeEstimateCard(estimate)
            }
        }

        effectiveSignal?.let { signal ->
            item {
                PreTradeSignalCard("Copilot Pre-Trade", signal)
            }
        }

        advancedResponse?.let { response ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Latest Advanced Order", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("${response.status.uppercase()} • ${response.order.symbol} ${response.order.side}", color = LocalAppTheme.current.text)
                        Text(response.message, color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        response.executedPrice?.let {
                            Text("Executed Price: ₹${String.format("%.2f", it)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                        if (response.riskFlags.isNotEmpty()) {
                            Text("Risk Flags: ${response.riskFlags.joinToString(", ")}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        triggerEvaluation?.let { evaluation ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Trigger Evaluation", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Processed: ${evaluation.processedCount}", color = LocalAppTheme.current.text)
                        Text("Status: ${evaluation.status}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Basket Builder", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = basketName,
                        onValueChange = { basketName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Basket Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = basketLegsInput,
                        onValueChange = { basketLegsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Legs (SYMBOL:QTY:SIDE per line)") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.createBasketOrder(
                                    name = basketName,
                                    legs = parseBasketLegs(basketLegsInput)
                                )
                            }
                        ) {
                            Text("Save Basket")
                        }
                        TextButton(onClick = { viewModel.evaluateTriggerOrders() }) {
                            Text("Evaluate Triggers")
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Saved Baskets", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    if (basketOrders.isEmpty()) {
                        Text("No baskets yet.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    } else {
                        basketOrders.take(6).forEach { basket ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${basket.name} • ${basket.status}", color = LocalAppTheme.current.text, fontSize = 13.sp)
                                    Text(basket.message, color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                                }
                                TextButton(onClick = { viewModel.executeBasketOrder(basket.basketId) }) {
                                    Text("Execute")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pending Triggers", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    if (triggerOrders.isEmpty()) {
                        Text("No pending trigger orders.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    } else {
                        triggerOrders.take(8).forEach { trigger ->
                            Text(
                                "• ${trigger.symbol} ${trigger.side} ${trigger.qty} @ ${trigger.triggerPrice ?: trigger.limitPrice ?: "MKT"} (${trigger.status})",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DerivativesIntelligenceScreen(viewModel: TradingViewModel) {
    val optionChain by viewModel.optionChain.collectAsState()
    val strategyPreview by viewModel.strategyPreview.collectAsState()
    val loading by viewModel.derivativesLoading.collectAsState()

    var symbol by remember { mutableStateOf("NIFTY") }
    var expiry by remember { mutableStateOf("2026-03-26") }
    var strategySpotInput by remember { mutableStateOf("") }
    var strategyLegsInput by remember {
        mutableStateOf("CALL:BUY:22500:120\nCALL:SELL:23000:80")
    }

    LaunchedEffect(optionChain?.spot) {
        val spot = optionChain?.spot
        if (spot != null && spot > 0.0 && strategySpotInput.isBlank()) {
            strategySpotInput = String.format("%.2f", spot)
        }
    }

    val chainContracts = remember(optionChain) {
        val chain = optionChain ?: return@remember emptyList()
        chain.contracts.sortedBy { kotlin.math.abs(it.strike - chain.spot) }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Derivatives Intelligence",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
        item { ActionBanner(viewModel) }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Option Chain", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Underlying") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Expiry (YYYY-MM-DD)") },
                        singleLine = true,
                    )
                    Button(onClick = { viewModel.loadOptionChain(symbol, expiry) }) {
                        Text("Load Chain")
                    }
                    if (loading) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                    }
                }
            }
        }

        optionChain?.let { chain ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${chain.symbol} Spot: ₹${String.format("%.2f", chain.spot)}", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Expiry: ${chain.expiry} • Contracts: ${chain.contracts.size}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        chainContracts.forEach { contract ->
                            Text(
                                "Strike ${contract.strike}: CE Δ ${String.format("%.2f", contract.callDelta)} OI ${contract.callOi} | PE Δ ${String.format("%.2f", contract.putDelta)} OI ${contract.putOi}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Strategy Builder", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = strategySpotInput,
                        onValueChange = { strategySpotInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Spot") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = strategyLegsInput,
                        onValueChange = { strategyLegsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Legs (CALL/PUT:SIDE:STRIKE:PREMIUM)") },
                    )
                    Button(
                        onClick = {
                            val spot = strategySpotInput.toDoubleOrNull() ?: optionChain?.spot ?: 0.0
                            val legs = parseStrategyLegs(strategyLegsInput)
                            viewModel.previewStrategy(symbol = symbol, spot = spot, legs = legs)
                        }
                    ) {
                        Text("Preview Risk")
                    }
                }
            }
        }

        strategyPreview?.let { preview ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Strategy Risk Preview", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Max Profit: ₹${String.format("%.2f", preview.maxProfit)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Max Loss: ₹${String.format("%.2f", preview.maxLoss)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Margin Estimate: ₹${String.format("%.2f", preview.marginEstimate)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Risk/Reward: ${String.format("%.2f", preview.riskRewardRatio)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        if (preview.breakevenPoints.isNotEmpty()) {
                            Text("Breakeven: ${preview.breakevenPoints.joinToString { String.format("%.2f", it) }}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                        preview.notes.take(4).forEach {
                            Text("• $it", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WealthOsScreen(viewModel: TradingViewModel) {
    val dashboard by viewModel.familyDashboard.collectAsState()
    val goals by viewModel.goalPlans.collectAsState()
    val loading by viewModel.wealthLoading.collectAsState()

    var memberName by remember { mutableStateOf("") }
    var memberRelation by remember { mutableStateOf("SELF") }
    var memberEquityInput by remember { mutableStateOf("0") }
    var memberMfInput by remember { mutableStateOf("0") }
    var memberCashInput by remember { mutableStateOf("0") }
    var memberLiabilityInput by remember { mutableStateOf("0") }

    var goalName by remember { mutableStateOf("") }
    var targetAmountInput by remember { mutableStateOf("500000") }
    var targetDate by remember { mutableStateOf("2030-12-31") }
    var monthlyContributionInput by remember { mutableStateOf("10000") }
    var riskProfile by remember { mutableStateOf("MODERATE") }

    var linkInstrumentsInput by remember { mutableStateOf("NIFTYBEES,PPFAS") }
    var incrementAmountInput by remember { mutableStateOf("2000") }

    LaunchedEffect(Unit) {
        viewModel.loadFamilyDashboard()
        viewModel.loadGoalPlans()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Wealth OS", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        item { ActionBanner(viewModel) }

        if (loading) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                        Text("Syncing family wealth data...", color = LocalAppTheme.current.text)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Family Member", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = memberName, onValueChange = { memberName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = memberRelation, onValueChange = { memberRelation = it.uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Relation") }, singleLine = true)
                    OutlinedTextField(value = memberEquityInput, onValueChange = { memberEquityInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Equity Value") }, singleLine = true)
                    OutlinedTextField(value = memberMfInput, onValueChange = { memberMfInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Mutual Fund Value") }, singleLine = true)
                    OutlinedTextField(value = memberCashInput, onValueChange = { memberCashInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cash Value") }, singleLine = true)
                    OutlinedTextField(value = memberLiabilityInput, onValueChange = { memberLiabilityInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Liabilities") }, singleLine = true)
                    Button(
                        onClick = {
                            viewModel.addFamilyMember(
                                name = memberName,
                                relation = memberRelation,
                                equityValue = memberEquityInput.toDoubleOrNull() ?: 0.0,
                                mutualFundValue = memberMfInput.toDoubleOrNull() ?: 0.0,
                                cashValue = memberCashInput.toDoubleOrNull() ?: 0.0,
                                liabilitiesValue = memberLiabilityInput.toDoubleOrNull() ?: 0.0,
                            )
                            memberName = ""
                            memberRelation = "SELF"
                        },
                        enabled = memberName.isNotBlank() && memberRelation.isNotBlank(),
                    ) {
                        Text("Add Member")
                    }
                }
            }
        }

        dashboard?.let { summary ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Family Dashboard", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Consolidated Net Worth: ₹${String.format("%,.2f", summary.consolidatedNetWorth)}", color = LocalAppTheme.current.text)
                        Text("Assets: ₹${String.format("%,.2f", summary.totalAssets)} • Liabilities: ₹${String.format("%,.2f", summary.totalLiabilities)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        if (summary.allocation.isNotEmpty()) {
                            Text("Allocation: ${summary.allocation.entries.joinToString { "${it.key} ${String.format("%.1f", it.value)}%" }}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                        summary.members.forEach { member ->
                            Text(
                                "• ${member.name} (${member.relation}) Net: ₹${String.format("%,.2f", member.netWorth)}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Goal Planner", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = goalName, onValueChange = { goalName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Goal Name") }, singleLine = true)
                    OutlinedTextField(value = targetAmountInput, onValueChange = { targetAmountInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Target Amount") }, singleLine = true)
                    OutlinedTextField(value = targetDate, onValueChange = { targetDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Target Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(value = monthlyContributionInput, onValueChange = { monthlyContributionInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Monthly Contribution") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW", "MODERATE", "HIGH").forEach { option ->
                            TextButton(onClick = { riskProfile = option }) {
                                Text(if (riskProfile == option) "● $option" else option)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.createGoalPlan(
                                goalName = goalName,
                                targetAmount = targetAmountInput.toDoubleOrNull() ?: 0.0,
                                targetDate = targetDate,
                                monthlyContribution = monthlyContributionInput.toDoubleOrNull() ?: 0.0,
                                riskProfile = riskProfile,
                            )
                            goalName = ""
                        },
                        enabled = goalName.isNotBlank() && (targetAmountInput.toDoubleOrNull() ?: 0.0) > 0.0,
                    ) {
                        Text("Create Goal")
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Goal Linking", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = linkInstrumentsInput,
                        onValueChange = { linkInstrumentsInput = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instruments (comma separated)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = incrementAmountInput,
                        onValueChange = { incrementAmountInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Increment Amount") },
                        singleLine = true,
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Goals", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    if (goals.isEmpty()) {
                        Text("No goals yet.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    } else {
                        goals.forEach { goal ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(goal.goalName, color = LocalAppTheme.current.text, fontWeight = FontWeight.Medium)
                                Text(
                                    "Progress ${String.format("%.1f", goal.progressPercent)}% • ₹${String.format("%,.0f", goal.currentAmount)} / ₹${String.format("%,.0f", goal.targetAmount)}",
                                    color = LocalAppTheme.current.textSecondary,
                                    fontSize = 12.sp
                                )
                                Text("Risk: ${goal.riskProfile} • Target: ${goal.targetDate}", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                                TextButton(
                                    onClick = {
                                        val instruments = linkInstrumentsInput
                                            .split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        viewModel.linkGoalInvestments(
                                            goalId = goal.id,
                                            instruments = instruments,
                                            incrementAmount = incrementAmountInput.toDoubleOrNull() ?: 0.0,
                                        )
                                    }
                                ) {
                                    Text("Link Instruments")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CopilotCenterScreen(viewModel: TradingViewModel) {
    val loading by viewModel.copilotLoading.collectAsState()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsState()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsState()
    val postTradeReview by viewModel.copilotPostTradeReview.collectAsState()
    val portfolioActions by viewModel.copilotPortfolioActions.collectAsState()

    var symbol by remember { mutableStateOf("RELIANCE") }
    var quantityInput by remember { mutableStateOf("1") }
    var side by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var validity by remember { mutableStateOf("DAY") }
    var limitInput by remember { mutableStateOf("") }
    var triggerInput by remember { mutableStateOf("") }
    var orderIdInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.clearPreTradeCopilotSignal()
        viewModel.loadPortfolioCopilotActions()
    }

    LaunchedEffect(symbol, quantityInput, side, orderType, validity, limitInput, triggerInput) {
        viewModel.clearPreTradeCopilotSignal()
    }

    val quantity = quantityInput.toIntOrNull() ?: 0
    val effectiveSignal = preTradeEstimate?.signal ?: preTradeSignal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Copilot Center", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        item { ActionBanner(viewModel) }

        if (loading) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                        Text("Running copilot...", color = LocalAppTheme.current.text)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pre-Trade Check", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Symbol") }, singleLine = true)
                    OutlinedTextField(value = quantityInput, onValueChange = { quantityInput = it.filter { ch -> ch.isDigit() }.take(6) }, modifier = Modifier.fillMaxWidth(), label = { Text("Quantity") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BUY", "SELL").forEach { option ->
                            TextButton(onClick = { side = option }) {
                                Text(if (side == option) "● $option" else option)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MARKET", "LIMIT", "SL", "SLM").forEach { option ->
                            TextButton(onClick = { orderType = option }) {
                                Text(if (orderType == option) "● $option" else option)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DAY", "IOC", "GTC").forEach { option ->
                            TextButton(onClick = { validity = option }) {
                                Text(if (validity == option) "● $option" else option)
                            }
                        }
                    }
                    OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Limit Price (optional)") }, singleLine = true)
                    OutlinedTextField(value = triggerInput, onValueChange = { triggerInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger Price (optional)") }, singleLine = true)
                    Button(
                        onClick = {
                            viewModel.fetchPreTradeEstimate(
                                AdvancedOrderRequest(
                                    symbol = symbol,
                                    qty = quantity,
                                    side = side,
                                    orderType = orderType,
                                    validity = validity,
                                    limitPrice = limitInput.toDoubleOrNull(),
                                    triggerPrice = triggerInput.toDoubleOrNull(),
                                )
                            )
                        },
                        enabled = symbol.isNotBlank() && quantity > 0,
                    ) {
                        Text("Run Estimate + Copilot")
                    }
                }
            }
        }

        preTradeEstimate?.let { estimate ->
            item {
                PreTradeEstimateCard(estimate)
            }
        }

        effectiveSignal?.let { signal ->
            item {
                PreTradeSignalCard("Pre-Trade Verdict", signal)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Post-Trade Review", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = orderIdInput, onValueChange = { orderIdInput = it.filter { ch -> ch.isDigit() }.take(10) }, modifier = Modifier.fillMaxWidth(), label = { Text("Order ID") }, singleLine = true)
                    OutlinedTextField(value = noteInput, onValueChange = { noteInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note (optional)") })
                    Button(
                        onClick = {
                            val orderId = orderIdInput.toIntOrNull() ?: 0
                            viewModel.fetchPostTradeCopilot(orderId = orderId, note = noteInput.takeIf { it.isNotBlank() })
                        },
                        enabled = (orderIdInput.toIntOrNull() ?: 0) > 0,
                    ) {
                        Text("Run Post-Trade Review")
                    }
                }
            }
        }

        postTradeReview?.let { review ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Post-Trade Summary", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text(review.summary, color = LocalAppTheme.current.text)
                        Text("PnL Now: ₹${String.format("%.2f", review.pnlNow)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        review.coaching.take(4).forEach {
                            Text("• $it", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Portfolio Actions", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { viewModel.loadPortfolioCopilotActions() }) {
                        Text("Refresh Actions")
                    }
                    if (portfolioActions == null) {
                        Text("No portfolio recommendations yet.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    } else {
                        Text("Priority: ${portfolioActions?.priority}", color = LocalAppTheme.current.text)
                        Text(portfolioActions?.rationale.orEmpty(), color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        portfolioActions?.actions?.take(6)?.forEach {
                            Text("• $it", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun parseBasketLegs(rawInput: String): List<BasketOrderLegRequest> {
    return rawInput
        .lines()
        .mapNotNull { line ->
            val tokens = line.split(":").map { it.trim() }
            if (tokens.size < 3) return@mapNotNull null
            val symbol = tokens[0].uppercase()
            val quantity = tokens[1].toIntOrNull() ?: return@mapNotNull null
            val side = tokens[2].uppercase()
            val orderType = tokens.getOrNull(3)?.uppercase() ?: "MARKET"
            if (symbol.isBlank() || quantity <= 0 || (side != "BUY" && side != "SELL")) return@mapNotNull null
            BasketOrderLegRequest(
                symbol = symbol,
                qty = quantity,
                side = side,
                orderType = orderType,
            )
        }
}

private fun parseStrategyLegs(rawInput: String): List<StrategyLeg> {
    return rawInput
        .lines()
        .mapNotNull { line ->
            val tokens = line.split(":").map { it.trim() }
            if (tokens.size < 4) return@mapNotNull null

            val optionType = when (tokens[0].uppercase()) {
                "C", "CE", "CALL" -> "CALL"
                "P", "PE", "PUT" -> "PUT"
                else -> return@mapNotNull null
            }
            val side = tokens[1].uppercase()
            val strike = tokens[2].toDoubleOrNull() ?: return@mapNotNull null
            val premium = tokens[3].toDoubleOrNull() ?: return@mapNotNull null
            val quantity = tokens.getOrNull(4)?.toIntOrNull() ?: 1
            val lotSize = tokens.getOrNull(5)?.toIntOrNull() ?: 1

            if ((side != "BUY" && side != "SELL") || strike <= 0.0 || premium < 0.0 || quantity <= 0 || lotSize <= 0) {
                return@mapNotNull null
            }

            StrategyLeg(
                optionType = optionType,
                side = side,
                strike = strike,
                premium = premium,
                quantity = quantity,
                lotSize = lotSize,
            )
        }
}
