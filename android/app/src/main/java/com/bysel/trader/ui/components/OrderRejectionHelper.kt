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

data class RejectionResolution(
    val title: String,
    val explanation: String,
    val primaryCta: String,
    val secondaryCta: String? = null,
    val category: RejectionCategory,
    val icon: ImageVector,
    val accentColor: Color = Color(0xFFE53935),
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
        accentColor = Color(0xFFE53935),
    ),
    // Market closed
    Regex("market.*(closed?|not.open|holiday|halted)|outside.*(trading.hours|market.hours)|pre.market|post.market", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Market Closed",
        explanation = "Orders cannot be placed outside trading hours. NSE/BSE hours are 9:15 AM – 3:30 PM on weekdays.",
        primaryCta = "Set GTT Alert",
        secondaryCta = "View Market Hours",
        category = RejectionCategory.MARKET_CLOSED,
        icon = Icons.Filled.Schedule,
        accentColor = Color(0xFFFF8F00),
    ),
    // Quantity limits
    Regex("(quantity|qty).*(exceed|limit|max|too.high|too.large)|max.*(quantity|qty)|lot.size", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Quantity Exceeds Limit",
        explanation = "The quantity you entered exceeds the maximum allowed for this instrument in a single order.",
        primaryCta = "Reduce Quantity",
        secondaryCta = "Split Order",
        category = RejectionCategory.QUANTITY,
        icon = Icons.Filled.FormatListNumbered,
        accentColor = Color(0xFFFF8F00),
    ),
    // Price / limit rejection
    Regex("price.*(circuit|freeze|limit|band|range|invalid|outside)|circuit.*(limit|breaker)|upper.circuit|lower.circuit|price.deviation", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Price Out of Allowed Range",
        explanation = "Your limit price is outside the exchange-permitted circuit band for this stock.",
        primaryCta = "Update Price",
        secondaryCta = "Use Market Order",
        category = RejectionCategory.PRICE,
        icon = Icons.Filled.PriceChange,
        accentColor = Color(0xFFFF8F00),
    ),
    // RMS / Risk block
    Regex("rms.*(reject|block|limit|exposure)|risk.*(reject|block|exceeded|limit)|exposure.*(limit|exceeded)|var.limit|margin.shortfall", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Risk Check Failed",
        explanation = "Your broker's risk management system has blocked this order. This may be due to exposure limits or margin shortfall.",
        primaryCta = "Contact Support",
        secondaryCta = "View Risk Limits",
        category = RejectionCategory.RISK,
        icon = Icons.Filled.Shield,
        accentColor = Color(0xFFE53935),
    ),
    // KYC / compliance
    Regex("kyc|not.verified|verification.pending|account.not.activated|demat.not.activated|dpid", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Account Verification Needed",
        explanation = "Your account is pending KYC or Demat activation. Complete verification to start trading.",
        primaryCta = "Verify Account",
        category = RejectionCategory.KYC,
        icon = Icons.Filled.VerifiedUser,
        accentColor = Color(0xFFE53935),
    ),
    // Instrument not available
    Regex("symbol.*(not.found|invalid|unavailable|suspended|delisted)|instrument.*(not.tradable|suspended|halted)|trading.suspended", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Instrument Not Tradable",
        explanation = "This symbol may be suspended, delisted, or temporarily halted on the exchange.",
        primaryCta = "Search Another Stock",
        category = RejectionCategory.INSTRUMENT,
        icon = Icons.Filled.DoNotDisturb,
        accentColor = Color(0xFFE53935),
    ),
    // Rate limit
    Regex("rate.limit|too.many.requests|throttle|slow.down", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Too Many Requests",
        explanation = "You have placed orders too quickly. Please wait a few seconds before trying again.",
        primaryCta = "Retry in 10s",
        category = RejectionCategory.RATE_LIMIT,
        icon = Icons.Filled.HourglassEmpty,
        accentColor = Color(0xFFFF8F00),
    ),
    // Technical / network errors
    Regex("timeout|connection|network.error|server.error|503|502|500|internal.error", RegexOption.IGNORE_CASE) to RejectionResolution(
        title = "Connection Error",
        explanation = "There was a temporary network or server issue. Your order was NOT placed.",
        primaryCta = "Retry Order",
        secondaryCta = "Check Status",
        category = RejectionCategory.TECHNICAL,
        icon = Icons.Filled.WifiOff,
        accentColor = Color(0xFFFF8F00),
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
) {
    val resolution = resolveRejection(rawMessage) ?: RejectionResolution(
        title = "Order Rejected",
        explanation = rawMessage.take(160),
        primaryCta = "Contact Support",
        category = RejectionCategory.UNKNOWN,
        icon = Icons.Filled.ErrorOutline,
        accentColor = Color(0xFFE53935),
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = resolution.accentColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = resolution.icon,
                    contentDescription = null,
                    tint = resolution.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resolution.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = resolution.accentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = resolution.explanation,
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPrimaryCta(resolution) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resolution.accentColor
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text(resolution.primaryCta, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                resolution.secondaryCta?.let { label ->
                    OutlinedButton(
                        onClick = { /* Secondary CTA handled by caller if needed */ },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = resolution.accentColor),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        )
                    ) {
                        Text(label, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
