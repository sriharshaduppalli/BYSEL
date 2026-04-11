package com.bysel.trader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.viewmodel.TradingViewModel

@Composable
fun AchievementsScreen(viewModel: TradingViewModel) {
    val achievements = viewModel.achievements.collectAsState().value
    val appTheme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Achievements",
            style = MaterialTheme.typography.headlineMedium,
            color = appTheme.text,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(achievements.size) { i ->
                val a = achievements[i]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (a.unlocked) appTheme.primary.copy(alpha = 0.14f) else appTheme.card
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            a.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = appTheme.text,
                        )
                        Text(
                            a.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = appTheme.textSecondary,
                        )
                        if (a.unlocked) {
                            Text("Unlocked!", color = appTheme.positive)
                        }
                    }
                }
            }
        }
    }
}
