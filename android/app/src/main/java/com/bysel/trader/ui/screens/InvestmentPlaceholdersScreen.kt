package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.bysel.trader.data.models.*
import com.bysel.trader.ui.components.FnoLiteracyMode
import com.bysel.trader.ui.components.FnoLiteracyPrimer
import com.bysel.trader.ui.components.InvestorTipsCard
import com.bysel.trader.ui.components.atmIvPlainEnglish
import com.bysel.trader.ui.components.callMoneyness
import com.bysel.trader.ui.components.ivSkewPlainEnglish
import com.bysel.trader.ui.components.localInvestorTips
import com.bysel.trader.ui.components.pcrPlainEnglish
import com.bysel.trader.ui.components.putMoneyness
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.viewmodel.TradingViewModel

@Composable
private fun LoadingOrEmpty(
    title: String,
    subtitle: String,
    loading: Boolean,
    body: String? = null,
) {
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
                if (!body.isNullOrBlank()) {
                    Text(body, color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProductPaperBanner(text: String) {
    Card(
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            color = LocalAppTheme.current.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ActionBanner(viewModel: TradingViewModel) {
    val msg by viewModel.productActionMessage.collectAsStateWithLifecycle()
    val derivativesError by viewModel.derivativesError.collectAsStateWithLifecycle()
    val display = derivativesError?.takeIf { it.isNotBlank() } ?: msg?.takeIf { it.isNotBlank() }
    if (!display.isNullOrBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.primary.copy(alpha = 0.12f))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(display, color = LocalAppTheme.current.text, modifier = Modifier.weight(1f))
                if (!derivativesError.isNullOrBlank()) {
                    TextButton(onClick = { viewModel.clearDerivativesError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }
        if (derivativesError.isNullOrBlank()) {
            LaunchedEffect(msg) { viewModel.clearProductActionMessage() }
        }
    }
}

@Composable
private fun MutualFundLearnCard(onAskAi: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    Card(colors = CardDefaults.cardColors(containerColor = theme.card)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Learn: What are Mutual Funds?", color = theme.text, fontWeight = FontWeight.SemiBold)
            Text(
                "Pooled investing via an AMC — units priced at NAV. Educational overview. SIP here is paper practice; returns are not guaranteed.",
                color = theme.textSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide primer" else "Read primer", maxLines = 1)
            }
            if (expanded) {
                MutualFundPrimerLine(
                    title = "The idea",
                    body = "Many investors pool money into a scheme that buys equities, debt, or both. You own units; an AMC manages the portfolio under SEBI rules.",
                )
                MutualFundPrimerLine(
                    title = "NAV",
                    body = "NAV ≈ (assets − liabilities) ÷ outstanding units. Wealth = units × NAV. A ₹10 NAV isn’t ‘cheaper’ than ₹100 — compare returns, risk, and TER.",
                )
                MutualFundPrimerLine(
                    title = "SIP vs lump sum",
                    body = "SIP invests a fixed amount on a schedule (rupee-cost averaging). Lump sum is a one-time buy. Match size to goal and risk you won’t abandon.",
                )
                MutualFundPrimerLine(
                    title = "Main types",
                    body = "Equity (higher volatility) · Debt (rate/credit risk) · Hybrid · Index (tracks a benchmark) · ETF (exchange-traded) · FoF (funds of funds).",
                )
                MutualFundPrimerLine(
                    title = "Costs & risks",
                    body = "Expense ratio is the annual drag; exit load can apply on early redeem. Returns aren’t guaranteed — don’t treat equity MFs like FDs.",
                )
                MutualFundPrimerLine(
                    title = "Who it suits",
                    body = "Beginners starting small SIPs, people who want diversification without picking stocks, goal-based investing — after writing horizon and risk capacity.",
                )
                Text(
                    "Educational paraphrase — not SEBI RA advice or a fund recommendation.",
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                )
                TextButton(onClick = onAskAi) {
                    Text("Ask AI: What are mutual funds?", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MutualFundPrimerLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = LocalAppTheme.current.text, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Text(body, color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SgbLearnCard(onAskAi: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val theme = LocalAppTheme.current
    Card(colors = CardDefaults.cardColors(containerColor = theme.card)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Learn: Sovereign Gold Bonds", color = theme.text, fontWeight = FontWeight.SemiBold)
            Text(
                "RBI-issued gold bonds — interest + gold-linked principal. Educational overview only.",
                color = theme.textSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide primer" else "Read primer", maxLines = 1)
            }
            if (expanded) {
                MutualFundPrimerLine(
                    title = "What it is",
                    body = "SGBs are government securities denominated in grams of gold. You hold them in demat or certificate form — no physical bars to store or assay.",
                )
                MutualFundPrimerLine(
                    title = "Returns intuition",
                    body = "You typically earn a fixed interest (historically ~2.5% p.a. on issue price, paid semi-annually) plus gold price movement on principal at redemption/sale. Live tranche terms can differ — always read the current RBI/NSE notice.",
                )
                MutualFundPrimerLine(
                    title = "Tenure & exit",
                    body = "Common tenor is 8 years with an exit window after ~5 years on interest payment dates (check the specific series). Secondary market on exchange may be thin — don’t assume instant liquidity.",
                )
                MutualFundPrimerLine(
                    title = "Tax sketch (verify yourself)",
                    body = "Interest is taxable as income. Capital gains on redemption at maturity for individuals have often been treated favourably vs physical gold — rules change; confirm with a tax advisor / latest IT guidance before acting.",
                )
                MutualFundPrimerLine(
                    title = "SGB vs gold ETF vs physical",
                    body = "SGB: sovereign credit + interest, lock-in/exit rules. Gold ETF: exchange liquidity, expense ratio, no interest. Physical: making charges, purity/storage risk, different tax path. Pick from goal and liquidity need — not from ‘gold is gold’.",
                )
                MutualFundPrimerLine(
                    title = "Who it suits",
                    body = "Long-horizon gold allocation where you want sovereign backing and can tolerate limited early exit. Not a trading vehicle and not a substitute for an emergency fund.",
                )
                Text(
                    "Educational paraphrase — not an RBI offer, not SEBI RA advice, not a buy recommendation.",
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                )
                TextButton(onClick = onAskAi) {
                    Text("Ask AI: What are Sovereign Gold Bonds?", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SgbScreen(viewModel: TradingViewModel) {
    val investorTips by viewModel.investorTips.collectAsStateWithLifecycle()
    val investorTipsLoading by viewModel.investorTipsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadInvestorTips("sgb")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Sovereign Gold Bonds",
                color = LocalAppTheme.current.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                "Educational explorer — not live RBI/NSE SGB applications or allotment tracking.",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
        }
        item {
            InvestorTipsCard(
                title = "SGB Tips",
                topicLabel = if (investorTips.topic == "sgb") {
                    investorTips.topicLabel.ifBlank { "SGB" }
                } else {
                    "SGB"
                },
                tips = if (investorTips.topic == "sgb") {
                    investorTips.tips
                } else {
                    localInvestorTips("sgb").tips
                },
                disclaimer = investorTips.disclaimer.ifBlank {
                    "Educational habits — not bond recommendations."
                },
                loading = investorTipsLoading,
                compact = true,
            )
        }
        item {
            SgbLearnCard(onAskAi = { viewModel.askAi("What are Sovereign Gold Bonds?") })
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Quick compare", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "• SGB — interest + gold link, sovereign issuer, longer hold\n" +
                            "• Gold ETF (e.g. GOLDBEES) — tradeable units, TER drag, no coupon\n" +
                            "• MCX gold futures — leveraged price bet, margin & expiry risk\n" +
                            "• Physical jewellery/coins — making charges & storage",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = { viewModel.askAi("SGB vs gold ETF vs physical gold") }) {
                        Text("Ask AI: SGB vs gold ETF", maxLines = 1)
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MutualFundsScreen(viewModel: TradingViewModel) {
    val funds by viewModel.mutualFunds.collectAsStateWithLifecycle()
    val loading by viewModel.productsLoading.collectAsStateWithLifecycle()
    val recommendations by viewModel.mutualFundRecommendations.collectAsStateWithLifecycle()
    val compareResult by viewModel.mutualFundCompare.collectAsStateWithLifecycle()
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

    val investorTips by viewModel.investorTips.collectAsStateWithLifecycle()
    val investorTipsLoading by viewModel.investorTipsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadMutualFunds(limit = 1000)
        viewModel.loadInvestorTips("mutual_funds")
    }

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
        LoadingOrEmpty(
            title = "Mutual Funds",
            subtitle = "No funds found yet.",
            loading = loading,
            body = "Browse AMFI NAVs and rehearse paper SIPs. This is not a live purchase rail and returns are not guaranteed.",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Mutual Funds", color = LocalAppTheme.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                Text(
                    "AMFI NAVs for browsing · SIP is paper-practice only · past returns are not guaranteed",
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 12.sp,
                )
            }
            item {
                ProductPaperBanner("Paper practice — no real money is invested and this is not a live AMC purchase.")
            }
            item { ActionBanner(viewModel) }
            item {
                InvestorTipsCard(
                    title = "Mutual Fund Tips",
                    topicLabel = investorTips.topicLabel.ifBlank { "Mutual funds" },
                    tips = if (investorTips.topic == "mutual_funds") investorTips.tips else localInvestorTips("mutual_funds").tips,
                    disclaimer = investorTips.disclaimer.ifBlank {
                        "Educational habits — not fund recommendations."
                    },
                    loading = investorTipsLoading,
                    compact = true,
                )
            }
            item { MutualFundLearnCard(onAskAi = { viewModel.askAi("What are mutual funds?") }) }

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
                        Text("Suitability finder (rule-based)", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Scores funds from risk/goal/horizon — not a live AI model or SEBI advice.",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                        )
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
                            Text("Find paper matches")
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

            items(filteredFunds, key = { it.schemeCode }) { fund ->
                Card(
                    colors = byselCardColors(),
                    elevation = byselCardElevation(),
                    border = byselCardBorder(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selected = fund }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            fund.schemeName,
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "NAV ₹${fund.nav} • ${fund.category}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Risk: ${fund.riskLevel ?: "N/A"} • House: ${fund.fundHouse ?: "N/A"}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = {
                                if (compareCodes.contains(fund.schemeCode)) {
                                    compareCodes = compareCodes - fund.schemeCode
                                } else if (compareCodes.size < 4) {
                                    compareCodes = compareCodes + fund.schemeCode
                                } else {
                                    compareHint = "You can compare up to 4 funds"
                                }
                            }) {
                                val selectedText = if (compareCodes.contains(fund.schemeCode)) "Selected" else "Compare"
                                Text(selectedText, maxLines = 1)
                            }
                            TextButton(onClick = { sipTarget = fund }) {
                                Text("Practice SIP", maxLines = 1)
                            }
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
                    Text(
                        "Educational compare only — past returns are not guaranteed and this is not a fund recommendation.",
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary,
                    )
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
        Text(
            fund.schemeName,
            color = LocalAppTheme.current.text,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scheme Code: ${fund.schemeCode}")
                Text("Category: ${fund.category}")
                Text("NAV: ₹${fund.nav} (${fund.navDate})")
                Text("Returns: 1Y ${fund.returns1Y ?: "-"}% • 3Y ${fund.returns3Y ?: "-"}% • 5Y ${fund.returns5Y ?: "-"}%")
                Text("Fund House: ${fund.fundHouse ?: "N/A"}")
                Text("Risk: ${fund.riskLevel ?: "N/A"}")
                Text(
                    "Past returns are not guaranteed. SIP here is paper practice only.",
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Button(onClick = onStartSip, modifier = Modifier.fillMaxWidth()) { Text("Practice SIP") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IpoListingsScreen(viewModel: TradingViewModel) {
    val ipos by viewModel.ipoListings.collectAsStateWithLifecycle()
    val loading by viewModel.productsLoading.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<IPOListing?>(null) }
    var applyTarget by remember { mutableStateOf<IPOListing?>(null) }
    var selectedTab by remember { mutableStateOf(IpoListingTab.OPEN) }

    val investorTips by viewModel.investorTips.collectAsStateWithLifecycle()
    val investorTipsLoading by viewModel.investorTipsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadIpoListings()
        viewModel.loadInvestorTips("ipo")
    }

    val openIpos = remember(ipos) { ipos.filter { classifyIpoTab(it) == IpoListingTab.OPEN } }
    val closedIpos = remember(ipos) { ipos.filter { classifyIpoTab(it) == IpoListingTab.CLOSED } }
    val upcomingIpos = remember(ipos) { ipos.filter { classifyIpoTab(it) == IpoListingTab.UPCOMING } }
    val filteredIpos = when (selectedTab) {
        IpoListingTab.OPEN -> openIpos
        IpoListingTab.CLOSED -> closedIpos
        IpoListingTab.UPCOMING -> upcomingIpos
    }

    if (selected != null) {
        IpoDetailScreen(
            ipo = selected!!,
            onBack = { selected = null },
            onApply = { if (selected?.status.equals("OPEN", true)) applyTarget = selected }
        )
    } else if (ipos.isEmpty()) {
        LoadingOrEmpty(
            title = "IPO Listings",
            subtitle = "No practice IPOs in the calendar right now.",
            loading = loading,
            body = "This is a paper IPO browser. Apply here is practice only — no real money, no ASBA block, and not an exchange application.",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("IPO Listings", color = LocalAppTheme.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item {
                Text(
                    "Paper IPO calendar for practice applies — not live NSE/BSE filings or ASBA.",
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 12.sp,
                )
            }
            item {
                ProductPaperBanner("No real money. Practice apply is not an exchange application and does not block UPI/ASBA cash.")
            }
            item { ActionBanner(viewModel) }
            item {
                InvestorTipsCard(
                    title = "IPO Tips",
                    topicLabel = investorTips.topicLabel.ifBlank { "IPOs" },
                    tips = if (investorTips.topic == "ipo") investorTips.tips else localInvestorTips("ipo").tips,
                    disclaimer = investorTips.disclaimer.ifBlank {
                        "Educational habits — not IPO recommendations."
                    },
                    loading = investorTipsLoading,
                    compact = true,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.loadIpoListings() }) { Text("Refresh IPOs") }
                }
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        IpoListingTab.OPEN to "Open (${openIpos.size})",
                        IpoListingTab.CLOSED to "Closed (${closedIpos.size})",
                        IpoListingTab.UPCOMING to "Upcoming (${upcomingIpos.size})"
                    ).forEach { (tab, label) ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        )
                    }
                }
            }

            if (filteredIpos.isEmpty()) {
                item {
                    Card(
                        colors = byselCardColors(),
                        elevation = byselCardElevation(),
                        border = byselCardBorder(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "No ${selectedTab.label.lowercase()} IPOs right now.",
                                color = LocalAppTheme.current.text,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Open = practice window now · Upcoming = announced later · Closed = window ended. Dates are educational IST calendar, not live exchange bids.",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            items(filteredIpos, key = { it.ipoId }) { ipo ->
                Card(
                    colors = byselCardColors(),
                    elevation = byselCardElevation(),
                    border = byselCardBorder(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selected = ipo }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            ipo.companyName,
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${ipo.status} • ${ipo.issueOpenDate} to ${ipo.issueCloseDate}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        when (classifyIpoTab(ipo)) {
                            IpoListingTab.OPEN -> Text(
                                "Closes on: ${ipo.issueCloseDate}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp
                            )
                            IpoListingTab.CLOSED -> Text(
                                "Closed on: ${ipo.issueCloseDate} • Listing: ${ipo.listingDate ?: "TBA"}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp
                            )
                            IpoListingTab.UPCOMING -> Text(
                                "Expected window: ${ipo.issueOpenDate} to ${ipo.issueCloseDate}",
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 12.sp
                            )
                        }
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

private enum class IpoListingTab(val label: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    UPCOMING("Upcoming")
}

private fun classifyIpoTab(ipo: IPOListing): IpoListingTab {
    val status = ipo.status.trim().uppercase()
    return when {
        status.contains("CLOSE") || status.contains("LISTED") || status.contains("ALLOTT") || status.contains("COMPLETE") -> IpoListingTab.CLOSED
        status.contains("UPCOMING") || status.contains("COMING") || status.contains("ANNOUNCED") || status.contains("YET") -> IpoListingTab.UPCOMING
        status.contains("OPEN") || status.contains("LIVE") -> IpoListingTab.OPEN
        else -> IpoListingTab.OPEN
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
        ProductPaperBanner("Paper practice apply — no real money and not an exchange / ASBA application.")
        Card(
            colors = byselCardColors(),
            elevation = byselCardElevation(),
            border = byselCardBorder(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Symbol: ${ipo.symbol}", color = LocalAppTheme.current.text)
                Text("Status: ${ipo.status}", color = LocalAppTheme.current.text)
                Text("Issue Window (IST calendar): ${ipo.issueOpenDate} - ${ipo.issueCloseDate}", color = LocalAppTheme.current.text)
                Text("Listing Date: ${ipo.listingDate ?: "TBA"}", color = LocalAppTheme.current.text)
                Text("Price Band: ₹${ipo.priceBandMin ?: "-"} - ₹${ipo.priceBandMax ?: "-"}", color = LocalAppTheme.current.text)
                Text("Lot Size: ${ipo.lotSize ?: "-"}", color = LocalAppTheme.current.text)
            }
        }
        if (ipo.status.equals("OPEN", true)) {
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Practice apply") }
        }
    }
}

@Composable
fun EtfScreen(viewModel: TradingViewModel) {
    val etfs by viewModel.etfInstruments.collectAsStateWithLifecycle()
    val loading by viewModel.productsLoading.collectAsStateWithLifecycle()
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
            items(etfs, key = { it.symbol }) { etf ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                    modifier = Modifier.fillMaxWidth().clickable { selected = etf }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            etf.name,
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
    val plans by viewModel.sipPlans.collectAsStateWithLifecycle()
    val loading by viewModel.productsLoading.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<com.bysel.trader.data.models.SipPlan?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSipPlans() }

    if (plans.isEmpty()) {
        LoadingOrEmpty(
            title = "My SIPs",
            subtitle = "No practice SIP plans yet.",
            loading = loading,
            body = "Create a paper SIP from Mutual Funds. No real money is invested and returns are not guaranteed.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("My SIPs", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
        item {
            ProductPaperBanner("Paper SIPs only — no real AMC debit and returns are not guaranteed.")
        }
        item { ActionBanner(viewModel) }
        items(plans, key = { it.id }) { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        plan.schemeName,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    val applications by viewModel.myIpoApplications.collectAsStateWithLifecycle()
    val loading by viewModel.productsLoading.collectAsStateWithLifecycle()
    var statusFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) { viewModel.loadMyIpoApplications() }

    val filtered = if (statusFilter == "ALL") {
        applications
    } else {
        applications.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    if (applications.isEmpty()) {
        LoadingOrEmpty(
            title = "My IPO Applications",
            subtitle = "No paper IPO applications yet.",
            loading = loading,
            body = "Practice applies from IPO Listings show up here. They are not exchange allotments and no real money is blocked.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("My IPO Applications", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
        item {
            ProductPaperBanner("Paper applications only — not live ASBA, UPI debit, or exchange allotment.")
        }
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
        items(filtered, key = { it.applicationId }) { app ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        app.companyName,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${app.ipoId} • ${app.status}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text("Lots: ${app.lots} • Bid: ₹${app.bidPrice}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    Text("Practice ref: ${app.upiId} • ${app.appliedAt}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
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
                Text(
                    sip.schemeName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
        title = { Text("Practice SIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fundName, fontWeight = FontWeight.SemiBold)
                Text(
                    "Paper SIP only — no real money is invested and returns are not guaranteed.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
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
            }) { Text("Create practice SIP") }
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
    var upiText by remember { mutableStateOf("paper@bysel") }
    var bidText by remember { mutableStateOf((ipo.priceBandMax ?: ipo.priceBandMin ?: 0.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Practice apply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ipo.companyName, fontWeight = FontWeight.SemiBold)
                Text(
                    "No real money. This is not an exchange application and does not block UPI or ASBA cash.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                )
                OutlinedTextField(value = lotsText, onValueChange = { lotsText = it }, label = { Text("Lots") })
                OutlinedTextField(value = bidText, onValueChange = { bidText = it }, label = { Text("Bid Price") })
                OutlinedTextField(
                    value = upiText,
                    onValueChange = { upiText = it },
                    label = { Text("Practice reference (not real UPI)") },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val lots = lotsText.toIntOrNull() ?: 1
                val bid = bidText.toDoubleOrNull() ?: 0.0
                if (lots > 0 && bid > 0) onApply(lots, upiText, bid)
            }) { Text("Submit paper apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdvancedOrdersScreen(viewModel: TradingViewModel) {
    val loading by viewModel.advancedLoading.collectAsStateWithLifecycle()
    val advancedResponse by viewModel.advancedOrderResponse.collectAsStateWithLifecycle()
    val triggerOrders by viewModel.triggerOrders.collectAsStateWithLifecycle()
    val triggerEvaluation by viewModel.triggerEvaluation.collectAsStateWithLifecycle()
    val basketOrders by viewModel.basketOrders.collectAsStateWithLifecycle()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsStateWithLifecycle()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsStateWithLifecycle()

    var symbol by remember { mutableStateOf("RELIANCE") }
    var quantityInput by remember { mutableStateOf("1") }
    var side by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var validity by remember { mutableStateOf("DAY") }
    var limitPriceInput by remember { mutableStateOf("") }
    var triggerPriceInput by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("manual") }
    var confirmAdvancedOrder by remember { mutableStateOf(false) }

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

    if (confirmAdvancedOrder) {
        AlertDialog(
            onDismissRequest = { confirmAdvancedOrder = false },
            title = { Text("Place $side order?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$quantity x $symbol", fontWeight = FontWeight.Bold)
                    Text("$orderType order, $validity validity")
                    limitPrice?.let { Text("Limit price: ₹${String.format("%.2f", it)}") }
                    triggerPrice?.let { Text("Trigger price: ₹${String.format("%.2f", it)}") }
                    if (orderType == "MARKET") {
                        Text(
                            text = "A market order fills at the best available price, which may differ from the last traded price.",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                    }
                    effectiveSignal?.verdict?.let { verdict ->
                        Text(
                            text = "Pre-trade check: $verdict",
                            fontSize = 12.sp,
                            color = if (verdict.equals("PROCEED", ignoreCase = true)) {
                                LocalAppTheme.current.positive
                            } else {
                                LocalAppTheme.current.negative
                            }
                        )
                    }
                }
            },
            confirmButton = {
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
                        confirmAdvancedOrder = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == "BUY") LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAdvancedOrder = false }) { Text("Cancel") }
            },
            containerColor = LocalAppTheme.current.card
        )
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
                            onClick = { confirmAdvancedOrder = true },
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
                PreTradeSignalCard("BYSEL Pre-Trade", signal)
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
                            Text("Evaluate Now")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DerivativesIntelligenceScreen(viewModel: TradingViewModel) {
    val optionChain by viewModel.optionChain.collectAsStateWithLifecycle()
    val strategyPreview by viewModel.strategyPreview.collectAsStateWithLifecycle()
    val loading by viewModel.derivativesLoading.collectAsStateWithLifecycle()
    val investorTips by viewModel.investorTips.collectAsStateWithLifecycle()
    val investorTipsLoading by viewModel.investorTipsLoading.collectAsStateWithLifecycle()

    var symbol by remember { mutableStateOf("NIFTY") }
    // Default to ~3 weeks out so the educational chain always has a forward expiry.
    var expiry by remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 21)
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        mutableStateOf(String.format("%04d-%02d-%02d", y, m, d))
    }
    var strategySpotInput by remember { mutableStateOf("") }
    var strategyLegsInput by remember {
        mutableStateOf("CALL:BUY:22500:120\nCALL:SELL:23000:80")
    }

    // Auto-load once so the Options tab isn't an empty form on first open.
    LaunchedEffect(Unit) {
        viewModel.loadInvestorTips("fno")
        if (optionChain == null) {
            viewModel.loadOptionChain(symbol, expiry)
        }
    }

    LaunchedEffect(optionChain?.spot) {
        val spot = optionChain?.spot
        if (spot != null && spot > 0.0 && strategySpotInput.isBlank()) {
            strategySpotInput = String.format("%.2f", spot)
        }
    }

    val chainContracts = remember(optionChain) {
        val chain = optionChain ?: return@remember emptyList()
        chain.contracts.sortedBy { kotlin.math.abs(it.strike - chain.spot) }.take(12)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "Options Chain & Greeks",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Text(
                "Paper F&O gym: live NSE chain when reachable, else a teaching chain. Educational only — no guaranteed P&L.",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
        }
        item {
            FnoLiteracyPrimer(mode = FnoLiteracyMode.OPTIONS, initiallyExpanded = false)
        }
        item {
            ProductPaperBanner("Cash shares 9:15–3:30 IST. F&O has extra closes after 3:15 (CAS ~3:35, derivatives ~3:40). Paper practice, not live brokerage.")
        }
        item { ActionBanner(viewModel) }
        item {
            InvestorTipsCard(
                title = "F&O Tips",
                topicLabel = investorTips.topicLabel.ifBlank { "F&O" },
                tips = if (investorTips.topic == "fno") investorTips.tips else localInvestorTips("fno").tips,
                disclaimer = investorTips.disclaimer.ifBlank {
                    "Educational paper habits — not trade recommendations. Returns are not guaranteed."
                },
                loading = investorTipsLoading,
                compact = true,
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Option Chain", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Start with NIFTY. Expiry is when the option ends — weekly index options usually Thursday.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp,
                    )
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
                        Text("Load Chain", maxLines = 1)
                    }
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LocalAppTheme.current.primary)
                    }
                }
            }
        }

        if (optionChain == null && !loading) {
            item {
                Card(
                    colors = byselCardColors(),
                    elevation = byselCardElevation(),
                    border = byselCardBorder(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "No chain loaded yet",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Enter an underlying (NIFTY, BANKNIFTY, or a stock) and expiry, then Load Chain. This is an educational paper gym — not a live F&O ticket and P&L is not guaranteed.",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        optionChain?.let { chain ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${chain.symbol} Spot: ₹${String.format("%.2f", chain.spot)}",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Source: ${chain.source.uppercase()} • Expiry: ${chain.expiry} • Near ATM: ${chainContracts.size} of ${chain.contracts.size}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val pcrText = chain.pcr?.let { String.format("%.2f", it) } ?: "—"
                        val skewText = chain.ivSkew?.let { String.format("%+.2f%%", it * 100.0) } ?: "—"
                        val atmIvText = chain.atmIv?.let { String.format("%.1f%%", it * 100.0) } ?: "—"
                        Text(
                            "PCR (OI) $pcrText · IV skew (P−C) $skewText · ATM IV $atmIvText",
                            color = LocalAppTheme.current.text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pcrPlainEnglish(chain.pcr),
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        Text(
                            atmIvPlainEnglish(chain.atmIv),
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        Text(
                            ivSkewPlainEnglish(chain.ivSkew),
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        chain.notes.take(2).forEach { note ->
                            Text(
                                note,
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        chainContracts.forEach { contract ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                val callIvPct = (contract.callIv ?: contract.impliedVolatility) * 100.0
                                val putIvPct = (contract.putIv ?: contract.impliedVolatility) * 100.0
                                Text(
                                    "Strike ${String.format("%.0f", contract.strike)} · Call ${callMoneyness(chain.spot, contract.strike)} · Put ${putMoneyness(chain.spot, contract.strike)} · IV C ${String.format("%.1f", callIvPct)}% / P ${String.format("%.1f", putIvPct)}%",
                                    color = LocalAppTheme.current.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "CE Δ ${String.format("%.2f", contract.callDelta)} · OI ${contract.callOi} (${if (contract.callOiChange >= 0) "+" else ""}${contract.callOiChange}) · LTP ₹${String.format("%.1f", contract.callLtp)}",
                                    color = LocalAppTheme.current.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "PE Δ ${String.format("%.2f", contract.putDelta)} · OI ${contract.putOi} (${if (contract.putOiChange >= 0) "+" else ""}${contract.putOiChange}) · LTP ₹${String.format("%.1f", contract.putLtp)}",
                                    color = LocalAppTheme.current.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Γ ${String.format("%.4f", contract.gamma)} · Θ ${String.format("%.2f", contract.theta)} · Vega ${String.format("%.2f", contract.vega)}",
                                    color = LocalAppTheme.current.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Strategy Builder (paper)", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap a recipe to fill the legs, then Preview Risk. Illustrative only — not a live order.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp,
                    )
                    val recipes = remember(optionChain, symbol) {
                        optionStrategyRecipes(optionChain)
                    }
                    if (recipes.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recipes.forEach { recipe ->
                                FilterChip(
                                    selected = strategyLegsInput == recipe.legsText,
                                    onClick = {
                                        strategySpotInput = String.format("%.2f", recipe.spot)
                                        strategyLegsInput = recipe.legsText
                                    },
                                    label = { Text(recipe.title, maxLines = 1) },
                                )
                            }
                        }
                        recipes.firstOrNull { it.legsText == strategyLegsInput }?.let { selected ->
                            Text(
                                selected.meaning,
                                color = LocalAppTheme.current.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
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
                        Text("Strategy Risk Preview (paper)", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Max Profit: ₹${String.format("%.2f", preview.maxProfit)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Max Loss: ₹${String.format("%.2f", preview.maxLoss)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Margin Estimate: ₹${String.format("%.2f", preview.marginEstimate)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Risk/Reward: ${String.format("%.2f", preview.riskRewardRatio)}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text(
                            "Max profit is the best case if the recipe works. Max loss is what you can lose if it does not. Margin is an estimate of cash typically blocked — not a broker SPAN quote.",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
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
    val dashboard by viewModel.familyDashboard.collectAsStateWithLifecycle()
    val goals by viewModel.goalPlans.collectAsStateWithLifecycle()
    val loading by viewModel.wealthLoading.collectAsStateWithLifecycle()

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

    val refreshWealthContext = {
        viewModel.loadFamilyDashboard()
        viewModel.loadGoalPlans()
    }

    LaunchedEffect(Unit) { refreshWealthContext() }

    val summary = dashboard
    val members = summary?.members.orEmpty()
    val allocationEntries = summary?.allocation.orEmpty().entries.sortedByDescending { it.value }
    val totalGoalTarget = goals.sumOf { it.targetAmount }
    val totalGoalCurrent = goals.sumOf { it.currentAmount }
    val averageGoalProgress = if (goals.isEmpty()) 0.0 else goals.map { it.progressPercent }.average()
    val netWorth = summary?.consolidatedNetWorth ?: 0.0
    val totalAssets = summary?.totalAssets ?: 0.0
    val totalLiabilities = summary?.totalLiabilities ?: 0.0

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

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.primary.copy(alpha = 0.14f))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Family Net Worth", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        text = formatInvestmentCurrency(netWorth),
                        color = LocalAppTheme.current.text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${members.size} member${if (members.size == 1) "" else "s"} • ${goals.size} goal${if (goals.size == 1) "" else "s"}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    if (goals.isNotEmpty()) {
                        Text(
                            text = "Goal corpus ${formatInvestmentCurrency(totalGoalCurrent)} / ${formatInvestmentCurrency(totalGoalTarget)}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = refreshWealthContext) { Text("Refresh") }
                        TextButton(onClick = { viewModel.loadGoalPlans() }) { Text("Sync Goals") }
                    }
                }
            }
        }

        if (loading) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = LocalAppTheme.current.primary)
                            Text("Syncing family wealth data...", color = LocalAppTheme.current.text)
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LocalAppTheme.current.primary)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WealthMetricTile(
                    title = "Assets",
                    value = formatInvestmentCurrency(totalAssets),
                    caption = "Total household assets",
                )
                WealthMetricTile(
                    title = "Liabilities",
                    value = formatInvestmentCurrency(totalLiabilities),
                    caption = "Loans and obligations",
                )
                WealthMetricTile(
                    title = "Goal Progress",
                    value = "${String.format("%.1f", averageGoalProgress)}%",
                    caption = "Average across all goals",
                )
                WealthMetricTile(
                    title = "Corpus Gap",
                    value = formatInvestmentCurrency((totalGoalTarget - totalGoalCurrent).coerceAtLeast(0.0)),
                    caption = "Amount left to reach targets",
                )
            }
        }

        if (allocationEntries.isNotEmpty()) {
            item {
                WealthSectionHeader(
                    title = "Allocation Map",
                    subtitle = "Current asset-class distribution across the household portfolio.",
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allocationEntries.forEach { allocation ->
                            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.primary.copy(alpha = 0.14f))) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(allocation.key, color = LocalAppTheme.current.text, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    Text(
                                        "${String.format("%.1f", allocation.value)}%",
                                        color = LocalAppTheme.current.textSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            WealthSectionHeader(
                title = "Add Family Member",
                subtitle = "Extend the household balance sheet and keep one consolidated wealth view.",
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = memberName, onValueChange = { memberName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("SELF", "SPOUSE", "CHILD", "PARENT", "OTHER").forEach { relation ->
                            TextButton(onClick = { memberRelation = relation }) {
                                Text(if (memberRelation == relation) "● $relation" else relation)
                            }
                        }
                    }
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

        if (members.isNotEmpty()) {
            item {
                WealthSectionHeader(
                    title = "Family Ledger",
                    subtitle = "Each member's net-worth contribution in the consolidated household view.",
                )
            }
        }

        items(members, key = { it.id }) { member ->
            val sharePercent = if (netWorth > 0.0) ((member.netWorth / netWorth) * 100.0).coerceAtLeast(0.0) else 0.0
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${member.name} • ${member.relation}",
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Net worth ${formatInvestmentCurrency(member.netWorth)}",
                        color = LocalAppTheme.current.text,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "Assets ${formatInvestmentCurrency(member.totalAssets)} • Liabilities ${formatInvestmentCurrency(member.liabilitiesValue)}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Household share ${String.format("%.1f", sharePercent)}%",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    LinearProgressIndicator(
                        progress = { (sharePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = LocalAppTheme.current.primary,
                    )
                }
            }
        }

        item {
            WealthSectionHeader(
                title = "Goal Planner",
                subtitle = "Create target-based plans with contribution and risk profile in one flow.",
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = goalName, onValueChange = { goalName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Goal Name") }, singleLine = true)
                    OutlinedTextField(value = targetAmountInput, onValueChange = { targetAmountInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Target Amount") }, singleLine = true)
                    OutlinedTextField(value = targetDate, onValueChange = { targetDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Target Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(value = monthlyContributionInput, onValueChange = { monthlyContributionInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Monthly Contribution") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
            WealthSectionHeader(
                title = "Goal Linking",
                subtitle = "Map instruments to goals and set the incremental amount per contribution cycle.",
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            WealthSectionHeader(
                title = "Goals Timeline",
                subtitle = "Track progress against each target and push additional instruments when needed.",
            )
        }

        if (goals.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No goals yet.", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("Create your first goal to start tracking household progress and corpus readiness.", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        items(goals.sortedBy { it.targetDate }, key = { it.id }) { goal ->
            val progressRatio = (goal.progressPercent / 100.0).toFloat().coerceIn(0f, 1f)
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(goal.goalName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Progress ${String.format("%.1f", goal.progressPercent)}% • ${formatInvestmentCurrency(goal.currentAmount)} / ${formatInvestmentCurrency(goal.targetAmount)}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier.fillMaxWidth(),
                        color = LocalAppTheme.current.primary,
                    )
                    Text(
                        "Risk ${goal.riskProfile} • Target ${goal.targetDate} • Monthly ${formatInvestmentCurrency(goal.monthlyContribution)}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 11.sp,
                    )
                    if (goal.linkedInstruments.isNotEmpty()) {
                        Text(
                            "Linked: ${goal.linkedInstruments.joinToString()}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
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

        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun WealthSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(subtitle, color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun WealthMetricTile(title: String, value: String, caption: String) {
    Card(
        modifier = Modifier.width(190.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
            Text(value, color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(caption, color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun CopilotCenterScreen(viewModel: TradingViewModel) {
    val loading by viewModel.copilotLoading.collectAsStateWithLifecycle()
    val preTradeEstimate by viewModel.preTradeEstimate.collectAsStateWithLifecycle()
    val preTradeSignal by viewModel.copilotPreTradeSignal.collectAsStateWithLifecycle()
    val postTradeReview by viewModel.copilotPostTradeReview.collectAsStateWithLifecycle()
    val portfolioActions by viewModel.copilotPortfolioActions.collectAsStateWithLifecycle()
    val orderTraceLookup by viewModel.orderTraceLookup.collectAsStateWithLifecycle()
    val lastOrderTraceId by viewModel.lastOrderTraceId.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    var symbol by remember { mutableStateOf("RELIANCE") }
    var quantityInput by remember { mutableStateOf("1") }
    var side by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var validity by remember { mutableStateOf("DAY") }
    var limitInput by remember { mutableStateOf("") }
    var triggerInput by remember { mutableStateOf("") }
    var orderIdInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var traceIdInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.clearPreTradeCopilotSignal()
        viewModel.loadPortfolioCopilotActions()
    }

    LaunchedEffect(symbol, quantityInput, side, orderType, validity, limitInput, triggerInput) {
        viewModel.clearPreTradeCopilotSignal()
    }

    LaunchedEffect(lastOrderTraceId) {
        if (traceIdInput.isBlank() && !lastOrderTraceId.isNullOrBlank()) {
            traceIdInput = lastOrderTraceId.orEmpty()
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pre-Trade Checks", color = LocalAppTheme.current.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(
                    "Rule-based risk checks on live prices, wallet, and orders — not a generative LLM research desk.",
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
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

        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trace Support Lookup", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Paste a trace ID from an order error to fetch server-side execution details instantly.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = traceIdInput,
                        onValueChange = { traceIdInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trace ID") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.lookupOrderByTrace(traceIdInput) },
                            enabled = traceIdInput.isNotBlank(),
                        ) {
                            Text("Lookup")
                        }
                        TextButton(onClick = {
                            traceIdInput = ""
                            viewModel.clearOrderTraceLookup()
                        }) {
                            Text("Clear")
                        }
                        if (!lastOrderTraceId.isNullOrBlank()) {
                            TextButton(onClick = { traceIdInput = lastOrderTraceId.orEmpty() }) {
                                Text("Use Last Failure Trace")
                            }
                        }
                    }
                }
            }
        }

        orderTraceLookup?.let { order ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Trace Result", color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text(order.message, color = LocalAppTheme.current.text)
                        Text("Trace: ${order.traceId}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("Order ID: ${order.orderId} • Status: ${order.status}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        OrderTraceTimeline(status = order.status)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(order.traceId)) }) {
                                Text("Copy Trace ID")
                            }
                            TextButton(onClick = { traceIdInput = order.traceId }) {
                                Text("Use This Trace")
                            }
                        }
                        Text(
                            "${order.symbol} ${order.side} ${order.quantity} • ${order.orderType}/${order.validity}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            "Price ${formatInvestmentCurrency(order.executedPrice)} • Total ${formatInvestmentCurrency(order.total)}",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        if (!order.idempotencyKey.isNullOrBlank()) {
                            Text("Idempotency: ${order.idempotencyKey}", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                        }
                        if (order.createdAt.isNotBlank()) {
                            Text("Created: ${order.createdAt}", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
                        }
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

private data class OrderTimelineStep(
    val label: String,
    val reached: Boolean,
    val isFailure: Boolean = false,
)

private fun normalizeStatusLabel(status: String): String {
    val trimmed = status.trim()
    if (trimmed.isBlank()) {
        return "Final"
    }
    return trimmed
        .replace('_', ' ')
        .lowercase()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase() else first.toString()
            }
        }
}

private fun buildOrderTimelineSteps(status: String): List<OrderTimelineStep> {
    val normalized = status.trim().uppercase()
    val failureStatuses = setOf("FAILED", "REJECTED", "ERROR", "CANCELLED", "CANCELED")
    val successStatuses = setOf("EXECUTED", "FILLED", "COMPLETED", "SUCCESS", "TRIGGER_EXECUTED")
    val exchangeStatuses = setOf("OPEN", "PARTIAL", "PARTIALLY_FILLED")
    val acceptedStatuses = setOf("PENDING", "QUEUED", "TRIGGER_PENDING", "DRAFT")

    val acceptedReached = normalized.isNotBlank()
    val riskReached = when {
        normalized in acceptedStatuses -> normalized != "DRAFT"
        normalized in exchangeStatuses || normalized in successStatuses || normalized in failureStatuses -> true
        else -> acceptedReached
    }
    val exchangeReached = normalized in exchangeStatuses || normalized in successStatuses
    val finalReached = normalized in successStatuses || normalized in failureStatuses
    val finalFailure = normalized in failureStatuses

    return listOf(
        OrderTimelineStep("Accepted", acceptedReached),
        OrderTimelineStep("Risk Check", riskReached),
        OrderTimelineStep("Exchange", exchangeReached),
        OrderTimelineStep(normalizeStatusLabel(status), finalReached, isFailure = finalFailure),
    )
}

@Composable
private fun OrderTraceTimeline(status: String) {
    val steps = remember(status) { buildOrderTimelineSteps(status) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Execution Timeline", color = LocalAppTheme.current.textSecondary, fontSize = 11.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                val nodeColor = when {
                    step.isFailure -> LocalAppTheme.current.negative
                    step.reached -> LocalAppTheme.current.primary
                    else -> LocalAppTheme.current.textSecondary.copy(alpha = 0.35f)
                }

                Column(
                    modifier = Modifier.width(86.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(nodeColor, CircleShape)
                    )
                    Text(
                        step.label,
                        color = if (step.reached || step.isFailure) LocalAppTheme.current.text else LocalAppTheme.current.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (step.isFailure) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }

                if (index < steps.lastIndex) {
                    val nextStep = steps[index + 1]
                    val connectorActive = step.reached && (nextStep.reached || nextStep.isFailure)
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(
                                if (connectorActive) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary.copy(alpha = 0.25f)
                            )
                    )
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

private data class OptionStrategyRecipe(
    val title: String,
    val meaning: String,
    val spot: Double,
    val legsText: String,
)

private fun nearestAtmContract(chain: OptionChainResponse): OptionContract? {
    return chain.contracts.minByOrNull { kotlin.math.abs(it.strike - chain.spot) }
}

private fun nextHigherContract(chain: OptionChainResponse, strike: Double): OptionContract? {
    return chain.contracts.filter { it.strike > strike }.minByOrNull { it.strike }
}

private fun formatStrategyLegLine(
    optionType: String,
    side: String,
    strike: Double,
    premium: Double,
): String {
    return "$optionType:$side:${String.format("%.0f", strike)}:${String.format("%.1f", premium)}"
}

private fun optionStrategyRecipes(chain: OptionChainResponse?): List<OptionStrategyRecipe> {
    val loaded = chain ?: return emptyList()
    val atm = nearestAtmContract(loaded) ?: return emptyList()
    val higher = nextHigherContract(loaded, atm.strike)
    val recipes = mutableListOf(
        OptionStrategyRecipe(
            title = "Long call",
            meaning = "Buy the ATM call. Helps if ${loaded.symbol} rises. Max loss is the premium you pay.",
            spot = loaded.spot,
            legsText = formatStrategyLegLine("CALL", "BUY", atm.strike, atm.callLtp),
        ),
        OptionStrategyRecipe(
            title = "Long put",
            meaning = "Buy the ATM put. Helps if ${loaded.symbol} falls, or as a hedge on shares you hold.",
            spot = loaded.spot,
            legsText = formatStrategyLegLine("PUT", "BUY", atm.strike, atm.putLtp),
        ),
    )
    if (higher != null) {
        recipes.add(
            OptionStrategyRecipe(
                title = "Bull call spread",
                meaning = "Buy ATM call and sell a higher call. Cheaper than a naked call; profit is capped at the short strike.",
                spot = loaded.spot,
                legsText = listOf(
                    formatStrategyLegLine("CALL", "BUY", atm.strike, atm.callLtp),
                    formatStrategyLegLine("CALL", "SELL", higher.strike, higher.callLtp),
                ).joinToString("\n"),
            ),
        )
    }
    return recipes
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
