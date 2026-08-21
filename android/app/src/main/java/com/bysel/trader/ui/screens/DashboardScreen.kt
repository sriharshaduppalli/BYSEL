package com.bysel.trader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bysel.trader.data.LocalHabitInsights
import com.bysel.trader.data.PracticeHabitStore
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.IntradayTip
import com.bysel.trader.data.models.IntradayTipsResponse
import com.bysel.trader.data.models.MarketMoverQuote
import com.bysel.trader.data.models.MarketNewsHeadline
import com.bysel.trader.data.models.MarketStatus
import com.bysel.trader.data.models.OrderResponse
import com.bysel.trader.data.models.PracticeIdea
import com.bysel.trader.data.models.Quote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShoppingCart
import java.util.Locale
import com.bysel.trader.ui.components.DashboardSkeletonLoader
import com.bysel.trader.ui.components.InfoChip
import com.bysel.trader.ui.components.InvestorTipsCard
import com.bysel.trader.ui.components.NewsWidget
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.exclusiveHorizontalScroll
import com.bysel.trader.ui.components.TraceAwareErrorSnackbar
import com.bysel.trader.ui.components.WatchlistWidget
import com.bysel.trader.ui.components.localInvestorTips
import com.bysel.trader.data.WatchlistSymbols
import com.bysel.trader.data.models.InvestorTipsResponse
import com.bysel.trader.ui.format.formatInr
import com.bysel.trader.ui.format.formatInrCompact
import com.bysel.trader.ui.format.formatSignedPct
import com.bysel.trader.ui.format.formatVolumeCompact
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.ui.theme.byselSectionSurface
import com.bysel.trader.ui.viewmodel.DashboardViewModel
import com.bysel.trader.utils.MarketSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
private data class DashboardMetric(
    val title: String,
    val value: String,
    val caption: String,
    val accent: Color,
)

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val onClick: () -> Unit,
)

private enum class HomeLayoutVariant(val title: String, val subtitle: String) {
    FOCUS("Focus", "Action-first cockpit"),
    FLOW("Flow", "Narrative market feed"),
}

private data class HomeGuideStep(
    val title: String,
    val body: String,
    val actionLabel: String,
    val icon: ImageVector,
)

private val HomeGuideSteps = listOf(
    HomeGuideStep(
        title = "BYSEL Pulse",
        body = "This top card is your session brief — market mood, portfolio value, and fast actions. Refresh updates quotes and headlines.",
        actionLabel = "Next",
        icon = Icons.Filled.Info,
    ),
    HomeGuideStep(
        title = "Home Layout",
        body = "Focus puts actions first. Flow leads with narrative. Try switching now — you’ll see the Home Layout chips change.",
        actionLabel = "Switch layout",
        icon = Icons.Filled.ViewAgenda,
    ),
    HomeGuideStep(
        title = "Pin to Your Space",
        body = "Pin News and Market Watch so they stay in Your Space above the fold. We’ll pin them for you and scroll there.",
        actionLabel = "Pin widgets",
        icon = Icons.Filled.PushPin,
    ),
    HomeGuideStep(
        title = "Reorder widgets",
        body = "Use the ↑↓ arrows beside pinned widgets to change order. We’ll bump News upward so you can see the reorder live.",
        actionLabel = "Reorder News",
        icon = Icons.Filled.SwapVert,
    ),
    HomeGuideStep(
        title = "Live refresh",
        body = "Pull down anywhere on Home, or tap Refresh, to sync quotes and market news.",
        actionLabel = "Refresh now",
        icon = Icons.Filled.Refresh,
    ),
    HomeGuideStep(
        title = "You’re set",
        body = "Customize Home around your session: pin what matters, reorder Your Space, switch Focus/Flow, and jump into stocks or AI.",
        actionLabel = "Done",
        icon = Icons.Filled.CheckCircle,
    ),
)

