package com.bysel.trader.data.live

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
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class LiveMarketDataClient(
    private val apiService: BYSELApiService = RetrofitClient.apiService
) {
    private val gson = Gson()
    private val truedataToken = BuildConfig.MARKET_TRUEDATA_TOKEN

    fun streamQuotes(symbols: List<String>): Flow<List<Quote>> {
        return when (BuildConfig.MARKET_DATA_PROVIDER.uppercase()) {
            MarketDataProvider.TRUEDATA.name -> streamViaTrueDataWebSocket(symbols)
            else -> streamViaWebSocket(symbols)
        }
    }

    private fun streamViaWebSocket(symbols: List<String>): Flow<List<Quote>> = callbackFlow {
        val wsUrl = BuildConfig.MARKET_WS_URL
        val request = Request.Builder().url(wsUrl).build()
        var socket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = mapOf("action" to "subscribe", "symbols" to symbols)
                webSocket.send(gson.toJson(payload))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = parseQuotes(text)
                if (parsed.isNotEmpty()) trySend(parsed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        }

        socket = RetrofitClient.httpClient.newWebSocket(request, listener)

        awaitClose {
            socket?.close(1000, "closed")
        }
    }

    private fun streamViaTrueDataWebSocket(symbols: List<String>): Flow<List<Quote>> = callbackFlow {
        val wsUrl = BuildConfig.MARKET_TRUEDATA_WS_URL
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val socket = RetrofitClient.httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (truedataToken.isNotBlank()) {
                    webSocket.send("auth:$truedataToken")
                }
                val subscribe = buildString {
                    append("subscribe:")
                    append(symbols.joinToString(","))
                }
                webSocket.send(subscribe)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = parseTrueDataTicks(text)
                if (parsed.isNotEmpty()) {
                    trySend(parsed)
                    return
                }
                val generic = parseQuotes(text)
                if (generic.isNotEmpty()) trySend(generic)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        awaitClose {
            socket.close(1000, "closed")
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
}
