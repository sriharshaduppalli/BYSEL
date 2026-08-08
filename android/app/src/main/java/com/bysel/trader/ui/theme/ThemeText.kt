package com.bysel.trader.ui.theme

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatSignedPct

/**
 * Text helpers from the Compose "Style text" guide — Material path (no experimental Styles API).
 */

/**
 * Scrolls long single-line labels (company names) when they don't fit — better than hard ellipsis
 * on watchlist / search rows where the full name matters.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalAppTheme.current.text,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(),
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/**
 * "₹1,234.50 · +1.2%" with price in body color and only the % in green/red.
 */
@Composable
fun PriceChangeLine(
    last: Double,
    pctChange: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val theme = LocalAppTheme.current
    val changeColor = theme.colorForChange(pctChange)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = theme.text, fontWeight = FontWeight.Medium)) {
                append(formatInr(last))
            }
            withStyle(SpanStyle(color = theme.textSecondary)) {
                append(" · ")
            }
            withStyle(SpanStyle(color = changeColor, fontWeight = FontWeight.SemiBold)) {
                append(formatSignedPct(pctChange))
            }
        },
        modifier = modifier,
        style = style.asNumeric(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
