package com.bysel.trader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.OptionContract
import com.bysel.trader.ui.format.usFmt
import com.bysel.trader.ui.theme.LocalAppTheme
import kotlin.math.max

fun chainMaxPain(contracts: List<OptionContract>): Double? {
    if (contracts.isEmpty() || contracts.all { it.callOi == 0 && it.putOi == 0 }) return null
    return contracts.minByOrNull { candidate ->
        contracts.sumOf { strike ->
            strike.callOi * max(0.0, candidate.strike - strike.strike) +
                strike.putOi * max(0.0, strike.strike - candidate.strike)
        }
    }?.strike
}

/**
 * Open interest and straddle cost from the chain already on screen.
 * Premiums and OI stay labeled as the chain's own figures.
 */
@Composable
fun ChainStudyCharts(
    contracts: List<OptionContract>,
    teaching: Boolean,
    modifier: Modifier = Modifier,
) {
    if (contracts.isEmpty()) return
    val theme = LocalAppTheme.current
    val oiNote = if (teaching) {
        "Open interest below is the teaching book on this chain, not the exchange OI."
    } else {
        "Open interest from this chain. Call bars are up, put bars are down."
    }
    Text(
        text = oiNote,
        color = theme.textSecondary,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = modifier.padding(top = 4.dp),
    )
    val maxOi = contracts.maxOf { max(it.callOi, it.putOi) }.coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(vertical = 4.dp),
    ) {
        val slot = size.width / contracts.size
        val mid = size.height / 2f
        contracts.forEachIndexed { index, contract ->
            val callH = (contract.callOi / maxOi.toFloat()) * (mid - 4f)
            val putH = (contract.putOi / maxOi.toFloat()) * (mid - 4f)
            val left = index * slot + slot * 0.18f
            val barW = slot * 0.28f
            drawRect(
                color = theme.positive,
                topLeft = Offset(left, mid - callH),
                size = androidx.compose.ui.geometry.Size(barW, callH.coerceAtLeast(0f)),
            )
            drawRect(
                color = theme.negative,
                topLeft = Offset(left + barW + 2f, mid),
                size = androidx.compose.ui.geometry.Size(barW, putH.coerceAtLeast(0f)),
            )
        }
    }
    val costs = contracts.map { it.callLtp + it.putLtp }
    val hi = costs.maxOrNull() ?: return
    val lo = costs.minOrNull() ?: return
    if (hi <= 0.0) return
    val cheap = contracts.minByOrNull { it.callLtp + it.putLtp }
    Text(
        text = "Straddle cost is call last plus put last at each strike on this screen" +
            (cheap?.let { ", cheapest here at ₹${usFmt("%.0f", it.strike)} (₹${usFmt("%.1f", it.callLtp + it.putLtp)})" } ?: "") +
            ". Not a traded straddle price.",
        color = theme.text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(top = 4.dp),
    ) {
        val span = (hi - lo).coerceAtLeast(hi * 0.05).coerceAtLeast(0.5)
        val path = Path()
        costs.forEachIndexed { index, cost ->
            val x = if (costs.size == 1) size.width / 2f else index * (size.width / (costs.size - 1))
            val y = size.height - ((cost - lo) / span).toFloat().coerceIn(0f, 1f) * (size.height - 6f) - 3f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = theme.primary, style = Stroke(width = 2.5f))
    }
}
