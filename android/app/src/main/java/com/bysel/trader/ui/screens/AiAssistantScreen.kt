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
import com.bysel.trader.ai.LlmDownloadState
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.components.ConfidenceCard
import com.bysel.trader.ui.components.PredictionReasoningCard
import com.bysel.trader.ui.components.EventRiskCard
import com.bysel.trader.ui.components.SentimentCard
import com.bysel.trader.ui.components.QueryUnderstandingCard
import com.bysel.trader.ui.components.ProfitSignalCard
import com.bysel.trader.ui.components.ProfitSignalExtractor
import com.bysel.trader.utils.TradeIntentParser
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
    onNavigateToStock: ((symbol: String) -> Unit)? = null,
    onDeviceLlmState: LlmDownloadState = LlmDownloadState.NotDownloaded,
    onDownloadModel: () -> Unit = {},
    likelyColdStart: Boolean = false,
    onWarmAi: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val appTheme = LocalAppTheme.current
    val adaptiveSuggestions = remember(chatHistory, selectedSymbol) {
        buildAdaptiveSuggestions(selectedSymbol, chatHistory)
    }

    // Keep the free-tier host warm while chat is open.
    LaunchedEffect(Unit) {
        onWarmAi()
        while (true) {
            kotlinx.coroutines.delay(4 * 60_000L)
            onWarmAi()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size, isLoading) {
        if (chatHistory.isNotEmpty() || isLoading) {
            val target = (chatHistory.size + if (isLoading) 1 else 0).coerceAtLeast(1) - 1
            listState.animateScrollToItem(target)
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

        // Simulation/educational disclaimer banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3E0))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📊 Simulation Mode — Paper trading with real market data. No real money involved.",
                fontSize = 11.sp,
                color = Color(0xFF9E6B00),
                lineHeight = 14.sp
            )
        }

        // On-device AI model download banner
        when (onDeviceLlmState) {
            is LlmDownloadState.NotDownloaded -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On-device AI available", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0xFF1B5E20))
                        Text("Download Gemma 2B (~1.4GB) for offline AI — no server needed", fontSize = 11.sp, color = Color(0xFF2E7D32))
                    }
                    TextButton(onClick = onDownloadModel) {
                        Text("Download", fontSize = 12.sp, color = Color(0xFF1B5E20))
                    }
                }
            }
            is LlmDownloadState.Downloading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Downloading AI model… ${onDeviceLlmState.progressPct}%", fontSize = 12.sp, color = Color(0xFF0D47A1))
                    LinearProgressIndicator(
                        progress = { onDeviceLlmState.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = Color(0xFF1565C0),
                    )
                }
            }
            is LlmDownloadState.Initializing -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading AI model into memory…", fontSize = 12.sp, color = Color(0xFF0D47A1))
                }
            }
            is LlmDownloadState.Ready -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("On-device AI active — responses are fully offline", fontSize = 11.sp, color = Color(0xFF1B5E20))
                }
            }
            is LlmDownloadState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AI model error: ${onDeviceLlmState.message.take(60)}", fontSize = 11.sp, color = Color(0xFFB71C1C), modifier = Modifier.weight(1f))
                    TextButton(onClick = onDownloadModel) { Text("Retry", fontSize = 11.sp, color = Color(0xFFB71C1C)) }
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
                items(
                    items = chatHistory,
                    key = { "${it.timestamp}_${it.isUser}" }
                ) { message ->
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
                        TypingIndicator(likelyColdStart = likelyColdStart)
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

private val knownSymbols = setOf(
    "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "WIPRO", "HCLTECH",
    "SBIN", "BAJFINANCE", "KOTAKBANK", "AXISBANK", "MARUTI", "TITAN",
    "SUNPHARMA", "LUPIN", "CIPLA", "DRREDDY", "DIVISLAB", "AUROPHARMA", "TORNTPHARM",
    "TATAMOTORS", "TATASTEEL", "HINDALCO", "JSWSTEEL", "ADANIENT", "ADANIPORTS",
    "ONGC", "BPCL", "IOC", "HPCL", "NTPC", "POWERGRID", "COALINDIA",
    "BHARTIARTL", "ASIANPAINT", "NESTLEIND", "BRITANNIA", "MARICO", "DABUR",
    "HINDUNILVR", "LTIM", "TECHM", "PERSISTENT", "COFORGE", "MPHASIS", "OFSS",
    "ZOMATO", "PAYTM", "NYKAA", "IRCTC", "DMART", "TRENT",
    "BAJAJFINSV", "CHOLAFIN", "MUTHOOTFIN", "SHRIRAMFIN", "JIOFIN",
    "APOLLOHOSP", "MAXHEALTH", "FORTIS", "SBILIFE", "HDFCLIFE", "LICI", "ICICIGI",
    "POLYCAB", "DIXON", "HAVELLS", "VOLTAS", "GRASIM", "ULTRACEMCO",
    "SAIL", "NMDC", "VEDL", "RECLTD", "PFC", "IRFC", "LT", "BHEL", "SIEMENS",
    "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT", "INDUSINDBK", "BANDHANBNK",
    "FEDERALBNK", "IDFCFIRSTB", "AUBANK", "CANBK", "BANKBARODA", "PNB",
    "BIOCON", "UPL", "GODREJCP", "EMAMILTD", "COLPAL", "VBL",
)

private val quickNameMap = mapOf(
    "reliance" to "RELIANCE", "ril" to "RELIANCE",
    "tcs" to "TCS", "tata consultancy" to "TCS",
    "infosys" to "INFY", "infy" to "INFY",
    "hdfc bank" to "HDFCBANK", "hdfc" to "HDFCBANK",
    "icici bank" to "ICICIBANK", "icici" to "ICICIBANK",
    "sbi" to "SBIN", "state bank" to "SBIN",
    "wipro" to "WIPRO", "hcl" to "HCLTECH",
    "bajaj finance" to "BAJFINANCE", "bajaj fin" to "BAJFINANCE",
    "kotak" to "KOTAKBANK", "axis bank" to "AXISBANK",
    "maruti" to "MARUTI", "titan" to "TITAN",
    "sun pharma" to "SUNPHARMA", "lupin" to "LUPIN",
    "cipla" to "CIPLA", "dr reddy" to "DRREDDY",
    "tata motors" to "TATAMOTORS", "tatamotors" to "TATAMOTORS",
    "zomato" to "ZOMATO", "irctc" to "IRCTC",
    "dmart" to "DMART", "airtel" to "BHARTIARTL",
    "ongc" to "ONGC", "ntpc" to "NTPC", "bpcl" to "BPCL",
    "asian paints" to "ASIANPAINT", "nestle" to "NESTLEIND",
    "tech mahindra" to "TECHM", "ltimindtree" to "LTIM",
    "bajaj finserv" to "BAJAJFINSV", "apollohosp" to "APOLLOHOSP",
    "apollo hospital" to "APOLLOHOSP", "polycab" to "POLYCAB",
    "dixon" to "DIXON", "havells" to "HAVELLS",
    "sbi life" to "SBILIFE", "hdfc life" to "HDFCLIFE",
)

private fun extractMentionedSymbols(prompts: List<String>, focusSymbol: String?): List<String> {
    val found = linkedSetOf<String>()
    // Prefer the most recent user prompts first so follow-ups stay on the same stock.
    for (prompt in prompts.asReversed().take(8)) {
        val upper = prompt.uppercase()
        for (sym in knownSymbols) {
            if (Regex("\\b${Regex.escape(sym)}\\b").containsMatchIn(upper)) found.add(sym)
        }
        val lower = prompt.lowercase()
        for ((name, sym) in quickNameMap.entries.sortedByDescending { it.key.length }) {
            if (lower.contains(name)) found.add(sym)
        }
    }
    if (focusSymbol != null) found.add(focusSymbol)
    return found.toList()
}

private fun suggestionMentionsOtherSymbol(text: String, primary: String, secondary: String?): Boolean {
    val upper = text.uppercase()
    for (sym in knownSymbols) {
        if (sym == primary || (secondary != null && sym == secondary)) continue
        if (Regex("\\b${Regex.escape(sym)}\\b").containsMatchIn(upper)) return true
    }
    return false
}

private fun buildAdaptiveSuggestions(
    selectedSymbol: String?,
    chatHistory: List<ChatMessage>
): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> {
    val userPrompts = chatHistory.filter { it.isUser }.map { it.text.trim() }.filter { it.isNotBlank() }
    val askedPrompts = userPrompts.map { normalizePrompt(it) }.toSet()
    val focusSymbol = selectedSymbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

    val allMentioned = extractMentionedSymbols(userPrompts, focusSymbol)
    val primarySymbol = allMentioned.firstOrNull()
    val secondarySymbol = allMentioned.drop(1).firstOrNull()

    // Prefer server follow-ups attached to the latest assistant reply when present.
    val lastAssistant = chatHistory.lastOrNull { !it.isUser }
    val serverFollowUps = lastAssistant?.suggestions.orEmpty()
        .mapNotNull { text ->
            val clean = text.trim()
            if (clean.isBlank()) null
            else clean to Icons.Filled.Lightbulb
        }

    val suggestions = linkedSetOf<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>()
    serverFollowUps.forEach { suggestions.add(it) }

    // Always start with symbol-specific follow-ups if a stock is in focus
    if (primarySymbol != null) {
        buildSymbolSuggestions(primarySymbol).forEach { suggestions.add(it) }
    }

    val recent = userPrompts.takeLast(3).map { it.lowercase() }

    // ── 1. VALUATION context ────────────────────────────────────────────────
    val hasValuation = recent.any { textContainsAny(it, listOf("overvalued", "undervalued", "valuation", "fair value", "pe ratio", "p/e", "expensive", "cheap", "fairly valued", "priced")) }
    if (hasValuation && primarySymbol != null) {
        suggestions.add("What P/E is fair for $primarySymbol vs its history?" to Icons.Filled.PriceCheck)
        suggestions.add("Compare $primarySymbol valuation with sector peers" to Icons.AutoMirrored.Filled.CompareArrows)
        suggestions.add("Is $primarySymbol cheap compared to its 5-year average?" to Icons.Filled.PriceCheck)
        suggestions.add("Price-to-book ratio analysis for $primarySymbol" to Icons.Filled.Analytics)
        if (secondarySymbol != null) {
            suggestions.add("Compare $primarySymbol and $secondarySymbol on P/E and P/B" to Icons.AutoMirrored.Filled.CompareArrows)
        }
    }

    // ── 2. PREDICTION context ───────────────────────────────────────────────
    val hasPrediction = recent.any { textContainsAny(it, listOf("predict", "forecast", "target", "future", "price next", "this quarter", "this month", "outlook", "upside")) }
    if (hasPrediction && primarySymbol != null) {
        suggestions.add("Bull case vs bear case for $primarySymbol" to Icons.Filled.Analytics)
        suggestions.add("$primarySymbol price target for next 3 months" to Icons.Filled.Timeline)
        suggestions.add("What catalysts could drive $primarySymbol higher?" to Icons.AutoMirrored.Filled.TrendingUp)
        suggestions.add("Downside risk for $primarySymbol if market corrects" to Icons.Filled.Warning)
        suggestions.add("Should I buy $primarySymbol after this forecast?" to Icons.AutoMirrored.Filled.TrendingUp)
        suggestions.add("Key support and resistance for $primarySymbol" to Icons.AutoMirrored.Filled.ShowChart)
    }

    // ── 3. COMPARISON context ───────────────────────────────────────────────
    val hasComparison = recent.any { textContainsAny(it, listOf("compare", "versus", " vs ", "better", "which is better", "difference between")) }
    if (hasComparison) {
        if (primarySymbol != null && secondarySymbol != null) {
            suggestions.add("$primarySymbol vs $secondarySymbol — debt and cash flow" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("Which is safer long-term: $primarySymbol or $secondarySymbol?" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("$primarySymbol vs $secondarySymbol on return on equity" to Icons.AutoMirrored.Filled.CompareArrows)
        } else if (primarySymbol != null) {
            suggestions.add("$primarySymbol vs its top competitor" to Icons.AutoMirrored.Filled.CompareArrows)
            suggestions.add("How does $primarySymbol rank in its sector?" to Icons.Filled.Analytics)
        }
    }

    // ── 4. BUY / SELL / ENTRY context ───────────────────────────────────────
    val hasRecommendation = recent.any { textContainsAny(it, listOf("buy", "sell", "should i", "invest", "entry", "accumulate", "hold", "exit", "add")) }
    if (hasRecommendation && primarySymbol != null) {
        suggestions.add("What is the ideal entry price for $primarySymbol?" to Icons.AutoMirrored.Filled.TrendingUp)
        suggestions.add("Should I SIP into $primarySymbol every month?" to Icons.Filled.Payments)
        suggestions.add("Stop-loss and target price for $primarySymbol trade" to Icons.Filled.PriceCheck)
        suggestions.add("Is $primarySymbol good for long-term holding?" to Icons.AutoMirrored.Filled.TrendingUp)
        suggestions.add("At what price should I add more $primarySymbol?" to Icons.AutoMirrored.Filled.TrendingUp)
    }

    // ── 5. TECHNICAL ANALYSIS context ───────────────────────────────────────
    val hasTechnical = recent.any { textContainsAny(it, listOf("technical", "rsi", "macd", "support", "resistance", "trend", "bollinger", "sma", "moving average", "breakout", "chart", "candlestick")) }
    if (hasTechnical && primarySymbol != null) {
        suggestions.add("RSI and MACD signal for $primarySymbol today" to Icons.Filled.Analytics)
        suggestions.add("Is $primarySymbol above its 200-day moving average?" to Icons.AutoMirrored.Filled.ShowChart)
        suggestions.add("Bollinger Bands position for $primarySymbol" to Icons.AutoMirrored.Filled.ShowChart)
        suggestions.add("Key support and resistance levels for $primarySymbol" to Icons.AutoMirrored.Filled.ShowChart)
        suggestions.add("Is $primarySymbol forming a bullish pattern?" to Icons.AutoMirrored.Filled.TrendingUp)
    }

    // ── 6. RISK context ─────────────────────────────────────────────────────
    val hasRisk = recent.any { textContainsAny(it, listOf("risk", "stop loss", "stop-loss", "drawdown", "volatile", "hedge", "safe")) }
    if (hasRisk && primarySymbol != null) {
        suggestions.add("Risk-to-reward ratio for buying $primarySymbol now" to Icons.Filled.PriceCheck)
        suggestions.add("What are the key risks in $primarySymbol?" to Icons.Filled.Warning)
        suggestions.add("How would $primarySymbol perform in a market crash?" to Icons.Filled.Warning)
    }

    // ── 7. EARNINGS / DIVIDEND context ──────────────────────────────────────
    val hasEarnings = recent.any { textContainsAny(it, listOf("earning", "results", "eps", "revenue", "quarter")) }
    if (hasEarnings && primarySymbol != null) {
        suggestions.add("$primarySymbol expected EPS and revenue this quarter" to Icons.Filled.Analytics)
        suggestions.add("$primarySymbol earnings trend — last 4 quarters" to Icons.Filled.Analytics)
        suggestions.add("What to expect from $primarySymbol next results?" to Icons.Filled.Analytics)
    }
    val hasDividend = recent.any { textContainsAny(it, listOf("dividend", "yield", "payout")) }
    if (hasDividend && primarySymbol != null) {
        suggestions.add("$primarySymbol dividend history and next expected payout" to Icons.Filled.Payments)
    }

    // When a stock is in focus, never inject unrelated tickers like INFY/TCS.
    // Peer compares that still mention the focus stock are allowed.
    val contextual = suggestions
        .asSequence()
        .filterNot { normalizePrompt(it.first) in askedPrompts }
        .filter {
            if (primarySymbol == null) true
            else {
                val upper = it.first.uppercase()
                upper.contains(primarySymbol) ||
                    !suggestionMentionsOtherSymbol(it.first, primarySymbol, secondarySymbol)
            }
        }
        .distinctBy { normalizePrompt(it.first) }
        .take(10)
        .toList()

    if (contextual.isNotEmpty()) return contextual

    // Empty chat / no symbol: only then use the default discovery pool.
    return buildDefaultSuggestionPool()
        .filterNot { normalizePrompt(it.first) in askedPrompts }
        .take(8)
        .ifEmpty { buildDefaultSuggestionPool().take(8) }
}

private fun textContainsAny(source: String, keywords: List<String>): Boolean {
    return keywords.any { source.contains(it, ignoreCase = true) }
}

private fun normalizePrompt(text: String): String {
    return text.lowercase().replace(Regex("\\s+"), " ").trim()
}

private fun buildDefaultSuggestionPool(): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    // Buy / Invest — specific stocks
    "Should I buy RELIANCE?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy TCS?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy HDFCBANK?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy SBIN?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy LUPIN?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Is ZOMATO a good buy right now?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Is Infosys a good investment?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I invest in Tata Motors?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy ICICIBANK?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I invest in BAJFINANCE?" to Icons.AutoMirrored.Filled.TrendingUp,
    // Predict — specific stocks
    "Predict TCS price" to Icons.Filled.Timeline,
    "Predict RELIANCE price" to Icons.Filled.Timeline,
    "Predict WIPRO price" to Icons.Filled.Timeline,
    "Predict SUNPHARMA price" to Icons.Filled.Timeline,
    "Predict TATAMOTORS price" to Icons.Filled.Timeline,
    "Predict ICICIBANK price" to Icons.Filled.Timeline,
    "Predict HDFCBANK price next month" to Icons.Filled.Timeline,
    "Predict LUPIN price this quarter" to Icons.Filled.Timeline,
    // Compare — specific pairs
    "Compare INFY and TCS" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare ICICI Bank and HDFC Bank" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TCS with Wipro" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TATAMOTORS and MARUTI" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare SBIN and HDFCBANK" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare SUNPHARMA and DRREDDY" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare LUPIN and CIPLA" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare AXISBANK and ICICIBANK" to Icons.AutoMirrored.Filled.CompareArrows,
    // Analyze — specific stocks
    "Analyze HDFCBANK" to Icons.Filled.Analytics,
    "Analyze Larsen and Toubro" to Icons.Filled.Analytics,
    "Analyze ICICIBANK" to Icons.Filled.Analytics,
    "Analyze WIPRO" to Icons.Filled.Analytics,
    "Analyze SUNPHARMA" to Icons.Filled.Analytics,
    "Analyze LUPIN" to Icons.Filled.Analytics,
    "Analyze ZOMATO" to Icons.Filled.Analytics,
    "Analyze TATAMOTORS" to Icons.Filled.Analytics,
    "Analyze BAJFINANCE" to Icons.Filled.Analytics,
    // Valuation — specific stocks
    "Is SBIN overvalued?" to Icons.Filled.PriceCheck,
    "Is Wipro undervalued?" to Icons.Filled.PriceCheck,
    "Is TCS fairly valued?" to Icons.Filled.PriceCheck,
    "Is RELIANCE overvalued?" to Icons.Filled.PriceCheck,
    "Is LUPIN fairly valued?" to Icons.Filled.PriceCheck,
    "Is BAJFINANCE overvalued?" to Icons.Filled.PriceCheck,
    "Is ZOMATO worth the current price?" to Icons.Filled.PriceCheck,
    // Sector screens — clear general questions (AI handles well)
    "Best bank stocks in India" to Icons.Filled.AccountBalance,
    "Top IT stocks to watch" to Icons.Filled.Analytics,
    "Best pharma stocks" to Icons.Filled.Analytics,
    "Top energy stocks in India" to Icons.Filled.Analytics,
    "Best auto stocks" to Icons.AutoMirrored.Filled.TrendingUp,
    "Top FMCG stocks" to Icons.Filled.Analytics,
    "Best defence stocks" to Icons.Filled.Analytics,
    "Top PSU bank stocks" to Icons.Filled.AccountBalance,
    "Best EV-related stocks in India" to Icons.AutoMirrored.Filled.TrendingUp,
    "Top infrastructure stocks in India" to Icons.Filled.Analytics,
    // Market & Macro — good general AI questions
    "NIFTY 50 market outlook this week" to Icons.AutoMirrored.Filled.ShowChart,
    "Which sector is performing best this quarter?" to Icons.Filled.Analytics,
    "Impact of RBI rate cut on banking stocks" to Icons.Filled.AccountBalance,
    "Effect of rupee weakness on IT stocks" to Icons.Filled.Analytics,
    "Best dividend-paying stocks in NIFTY 50" to Icons.Filled.Payments,
    "Large-cap stocks near their 52-week lows" to Icons.AutoMirrored.Filled.TrendingUp,
    "Which defensive stocks are safe to hold?" to Icons.Filled.Analytics,
    "Is it a good time to invest in IT sector?" to Icons.Filled.Analytics,
    // Educational — clear, focused questions
    "What does RSI above 70 mean?" to Icons.AutoMirrored.Filled.Help,
    "How to read Bollinger Bands?" to Icons.AutoMirrored.Filled.Help,
    "What is a good P/E ratio for Indian stocks?" to Icons.AutoMirrored.Filled.Help,
    "When should I use stop-loss?" to Icons.AutoMirrored.Filled.Help,
    "Explain support and resistance levels" to Icons.AutoMirrored.Filled.Help,
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

        // AI disclaimer for all AI responses
        if (!message.isUser) {
            Text(
                text = "⚠ AI analysis is for educational purposes only. Not financial advice. Always do your own research.",
                fontSize = 9.sp,
                color = LocalAppTheme.current.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
                lineHeight = 12.sp
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
private fun TypingIndicator(likelyColdStart: Boolean = false) {
    var statusText by remember(likelyColdStart) {
        mutableStateOf(if (likelyColdStart) "Connecting to AI…" else "AI is thinking…")
    }
    LaunchedEffect(likelyColdStart) {
        if (likelyColdStart) {
            // Only mention cold-start when the host may actually be asleep.
            kotlinx.coroutines.delay(12_000L)
            statusText = "Server may be waking up — almost there…"
            kotlinx.coroutines.delay(20_000L)
            statusText = "Still working — analyzing market data…"
        } else {
            kotlinx.coroutines.delay(8_000L)
            statusText = "Still working…"
            kotlinx.coroutines.delay(15_000L)
            statusText = "Taking a bit longer — finishing analysis…"
        }
    }

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
            statusText,
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp
        )
    }
}
