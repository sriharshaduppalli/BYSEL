package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.PaperPortfolioRisk
import com.bysel.trader.portfolio.PaperPortfolioRiskMath
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatInrCompact
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import kotlin.math.abs
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioRiskDashboardCard(risk: PaperPortfolioRisk) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Portfolio risk",
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Paper · educational",
                    color = theme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompactStat(
                    label = "Value",
                    value = formatInrCompact(risk.totalValue),
                    color = theme.text,
                )
                CompactStat(
                    label = "Day P&L",
                    value = if (risk.dayPnlAvailable) {
                        "${signedInr(risk.dayPnl)}  ${formatSignedPct(risk.dayPnlPercent)}"
                    } else {
                        "—"
                    },
                    color = when {
                        !risk.dayPnlAvailable -> theme.textSecondary
                        risk.dayPnl >= 0.0 -> theme.positive
                        else -> theme.negative
                    },
                    alignEnd = true,
                )
            }

            val score = risk.byselScore
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (score.valueWeighted != null) {
                        "BYSEL Score ${score.valueWeighted}"
                    } else {
                        "BYSEL Score —"
                    },
                    color = theme.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        score.valueWeighted == null -> "no scores on these names yet"
                        score.missingCount > 0 ->
                            "value-weighted · ${score.scoredCount} of ${score.scoredCount + score.missingCount} names"
                        else -> "value-weighted · all names scored"
                    },
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Concentration",
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            val conc = risk.concentration
            RiskGaugeBar(
                value = conc.gauge,
                higherIsRiskier = true,
            )
            Text(
                "Top 1 ${conc.top1Symbol.ifBlank { "—" }}  ${pct(conc.top1Pct)}   ·   Top 5  ${pct(conc.top5Pct)}",
                color = theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                conc.gaugeHint,
                color = theme.textSecondary.copy(alpha = 0.85f),
                fontSize = 10.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Sector mix",
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            RiskGaugeBar(
                value = risk.sectorSpread.gauge,
                higherIsRiskier = false,
            )
            Text(
                "${risk.sectorSpread.sectorCount} sector${if (risk.sectorSpread.sectorCount == 1) "" else "s"}  ·  ${risk.sectorSpread.gaugeHint}",
                color = theme.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (risk.sectors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    risk.sectors.take(8).forEach { sector ->
                        Text(
                            text = "${sector.name} ${pct(sector.weightPct)}",
                            color = theme.text,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.mutedSurface)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "What-if · Nifty move",
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhatIfChip(
                    label = "If Nifty −5%",
                    value = signedInr(risk.whatIf.niftyDown5),
                    modifier = Modifier.weight(1f),
                )
                WhatIfChip(
                    label = "If Nifty −10%",
                    value = signedInr(risk.whatIf.niftyDown10),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                risk.whatIf.label,
                color = theme.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                risk.volatility.note.ifBlank { "Needs more history" },
                color = theme.textSecondary,
                fontSize = 10.sp,
            )
            Text(
                risk.importNote.ifBlank { PaperPortfolioRiskMath.IMPORT_NOTE },
                color = theme.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                risk.disclaimer.ifBlank { "Educational paper metrics. Not advice." },
                color = theme.textSecondary.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: String,
    color: Color,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = LocalAppTheme.current.textSecondary, fontSize = 10.sp)
        Text(
            value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RiskGaugeBar(value: Int, higherIsRiskier: Boolean) {
    val theme = LocalAppTheme.current
    val clamped = value.coerceIn(0, 100)
    val color = if (higherIsRiskier) {
        when {
            clamped >= 60 -> theme.negative
            clamped >= 40 -> Color(0xFFFF9100)
            clamped >= 25 -> Color(0xFFFFB300)
            else -> theme.positive
        }
    } else {
        when {
            clamped >= 70 -> theme.positive
            clamped >= 50 -> Color(0xFFFFB300)
            clamped >= 30 -> Color(0xFFFF9100)
            else -> theme.negative
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = theme.mutedSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "$clamped",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WhatIfChip(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.mutedSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, color = theme.textSecondary, fontSize = 10.sp)
        Text(
            value,
            color = theme.negative,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun pct(value: Double): String = "${String.format(Locale.US, "%.1f", value)}%"

private fun signedInr(value: Double): String {
    val sign = if (value >= 0.0) "+" else "-"
    return "$sign${formatInr(abs(value))}"
}