@Composable
private fun HomeGuideDialog(
    step: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onTryAction: (Int) -> Unit,
    actionFeedback: String? = null,
) {
    val theme = LocalAppTheme.current
    val current = HomeGuideSteps[step.coerceIn(0, HomeGuideSteps.lastIndex)]
    val isLast = step >= HomeGuideSteps.lastIndex

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = byselCardColors(),
            elevation = byselCardElevation(),
            border = byselCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(current.icon, contentDescription = null, tint = theme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home Guide",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = current.title,
                            color = theme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${step + 1}/${HomeGuideSteps.size}",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                LinearProgressIndicator(
                    progress = { (step + 1f) / HomeGuideSteps.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.primary,
                    trackColor = theme.textSecondary.copy(alpha = 0.2f),
                )

                Text(
                    text = current.body,
                    color = theme.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                )

                if (!actionFeedback.isNullOrBlank()) {
                    Text(
                        text = actionFeedback,
                        color = theme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeGuideSteps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .weight(1f)
                                .background(
                                    color = if (index <= step) theme.primary else theme.textSecondary.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(2.dp),
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (step > 0) {
                        OutlinedButton(onClick = { onStepChange(step - 1) }) {
                            Text("Back")
                        }
                    }
                    Button(
                        onClick = {
                            if (isLast) onDismiss() else onTryAction(step)
                        },
                    ) {
                        Icon(
                            if (isLast) Icons.Filled.CheckCircle else Icons.Filled.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(current.actionLabel)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun DashboardScreen(
    holdings: List<Holding>,
    quotes: List<Quote>,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    error: String?,
    onRefresh: () -> Unit,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    onAiClick: (() -> Unit)? = null,
    marketStatus: MarketStatus? = null,
    onQuickTradeClick: ((String) -> Unit)? = null,
    onSignalLabClick: (() -> Unit)? = null,
    onScannerClick: (() -> Unit)? = null,
    onSmartMoneyClick: (() -> Unit)? = null,
    onPaperBuy: ((String, Int) -> Unit)? = null,
    onPracticeAlert: ((String, Double, String) -> Unit)? = null,
    lastExecutedOrder: OrderResponse? = null,
    onPracticeReviewSubmit: ((symbol: String, qty: Int, price: Double, note: String, setSl: Boolean, followedPlan: Boolean) -> Unit)? = null,
    walletBalance: Double = 0.0,
    onAddPracticeFunds: (() -> Unit)? = null,
    watchlistSymbols: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel()
    val pinnedStocks by dashboardViewModel.pinnedStocks.collectAsStateWithLifecycle()
    val portfolioPinned by dashboardViewModel.portfolioPinned.collectAsStateWithLifecycle()
    val newsPinned by dashboardViewModel.newsPinned.collectAsStateWithLifecycle()
    val widgetOrder by dashboardViewModel.widgetOrder.collectAsStateWithLifecycle()
    val watchlistPinned by dashboardViewModel.watchlistPinned.collectAsStateWithLifecycle()
    val marketNews by dashboardViewModel.marketNews.collectAsStateWithLifecycle()
    val newsSymbols by dashboardViewModel.newsSymbols.collectAsStateWithLifecycle()
    val newsLoading by dashboardViewModel.newsLoading.collectAsStateWithLifecycle()
    val newsError by dashboardViewModel.newsError.collectAsStateWithLifecycle()
    val marketGainers by dashboardViewModel.momentumLeaders.collectAsStateWithLifecycle()
    val marketLosers by dashboardViewModel.pressureZone.collectAsStateWithLifecycle()
    val moversUniverseSize by dashboardViewModel.moversUniverseSize.collectAsStateWithLifecycle()
    val moversLoading by dashboardViewModel.moversLoading.collectAsStateWithLifecycle()
    val practiceIdeas by dashboardViewModel.practiceIdeas.collectAsStateWithLifecycle()
    val practiceIdeasLoading by dashboardViewModel.practiceIdeasLoading.collectAsStateWithLifecycle()
    val practiceIdeasDisclaimer by dashboardViewModel.practiceIdeasDisclaimer.collectAsStateWithLifecycle()
    val intradayTips by dashboardViewModel.intradayTips.collectAsStateWithLifecycle()
    val intradayTipsLoading by dashboardViewModel.intradayTipsLoading.collectAsStateWithLifecycle()
    val investorTips by dashboardViewModel.investorTips.collectAsStateWithLifecycle()
    val investorTipsLoading by dashboardViewModel.investorTipsLoading.collectAsStateWithLifecycle()
    val investorTipTopic by dashboardViewModel.investorTipTopic.collectAsStateWithLifecycle()

    var showHomeGuide by rememberSaveable { mutableStateOf(false) }
    var homeGuideStep by rememberSaveable { mutableIntStateOf(0) }
    var habit by remember { mutableStateOf(PracticeHabitStore.load(context)) }
    var practiceProgress by remember { mutableStateOf(PracticeHabitStore.loadProgress(context)) }
    var pendingPracticeBuy by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var practiceReview by remember { mutableStateOf<PracticeReviewTarget?>(null) }

    val personalNewsSymbols = remember(watchlistSymbols, holdings) {
        buildPersonalNewsSymbols(watchlistSymbols, holdings.map { it.symbol })
    }

    LaunchedEffect(personalNewsSymbols) {
        dashboardViewModel.refreshMarketNews(personalNewsSymbols)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (MarketSession.isOpen()) {
                dashboardViewModel.refreshMarketMovers(showSpinner = false)
            }
        }
    }

    LaunchedEffect(practiceIdeas) {
        if (practiceIdeas.isNotEmpty()) {
            habit = PracticeHabitStore.markIdeaSeen(context)
        }
    }

    LaunchedEffect(lastExecutedOrder) {
        val order = lastExecutedOrder ?: return@LaunchedEffect
        val pending = pendingPracticeBuy ?: return@LaunchedEffect
        if (!order.order.symbol.equals(pending.first, ignoreCase = true)) return@LaunchedEffect
        habit = PracticeHabitStore.markTraded(context, order.order.symbol)
        val price = order.executedPrice
            ?: quotes.firstOrNull { it.symbol.equals(order.order.symbol, true) }?.last
            ?: 0.0
        practiceReview = PracticeReviewTarget(
            symbol = order.order.symbol.uppercase(Locale.US),
            qty = pending.second.coerceAtLeast(1),
            price = price,
        )
        pendingPracticeBuy = null
    }

    if (isLoading && quotes.isEmpty()) {
        DashboardSkeletonLoader(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
        )
    } else {
        DashboardContent(
            dashboardViewModel = dashboardViewModel,
            holdings = holdings,
            quotes = quotes,
            error = error,
            onTradeClick = onTradeClick,
            onErrorDismiss = onErrorDismiss,
            onRefresh = {
                onRefresh()
                dashboardViewModel.refreshMarketNews(personalNewsSymbols)
                dashboardViewModel.refreshMarketMovers(staggerMs = 400L)
                dashboardViewModel.refreshPracticeIdeas()
                val (up, down) = sessionBreadth(quotes)
                val share = if (up + down > 0) up.toDouble() / (up + down).toDouble() else null
                dashboardViewModel.refreshIntradayTips(advanceShare = share)
                dashboardViewModel.refreshInvestorTips()
                habit = PracticeHabitStore.load(context)
                practiceProgress = PracticeHabitStore.loadProgress(context)
            },
            watchlistSymbols = watchlistSymbols,
            intradayTips = intradayTips,
            intradayTipsLoading = intradayTipsLoading,
            investorTips = investorTips,
            investorTipsLoading = investorTipsLoading,
            investorTipTopic = investorTipTopic,
            showHomeGuide = showHomeGuide,
            homeGuideStep = homeGuideStep,
            onShowGuide = {
                homeGuideStep = 0
                showHomeGuide = true
            },
            onHomeGuideStepChange = { homeGuideStep = it },
            onDismissHomeGuide = {
                showHomeGuide = false
                homeGuideStep = 0
            },
            isRefreshing = isRefreshing,
            portfolioPinned = portfolioPinned,
            newsPinned = newsPinned,
            watchlistPinned = watchlistPinned,
            widgetOrder = widgetOrder,
            pinnedStocks = pinnedStocks,
            marketNews = marketNews,
            newsSymbols = newsSymbols,
            newsLoading = newsLoading,
            newsError = newsError,
            marketGainers = marketGainers,
            marketLosers = marketLosers,
            moversUniverseSize = moversUniverseSize,
            practiceIdeas = practiceIdeas,
            practiceIdeasLoading = practiceIdeasLoading,
            practiceIdeasDisclaimer = practiceIdeasDisclaimer,
            practiceHabit = habit,
            practiceProgress = practiceProgress,
            onAiClick = onAiClick,
            marketStatus = marketStatus,
            onQuickTradeClick = onQuickTradeClick,
            onSignalLabClick = onSignalLabClick,
            onScannerClick = onScannerClick,
            onSmartMoneyClick = onSmartMoneyClick,
            onPaperBuy = onPaperBuy?.let { buy ->
                { symbol, qty ->
                    if (walletBalance <= 0.0 && onAddPracticeFunds != null) {
                        onAddPracticeFunds()
                    } else {
                        pendingPracticeBuy = symbol to qty
                        buy(symbol, qty)
                    }
                }
            },
            onPracticeAlert = onPracticeAlert?.let { alert ->
                { symbol, price, type ->
                    habit = PracticeHabitStore.markAlertSet(context, symbol)
                    alert(symbol, price, type)
                }
            },
            onOpenPracticeReview = {
                val symbol = habit.tradedSymbol
                if (!symbol.isNullOrBlank()) {
                    val qty = 1
                    val price = quotes.firstOrNull { it.symbol.equals(symbol, true) }?.last ?: 0.0
                    practiceReview = PracticeReviewTarget(symbol, qty, price)
                }
            },
            walletBalance = walletBalance,
            onAddPracticeFunds = onAddPracticeFunds,
        )
    }

    practiceReview?.let { target ->
        PracticeReviewSheet(
            target = target,
            onDismiss = { practiceReview = null },
            onSubmit = { note, setSl, followedPlan ->
                habit = PracticeHabitStore.markReviewed(context, setSl, followedPlan)
                practiceProgress = PracticeHabitStore.loadProgress(context)
                onPracticeReviewSubmit?.invoke(
                    target.symbol,
                    target.qty,
                    target.price,
                    note,
                    setSl,
                    followedPlan,
                )
                practiceReview = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardContent(
    dashboardViewModel: DashboardViewModel,
    holdings: List<Holding>,
    quotes: List<Quote>,
    error: String?,
    onTradeClick: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onShowGuide: () -> Unit,
    showHomeGuide: Boolean = false,
    homeGuideStep: Int = 0,
    onHomeGuideStepChange: (Int) -> Unit = {},
    onDismissHomeGuide: () -> Unit = {},
    isRefreshing: Boolean,
    portfolioPinned: Boolean,
    newsPinned: Boolean,
    watchlistPinned: Boolean,
    widgetOrder: List<String>,
    pinnedStocks: Set<String>,
    marketNews: List<MarketNewsHeadline>,
    newsSymbols: List<String>,
    newsLoading: Boolean,
    newsError: String?,
    marketGainers: List<MarketMoverQuote> = emptyList(),
    marketLosers: List<MarketMoverQuote> = emptyList(),
    moversUniverseSize: Int = 0,
    practiceIdeas: List<PracticeIdea> = emptyList(),
    practiceIdeasLoading: Boolean = false,
    practiceIdeasDisclaimer: String = "",
    onAiClick: (() -> Unit)? = null,
    marketStatus: MarketStatus? = null,
    onQuickTradeClick: ((String) -> Unit)? = null,
    onSignalLabClick: (() -> Unit)? = null,
    onScannerClick: (() -> Unit)? = null,
    onSmartMoneyClick: (() -> Unit)? = null,
    onPaperBuy: ((String, Int) -> Unit)? = null,
    onPracticeAlert: ((String, Double, String) -> Unit)? = null,
    practiceHabit: PracticeHabitStore.DayState = PracticeHabitStore.DayState(dateKey = ""),
    practiceProgress: PracticeHabitStore.Progress = PracticeHabitStore.Progress(),
    onOpenPracticeReview: (() -> Unit)? = null,
    walletBalance: Double = 0.0,
    onAddPracticeFunds: (() -> Unit)? = null,
    watchlistSymbols: List<String> = emptyList(),
    intradayTips: IntradayTipsResponse? = null,
    intradayTipsLoading: Boolean = false,
    investorTips: InvestorTipsResponse = localInvestorTips("long_term"),
    investorTipsLoading: Boolean = false,
    investorTipTopic: String = "long_term",
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val layoutRequester = remember { BringIntoViewRequester() }
    val yourSpaceRequester = remember { BringIntoViewRequester() }
    val portfolioWidgetRequester = remember { BringIntoViewRequester() }
    val newsRequester = remember { BringIntoViewRequester() }
    var guideFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var scrollPortfolioIntoView by remember { mutableStateOf(false) }

    LaunchedEffect(portfolioPinned, scrollPortfolioIntoView) {
        if (portfolioPinned && scrollPortfolioIntoView) {
            delay(100)
            yourSpaceRequester.bringIntoView()
            delay(60)
            portfolioWidgetRequester.bringIntoView()
            guideFeedback = "Portfolio card added under Your Space — use ↑↓ to reorder."
            scrollPortfolioIntoView = false
        }
    }
    var layoutVariant by rememberSaveable { mutableStateOf(HomeLayoutVariant.FOCUS.name) }
    val selectedVariant = remember(layoutVariant) {
        runCatching { HomeLayoutVariant.valueOf(layoutVariant) }
            .getOrDefault(HomeLayoutVariant.FOCUS)
    }

    LaunchedEffect(showHomeGuide) {
        if (showHomeGuide && homeGuideStep == 0) {
            guideFeedback = null
        }
    }
    val pinnedList = remember(quotes, pinnedStocks) {
        quotes.filter { pinnedStocks.contains(it.symbol) }
    }
    val localGainers = remember(quotes, pinnedStocks) {
        quotes
            .filter { it.symbol.uppercase() !in HOME_INDEX_SYMBOLS }
            .filter { it.pctChange > 0.0 && !pinnedStocks.contains(it.symbol) }
            .sortedByDescending { it.pctChange }
            .take(8)
    }
    val localLosers = remember(quotes, pinnedStocks) {
        quotes
            .filter { it.symbol.uppercase() !in HOME_INDEX_SYMBOLS }
            .filter { it.pctChange < 0.0 && !pinnedStocks.contains(it.symbol) }
            .sortedBy { it.pctChange }
            .take(8)
    }
    val topGainers = remember(marketGainers, localGainers) {
        val fromMarket = marketGainers
            .filter { it.pctChange > 0.0 }
            .map {
                Quote(
                    symbol = it.symbol,
                    last = it.last,
                    pctChange = it.pctChange,
                    volume = it.volume,
                )
            }
        fromMarket.ifEmpty { localGainers }
    }
    val topLosers = remember(marketLosers, localLosers) {
        val fromMarket = marketLosers
            .filter { it.pctChange < 0.0 }
            .map {
                Quote(
                    symbol = it.symbol,
                    last = it.last,
                    pctChange = it.pctChange,
                    volume = it.volume,
                )
            }
        fromMarket.ifEmpty { localLosers }
    }
    val moversAreMarketWide = marketGainers.any { it.pctChange > 0.0 } ||
        marketLosers.any { it.pctChange < 0.0 }
    val totalValue = remember(holdings, quotes) {
        holdings.sumOf { it.qty * liveHoldingPrice(it, quotes) }
    }
    val totalInvested = remember(holdings) { holdings.sumOf { it.qty * it.avgPrice } }
    val totalPnL = remember(totalValue, totalInvested) { totalValue - totalInvested }
    val totalPnLPercent = remember(totalValue, totalInvested) {
        if (totalInvested > 0.0) (totalPnL / totalInvested) * 100.0 else 0.0
    }
    val (positiveCount, negativeCount) = remember(quotes) { sessionBreadth(quotes) }
    val averageMove = remember(quotes) {
        val tape = quotes.filter { it.symbol.uppercase() !in HOME_INDEX_SYMBOLS }
        if (tape.isEmpty()) 0.0 else tape.map { it.pctChange }.average()
    }
    val marketMoodTitle = remember(positiveCount, negativeCount, averageMove) {
        when {
            averageMove >= 0.75 && positiveCount >= negativeCount -> "Risk-On Session"
            averageMove <= -0.75 && negativeCount > positiveCount -> "Defensive Tape"
            positiveCount >= negativeCount * 2 && positiveCount > 0 -> "Breadth Expansion"
            negativeCount >= positiveCount * 2 && negativeCount > 0 -> "Pressure Building"
            else -> "Selective Opportunity"
        }
    }
    val marketMoodDetail = remember(positiveCount, negativeCount, averageMove, marketNews) {
        when {
            marketNews.isEmpty() && positiveCount == 0 && negativeCount == 0 -> "Waiting for the first full market snapshot."
            averageMove >= 0.75 -> "Momentum is broad enough to support fresh entries, but still reward quality names."
            averageMove <= -0.75 -> "Capital protection matters more right now. Use Home to narrow into resilient names first."
            else -> "The market is mixed. Use news flow and strong relative movers to find cleaner setups."
        }
    }
    val focusQuotes = remember(pinnedList, topGainers, topLosers) {
        (pinnedList + topGainers + topLosers)
            .distinctBy { it.symbol }
            .take(6)
    }
    val watchlistQuotes = remember(quotes, watchlistSymbols) {
        val order = watchlistSymbols.map { WatchlistSymbols.normalize(it) }.filter { it.isNotBlank() }.distinct()
        order.mapNotNull { sym -> WatchlistSymbols.findQuote(quotes, sym) }
    }
    val newsRefreshSymbols = remember(watchlistSymbols, holdings) {
        buildPersonalNewsSymbols(watchlistSymbols, holdings.map { it.symbol })
    }
    val signalBuckets = remember(quotes) { buildSignalLabBuckets(quotes).take(3) }
    val dashboardMetrics = remember(totalValue, holdings, positiveCount, negativeCount, focusQuotes, marketNews, theme) {
        listOf(
            DashboardMetric(
                title = "Portfolio",
                value = if (holdings.isEmpty()) "No holdings" else formatCompactCurrency(totalValue),
                caption = if (holdings.isEmpty()) "Build your first tracked book" else "${holdings.size} active holding${if (holdings.size == 1) "" else "s"}",
                accent = theme.primary,
            ),
            DashboardMetric(
                title = "Breadth",
                value = "$positiveCount up / $negativeCount down",
                caption = "Tracked names on Home (not full NSE)",
                accent = if (positiveCount >= negativeCount) theme.positive else theme.negative,
            ),
            DashboardMetric(
                title = "Top Swing",
                value = focusQuotes.firstOrNull()?.symbol ?: "Syncing",
                caption = focusQuotes.firstOrNull()?.let { formatSignedPercent(it.pctChange) } ?: "Waiting for quotes",
                accent = focusQuotes.firstOrNull()?.let { if (it.pctChange >= 0) theme.positive else theme.negative } ?: theme.textSecondary,
            ),
            DashboardMetric(
                title = "News Flow",
                value = "${marketNews.size}",
                caption = if (marketNews.isEmpty()) "No live headlines yet" else "Tracked storylines in motion",
                accent = theme.primary,
            ),
        )
    }
    val homeActions = remember(focusQuotes, onAiClick, onRefresh, onShowGuide, theme) {
        listOfNotNull(
            HomeAction(
                title = "Fast Refresh",
                subtitle = "Sync quotes + news",
                colors = listOf(theme.primary.copy(alpha = 0.3f), theme.card),
                onClick = {
                    onRefresh()
                    dashboardViewModel.refreshMarketNews(newsRefreshSymbols)
                    dashboardViewModel.refreshMarketMovers(staggerMs = 400L)
                },
            ),
            HomeAction(
                title = "Market Leader",
                subtitle = focusQuotes.firstOrNull()?.let { "Open ${it.symbol}" } ?: "Waiting for setup",
                colors = listOf(theme.positive.copy(alpha = 0.25f), theme.card),
                onClick = { focusQuotes.firstOrNull()?.let { onTradeClick(it.symbol) } },
            ),
            HomeAction(
                title = "Home Guide",
                subtitle = "Pin + reorder widgets",
                colors = listOf(theme.textSecondary.copy(alpha = 0.2f), theme.card),
                onClick = onShowGuide,
            ),
            onAiClick?.let {
                HomeAction(
                    title = "Ask AI",
                    subtitle = "Chat about the tape",
                    colors = listOf(theme.primary.copy(alpha = 0.2f), theme.surface),
                    onClick = it,
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                onRefresh()
                dashboardViewModel.refreshMarketNews(newsRefreshSymbols)
                dashboardViewModel.refreshMarketMovers(staggerMs = 400L)
            },
            enabled = true
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
                .padding(16.dp)
        ) {
        item {
            DashboardHeroCard(
                marketMoodTitle = marketMoodTitle,
                marketMoodDetail = marketMoodDetail,
                totalValue = totalValue,
                totalPnL = totalPnL,
                totalPnLPercent = totalPnLPercent,
                holdingsCount = holdings.size,
                leadQuote = focusQuotes.firstOrNull(),
                headlineCount = marketNews.size,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                portfolioPinned = portfolioPinned,
                onRefresh = {
                    onRefresh()
                    dashboardViewModel.refreshMarketNews(newsRefreshSymbols)
                    dashboardViewModel.refreshMarketMovers(staggerMs = 400L)
                },
                onShowGuide = onShowGuide,
                onTogglePortfolioPin = {
                    val willPin = !portfolioPinned
                    dashboardViewModel.togglePortfolioPin()
                    if (willPin) {
                        scrollPortfolioIntoView = true
                    } else {
                        guideFeedback = "Portfolio card removed from Your Space."
                    }
                },
                onResetLayout = { dashboardViewModel.resetDashboardLayout() },
                onOpenLead = { focusQuotes.firstOrNull()?.let { onTradeClick(it.symbol) } },
            )
        }

        item {
            MarketPulseHero(
                quotes = quotes,
                marketStatus = marketStatus,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                moodTitle = marketMoodTitle,
            )
        }

        item {
            IdeasRail(
                topMover = focusQuotes.firstOrNull(),
                signalTitle = signalBuckets.firstOrNull()?.title,
                newsCount = marketNews.size,
                onAiBrief = onAiClick,
                onSignalLab = onSignalLabClick,
                onScanner = onScannerClick,
                onSmartMoney = onSmartMoneyClick,
                onOpenMover = focusQuotes.firstOrNull()?.let { q -> { onTradeClick(q.symbol) } },
            )
        }

        if (error != null) {
            item {
                TraceAwareErrorSnackbar(
                    error = error,
                    onDismiss = onErrorDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }

                when (selectedVariant) {
                    HomeLayoutVariant.FOCUS -> {
                        item {
                            DashboardMetricsRow(metrics = dashboardMetrics)
                        }

                        if (homeActions.isNotEmpty()) {
                            item {
                                HomeActionRail(actions = homeActions)
                            }
                        }

                        item {
                            AiDailyBriefCard(
                                holdings = holdings,
                                positiveCount = positiveCount,
                                negativeCount = negativeCount,
                                averageMove = averageMove,
                                newsCount = marketNews.size,
                                topMover = focusQuotes.firstOrNull(),
                                onAskAi = onAiClick
                            )
                        }

                        if (focusQuotes.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Quick Board",
                                    subtitle = "Tap straight into strongest movers and fast contexts.",
                                )
                            }
                            item {
                                LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(focusQuotes.take(4), key = { it.symbol }) { quote ->
                                        HomeQuoteBoardCard(
                                            quote = quote,
                                            onOpen = { onTradeClick(quote.symbol) },
                                            isPinned = pinnedStocks.contains(quote.symbol),
                                            onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                                        )
                                    }
                                }
                            }
                        }

                        if (signalBuckets.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Signal Playbooks",
                                    subtitle = "Breakout, volume and dividend setups, ranked for immediate action.",
                                )
                            }
                            item {
                                LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(signalBuckets, key = { it.title }) { bucket ->
                                        HomeSignalCard(
                                            bucket = bucket,
                                            onOpen = { bucket.quotes.firstOrNull()?.let { quote -> onTradeClick(quote.symbol) } },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HomeLayoutVariant.FLOW -> {
                        if (homeActions.isNotEmpty()) {
                            item {
                                HomeActionRail(actions = homeActions)
                            }
                        }

                        item {
                            AiDailyBriefCard(
                                holdings = holdings,
                                positiveCount = positiveCount,
                                negativeCount = negativeCount,
                                averageMove = averageMove,
                                newsCount = marketNews.size,
                                topMover = focusQuotes.firstOrNull(),
                                onAskAi = onAiClick
                            )
                        }

                        item {
                            DashboardMetricsRow(metrics = dashboardMetrics)
                        }

                        if (signalBuckets.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Signal Playbooks",
                                    subtitle = "Start from thesis-first buckets, then drill into symbols.",
                                )
                            }
                            item {
                                LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(signalBuckets, key = { it.title }) { bucket ->
                                        HomeSignalCard(
                                            bucket = bucket,
                                            onOpen = { bucket.quotes.firstOrNull()?.let { quote -> onTradeClick(quote.symbol) } },
                                        )
                                    }
                                }
                            }
                        }

                        if (focusQuotes.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Quick Board",
                                    subtitle = "Context cards for the most relevant symbols this session.",
                                )
                            }
                            item {
                                LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(focusQuotes.take(4), key = { it.symbol }) { quote ->
                                        HomeQuoteBoardCard(
                                            quote = quote,
                                            onOpen = { onTradeClick(quote.symbol) },
                                            isPinned = pinnedStocks.contains(quote.symbol),
                                            onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

        item {
            SectionHeader(
                title = "Your Space",
                subtitle = "Watchlist and news — pin and reorder what you want on the tape.",
                modifier = Modifier.bringIntoViewRequester(yourSpaceRequester),
            )
        }

        widgetOrder.forEachIndexed { idx, widget ->
                when (widget) {
                    "portfolio" -> if (portfolioPinned) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .bringIntoViewRequester(portfolioWidgetRequester),
                colors = byselCardColors(),
                elevation = byselCardElevation(),
                                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, LocalAppTheme.current.primary)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Pinned · Portfolio",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LocalAppTheme.current.primary,
                                        )
                                        Row {
                                            IconButton(onClick = { dashboardViewModel.togglePortfolioPin() }) {
                                                Icon(
                                                    Icons.Default.PushPin,
                                                    contentDescription = "Unpin Portfolio",
                                                    tint = LocalAppTheme.current.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { dashboardViewModel.moveWidgetUp("portfolio") },
                                                enabled = idx > 0,
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowUpward,
                                                    contentDescription = "Move Up",
                                                    tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary,
                                                )
                                            }
                                            IconButton(
                                                onClick = { dashboardViewModel.moveWidgetDown("portfolio") },
                                                enabled = idx < widgetOrder.size - 1,
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowDownward,
                                                    contentDescription = "Move Down",
                                                    tint = if (idx < widgetOrder.size - 1) {
                                                        LocalAppTheme.current.primary
                                                    } else {
                                                        LocalAppTheme.current.textSecondary
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    PortfolioSummaryCard(holdings, quotes)
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                "news" -> if (newsPinned) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .bringIntoViewRequester(newsRequester),
                            colors = byselCardColors(),
                            elevation = byselCardElevation(),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else byselCardBorder()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                NewsWidget(
                                    isPinned = true,
                                    headlines = marketNews,
                                    trackedSymbols = newsSymbols,
                                    isLoading = newsLoading,
                                    error = newsError,
                                    onPinClick = { dashboardViewModel.toggleNewsPin() },
                                    onRefresh = { dashboardViewModel.refreshMarketNews(newsRefreshSymbols) },
                                )
                                Column {
                                    IconButton(onClick = { dashboardViewModel.moveWidgetUp("news") }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                    IconButton(onClick = { dashboardViewModel.moveWidgetDown("news") }, enabled = idx < widgetOrder.size - 1) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < widgetOrder.size - 1) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                "watchlist" -> if (watchlistPinned) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = byselCardColors(),
                            elevation = byselCardElevation(),
                            shape = RoundedCornerShape(16.dp),
                            border = if (idx == 0) BorderStroke(2.dp, LocalAppTheme.current.primary) else byselCardBorder()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                WatchlistWidget(
                                    isPinned = true,
                                    quotes = watchlistQuotes,
                                    trackedCount = watchlistSymbols.size,
                                    onPinClick = { dashboardViewModel.toggleWatchlistPin() },
                                    onQuoteClick = { onTradeClick(it.symbol) },
                                    onTradeClick = onQuickTradeClick?.let { handler -> { handler(it.symbol) } },
                                )
                                Column {
                                    IconButton(onClick = { dashboardViewModel.moveWidgetUp("watchlist") }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                    IconButton(onClick = { dashboardViewModel.moveWidgetDown("watchlist") }, enabled = idx < widgetOrder.size - 1) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < widgetOrder.size - 1) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        if (!newsPinned) {
            item {
                Box(modifier = Modifier.bringIntoViewRequester(newsRequester)) {
                NewsWidget(
                    isPinned = false,
                    headlines = marketNews,
                    trackedSymbols = newsSymbols,
                    isLoading = newsLoading,
                    error = newsError,
                    onPinClick = { dashboardViewModel.toggleNewsPin() },
                        onRefresh = { dashboardViewModel.refreshMarketNews(newsRefreshSymbols) },
                )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (!watchlistPinned) {
            item {
                WatchlistWidget(
                    isPinned = false,
                    quotes = watchlistQuotes,
                    trackedCount = watchlistSymbols.size,
                    onPinClick = { dashboardViewModel.toggleWatchlistPin() },
                    onQuoteClick = { onTradeClick(it.symbol) },
                    onTradeClick = onQuickTradeClick?.let { handler -> { handler(it.symbol) } },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (pinnedStocks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Pinned Conviction",
                    subtitle = "The names you explicitly want to keep on the radar while the market rotates.",
                )
            }
            items(items = pinnedList, key = { "pinned_${it.symbol}" }) { quote ->
                GainerLosersCard(
                    quote,
                    isGainer = quote.pctChange >= 0,
                    isPinned = true,
                    onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                    onClick = { onTradeClick(quote.symbol) }
                )
            }
        }

        item {
            SectionHeader(
                title = "Momentum Leaders",
                subtitle = if (moversAreMarketWide && moversUniverseSize > 0) {
                    "Top gainers across $moversUniverseSize liquid NSE names today."
                } else {
                    "Strong relative performers from your current Home quote set (market feed loading…)."
                },
            )
        }
        items(items = topGainers, key = { "gainer_${it.symbol}" }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = true,
                isPinned = pinnedStocks.contains(quote.symbol),
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }
        if (topGainers.isEmpty()) {
            item {
                Text(
                    text = "No names are up in the current snapshot.",
                    fontSize = 13.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        item {
            SectionHeader(
                title = "Pressure Zone",
                subtitle = if (moversAreMarketWide && moversUniverseSize > 0) {
                    "Top losers across $moversUniverseSize liquid NSE names today."
                } else {
                    "Names under the heaviest selling pressure in your current Home quote set."
                },
            )
        }

        items(items = topLosers, key = { "loser_${it.symbol}" }) { quote ->
            GainerLosersCard(
                quote,
                isGainer = false,
                isPinned = pinnedStocks.contains(quote.symbol),
                onPinClick = { dashboardViewModel.togglePin(quote.symbol) },
                onClick = { onTradeClick(quote.symbol) }
            )
        }
        if (topLosers.isEmpty()) {
            item {
                Text(
                    text = "No names are down in the current snapshot.",
                    fontSize = 13.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        item {
            HomeVariantSwitcher(
                selectedVariant = selectedVariant,
                onVariantSelected = { layoutVariant = it.name },
                modifier = Modifier.bringIntoViewRequester(layoutRequester),
            )
        }

        item {
            PaperWalletHomeStrip(
                balance = walletBalance,
                onAddFunds = onAddPracticeFunds,
            )
        }

        item {
            TodaysPracticeStrip(
                habit = practiceHabit,
                progress = practiceProgress,
                onReviewClick = onOpenPracticeReview,
            )
        }

        item {
            PracticeIdeasSection(
                ideas = practiceIdeas.ifEmpty {
                    buildLocalPracticeIdeas(topGainers + topLosers)
                },
                loading = practiceIdeasLoading && practiceIdeas.isEmpty(),
                disclaimer = practiceIdeasDisclaimer.ifBlank {
                    "Educational paper drills only — not investment advice."
                },
                onOpenSymbol = onTradeClick,
                onPaperBuy = onPaperBuy,
                onPracticeAlert = onPracticeAlert,
                needsPracticeCredit = walletBalance <= 0.0,
                onAddPracticeFunds = onAddPracticeFunds,
            )
        }

        item {
            val localSession = LocalHabitInsights.sessionHabits(
                habit = practiceHabit,
                progress = practiceProgress,
                holdings = holdings,
                watchlistSize = watchlistSymbols.size,
            )
            val tipsPayload = LocalHabitInsights.mergeSession(
                remote = intradayTips,
                local = localSession,
                fallback = buildLocalIntradayTips(marketStatus),
                limit = 4,
            )
            IntradayTipsSection(
                phaseLabel = tipsPayload.phaseLabel,
                tips = tipsPayload.tips,
                disclaimer = tipsPayload.disclaimer.ifBlank {
                    "Educational session habits — not stock tips."
                },
                loading = intradayTipsLoading && tipsPayload.tips.isEmpty(),
                mood = tipsPayload.mood,
                paperNote = tipsPayload.paperNote,
                sampleSize = tipsPayload.sampleSize,
            )
        }

        item {
            val remoteInvestor = if (investorTips.tips.isEmpty()) {
                localInvestorTips(investorTipTopic)
            } else {
                investorTips
            }
            val mergedInvestor = LocalHabitInsights.mergeInvestor(
                remote = remoteInvestor,
                local = LocalHabitInsights.investorHabits(
                    habit = practiceHabit,
                    progress = practiceProgress,
                    holdings = holdings,
                    watchlistSize = watchlistSymbols.size,
                    topic = investorTipTopic,
                ),
                limit = 4,
            )
            InvestorTipsCard(
                title = "Investor habits",
                topicLabel = mergedInvestor.topicLabel,
                tips = mergedInvestor.tips,
                disclaimer = mergedInvestor.disclaimer.ifBlank {
                    "Educational investor habits — not stock, fund, or IPO recommendations."
                },
                loading = investorTipsLoading && mergedInvestor.tips.isEmpty(),
                topics = mergedInvestor.topics.ifEmpty { localInvestorTips("long_term").topics },
                selectedTopic = investorTipTopic,
                onTopicSelected = { dashboardViewModel.selectInvestorTipTopic(it) },
                paperNote = mergedInvestor.paperNote,
                sampleSize = mergedInvestor.sampleSize,
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    }
    }

    if (showHomeGuide) {
        HomeGuideDialog(
            step = homeGuideStep.coerceIn(0, HomeGuideSteps.lastIndex),
            onStepChange = {
                guideFeedback = null
                onHomeGuideStepChange(it)
            },
            onDismiss = {
                guideFeedback = null
                onDismissHomeGuide()
            },
            actionFeedback = guideFeedback,
            onTryAction = { step ->
                scope.launch {
                    when (step) {
                        0 -> {
                            guideFeedback = null
                            onHomeGuideStepChange(1)
                        }
                        1 -> {
                            layoutVariant = if (selectedVariant == HomeLayoutVariant.FOCUS) {
                                HomeLayoutVariant.FLOW.name
                            } else {
                                HomeLayoutVariant.FOCUS.name
                            }
                            delay(80)
                            layoutRequester.bringIntoView()
                            guideFeedback = "Layout switched — Focus ↔ Flow chips updated above."
                            onHomeGuideStepChange(2)
                        }
                        2 -> {
                            if (!newsPinned) {
                                dashboardViewModel.toggleNewsPin()
                                delay(120)
                            }
                            if (!watchlistPinned) {
                                dashboardViewModel.toggleWatchlistPin()
                                delay(120)
                            }
                            yourSpaceRequester.bringIntoView()
                            delay(80)
                            newsRequester.bringIntoView()
                            guideFeedback = "Pinned News & Market Watch into Your Space."
                            onHomeGuideStepChange(3)
                        }
                        3 -> {
                            if (!newsPinned) {
                                dashboardViewModel.toggleNewsPin()
                                delay(120)
                            }
                            repeat(2) {
                                dashboardViewModel.moveWidgetUp("news")
                                delay(60)
                            }
                            yourSpaceRequester.bringIntoView()
                            delay(60)
                            newsRequester.bringIntoView()
                            guideFeedback = "Moved News up in Your Space — use ↑↓ anytime."
                            onHomeGuideStepChange(4)
                        }
                        4 -> {
                            onRefresh()
                            dashboardViewModel.refreshMarketNews(newsRefreshSymbols)
                            guideFeedback = "Quotes and news refresh started."
                            onHomeGuideStepChange(5)
                        }
                        else -> {
                            guideFeedback = null
                            onDismissHomeGuide()
                        }
                    }
                }
            },
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardHeroCard(
    marketMoodTitle: String,
    marketMoodDetail: String,
    totalValue: Double,
    totalPnL: Double,
    totalPnLPercent: Double,
    holdingsCount: Int,
    leadQuote: Quote?,
    headlineCount: Int,
    positiveCount: Int,
    negativeCount: Int,
    portfolioPinned: Boolean,
    onRefresh: () -> Unit,
    onShowGuide: () -> Unit,
    onTogglePortfolioPin: () -> Unit,
    onResetLayout: () -> Unit,
    onOpenLead: () -> Unit,
) {
    val theme = LocalAppTheme.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = byselCardElevation(),
                border = byselCardBorder(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.card,
                            theme.primary.copy(alpha = 0.28f),
                            theme.surface,
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BYSEL Pulse",
                        style = MaterialTheme.typography.headlineLarge,
                        color = theme.text,
                    )
                    Text(
                        text = "Paper Practice · Simulated money",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.primary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                    Text(
                        text = marketMoodTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = marketMoodDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        maxLines = 3,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilterChip(
                    selected = portfolioPinned,
                    onClick = onTogglePortfolioPin,
                    label = {
                        Text(
                            text = if (portfolioPinned) "Pinned" else "Pin",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }

            Text(
                text = if (portfolioPinned) {
                    "Portfolio card is under Your Space below — scroll or wait; use ↑↓ to reorder."
                } else {
                    "Pin adds a Portfolio card under Your Space (below). Hero totals always stay here."
                },
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (holdingsCount > 0) {
                Text(
                    text = formatCompactCurrency(totalValue),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatCompactCurrency(totalPnL)} • ${formatSignedPercent(totalPnLPercent)} since entry across $holdingsCount holding${if (holdingsCount == 1) "" else "s"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (totalPnL >= 0) theme.positive else theme.negative,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = "No holdings yet",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = "Use Home to move from market signal to stock context quickly, then build positions with conviction.",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", maxLines = 1)
                }
                OutlinedButton(onClick = onShowGuide) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guide", maxLines = 1)
                }
                if (leadQuote != null) {
                    Button(onClick = onOpenLead) {
                        Text(
                            text = "Open ${leadQuote.symbol}",
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoChip(label = { Text("$headlineCount headlines") })
                InfoChip(label = { Text("$positiveCount up / $negativeCount down") })
                if (leadQuote != null) {
                    AssistChip(
                        onClick = onOpenLead,
                        label = {
                            Text(
                                text = "${leadQuote.symbol} ${formatSignedPercent(leadQuote.pctChange)}",
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            TextButton(onClick = onResetLayout) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset dashboard layout")
            }
        }
    }
}

@Composable
private fun DashboardMetricsRow(metrics: List<DashboardMetric>) {
    LazyRow(
        modifier = Modifier
            .padding(top = 16.dp)
            .exclusiveHorizontalScroll(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(metrics, key = { it.title }) { metric ->
            Card(
                modifier = Modifier
                    .width(200.dp)
                    .heightIn(min = 112.dp),
                colors = byselCardColors(),
                elevation = byselCardElevation(),
                border = byselCardBorder(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        metric.title,
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metric.value,
                        color = metric.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metric.caption,
                        color = LocalAppTheme.current.text,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeActionRail(actions: List<HomeAction>) {
    LazyRow(
        modifier = Modifier
            .padding(top = 12.dp)
            .exclusiveHorizontalScroll(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(actions, key = { it.title }) { action ->
            Card(
                modifier = Modifier
                    .width(220.dp)
                    .clickable { action.onClick() },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(action.colors))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = action.title,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = action.subtitle,
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeVariantSwitcher(
    selectedVariant: HomeLayoutVariant,
    onVariantSelected: (HomeLayoutVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Home Layout",
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HomeLayoutVariant.entries, key = { it.name }) { variant ->
                    AssistChip(
                        onClick = { onVariantSelected(variant) },
                        label = {
                            Text(
                                text = if (selectedVariant == variant) {
                                    "● ${variant.title}: ${variant.subtitle}"
                                } else {
                                    "${variant.title}: ${variant.subtitle}"
                                },
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 22.dp, bottom = 10.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
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
private fun HomeQuoteBoardCard(
    quote: Quote,
    onOpen: () -> Unit,
    isPinned: Boolean,
    onPinClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .padding(vertical = 4.dp)
            .clickable { onOpen() },
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.symbol,
                        color = LocalAppTheme.current.text,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "₹${String.format("%.2f", quote.last)}",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPinClick) {
                    Icon(
                        imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary,
                    )
                }
            }

            Text(
                text = formatSignedPercent(quote.pctChange),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (quote.pctChange >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
            )
            Text(
                text = if (quote.effectiveVolume() > 0L) {
                    "Volume ${formatCompactVolume(quote.effectiveVolume())}"
                } else {
                    "Open stock context and decide fast"
                },
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Text("Open stock context")
            }
        }
    }
}

@Composable
private fun HomeSignalCard(
    bucket: SignalLabBucket,
    onOpen: () -> Unit,
) {
    val leadQuote = bucket.quotes.firstOrNull()

    Card(
        modifier = Modifier
            .width(240.dp)
            .padding(vertical = 4.dp)
            .clickable(enabled = leadQuote != null, onClick = onOpen),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bucket.title,
                color = LocalAppTheme.current.text,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${bucket.quotes.size} live setup${if (bucket.quotes.size == 1) "" else "s"}",
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = signalLabLeadSummary(bucket),
                color = LocalAppTheme.current.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bucket.quotes.take(3).joinToString(" • ") { it.symbol },
                color = LocalAppTheme.current.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onOpen,
                enabled = leadQuote != null,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (leadQuote != null) "Open ${leadQuote.symbol}" else "No live setup")
            }
        }
    }
}

@Composable
private fun MarketPulseHero(
    quotes: List<Quote>,
    marketStatus: MarketStatus?,
    positiveCount: Int,
    negativeCount: Int,
    moodTitle: String,
) {
    val theme = LocalAppTheme.current
    val indices = remember(quotes) {
        listOf("NIFTY50", "SENSEX", "BANKNIFTY").mapNotNull { symbol ->
            quotes.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
        }
    }
    val sessionOpen = marketStatus?.isOpen ?: MarketSession.isOpen()
    val sessionLabel = marketStatus?.message
        ?: if (sessionOpen) "NSE session live" else "Market closed · last session levels"
    val breadthTotal = (positiveCount + negativeCount).coerceAtLeast(1)
    val advanceShare = positiveCount.toFloat() / breadthTotal.toFloat()

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(420)) +
            slideInVertically(initialOffsetY = { it / 8 }),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.primary.copy(alpha = 0.16f),
                            theme.card,
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BYSEL Market Pulse",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = moodTitle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = theme.textSecondary,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (sessionOpen) theme.positive.copy(alpha = 0.16f)
                            else theme.negative.copy(alpha = 0.14f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = if (sessionOpen) theme.positive else theme.negative,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (sessionOpen) "Open" else "Closed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sessionOpen) theme.positive else theme.negative,
                    )
                }
            }

            Text(
                text = sessionLabel,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = theme.textSecondary,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )

            if (indices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    indices.take(3).forEach { quote ->
                        val accent = if (quote.pctChange >= 0) theme.positive else theme.negative
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 78.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.surface.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = when (quote.symbol.uppercase()) {
                                    "NIFTY50" -> "NIFTY 50"
                                    "BANKNIFTY" -> "BANK NIFTY"
                                    else -> quote.symbol.uppercase()
                                },
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = theme.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatInr(quote.last, decimals = 2),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.text,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatSignedPercent(quote.pctChange),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = accent,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "Session breadth",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = theme.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        softWrap = true,
                    )
                    Text(
                        text = "$positiveCount up · $negativeCount down",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.text,
                        maxLines = 2,
                        softWrap = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(theme.negative.copy(alpha = 0.35f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(advanceShare.coerceIn(0.05f, 1f))
                            .background(theme.positive.copy(alpha = 0.85f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntradayTipsSection(
    phaseLabel: String,
    tips: List<IntradayTip>,
    disclaimer: String,
    loading: Boolean,
    mood: String? = null,
    paperNote: String = "",
    sampleSize: Int = 0,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .byselSectionSurface(RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Session habits",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(
                    text = buildString {
                        append(phaseLabel.ifBlank { "Session" })
                        if (!mood.isNullOrBlank()) append(" · ${mood.replaceFirstChar { it.uppercase() }} tape")
                    },
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (tips.any { it.source.equals("paper", true) }) "Paper book" else "Habits",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = theme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (loading && tips.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = theme.primary,
            )
        } else {
            tips.take(4).forEach { tip ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surface.copy(alpha = 0.55f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tip.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tip.body,
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        lineHeight = 15.sp,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildString {
                        if (tip.source.equals("paper", true)) append("From your paper trades")
                        else append("Session cue")
                        if (!tip.evidence.isNullOrBlank()) append(" · ${tip.evidence}")
                    }
                    Text(
                        text = meta,
                        fontSize = 10.sp,
                        color = theme.primary.copy(alpha = 0.85f),
                        lineHeight = 13.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val note = when {
            paperNote.isNotBlank() -> paperNote
            sampleSize > 0 -> "Based on $sampleSize paper fills (IST windows)."
            else -> ""
        }
        if (note.isNotBlank()) {
            Text(
                text = note,
                fontSize = 10.sp,
                color = theme.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = disclaimer,
            fontSize = 10.sp,
            color = theme.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildLocalIntradayTips(marketStatus: MarketStatus?): IntradayTipsResponse {
    val isHoliday = marketStatus?.message?.contains("holiday", ignoreCase = true) == true
    val phase = MarketSession.phase(isHoliday = isHoliday)
    val tips = when (phase.id) {
        "weekend" -> listOf(
            IntradayTip("wk_journal", "Weekend review", "Tag last week's paper trades: plan followed? size too large? one process fix for Monday.", "process", "session"),
            IntradayTip("wk_calendar", "Scan the week ahead", "Note RBI/Fed/earnings dates — busy event days favour smaller size or sitting out.", "risk", "session"),
            IntradayTip("wk_watchlist", "Trim the watchlist", "Keep 5–8 liquid names with a clear level to avoid FOMO entries.", "process", "session"),
        )
        "holiday" -> listOf(
            IntradayTip("hol_gap", "Holiday gap risk", "Overnight news can gap the reopen. Prefer smaller size on the next cash session.", "risk", "session"),
            IntradayTip("hol_plan", "Prep, don't chase", "Write entry/stop/target while the tape is shut — decide before the open auction.", "process", "session"),
        )
        "pre_market" -> listOf(
            IntradayTip("pm_levels", "Mark key levels", "Note prior day high/low and invalidation before 9:15 IST.", "process", "session"),
            IntradayTip("pm_news", "Headline check", "If you can't name the risk, skip the paper trade.", "risk", "session"),
            IntradayTip("pm_size", "Pre-commit size", "Decide max loss in ₹ before the open. Intraday size should survive a bad first hour.", "risk", "session"),
        )
        "pre_open" -> listOf(
            IntradayTip("po_auction", "Pre-open is noisy", "9:00–9:15 discovery can fake breakouts. Wait for the continuous session.", "session", "session"),
            IntradayTip("po_orders", "Order discipline", "Prefer limits near your level — market orders into the open pay spreads.", "process", "session"),
        )
        "first_hour" -> listOf(
            IntradayTip("fh_patience", "First-hour volatility", "Let an opening range form (15–30 min) before chasing breakouts.", "session", "session"),
            IntradayTip("fh_stop", "Stop first, entry second", "If you can't place a stop where the thesis dies, you don't have a trade.", "process", "session"),
            IntradayTip("fh_fomo", "Skip the gap chase", "Late FOMO into already-extended opens often has poor R:R.", "psychology", "session"),
        )
        "mid_morning" -> listOf(
            IntradayTip("mm_trend", "Trade with breadth", "Strong advances → pullback longs; heavy declines → tighten risk.", "session", "session"),
            IntradayTip("mm_scale", "Scale, don't all-in", "Don't average losers mid-morning — that is how a practice day blows up.", "risk", "session"),
        )
        "lunch_lull" -> listOf(
            IntradayTip("ll_chop", "Midday chop zone", "12:00–13:30 IST often ranges. Smaller size or wait — fake breaks are common.", "session", "session"),
            IntradayTip("ll_revenge", "No revenge trades", "After a stop-out, step away 10 minutes. The next impulse ticket is rarely the best idea.", "psychology", "session"),
        )
        "afternoon" -> listOf(
            IntradayTip("af_size_down", "Cut size into the close", "New paper positions after 14:30 IST need a stronger reason — less time for the thesis.", "risk", "session"),
            IntradayTip("af_time_stop", "Time stops matter", "If it hasn't worked by mid-afternoon, reassess. Dead capital needs a decision.", "process", "session"),
        )
        "closing_window" -> listOf(
            IntradayTip("cw_cas", "Know the CAS clock", "From 3 Aug 2026: F&O cash continuous ~15:15, CAS ~15:35, derivatives ~15:40 IST — broker MIS may square earlier.", "session", "session"),
            IntradayTip("cw_flat", "Intraday → flat", "Don't leave MIS hopes overnight. Square off with a time buffer — last minutes are chaotic.", "risk", "session"),
            IntradayTip("cw_no_lottery", "No closing lottery", "Don't double size in the last 20 minutes to 'make the day back'. That is variance, not skill.", "psychology", "session"),
        )
        else -> listOf(
            IntradayTip("ah_review", "After-hours debrief", "Grade process, not P&L. A green day with broken rules is still a bad practice day.", "process", "session"),
            IntradayTip("ah_rest", "Protect attention", "Stop refreshing after close — fresh decisions need a clear head at 9:15 IST.", "psychology", "session"),
        )
    }
    return IntradayTipsResponse(
        phase = phase.id,
        phaseLabel = phase.label,
        isOpen = phase.isOpen && marketStatus?.isOpen != false,
        tips = tips,
        disclaimer = "Educational session habits — not stock tips or investment advice.",
        paperNote = "IST session cues until enough paper fills are logged.",
    )
}

@Composable
private fun TodaysPracticeStrip(
    habit: PracticeHabitStore.DayState,
    progress: PracticeHabitStore.Progress = PracticeHabitStore.Progress(),
    onReviewClick: (() -> Unit)?,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .byselSectionSurface(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today's Practice",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                val streakLabel = if (progress.streakDays > 0) " · ${progress.streakDays}-day streak" else ""
                Text(
                    text = "Idea → Paper trade → Review  ·  score ${habit.score}/3$streakLabel",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
            Text(
                text = "${habit.score}/3",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = theme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PracticeStepChip(
                label = "Idea",
                done = habit.ideaSeen,
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.weight(1f),
            )
            PracticeStepChip(
                label = if (habit.tradeDone) habit.tradedSymbol ?: "Trade" else "Trade",
                done = habit.tradeDone || habit.alertSet,
                icon = if (habit.alertSet && !habit.tradeDone) {
                    Icons.Filled.NotificationsActive
                } else {
                    Icons.Filled.ShoppingCart
                },
                modifier = Modifier.weight(1f),
            )
            PracticeStepChip(
                label = "Review",
                done = habit.reviewed,
                icon = Icons.Filled.EditNote,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!habit.reviewed && habit.tradeDone && onReviewClick != null) {
                            Modifier.clickable(onClick = onReviewClick)
                        } else {
                            Modifier
                        }
                    ),
            )
        }
        if (habit.reviewed || progress.reviewsCompleted > 0) {
            val slBits = buildList {
                if (habit.reviewed) {
                    add(if (habit.setSl) "SL ✓ today" else "SL skipped today")
                    add(if (habit.followedPlan) "Plan ✓" else "Plan skipped")
                }
                progress.slDisciplinePct?.let { add("SL discipline $it% (${progress.slRespected}/${progress.reviewsCompleted})") }
            }
            if (slBits.isNotEmpty()) {
                Text(
                    text = slBits.joinToString(" · "),
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                )
            }
        }
        if (habit.tradeDone && !habit.reviewed) {
            Text(
                text = "Tap Review to journal SL discipline — that closes today's loop.",
                fontSize = 10.sp,
                color = theme.textSecondary,
            )
        } else if (habit.reviewed) {
            Text(
                text = if (progress.streakDays > 1) {
                    "Loop complete · keep the ${progress.streakDays}-day streak tomorrow."
                } else {
                    "Loop complete. Come back tomorrow for the next drill."
                },
                fontSize = 10.sp,
                color = theme.positive,
            )
        } else {
            Text(
                text = "Pick a Practice Idea below, paper-buy or set Alert @ SL, then review.",
                fontSize = 10.sp,
                color = theme.textSecondary,
            )
        }
    }
}

@Composable
private fun PracticeStepChip(
    label: String,
    done: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (done) theme.positive.copy(alpha = 0.14f) else theme.surface
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else icon,
            contentDescription = null,
            tint = if (done) theme.positive else theme.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (done) theme.positive else theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class PracticeReviewTarget(
    val symbol: String,
    val qty: Int,
    val price: Double,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeReviewSheet(
    target: PracticeReviewTarget,
    onDismiss: () -> Unit,
    onSubmit: (note: String, setSl: Boolean, followedPlan: Boolean) -> Unit,
) {
    val theme = LocalAppTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by rememberSaveable { mutableStateOf("") }
    var setSl by rememberSaveable { mutableStateOf(true) }
    var followedPlan by rememberSaveable { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Practice Review",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            Text(
                text = "${target.symbol} · ${target.qty} share(s) @ ₹${"%.2f".format(target.price)}",
                fontSize = 13.sp,
                color = theme.textSecondary,
            )
            Text(
                text = "Close the habit loop — process over P&L.",
                fontSize = 12.sp,
                color = theme.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = setSl,
                    onCheckedChange = { setSl = it },
                )
                Text("I set / respected a stop-loss", color = theme.text, fontSize = 13.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = followedPlan,
                    onCheckedChange = { followedPlan = it },
                )
                Text("I followed the idea plan (no chase)", color = theme.text, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(240) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Journal note (optional)") },
                placeholder = { Text("Why this entry? What will you do if SL hits?") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Button(
                onClick = { onSubmit(note, setSl, followedPlan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save review", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun PaperWalletHomeStrip(
    balance: Double,
    onAddFunds: (() -> Unit)?,
) {
    val theme = LocalAppTheme.current
    val empty = balance <= 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (empty) theme.primary.copy(alpha = 0.12f)
                else theme.card
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Paper wallet",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary,
            )
            com.bysel.trader.ui.theme.AnimatedAmountText(
                amount = balance,
                formatter = { "₹${"%,.0f".format(it)}" },
                style = MaterialTheme.typography.headlineSmall,
                color = theme.text,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (empty) "Add practice credit before Paper Buy." else "Simulation cash · not real money",
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
        }
        if (onAddFunds != null) {
            Button(
                onClick = onAddFunds,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = if (empty) "Add credit" else "Top up",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PracticeIdeasSection(
    ideas: List<PracticeIdea>,
    loading: Boolean,
    disclaimer: String,
    onOpenSymbol: (String) -> Unit,
    onPaperBuy: ((String, Int) -> Unit)?,
    onPracticeAlert: ((String, Double, String) -> Unit)?,
    needsPracticeCredit: Boolean = false,
    onAddPracticeFunds: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Practice Ideas",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(
                    text = "Drill entry · stop · target with paper money",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                )
            }
            Text(
                text = "SIM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = theme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (needsPracticeCredit && onAddPracticeFunds != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add practice credit to start",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                    )
                    Text(
                        text = "Paper wallet is empty — fund simulation cash before Paper Buy.",
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                    )
                }
                Button(
                    onClick = onAddPracticeFunds,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Add credit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        when {
            loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = theme.primary,
                )
            }
            ideas.isEmpty() -> {
                Text(
                    text = "Pull to refresh for today’s practice drills.",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )
            }
            else -> {
                LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ideas, key = { it.symbol + it.stance }) { idea ->
                        PracticeIdeaCard(
                            idea = idea,
                            onOpen = { onOpenSymbol(idea.symbol) },
                            onPaperBuy = onPaperBuy?.let { buy ->
                                { buy(idea.symbol, idea.suggestedQty.coerceAtLeast(1)) }
                            },
                            onAlertAtStop = onPracticeAlert?.let { alert ->
                                { alert(idea.symbol, idea.stopLoss, "BELOW") }
                            },
                        )
                    }
                }
                Text(
                    text = disclaimer,
                    fontSize = 10.sp,
                    color = theme.textSecondary,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun PracticeIdeaCard(
    idea: PracticeIdea,
    onOpen: () -> Unit,
    onPaperBuy: (() -> Unit)?,
    onAlertAtStop: (() -> Unit)?,
) {
    val theme = LocalAppTheme.current
    val stanceColor = when (idea.stance) {
        "MOMENTUM_DRILL" -> theme.positive
        "DIP_DRILL" -> theme.negative
        else -> theme.primary
    }
    Column(
        modifier = Modifier
            .width(268.dp)
            .byselSectionSurface(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = idea.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                )
                Text(
                    text = idea.title.ifBlank { "Practice drill" },
                    fontSize = 11.sp,
                    color = stanceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatSignedPercent(idea.pctChange),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (idea.pctChange >= 0) theme.positive else theme.negative,
            )
        }

        Text(
            text = idea.coaching,
            fontSize = 11.sp,
            color = theme.textSecondary,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LevelPill(label = "Entry", value = "₹${"%.1f".format(idea.entry)}")
            LevelPill(label = "SL", value = "₹${"%.1f".format(idea.stopLoss)}")
            LevelPill(label = "Target", value = "₹${"%.1f".format(idea.target)}")
        }

        Text(
            text = "R:R ${"%.2f".format(idea.riskReward)} · qty ${idea.suggestedQty.coerceAtLeast(1)}",
            fontSize = 10.sp,
            color = theme.textSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onPaperBuy != null) {
                Button(
                    onClick = onPaperBuy,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.positive),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Paper Buy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onAlertAtStop != null) {
                OutlinedButton(
                    onClick = onAlertAtStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Alert @ SL", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LevelPill(label: String, value: String) {
    val theme = LocalAppTheme.current
    Column {
        Text(text = label, fontSize = 9.sp, color = theme.textSecondary)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.text)
    }
}

private fun buildLocalPracticeIdeas(quotes: List<Quote>): List<PracticeIdea> {
    return quotes
        .filter { it.last > 0 }
        .distinctBy { it.symbol }
        .take(6)
        .map { q ->
            val move = kotlin.math.abs(q.pctChange)
            val slPct = (move / 100.0 * 1.4 + 0.01).coerceIn(0.012, 0.045)
            val tpPct = (slPct * 1.7).coerceIn(0.018, 0.07)
            val entry = q.last
            val stop = entry * (1.0 - slPct)
            val target = entry * (1.0 + tpPct)
            val risk = (entry - stop).coerceAtLeast(0.01)
            val reward = (target - entry).coerceAtLeast(0.01)
            val stance = when {
                q.pctChange >= 0.8 -> "MOMENTUM_DRILL"
                q.pctChange <= -0.8 -> "DIP_DRILL"
                else -> "RANGE_DRILL"
            }
            val title = when (stance) {
                "MOMENTUM_DRILL" -> "Momentum practice"
                "DIP_DRILL" -> "Dip discipline drill"
                else -> "Range journaling drill"
            }
            PracticeIdea(
                symbol = q.symbol,
                name = q.symbol,
                stance = stance,
                title = title,
                coaching = "Use live tape on ${q.symbol} to rehearse levels — paper only.",
                lastPrice = entry,
                pctChange = q.pctChange,
                entry = entry,
                stopLoss = stop,
                target = target,
                riskReward = reward / risk,
                suggestedQty = 1,
            )
        }
}

@Composable
private fun IdeasRail(
    topMover: Quote?,
    signalTitle: String?,
    newsCount: Int,
    onAiBrief: (() -> Unit)?,
    onSignalLab: (() -> Unit)?,
    onScanner: (() -> Unit)?,
    onSmartMoney: (() -> Unit)?,
    onOpenMover: (() -> Unit)?,
) {
    val theme = LocalAppTheme.current
    val ideas = listOfNotNull(
        onAiBrief?.let {
            IdeaChip(
                title = "AI Brief",
                subtitle = if (newsCount > 0) "$newsCount stories in play" else "Ask the AI tab",
                icon = Icons.Filled.Psychology,
                onClick = it,
            )
        },
        onScanner?.let {
            IdeaChip(
                title = "BYSEL Top Picks",
                subtitle = "Scanner · score + paper swing cards",
                icon = Icons.Filled.Explore,
                onClick = it,
            )
        },
        onSignalLab?.let {
            IdeaChip(
                title = "Signal Lab",
                subtitle = signalTitle ?: "Phase setups",
                icon = Icons.Filled.AutoAwesome,
                onClick = it,
            )
        },
        onSmartMoney?.let {
            IdeaChip(
                title = "Smart Money",
                subtitle = "Investor portfolios",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                onClick = it,
            )
        },
        if (topMover != null && onOpenMover != null) {
            IdeaChip(
                title = topMover.symbol,
                subtitle = formatSignedPercent(topMover.pctChange),
                icon = if (topMover.pctChange >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                onClick = onOpenMover,
            )
        } else null,
    )
    if (ideas.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Shortcuts",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
        )
        LazyRow(modifier = Modifier.exclusiveHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ideas, key = { it.title }) { idea ->
                Row(
                    modifier = Modifier
                        .widthIn(min = 148.dp, max = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.card)
                        .clickable(onClick = idea.onClick)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        idea.icon,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = idea.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.text,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = idea.subtitle,
                            fontSize = 10.sp,
                            color = theme.textSecondary,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class IdeaChip(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private fun formatCompactCurrency(value: Double): String = formatInrCompact(value)

private val HOME_INDEX_SYMBOLS = setOf("NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT")

private fun sessionBreadth(quotes: List<Quote>): Pair<Int, Int> {
    val tape = quotes.filter { it.symbol.uppercase() !in HOME_INDEX_SYMBOLS }
    return tape.count { it.pctChange > 0.0 } to tape.count { it.pctChange < 0.0 }
}

private fun liveHoldingPrice(holding: Holding, quotes: List<Quote>): Double {
    val live = quotes.firstOrNull { it.symbol.equals(holding.symbol, ignoreCase = true) }?.last
    return if (live != null && live > 0.0) live else holding.last
}

private fun formatSignedPercent(value: Double): String = formatSignedPct(value)

private fun formatCompactVolume(value: Long?): String = formatVolumeCompact(value)

@Composable
fun PortfolioSummaryCard(holdings: List<Holding>, quotes: List<Quote> = emptyList()) {
    val totalValue = holdings.sumOf { it.qty * liveHoldingPrice(it, quotes) }
    val totalInvested = holdings.sumOf { it.qty * it.avgPrice }
    val totalPnL = totalValue - totalInvested
    val totalPnLPercent = if (totalInvested > 0) (totalPnL / totalInvested) * 100 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppTheme.current.card)
            .padding(bottom = 16.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Total Portfolio Value",
                fontSize = 14.sp,
                color = LocalAppTheme.current.textSecondary
            )
            Text(
                text = "₹${String.format("%.2f", totalValue)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Invested",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalInvested)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "P&L",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", totalPnL)} (${String.format("%.2f", totalPnLPercent)}%)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalPnL >= 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = LocalAppTheme.current.textSecondary.copy(alpha = 0.25f)
            )

            Text(
                text = "Holdings: ${holdings.size} stocks",
                fontSize = 12.sp,
                color = LocalAppTheme.current.textSecondary
            )
        }
    }
}

@Composable
fun GainerLosersCard(
    quote: Quote,
    isGainer: Boolean,
    isPinned: Boolean = false,
    onPinClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) LocalAppTheme.current.positive else LocalAppTheme.current.textSecondary
                )
            }

            Row(
                modifier = Modifier
                    .background(
                        color = if (isGainer) LocalAppTheme.current.pnlWash(true)
                        else LocalAppTheme.current.pnlWash(false),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGainer) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (isGainer) LocalAppTheme.current.positive else LocalAppTheme.current.negative,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGainer) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
            }
        }
    }
}

@Composable
fun AiDailyBriefCard(
    holdings: List<Holding>,
    positiveCount: Int,
    negativeCount: Int,
    averageMove: Double,
    newsCount: Int,
    topMover: Quote?,
    onAskAi: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val briefLines = remember(holdings, positiveCount, negativeCount, averageMove, newsCount, topMover) {
        buildList {
            // Market tone
            when {
                averageMove >= 0.75 -> add("Markets are broadly positive today — momentum favours fresh entries.")
                averageMove <= -0.75 -> add("Selling pressure is dominant — consider tightening stops.")
                positiveCount > negativeCount -> add("Mixed session leaning bullish — selective opportunities.")
                else -> add("Caution in the tape — breadth is weak today.")
            }
            // Portfolio
            if (holdings.isNotEmpty()) {
                val pnl = holdings.sumOf { it.qty * it.last } - holdings.sumOf { it.qty * it.avgPrice }
                if (pnl >= 0) add("Your portfolio is up ₹${String.format("%,.0f", pnl)} today.")
                else add("Your portfolio is down ₹${String.format("%,.0f", -pnl)} — review weak links.")
            } else {
                add("No holdings yet — ask AI for starter picks.")
            }
            // Top mover
            topMover?.let {
                add("${it.symbol} leads at ${if (it.pctChange >= 0) "+" else ""}${String.format("%.1f", it.pctChange)}%.")
            }
            // News
            if (newsCount > 0) add("$newsCount live headlines driving sentiment.")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Daily Brief",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = theme.text
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            briefLines.forEach { line ->
                Text(
                    text = "• $line",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp,
                )
            }
            if (onAskAi != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAskAi,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Ask AI for Detailed Analysis", fontSize = 13.sp)
                }
            }
        }
    }
}

/** Prefer watchlist + holdings for Home news, pad with liquid names up to 12. */
private fun buildPersonalNewsSymbols(
    watchlist: List<String>,
    holdingSymbols: List<String>,
): List<String> {
    val preferred = (watchlist + holdingSymbols)
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotBlank() && !it.startsWith("^") }
        .distinct()
    val liquidDefaults = listOf(
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
        "SBIN", "BHARTIARTL", "ITC", "LT", "TMPV",
        "AXISBANK", "KOTAKBANK",
    )
    return (preferred + liquidDefaults).distinct().take(12)
}
