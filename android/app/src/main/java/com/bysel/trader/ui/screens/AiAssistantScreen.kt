package com.bysel.trader.ui.screens
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.bysel.trader.viewmodel.ChatMessage
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.PulsingDots
import com.bysel.trader.ui.components.ConfidenceCard
import com.bysel.trader.ui.components.PredictionReasoningCard
import com.bysel.trader.ui.components.EventRiskCard
import com.bysel.trader.ui.components.SentimentCard
import com.bysel.trader.ui.components.QueryUnderstandingCard
import com.bysel.trader.ui.components.AiChatStyledText
import com.bysel.trader.ui.components.exclusiveHorizontalScroll
import com.bysel.trader.ui.components.ProfitSignal
import com.bysel.trader.ui.components.ProfitSignalCard
import com.bysel.trader.ui.components.ProfitSignalExtractor
import com.bysel.trader.utils.TradeIntentParser
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
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
    /** Return to Stock Detail / prior screen after Trust & Tools AI actions. */
    onNavigateBack: (() -> Unit)? = null,
    backLabel: String? = null,
    likelyColdStart: Boolean = false,
    onWarmAi: () -> Unit = {},
    onAiFeedback: ((query: String, answer: String, helpful: Boolean) -> Unit)? = null,
    /** Only warm while the AI tab is visible — avoids competing with wallet/holdings on Home. */
    isActive: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val appTheme = LocalAppTheme.current
    val adaptiveSuggestions = remember(chatHistory, selectedSymbol) {
        buildAdaptiveSuggestions(selectedSymbol, chatHistory)
    }

    // Keep the free-tier host warm while chat is open (not while merely prefetched off-screen).
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
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
                .background(Brush.horizontalGradient(colors = appTheme.headerGradientColors))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backLabel ?: "Back",
                            tint = appTheme.onPrimary,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(appTheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = appTheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "BYSEL AI Assistant",
                        color = appTheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            !backLabel.isNullOrBlank() -> backLabel
                            onNavigateBack != null -> "Swipe from left edge to go back"
                            else -> "Your smart stock advisor"
                        },
                        color = appTheme.onPrimary.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chatHistory.isNotEmpty()) {
                IconButton(onClick = onClearChat) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear chat",
                        tint = appTheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
            }
        }

        // Simulation/educational disclaimer banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appTheme.tintedSurface(appTheme.primary))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Simulation Mode — Paper trading with real market data. No real money involved.",
                fontSize = 11.sp,
                color = appTheme.text,
                lineHeight = 14.sp
            )
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
                        onNavigateToStock = onNavigateToStock,
                        onAiFeedback = onAiFeedback,
                        priorUserQuery = chatHistory
                            .asReversed()
                            .firstOrNull { it.isUser && it.timestamp <= message.timestamp }
                            ?.text
                            .orEmpty(),
                    )
                }
                if (isLoading) {
                    item {
                        TypingIndicator(likelyColdStart = likelyColdStart)
                    }
                }
            }

            if (adaptiveSuggestions.isNotEmpty() && !inputFocused) {
                AdaptiveSuggestionsStrip(
                    suggestions = adaptiveSuggestions.take(8),
                    onSuggestionClick = onSuggestionClick
                )
            }
        }

        // Composer sits above the keyboard; inner field is tall enough that typed text is not clipped.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .background(appTheme.card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val fieldShape = RoundedCornerShape(16.dp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 140.dp)
                    .onFocusChanged { inputFocused = it.isFocused }
                    .clip(fieldShape)
                    .background(appTheme.surface)
                    .border(1.dp, if (inputFocused) appTheme.primary else appTheme.cardOutline, fieldShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                textStyle = TextStyle(
                    color = appTheme.text,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                cursorBrush = SolidColor(appTheme.primary),
                maxLines = 5,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (query.isNotBlank() && !isLoading) {
                        onSendQuery(query.trim())
                        query = ""
                        focusManager.clearFocus()
                    }
                }),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text(
                                "Ask about any stock...",
                                color = appTheme.textSecondary,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            )
                        }
                        innerTextField()
                    }
                },
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
                    contentColor = appTheme.onPrimary,
                    disabledContainerColor = appTheme.textSecondary.copy(alpha = 0.35f),
                    disabledContentColor = appTheme.textSecondary,
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = appTheme.onPrimary
                )
            }
        }
    }
}

