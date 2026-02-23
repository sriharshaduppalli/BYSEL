package com.bysel.trader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bysel.trader.viewmodel.TradingViewModel

@Composable
fun AchievementsScreen(viewModel: TradingViewModel) {
    val achievements = viewModel.achievements.collectAsState().value
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Achievements", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(achievements.size) { i ->
                val a = achievements[i]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (a.unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium)
                        Text(a.description, style = MaterialTheme.typography.bodyMedium)
                        if (a.unlocked) {
                            Text("Unlocked!", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
