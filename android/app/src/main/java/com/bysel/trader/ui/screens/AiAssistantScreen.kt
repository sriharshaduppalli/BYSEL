package com.bysel.trader.ui.screens
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.viewmodel.ChatMessage
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.ConfidenceCard
import com.bysel.trader.ui.components.PredictionReasoningCard
import com.bysel.trader.ui.components.EventRiskCard
import com.bysel.trader.ui.components.SentimentCard
import com.bysel.trader.ui.components.QueryUnderstandingCard
import com.bysel.trader.ui.components.ProfitSignalCard
import com.bysel.trader.ui.components.ProfitSignalExtractor
import com.bysel.trader.utils.TradeIntentParser
import kotlin.random.Random

@Composable
fun AiAssistantScreen(
    chatHistory: List<ChatMessage>,
    isLoading: Boolean,
    onSendQuery: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onClearChat: () -> Unit,
    selectedSymbol: String? = null,
    onTradeAction: ((symbol: String, side: String, qty: Int?) -> Unit)? = null,
    onAlertAction: ((symbol: String, price: Double?, alertType: String) -> Unit)? = null,
    onNavigateToStock: ((symbol: String) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val appTheme = LocalAppTheme.current
    val adaptiveSuggestions = remember(chatHistory, selectedSymbol) {
        buildAdaptiveSuggestions(selectedSymbol, chatHistory)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1A237E), Color(0xFF7C4DFF))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "BYSEL AI Assistant",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Your smart stock advisor",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp
                    )
                }
            }
            if (chatHistory.isNotEmpty()) {
                IconButton(onClick = onClearChat) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear chat",
                        tint = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }

        // Chat messages
        if (chatHistory.isEmpty()) {
            // Welcome screen
            WelcomeContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onSuggestionClick = onSuggestionClick,
                suggestions = adaptiveSuggestions
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(chatHistory) { message ->
                    ChatBubble(
                        message = message,
                        onSuggestionClick = onSuggestionClick,
                        onTradeAction = onTradeAction,
                        onAlertAction = onAlertAction,
                        onNavigateToStock = onNavigateToStock
                    )
                }
                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            if (adaptiveSuggestions.isNotEmpty()) {
                AdaptiveSuggestionsStrip(
                    suggestions = adaptiveSuggestions.take(8),
                    onSuggestionClick = onSuggestionClick
                )
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appTheme.card)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "Ask about any stock...",
                        color = appTheme.textSecondary
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = appTheme.text,
                    unfocusedTextColor = appTheme.text,
                    focusedBorderColor = appTheme.primary,
                    unfocusedBorderColor = appTheme.textSecondary.copy(alpha = 0.35f),
                    cursorColor = appTheme.primary,
                    focusedContainerColor = appTheme.card,
                    unfocusedContainerColor = appTheme.card
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (query.isNotBlank() && !isLoading) {
                        onSendQuery(query.trim())
                        query = ""
                        focusManager.clearFocus()
                    }
                }),
                maxLines = 3,
                singleLine = false
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (query.isNotBlank() && !isLoading) {
                        onSendQuery(query.trim())
                        query = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = query.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = appTheme.primary,
                    disabledContainerColor = appTheme.textSecondary.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
            }
        }
    }

private fun buildSymbolSuggestions(symbol: String): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    "Should I buy $symbol?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Predict $symbol price" to Icons.Filled.Timeline,
    "Analyze $symbol" to Icons.Filled.Analytics,
    "Entry and exit targets for $symbol" to Icons.Filled.PriceCheck,
    "Profit potential for $symbol this quarter" to Icons.Filled.Payments,
    "Risk vs reward for buying $symbol now" to Icons.Filled.Warning,
    "Is $symbol overvalued?" to Icons.Filled.PriceCheck,
    "Technical analysis of $symbol" to Icons.Filled.Analytics,
    "Support and resistance for $symbol" to Icons.AutoMirrored.Filled.ShowChart,
    "What are risks in $symbol now?" to Icons.Filled.Warning,
    "Compare $symbol with peers" to Icons.AutoMirrored.Filled.CompareArrows,
    "Best entry price for $symbol with stop-loss" to Icons.AutoMirrored.Filled.TrendingUp,
)

