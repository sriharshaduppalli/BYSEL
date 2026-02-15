package com.bysel.trader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.ui.screens.*
import com.bysel.trader.viewmodel.TradingViewModel
import com.bysel.trader.viewmodel.TradingViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = BYSELDatabase.getInstance(this)
        val repository = TradingRepository(database)
        val viewModel = ViewModelProvider(
            this,
            TradingViewModelFactory(repository)
        ).get(TradingViewModel::class.java)

        setContent {
            BYSELApp(viewModel)
        }
    }
}

@Composable
fun BYSELApp(viewModel: TradingViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val quotes by viewModel.quotes.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0D0D)
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.background(Color(0xFF1A1A1A)),
                    containerColor = Color(0xFF1A1A1A)
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.AttachMoney, contentDescription = "Trading") },
                        label = { Text("Trading") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.ShowChart, contentDescription = "Portfolio") },
                        label = { Text("Portfolio") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = "Alerts") },
                        label = { Text("Alerts") },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            selectedTextColor = Color.Blue,
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
                    .background(Color(0xFF0D0D0D))
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        holdings = holdings,
                        quotes = quotes,
                        isLoading = isLoading,
                        error = error,
                        onRefresh = { viewModel.refreshQuotes() },
                        onTradeClick = { selectedTab = 1 },
                        onErrorDismiss = { viewModel.clearError() }
                    )
                    1 -> TradingScreen(
                        quotes = quotes,
                        isLoading = isLoading,
                        error = error,
                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                        onRefresh = { viewModel.refreshQuotes() },
                        onErrorDismiss = { viewModel.clearError() }
                    )
                    2 -> PortfolioScreen(
                        holdings = holdings,
                        quotes = quotes,
                        isLoading = isLoading,
                        error = error,
                        onRefresh = { viewModel.refreshHoldings() },
                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                        onErrorDismiss = { viewModel.clearError() }
                    )
                    3 -> AlertsScreen(
                        alerts = alerts,
                        isLoading = isLoading,
                        onCreateAlert = { symbol, price, type ->
                            viewModel.createAlert(symbol, price, type)
                        },
                        onDeleteAlert = { alertId ->
                            viewModel.deleteAlert(alertId)
                        }
                    )
                    4 -> SearchScreen(
                        quotes = quotes,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onSearchQuery = { query -> viewModel.searchStocks(query) },
                        onClearSearch = { viewModel.clearSearchResults() },
                        onQuoteClick = { selectedTab = 1 },
                        onSymbolClick = { symbol -> selectedTab = 1 }
                    )
                    5 -> SettingsScreen()
                }
            }
        }
    }
}
