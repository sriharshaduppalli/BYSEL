package com.bysel.trader
import androidx.compose.material.icons.automirrored.filled.ShowChart

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.ui.screens.*
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.getTheme
import com.bysel.trader.viewmodel.TradingViewModel
import com.bysel.trader.viewmodel.TradingViewModelFactory

class MainActivity : ComponentActivity() {
    private var upiResultCallback: ((Boolean) -> Unit)? = null



    private lateinit var upiLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        upiLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val response = data?.getStringExtra("response") ?: ""
            val success = response.contains("SUCCESS", ignoreCase = true)
            upiResultCallback?.invoke(success)
            upiResultCallback = null
        }

        val database = BYSELDatabase.getInstance(this)
        val repository = TradingRepository(database)
        val viewModel = ViewModelProvider(
            this,
            TradingViewModelFactory(repository)
        ).get(TradingViewModel::class.java)

        setContent {
            BYSELApp(viewModel) { amount, upiPackageName ->
                launchUpiPayment(amount, upiPackageName) { success ->
                    if (success) viewModel.addFunds(amount)
                }
            }
        }
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
fun BYSELApp(viewModel: TradingViewModel, onUpiPay: (Double, String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bysel_settings", Context.MODE_PRIVATE) }
    var currentThemeName by remember { mutableStateOf(prefs.getString("theme", "Default") ?: "Default") }
    val appTheme = remember(currentThemeName) { getTheme(currentThemeName.lowercase()) }

    var selectedTab by remember { mutableStateOf(0) }
    var previousTab by remember { mutableIntStateOf(0) }
    
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

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
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
                        selected = selectedTab in 5..9,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(appTheme.surface)
            ) {
                when (selectedTab) {
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
                        onClearChat = { viewModel.clearChatHistory() }
                    )
                    2 -> TradingScreen(
                        quotes = quotes,
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
                        onErrorDismiss = { viewModel.clearError() }
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
                    5 -> MoreScreen(
                        onSearchClick = { selectedTab = 6 },
                        onAlertsClick = { selectedTab = 7 },
                        onSettingsClick = { selectedTab = 8 }
                    )
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
                        onThemeChange = { theme ->
                            currentThemeName = theme
                            prefs.edit().putString("theme", theme).apply()
                        }
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
                                onBackPress = { selectedTab = previousTab },
                                onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                                onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") }
                            )
                        }
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider
}
