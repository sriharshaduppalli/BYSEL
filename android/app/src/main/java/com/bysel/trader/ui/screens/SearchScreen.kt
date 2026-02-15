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

@Composable
fun SearchScreen(
    quotes: List<Quote>,
    onQuoteClick: (Quote) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredQuotes = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        quotes.filter {
            it.symbol.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(16.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search stocks...", color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.Gray,
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color(0xFF2A2A2A),
                cursorColor = Color.Blue
            ),
            shape = RoundedCornerShape(10.dp)
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
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else if (filteredQuotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No stocks found",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredQuotes) { quote ->
                    SearchResultCard(quote) {
                        onQuoteClick(quote)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(quote: Quote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
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
                    text = quote.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "₹${String.format("%.2f", quote.last)}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (quote.pctChange > 0) "+" else ""}${String.format("%.2f", quote.pctChange)}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (quote.pctChange > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                )
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(32.dp)
                        .wrapContentWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
