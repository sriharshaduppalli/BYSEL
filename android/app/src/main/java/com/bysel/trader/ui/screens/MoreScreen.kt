package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.BuildConfig
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.ScreenHeader
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.ui.theme.contentColorForFill

private data class MoreMenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val onClick: () -> Unit,
    val badgeCount: Int = 0,
    val keywords: String = "",
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
            subtitle.contains(q, ignoreCase = true) ||
            keywords.contains(q, ignoreCase = true)
    }
}

@Composable
fun MoreScreen(
    activeAlertCount: Int = 0,
    onSearchClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onEquityClick: () -> Unit = {},
    onFnoClick: () -> Unit = {},
    onMutualFundsClick: () -> Unit,
    onIpoClick: () -> Unit,
    onEtfClick: () -> Unit,
    onSgbClick: () -> Unit = {},
    onSipClick: () -> Unit,
    onMyIpoApplicationsClick: () -> Unit,
    onAdvancedOrdersClick: () -> Unit,
    onDerivativesClick: () -> Unit,
    onWealthOsClick: () -> Unit,
    onCopilotCenterClick: () -> Unit,
    onSignalLabClick: () -> Unit,
    onScannerClick: () -> Unit = {},
    onInvestorPortfoliosClick: () -> Unit,
    onRiskLabClick: () -> Unit,
    onEarningsCalendarClick: () -> Unit,
    onTradeJournalClick: () -> Unit,
    onOrderHistoryClick: () -> Unit = {},
    onWatchlistClick: () -> Unit,
    onMarketCalendarClick: () -> Unit,
) {
    var investExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val labsEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Explore,
            title = "Scanner",
            subtitle = "Long-term, swing, quality, momentum, value",
            gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF42A5F5)),
            onClick = onScannerClick,
            keywords = "screener quality momentum value swing",
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "Signal Lab",
            subtitle = "Breakouts, volume spikes, and yield setups",
            gradientColors = listOf(Color(0xFF004D40), Color(0xFF26A69A)),
            onClick = onSignalLabClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Analytics,
            title = "Risk Lab",
            subtitle = "VaR, Monte Carlo & portfolio stress test",
            gradientColors = listOf(Color(0xFFB71C1C), Color(0xFFEF5350)),
            onClick = onRiskLabClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.AutoStories,
            title = "Trade Journal",
            subtitle = "Practice reviews and behavioral insights",
            gradientColors = listOf(Color(0xFF4A148C), Color(0xFF9C27B0)),
            onClick = onTradeJournalClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Receipt,
            title = "Order history",
            subtitle = "Paper fills — buy/sell time, qty, and price",
            gradientColors = listOf(Color(0xFF37474F), Color(0xFF90A4AE)),
            onClick = onOrderHistoryClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Bookmarks,
            title = "My Watchlist",
            subtitle = "Sort and open every tracked symbol",
            gradientColors = listOf(Color(0xFF00695C), Color(0xFF4DB6AC)),
            onClick = onWatchlistClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.People,
            title = "Smart Money",
            subtitle = "Legendary investor disclosed holdings",
            gradientColors = listOf(Color(0xFF1B5E20), Color(0xFF43A047)),
            onClick = onInvestorPortfoliosClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Psychology,
            title = "Pre-Trade Checks",
            subtitle = "Rule-based order risk — not the AI chat tab",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFBA68C8)),
            onClick = onCopilotCenterClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.CalendarMonth,
            title = "Earnings Calendar",
            subtitle = "Upcoming results for your watchlist",
            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00BFA5)),
            onClick = onEarningsCalendarClick,
        ),
    )

    val utilityEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Search,
            title = "Search Stocks",
            subtitle = "Full NSE listed catalog (~2,400+)",
            gradientColors = listOf(Color(0xFF1A237E), Color(0xFF7C4DFF)),
            onClick = onSearchClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Notifications,
            title = "Price Alerts",
            subtitle = if (activeAlertCount > 0) {
                "$activeAlertCount active · checked about every 15 minutes in the background; faster while the app is open"
            } else {
                "Checked about every 15 minutes in the background; faster while the app is open"
            },
            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFFB300)),
            onClick = onAlertsClick,
            badgeCount = activeAlertCount,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.EventAvailable,
            title = "Market Holidays",
            subtitle = "NSE/BSE trading holidays and next session",
            gradientColors = listOf(Color(0xFF4A148C), Color(0xFF9575CD)),
            onClick = onMarketCalendarClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.EmojiEvents,
            title = "Achievements",
            subtitle = "Your milestones and streaks",
            gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF81C784)),
            onClick = onAchievementsClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "Theme, privacy, and app preferences",
            gradientColors = listOf(Color(0xFF424242), Color(0xFF757575)),
            onClick = onSettingsClick,
        ),
    )

    val investingEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = "Mutual Funds",
            subtitle = "Educational explorer (not live brokerage)",
            gradientColors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
            onClick = onMutualFundsClick,
            keywords = "mf funds sip",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.BusinessCenter,
            title = "IPO Listings",
            subtitle = "Paper practice apply — not live ASBA",
            gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)),
            onClick = onIpoClick,
            keywords = "asba listing ipo",
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "ETFs",
            subtitle = "Index and sector ETF baskets",
            gradientColors = listOf(Color(0xFF00695C), Color(0xFF26A69A)),
            onClick = onEtfClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Savings,
            title = "Sovereign Gold Bonds",
            subtitle = "SGB education — not live RBI applications",
            gradientColors = listOf(Color(0xFFF9A825), Color(0xFFFFD54F)),
            onClick = onSgbClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Payments,
            title = "My SIPs",
            subtitle = "Simulated SIP plans",
            gradientColors = listOf(Color(0xFFEF6C00), Color(0xFFFFA726)),
            onClick = onSipClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.Assignment,
            title = "My IPO Applications",
            subtitle = "Paper applications — not exchange allotment",
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
            subtitle = "Plain-English chain, recipes, and paper risk",
            gradientColors = listOf(Color(0xFF00838F), Color(0xFF4DD0E1)),
            onClick = onDerivativesClick,
            keywords = "fno f&o futures options chain",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = "Wealth OS",
            subtitle = "Family goals and net-worth planning (sim)",
            gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)),
            onClick = onWealthOsClick,
        ),
    )

    val tradeEntries = listOf(
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "Equity",
            subtitle = "Spot paper trading on the Trade tab",
            gradientColors = listOf(Color(0xFF1A237E), Color(0xFF5C6BC0)),
            onClick = onEquityClick,
            keywords = "stocks spot trade cash",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Analytics,
            title = "F&O",
            subtitle = "Options and futures practice desks",
            gradientColors = listOf(Color(0xFF006064), Color(0xFF26C6DA)),
            onClick = onFnoClick,
            keywords = "fno futures options derivatives",
        ),
    )
    val catalog = labsEntries + utilityEntries + investingEntries + advancedEntries + tradeEntries
    val filtered = if (query.isBlank()) emptyList() else catalog.filter { it.matches(query) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "More",
                subtitle = "Search any tool — explorers are educational, not live brokerage.",
            )
        }

        item {
            val theme = LocalAppTheme.current
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search Scanner, IPO, Journal…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = theme.textSecondary)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = theme.textSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.35f),
                    focusedPlaceholderColor = theme.textSecondary,
                    unfocusedPlaceholderColor = theme.textSecondary,
                    cursorColor = theme.primary,
                ),
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (query.isNotBlank()) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "No matching tools. Try Scanner, IPO, or Journal.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                item { SectionHeader("Results") }
                items(filtered, key = { "hit-${it.title}" }) { entry ->
                    MoreMenuItem(entry = entry)
                }
            }
        } else {
        item { SectionHeader("Practice & Labs") }
        items(labsEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item { SectionHeader("Utility") }
        items(utilityEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { investExpanded = !investExpanded }
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Invest explorers",
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalAppTheme.current.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (investExpanded) {
                            "Educational MF / IPO / ETF / SGB / SIP"
                        } else {
                            "Collapsed · search above or expand"
                        },
                        fontSize = 11.sp,
                        color = LocalAppTheme.current.textSecondary,
                    )
                }
                Icon(
                    imageVector = if (investExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (investExpanded) "Collapse" else "Expand",
                    tint = LocalAppTheme.current.textSecondary,
                )
            }
        }
        if (investExpanded) {
            items(investingEntries) { entry ->
                MoreMenuItem(entry = entry)
            }
        }

        item { SectionHeader("Advanced tools") }
        items(advancedEntries) { entry ->
            MoreMenuItem(entry = entry)
        }
        }

        item {
            Text(
                text = "BYSEL v${BuildConfig.VERSION_NAME} — paper trading + AI practice gym",
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
private fun MoreMenuItem(entry: MoreMenuEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (entry.badgeCount > 0) {
                        Badge(
                            containerColor = LocalAppTheme.current.primary,
                            contentColor = LocalAppTheme.current.onPrimary,
                        ) {
                            Text(
                                text = if (entry.badgeCount > 99) "99+" else entry.badgeCount.toString(),
                                fontSize = 10.sp,
                            )
                        }
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            brush = Brush.linearGradient(entry.gradientColors),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = contentColorForFill(entry.gradientColors.last()),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = LocalAppTheme.current.text,
                )
                Text(
                    text = entry.subtitle,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalAppTheme.current.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(0.dp))
    }
}
