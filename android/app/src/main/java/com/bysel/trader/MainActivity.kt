package com.bysel.trader
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bysel.trader.security.BiometricAuthManager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.bysel.trader.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.bysel.trader.ui.components.MarketDataStatusBanner
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.repository.AuthRepository
import com.bysel.trader.data.repository.Result
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.navigation.ShortcutActions
import com.bysel.trader.ui.screens.*
import com.bysel.trader.ui.theme.ByselShapes
import com.bysel.trader.ui.theme.ByselTypography
import com.bysel.trader.ui.theme.DEFAULT_THEME_ID
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.getTheme
import com.bysel.trader.ui.theme.getMaterialColorScheme
import com.bysel.trader.ui.theme.isDynamicThemeId
import com.bysel.trader.ui.theme.isLightThemeId
import com.bysel.trader.ui.theme.normalizeThemeId
import com.bysel.trader.ui.theme.toAppTheme
import com.bysel.trader.util.rememberWindowLayoutInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.bysel.trader.viewmodel.TradingViewModel
import com.bysel.trader.viewmodel.TradingViewModelFactory
import com.bysel.trader.ai.LlmDownloadState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : FragmentActivity() {
    private var upiResultCallback: ((Boolean) -> Unit)? = null
    private lateinit var biometricAuthManager: BiometricAuthManager
    private var tradingViewModel: TradingViewModel? = null
    private var isAuthenticated = false



    private lateinit var upiLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    /** Launcher shortcut / notification deep-link; updated from [onNewIntent] when already running. */
    private val pendingShortcutAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install modern splash screen (Material You)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Catch accidental main-thread I/O during startup in debug builds only.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        
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

        pendingShortcutAction.value = intent.getStringExtra(ShortcutActions.EXTRA_ACTION)

        setContent {
            // activity-compose + lifecycle-runtime-compose can leave LocalLifecycleOwner
            // unset on FragmentActivity; provide it explicitly so collectAsStateWithLifecycle
            // does not crash at cold start.
            CompositionLocalProvider(LocalLifecycleOwner provides this@MainActivity) {
            val authRepository = remember { AuthRepository() }
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current
            val isLoggedIn by AuthSessionManager.sessionState.collectAsStateWithLifecycle()
            var wasLoggedIn by remember { mutableStateOf(isLoggedIn) }
            var manualLogoutInProgress by remember { mutableStateOf(false) }
            var activeTradingViewModel by remember { mutableStateOf(tradingViewModel) }
            val shortcutAction by pendingShortcutAction
            val initialTab = remember(shortcutAction) {
                ShortcutActions.tabForAction(shortcutAction)
            }

            // Biometric unlock gate — cleared on background so returning requires unlock again.
            var biometricUnlocked by remember {
                mutableStateOf(!biometricAuthManager.isBiometricEnabled())
            }
            val showLockScreen =
                isLoggedIn && biometricAuthManager.isBiometricEnabled() && !biometricUnlocked

            DisposableEffect(lifecycleOwner, isLoggedIn) {
                val observer = LifecycleEventObserver { _, event ->
                    if (
                        event == Lifecycle.Event.ON_STOP &&
                        AuthSessionManager.hasSession() &&
                        biometricAuthManager.isBiometricEnabled()
                    ) {
                        biometricUnlocked = false
                        isAuthenticated = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            
            LaunchedEffect(isLoggedIn) {
                keepSplashScreen = false // Dismiss splash screen

                if (wasLoggedIn && !isLoggedIn && !manualLogoutInProgress) {
                    isAuthenticated = false
                    biometricUnlocked = false
                    Toast.makeText(this@MainActivity, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show()
                }

                if (!isLoggedIn) {
                    manualLogoutInProgress = false
                    tradingViewModel = null
                    activeTradingViewModel = null
                    viewModelStore.clear()
                    isAuthenticated = false
                    biometricUnlocked = !biometricAuthManager.isBiometricEnabled()
                }
                wasLoggedIn = isLoggedIn
            }

            LaunchedEffect(showLockScreen) {
                if (showLockScreen) {
                    biometricAuthManager.authenticateForAppUnlock(
                        activity = this@MainActivity,
                        onSuccess = {
                            isAuthenticated = true
                            biometricUnlocked = true
                        },
                        // Stay on lock screen — cancel must not sign the user out or kill the process.
                        onCancel = { }
                    )
                }
            }

            LaunchedEffect(isLoggedIn, showLockScreen) {
                if (isLoggedIn && !showLockScreen && activeTradingViewModel == null) {
                    val database = BYSELDatabase.getInstance(applicationContext)
                    val repository = TradingRepository(database)
                    val factory = TradingViewModelFactory(repository)
                    factory.initApplication(application)
                    val createdViewModel = ViewModelProvider(
                        this@MainActivity,
                        factory
                    ).get(TradingViewModel::class.java)
                    tradingViewModel = createdViewModel
                    activeTradingViewModel = createdViewModel
                }
            }

            if (!isLoggedIn) {
                AuthScreen(
                    onAuthenticated = {
                        // Successful login/register unlocks this session; biometric re-locks on background.
                        isAuthenticated = true
                        biometricUnlocked = true
                    }
                )
            } else if (showLockScreen) {
                BiometricLockScreen(
                    onRetry = {
                        biometricAuthManager.authenticateForAppUnlock(
                            activity = this@MainActivity,
                            onSuccess = {
                                isAuthenticated = true
                                biometricUnlocked = true
                            },
                            onCancel = { }
                        )
                    }
                )
            } else if (activeTradingViewModel == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val currentTradingViewModel = activeTradingViewModel
                if (currentTradingViewModel != null) {
                BYSELApp(
                    viewModel = currentTradingViewModel,
                    biometricAuthManager = biometricAuthManager,
                    initialTab = initialTab,
                    pendingShortcutAction = shortcutAction,
                    onShortcutConsumed = { pendingShortcutAction.value = null },
                    onUpiPay = { amount, upiPackageName ->
                        launchUpiPayment(amount, upiPackageName) { success ->
                            if (success) currentTradingViewModel.addFunds(amount)
                        }
                    },
                    onLogout = {
                        manualLogoutInProgress = true
                        scope.launch {
                            val result = authRepository.logout()
                            tradingViewModel = null
                            activeTradingViewModel = null
                            viewModelStore.clear()
                            isAuthenticated = false
                            biometricUnlocked = false
                            val message = if (result is Result.Error) {
                                "Signed out on this device, but the server could not be reached"
                            } else {
                                "Logged out successfully"
                            }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogoutAllDevices = {
                        manualLogoutInProgress = true
                        scope.launch {
                            val result = authRepository.logoutAllDevices()
                            tradingViewModel = null
                            activeTradingViewModel = null
                            viewModelStore.clear()
                            isAuthenticated = false
                            biometricUnlocked = false
                            val message = if (result is Result.Error) {
                                "Signed out here, but other devices may still be signed in"
                            } else {
                                "Logged out from all devices"
                            }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                }
            }
            } // CompositionLocalProvider LocalLifecycleOwner
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShortcutAction.value = intent.getStringExtra(ShortcutActions.EXTRA_ACTION)
    }

    override fun onStart() {
        super.onStart()
        // Single resume entry — avoid onStart+onResume double warmup stampede.
        tradingViewModel?.takeIf { AuthSessionManager.hasSession() }?.onAppForegroundResume()
    }

    override fun onResume() {
        super.onResume()
        // Re-check authentication when app returns from background
        if (AuthSessionManager.hasSession() && biometricAuthManager.isBiometricEnabled() && !isAuthenticated) {
            biometricAuthManager.authenticateForAppUnlock(
                activity = this,
                onSuccess = { isAuthenticated = true },
                onCancel = { }
            )
        }
    }

    override fun onStop() {
        tradingViewModel?.onAppBackgroundPause()
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

/** A trade the AI assistant proposed, held until the user confirms it. */
private data class AiTradeRequest(
    val symbol: String,
    val side: String,
    val quantity: Int
)

@Composable
fun BYSELApp(
    viewModel: TradingViewModel, 
    biometricAuthManager: BiometricAuthManager,
    onUpiPay: (Double, String) -> Unit,
    onLogout: () -> Unit = {},
    onLogoutAllDevices: () -> Unit = {},
    initialTab: Int = 0,
    pendingShortcutAction: String? = null,
    onShortcutConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("bysel_settings", Context.MODE_PRIVATE) }
    var currentThemeName by remember {
        val normalized = normalizeThemeId(prefs.getString("theme", DEFAULT_THEME_ID))
        if (prefs.getString("theme", null) != normalized) {
            prefs.edit().putString("theme", normalized).apply()
        }
        mutableStateOf(normalized)
    }
    val materialScheme = remember(currentThemeName) { getMaterialColorScheme(currentThemeName, context) }
    val appTheme = remember(currentThemeName, materialScheme) {
        if (isDynamicThemeId(currentThemeName)) materialScheme.toAppTheme("Dynamic")
        else getTheme(currentThemeName)
    }

    // Light themes need dark status-bar icons; dark themes need light icons.
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val lightBars = appTheme.surface.luminance() > 0.5f || isLightThemeId(currentThemeName)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBars
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = lightBars
    }

    // Play Core managers for modern app lifecycle
    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }
    val reviewManager = remember { ReviewManagerFactory.create(context) }

    // Onboarding state
    var showOnboarding by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false).not()) }

    var selectedTab by remember { mutableStateOf(initialTab) }
    var previousTab by remember { mutableIntStateOf(0) }

    // Warm start / notification tap: apply deep-link after composition is ready.
    LaunchedEffect(pendingShortcutAction) {
        val action = pendingShortcutAction ?: return@LaunchedEffect
        selectedTab = ShortcutActions.tabForAction(action)
        onShortcutConsumed()
    }

    // Trades suggested by the AI assistant are confirmed here before they reach the broker.
    var pendingAiTrade by remember { mutableStateOf<AiTradeRequest?>(null) }
    var pendingOpenAddFunds by remember { mutableStateOf(false) }
    var showHomeAddFundsDialog by remember { mutableStateOf(false) }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    // Default 5s heatmap poll — 1–2s caused overlapping Yahoo/Render storms.
    var heatmapInterval by remember {
        val saved = prefs.getInt("heatmapInterval", 5_000)
        val migrated = when {
            saved in 1_000..2_000 -> 5_000
            saved in 5_000..10_000 -> saved
            else -> 5_000
        }
        if (migrated != saved) {
            prefs.edit().putInt("heatmapInterval", migrated).apply()
        }
        mutableStateOf(migrated)
    }
    val density = LocalDensity.current
    val windowLayout = rememberWindowLayoutInfo()
    val contentHorizontalPadding = if (windowLayout.isWide) 24.dp else 0.dp
    val edgeThresholdPx = with(density) { 28.dp.toPx() }
    val swipeTriggerPx = with(density) { 110.dp.toPx() }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, 4),
        pageCount = { 5 }
    )
    
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val watchlistSymbols by viewModel.watchlist.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val productActionMessage by viewModel.productActionMessage.collectAsStateWithLifecycle()
    val lastExecutedOrder by viewModel.lastExecutedOrder.collectAsStateWithLifecycle()
    val activeAlertCount = remember(alerts) { alerts.count { it.isActive } }
    val snackbarHostState = remember { SnackbarHostState() }
    // AI & Analytics state
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiLikelyColdStart by viewModel.aiLikelyColdStart.collectAsStateWithLifecycle()
    val onDeviceLlmState by viewModel.onDeviceLlmState.collectAsStateWithLifecycle()
    val portfolioHealth by viewModel.portfolioHealth.collectAsStateWithLifecycle()
    val healthLoading by viewModel.healthLoading.collectAsStateWithLifecycle()
    val marketHeatmap by viewModel.marketHeatmap.collectAsStateWithLifecycle()
    val heatmapLoading by viewModel.heatmapLoading.collectAsStateWithLifecycle()
    val signalLabBuckets by viewModel.signalLabBuckets.collectAsStateWithLifecycle()
    val signalLabBucketsLoading by viewModel.signalLabBucketsLoading.collectAsStateWithLifecycle()
    val selectedQuote by viewModel.selectedQuote.collectAsStateWithLifecycle()
    val detailLoading by viewModel.detailLoading.collectAsStateWithLifecycle()
    val walletBalance by viewModel.walletBalance.collectAsStateWithLifecycle()
    val marketStatus by viewModel.marketStatus.collectAsStateWithLifecycle()
    val lastQuoteUpdateAt by viewModel.lastQuoteUpdateAt.collectAsStateWithLifecycle()
    val investorPortfolios by viewModel.investorPortfolios.collectAsStateWithLifecycle()
    val investorPortfoliosLoading by viewModel.investorPortfoliosLoading.collectAsStateWithLifecycle()
    val investorPortfolioChanges by viewModel.investorPortfolioChanges.collectAsStateWithLifecycle()
    val smartMoneyIdeas by viewModel.smartMoneyIdeas.collectAsStateWithLifecycle()
    val smartMoneyQuarterLabel by viewModel.smartMoneyQuarterLabel.collectAsStateWithLifecycle()
    val investorInsightsLoading by viewModel.investorInsightsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(productActionMessage) {
        val message = productActionMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        viewModel.clearProductActionMessage()
    }

    // Tell the system when Home is usable (TTFD) — cached wallet/holdings/quotes count.
    var reportedFullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(quotes, holdings, walletBalance, showOnboarding) {
        if (reportedFullyDrawn || showOnboarding) return@LaunchedEffect
        val usable = quotes.isNotEmpty() || holdings.isNotEmpty() || walletBalance > 0.0
        if (!usable) return@LaunchedEffect
        kotlinx.coroutines.delay(32)
        (context as? android.app.Activity)?.reportFullyDrawn()
        reportedFullyDrawn = true
    }
    // Fallback so Play/vitals still get a signal if cache is empty on first install.
    LaunchedEffect(showOnboarding) {
        if (reportedFullyDrawn || showOnboarding) return@LaunchedEffect
        kotlinx.coroutines.delay(2_500)
        if (!reportedFullyDrawn) {
            (context as? android.app.Activity)?.reportFullyDrawn()
            reportedFullyDrawn = true
        }
    }

    // Defer Play update check so it does not compete with first Home paint.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3_000)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE,
                    context as androidx.activity.ComponentActivity,
                    1001
                )
            }
        }
    }

    // Request review after positive user interactions
    val requestReview: () -> Unit = {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(context as androidx.activity.ComponentActivity, reviewInfo)
                flow.addOnCompleteListener {
                    // Review flow completed
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab in 0..4 && pagerState.settledPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
        // Soft resync when jumping Home ↔ Trade; skip if we just topped up (avoids stale overwrite).
        if (selectedTab == 0 || selectedTab == 2) {
            viewModel.refreshWallet(force = false)
        }
        if (selectedTab == 6 || selectedTab == 20) {
            viewModel.loadSignalLabBuckets()
        }
        if (selectedTab == 20) {
            if (marketHeatmap == null) {
                viewModel.loadMarketHeatmap()
            }
        }
        if (selectedTab == 21) {
            if (investorPortfolios.isEmpty()) {
                viewModel.loadInvestorPortfolios()
            }
            if (investorPortfolioChanges.isEmpty() || smartMoneyIdeas.isEmpty()) {
                viewModel.loadInvestorPortfolioInsights()
            }
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

            selectedTab in 6..8 || selectedTab in 10..26 -> {
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
        MaterialTheme(
            colorScheme = materialScheme,
            typography = ByselTypography,
            shapes = ByselShapes,
        ) {
            pendingAiTrade?.let { trade ->
                val livePrice = quotes.firstOrNull { it.symbol == trade.symbol }?.last
                val estimate = livePrice?.let { it * trade.quantity }
                AlertDialog(
                    onDismissRequest = { pendingAiTrade = null },
                    title = { Text("${trade.side} ${trade.quantity} ${trade.symbol}?") },
                    text = {
                        Column {
                            if (livePrice != null) {
                                Text("Last traded price: ₹${String.format("%.2f", livePrice)}")
                                Text("Approximate order value: ₹${String.format("%.2f", estimate)}")
                            } else {
                                Text("Live price unavailable — the order will execute at the prevailing market price.")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This order was suggested by the AI assistant. AI analysis can be wrong; " +
                                    "you are responsible for the trade.",
                                fontSize = 12.sp,
                                color = appTheme.textSecondary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.placeOrder(trade.symbol, trade.quantity, trade.side)
                                pendingAiTrade = null
                                selectedTab = 2
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (trade.side == "BUY") appTheme.positive else appTheme.negative
                            )
                        ) {
                            Text("Confirm ${trade.side.lowercase()}")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingAiTrade = null }) { Text("Cancel") }
                    },
                    containerColor = appTheme.card
                )
            }
            if (showHomeAddFundsDialog) {
                AddFundsDialog(
                    onDismiss = { showHomeAddFundsDialog = false },
                    onAddPracticeCredit = { amount ->
                        viewModel.addFunds(amount)
                        showHomeAddFundsDialog = false
                    },
                )
            }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = contentHorizontalPadding),
                    color = appTheme.surface
                ) {
                    Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = appTheme.card,
                                contentColor = appTheme.text,
                                actionColor = appTheme.primary,
                                dismissActionContentColor = appTheme.textSecondary,
                            )
                        }
                    },
                    bottomBar = {
                        // Stock detail (tab 9) is opened on top of another tab, so keep the
                        // originating tab highlighted instead of falling through to "More".
                        val navHighlightTab = if (selectedTab == 9 && previousTab in 0..4) {
                            previousTab
                        } else {
                            selectedTab
                        }
                        NavigationBar(
                            modifier = Modifier.background(appTheme.card),
                            containerColor = appTheme.card
                        ) {
                    // Tab 0: Dashboard
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard", modifier = Modifier.size(22.dp)) },
                        label = { Text("Home", fontSize = 10.sp) },
                        selected = navHighlightTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 1: AI Assistant
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Psychology, contentDescription = "AI", modifier = Modifier.size(22.dp)) },
                        label = { Text("AI", fontSize = 10.sp) },
                        selected = navHighlightTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 2: Trading
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.AttachMoney, contentDescription = "Trade", modifier = Modifier.size(22.dp)) },
                        label = { Text("Trade", fontSize = 10.sp) },
                        selected = navHighlightTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 3: Portfolio
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Portfolio", modifier = Modifier.size(22.dp)) },
                        label = { Text("Portfolio", fontSize = 10.sp) },
                        selected = navHighlightTab == 3,
                        onClick = { selectedTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 4: Heatmap
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.GridView, contentDescription = "Heatmap", modifier = Modifier.size(22.dp)) },
                        label = { Text("Heatmap", fontSize = 10.sp) },
                        selected = navHighlightTab == 4,
                        onClick = { selectedTab = 4 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // Tab 5: More (Search, Alerts, Settings) — badge when price alerts are armed
                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeAlertCount > 0) {
                                        Badge(
                                            containerColor = appTheme.primary,
                                            contentColor = appTheme.onPrimary,
                                        ) {
                                            Text(
                                                text = if (activeAlertCount > 99) "99+" else activeAlertCount.toString(),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = if (activeAlertCount > 0) {
                                        "More, $activeAlertCount active alerts"
                                    } else {
                                        "More"
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                        label = { Text("More", fontSize = 10.sp) },
                        selected = navHighlightTab in 5..26,
                        onClick = { selectedTab = 5 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appTheme.primary,
                            selectedTextColor = appTheme.primary,
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

                                    val canSwipeBack = selectedTab == 9 || selectedTab in 6..8 || selectedTab in 10..26
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(appTheme.surface)
                    ) {
                        // Shown on every tab so a stale price is never mistaken for a live one.
                        MarketDataStatusBanner(
                            lastQuoteUpdateAt = lastQuoteUpdateAt,
                            isMarketOpen = marketStatus?.isOpen
                        )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(edgeGestureModifier)
                    ) {
                        // Swipeable tabs for main 5 tabs (0-4)
                        if (selectedTab in 0..4) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // Don't precompose adjacent tabs (AI/Trade) during Home cold start.
                                beyondBoundsPageCount = 0
                            ) { page ->
                                when (page) {
                                    0 -> DashboardScreen(
                                        holdings = holdings,
                                        quotes = quotes,
                                        isLoading = isLoading,
                                        error = error,
                                        walletBalance = walletBalance,
                                        watchlistSymbols = watchlistSymbols,
                                        onRefresh = { viewModel.refreshQuotes(force = true) },
                                        onTradeClick = { symbol ->
                                            previousTab = selectedTab
                                            viewModel.fetchAndSelectQuote(symbol)
                                            selectedTab = 9
                                        },
                                        onErrorDismiss = { viewModel.clearError() },
                                        onAiClick = { selectedTab = 1 },
                                        marketStatus = marketStatus,
                                        onQuickTradeClick = { symbol ->
                                            viewModel.fetchAndSelectQuote(symbol)
                                            selectedTab = 2
                                        },
                                        onSignalLabClick = { selectedTab = 20 },
                                        onSmartMoneyClick = {
                                            previousTab = selectedTab
                                            selectedTab = 21
                                        },
                                        onAddPracticeFunds = {
                                            showHomeAddFundsDialog = true
                                        },
                                        onPaperBuy = { symbol, qty ->
                                            if (walletBalance <= 0.0) {
                                                showHomeAddFundsDialog = true
                                                Toast.makeText(
                                                    context,
                                                    "Add practice credit before Paper Buy",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                viewModel.placeOrder(symbol, qty, "BUY")
                                            }
                                        },
                                        onPracticeAlert = { symbol, price, alertType ->
                                            viewModel.createAlert(symbol, price, alertType)
                                        },
                                        lastExecutedOrder = lastExecutedOrder,
                                        onPracticeReviewSubmit = { symbol, qty, price, note, setSl, followedPlan ->
                                            viewModel.logPracticeReview(
                                                symbol = symbol,
                                                qty = qty,
                                                price = price,
                                                userNote = note,
                                                setSl = setSl,
                                                followedPlan = followedPlan,
                                            )
                                        },
                                    )
                                    1 -> AiAssistantScreen(
                                        chatHistory = chatHistory,
                                        isLoading = aiLoading,
                                        onSendQuery = { query ->
                                            if (query.lowercase().contains("optimize my portfolio")) viewModel.optimizePortfolio()
                                            else viewModel.askAi(query)
                                        },
                                        onSuggestionClick = { suggestion ->
                                            if (suggestion.lowercase().contains("optimize my portfolio")) viewModel.optimizePortfolio()
                                            else viewModel.askAi(suggestion)
                                        },
                                        onClearChat = { viewModel.clearChatHistory() },
                                        selectedSymbol = selectedQuote?.symbol,
                                        onTradeAction = { symbol, side, qty ->
                                            viewModel.fetchAndSelectQuote(symbol)
                                            pendingAiTrade = AiTradeRequest(symbol, side, qty ?: 1)
                                        },
                                        onAlertAction = { symbol, price, alertType ->
                                            viewModel.createAlert(symbol, price, alertType)
                                        },
                                        onNavigateToStock = { symbol ->
                                            previousTab = selectedTab
                                            viewModel.fetchAndSelectQuote(symbol)
                                            // Warm candles so Stock Detail chart paints quickly.
                                            viewModel.fetchQuoteHistory(symbol, period = "1mo", interval = "1d")
                                            selectedTab = 9
                                        },
                                        onAiFeedback = { query, answer, helpful ->
                                            viewModel.submitAiFeedback(query, answer, helpful)
                                        },
                                        onDeviceLlmState = onDeviceLlmState,
                                        onDownloadModel = { viewModel.downloadOnDeviceModel() },
                                        likelyColdStart = aiLikelyColdStart,
                                        onWarmAi = { viewModel.warmAiBackend() },
                                        isActive = pagerState.currentPage == 1,
                                    )
                                    2 -> TradingScreen(
                                        isLoading = isLoading,
                                        error = error,
                                        walletBalance = walletBalance,
                                        marketStatus = marketStatus,
                                        openAddFundsRequest = pendingOpenAddFunds,
                                        onOpenAddFundsConsumed = { pendingOpenAddFunds = false },
                                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                                        onRefresh = {
                                            viewModel.refreshQuotes(force = true)
                                            viewModel.refreshWallet()
                                            viewModel.refreshMarketStatus()
                                        },
                                        onAddFunds = { amount, _ -> viewModel.addFunds(amount) },
                                        onAddPracticeCredit = { amount -> viewModel.addFunds(amount) },
                                        onErrorDismiss = { viewModel.clearError() },
                                        onTraceSupportLookup = { traceId ->
                                            viewModel.seedTraceLookup(traceId)
                                            viewModel.lookupOrderByTrace(traceId)
                                            selectedTab = 19
                                        },
                                        isActive = pagerState.currentPage == 2,
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
                                        onErrorDismiss = { viewModel.clearError() },
                                        onNavigateToTrade = { selectedTab = 2 }
                                    )
                                    4 -> HeatmapScreen(
                                        heatmap = marketHeatmap,
                                        isLoading = heatmapLoading,
                                        heatmapInterval = heatmapInterval,
                                        isActive = pagerState.currentPage == 4,
                                        onRefresh = { viewModel.loadMarketHeatmap(force = false) },
                                        onForceRefresh = { viewModel.loadMarketHeatmap(force = true) },
                                        onStockClick = { symbol ->
                                            previousTab = selectedTab
                                            viewModel.fetchAndSelectQuote(symbol)
                                            selectedTab = 9
                                        },
                                    )
                                }
                            }
                        } else {
                            // Non-swipeable screens (More, Search, Alerts, Settings, Detail, Achievements)
                            when (selectedTab) {
                                5 -> MoreScreen(
                                    activeAlertCount = activeAlertCount,
                                    onSearchClick = { selectedTab = 6 },
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
                                    onCopilotCenterClick = { selectedTab = 19 },
                                    onSignalLabClick = { selectedTab = 20 },
                                    onInvestorPortfoliosClick = { selectedTab = 21 },
                                    onRiskLabClick = { selectedTab = 22 },
                                    onEarningsCalendarClick = { selectedTab = 23 },
                                    onTradeJournalClick = { selectedTab = 24 },
                                    onWatchlistClick = { selectedTab = 25 },
                                    onMarketCalendarClick = { selectedTab = 26 },
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
                                20 -> SignalLabScreen(
                                    quotes = quotes,
                                    heatmap = marketHeatmap,
                                    backendBuckets = signalLabBuckets,
                                    isLoading = isLoading || heatmapLoading || signalLabBucketsLoading,
                                    onRefresh = {
                                        viewModel.refreshQuotes(force = true)
                                        viewModel.loadMarketHeatmap()
                                        viewModel.loadSignalLabBuckets(force = true)
                                    },
                                    onOpenSymbol = { symbol ->
                                        previousTab = selectedTab
                                        viewModel.fetchAndSelectQuote(symbol)
                                        selectedTab = 9
                                    },
                                )
                                21 -> InvestorPortfoliosScreen(
                                    portfolios = investorPortfolios,
                                    portfolioChanges = investorPortfolioChanges,
                                    ideas = smartMoneyIdeas,
                                    quarterLabel = smartMoneyQuarterLabel,
                                    isLoading = investorPortfoliosLoading || investorInsightsLoading,
                                    onRefresh = {
                                        viewModel.loadInvestorPortfolios()
                                        viewModel.loadInvestorPortfolioInsights()
                                    },
                                    onOpenSymbol = { symbol ->
                                        previousTab = selectedTab
                                        viewModel.fetchAndSelectQuote(symbol)
                                        selectedTab = 9
                                    },
                                )
                                22 -> com.bysel.trader.ui.screens.RiskLabScreen(
                                    viewModel = viewModel,
                                    onBack = { selectedTab = 5 }
                                )
                                23 -> com.bysel.trader.ui.screens.EarningsCalendarScreen(
                                    viewModel = viewModel,
                                    onBack = { selectedTab = 5 }
                                )
                                24 -> com.bysel.trader.ui.screens.TradeJournalScreen(
                                    viewModel = viewModel,
                                    onBack = { selectedTab = 5 }
                                )
                                25 -> WatchlistScreen(
                                    quotes = quotes.filter { quote ->
                                        watchlistSymbols.any { it.equals(quote.symbol, ignoreCase = true) }
                                    },
                                    isLoading = isLoading,
                                    error = error,
                                    onRefresh = { viewModel.refreshQuotes(force = true) },
                                    onQuoteClick = { quote ->
                                        previousTab = selectedTab
                                        viewModel.setSelectedQuote(quote)
                                        selectedTab = 9
                                    },
                                    onErrorDismiss = { viewModel.clearError() }
                                )
                                26 -> MarketCalendarScreen(onBack = { selectedTab = 5 })
                                6 -> SearchScreen(
                                    quotes = quotes,
                                    watchlistSymbols = watchlistSymbols,
                                    backendBuckets = signalLabBuckets,
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
                                    },
                                    onAddToWatchlist = { symbol -> viewModel.addToWatchlist(symbol) },
                                    onRouteClick = { targetTab -> selectedTab = targetTab }
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
                                        val normalized = normalizeThemeId(theme)
                                        if (!isLightThemeId(normalized)) {
                                            prefs.edit().putString("lastDarkTheme", normalized).apply()
                                        }
                                        currentThemeName = normalized
                                        prefs.edit().putString("theme", normalized).apply()
                                    },
                                    heatmapInterval = heatmapInterval,
                                    onHeatmapIntervalChange = { interval ->
                                        val clamped = interval.coerceIn(1_000, 10_000)
                                        heatmapInterval = clamped
                                        prefs.edit().putInt("heatmapInterval", clamped).apply()
                                    },
                                    onLogout = onLogout,
                                    onLogoutAllDevices = onLogoutAllDevices,
                                    onOpenPriceAlerts = {
                                        previousTab = selectedTab
                                        selectedTab = 7
                                    },
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
                                            onOpenTrustCenter = { traceId ->
                                                traceId?.takeIf { it.isNotBlank() }?.let {
                                                    viewModel.seedTraceLookup(it)
                                                    viewModel.lookupOrderByTrace(it)
                                                }
                                                selectedTab = 19
                                            },
                                            onAiQuery = { query ->
                                                viewModel.askAi(query)
                                                selectedTab = 1
                                            },
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
        }
    }
} // end CompositionLocalProvider
}
