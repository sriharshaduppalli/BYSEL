package com.bysel.trader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.paper.PaperPositionSizer
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun PaperPositionSizeHelper(
    walletBalance: Double,
    entryPrice: Double,
    onApplyQty: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var riskPercentText by rememberSaveable { mutableStateOf("1") }
    var stopText by rememberSaveable { mutableStateOf("") }
    val riskPercent = riskPercentText.toDoubleOrNull() ?: 0.0
    val stop = stopText.toDoubleOrNull() ?: 0.0
    val qty = PaperPositionSizer.suggestedQty(walletBalance, riskPercent, entryPrice, stop)
    val budget = PaperPositionSizer.riskBudget(walletBalance, riskPercent)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Paper size helper",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = theme.text,
        )
        Text(
            "Qty = wallet × risk% ÷ (entry − stop). Uses your paper wallet. Not a recommended size.",
            fontSize = 11.sp,
            color = theme.textSecondary,
            lineHeight = 15.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("0.5", "1", "2").forEach { pct ->
                FilterChip(
                    selected = riskPercentText == pct,
                    onClick = { riskPercentText = pct },
                    label = { Text("$pct%") },
                )
            }
        }
        OutlinedTextField(
            value = stopText,
            onValueChange = { stopText = filterDecimalInput(it) },
            label = { Text("Your stop (₹)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = appOutlinedTextFieldColors(containerColor = theme.surface),
            shape = RoundedCornerShape(12.dp),
        )
        Text(
            when {
                walletBalance <= 0.0 -> "Wallet is ₹0 — add paper cash or pick a smaller risk % after you fund practice."
                stop <= 0.0 -> "Enter a stop you chose. We will not invent one."
                qty == null -> "Stop is too close to entry, or risk budget is below one share."
                else -> "Risk budget ${formatInr(budget)} → $qty shares at ${formatInr(entryPrice)}."
            },
            fontSize = 12.sp,
            color = theme.text,
            style = MaterialTheme.typography.bodySmall,
        )
        if (qty != null) {
            OutlinedButton(
                onClick = { onApplyQty(qty) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use $qty qty")
            }
        }
    }
}
