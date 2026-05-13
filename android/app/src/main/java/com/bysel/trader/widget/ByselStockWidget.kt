package com.bysel.trader.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProviders
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import com.bysel.trader.MainActivity
import com.bysel.trader.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ByselStockWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = fetchWidgetData(context)
        provideContent {
            WidgetContent(data = data)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E)))
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            // Header — App name + time
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.Start,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = "BYSEL",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF00B0FF)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = data.lastUpdated,
                    style = TextStyle(color = ColorProvider(Color(0xFF757575)), fontSize = 10.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Portfolio value
            Text(
                text = data.portfolioValue,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            )
            val pnlColor = if (data.pnlPositive) Color(0xFF00C853) else Color(0xFFE53935)
            Text(
                text = data.pnlText,
                style = TextStyle(color = ColorProvider(pnlColor), fontSize = 11.sp)
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // NIFTY 50 level
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.Start) {
                Text(
                    text = "NIFTY 50  ",
                    style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 11.sp)
                )
                Text(
                    text = data.niftyLevel,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "  ${data.niftyChange}",
                    style = TextStyle(
                        color = ColorProvider(if (data.niftyPositive) Color(0xFF00C853) else Color(0xFFE53935)),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Top gainer / loser row
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.Start) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("Top Gainer", style = TextStyle(color = ColorProvider(Color(0xFF757575)), fontSize = 9.sp))
                    Text(data.topGainer, style = TextStyle(color = ColorProvider(Color(0xFF00C853)), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("Top Loser", style = TextStyle(color = ColorProvider(Color(0xFF757575)), fontSize = 9.sp))
                    Text(data.topLoser, style = TextStyle(color = ColorProvider(Color(0xFFE53935)), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }

    private suspend fun fetchWidgetData(context: Context): WidgetData {
        return withContext(Dispatchers.IO) {
            try {
                val api = RetrofitClient.apiService
                val portfolio = api.getPortfolioValue()
                val heatmap = api.getMarketHeatmap()

                val allStocks = heatmap.sectors.flatMap { it.stocks }
                val sortedByChange = allStocks.sortedByDescending { it.pctChange }

                val pnlPositive = portfolio.pnl >= 0

                WidgetData(
                    portfolioValue = "₹${String.format("%,.0f", portfolio.value)}",
                    pnlText = "${if (pnlPositive) "+" else ""}${String.format("%.2f", portfolio.pnlPercent)}% today",
                    pnlPositive = pnlPositive,
                    niftyLevel = String.format("%,.0f", allStocks.firstOrNull()?.price ?: 0.0),
                    niftyChange = "${String.format("%.2f", allStocks.firstOrNull()?.pctChange ?: 0.0)}%",
                    niftyPositive = (allStocks.firstOrNull()?.pctChange ?: 0.0) >= 0,
                    topGainer = sortedByChange.firstOrNull()?.let { s -> "${s.symbol} +${String.format("%.1f", s.pctChange)}%" } ?: "--",
                    topLoser = sortedByChange.lastOrNull()?.let { s -> "${s.symbol} ${String.format("%.1f", s.pctChange)}%" } ?: "--",
                    lastUpdated = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                )
            } catch (e: Exception) {
                WidgetData()
            }
        }
    }
}

data class WidgetData(
    val portfolioValue: String = "--",
    val pnlText: String = "Portfolio P&L",
    val pnlPositive: Boolean = true,
    val niftyLevel: String = "--",
    val niftyChange: String = "0.00%",
    val niftyPositive: Boolean = true,
    val topGainer: String = "--",
    val topLoser: String = "--",
    val lastUpdated: String = "--:--",
)

class ByselWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ByselStockWidget()
}
