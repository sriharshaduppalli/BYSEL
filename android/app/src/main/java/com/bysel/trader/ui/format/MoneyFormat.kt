package com.bysel.trader.ui.format

import java.util.Locale
import kotlin.math.abs

/**
 * Shared money and percentage formatting for Indian market conventions.
 *
 * Indian digit grouping puts the last three digits together and then groups the
 * remaining digits in pairs, so 524850 reads as 5,24,850 rather than 524,850.
 * `String.format("%,.2f")` cannot produce this, and neither can `DecimalFormat`,
 * because both repeat a single grouping width.
 */

/** Groups an amount using Indian digit separators, e.g. `1,23,45,678.90`. */
fun indianDigits(value: Double, decimals: Int = 2): String {
    val negative = value < 0
    // Format with Locale.US so the decimal separator is always '.' before we regroup.
    val fixed = String.format(Locale.US, "%.${decimals}f", abs(value))
    val dot = fixed.indexOf('.')
    val intPart = if (dot >= 0) fixed.substring(0, dot) else fixed
    val decPart = if (dot >= 0) fixed.substring(dot) else ""

    val grouped = if (intPart.length <= 3) {
        intPart
    } else {
        val tail = intPart.substring(intPart.length - 3)
        val head = intPart.substring(0, intPart.length - 3)
        val groups = mutableListOf<String>()
        var i = head.length
        while (i > 2) {
            groups.add(0, head.substring(i - 2, i))
            i -= 2
        }
        if (i > 0) groups.add(0, head.substring(0, i))
        groups.joinToString(",") + "," + tail
    }

    return if (negative) "-$grouped$decPart" else "$grouped$decPart"
}

/** Rupee amount with Indian grouping, e.g. `₹5,24,850.00`. */
fun formatInr(value: Double, decimals: Int = 2): String = "₹${indianDigits(value, decimals)}"

/**
 * Rupee amount abbreviated to crore/lakh for headline figures, e.g. `₹1.24 Cr`.
 * Falls back to fully grouped digits below one lakh so small amounts stay exact.
 */
fun formatInrCompact(value: Double): String {
    val magnitude = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        magnitude >= 1_00_00_000 -> "$sign₹${String.format(Locale.US, "%.2f", magnitude / 1_00_00_000)} Cr"
        magnitude >= 1_00_000 -> "$sign₹${String.format(Locale.US, "%.2f", magnitude / 1_00_000)} L"
        else -> "$sign₹${indianDigits(magnitude, 2)}"
    }
}

/** Percentage with an explicit sign, e.g. `+1.24%`. */
fun formatSignedPct(value: Double, decimals: Int = 2): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign${String.format(Locale.US, "%.${decimals}f", abs(value))}%"
}

/** Share/contract volume abbreviated using Indian crore/lakh units. */
fun formatVolumeCompact(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    val magnitude = value.toDouble()
    return when {
        magnitude >= 1_00_00_000 -> "${String.format(Locale.US, "%.2f", magnitude / 1_00_00_000)} Cr"
        magnitude >= 1_00_000 -> "${String.format(Locale.US, "%.2f", magnitude / 1_00_000)} L"
        magnitude >= 1_000 -> "${String.format(Locale.US, "%.1f", magnitude / 1_000)} K"
        else -> indianDigits(magnitude, 0)
    }
}
