package com.receiptparser.tab.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object Money {
    const val MAX = 1_000_000_000L
    private val inputPattern = Regex("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?")
    fun parse(text: String): Long? {
        val value = text.trim().replace(Regex("^(?:₱|PHP)\\s*", RegexOption.IGNORE_CASE), "")
        if (!inputPattern.matches(value)) return null
        return try { BigDecimal(value.replace(",", "")).movePointRight(2).longValueExact().takeIf { it in 0..MAX } } catch (_: ArithmeticException) { null } catch (_: NumberFormatException) { null }
    }
    fun format(amount: Long): String {
        val number = NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        return (if (amount < 0) "−₱" else "₱") + number.format(BigDecimal.valueOf(amount).abs().movePointLeft(2))
    }
    fun input(amount: Long): String = BigDecimal.valueOf(amount, 2).toPlainString()
}
