package com.bysel.trader.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bysel.trader.data.api.ChartPattern
import com.bysel.trader.data.models.HistoryCandle
import org.json.JSONArray
import org.json.JSONObject

private enum class TvOverlay(val label: String) {
    SMA20("SMA 20"),
    SMA50("SMA 50"),
    EMA20("EMA 20"),
    BB("Bollinger"),
    VOLUME("Volume"),
}

private enum class TvInterval(val label: String, val value: String) {
    M1("1m", "1"),
    M5("5m", "5"),
    M15("15m", "15"),
    H1("1h", "60"),
    D1("1D", "D"),
    W1("1W", "W"),
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChart(
    symbol: String,
    history: List<HistoryCandle>,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    patterns: List<ChartPattern> = emptyList(),
) {
    if (history.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) { return }
    }

    var activeOverlays by remember { mutableStateOf(setOf<TvOverlay>()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val candleJson = remember(history) {
        JSONArray().apply {
            history.forEach { c ->
                put(JSONObject().apply {
                    put("time", c.timestamp / 1000)
                    put("open", c.open)
                    put("high", c.high)
                    put("low", c.low)
                    put("close", c.close)
                    put("volume", c.volume)
                })
            }
        }.toString()
    }

    val patternsJson = remember(patterns, history) {
        if (patterns.isEmpty() || history.isEmpty()) return@remember "[]"
        JSONArray().apply {
            patterns.forEach { p ->
                val endIdx = p.endIdx.coerceIn(0, history.size - 1)
                val candle = history[endIdx]
                put(JSONObject().apply {
                    put("time", candle.timestamp / 1000)
                    put("signal", p.signal)
                    put("name", "${p.pattern} (${p.confidence}%)")
                    put("type", p.type)
                })
            }
        }.toString()
    }

    val lastCandle = history.last()
    val priceChange = lastCandle.close - lastCandle.open
    val priceChangePct = if (lastCandle.open != 0.0) (priceChange / lastCandle.open) * 100 else 0.0
    val priceColor = if (priceChange >= 0) Color(0xFF00C853) else Color(0xFFE53935)

    Column(modifier = modifier) {
        // OHLCV header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "O" to lastCandle.open,
                    "H" to lastCandle.high,
                    "L" to lastCandle.low,
                    "C" to lastCandle.close,
                ).forEach { (label, value) ->
                    Text("$label: ${String.format("%.2f", value)}", color = Color.Gray, fontSize = 11.sp)
                }
            }
            Text(
                text = "${if (priceChange >= 0) "+" else ""}${String.format("%.2f", priceChangePct)}%",
                color = priceColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Overlay toggle chips
        LazyRowChips(activeOverlays) { overlay ->
            activeOverlays = if (overlay in activeOverlays) activeOverlays - overlay else activeOverlays + overlay
            webViewRef?.evaluateJavascript("toggleOverlay('${overlay.name}', ${overlay in activeOverlays})", null)
        }

        // TradingView Lightweight Charts WebView
        AndroidView(
            factory = { context ->
                WebView(context).also { wv ->
                    webViewRef = wv
                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    wv.addJavascriptInterface(ChartBridge(), "Android")
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript("loadCandles($candleJson)", null)
                            if (patternsJson != "[]") {
                                view?.evaluateJavascript("showPatterns($patternsJson)", null)
                            }
                        }
                    }
                    wv.loadDataWithBaseURL(null, buildChartHtml(isDarkTheme), "text/html", "UTF-8", null)
                }
            },
            update = { wv ->
                wv.evaluateJavascript("loadCandles($candleJson)", null)
            },
            modifier = Modifier.fillMaxWidth().height(280.dp)
        )
    }
}

