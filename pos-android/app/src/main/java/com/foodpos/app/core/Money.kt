package com.foodpos.app.core

import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * Every amount in the app is stored as whole cents (centavos) in a [Long]. Floating point is never
 * used for money, so totals, change and reports always add up exactly.
 */
object Money {
    const val DEFAULT_SYMBOL = "₱"

    /** 1234550 -> "₱12,345.50" */
    fun format(cents: Long, symbol: String = DEFAULT_SYMBOL): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        return String.format(Locale.US, "%s%s%,d.%02d", sign, symbol, value / 100, value % 100)
    }

    /** 1234550 -> "12345.50": no symbol and no grouping, for receipts and edit fields. */
    fun plain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        return String.format(Locale.US, "%s%d.%02d", sign, value / 100, value % 100)
    }

    /** Short label for chart axes and calendar cells: 1250000 -> "₱12.5k". */
    fun compact(cents: Long, symbol: String = DEFAULT_SYMBOL): String {
        val value = cents / 100.0
        val size = abs(value)
        val text = when {
            size >= 1_000_000 -> oneDecimal(value / 1_000_000) + "M"
            size >= 1_000 -> oneDecimal(value / 1_000) + "k"
            else -> oneDecimal(value)
        }
        return symbol + text
    }

    /**
     * Parses what a person typed ("1,250.5", "₱80") into cents. Returns null for anything that is
     * not a non-negative amount with at most two decimals.
     */
    fun parse(input: String): Long? {
        val cleaned = input.replace(",", "").replace(DEFAULT_SYMBOL, "").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value.scale() > 2) return null
        return runCatching {
            value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()
    }

    private fun oneDecimal(value: Double): String =
        String.format(Locale.US, "%.1f", value).removeSuffix(".0")
}
