package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            title = "Paper practice for NSE",
            description = "Rehearse buys and sells with simulated money. No demat, no UPI, no real rupees."
        ),
        OnboardingPage(
            title = "Educational answers",
            description = "Ask about a name, the session, or a paper plan. This is not SEBI-registered advice and not a forecast."
        ),
        OnboardingPage(
            title = "Learn the loop",
            description = "Idea → paper trade or alert → review. Your wallet stays at ₹0 until you add practice credit."
        ),
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LocalAppTheme.current.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pages[page].title,
                style = MaterialTheme.typography.headlineMedium,
                color = LocalAppTheme.current.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = pages[page].description,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalAppTheme.current.text,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(pages.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 12.dp else 8.dp)
                            .background(if (i == page) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary, shape = MaterialTheme.shapes.small)
                            .padding(4.dp)
                    )
                    if (i < pages.size - 1) Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (page < pages.size - 1) page++ else onFinish()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text(if (page < pages.size - 1) "Next" else "Start practicing")
            }
        }
    }
}

data class OnboardingPage(val title: String, val description: String)