private fun buildSymbolSuggestions(symbol: String): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    "Should I buy $symbol?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Predict $symbol price" to Icons.Filled.Timeline,
    "Analyze $symbol" to Icons.Filled.Analytics,
    "Practice levels for $symbol" to Icons.AutoMirrored.Filled.ShowChart,
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
    "TMPV", "TMCV", "TATASTEEL", "HINDALCO", "JSWSTEEL", "ADANIENT", "ADANIPORTS",
    "HINDPETRO", "ETERNAL", "MOTHERSON", "UNOMINDA", "LTF", "CANBK",
    "ONGC", "BPCL", "IOC", "HINDPETRO", "NTPC", "POWERGRID", "COALINDIA",
    "BHARTIARTL", "ASIANPAINT", "NESTLEIND", "BRITANNIA", "MARICO", "DABUR",
    "HINDUNILVR", "LTIM", "TECHM", "PERSISTENT", "COFORGE", "MPHASIS", "OFSS",
    "ETERNAL", "PAYTM", "NYKAA", "IRCTC", "DMART", "TRENT",
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
    "tata motors" to "TMPV", "tatamotors" to "TMPV",
    "tmpv" to "TMPV", "tmcv" to "TMCV",
    "zomato" to "ETERNAL", "eternal" to "ETERNAL", "irctc" to "IRCTC",
    "hpcl" to "HINDPETRO", "hindustan petroleum" to "HINDPETRO",
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

private fun isSectorThemePrompt(text: String): Boolean {
    val q = text.lowercase()
    val themedNoun = Regex(
        """\b(defence|defense|pharma|banking|fmcg|realty|railway|infra|psu|metal|cement|auto|energy|telecom|insurance)\b"""
    ).containsMatchIn(q) &&
        Regex("""\b(stocks?|sector|theme|names|companies|picks?)\b""").containsMatchIn(q)
    val bestTop = Regex(
        """\b(best|top|good)\s+(bank|pharma|auto|it|defence|defense|energy|fmcg|metal|infra|psu|realty|railway)\b"""
    ).containsMatchIn(q)
    val stocksSuffix = Regex(
        """\b(bank|pharma|auto|it|defence|defense|energy|fmcg|metal|infra|psu|realty|railway)\s+stocks?\b"""
    ).containsMatchIn(q)
    return themedNoun || bestTop || stocksSuffix
}

private fun buildAdaptiveSuggestions(
    selectedSymbol: String?,
    chatHistory: List<ChatMessage>
): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> {
    val userPrompts = chatHistory.filter { it.isUser }.map { it.text.trim() }.filter { it.isNotBlank() }
    val askedPrompts = userPrompts.map { normalizePrompt(it) }.toSet()
    val latestUser = userPrompts.lastOrNull().orEmpty()
    val sectorTheme = isSectorThemePrompt(latestUser)
    // For sector asks, ignore the currently selected quote ticker (avoids Buy INFY after defence).
    val focusSymbol = selectedSymbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() && !sectorTheme }

    val allMentioned = extractMentionedSymbols(userPrompts, focusSymbol)
    val primarySymbol = if (sectorTheme) null else allMentioned.firstOrNull()
    val secondarySymbol = if (sectorTheme) null else allMentioned.drop(1).firstOrNull()

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

    if (sectorTheme) {
        buildSectorThemeSuggestions(latestUser, lastAssistant?.text.orEmpty()).forEach {
            suggestions.add(it)
        }
        val sectorOnly = suggestions
            .asSequence()
            .filterNot { normalizePrompt(it.first) in askedPrompts }
            .distinctBy { normalizePrompt(it.first) }
            .take(8)
            .toList()
        if (sectorOnly.isNotEmpty()) return sectorOnly
    }

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

