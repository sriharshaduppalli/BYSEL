package com.bysel.trader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

private fun formatOiCompact(oi: Int): String = when {
    oi >= 100_000 -> "${usFmt("%.1f", oi / 100_000.0)}L"
    oi >= 1_000 -> "${usFmt("%.0f", oi / 1_000.0)}k"
    else -> oi.toString()
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
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = remember(theme.textSecondary) {
        TextStyle(color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
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
    val firstStrike = usFmt("%.0f", contracts.first().strike)
    val lastStrike = usFmt("%.0f", contracts.last().strike)
    val oiSummary = "Option chain open interest chart across ${contracts.size} strikes from $firstStrike to $lastStrike, peak OI ${formatOiCompact(maxOi)}."
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = oiSummary },
    ) {
        val topLabel = textMeasurer.measure("C ${formatOiCompact(maxOi)}", style = axisStyle)
        val botLabel = textMeasurer.measure("P ${formatOiCompact(maxOi)}", style = axisStyle)
        val leftPad = max(topLabel.size.width, botLabel.size.width).toFloat() + 6.dp.toPx()
        val bottomPad = topLabel.size.height.toFloat() + 4.dp.toPx()
        val plotW = (size.width - leftPad).coerceAtLeast(1f)
        val plotH = (size.height - bottomPad).coerceAtLeast(1f)
        val mid = plotH / 2f
        val axisColor = theme.text.copy(alpha = 0.24f)

        drawLine(
            color = axisColor,
            start = Offset(leftPad, 0f),
            end = Offset(leftPad, plotH),
            strokeWidth = 1f,
        )
        drawLine(
            color = axisColor,
            start = Offset(leftPad, mid),
            end = Offset(leftPad + plotW, mid),
            strokeWidth = 1f,
        )

        val slot = plotW / contracts.size
        contracts.forEachIndexed { index, contract ->
            val callH = (contract.callOi / maxOi.toFloat()) * (mid - 4f)
            val putH = (contract.putOi / maxOi.toFloat()) * (mid - 4f)
            val left = leftPad + index * slot + slot * 0.18f
            val barW = (slot * 0.28f).coerceAtLeast(1.5f)
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

        drawText(topLabel, topLeft = Offset(2f, 0f))
        drawText(
            botLabel,
            topLeft = Offset(2f, (plotH - botLabel.size.height).coerceAtLeast(mid)),
        )

        val tickIndices = listOf(0, contracts.size / 2, contracts.lastIndex).distinct()
        tickIndices.forEach { idx ->
            val strikeLabel = textMeasurer.measure(usFmt("%.0f", contracts[idx].strike), style = axisStyle)
            val centerX = leftPad + idx * slot + slot / 2f
            val tx = (centerX - strikeLabel.size.width / 2f)
                .coerceIn(leftPad, (leftPad + plotW - strikeLabel.size.width).coerceAtLeast(leftPad))
            drawText(strikeLabel, topLeft = Offset(tx, plotH + 2.dp.toPx()))
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
    val straddleSummary = "Straddle cost chart from ₹${usFmt("%.1f", lo)} to ₹${usFmt("%.1f", hi)} across strikes $firstStrike to $lastStrike."
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .padding(top = 4.dp)
            .semantics { contentDescription = straddleSummary },
    ) {
        val hiLabel = textMeasurer.measure("₹${usFmt("%.0f", hi)}", style = axisStyle)
        val loLabel = textMeasurer.measure("₹${usFmt("%.0f", lo)}", style = axisStyle)
        val leftPad = max(hiLabel.size.width, loLabel.size.width).toFloat() + 6.dp.toPx()
        val bottomPad = hiLabel.size.height.toFloat() + 4.dp.toPx()
        val plotW = (size.width - leftPad).coerceAtLeast(1f)
        val plotH = (size.height - bottomPad).coerceAtLeast(1f)
        val axisColor = theme.text.copy(alpha = 0.24f)

        drawLine(
            color = axisColor,
            start = Offset(leftPad, 0f),
            end = Offset(leftPad, plotH),
            strokeWidth = 1f,
        )
        drawLine(
            color = axisColor,
            start = Offset(leftPad, plotH),
            end = Offset(leftPad + plotW, plotH),
            strokeWidth = 1f,
        )

        val span = (hi - lo).coerceAtLeast(hi * 0.05).coerceAtLeast(0.5)
        val path = Path()
        costs.forEachIndexed { index, cost ->
            val x = leftPad + if (costs.size == 1) plotW / 2f else index * (plotW / (costs.size - 1))
            val y = plotH - ((cost - lo) / span).toFloat().coerceIn(0f, 1f) * (plotH - 6f) - 3f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = theme.primary, style = Stroke(width = 2.5f))

        drawText(hiLabel, topLeft = Offset(2f, 0f))
        drawText(loLabel, topLeft = Offset(2f, (plotH - loLabel.size.height).coerceAtLeast(0f)))

        val tickIndices = listOf(0, contracts.size / 2, contracts.lastIndex).distinct()
        tickIndices.forEach { idx ->
            val strikeLabel = textMeasurer.measure(usFmt("%.0f", contracts[idx].strike), style = axisStyle)
            val centerX = leftPad + if (contracts.size == 1) plotW / 2f else idx * (plotW / (contracts.size - 1))
            val tx = (centerX - strikeLabel.size.width / 2f)
                .coerceIn(leftPad, (leftPad + plotW - strikeLabel.size.width).coerceAtLeast(leftPad))
            drawText(strikeLabel, topLeft = Offset(tx, plotH + 2.dp.toPx()))
        }
    }
}
