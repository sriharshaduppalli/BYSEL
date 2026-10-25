package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

/**
 * Maps raw backend order rejection messages to structured, user-friendly
 * copy with deterministic recovery CTAs (S2-002).
 */

enum class RejectionCategory {
    FUNDS,
    MARKET_CLOSED,
    QUANTITY,
    PRICE,
    RISK,
    KYC,
    INSTRUMENT,
    RATE_LIMIT,
    TECHNICAL,
    UNKNOWN
}

/** Blocking rejections use the skin's negative hue; advisory ones its caution hue. */
enum class RejectionTone { BLOCKING, ADVISORY }

data class RejectionResolution(
    val title: String,
    val explanation: String,
    val primaryCta: String,
    val secondaryCta: String? = null,
    val category: RejectionCategory,
    val icon: ImageVector,
    val tone: RejectionTone = RejectionTone.BLOCKING,
)

private val REJECTION_RULES: List<Pair<Regex, RejectionResolution>> = listOf(
    // Insufficient funds
    Regex("insufficient.*(funds?|balance|margin)|not.enough.*(funds?|balance|margin)|low.balance", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Insufficient Funds",
        explanation = "Your wallet balance is too low to cover this order including all charges.",
        primaryCta = "Add Funds",
        secondaryCta = "Reduce Quantity",
        category = RejectionCategory.FUNDS,
        icon = Icons.Filled.AccountBalanceWallet,
        tone = RejectionTone.BLOCKING,
    ),
    // Market closed (legacy copy — paper fills are allowed after hours)
    Regex("market.*(closed?|not.open|holiday|halted)|outside.*(trading.hours|market.hours)|pre.market|post.market|cannot trade", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "NSE Session Closed",
        explanation = "Live NSE is closed, but BYSEL paper trading still works using last session prices. Add practice credit if your wallet is empty.",
        primaryCta = "Dismiss",
        secondaryCta = "Add Funds",
        category = RejectionCategory.MARKET_CLOSED,
        icon = Icons.Filled.Schedule,
        tone = RejectionTone.ADVISORY,
    ),
    // Quantity limits
    Regex("(quantity|qty).*(exceed|limit|max|too.high|too.large)|max.*(quantity|qty)|lot.size", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Quantity Exceeds Limit",
        explanation = "The quantity you entered exceeds the maximum allowed for this instrument in a single order.",
        primaryCta = "Reduce Quantity",
        secondaryCta = "Split Order",
        category = RejectionCategory.QUANTITY,
        icon = Icons.Filled.FormatListNumbered,
        tone = RejectionTone.ADVISORY,
    ),
    // Price / limit rejection
    Regex("price.*(circuit|freeze|limit|band|range|invalid|outside)|circuit.*(limit|breaker)|upper.circuit|lower.circuit|price.deviation", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Price Out of Allowed Range",
        explanation = "Your limit price is outside the exchange-permitted circuit band for this stock.",
        primaryCta = "Update Price",
        secondaryCta = "Use Market Order",
        category = RejectionCategory.PRICE,
        icon = Icons.Filled.PriceChange,
        tone = RejectionTone.ADVISORY,
    ),
    // RMS / Risk block
    Regex("rms.*(reject|block|limit|exposure)|risk.*(reject|block|exceeded|limit)|exposure.*(limit|exceeded)|var.limit|margin.shortfall", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Practice Risk Check Blocked This",
        explanation = "BYSEL's paper risk check stopped this order — usually the position would be too large for your practice wallet or concentration limit. Try a smaller size.",
        primaryCta = "Dismiss",
        secondaryCta = "Reduce Quantity",
        category = RejectionCategory.RISK,
        icon = Icons.Filled.Shield,
        tone = RejectionTone.BLOCKING,
    ),
    // Sign-in / account state (paper app: there is no KYC or demat step)
    Regex("kyc|not.verified|verification.pending|account.not.activated|demat.not.activated|dpid|unauthori[sz]ed|session.expired|401", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Sign-in Needed",
        explanation = "Your BYSEL sign-in has expired, so the paper order was not recorded. Sign in again and retry — no KYC or demat is involved.",
        primaryCta = "Dismiss",
        category = RejectionCategory.KYC,
        icon = Icons.Filled.VerifiedUser,
        tone = RejectionTone.BLOCKING,
    ),
    // Instrument not available
    Regex("symbol.*(not.found|invalid|unavailable|suspended|delisted)|instrument.*(not.tradable|suspended|halted)|trading.suspended", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Instrument Not Tradable",
        explanation = "This symbol may be suspended, delisted, or temporarily halted on the exchange.",
        primaryCta = "Search Another Stock",
        category = RejectionCategory.INSTRUMENT,
        icon = Icons.Filled.DoNotDisturb,
        tone = RejectionTone.BLOCKING,
    ),
    // Rate limit
    Regex("rate.limit|too.many.requests|throttle|slow.down", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Too Many Requests",
        explanation = "You have placed orders too quickly. Please wait a few seconds before trying again.",
        primaryCta = "Retry in 10s",
        category = RejectionCategory.RATE_LIMIT,
        icon = Icons.Filled.HourglassEmpty,
        tone = RejectionTone.ADVISORY,
    ),
    // Technical / network errors
    Regex("timeout|connection|network.error|server.error|503|502|500|internal.error", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Connection Error",
        explanation = "There was a temporary network or server issue. Your order was NOT placed.",
        primaryCta = "Retry Order",
        category = RejectionCategory.TECHNICAL,
        icon = Icons.Filled.WifiOff,
        tone = RejectionTone.ADVISORY,
    ),
)

/** Returns a structured rejection resolution for a given raw error message, or null if unknown. */
fun resolveRejection(rawMessage: String?): RejectionResolution? {
    if (rawMessage.isNullOrBlank()) return null
    return REJECTION_RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(rawMessage) }?.second
}

/**
 * Drop-in rejection banner that shows above the trade confirmation button.
 * Automatically resolves the raw message to friendly UX with recovery CTAs.
 */
@Composable
fun OrderRejectionBanner(
    rawMessage: String,
    onPrimaryCta: (RejectionResolution) -> Unit,
    modifier: Modifier = Modifier,
    onSecondaryCta: ((RejectionResolution) -> Unit)? = null,
) {
    val resolution = resolveRejection(rawMessage) ?: RejectionResolution(
        title = "Paper Order Not Recorded",
        explanation = rawMessage.take(160),
        primaryCta = "Dismiss",
        category = RejectionCategory.UNKNOWN,
        icon = Icons.Filled.ErrorOutline,
        tone = RejectionTone.BLOCKING,
    )

    val theme = LocalAppTheme.current
    val accentColor = if (resolution.tone == RejectionTone.BLOCKING) theme.negative else theme.caution
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = resolution.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resolution.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = resolution.explanation,
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPrimaryCta(resolution) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = com.bysel.trader.ui.theme.contentColorForFill(accentColor),
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text(resolution.primaryCta, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                // Only offered when the caller supplies a handler, so the button is never inert.
                val secondaryLabel = resolution.secondaryCta
                if (secondaryLabel != null && onSecondaryCta != null) {
                    OutlinedButton(
                        onClick = { onSecondaryCta(resolution) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        )
                    ) {
                        Text(secondaryLabel, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
