package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.allThemes
import com.bysel.trader.ui.theme.getTheme

@Composable
fun ThemeSelectorScreen(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            LocalAppTheme.current.primary.copy(alpha = 0.8f),
                            LocalAppTheme.current.primary
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Choose Theme",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${allThemes.size} themes available",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Theme description
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current: ${currentTheme}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap any theme below to apply instantly. Your selection is saved automatically.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Theme grid
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allThemes) { themeName ->
                ThemePreviewCard(
                    themeName = themeName,
                    isSelected = themeName.equals(currentTheme, ignoreCase = true),
                    onClick = { onThemeSelected(themeName.lowercase()) }
                )
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    themeName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = getTheme(themeName.lowercase())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = LocalAppTheme.current.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(if (isSelected) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Theme name and colors preview
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = themeName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Color palette preview
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorCircle(theme.primary, "Primary")
                    ColorCircle(theme.card, "Card")
                    ColorCircle(theme.positive, "Profit")
                    ColorCircle(theme.negative, "Loss")
                }
            }

            // Right side: Mini stock card preview
            Card(
                modifier = Modifier
                    .width(140.dp)
                    .height(80.dp),
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RELIANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.text
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "₹2,834.50",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.text
                        )
                        Text(
                            text = "+2.3%",
                            fontSize = 10.sp,
                            color = theme.positive
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorCircle(color: Color, label: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
    )
}
