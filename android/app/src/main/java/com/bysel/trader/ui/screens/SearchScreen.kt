package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.Quote
import com.bysel.trader.data.models.StockSearchResult
import kotlinx.coroutines.delay
import com.bysel.trader.ui.theme.LocalAppTheme

@Composable
fun SearchScreen(
    quotes: List<Quote>,
    searchResults: List<StockSearchResult>,
    isSearching: Boolean,
    onSearchQuery: (String) -> Unit,
    onClearSearch: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onSymbolClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // Debounce search - wait 300ms after typing stops
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            onClearSearch()
            return@LaunchedEffect
        }
        delay(300)
        onSearchQuery(searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 500+ Indian stocks...", color = LocalAppTheme.current.textSecondary) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = LocalAppTheme.current.textSecondary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onClearSearch()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = LocalAppTheme.current.textSecondary)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LocalAppTheme.current.text,
                unfocusedTextColor = LocalAppTheme.current.textSecondary,
                focusedBorderColor = LocalAppTheme.current.primary,
                unfocusedBorderColor = Color(0xFF2A2A2A),
                cursorColor = LocalAppTheme.current.primary
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        if (searchQuery.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF2A2A2A),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Search for stocks",
                        fontSize = 16.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Search by symbol (RELIANCE) or company name (Tata Motors)",
                        fontSize = 12.sp,
                        color = Color(0xFF555555),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LocalAppTheme.current.primary)
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No stocks found for \"$searchQuery\"",
                    fontSize = 16.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            }
        } else {
            // Show result count
            Text(
                "${searchResults.size} stocks found",
                fontSize = 12.sp,
                color = Color(0xFF888888),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { result ->
                    // Check if we have live price data
                    val existingQuote = quotes.find { it.symbol == result.symbol }
                    if (existingQuote != null) {
                        SearchResultCardWithPrice(result, existingQuote) {
                            onQuoteClick(existingQuote)
                        }
                    } else {
                        SearchResultCard(result) {
                            onSymbolClick(result.symbol)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: StockSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = result.name,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
                )
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(32.dp)
                    .wrapContentWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SearchResultCardWithPrice(
    result: StockSearchResult,
    quote: Quote,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Text(
                    text = result.name,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.text,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (quote.pctChange > 0) LocalAppTheme.current.positive else LocalAppTheme.current.negative
                )
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(32.dp)
                        .wrapContentWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
