package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.BuildConfig
import com.bysel.trader.ui.theme.LocalAppTheme

private data class MoreMenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val onClick: () -> Unit,
)

@Composable
fun MoreScreen(
    onSearchClick: () -> Unit,
    onLiveQuotesClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onMutualFundsClick: () -> Unit,
    onIpoClick: () -> Unit,
    onEtfClick: () -> Unit,
    onSipClick: () -> Unit,
    onMyIpoApplicationsClick: () -> Unit,
    onAdvancedOrdersClick: () -> Unit,
    onDerivativesClick: () -> Unit,
    onWealthOsClick: () -> Unit,
    onCopilotCenterClick: () -> Unit,
    onSignalLabClick: () -> Unit,
    onInvestorPortfoliosClick: () -> Unit,
) {
    val utilityEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Search,
            title = "Search Stocks",
            subtitle = "Find any Indian stock quickly",
            gradientColors = listOf(Color(0xFF1A237E), Color(0xFF7C4DFF)),
            onClick = onSearchClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Notifications,
            title = "Price Alerts",
            subtitle = "Real-time trigger notifications",
            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFFB300)),
            onClick = onAlertsClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "Theme, privacy, and app preferences",
            gradientColors = listOf(Color(0xFF424242), Color(0xFF757575)),
            onClick = onSettingsClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.EmojiEvents,
            title = "Achievements",
            subtitle = "Your milestones and streaks",
            gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF81C784)),
            onClick = onAchievementsClick,
        ),
    )

    val investingEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = "Mutual Funds",
            subtitle = "Explore funds and start SIPs",
            gradientColors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
            onClick = onMutualFundsClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.BusinessCenter,
            title = "IPO Listings",
            subtitle = "Upcoming, open, and listed IPOs",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)),
            onClick = onIpoClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "ETFs",
            subtitle = "Index and sector ETF baskets",
            gradientColors = listOf(Color(0xFF00695C), Color(0xFF26A69A)),
            onClick = onEtfClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Payments,
            title = "My SIPs",
            subtitle = "Track and manage SIP plans",
            gradientColors = listOf(Color(0xFFEF6C00), Color(0xFFFFA726)),
            onClick = onSipClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.Assignment,
            title = "My IPO Applications",
            subtitle = "Application status and allotment",
            gradientColors = listOf(Color(0xFF455A64), Color(0xFF90A4AE)),
            onClick = onMyIpoApplicationsClick,
        ),
    )

    val advancedEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Settings,
            title = "Advanced Orders",
            subtitle = "Triggers, baskets, and execution controls",
            gradientColors = listOf(Color(0xFF283593), Color(0xFF5C6BC0)),
            onClick = onAdvancedOrdersClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "Derivatives Intelligence",
            subtitle = "Option chain, Greeks, and strategy risk",
            gradientColors = listOf(Color(0xFF00838F), Color(0xFF4DD0E1)),
            onClick = onDerivativesClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = "Wealth OS",
            subtitle = "Family goals and net-worth planning",
            gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)),
            onClick = onWealthOsClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "Signal Lab",
            subtitle = "Filter breakouts, volume spikes, and yield setups",
            gradientColors = listOf(Color(0xFF004D40), Color(0xFF26A69A)),
            onClick = onSignalLabClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Psychology,
            title = "Copilot Center",
            subtitle = "Pre-trade and post-trade guidance",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFBA68C8)),
            onClick = onCopilotCenterClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.People,
            title = "Smart Money",
            subtitle = "Track legendary investor disclosed holdings",
            gradientColors = listOf(Color(0xFF1B5E20), Color(0xFF43A047)),
            onClick = onInvestorPortfoliosClick,
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "More",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LocalAppTheme.current.text,
            )
        }

        item {
            Text(
                text = "Faster gestures: swipe left-right between Home tabs, swipe back inside feature screens",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickInfoChip(label = "Live Quotes", onClick = onLiveQuotesClick)
                QuickInfoChip(label = "ETF", onClick = onEtfClick)
                QuickInfoChip(label = "F&O", onClick = onDerivativesClick)
                QuickInfoChip(label = "SIP", onClick = onSipClick)
                QuickInfoChip(label = "Signal Lab", onClick = onSignalLabClick)
                QuickInfoChip(label = "AI Copilot", onClick = onCopilotCenterClick)
                QuickInfoChip(label = "Smart Money", onClick = onInvestorPortfoliosClick)
            }
        }

        item { SectionHeader("Utility") }
        items(utilityEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item { SectionHeader("Invest") }
        items(investingEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item { SectionHeader("Pro Tools") }
        items(advancedEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item {
            Text(
                text = "BYSEL v${BuildConfig.VERSION_NAME} - AI-Powered Trading",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalAppTheme.current.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun QuickInfoChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = LocalAppTheme.current.card,
            labelColor = LocalAppTheme.current.text,
        ),
        border = null,
    )
}

@Composable
private fun MoreMenuItem(entry: MoreMenuEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(entry.gradientColors),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = LocalAppTheme.current.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.subtitle,
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 12.sp,
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalAppTheme.current.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
