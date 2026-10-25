package com.bysel.trader.ui.screens

import com.bysel.trader.R
import androidx.compose.ui.res.stringResource

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
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
private data class MoreMenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
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
    onFnoOptionsClick: () -> Unit = {},
    onFnoFuturesClick: () -> Unit = {},
    onHeatmapClick: () -> Unit = {},
    onMutualFundsClick: () -> Unit,
    onIpoClick: () -> Unit,
    onEtfClick: () -> Unit,
    onSgbClick: () -> Unit = {},
    onSipClick: () -> Unit,
    onMyIpoApplicationsClick: () -> Unit,
    onAdvancedOrdersClick: () -> Unit,
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
    var showFnoChoice by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val labsEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Explore,
            title = stringResource(R.string.more_scanner),
            subtitle = stringResource(R.string.more_scanner_sub),
            onClick = onScannerClick,
            keywords = "screener quality momentum value swing",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Bolt,
            title = stringResource(R.string.more_signal_lab),
            subtitle = stringResource(R.string.more_signal_lab_sub),
            onClick = onSignalLabClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.GridView,
            title = stringResource(R.string.more_heatmap),
            subtitle = stringResource(R.string.more_heatmap_sub),
            onClick = onHeatmapClick,
            keywords = "heatmap sector breadth movers",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Analytics,
            title = stringResource(R.string.more_risk_lab),
            subtitle = stringResource(R.string.more_risk_lab_sub),
            onClick = onRiskLabClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.AutoStories,
            title = stringResource(R.string.more_journal),
            subtitle = stringResource(R.string.more_journal_sub),
            onClick = onTradeJournalClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Receipt,
            title = stringResource(R.string.more_order_history),
            subtitle = stringResource(R.string.more_order_history_sub),
            onClick = onOrderHistoryClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Bookmarks,
            title = stringResource(R.string.more_watchlist),
            subtitle = stringResource(R.string.more_watchlist_sub),
            onClick = onWatchlistClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.People,
            title = stringResource(R.string.more_smart_money),
            subtitle = stringResource(R.string.more_smart_money_sub),
            onClick = onInvestorPortfoliosClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Psychology,
            title = stringResource(R.string.more_pretrade),
            subtitle = stringResource(R.string.more_pretrade_sub),
            onClick = onCopilotCenterClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.more_earnings),
            subtitle = stringResource(R.string.more_earnings_sub),
            onClick = onEarningsCalendarClick,
        ),
    )

    val utilityEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Search,
            title = stringResource(R.string.more_search),
            subtitle = stringResource(R.string.more_search_sub),
            onClick = onSearchClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.more_alerts),
            subtitle = if (activeAlertCount > 0) {
                stringResource(R.string.more_alerts_sub_active, activeAlertCount)
            } else {
                stringResource(R.string.more_alerts_sub)
            },
            onClick = onAlertsClick,
            badgeCount = activeAlertCount,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.EventAvailable,
            title = stringResource(R.string.more_holidays),
            subtitle = stringResource(R.string.more_holidays_sub),
            onClick = onMarketCalendarClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.EmojiEvents,
            title = stringResource(R.string.more_achievements),
            subtitle = stringResource(R.string.more_achievements_sub),
            onClick = onAchievementsClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.more_settings),
            subtitle = stringResource(R.string.more_settings_sub),
            onClick = onSettingsClick,
        ),
    )

    val investingEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = stringResource(R.string.more_mf),
            subtitle = stringResource(R.string.more_mf_sub),
            onClick = onMutualFundsClick,
            keywords = "mf funds sip",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.BusinessCenter,
            title = stringResource(R.string.more_ipo),
            subtitle = stringResource(R.string.more_ipo_sub),
            onClick = onIpoClick,
            keywords = "asba listing ipo",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.PieChart,
            title = stringResource(R.string.more_etf),
            subtitle = stringResource(R.string.more_etf_sub),
            onClick = onEtfClick,
            keywords = "etf basket index",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Savings,
            title = stringResource(R.string.more_sgb),
            subtitle = stringResource(R.string.more_sgb_sub),
            onClick = onSgbClick,
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Payments,
            title = stringResource(R.string.more_sip),
            subtitle = stringResource(R.string.more_sip_sub),
            onClick = onSipClick,
        ),
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.Assignment,
            title = stringResource(R.string.more_my_ipo),
            subtitle = stringResource(R.string.more_my_ipo_sub),
            onClick = onMyIpoApplicationsClick,
        ),
    )

    val advancedEntries = listOf(
        MoreMenuEntry(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.more_adv_orders),
            subtitle = stringResource(R.string.more_adv_orders_sub),
            onClick = onAdvancedOrdersClick,
            keywords = "trigger basket gtt",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.AccountBalance,
            title = stringResource(R.string.more_wealth),
            subtitle = stringResource(R.string.more_wealth_sub),
            onClick = onWealthOsClick,
        ),
    )

    val tradeEntries = listOf(
        MoreMenuEntry(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = stringResource(R.string.more_equity),
            subtitle = stringResource(R.string.more_equity_sub),
            onClick = onEquityClick,
            keywords = "stocks spot trade cash",
        ),
        MoreMenuEntry(
            icon = Icons.Filled.Analytics,
            title = stringResource(R.string.more_fno),
            subtitle = stringResource(R.string.more_fno_sub),
            onClick = { showFnoChoice = true },
            keywords = "fno futures options derivatives chain",
        ),
    )
    val catalog = labsEntries + utilityEntries + investingEntries + advancedEntries + tradeEntries
    val filtered = if (query.isBlank()) emptyList() else catalog.filter { it.matches(query) }

    if (showFnoChoice) {
        AlertDialog(
            onDismissRequest = { showFnoChoice = false },
            title = { Text(stringResource(R.string.more_fno_title)) },
            text = { Text(stringResource(R.string.more_fno_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showFnoChoice = false
                    onFnoOptionsClick()
                }) { Text(stringResource(R.string.trade_tab_options)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFnoChoice = false
                    onFnoFuturesClick()
                }) { Text(stringResource(R.string.trade_tab_futures)) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.more_title),
                subtitle = stringResource(R.string.more_subtitle),
            )
        }

        item {
            val theme = LocalAppTheme.current
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.more_search_hint)) },
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
                        text = stringResource(R.string.more_no_match),
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.more_results)) }
                items(filtered, key = { "hit-${it.title}" }) { entry ->
                    MoreMenuItem(entry = entry)
                }
            }
        } else {
        item { SectionHeader(stringResource(R.string.more_section_labs)) }
        items(labsEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item { SectionHeader(stringResource(R.string.more_section_desks)) }
        items(tradeEntries) { entry ->
            MoreMenuItem(entry = entry)
        }

        item { SectionHeader(stringResource(R.string.more_section_utility)) }
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
                        text = stringResource(R.string.more_section_invest),
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalAppTheme.current.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (investExpanded) {
                            stringResource(R.string.more_invest_expanded)
                        } else {
                            stringResource(R.string.more_invest_collapsed)
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

        item { SectionHeader(stringResource(R.string.more_section_advanced)) }
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
                val theme = LocalAppTheme.current
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(theme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = theme.onPrimaryContainer,
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
