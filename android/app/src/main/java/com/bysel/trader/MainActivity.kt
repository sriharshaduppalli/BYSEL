package com.bysel.trader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.bysel.trader.data.local.BYSELDatabase
import com.bysel.trader.data.repository.TradingRepository
import com.bysel.trader.ui.screens.WatchlistScreen
import com.bysel.trader.ui.screens.PortfolioScreen
import com.bysel.trader.ui.screens.AlertsScreen
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
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.background(Color(0xFF1E1E1E)),
                    containerColor = Color(0xFF1E1E1E)
                ) {
                    NavigationBarItem(
                        icon = {},
                        label = { Text("Watchlist") },
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
                        icon = {},
                        label = { Text("Portfolio") },
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
                        icon = {},
                        label = { Text("Alerts") },
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
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                when (selectedTab) {
                    0 -> WatchlistScreen(
                        quotes = quotes,
                        isLoading = isLoading,
                        error = error,
                        onRefresh = { viewModel.refreshQuotes() },
                        onQuoteClick = {},
                        onErrorDismiss = { viewModel.clearError() }
                    )
                    1 -> PortfolioScreen(
                        holdings = holdings,
                        quotes = quotes,
                        isLoading = isLoading,
                        error = error,
                        onRefresh = { viewModel.refreshHoldings() },
                        onBuy = { symbol, qty -> viewModel.placeOrder(symbol, qty, "BUY") },
                        onSell = { symbol, qty -> viewModel.placeOrder(symbol, qty, "SELL") },
                        onErrorDismiss = { viewModel.clearError() }
                    )
                    2 -> AlertsScreen(
                        alerts = alerts,
                        isLoading = isLoading,
                        onCreateAlert = { symbol, price, type ->
                            viewModel.createAlert(symbol, price, type)
                        },
                        onDeleteAlert = { alertId ->
                            viewModel.deleteAlert(alertId)
                        }
                    )
                }
            }
        }
    }
}
