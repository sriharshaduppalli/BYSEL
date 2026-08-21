package com.bysel.trader.data.importbook

import android.content.Context
import com.google.gson.Gson

object ImportedBookStore {
    private const val PREFS = "bysel_imported_book"
    private const val KEY = "book_json"
    private val gson = Gson()

    fun read(context: Context): ImportedBook? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return runCatching { gson.fromJson(raw, ImportedBook::class.java) }.getOrNull()
            ?.takeIf { it.rows.isNotEmpty() }
    }

    fun write(context: Context, book: ImportedBook) {
        prefs(context).edit().putString(KEY, gson.toJson(book)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
