package com.bysel.trader.data

import android.content.Context
import com.bysel.trader.data.models.MarketNewsResponse
import com.google.gson.Gson

/** Last successful Home headlines so a 5xx / timeout does not blank Market News. */
object MarketNewsStore {
    private const val PREFS = "bysel_market_news"
    private const val KEY = "last_feed_json"
    private val gson = Gson()

    fun read(context: Context): MarketNewsResponse? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return runCatching { gson.fromJson(raw, MarketNewsResponse::class.java) }.getOrNull()
            ?.takeIf { it.headlines.isNotEmpty() }
    }

    fun write(context: Context, feed: MarketNewsResponse) {
        if (feed.headlines.isEmpty()) return
        prefs(context).edit().putString(KEY, gson.toJson(feed)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
