package com.bysel.trader.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 4.0.28 crashed on Play because named-watchlist prefs used a Gson [com.google.gson.reflect.TypeToken]
 * field. That initializes when the Kotlin `object` loads. R8 can strip the anonymous TypeToken
 * subclass, so ViewModel.init died before the first frame. Debug unit tests never saw it.
 *
 * These checks fail the next release compile if that pattern, or the keep rules that back it, regress.
 */
class LocalStoreReleaseContractTest {

    @Test
    fun localStoresMustNotUseGsonTypeToken() {
        val hits = storeSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (TYPE_TOKEN_MARKERS.any { marker -> line.contains(marker) }) {
                    "${file.relativeTo(moduleRoot())}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        assertTrue(
            "Local *Store.kt files must not use Gson TypeToken. " +
                "It can throw ExceptionInInitializerError after R8. Use JsonObject/JsonParser " +
                "or fromJson(raw, SomeClass::class.java) on a kept type.\n" +
                hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun proguardKeepsStartupPersistedModels() {
        val rules = readRequired("proguard-rules.pro").readText()
        REQUIRED_KEEP_TYPES.forEach { type ->
            val keep = "-keep class $type"
            assertTrue(
                "proguard-rules.pro must keep $type so Play minification cannot strip prefs JSON.\n" +
                    "Add: $keep { *; }",
                rules.contains(keep),
            )
        }
    }

    private fun storeSources(): List<File> {
        val dataDir = readRequired("src/main/java/com/bysel/trader/data")
        return dataDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Store.kt") }
            .toList()
            .also { found ->
                assertTrue("Expected local Store.kt files under ${dataDir.canonicalPath}", found.isNotEmpty())
            }
    }

    private fun readRequired(relativeFromApp: String): File {
        val candidates = listOf(
            File(relativeFromApp),
            File("android/app/$relativeFromApp"),
            File("../$relativeFromApp"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find $relativeFromApp from ${File(".").canonicalPath}")
    }

    private fun moduleRoot(): File {
        val javaDir = readRequired("src/main/java")
        return javaDir.parentFile?.parentFile?.parentFile ?: javaDir
    }

    companion object {
        private val TYPE_TOKEN_MARKERS = listOf(
            "TypeToken",
            "com.google.gson.reflect",
        )
        private val REQUIRED_KEEP_TYPES = listOf(
            "com.bysel.trader.data.NamedWatchlist",
            "com.bysel.trader.data.NamedWatchlistBoard",
            "com.bysel.trader.data.ScannerBoard",
            "com.bysel.trader.data.ScannerBoardShelf",
            "com.bysel.trader.data.CustomScannerFilters",
            "com.bysel.trader.data.importbook.ImportedBook",
            "com.bysel.trader.data.importbook.ImportedHolding",
        )
    }
}
