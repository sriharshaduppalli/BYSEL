package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

object FirstSession {
    val STARTER_NAMES = listOf("RELIANCE", "TCS", "HDFCBANK", "INFY", "ITC", "SBIN")
    const val STARTER_CREDIT = 25_000.0
    const val PICK_COUNT = 3
}

data class FirstSessionResult(
    val practiceCredit: Double = 0.0,
    val symbols: List<String> = emptyList(),
    val openTrade: Boolean = true,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinish: (FirstSessionResult) -> Unit) {
    var page by remember { mutableStateOf(0) }
    var addCredit by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf("RELIANCE", "TCS", "INFY")) }
    val pages = listOf(
        OnboardingPage(
            title = "Paper practice for NSE",
            description = "Rehearse buys and sells with simulated money. No demat, no UPI, no real rupees.",
        ),
        OnboardingPage(
            title = "Educational answers",
            description = "Ask about a name, the session, or a paper plan. This is not SEBI-registered advice and not a forecast.",
        ),
        OnboardingPage(
            title = "Learn the loop",
            description = "Idea → Practice BUY or alert → review. Your wallet stays at ₹0 until you add practice credit.",
        ),
    )
    val lastIntro = pages.lastIndex
    val creditPage = lastIntro + 1
    val namesPage = lastIntro + 2
    val lastPage = namesPage
    val theme = LocalAppTheme.current

    fun finish() {
        onFinish(
            FirstSessionResult(
                practiceCredit = if (addCredit) FirstSession.STARTER_CREDIT else 0.0,
                symbols = selected.toList().take(FirstSession.PICK_COUNT),
                openTrade = true,
            ),
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (page) {
                in pages.indices -> {
                    Text(
                        text = pages[page].title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pages[page].description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.text,
                        fontSize = 18.sp,
                    )
                }
                creditPage -> {
                    Text(
                        text = "Practice credit",
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Optional simulation cash so you can Practice BUY today. Not real money. Not a loan.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.text,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { addCredit = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (addCredit) theme.primary else theme.mutedSurface,
                            contentColor = if (addCredit) theme.onPrimary else theme.text,
                        ),
                    ) {
                        Text("Add ₹25,000 practice credit", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { addCredit = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (!addCredit) "Selected · start at ₹0" else "Skip — keep wallet at ₹0")
                    }
                }
                namesPage -> {
                    Text(
                        text = "Three names to watch",
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pick ${FirstSession.PICK_COUNT} NSE delivery names for My list. You can change them later.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.text,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FirstSession.STARTER_NAMES.forEach { symbol ->
                            val on = symbol in selected
                            FilterChip(
                                selected = on,
                                onClick = {
                                    selected = if (on) {
                                        if (selected.size <= 1) selected else selected - symbol
                                    } else if (selected.size < FirstSession.PICK_COUNT) {
                                        selected + symbol
                                    } else {
                                        selected
                                    }
                                },
                                label = { Text(symbol) },
                            )
                        }
                    }
                    Text(
                        text = "${selected.size} of ${FirstSession.PICK_COUNT} selected",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(lastPage + 1) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 12.dp else 8.dp)
                            .background(
                                if (i == page) theme.primary else theme.textSecondary,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    if (i < lastPage) Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = {
                    if (page < lastPage) page++ else finish()
                },
                enabled = page != namesPage || selected.size == FirstSession.PICK_COUNT,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
            ) {
                Text(
                    when {
                        page < lastIntro -> "Next"
                        page == creditPage -> "Next"
                        else -> "Open Trade"
                    },
                )
            }
        }
    }
}

data class OnboardingPage(val title: String, val description: String)
