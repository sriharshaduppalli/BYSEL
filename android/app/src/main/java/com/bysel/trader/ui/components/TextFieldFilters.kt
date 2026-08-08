package com.bysel.trader.ui.components

/**
 * Value-based TextField filters (Configure text fields guide).
 * State-based InputTransformation stays Later until Material3 exposes stable APIs on our BOM.
 */

/** Quantity / lot size — digits only. */
fun filterDigitsOnly(raw: String, maxLength: Int = 9): String =
    raw.filter { it.isDigit() }.take(maxLength)

/**
 * Limit price / wallet amount — optional decimal, one dot, limited precision.
 */
fun filterDecimalInput(raw: String, maxIntegerDigits: Int = 12, maxFractionDigits: Int = 2): String {
    val cleaned = buildString {
        var seenDot = false
        for (ch in raw) {
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !seenDot -> {
                    seenDot = true
                    append(ch)
                }
            }
        }
    }
    val parts = cleaned.split('.', limit = 2)
    val intPart = parts[0].take(maxIntegerDigits)
    return if (parts.size == 1 || maxFractionDigits <= 0) {
        intPart
    } else {
        intPart + "." + parts[1].take(maxFractionDigits)
    }
}