private fun buildSectorThemeSuggestions(
    query: String,
    answer: String,
): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> {
    val q = query.lowercase()
    val sector = when {
        "defence" in q || "defense" in q -> "defence"
        "pharma" in q -> "pharma"
        "bank" in q -> "bank"
        "auto" in q -> "auto"
        "fmcg" in q -> "FMCG"
        "energy" in q -> "energy"
        "metal" in q -> "metal"
        "infra" in q -> "infra"
        "realty" in q || "real estate" in q -> "realty"
        "railway" in q || "rail" in q -> "railway"
        "psu" in q -> "PSU"
        "it " in q || q.endsWith(" it") || " it stocks" in q || "top it" in q -> "IT"
        else -> "this sector"
    }
    val out = linkedSetOf<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>()
    // Prefer tickers actually listed in the assistant answer.
    val answerTickers = Regex("""\*\*([A-Z][A-Z0-9.&-]{1,14})\*\*""")
        .findAll(answer)
        .map { it.groupValues[1] }
        .distinct()
        .take(3)
        .toList()
    answerTickers.forEach { sym ->
        out.add("Analyze $sym" to Icons.Filled.Analytics)
        out.add("Should I buy $sym?" to Icons.AutoMirrored.Filled.TrendingUp)
    }
    if (answerTickers.size >= 2) {
        out.add(
            "Compare ${answerTickers[0]} and ${answerTickers[1]}" to
                Icons.AutoMirrored.Filled.CompareArrows,
        )
    }
    out.add("Top $sector stocks by momentum" to Icons.AutoMirrored.Filled.TrendingUp)
    out.add("Risks in $sector stocks right now" to Icons.Filled.Warning)
    out.add("Best $sector stock for long-term holding" to Icons.Filled.Analytics)
    return out.toList()
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
    "Is ETERNAL a good buy right now?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Is Infosys a good investment?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I invest in Tata Motors?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I buy ICICIBANK?" to Icons.AutoMirrored.Filled.TrendingUp,
    "Should I invest in BAJFINANCE?" to Icons.AutoMirrored.Filled.TrendingUp,
    // Predict — specific stocks
    "Predict TCS price" to Icons.Filled.Timeline,
    "Predict RELIANCE price" to Icons.Filled.Timeline,
    "Predict WIPRO price" to Icons.Filled.Timeline,
    "Predict SUNPHARMA price" to Icons.Filled.Timeline,
    "Predict TMPV price" to Icons.Filled.Timeline,
    "Predict ICICIBANK price" to Icons.Filled.Timeline,
    "Predict HDFCBANK price next month" to Icons.Filled.Timeline,
    "Predict LUPIN price this quarter" to Icons.Filled.Timeline,
    // Compare — specific pairs
    "Compare INFY and TCS" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare ICICI Bank and HDFC Bank" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TCS with Wipro" to Icons.AutoMirrored.Filled.CompareArrows,
    "Compare TMPV and MARUTI" to Icons.AutoMirrored.Filled.CompareArrows,
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
    "Analyze ETERNAL" to Icons.Filled.Analytics,
    "Analyze TMPV" to Icons.Filled.Analytics,
    "Analyze BAJFINANCE" to Icons.Filled.Analytics,
    // Valuation — specific stocks
    "Is SBIN overvalued?" to Icons.Filled.PriceCheck,
    "Is Wipro undervalued?" to Icons.Filled.PriceCheck,
    "Is TCS fairly valued?" to Icons.Filled.PriceCheck,
    "Is RELIANCE overvalued?" to Icons.Filled.PriceCheck,
    "Is LUPIN fairly valued?" to Icons.Filled.PriceCheck,
    "Is BAJFINANCE overvalued?" to Icons.Filled.PriceCheck,
    "Is ETERNAL worth the current price?" to Icons.Filled.PriceCheck,
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
    "Practice levels for RELIANCE" to Icons.AutoMirrored.Filled.ShowChart,
    "Explain T+1 settlement" to Icons.AutoMirrored.Filled.Help,
    "How should I journal a paper trade?" to Icons.AutoMirrored.Filled.Help,
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
    val theme = LocalAppTheme.current
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
                .background(Brush.radialGradient(colors = theme.headerGradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = theme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Hi! I'm your AI Stock Assistant",
            color = theme.text,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Ask me anything about Indian stocks.\nI can analyze, predict, and compare stocks for you.",
            color = theme.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Or type your own question in the box below.",
            color = theme.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Try asking:",
            color = theme.primary,
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
            modifier = Modifier.exclusiveHorizontalScroll(),
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
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onSuggestionClick: (String) -> Unit,
    onTradeAction: ((symbol: String, side: String, qty: Int?) -> Unit)? = null,
    onAlertAction: ((symbol: String, price: Double?, alertType: String) -> Unit)? = null,
    onNavigateToStock: ((symbol: String) -> Unit)? = null,
    onAiFeedback: ((query: String, answer: String, helpful: Boolean) -> Unit)? = null,
    priorUserQuery: String = "",
) {
    var feedbackSent by remember(message.timestamp) { mutableStateOf<Boolean?>(null) }
    val shareContext = LocalContext.current
    val contextSymbol = message.symbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

    // Parse trade intents from AI responses
    val tradeIntents = remember(message.text, message.isUser, contextSymbol) {
        if (!message.isUser) TradeIntentParser.parse(message.text, contextSymbol) else emptyList()
    }

    // Extract profit signal from AI response text (or synthesize from API symbol/price).
    val profitSignal = remember(
        message.text,
        message.isUser,
        contextSymbol,
        message.lastPrice,
        message.signal,
        message.confidence,
    ) {
        if (message.isUser) return@remember null
        val apiConfidencePct = message.confidence
            ?.takeIf { it > 0.0 }
            ?.let { value ->
                val pct = if (value <= 1.0) value * 100.0 else value
                pct.toInt().coerceIn(1, 99)
            }
        val extracted = ProfitSignalExtractor.extract(message.text, contextSymbol)
        when {
            extracted != null -> {
                val symbol = extracted.symbol.ifBlank { contextSymbol.orEmpty() }
                val signal = message.signal?.takeIf { it.isNotBlank() } ?: extracted.signal
                val entry = when {
                    extracted.entry != null && extracted.entry >= 10.0 -> extracted.entry
                    message.lastPrice != null && message.lastPrice >= 10.0 -> message.lastPrice
                    else -> extracted.entry
                }
                val target = extracted.target?.takeIf { it >= 10.0 }
                    ?: entry?.times(1.05)
                val stopLoss = extracted.stopLoss?.takeIf { it >= 10.0 }
                    ?: entry?.times(0.97)
                extracted.copy(
                    symbol = symbol,
                    signal = signal,
                    entry = entry,
                    target = target,
                    stopLoss = stopLoss,
                    confidence = extracted.confidence ?: apiConfidencePct,
                )
            }
            contextSymbol != null && message.lastPrice != null && message.lastPrice >= 10.0 -> {
                val entry = message.lastPrice
                ProfitSignal(
                    symbol = contextSymbol,
                    signal = message.signal ?: "HOLD",
                    entry = entry,
                    target = entry * 1.05,
                    stopLoss = entry * 0.97,
                    confidence = apiConfidencePct,
                    timeframe = null,
                )
            }
            else -> null
        }
    }

    // Prefer backend-attached symbol over text extraction (avoids OVERALL/TRIM false tickers).
    val actionSymbol = contextSymbol
        ?: profitSignal?.symbol?.takeIf { it.isNotBlank() }
        ?: tradeIntents.firstOrNull { it.action == TradeIntentParser.Action.ANALYZE }?.symbol
        ?: tradeIntents.firstOrNull()?.symbol

    val appTheme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(
                    min = if (message.isUser) 0.dp else 0.dp,
                    max = if (message.isUser) 320.dp else 420.dp,
                )
                .then(
                    if (message.isUser) Modifier else Modifier.fillMaxWidth(0.96f)
                ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    appTheme.primary
                } else {
                    appTheme.card.copy(alpha = 0.96f)
                }
            ),
            border = if (message.isUser) {
                null
            } else {
                BorderStroke(1.dp, appTheme.primary.copy(alpha = 0.22f))
            }
        ) {
            // Long-press select/copy — especially useful for AI answers (share alone isn't enough).
            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    if (!message.isUser) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(appTheme.primary.copy(alpha = 0.7f))
                        )
                    }
                    AiChatStyledText(
                        text = message.text,
                        isUser = message.isUser,
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (message.isUser) 12.dp else 12.dp,
                                end = 14.dp,
                                top = 12.dp,
                                bottom = 14.dp,
                            ),
                    )
                }
            }
        }

        // Source + confidence badge for AI responses
        if (!message.isUser && message.source.isNotBlank()) {
            val sourceLabel = when (message.source.lowercase()) {
                "groq" -> "Groq"
                "gemini" -> "Gemini"
                "indian-stock-llm", "indian-stock-llm-education", "indian-stock-llm-indicator" -> "India grounded"
                "rule-engine" -> "Rules"
                "education" -> "Education"
                "on-device" -> "On-device"
                "small-talk" -> "Quick reply"
                else -> message.source
            }
            val conf = message.confidence?.takeIf { it > 0.0 }?.let {
                " · ${"%.0f".format(it.coerceIn(0.0, 1.0) * 100)}% conf"
            }.orEmpty()
            Text(
                text = "Educational · $sourceLabel$conf",
                fontSize = 10.sp,
                color = LocalAppTheme.current.primary.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }

        // AI disclaimer + feedback for all AI responses
        if (!message.isUser) {
            Text(
                text = "AI analysis is for educational purposes only. Not financial advice. Always do your own research.",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppTheme.current.textSecondary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
            )
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAiFeedback != null && priorUserQuery.isNotBlank()) {
                    Text(
                        text = if (feedbackSent != null) "Thanks for the feedback" else "Helpful?",
                        fontSize = 10.sp,
                        color = LocalAppTheme.current.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    if (feedbackSent == null) {
                        IconButton(
                            onClick = {
                                feedbackSent = true
                                onAiFeedback(priorUserQuery, message.text, true)
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ThumbUp,
                                contentDescription = "Helpful",
                                tint = LocalAppTheme.current.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                feedbackSent = false
                                onAiFeedback(priorUserQuery, message.text, false)
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ThumbDown,
                                contentDescription = "Not helpful",
                                tint = LocalAppTheme.current.textSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        val shareText = buildString {
                            if (priorUserQuery.isNotBlank()) {
                                append("Q: ").append(priorUserQuery.trim()).append("\n\n")
                            }
                            append("A: ").append(message.text.trim())
                            append("\n\n— Shared from BYSEL (educational, not financial advice)")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        shareContext.startActivity(
                            Intent.createChooser(intent, "Share AI answer"),
                        )
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share answer",
                        tint = LocalAppTheme.current.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
                    symbol = actionSymbol.orEmpty(),
                    signal = message.enhancedFeatures.predictionReasoning.signal,
                    whyConfident = message.enhancedFeatures.predictionReasoning.whyConfident.orEmpty(),
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
        if (profitSignal != null && (actionSymbol != null || profitSignal.symbol.isNotBlank())) {
            Spacer(modifier = Modifier.height(8.dp))
            val signalUpper = profitSignal.signal.uppercase()
            val isBearish = signalUpper.contains("SELL")
            val alertPrice = profitSignal.target
                ?: profitSignal.entry?.times(1.02)
                ?: message.lastPrice?.times(1.02)
            val alertType = if (isBearish) "BELOW" else "ABOVE"
            val cardSignal = if (actionSymbol != null && profitSignal.symbol != actionSymbol) {
                profitSignal.copy(symbol = actionSymbol)
            } else {
                profitSignal
            }
            ProfitSignalCard(
                signal = cardSignal,
                onBuy = if (onTradeAction != null && !isBearish) {
                    { onTradeAction.invoke(cardSignal.symbol, "BUY", null) }
                } else null,
                onSetAlert = if (onAlertAction != null) {
                    { onAlertAction.invoke(cardSignal.symbol, alertPrice, alertType) }
                } else null,
                onViewChart = if (onNavigateToStock != null && cardSignal.symbol.isNotBlank()) {
                    { onNavigateToStock.invoke(cardSignal.symbol) }
                } else null,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        } else if (
            !message.isUser &&
            actionSymbol != null &&
            (onTradeAction != null || onAlertAction != null || onNavigateToStock != null)
        ) {
            // Fallback when reply has a symbol but no Entry/Target formatting.
            Spacer(modifier = Modifier.height(8.dp))
            val signalUpper = (message.signal ?: "").uppercase()
            val isBearish = signalUpper.contains("SELL")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onTradeAction != null && !isBearish) {
                    Button(
                        onClick = { onTradeAction.invoke(actionSymbol, "BUY", null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalAppTheme.current.positive,
                            contentColor = LocalAppTheme.current.onPositive,
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Practice buy", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onNavigateToStock != null) {
                    OutlinedButton(
                        onClick = { onNavigateToStock.invoke(actionSymbol) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("View chart", fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (onAlertAction != null) {
                    OutlinedButton(
                        onClick = {
                            onAlertAction.invoke(
                                actionSymbol,
                                message.lastPrice?.times(if (isBearish) 0.98 else 1.02),
                                if (isBearish) "BELOW" else "ABOVE"
                            )
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Set Alert", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // Avoid a second "View chart" when ProfitSignal / symbol row already has one.
        val chartCtaShown = onNavigateToStock != null && actionSymbol != null && (
            (profitSignal != null && profitSignal.symbol.isNotBlank()) ||
                (profitSignal == null)
            )

        // Trade intent action buttons (parsed from AI messages)
        if (tradeIntents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tradeIntents.forEach { intent ->
                    when (intent.action) {
                        TradeIntentParser.Action.BUY -> {
                            Button(
                                onClick = { onTradeAction?.invoke(intent.symbol, "BUY", intent.quantity) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalAppTheme.current.positive,
                                    contentColor = LocalAppTheme.current.onPositive,
                                ),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TradeIntentParser.Action.SELL -> {
                            Button(
                                onClick = { onTradeAction?.invoke(intent.symbol, "SELL", intent.quantity) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalAppTheme.current.negative,
                                    contentColor = LocalAppTheme.current.onNegative,
                                ),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TradeIntentParser.Action.ALERT -> {
                            OutlinedButton(
                                onClick = { onAlertAction?.invoke(intent.symbol, intent.price, intent.alertType ?: "ABOVE") },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(intent.displayText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TradeIntentParser.Action.ANALYZE -> {
                            val duplicateChart = chartCtaShown &&
                                intent.symbol.equals(actionSymbol, ignoreCase = true)
                            if (!duplicateChart) {
                                OutlinedButton(
                                    onClick = { onNavigateToStock?.invoke(intent.symbol) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        intent.displayText.ifBlank { "View chart ${intent.symbol}" },
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
                modifier = Modifier.exclusiveHorizontalScroll(),
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
    var statusText by remember {
        mutableStateOf(
            if (likelyColdStart) "Waking the market server…" else "AI is thinking…"
        )
    }
    LaunchedEffect(likelyColdStart) {
        if (likelyColdStart) {
            kotlinx.coroutines.delay(6_000L)
            statusText = "Still waking — first reply can take longer…"
            kotlinx.coroutines.delay(20_000L)
            statusText = "Finishing analysis…"
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
            PulsingDots(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = LocalAppTheme.current.primary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            statusText,
            color = LocalAppTheme.current.textSecondary,
            fontSize = 12.sp
        )
    }
}
