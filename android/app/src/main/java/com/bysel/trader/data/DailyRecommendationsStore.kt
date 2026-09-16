package com.bysel.trader.data

import android.content.Context
import com.bysel.trader.data.models.StockRecommendationsResponse
import com.google.gson.Gson

/** Last successful Home daily picks so a timeout does not blank the card. */
object DailyRecommendationsStore {
    private const val PREFS = "bysel_daily_recommendations"
    private const val KEY = "last_feed_json"
    private val gson = Gson()

    fun read(context: Context): StockRecommendationsResponse? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return runCatching { gson.fromJson(raw, StockRecommendationsResponse::class.java) }.getOrNull()
            ?.takeIf { it.allScored.isNotEmpty() || it.recommendations.values.any { rows -> rows.isNotEmpty() } }
    }

    fun write(context: Context, feed: StockRecommendationsResponse) {
        val hasRows = feed.allScored.isNotEmpty() || feed.recommendations.values.any { it.isNotEmpty() }
        if (!hasRows) return
        prefs(context).edit().putString(KEY, gson.toJson(feed)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
