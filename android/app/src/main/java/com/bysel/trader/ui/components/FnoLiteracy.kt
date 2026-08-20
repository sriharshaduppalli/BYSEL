package com.bysel.trader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselSectionSurface
import kotlin.math.abs

enum class FnoLiteracyMode {
    SCANNER,
    OPTIONS,
    FUTURES,
}

data class FnoTerm(
    val title: String,
    val meaning: String,
)

fun pcrPlainEnglish(pcr: Double?): String {
    if (pcr == null) {
        return "PCR compares put open interest to call open interest. Above 1 means more puts than calls."
    }
    val value = String.format("%.2f", pcr)
    return when {
        pcr >= 1.2 ->
            "PCR $value — more put OI than calls. Often read as hedging or caution, not a sell signal."
        pcr <= 0.8 ->
            "PCR $value — more call OI than puts. Often read as bullish positioning, not a buy signal."
        else ->
            "PCR $value — puts and calls are roughly balanced. Use it as context, not a trade trigger."
    }
}

fun atmIvPlainEnglish(atmIv: Double?): String {
    val value = atmIv?.let { String.format("%.1f%%", it * 100.0) } ?: "—"
    return "ATM IV $value is the market’s priced-in move size. Higher IV makes options more expensive."
}

fun ivSkewPlainEnglish(skew: Double?): String {
    if (skew == null) return "IV skew compares put vs call implied vol. Puts often cost more as crash insurance."
    val value = String.format("%+.1f%%", skew * 100.0)
    return "IV skew $value (put IV − call IV). A richer put IV usually means more demand for downside protection."
}

fun basisPlainEnglish(basis: Double, spot: Double): String {
    val absBasis = abs(basis)
    val vsSpot = if (spot > 0.0) absBasis / spot * 100.0 else 0.0
    val pct = if (vsSpot > 0.0) String.format(" (%.2f%% vs spot)", vsSpot) else ""
    return when {
        basis > 0.5 -> "Futures trade at a premium to spot$pct — buyers are paying extra for later delivery."
        basis < -0.5 -> "Futures trade at a discount to spot$pct — the contract is cheaper than cash."
        else -> "Futures are nearly in line with spot$pct."
    }
}

fun callMoneyness(spot: Double, strike: Double): String {
    if (spot <= 0.0) return "—"
    val gap = abs(strike - spot) / spot
    return when {
        gap < 0.008 -> "ATM"
        strike < spot -> "ITM"
        else -> "OTM"
    }
}

fun putMoneyness(spot: Double, strike: Double): String {
    if (spot <= 0.0) return "—"
    val gap = abs(strike - spot) / spot
    return when {
        gap < 0.008 -> "ATM"
        strike > spot -> "ITM"
        else -> "OTM"
    }
}

private fun termsFor(mode: FnoLiteracyMode): List<FnoTerm> {
    val shared = listOf(
        FnoTerm("Paper only", "BYSEL F&O is a practice gym. No live brokerage, no guaranteed P&L, no SPAN from your broker."),
        FnoTerm("Lot", "You cannot buy 1 share of NIFTY futures. You buy 1 lot (e.g. 50). Size risk from the lot, not the LTP."),
        FnoTerm("Notional vs margin", "Notional = lot × price (value you control). Margin is cash typically blocked. A move can lose more than margin."),
    )
    val options = listOf(
        FnoTerm("Call", "Right to buy the underlying. Helps if you think the price will rise. Max loss if you buy a call = premium paid."),
        FnoTerm("Put", "Right to sell the underlying. Helps if you think the price will fall, or as a hedge on shares you already hold."),
        FnoTerm("Strike + expiry", "Strike is the agreed price. Expiry is when the right ends. Theta (time decay) speeds up near expiry."),
        FnoTerm("ITM / ATM / OTM", "In / at / out of the money. Start with the ATM row — that is closest to today’s spot."),
        FnoTerm("OI + PCR", "Open interest is outstanding contracts. PCR = put OI / call OI. Context only — not a buy/sell signal."),
        FnoTerm("Greeks", "Delta ≈ how much premium moves if spot moves ₹1. Theta = daily time decay. Vega = sensitivity to IV. Gamma = how fast delta changes."),
    )
    val futures = listOf(
        FnoTerm("Futures", "A contract to buy or sell later at today’s futures price. Profit/loss tracks the underlying, with leverage."),
        FnoTerm("Basis", "Futures price minus spot. Premium = futures above cash. Discount = futures below cash."),
        FnoTerm("Mark-to-market", "P&L is settled daily. A gap against you can eat margin even if you plan to hold to expiry."),
        FnoTerm("Session clock", "Cash shares 9:15–3:30 IST. From 3 Aug 2026, F&O cash ~3:15, CAS ~3:35, derivatives ~3:40. Brokers may square MIS earlier."),
    )
    return when (mode) {
        FnoLiteracyMode.OPTIONS -> shared + options
        FnoLiteracyMode.FUTURES -> shared + futures
        FnoLiteracyMode.SCANNER -> shared + listOf(options.first(), futures.first()) + options.drop(1).take(2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FnoLiteracyPrimer(
    mode: FnoLiteracyMode,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onOpenOptions: (() -> Unit)? = null,
    onOpenFutures: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    var expanded by rememberSaveable(mode) { mutableStateOf(initiallyExpanded) }
    val terms = termsFor(mode)
    val title = when (mode) {
        FnoLiteracyMode.SCANNER -> "F&O in plain English"
        FnoLiteracyMode.OPTIONS -> "How to read this options chain"
        FnoLiteracyMode.FUTURES -> "How to read this futures board"
    }
    val summary = when (mode) {
        FnoLiteracyMode.SCANNER ->
            "Futures = agreement to buy/sell later (levered). Options = paid right to buy (call) or sell (put). Practice here first."
        FnoLiteracyMode.OPTIONS ->
            "Load a chain, start at the ATM strike, then preview a simple recipe. Numbers are educational — not a live ticket."
        FnoLiteracyMode.FUTURES ->
            "1 lot controls a large notional. Preview margin before you place a paper ticket. Max loss can exceed margin."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .byselSectionSurface(RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(summary, color = theme.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide F&O glossary" else "Show F&O glossary",
                tint = theme.primary,
            )
        }

        if (expanded) {
            terms.forEach { term ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(term.title, color = theme.text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(term.meaning, color = theme.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        if (onOpenOptions != null || onOpenFutures != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onOpenOptions != null) {
                    FilledTonalButton(onClick = onOpenOptions) {
                        Text("Open Options", maxLines = 1)
                    }
                }
                if (onOpenFutures != null) {
                    OutlinedButton(onClick = onOpenFutures) {
                        Text("Open Futures", maxLines = 1)
                    }
                }
            }
        }
    }
}
