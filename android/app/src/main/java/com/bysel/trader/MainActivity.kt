package com.bysel.trader
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bysel.trader.security.BiometricAuthManager

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.repository.AuthRepository
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.ui.screens.*
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.getTheme
import com.bysel.trader.viewmodel.TradingViewModel
import com.bysel.trader.viewmodel.TradingViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : FragmentActivity() {
    private var upiResultCallback: ((Boolean) -> Unit)? = null
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var tradingViewModel: TradingViewModel
    private var isAuthenticated = false



    private lateinit var upiLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install modern splash screen (Material You)
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Initialize biometric auth manager
        biometricAuthManager = BiometricAuthManager(this)
        
        // Keep splash screen visible while checking biometric auth
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        upiLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val response = data?.getStringExtra("response") ?: ""
            val success = response.contains("SUCCESS", ignoreCase = true)
            upiResultCallback?.invoke(success)
            upiResultCallback = null
        }

        AuthSessionManager.init(applicationContext)

        val database = BYSELDatabase.getInstance(this)
        val repository = TradingRepository(database)
        val factory = TradingViewModelFactory(repository)
        factory.initApplication(application)
        tradingViewModel = ViewModelProvider(
            this,
            factory
        ).get(TradingViewModel::class.java)
        
        // Handle app shortcuts
        val shortcutAction = intent.getStringExtra("shortcut_action")

        setContent {
            val authRepository = remember { AuthRepository() }
            val scope = rememberCoroutineScope()
            val isLoggedIn by AuthSessionManager.sessionState.collectAsState()
            var wasLoggedIn by remember { mutableStateOf(isLoggedIn) }
            var manualLogoutInProgress by remember { mutableStateOf(false) }

            // Show biometric lock screen if enabled and not authenticated
            var showLockScreen by remember { mutableStateOf(
                isLoggedIn && biometricAuthManager.isBiometricEnabled() && !isAuthenticated
            ) }
            
            // Determine initial tab based on shortcut
            val initialTab = remember {
                when (shortcutAction) {
                    "open_portfolio" -> 3  // Portfolio tab
                    "buy_stock" -> 2       // Trading tab
                    "market_status" -> 4   // Heatmap tab
                    "price_alerts" -> 7    // Alerts screen
                    else -> 0              // Default: Home
                }
            }
            
            LaunchedEffect(isLoggedIn) {
                keepSplashScreen = false // Dismiss splash screen

                if (wasLoggedIn && !isLoggedIn && !manualLogoutInProgress) {
                    isAuthenticated = false
                    showLockScreen = false
                    Toast.makeText(this@MainActivity, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show()
                }

                if (!isLoggedIn) {
                    manualLogoutInProgress = false
                }
                wasLoggedIn = isLoggedIn
                
                // Trigger biometric auth if enabled
                if (isLoggedIn && biometricAuthManager.isBiometricEnabled() && !isAuthenticated) {
                    biometricAuthManager.authenticateForAppUnlock(
                        activity = this@MainActivity,
                        onSuccess = {
                            isAuthenticated = true
                            showLockScreen = false
                        },
                        onCancel = {
                            // User cancelled - exit app
                            finish()
                        }
                    )
                }
            }

            if (!isLoggedIn) {
                AuthScreen(
                    onAuthenticated = {
                        isAuthenticated = !biometricAuthManager.isBiometricEnabled()
                        showLockScreen = biometricAuthManager.isBiometricEnabled() && !isAuthenticated
                    }
                )
            } else if (showLockScreen) {
                BiometricLockScreen(
                    onRetry = {
                        biometricAuthManager.authenticateForAppUnlock(
                            activity = this@MainActivity,
                            onSuccess = {
                                isAuthenticated = true
                                showLockScreen = false
                            },
                            onCancel = { finish() }
                        )
                    }
                )
            } else {
                BYSELApp(
                    viewModel = tradingViewModel,
                    biometricAuthManager = biometricAuthManager,
                    initialTab = initialTab,
                    onUpiPay = { amount, upiPackageName ->
                        launchUpiPayment(amount, upiPackageName) { success ->
                            if (success) tradingViewModel.addFunds(amount)
                        }
                    },
                    onLogout = {
                        manualLogoutInProgress = true
                        scope.launch {
                            authRepository.logout()
                            isAuthenticated = false
                            showLockScreen = false
                            Toast.makeText(this@MainActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogoutAllDevices = {
                        manualLogoutInProgress = true
                        scope.launch {
                            authRepository.logoutAllDevices()
                            isAuthenticated = false
                            showLockScreen = false
                            Toast.makeText(this@MainActivity, "Logged out from all devices", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::tradingViewModel.isInitialized && AuthSessionManager.hasSession()) {
            tradingViewModel.onAppForegroundResume()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check authentication when app returns from background
        if (AuthSessionManager.hasSession() && biometricAuthManager.isBiometricEnabled() && !isAuthenticated) {
            biometricAuthManager.authenticateForAppUnlock(
                activity = this,
                onSuccess = { isAuthenticated = true },
                onCancel = { finish() }
            )
        }

        if (::tradingViewModel.isInitialized && AuthSessionManager.hasSession()) {
            tradingViewModel.onAppForegroundResume()
        }
    }

    override fun onStop() {
        if (::tradingViewModel.isInitialized) {
            tradingViewModel.onAppBackgroundPause()
        }
        super.onStop()
    }

    private fun launchUpiPayment(amount: Double, upiPackage: String, onResult: (Boolean) -> Unit) {
        val upiUri = android.net.Uri.parse(
            "upi://pay?pa=your-vpa@upi&pn=BYSEL&am=$amount&cu=INR"
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, upiUri)
        intent.setPackage(upiPackage)
        upiResultCallback = onResult
        try {
            upiLauncher.launch(intent)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

@Composable
fun BiometricLockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF7C4DFF)
            )
            
            Text(
                text = "BYSEL is Locked",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Authenticate to access your portfolio",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF)
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock with Biometric", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun BYSELApp(
    viewModel: TradingViewModel, 
    biometricAuthManager: BiometricAuthManager,
    onUpiPay: (Double, String) -> Unit,
    onLogout: () -> Unit = {},
    onLogoutAllDevices: () -> Unit = {},
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bysel_settings", Context.MODE_PRIVATE) }
    var currentThemeName by remember { mutableStateOf(prefs.getString("theme", "Default") ?: "Default") }
    val appTheme = remember(currentThemeName) { getTheme(currentThemeName.lowercase()) }

    // Onboarding state
    var showOnboarding by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false).not()) }

    var selectedTab by remember { mutableStateOf(initialTab) }
    var previousTab by remember { mutableIntStateOf(0) }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 28.dp.toPx() }
    val swipeTriggerPx = with(density) { 110.dp.toPx() }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, 4),
        pageCount = { 5 }
    )
    
    val quotes by viewModel.quotes.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    // AI & Analytics state
    val chatHistory by viewModel.chatHistory.collectAsState()
    val aiLoading by viewModel.aiLoading.collectAsState()
    val portfolioHealth by viewModel.portfolioHealth.collectAsState()
    val healthLoading by viewModel.healthLoading.collectAsState()
    val marketHeatmap by viewModel.marketHeatmap.collectAsState()
    val heatmapLoading by viewModel.heatmapLoading.collectAsState()
    val selectedQuote by viewModel.selectedQuote.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()
    val marketStatus by viewModel.marketStatus.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab in 0..4 && pagerState.settledPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                if (selectedTab in 0..4 && selectedTab != settledPage) {
                    selectedTab = settledPage
                }
            }
    }

    BackHandler(enabled = true) {
        when {
            selectedTab == 9 -> {
                selectedTab = previousTab
            }

            selectedTab in 6..8 || selectedTab in 10..19 -> {
                selectedTab = 5
            }

            selectedTab in 1..5 -> {
                selectedTab = 0
            }

            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 1500L) {
                    (context as? androidx.activity.ComponentActivity)?.finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(context, "Swipe back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        if (showOnboarding) {
            com.bysel.trader.ui.screens.OnboardingScreen(
                onFinish = {
                    // Do NOT auto-initialize demo funds. Keep wallet at 0 by default.
                    // User can opt-in to demo from Settings or explicit UI action.
                    showOnboarding = false
                    prefs.edit().putBoolean("onboarding_complete", true).apply()
                }
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = appTheme.surface
            ) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.background(appTheme.card),
                            containerColor = appTheme.card
                        ) {
                    // Tab 0: Dashboard
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard", modifier = Modifier.size(22.dp)) },
                        label = { Text("Home", fontSize = 10.sp) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 1: AI Assistant
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Psychology, contentDescription = "AI", modifier = Modifier.size(22.dp)) },
                        label = { Text("AI", fontSize = 10.sp) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 2: Trading
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.AttachMoney, contentDescription = "Trade", modifier = Modifier.size(22.dp)) },
                        label = { Text("Trade", fontSize = 10.sp) },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 3: Portfolio
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Portfolio", modifier = Modifier.size(22.dp)) },
                        label = { Text("Portfolio", fontSize = 10.sp) },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 4: Heatmap
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.GridView, contentDescription = "Heatmap", modifier = Modifier.size(22.dp)) },
                        label = { Text("Heatmap", fontSize = 10.sp) },
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 5: More (Search, Alerts, Settings)
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More", modifier = Modifier.size(22.dp)) },
                        label = { Text("More", fontSize = 10.sp) },
                        selected = selectedTab in 5..19,
                        onClick = { selectedTab = 5 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C4DFF),
                            selectedTextColor = Color(0xFF7C4DFF),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                        }
                    }
                ) { paddingValues ->
                    val edgeGestureModifier = if (selectedTab !in 0..4) {
                        Modifier.pointerInput(selectedTab, previousTab) {
                            var dragStartX = 0f
                            var totalDragX = 0f
                            var handled = false

                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragStartX = offset.x
                                    totalDragX = 0f
                                    handled = false
                                },
                                onDragEnd = {
                                    totalDragX = 0f
                                    handled = false
                                },
                                onDragCancel = {
                                    totalDragX = 0f
                                    handled = false
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (handled) {
                                        return@detectHorizontalDragGestures
                                    }

                                    val canSwipeBack = selectedTab == 9 || selectedTab in 6..8 || selectedTab in 10..19
                                    val canSwipeForwardFromMore = selectedTab == 5
                                    val startedFromLeftEdge = dragStartX <= edgeThresholdPx
                                    val startedFromRightEdge = dragStartX >= size.width - edgeThresholdPx
                                    val triggerDistance = kotlin.math.max(swipeTriggerPx, size.width * 0.14f)

                                    totalDragX += dragAmount

                                    if (canSwipeBack && startedFromLeftEdge && totalDragX > triggerDistance) {
                                        handled = true
                                        change.consume()
                                        selectedTab = if (selectedTab == 9) previousTab else 5
                                    } else if (
                                        canSwipeForwardFromMore &&
                                        startedFromRightEdge &&
                                        totalDragX < -triggerDistance
                                    ) {
                                        handled = true
                                        change.consume()
                                        selectedTab = 6
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .then(edgeGestureModifier)
                            .background(appTheme.surface)
                    ) {
                        // Swipeable tabs for main 5 tabs (0-4)
                        if (selectedTab in 0..4) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondBoundsPageCount = 1
                            ) { page ->
                                when (page) {
                                    0 -> DashboardScreen(
                                        holdings = holdings,
                                        quotes = quotes,
                                        isLoading = isLoading,
                                        error = error,
                                        onRefresh = { viewModel.refreshQuotes() },
                                        onTradeClick = { selectedTab = 2 },
                                        onErrorDismiss = { viewModel.clearError() }
                                    )
                                    1 -> AiAssistantScreen(
                                        chatHistory = chatHistory,
                                        isLoading = aiLoading,
                                        onSendQuery = { query -> viewModel.askAi(query) },
                                        onSuggestionClick = { suggestion -> viewModel.askAi(suggestion) },
                                        onClearChat = { viewModel.clearChatHistory() },
                                        selectedSymbol = selectedQuote?.symbol
                                    )
                                    2 -> TradingScreen(
                                        isLoading = isLoading,
                                        error = error,
                                        walletBalance = walletBalance,
                                        marketStatus = marketStatus,
                                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                                        onRefresh = {
                                            viewModel.refreshQuotes()
                                            viewModel.refreshWallet()
                                            viewModel.refreshMarketStatus()
                                        },
                                        onAddFunds = { amount, upiProvider -> onUpiPay(amount, upiProvider) },
                                        onErrorDismiss = { viewModel.clearError() },
                                        onTraceSupportLookup = { traceId ->
                                            viewModel.seedTraceLookup(traceId)
                                            viewModel.lookupOrderByTrace(traceId)
                                            selectedTab = 19
                                        },
                                        viewModel = viewModel
                                    )
                                    3 -> PortfolioScreen(
                                        holdings = holdings,
                                        quotes = quotes,
                                        isLoading = isLoading,
                                        error = error,
                                        portfolioHealth = portfolioHealth,
                                        healthLoading = healthLoading,
                                        onRefresh = { viewModel.refreshHoldings() },
                                        onRefreshHealth = { viewModel.loadPortfolioHealth() },
                                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                                        onErrorDismiss = { viewModel.clearError() }
                                    )
                                    4 -> HeatmapScreen(
                                        heatmap = marketHeatmap,
                                        isLoading = heatmapLoading,
                                        onRefresh = { viewModel.loadMarketHeatmap() },
                                        onStockClick = { symbol ->
                                            previousTab = selectedTab
                                            viewModel.fetchAndSelectQuote(symbol)
                                            selectedTab = 9
                                        }
                                    )
                                }
                            }
                        } else {
                            // Non-swipeable screens (More, Search, Alerts, Settings, Detail, Achievements)
                            when (selectedTab) {
                                5 -> MoreScreen(
                                    onSearchClick = { selectedTab = 6 },
                                    onLiveQuotesClick = { selectedTab = 0 },
                                    onAlertsClick = { selectedTab = 7 },
                                    onSettingsClick = { selectedTab = 8 },
                                    onAchievementsClick = { selectedTab = 10 },
                                    onMutualFundsClick = { selectedTab = 11 },
                                    onIpoClick = { selectedTab = 12 },
                                    onEtfClick = { selectedTab = 13 },
                                    onSipClick = { selectedTab = 14 },
                                    onMyIpoApplicationsClick = { selectedTab = 15 },
                                    onAdvancedOrdersClick = { selectedTab = 16 },
                                    onDerivativesClick = { selectedTab = 17 },
                                    onWealthOsClick = { selectedTab = 18 },
                                    onCopilotCenterClick = { selectedTab = 19 }
                                )
                                10 -> com.bysel.trader.ui.screens.AchievementsScreen(viewModel)
                                11 -> MutualFundsScreen(viewModel)
                                12 -> IpoListingsScreen(viewModel)
                                13 -> EtfScreen(viewModel)
                                14 -> SipPlansScreen(viewModel)
                                15 -> MyIpoApplicationsScreen(viewModel)
                                16 -> AdvancedOrdersScreen(viewModel)
                                17 -> DerivativesIntelligenceScreen(viewModel)
                                18 -> WealthOsScreen(viewModel)
                                19 -> CopilotCenterScreen(viewModel)
                                6 -> SearchScreen(
                                    quotes = quotes,
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    onSearchQuery = { query -> viewModel.searchStocks(query) },
                                    onClearSearch = { viewModel.clearSearchResults() },
                                    onQuoteClick = { quote ->
                                        previousTab = selectedTab
                                        viewModel.setSelectedQuote(quote)
                                        selectedTab = 9
                                    },
                                    onSymbolClick = { symbol ->
                                        previousTab = selectedTab
                                        viewModel.fetchAndSelectQuote(symbol)
                                        selectedTab = 9
                                    }
                                )
                                7 -> AlertsScreen(
                                    alerts = alerts,
                                    isLoading = isLoading,
                                    onCreateAlert = { symbol, price, type ->
                                        viewModel.createAlert(symbol, price, type)
                                    },
                                    onDeleteAlert = { alertId ->
                                        viewModel.deleteAlert(alertId)
                                    }
                                )
                                8 -> SettingsScreen(
                                    currentTheme = currentThemeName,
                                    biometricAuthManager = biometricAuthManager,
                                    onThemeChange = { theme ->
                                        currentThemeName = theme
                                        prefs.edit().putString("theme", theme).apply()
                                    },
                                    onLogout = onLogout,
                                    onLogoutAllDevices = onLogoutAllDevices
                                )
                                9 -> {
                                    if (detailLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(appTheme.surface),
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = appTheme.primary)
                                        }
                                    } else {
                                        StockDetailScreen(
                                            quote = selectedQuote,
                                            history = viewModel.quoteHistory.value,
                                            onBackPress = { selectedTab = previousTab },
                                            onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                                            onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } // end CompositionLocalProvider
}
