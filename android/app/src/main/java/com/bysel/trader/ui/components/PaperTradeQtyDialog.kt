package com.bysel.trader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun PaperTradeQtyDialog(
    symbol: String,
    side: String,
    lastPrice: Double?,
    initialQty: Int,
    maxSellQty: Int? = null,
    walletBalance: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (qty: Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val buy = side.equals("BUY", ignoreCase = true)
    var qtyText by rememberSaveable(symbol, side) {
        mutableStateOf(initialQty.coerceAtLeast(1).toString())
    }
    val qty = qtyText.toIntOrNull() ?: 0
    val sellCap = maxSellQty?.takeIf { it > 0 }
    val overSell = !buy && sellCap != null && qty > sellCap
    val canConfirm = qty >= 1 && !overSell
    val estimate = lastPrice?.takeIf { it > 0 }?.times(qty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (buy) "How many $symbol to buy?" else "How many $symbol to sell?")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lastPrice != null && lastPrice > 0) {
                    Text("Last price: ${formatInr(lastPrice)}", fontSize = 13.sp, color = theme.text)
                } else {
                    Text(
                        "Live price unavailable — the paper order uses the next available mark.",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
                if (!buy && sellCap != null) {
                    Text(
                        "You hold $sellCap share(s). Selling more than that is not allowed.",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { raw ->
                        qtyText = raw.filter { it.isDigit() }.take(7)
                    },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = buildList {
                        add(1); add(5); add(10); add(25)
                        if (!buy && sellCap != null) add(sellCap)
                    }.distinct()
                    chips.forEach { n ->
                        FilterChip(
                            selected = qty == n,
                            onClick = { qtyText = n.toString() },
                            label = { Text(if (!buy && sellCap != null && n == sellCap) "All" else n.toString()) },
                        )
                    }
                }
                if (estimate != null) {
                    Text(
                        "Approx. value: ${formatInr(estimate)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = theme.text,
                    )
                }
                if (buy && walletBalance != null && estimate != null && estimate > walletBalance) {
                    Text(
                        "This is above your paper wallet (${formatInr(walletBalance)}). The order may be rejected.",
                        fontSize = 11.sp,
                        color = theme.negative,
                    )
                }
                if (overSell) {
                    Text(
                        "Enter $sellCap or fewer shares.",
                        fontSize = 11.sp,
                        color = theme.negative,
                    )
                }
                if (buy && lastPrice != null && lastPrice > 0) {
                    PaperPositionSizeHelper(
                        walletBalance = walletBalance ?: 0.0,
                        entryPrice = lastPrice,
                        onApplyQty = { qtyText = it.toString() },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Paper practice only — not a live broker order.",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (canConfirm) onConfirm(qty) },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (buy) theme.positive else theme.negative,
                ),
            ) {
                Text(if (buy) "Buy $qty" else "Sell $qty")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = theme.card,
    )
}