@Composable
private fun LazyRowChips(
    activeOverlays: Set<TvOverlay>,
    onToggle: (TvOverlay) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TvOverlay.entries.forEach { overlay ->
            item {
                val selected = overlay in activeOverlays
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(overlay) },
                    label = { Text(overlay.label, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1976D2).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF42A5F5)
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

private class ChartBridge {
    @JavascriptInterface
    fun onCrosshairMove(price: Double, time: Long) {}

    @JavascriptInterface
    fun onBarClick(open: Double, high: Double, low: Double, close: Double, volume: Long) {}
}

private fun buildChartHtml(isDark: Boolean): String {
    val bg = if (isDark) "#121212" else "#FFFFFF"
    val textColor = if (isDark) "#E0E0E0" else "#333333"
    val gridColor = if (isDark) "#2A2A2A" else "#EEEEEE"
    val upColor = "#00C853"
    val downColor = "#E53935"
    val wickUp = "#00C853"
    val wickDown = "#E53935"

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: $bg; overflow: hidden; }
  #chart { width: 100%; height: 100vh; }
</style>
</head>
<body>
<div id="chart"></div>
<script src="https://unpkg.com/lightweight-charts@4.1.3/dist/lightweight-charts.standalone.production.js"></script>
<script>
const chart = LightweightCharts.createChart(document.getElementById('chart'), {
  layout: {
    background: { color: '$bg' },
    textColor: '$textColor',
    fontSize: 11,
  },
  grid: {
    vertLines: { color: '$gridColor' },
    horzLines: { color: '$gridColor' },
  },
  crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
  rightPriceScale: { borderColor: '$gridColor' },
  timeScale: { borderColor: '$gridColor', timeVisible: true },
  handleScroll: true,
  handleScale: true,
});

const candleSeries = chart.addCandlestickSeries({
  upColor: '$upColor',
  downColor: '$downColor',
  wickUpColor: '$wickUp',
  wickDownColor: '$wickDown',
  borderVisible: false,
});

const volumeSeries = chart.addHistogramSeries({
  color: '#26a69a',
  priceFormat: { type: 'volume' },
  priceScaleId: 'volume',
  visible: false,
});

chart.priceScale('volume').applyOptions({
  scaleMargins: { top: 0.8, bottom: 0 },
});

let sma20Series = null, sma50Series = null, ema20Series = null;
let bbUpper = null, bbMiddle = null, bbLower = null;
let allData = [];

function sma(data, period) {
  return data.map((_, i) => {
    if (i < period - 1) return null;
    const slice = data.slice(i - period + 1, i + 1).map(d => d.close);
    const avg = slice.reduce((a, b) => a + b, 0) / period;
    return { time: data[i].time, value: avg };
  }).filter(Boolean);
}

function ema(data, period) {
  const k = 2 / (period + 1);
  const result = [];
  let prev = null;
  data.forEach((d, i) => {
    if (i < period - 1) return;
    if (prev === null) {
      const slice = data.slice(0, period).map(x => x.close);
      prev = slice.reduce((a, b) => a + b, 0) / period;
      result.push({ time: d.time, value: prev });
    } else {
      prev = d.close * k + prev * (1 - k);
      result.push({ time: d.time, value: prev });
    }
  });
  return result;
}

function bollingerBands(data, period = 20, stdDev = 2) {
  const upper = [], middle = [], lower = [];
  data.forEach((_, i) => {
    if (i < period - 1) return;
    const slice = data.slice(i - period + 1, i + 1).map(d => d.close);
    const avg = slice.reduce((a, b) => a + b, 0) / period;
    const variance = slice.reduce((sum, v) => sum + Math.pow(v - avg, 2), 0) / period;
    const sd = Math.sqrt(variance);
    middle.push({ time: data[i].time, value: avg });
    upper.push({ time: data[i].time, value: avg + stdDev * sd });
    lower.push({ time: data[i].time, value: avg - stdDev * sd });
  });
  return { upper, middle, lower };
}

function loadCandles(data) {
  allData = data;
  candleSeries.setData(data);
  volumeSeries.setData(data.map(d => ({ time: d.time, value: d.volume, color: d.close >= d.open ? '#00C85340' : '#E5393540' })));
  chart.timeScale().fitContent();
}

function showPatterns(patterns) {
  const markers = patterns.map(p => {
    const isBuy = p.signal === 'BUY';
    const isSell = p.signal === 'SELL';
    return {
      time: p.time,
      position: isBuy ? 'belowBar' : 'aboveBar',
      color: isBuy ? '#00C853' : isSell ? '#E53935' : '#FFD600',
      shape: isBuy ? 'arrowUp' : isSell ? 'arrowDown' : 'circle',
      text: p.name,
      size: 1,
    };
  });
  markers.sort((a, b) => a.time - b.time);
  candleSeries.setMarkers(markers);
}

function toggleOverlay(name, visible) {
  if (name === 'SMA20') {
    if (!sma20Series) sma20Series = chart.addLineSeries({ color: '#FFD600', lineWidth: 1, priceLineVisible: false });
    sma20Series.applyOptions({ visible });
    if (visible) sma20Series.setData(sma(allData, 20));
  } else if (name === 'SMA50') {
    if (!sma50Series) sma50Series = chart.addLineSeries({ color: '#00B0FF', lineWidth: 1, priceLineVisible: false });
    sma50Series.applyOptions({ visible });
    if (visible) sma50Series.setData(sma(allData, 50));
  } else if (name === 'EMA20') {
    if (!ema20Series) ema20Series = chart.addLineSeries({ color: '#FF6D00', lineWidth: 1, priceLineVisible: false });
    ema20Series.applyOptions({ visible });
    if (visible) ema20Series.setData(ema(allData, 20));
  } else if (name === 'BB') {
    if (!bbUpper) {
      bbUpper = chart.addLineSeries({ color: '#AB47BC', lineWidth: 1, priceLineVisible: false });
      bbMiddle = chart.addLineSeries({ color: '#AB47BC60', lineWidth: 1, lineStyle: 2, priceLineVisible: false });
      bbLower = chart.addLineSeries({ color: '#AB47BC', lineWidth: 1, priceLineVisible: false });
    }
    const bb = bollingerBands(allData);
    [bbUpper, bbMiddle, bbLower].forEach(s => s.applyOptions({ visible }));
    if (visible) { bbUpper.setData(bb.upper); bbMiddle.setData(bb.middle); bbLower.setData(bb.lower); }
  } else if (name === 'VOLUME') {
    volumeSeries.applyOptions({ visible });
  }
}

window.addEventListener('resize', () => {
  chart.resize(window.innerWidth, window.innerHeight);
});
</script>
</body>
</html>
""".trimIndent()
}
