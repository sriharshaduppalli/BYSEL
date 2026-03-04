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

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            title = "Welcome to BYSEL!",
            description = "Your AI-powered stock trading companion. Let's take a quick tour!"
        ),
        OnboardingPage(
            title = "Live Market Data",
            description = "Track real-time prices, heatmaps, and analytics."
        ),
        OnboardingPage(
            title = "AI Assistant",
            description = "Get instant answers, insights, and trade ideas from our AI."
        ),
        OnboardingPage(
            title = "Demo Account",
            description = "Practice trading with a free demo account."
        ),
        OnboardingPage(
            title = "Seamless Experience",
            description = "Personalize your settings and start trading!"
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
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
                color = Color(0xFF7C4DFF)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = pages[page].description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray,
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
                            .background(if (i == page) Color(0xFF7C4DFF) else Color.LightGray, shape = MaterialTheme.shapes.small)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (page < pages.size - 1) "Next" else "Get Started")
            }
        }
    }
}

data class OnboardingPage(val title: String, val description: String)
