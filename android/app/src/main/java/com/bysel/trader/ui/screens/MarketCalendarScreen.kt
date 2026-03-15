package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.MarketHoliday
import com.bysel.trader.data.models.MarketHolidayCalendar
import com.bysel.trader.ui.theme.LocalAppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun MarketCalendarScreen(onBack: () -> Unit) {
    val upcomingHolidays = remember { MarketHolidayCalendar.getUpcomingHolidays(10) }
    val allHolidays = remember { MarketHolidayCalendar.getAllHolidays() }
    val today = remember { LocalDate.now() }
    val isTradingDay = remember { MarketHolidayCalendar.isTradingDay(today) }
    val nextTradingDay = remember { MarketHolidayCalendar.getNextTradingDay(today) }
    
    var showAllHolidays by remember { mutableStateOf(false) }

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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Market Calendar 2026",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${allHolidays.size} holidays | NSE & BSE",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Market Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isTradingDay) 
                    LocalAppTheme.current.positive.copy(alpha = 0.1f) 
                else 
                    LocalAppTheme.current.negative.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary
                        )
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppTheme.current.text
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTradingDay) 
                                    LocalAppTheme.current.positive 
                                else 
                                    LocalAppTheme.current.negative
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTradingDay) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = LocalAppTheme.current.textSecondary.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = MarketHolidayCalendar.getMarketStatusMessage(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isTradingDay) 
                        LocalAppTheme.current.positive 
                    else 
                        LocalAppTheme.current.negative
                )
                
                if (!isTradingDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Next trading day: ${nextTradingDay.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary
                    )
                }
            }
        }

        // Toggle button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAllHolidays) "All Holidays (${allHolidays.size})" else "Upcoming Holidays (${upcomingHolidays.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppTheme.current.text
            )
            
            Button(
                onClick = { showAllHolidays = !showAllHolidays },
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.primary)
            ) {
                Text(if (showAllHolidays) "Show Upcoming" else "Show All")
            }
        }

        // Holidays List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val holidaysToShow = if (showAllHolidays) allHolidays else upcomingHolidays
            
            items(holidaysToShow) { holiday ->
                HolidayCard(holiday = holiday, isToday = holiday.date == today)
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun HolidayCard(holiday: MarketHoliday, isToday: Boolean) {
    val isPast = holiday.date < LocalDate.now()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = LocalAppTheme.current.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) 
                LocalAppTheme.current.card.copy(alpha = 0.5f) 
            else 
                LocalAppTheme.current.card
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date box
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                LocalAppTheme.current.primary.copy(alpha = 0.8f),
                                LocalAppTheme.current.primary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = holiday.date.dayOfMonth.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = holiday.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Holiday info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = holiday.holidayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = holiday.description,
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                // Day of week and exchanges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = holiday.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                fontSize = 10.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = LocalAppTheme.current.primary.copy(alpha = 0.2f),
                            labelColor = LocalAppTheme.current.primary
                        )
                    )
                    
                    holiday.exchanges.forEach { exchange ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = exchange,
                                    fontSize = 10.sp
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = LocalAppTheme.current.textSecondary.copy(alpha = 0.2f),
                                labelColor = LocalAppTheme.current.textSecondary
                            )
                        )
                    }
                }
            }
            
            // Status icon
            Icon(
                imageVector = if (isPast) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                contentDescription = null,
                tint = if (isPast) 
                    LocalAppTheme.current.textSecondary.copy(alpha = 0.5f) 
                else 
                    LocalAppTheme.current.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