private fun buildAdaptiveSuggestions(
    selectedSymbol: String?,
    chatHistory: List<ChatMessage>
): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> {
    val userPrompts = chatHistory.filter { it.isUser }.map { it.text.trim() }.filter { it.isNotBlank() }
    val askedPrompts = userPrompts.map { normalizePrompt(it) }.toSet()
    val focusSymbol = selectedSymbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

    val suggestions = linkedSetOf<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>()

    if (focusSymbol != null) {
        buildSymbolSuggestions(focusSymbol).forEach { suggestions.add(it) }
    }

    val recent = userPrompts.takeLast(6).map { it.lowercase() }
    val hasValuation = recent.any { textContainsAny(it, listOf("overvalued", "undervalued", "valuation", "fair value", "expensive", "cheap")) }
    val hasPrediction = recent.any { textContainsAny(it, listOf("predict", "forecast", "target", "future")) }
    val hasComparison = recent.any { textContainsAny(it, listOf("compare", "versus", "vs", "better")) }
    val hasRecommendation = recent.any { textContainsAny(it, listOf("buy", "sell", "should i", "invest")) }
    val hasTechnical = recent.any { textContainsAny(it, listOf("technical", "rsi", "macd", "support", "resistance", "trend")) }

    if (hasValuation) {
        if (focusSymbol != null) {
            suggestions.add("Compare $focusSymbol valuation with peers" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("What can justify $focusSymbol current valuation?" to Icons.AutoMirrored.Filled.Help)
        } else {
            suggestions.add("Which IT stocks are undervalued now?" to Icons.Filled.PriceCheck)
            suggestions.add("Best undervalued bank stocks" to Icons.Filled.PriceCheck)
        }
    }

    if (hasPrediction) {
        if (focusSymbol != null) {
            suggestions.add("3-month outlook for $focusSymbol" to Icons.Filled.Timeline)
            suggestions.add("Bull case vs bear case for $focusSymbol" to Icons.Filled.Analytics)
        } else {
            suggestions.add("Predict INFY price next month" to Icons.Filled.Timeline)
            suggestions.add("Predict RELIANCE price this quarter" to Icons.Filled.Timeline)
        }
    }

    if (hasComparison) {
        if (focusSymbol != null) {
            suggestions.add("Compare $focusSymbol with sector leader" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("$focusSymbol vs peers on growth and margins" to Icons.AutoMirrored.Filled.CompareArrows)
        } else {
            suggestions.add("Compare TCS and INFY by valuation" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("Compare HDFCBANK and ICICIBANK" to Icons.AutoMirrored.Filled.CompareArrows)
        }
    }

    if (hasRecommendation) {
        if (focusSymbol != null) {
            suggestions.add("When is a good entry price for $focusSymbol?" to Icons.AutoMirrored.Filled.TrendingUp)
            suggestions.add("Should I SIP into $focusSymbol?" to Icons.Filled.Payments)
        } else {
            suggestions.add("Top stocks to accumulate this month" to Icons.AutoMirrored.Filled.TrendingUp)
            suggestions.add("Best stocks for medium-term investing" to Icons.AutoMirrored.Filled.TrendingUp)
        }
    }

    if (hasTechnical) {
        if (focusSymbol != null) {
            suggestions.add("RSI and MACD view for $focusSymbol" to Icons.Filled.Analytics)
            suggestions.add("Breakout setup check for $focusSymbol" to Icons.AutoMirrored.Filled.ShowChart)
        } else {
            suggestions.add("Technical setup for NIFTY IT stocks" to Icons.Filled.Analytics)
            suggestions.add("Stocks near breakout today" to Icons.AutoMirrored.Filled.ShowChart)
        }
    }

    // Profit / Money / Portfolio related context
    val hasProfit = recent.any { textContainsAny(it, listOf("profit", "gain", "return", "earning", "money", "portfolio", "optimize", "loss", "rebalance")) }
    if (hasProfit) {
        suggestions.add("Optimize my portfolio for maximum returns" to Icons.Filled.Payments)
        suggestions.add("Which of my holdings should I exit?" to Icons.Filled.Warning)
        suggestions.add("Best stocks for quick 10% gains" to Icons.AutoMirrored.Filled.TrendingUp)
        if (focusSymbol != null) {
            suggestions.add("Set profit target and stop-loss for $focusSymbol" to Icons.Filled.PriceCheck)
        }
    }

    val fallbackPool = buildDefaultSuggestionPool().shuffled(Random(System.currentTimeMillis()))
    for (item in fallbackPool) {
        if (suggestions.size >= 14) break
        suggestions.add(item)
    }

    return suggestions
        .filterNot { normalizePrompt(it.first) in askedPrompts }
        .take(12)
        .ifEmpty { fallbackPool.take(8) }
}

private fun textContainsAny(source: String, keywords: List<String>): Boolean {
    return keywords.any { source.contains(it, ignoreCase = true) }
}

private fun normalizePrompt(text: String): String {
    return text.lowercase().replace(Regex("\\s+"), " ").trim()
}

private fun buildDefaultSuggestionPool(): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    // Profit & Action-oriented (NEW)
    "Best stocks for quick 10% gains" to Icons.AutoMirrored.Filled.TrendingUp,
    "Top momentum stocks for swing trading" to Icons.AutoMirrored.Filled.TrendingUp,
    "Stocks near 52-week low with strong fundamentals" to Icons.Filled.PriceCheck,
    "Best entry points for blue chips today" to Icons.Filled.Payments,
    "Dividend stocks paying this month" to Icons.Filled.Payments,
    "Optimize my portfolio for max returns" to Icons.Filled.Analytics,
    "Which of my holdings should I exit?" to Icons.Filled.Warning,
    "Safest stocks to park money right now" to Icons.Filled.AccountBalance,
    // Buy / Invest
    "Should I buy RELIANCE?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy TCS?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy HDFCBANK?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy SBIN?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Is Infosys a good investment?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I invest in Tata Motors?" to Icons.AutoMirrored.Filled.TrendingUp,
    // Predict
    "Predict TCS price" to Icons.Filled.Timeline,
    "Predict RELIANCE price" to Icons.Filled.Timeline,
    "Predict WIPRO price" to Icons.Filled.Timeline,
    "Predict SUNPHARMA price" to Icons.Filled.Timeline,
    "Predict TATAMOTORS price" to Icons.Filled.Timeline,
    "Predict ICICIBANK price" to Icons.Filled.Timeline,
    // Compare
    "Compare INFY and TCS" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare ICICI Bank and HDFC Bank" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TCS with Wipro" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TATAMOTORS and MARUTI" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare SBIN and HDFCBANK" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare SUNPHARMA and DRREDDY" to Icons.AutoMirrored.Filled.CompareArrows,
    // Sector screens
    "Best bank stocks" to Icons.Filled.AccountBalance,
    "Top IT stocks" to Icons.Filled.Analytics,
    "Best pharma stocks" to Icons.Filled.Analytics,
    "Top energy stocks" to Icons.Filled.Bolt,
    "Best auto stocks" to Icons.AutoMirrored.Filled.TrendingUp,
    "Top FMCG stocks" to Icons.Filled.Analytics,
    "Best defence stocks" to Icons.Filled.Analytics,
    // Analyze
    "Analyze HDFCBANK" to Icons.Filled.Analytics,
    "Analyze Larsen and Toubro" to Icons.Filled.Analytics,
    "Analyze ICICIBANK" to Icons.Filled.Analytics,
    "Analyze WIPRO" to Icons.Filled.Analytics,
    "Analyze SUNPHARMA" to Icons.Filled.Analytics,
    // Overvaluation
    "Is SBIN overvalued?" to Icons.Filled.PriceCheck,
    "Is Wipro undervalued?" to Icons.Filled.PriceCheck,
    "Is TCS fairly valued?" to Icons.Filled.PriceCheck,
    "Is RELIANCE overvalued?" to Icons.Filled.PriceCheck,
)

@Composable
private fun WelcomeContent(
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit,
    suggestions: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // AI Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF7C4DFF), Color(0xFF1A237E))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = LocalAppTheme.current.text,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Hi! I'm your AI Stock Assistant",
            color = LocalAppTheme.current.text,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Ask me anything about Indian stocks.\nI can analyze, predict, and compare stocks for you.",
            color = LocalAppTheme.current.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Or type your own question in the box below.",
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Try asking:",
            color = Color(0xFF7C4DFF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        suggestions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (text, icon) ->
                    SuggestionChip(
                        text = text,
                        icon = icon,
                        modifier = Modifier.weight(1f),
                        onClick = { onSuggestionClick(text) }
                    )
                }
                // Fill remaining space if odd number
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AdaptiveSuggestionsStrip(
    suggestions: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "Suggested next prompts",
            color = LocalAppTheme.current.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(suggestions) { (text, icon) ->
                SuggestionChip(
                    text = text,
                    icon = icon,
                    onClick = { onSuggestionClick(text) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalAppTheme.current.card
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, LocalAppTheme.current.primary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = LocalAppTheme.current.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = LocalAppTheme.current.text,
                fontSize = 12.sp,
                maxLines = 2,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onSuggestionClick: (String) -> Unit,
    onTradeAction: ((symbol: String, side: String, qty: Int?) -> Unit)? = null,
    onAlertAction: ((symbol: String, price: Double?, alertType: String) -> Unit)? = null,
    onNavigateToStock: ((symbol: String) -> Unit)? = null
) {
    // Parse trade intents from AI responses
    val tradeIntents = remember(message.text, message.isUser) {
        if (!message.isUser) TradeIntentParser.parse(message.text) else emptyList()
    }

    // Extract profit signal from AI response text
    val profitSignal = remember(message.text, message.isUser) {
        if (!message.isUser) ProfitSignalExtractor.extract(message.text) else null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) LocalAppTheme.current.primary else LocalAppTheme.current.card
            )
        ) {
            Text(
                text = message.text,
                color = if (message.isUser) Color.White else LocalAppTheme.current.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp
            )
        }

        // Source badge for AI responses
        if (!message.isUser && message.source == "gemini") {
            Text(
                text = "✦ Gemini",
                fontSize = 10.sp,
                color = Color(0xFF7C4DFF).copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }

        // Enhanced AI components (only for AI responses with enhanced features)
        if (!message.isUser && message.enhancedFeatures != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Query Understanding
                QueryUnderstandingCard(
                    queryUnderstanding = message.enhancedFeatures.queryUnderstanding,
                    modifier = Modifier.fillMaxWidth()
                )

                // Confidence Breakdown
                ConfidenceCard(
                    overallConfidence = message.enhancedFeatures.confidenceBreakdown.overallConfidence,
                    confidenceLevel = message.enhancedFeatures.confidenceBreakdown.confidenceLevel,
                    factors = message.enhancedFeatures.confidenceBreakdown.factors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Prediction Reasoning
                PredictionReasoningCard(
                    symbol = "", // Will be filled from context
                    signal = message.enhancedFeatures.predictionReasoning.signal,
                    whyConfident = message.enhancedFeatures.predictionReasoning.whyConfident,
                    caveats = message.enhancedFeatures.predictionReasoning.caveats,
                    modifier = Modifier.fillMaxWidth()
                )

                // Event Risk Analysis (if available)
                message.enhancedFeatures.eventRiskAnalysis?.let { eventRisk ->
                    EventRiskCard(
                        baseConfidence = eventRisk.baseConfidence,
                        adjustedConfidence = eventRisk.adjustedConfidence,
                        adjustmentFactor = eventRisk.adjustmentFactor,
                        eventRisks = eventRisk.eventRisks,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Sentiment Analysis
                SentimentCard(
                    overallSentiment = message.enhancedFeatures.sentimentAnalysis.overallSentiment,
                    score = message.enhancedFeatures.sentimentAnalysis.score,
                    strength = message.enhancedFeatures.sentimentAnalysis.strength,
                    breakdown = message.enhancedFeatures.sentimentAnalysis.breakdown,
                    interpretation = message.enhancedFeatures.sentimentAnalysis.interpretation,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Profit Signal Card (extracted from AI response text)
        if (profitSignal != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ProfitSignalCard(
                signal = profitSignal,
                onBuy = if (onTradeAction != null && profitSignal.symbol.isNotBlank()) {
                    { onTradeAction.invoke(profitSignal.symbol, "BUY", null) }
                } else null,
                onSetAlert = if (onAlertAction != null && profitSignal.symbol.isNotBlank()) {
                    { onAlertAction.invoke(profitSignal.symbol, profitSignal.target, "ABOVE") }
                } else null,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }

        // Trade intent action buttons (parsed from AI messages)
        if (tradeIntents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                tradeIntents.forEach { intent ->
                    when (intent.action) {
                        TradeIntentParser.Action.BUY -> {
                            Button(
                                onClick = { onTradeAction?.invoke(intent.symbol, "BUY", intent.quantity) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp, color = Color.White)
                            }
                        }
                        TradeIntentParser.Action.SELL -> {
                            Button(
                                onClick = { onTradeAction?.invoke(intent.symbol, "SELL", intent.quantity) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp, color = Color.White)
                            }
                        }
                        TradeIntentParser.Action.ALERT -> {
                            OutlinedButton(
                                onClick = { onAlertAction?.invoke(intent.symbol, intent.price, intent.alertType ?: "ABOVE") },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp)
                            }
                        }
                        TradeIntentParser.Action.ANALYZE -> {
                            OutlinedButton(
                                onClick = { onNavigateToStock?.invoke(intent.symbol) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("View ${intent.symbol}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Suggestions after AI response
        if (!message.isUser && message.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(message.suggestions) { suggestion ->
                    AssistChip(
                        onClick = { onSuggestionClick(suggestion) },
                        label = {
                            Text(
                                suggestion,
                                fontSize = 11.sp,
                                color = LocalAppTheme.current.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = LocalAppTheme.current.card
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = LocalAppTheme.current.primary.copy(alpha = 0.3f),
                            enabled = true
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = LocalAppTheme.current.card
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LocalAppTheme.current.primary.copy(alpha = 0.6f))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "AI is thinking...",
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp
        )
    }
}
