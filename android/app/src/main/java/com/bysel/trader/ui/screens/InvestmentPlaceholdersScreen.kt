package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.ETFInstrument
import com.bysel.trader.data.models.IPOListing
import com.bysel.trader.data.models.MutualFund
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
fun MutualFundsScreen(viewModel: TradingViewModel) {
    val funds by viewModel.mutualFunds.collectAsState()
    val loading by viewModel.productsLoading.collectAsState()
    var selected by remember { mutableStateOf<MutualFund?>(null) }
    var sipTarget by remember { mutableStateOf<MutualFund?>(null) }

    LaunchedEffect(Unit) { viewModel.loadMutualFunds() }

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
            items(funds) { fund ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
                    modifier = Modifier.fillMaxWidth().clickable { selected = fund }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fund.schemeName, color = LocalAppTheme.current.text, fontWeight = FontWeight.SemiBold)
                        Text("NAV ₹${fund.nav} • ${fund.category}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                        Text("1Y: ${fund.returns1Y ?: 0.0}% • Risk: ${fund.riskLevel ?: "N/A"}", color = LocalAppTheme.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
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
