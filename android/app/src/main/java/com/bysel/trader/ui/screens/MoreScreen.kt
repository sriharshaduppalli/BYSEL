package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun MoreScreen(
    onSearchClick: () -> Unit,
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "More",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = LocalAppTheme.current.text
        )
        Spacer(modifier = Modifier.height(24.dp))

        MoreMenuItem(
            icon = Icons.Filled.Search,
            title = "Search Stocks",
            subtitle = "Find any Indian stock from 363+ companies",
            gradientColors = listOf(Color(0xFF1A237E), Color(0xFF7C4DFF)),
            onClick = onSearchClick
        )
        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Notifications,
            title = "Price Alerts",
            subtitle = "Set up alerts for price movements",
            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFFB300)),
            onClick = onAlertsClick
        )
        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "Customize themes and preferences",
            gradientColors = listOf(Color(0xFF424242), Color(0xFF757575)),
            onClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.EmojiEvents,
            title = "Achievements",
            subtitle = "View your unlocked badges",
            gradientColors = listOf(Color(0xFF388E3C), Color(0xFF81C784)),
            onClick = onAchievementsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.AccountBalance,
            title = "Mutual Funds",
            subtitle = "Explore funds and start SIPs",
            gradientColors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
            onClick = onMutualFundsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.BusinessCenter,
            title = "IPO Listings",
            subtitle = "Track upcoming and open IPOs",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)),
            onClick = onIpoClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.ShowChart,
            title = "ETFs",
            subtitle = "Browse index and sector ETFs",
            gradientColors = listOf(Color(0xFF00695C), Color(0xFF26A69A)),
            onClick = onEtfClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Payments,
            title = "My SIPs",
            subtitle = "Manage your SIP plans",
            gradientColors = listOf(Color(0xFFEF6C00), Color(0xFFFFA726)),
            onClick = onSipClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Assignment,
            title = "My IPO Applications",
            subtitle = "Track submitted IPO applications",
            gradientColors = listOf(Color(0xFF455A64), Color(0xFF90A4AE)),
            onClick = onMyIpoApplicationsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Settings,
            title = "Advanced Orders",
            subtitle = "Order types, triggers, and baskets",
            gradientColors = listOf(Color(0xFF283593), Color(0xFF5C6BC0)),
            onClick = onAdvancedOrdersClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.ShowChart,
            title = "Derivatives Intelligence",
            subtitle = "Option chain, Greeks, and strategy risk",
            gradientColors = listOf(Color(0xFF00838F), Color(0xFF4DD0E1)),
            onClick = onDerivativesClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.AccountBalance,
            title = "Wealth OS",
            subtitle = "Family net worth and goal-linked investing",
            gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)),
            onClick = onWealthOsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoreMenuItem(
            icon = Icons.Filled.Psychology,
            title = "Copilot Center",
            subtitle = "Pre-trade, post-trade, and portfolio guidance",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFBA68C8)),
            onClick = onCopilotCenterClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // App info
        Text(
            "BYSEL v2.4 — AI-Powered Stock Trading",
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(gradientColors),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = LocalAppTheme.current.text,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = LocalAppTheme.current.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = LocalAppTheme.current.textSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalAppTheme.current.textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
