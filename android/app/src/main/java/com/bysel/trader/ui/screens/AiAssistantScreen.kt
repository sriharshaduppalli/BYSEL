package com.bysel.trader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun AiAssistantScreen(
    chatHistory: List<ChatMessage>,
    isLoading: Boolean,
    onSendQuery: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
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
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            if (chatHistory.isNotEmpty()) {
                IconButton(onClick = onClearChat) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear chat",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Chat messages
        if (chatHistory.isEmpty()) {
            // Welcome screen
            WelcomeContent(onSuggestionClick = onSuggestionClick)
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
                        onSuggestionClick = onSuggestionClick
                    )
                }
                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "Ask about any stock...",
                        color = Color.Gray
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF7C4DFF),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFF7C4DFF),
                    focusedContainerColor = Color(0xFF222222),
                    unfocusedContainerColor = Color(0xFF222222)
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (query.isNotBlank()) {
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
                    if (query.isNotBlank()) {
                        onSendQuery(query.trim())
                        query = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = query.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF7C4DFF),
                    disabledContainerColor = Color(0xFF333333)
                )
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf(
        "Should I buy RELIANCE?" to Icons.Filled.TrendingUp,
        "Predict TCS price" to Icons.Filled.Timeline,
        "Compare INFY and TCS" to Icons.Filled.CompareArrows,
        "Best bank stocks" to Icons.Filled.AccountBalance,
        "Analyze HDFCBANK" to Icons.Filled.Analytics,
        "Is SBIN overvalued?" to Icons.Filled.PriceCheck,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Hi! I'm your AI Stock Assistant",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Ask me anything about Indian stocks.\nI can analyze, predict, and compare stocks for you.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
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
            containerColor = Color(0xFF1A1A2E)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFF7C4DFF).copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = Color.White,
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
    onSuggestionClick: (String) -> Unit
) {
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
                containerColor = if (message.isUser) Color(0xFF7C4DFF)
                else Color(0xFF1A1A2E)
            )
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp
            )
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
                                color = Color(0xFF7C4DFF)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A1A2E)
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = Color(0xFF7C4DFF).copy(alpha = 0.3f),
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
                containerColor = Color(0xFF1A1A2E)
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
                            .background(Color(0xFF7C4DFF).copy(alpha = 0.6f))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "AI is thinking...",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
