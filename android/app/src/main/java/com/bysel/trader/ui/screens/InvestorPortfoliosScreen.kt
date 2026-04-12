package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.InvestorHolding
import com.bysel.trader.data.models.InvestorHoldingDeltaFeed
import com.bysel.trader.data.models.InvestorPortfolioChangeFeed
import com.bysel.trader.data.models.InvestorPortfolio
import com.bysel.trader.data.models.SmartMoneyIdeaFeedCard
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun InvestorPortfoliosScreen(
    portfolios: List<InvestorPortfolio>,
    portfolioChanges: List<InvestorPortfolioChangeFeed>,
    ideas: List<SmartMoneyIdeaFeedCard>,
    quarterLabel: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    var selectedInvestorId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPortfolio = portfolios.firstOrNull { it.id == selectedInvestorId }
    val selectedPortfolioChanges = portfolioChanges.firstOrNull { it.investorId == selectedInvestorId }
    val selectedIdeas = ideas.filter { idea ->
        selectedPortfolio?.investorName?.let { investorName ->
            idea.backingInvestors.any { it.equals(investorName, ignoreCase = true) }
        } == true
    }

    LaunchedEffect(Unit) {
        if (portfolios.isEmpty() || portfolioChanges.isEmpty() || ideas.isEmpty()) onRefresh()
    }

    Scaffold(containerColor = theme.surface) { paddingValues ->
        if (selectedPortfolio != null) {
            InvestorPortfolioDetailView(
                portfolio = selectedPortfolio,
                changeFeed = selectedPortfolioChanges,
                ideas = selectedIdeas,
                onBack = { selectedInvestorId = null },
                onOpenSymbol = onOpenSymbol,
                paddingValues = paddingValues,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.surface),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = paddingValues.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SmartMoneyHeroCard(
                        portfolioCount = portfolios.size,
                        ideaCount = ideas.size,
                        quarterLabel = quarterLabel,
                        isLoading = isLoading,
                        onRefresh = onRefresh,
                    )
                }

                if (isLoading && portfolios.isEmpty() && portfolioChanges.isEmpty() && ideas.isEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                                color = theme.primary,
                            )
                            Text(
                                text = "Loading smart money portfolios…",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                if (portfolioChanges.isNotEmpty()) {
                    item {
                        SmartMoneySectionHeader(
                            title = "Portfolio Changes",
                            subtitle = quarterLabel ?: "Track what changed across the latest disclosed quarter.",
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(portfolioChanges, key = { it.investorId }) { feed ->
                                PortfolioChangeCard(
                                    feed = feed,
                                    onOpenSymbol = onOpenSymbol,
                                )
                            }
                        }
                    }
                }

                if (ideas.isNotEmpty()) {
                    item {
                        SmartMoneySectionHeader(
                            title = "Explainable Idea Feed",
                            subtitle = "Why a symbol is surfacing now, which investors back it, and what to watch before acting.",
                        )
                    }
                    items(ideas, key = { it.ideaId }) { idea ->
                        SmartMoneyIdeaCard(
                            idea = idea,
                            onOpenSymbol = onOpenSymbol,
                        )
                    }
                }

                item {
                    SmartMoneySectionHeader(
                        title = "Investor Library",
                        subtitle = "Open an investor profile to inspect holdings, style, and the quarter's key position changes.",
                    )
                }

                items(portfolios, key = { it.id }) { portfolio ->
                    InvestorPortfolioCard(
                        portfolio = portfolio,
                        changeFeed = portfolioChanges.firstOrNull { it.investorId == portfolio.id },
                        onClick = { selectedInvestorId = portfolio.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartMoneyHeroCard(
    portfolioCount: Int,
    ideaCount: Int,
    quarterLabel: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.card,
                            theme.primary.copy(alpha = 0.22f),
                            theme.surface,
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Smart Money",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = "Track India's legendary market participants — their disclosed holdings and investment styles.",
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("$portfolioCount investors") })
                AssistChip(onClick = {}, label = { Text("$ideaCount ideas") })
            }
            if (!quarterLabel.isNullOrBlank()) {
                AssistChip(onClick = {}, label = { Text(quarterLabel) })
            }
            FilledTonalButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = theme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun InvestorPortfolioCard(
    portfolio: InvestorPortfolio,
    changeFeed: InvestorPortfolioChangeFeed?,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    InvestorInitialsAvatar(
                        name = portfolio.investorName,
                        color = theme.primary,
                    )
                    Column {
                        Text(
                            text = portfolio.displayTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = portfolio.style,
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = { Text("AUM ${portfolio.aum}") },
                )
            }

            Text(
                text = portfolio.bio,
                fontSize = 12.sp,
                color = theme.textSecondary,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            changeFeed?.changes?.firstOrNull()?.let { delta ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Latest move: ${delta.symbol} ${formatDelta(delta.deltaPct)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (delta.deltaPct >= 0) theme.positive else theme.negative,
                        )
                        Text(
                            text = delta.commentary,
                            fontSize = 11.sp,
                            color = theme.textSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // Top 3 holdings preview row
            if (portfolio.holdings.isNotEmpty()) {
                Text(
                    text = "Top positions",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(portfolio.holdings.take(4), key = { it.symbol }) { holding ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "${holding.symbol} · ${String.format("%.1f", holding.holdingPct)}%",
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestorPortfolioDetailView(
    portfolio: InvestorPortfolio,
    changeFeed: InvestorPortfolioChangeFeed?,
    ideas: List<SmartMoneyIdeaFeedCard>,
    onBack: () -> Unit,
    onOpenSymbol: (String) -> Unit,
    paddingValues: PaddingValues,
) {
    val theme = LocalAppTheme.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = paddingValues.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Header / back
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onBack) { Text("← Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = portfolio.displayTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = portfolio.investorName,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                    )
                }
            }
        }

        item {
            // Bio card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(portfolio.style) })
                        AssistChip(onClick = {}, label = { Text("AUM ${portfolio.aum}") })
                    }
                    Text(
                        text = portfolio.bio,
                        fontSize = 13.sp,
                        color = theme.textSecondary,
                        lineHeight = 20.sp,
                    )
                }
            }
        }

        changeFeed?.let { feed ->
            item {
                SmartMoneySectionHeader(
                    title = "Quarter Change Map",
                    subtitle = feed.quarterLabel,
                )
            }
            items(feed.changes, key = { it.symbol }) { delta ->
                InvestorDeltaRow(
                    delta = delta,
                    onOpenSymbol = onOpenSymbol,
                )
            }
        }

        if (ideas.isNotEmpty()) {
            item {
                SmartMoneySectionHeader(
                    title = "Related Ideas",
                    subtitle = "Signals where this investor appears among the tracked backing disclosures.",
                )
            }
            items(ideas, key = { it.ideaId }) { idea ->
                SmartMoneyIdeaCard(
                    idea = idea,
                    onOpenSymbol = onOpenSymbol,
                )
            }
        }

        item {
            Text(
                text = "Disclosed Holdings (${portfolio.holdings.size})",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
        }

        items(portfolio.holdings, key = { it.symbol }) { holding ->
            InvestorHoldingRow(
                holding = holding,
                onOpenSymbol = onOpenSymbol,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Holdings data from latest publicly available SEBI/BSE regulatory disclosures. This is not financial advice.",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun InvestorHoldingRow(
    holding: InvestorHolding,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.symbol,
                    color = theme.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = holding.companyName,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${String.format("%.2f", holding.holdingPct)}% holding",
                        color = theme.primary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = "· ${holding.sector}",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Button(onClick = { onOpenSymbol(holding.symbol) }) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun PortfolioChangeCard(
    feed: InvestorPortfolioChangeFeed,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(feed.investorName, color = theme.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(feed.style, color = theme.textSecondary, fontSize = 11.sp)
            feed.changes.forEachIndexed { index, delta ->
                if (index > 0) {
                    HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.14f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(delta.symbol, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = formatDelta(delta.deltaPct),
                            color = if (delta.deltaPct >= 0) theme.positive else theme.negative,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = delta.commentary,
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(onClick = { onOpenSymbol(delta.symbol) }) {
                        Text("Open ${delta.symbol}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartMoneyIdeaCard(
    idea: SmartMoneyIdeaFeedCard,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${idea.symbol} • ${idea.action.replace('_', ' ')}",
                        color = theme.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = idea.companyName,
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(onClick = {}, label = { Text("${idea.confidence}%") })
            }

            Text(
                text = idea.thesis,
                color = theme.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = idea.whyNow,
                color = theme.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = "Risk: ${idea.riskNote}",
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            if (idea.backingInvestors.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(idea.backingInvestors, key = { it }) { investor ->
                        AssistChip(onClick = {}, label = { Text(investor, maxLines = 1) })
                    }
                }
            }
            if (idea.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(idea.tags, key = { it }) { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.replace('_', ' ')) })
                    }
                }
            }
            Button(onClick = { onOpenSymbol(idea.symbol) }) {
                Text("Open ${idea.symbol}")
            }
        }
    }
}

@Composable
private fun InvestorDeltaRow(
    delta: InvestorHoldingDeltaFeed,
    onOpenSymbol: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(delta.symbol, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(delta.companyName, color = theme.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${delta.action.replace('_', ' ')} • ${formatHolding(delta.previousHoldingPct)} to ${formatHolding(delta.currentHoldingPct)}",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = delta.commentary,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDelta(delta.deltaPct),
                    color = if (delta.deltaPct >= 0) theme.positive else theme.negative,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Button(onClick = { onOpenSymbol(delta.symbol) }) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SmartMoneySectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppTheme.current.text,
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = LocalAppTheme.current.textSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun InvestorInitialsAvatar(name: String, color: Color) {
    val initials = name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private fun formatDelta(value: Double): String = buildString {
    if (value > 0.0) append("+")
    append(String.format("%.2f", value))
    append("%")
}

private fun formatHolding(value: Double): String = String.format("%.2f%%", value)
