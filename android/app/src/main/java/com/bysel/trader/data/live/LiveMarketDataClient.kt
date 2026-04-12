package com.bysel.trader.data.live

import android.util.Log
import com.bysel.trader.BuildConfig
import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.api.RetrofitClient
import com.bysel.trader.data.models.Quote
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min
import kotlin.random.Random
import java.util.UUID

class LiveMarketDataClient(
    private val apiService: BYSELApiService = RetrofitClient.apiService
) {
    private companion object {
        const val TAG = "LiveMarketDataClient"
        const val STALE_STREAM_TIMEOUT_MS = 12_000L
        const val BASE_RECONNECT_DELAY_MS = 800L
        const val MAX_RECONNECT_DELAY_MS = 20_000L
        const val TRACE_HEADER = "X-Trace-Id"
        const val TRACE_QUERY_PARAM = "traceId"
    }

    private val gson = Gson()
    private val truedataToken = BuildConfig.MARKET_TRUEDATA_TOKEN

    fun streamQuotes(symbols: List<String>): Flow<List<Quote>> {
        return when (BuildConfig.MARKET_DATA_PROVIDER.uppercase()) {
            MarketDataProvider.TRUEDATA.name -> streamViaTrueDataWebSocket(symbols)
            else -> streamViaWebSocket(symbols)
        }
    }

    private fun streamViaWebSocket(symbols: List<String>): Flow<List<Quote>> {
        return streamViaReconnectingWebSocket(
            wsUrl = BuildConfig.MARKET_WS_URL,
            symbols = symbols,
            onSocketOpen = { webSocket, normalizedSymbols, traceId ->
                val payload = mapOf(
                    "action" to "subscribe",
                    "symbols" to normalizedSymbols,
                    "traceId" to traceId,
                )
                webSocket.send(gson.toJson(payload))
            },
            includeTraceQuery = true,
            parsePayload = { text -> parseQuotes(text) },
        )
    }

    private fun streamViaTrueDataWebSocket(symbols: List<String>): Flow<List<Quote>> {
        return streamViaReconnectingWebSocket(
            wsUrl = BuildConfig.MARKET_TRUEDATA_WS_URL,
            symbols = symbols,
            onSocketOpen = { webSocket, normalizedSymbols, _ ->
                if (truedataToken.isNotBlank()) {
                    webSocket.send("auth:$truedataToken")
                }
                val subscribe = buildString {
                    append("subscribe:")
                    append(normalizedSymbols.joinToString(","))
                }
                webSocket.send(subscribe)
            },
            includeTraceQuery = false,
            parsePayload = { text ->
                val parsed = parseTrueDataTicks(text)
                if (parsed.isNotEmpty()) parsed else parseQuotes(text)
            },
        )
    }

    private fun streamViaReconnectingWebSocket(
        wsUrl: String,
        symbols: List<String>,
        onSocketOpen: (WebSocket, List<String>, String) -> Unit,
        includeTraceQuery: Boolean,
        parsePayload: (String) -> List<Quote>,
    ): Flow<List<Quote>> = callbackFlow {
        val normalizedSymbols = normalizeSymbols(symbols)
        val streamTraceId = buildStreamTraceId()
        if (normalizedSymbols.isEmpty()) {
            close(IllegalArgumentException("Live quote stream requires at least one symbol"))
            return@callbackFlow
        }

        if (wsUrl.isBlank()) {
            close(IllegalStateException("WebSocket URL is not configured"))
            return@callbackFlow
        }

        var socket: WebSocket? = null
        var isClosing = false
        var reconnectAttempt = 0
        var reconnectJob: kotlinx.coroutines.Job? = null
        var lastMessageAtMs = System.currentTimeMillis()

        suspend fun emitRestSnapshot() {
            runCatching {
                apiService.getQuotes(normalizedSymbols.joinToString(","))
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { quotes ->
                trySend(quotes)
            }
        }

        lateinit var scheduleReconnect: (String) -> Unit
        lateinit var openSocketConnection: () -> Unit

        scheduleReconnect = fun(reason: String) {
            if (isClosing || !isActive) return
            if (reconnectJob?.isActive == true) return

            reconnectAttempt += 1
            val backoffMs = nextReconnectDelayMs(reconnectAttempt)
            Log.w(
                TAG,
                "Stream reconnect scheduled in ${backoffMs}ms (attempt=$reconnectAttempt reason=$reason traceId=$streamTraceId)"
            )

            reconnectJob = launch {
                emitRestSnapshot()
                delay(backoffMs)
                if (!isClosing && isActive) {
                    openSocketConnection()
                }
            }
        }

        openSocketConnection = {
            if (!isClosing && isActive) {
                val resolvedWsUrl = if (includeTraceQuery) {
                    appendTraceQueryIfMissing(wsUrl, streamTraceId)
                } else {
                    wsUrl
                }
                val request = Request.Builder()
                    .url(resolvedWsUrl)
                    .header(TRACE_HEADER, streamTraceId)
                    .build()
                try {
                    socket = RetrofitClient.httpClient.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            reconnectAttempt = 0
                            lastMessageAtMs = System.currentTimeMillis()
                            Log.i(TAG, "Stream socket opened traceId=$streamTraceId url=${request.url}")
                            runCatching { onSocketOpen(webSocket, normalizedSymbols, streamTraceId) }
                                .onFailure { error ->
                                    Log.e(TAG, "Stream subscription failed traceId=$streamTraceId", error)
                                    scheduleReconnect("subscription_failed")
                                }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            lastMessageAtMs = System.currentTimeMillis()
                            val parsed = parsePayload(text)
                            if (parsed.isNotEmpty()) {
                                trySend(parsed)
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (isClosing) return
                            Log.w(TAG, "Stream socket failed traceId=$streamTraceId", t)
                            scheduleReconnect("socket_failure")
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            Log.i(TAG, "Stream socket closed traceId=$streamTraceId code=$code reason=$reason")
                            if (isClosing || code == 1000) return
                            scheduleReconnect("socket_closed_$code")
                        }
                    })
                } catch (error: Exception) {
                    Log.e(TAG, "Stream connection setup failed traceId=$streamTraceId", error)
                    scheduleReconnect("connect_exception")
                }
            }
        }

        val staleWatchdogJob = launch {
            while (isActive && !isClosing) {
                delay(STALE_STREAM_TIMEOUT_MS)
                val staleForMs = System.currentTimeMillis() - lastMessageAtMs
                if (staleForMs >= STALE_STREAM_TIMEOUT_MS) {
                    socket?.cancel()
                    lastMessageAtMs = System.currentTimeMillis()
                    scheduleReconnect("stale_stream")
                }
            }
        }

        launch { emitRestSnapshot() }
        openSocketConnection()

        awaitClose {
            isClosing = true
            reconnectJob?.cancel()
            staleWatchdogJob.cancel()
            socket?.close(1000, "closed")
            socket?.cancel()
        }
    }

    fun pollQuotes(symbols: List<String>, intervalMs: Long = 1000L): Flow<List<Quote>> = flow {
        while (true) {
            val quoteList = apiService.getQuotes(symbols.joinToString(","))
            emit(quoteList)
            delay(intervalMs)
        }
    }

    private fun parseQuotes(text: String): List<Quote> {
        return try {
            val root = JsonParser.parseString(text)
            when {
                root.isJsonArray -> gson.fromJson(text, Array<Quote>::class.java).toList()
                root.isJsonObject && root.asJsonObject.has("quotes") -> {
                    gson.fromJson(root.asJsonObject.get("quotes"), Array<Quote>::class.java).toList()
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTrueDataTicks(text: String): List<Quote> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("{")) {
                parseTrueDataJson(trimmed)
            } else {
                parseTrueDataCsv(trimmed)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTrueDataJson(text: String): List<Quote> {
        val root = JsonParser.parseString(text)
        return when {
            root.isJsonArray -> parseTrueDataJsonArray(root.asJsonArray)
            root.isJsonObject -> parseTrueDataJsonObject(root.asJsonObject)
            else -> emptyList()
        }
    }

    private fun parseTrueDataJsonArray(array: JsonArray): List<Quote> {
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val symbol = obj.optString("symbol") ?: obj.optString("s") ?: return@mapNotNull null
            val last = obj.optDouble("ltp") ?: obj.optDouble("last") ?: obj.optDouble("l") ?: return@mapNotNull null
            val pct = obj.optDouble("pct_change") ?: obj.optDouble("chg_pct") ?: obj.optDouble("pc") ?: 0.0
            Quote(symbol = symbol.uppercase(), last = last, pctChange = pct)
        }
    }

    private fun parseTrueDataJsonObject(obj: JsonObject): List<Quote> {
        if (obj.has("ticks") && obj.get("ticks").isJsonArray) {
            return parseTrueDataJsonArray(obj.getAsJsonArray("ticks"))
        }
        val symbol = obj.optString("symbol") ?: obj.optString("s") ?: return emptyList()
        val last = obj.optDouble("ltp") ?: obj.optDouble("last") ?: obj.optDouble("l") ?: return emptyList()
        val pct = obj.optDouble("pct_change") ?: obj.optDouble("chg_pct") ?: obj.optDouble("pc") ?: 0.0
        return listOf(Quote(symbol = symbol.uppercase(), last = last, pctChange = pct))
    }

    private fun parseTrueDataCsv(text: String): List<Quote> {
        val lines = text.split('\n')
        return lines.mapNotNull { line ->
            val parts = line.split(',').map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val symbol = parts[0].ifBlank { return@mapNotNull null }
            val last = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val pct = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            Quote(symbol = symbol.uppercase(), last = last, pctChange = pct)
        }
    }

    private fun JsonObject.optString(key: String): String? {
        return if (has(key) && !get(key).isJsonNull) get(key).asString else null
    }

    private fun JsonObject.optDouble(key: String): Double? {
        return if (has(key) && !get(key).isJsonNull) get(key).asDouble else null
    }

    private fun normalizeSymbols(symbols: List<String>): List<String> {
        return symbols
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun nextReconnectDelayMs(attempt: Int): Long {
        val exponent = min(attempt, 5)
        val delay = BASE_RECONNECT_DELAY_MS * (1L shl exponent)
        val jitter = Random.nextLong(200L, 900L)
        return min(MAX_RECONNECT_DELAY_MS, delay + jitter)
    }

    private fun buildStreamTraceId(): String {
        val compactUuid = UUID.randomUUID().toString().replace("-", "").take(16)
        return "trc-ws-$compactUuid"
    }

    private fun appendTraceQueryIfMissing(url: String, traceId: String): String {
        if (url.contains("traceId=", ignoreCase = true) || url.contains("trace_id=", ignoreCase = true)) {
            return url
        }
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator$TRACE_QUERY_PARAM=$traceId"
    }
}
