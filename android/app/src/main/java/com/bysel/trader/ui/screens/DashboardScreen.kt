package com.bysel.trader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.bysel.trader.data.PracticeHabitStore
import com.bysel.trader.data.models.Holding
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
import com.bysel.trader.ui.components.HabitLiteracyCatalog
import com.bysel.trader.ui.components.NewsWidget
import com.bysel.trader.ui.components.PullToRefreshBox
import com.bysel.trader.ui.components.blockParentHorizontalPager
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

private data class HomeGuideStep(
    val title: String,
    val body: String,
    val lookHint: String,
    val actionLabel: String,
    val icon: ImageVector,
)

private val HomeGuideSteps = listOf(
    HomeGuideStep(
        title = "BYSEL Pulse",
        body = "This top card is your session brief — market mood, portfolio value, and fast actions. Pull down on Home to sync quotes and headlines.",
        lookHint = "Watch the glowing Pulse card above.",
        actionLabel = "Next",
        icon = Icons.Filled.Info,
    ),
    HomeGuideStep(
        title = "Session tape",
        body = "Nifty, Sensex, and Bank Nifty plus whether NSE is open. Mood and portfolio stay on Pulse — this strip is only the session tape.",
        lookHint = "Watch Session tape glow.",
        actionLabel = "Show tape",
        icon = Icons.Filled.Schedule,
    ),
    HomeGuideStep(
        title = "Pin to Your Space",
        body = "Pin News and Market Watch so they stay in Your Space above the fold. We’ll pin them for you and scroll there.",
        lookHint = "Your Space will glow after we pin.",
        actionLabel = "Pin widgets",
        icon = Icons.Filled.PushPin,
    ),
    HomeGuideStep(
        title = "Reorder widgets",
        body = "Use the ↑↓ arrows beside pinned widgets to change order. We’ll bump News upward so you can see the reorder live.",
        lookHint = "Watch Your Space move News up.",
        actionLabel = "Reorder News",
        icon = Icons.Filled.SwapVert,
    ),
    HomeGuideStep(
        title = "Live refresh",
        body = "Pull down anywhere on Home to sync quotes and market news. There is no extra Refresh button — the pull is the refresh.",
        lookHint = "Pull the Home list down to refresh.",
        actionLabel = "Refresh now",
        icon = Icons.Filled.Refresh,
    ),
    HomeGuideStep(
        title = "You’re set",
        body = "Customize Home around your session: pin what matters, reorder Your Space, and use Ask AI on the brief card when you want a chat.",
        lookHint = "You can reopen Guide anytime from Pulse.",
        actionLabel = "Done",
        icon = Icons.Filled.CheckCircle,
    ),
)

