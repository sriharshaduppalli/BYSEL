package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bysel.trader.BuildConfig
import com.bysel.trader.data.models.HistoryCandle

@Composable
fun PriceHistoryChart(
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier
) {
    return when (BuildConfig.CHART_ENGINE.uppercase()) {
        "SCICHART" -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0x1FFFFFFF))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SciChart mode selected. Add SciChart SDK/license to enable native renderer.")
            }
        }
        else -> CandlestickChart(history = history, modifier = modifier)
    }
}