@Composable
private fun Modifier.guideSpotlight(active: Boolean): Modifier {
    if (!active) return this
    val theme = LocalAppTheme.current
    val pulse = rememberInfiniteTransition(label = "guideSpotlight")
    val glow by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "guideSpotlightGlow",
    )
    return this.border(
        width = 2.dp,
        color = theme.primary.copy(alpha = glow),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun HomeGuideDialog(
    step: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onTryAction: (Int) -> Unit,
    actionFeedback: String? = null,
) {
    val theme = LocalAppTheme.current
    val safeStep = step.coerceIn(0, HomeGuideSteps.lastIndex)
    val current = HomeGuideSteps[safeStep]
    val isLast = safeStep >= HomeGuideSteps.lastIndex
    val progress by animateFloatAsState(
        targetValue = (safeStep + 1f) / HomeGuideSteps.size,
        animationSpec = tween(durationMillis = 420),
        label = "guideProgress",
    )
    val iconPulse = rememberInfiniteTransition(label = "guideIcon")
    val iconScale by iconPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "guideIconScale",
    )
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }

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
            AnimatedVisibility(
                visible = sheetVisible,
                enter = fadeIn(tween(180)) + slideInVertically(tween(320)) { it / 4 },
                exit = fadeOut(tween(140)),
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
                            .padding(20.dp)
                            .animateContentSize(animationSpec = tween(280)),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedContent(
                            targetState = safeStep,
                            transitionSpec = {
                                val forward = targetState >= initialState
                                val enter = slideInHorizontally(
                                    animationSpec = tween(280),
                                    initialOffsetX = { if (forward) it / 3 else -it / 3 },
                                ) + fadeIn(tween(220))
                                val exit = slideOutHorizontally(
                                    animationSpec = tween(220),
                                    targetOffsetX = { if (forward) -it / 4 else it / 4 },
                                ) + fadeOut(tween(160))
                                (enter togetherWith exit).using(SizeTransform(clip = false))
                            },
                            label = "guideStepContent",
                        ) { animatedStep ->
                            val shown = HomeGuideSteps[animatedStep]
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        shown.icon,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.scale(iconScale),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Home Guide",
                                            color = theme.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = shown.title,
                                            color = theme.text,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = "${animatedStep + 1}/${HomeGuideSteps.size}",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = shown.lookHint,
                                    color = theme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = shown.body,
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp, max = 120.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.primary,
                            trackColor = theme.textSecondary.copy(alpha = 0.2f),
                        )

                        AnimatedVisibility(
                            visible = !actionFeedback.isNullOrBlank(),
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                        ) {
                            Text(
                                text = actionFeedback.orEmpty(),
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
                                val filled by animateColorAsState(
                                    targetValue = if (index <= safeStep) {
                                        theme.primary
                                    } else {
                                        theme.textSecondary.copy(alpha = 0.25f)
                                    },
                                    animationSpec = tween(280),
                                    label = "guideDot$index",
                                )
                                Box(
                                    modifier = Modifier
                                        .height(4.dp)
                                        .weight(1f)
                                        .background(color = filled, shape = RoundedCornerShape(2.dp)),
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
                            if (safeStep > 0) {
                                OutlinedButton(onClick = { onStepChange(safeStep - 1) }) {
                                    Text("Back")
                                }
                            }
                            Button(
                                onClick = {
                                    if (isLast) onDismiss() else onTryAction(safeStep)
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
    onAiQuery: ((String) -> Unit)? = null,
    marketStatus: MarketStatus? = null,
    onQuickTradeClick: ((String) -> Unit)? = null,
    onSignalLabClick: (() -> Unit)? = null,
    onScannerClick: (() -> Unit)? = null,
    onSmartMoneyClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onPaperBuy: ((String, Int) -> Unit)? = null,
    onPracticeAlert: ((String, Double, String) -> Unit)? = null,
    lastExecutedOrder: OrderResponse? = null,
    onPracticeReviewSubmit: ((symbol: String, qty: Int, price: Double, note: String, setSl: Boolean, followedPlan: Boolean) -> Unit)? = null,
    walletBalance: Double = 0.0,
    onAddPracticeFunds: (() -> Unit)? = null,
    watchlistSymbols: List<String> = emptyList(),
    scrollToTopTick: Int = 0,
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
            scrollToTopTick = scrollToTopTick,
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
            onAiQuery = onAiQuery,
            marketStatus = marketStatus,
            onQuickTradeClick = onQuickTradeClick,
            onSignalLabClick = onSignalLabClick,
            onScannerClick = onScannerClick,
            onSmartMoneyClick = onSmartMoneyClick,
            onSearchClick = onSearchClick,
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
    onAiQuery: ((String) -> Unit)? = null,
    marketStatus: MarketStatus? = null,
    onQuickTradeClick: ((String) -> Unit)? = null,
    onSignalLabClick: (() -> Unit)? = null,
    onScannerClick: (() -> Unit)? = null,
    onSmartMoneyClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
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
    scrollToTopTick: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            homeListState.animateScrollToItem(0)
        }
    }
    val sessionTapeRequester = remember { BringIntoViewRequester() }
    val yourSpaceRequester = remember { BringIntoViewRequester() }
    val portfolioWidgetRequester = remember { BringIntoViewRequester() }
    val newsRequester = remember { BringIntoViewRequester() }
    var guideFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var layoutResetHint by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(showHomeGuide) {
        if (showHomeGuide && homeGuideStep == 0) {
            guideFeedback = null
        }
    }
    LaunchedEffect(showHomeGuide, homeGuideStep) {
        if (!showHomeGuide) return@LaunchedEffect
        delay(200)
        when (homeGuideStep) {
            0, 4 -> homeListState.animateScrollToItem(0)
            1 -> sessionTapeRequester.bringIntoView()
            2, 3 -> yourSpaceRequester.bringIntoView()
        }
    }
    val pinnedList = remember(quotes, pinnedStocks) {
        quotes.filter { pinnedStocks.contains(it.symbol) }
    }
    val pinnedNotOnWatchlist = remember(pinnedList, watchlistSymbols) {
        val watched = watchlistSymbols.map { WatchlistSymbols.normalize(it) }.toSet()
        pinnedList.filter { WatchlistSymbols.normalize(it.symbol) !in watched }
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
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.surface)
                .padding(16.dp)
        ) {
        item {
            Box(
                modifier = Modifier.guideSpotlight(
                    showHomeGuide && (homeGuideStep == 0 || homeGuideStep == 4),
                ),
            ) {
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
                onResetLayout = {
                    dashboardViewModel.resetDashboardLayout()
                    layoutResetHint = "Restored News and Market Watch in Your Space."
                    scope.launch {
                        delay(120)
                        yourSpaceRequester.bringIntoView()
                    }
                },
                onOpenLead = { focusQuotes.firstOrNull()?.let { onTradeClick(it.symbol) } },
                layoutResetHint = layoutResetHint,
            )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .bringIntoViewRequester(sessionTapeRequester)
                    .guideSpotlight(showHomeGuide && homeGuideStep == 1),
            ) {
                MarketPulseHero(
                    quotes = quotes,
                    marketStatus = marketStatus,
                )
            }
        }

        item {
            IdeasRail(
                signalTitle = signalBuckets.firstOrNull()?.title,
                onSignalLab = onSignalLabClick,
                onScanner = onScannerClick,
                onSmartMoney = onSmartMoneyClick,
                onSearch = onSearchClick,
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

        item {
            AiDailyBriefCard(onAskAi = onAiClick)
        }

        item {
            SectionHeader(
                title = "Your Space",
                subtitle = "Watchlist and news — pin and reorder what you want on the tape.",
                modifier = Modifier
                    .bringIntoViewRequester(yourSpaceRequester)
                    .guideSpotlight(showHomeGuide && homeGuideStep in 2..3),
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

        if (pinnedNotOnWatchlist.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Pinned Conviction",
                    subtitle = "Starred on Home and not already on Market Watch.",
                )
            }
            items(items = pinnedNotOnWatchlist, key = { "pinned_${it.symbol}" }) { quote ->
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

        if (onAiQuery != null) {
            item {
                HomeLearnSection(onLearnQuery = onAiQuery)
            }
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
                            sessionTapeRequester.bringIntoView()
                            guideFeedback = "Session tape — indices and open/closed. Mood stays on Pulse."
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
    onShowGuide: () -> Unit,
    onTogglePortfolioPin: () -> Unit,
    onResetLayout: () -> Unit,
    onOpenLead: () -> Unit,
    layoutResetHint: String? = null,
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
            }

            TextButton(onClick = onResetLayout) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset dashboard layout")
            }
            if (!layoutResetHint.isNullOrBlank()) {
                Text(
                    text = layoutResetHint,
                    color = theme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                )
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
private fun MarketPulseHero(
    quotes: List<Quote>,
    marketStatus: MarketStatus?,
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
                        text = "Session tape",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    )
                    Text(
                        text = "Nifty · Sensex · Bank Nifty",
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
        }
    }
}

@Composable
private fun HomeLearnSection(
    onLearnQuery: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    val links = HabitLiteracyCatalog.sessionLinks + HabitLiteracyCatalog.investorLinks
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .byselSectionSurface(RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Learn",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
        )
        Text(
            text = "Opens Ask AI with that lesson — one chat, not a second brief.",
            fontSize = 11.sp,
            color = theme.textSecondary,
            lineHeight = 15.sp,
        )
        links.forEach { link ->
            TextButton(
                onClick = { onLearnQuery(link.learnQuery) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = link.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.primary,
                )
            }
        }
    }
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
    signalTitle: String?,
    onSignalLab: (() -> Unit)?,
    onScanner: (() -> Unit)?,
    onSmartMoney: (() -> Unit)?,
    onSearch: (() -> Unit)?,
) {
    val theme = LocalAppTheme.current
    val ideas = listOfNotNull(
        onSearch?.let {
            IdeaChip(
                title = "Search Stocks",
                subtitle = "Full NSE catalog · add to My list",
                icon = Icons.Filled.Search,
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
    )
    if (ideas.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .blockParentHorizontalPager(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Shortcuts",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
        )
        ideas.forEach { idea ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.card)
                    .clickable(onClick = idea.onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    idea.icon,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = idea.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = idea.subtitle,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    onAskAi: (() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
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
                    text = "Ask AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = theme.text
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ask about the session, a name, or a paper plan. Mood and headlines stay on Pulse and News.",
                fontSize = 13.sp,
                color = theme.textSecondary,
                lineHeight = 18.sp,
            )
            if (onAskAi != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAskAi,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Open chat", fontSize = 13.sp)
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
