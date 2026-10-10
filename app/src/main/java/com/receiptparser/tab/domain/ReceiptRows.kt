package com.receiptparser.tab.domain

import kotlin.math.abs

internal object ReceiptRows {
    private val descriptionHeader = Regex("^(?:ITEMS?(?: ORDERED)?|DESCRIPTION)$", RegexOption.IGNORE_CASE)
    private val amountHeader = Regex("^(?:AMOUNT|LINE TOTAL|TOTAL PRICE)$", RegexOption.IGNORE_CASE)
    private val quantityDescription = Regex("^\\d{1,3}\\s+.*[A-Za-z].*$")
    private val amount = Regex("^(?:₱|PHP\\s*)?\\d+(?:[,.]\\d{3})*[.,]\\d{2}$", RegexOption.IGNORE_CASE)

    fun reconstruct(lines: List<OcrLine>): List<String> = reconstructLines(lines).map { it.text }

    fun reconstructLines(lines: List<OcrLine>): List<OcrLine> {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        val slope = validatedSlope(nonBlank)
        val groups = mutableListOf<MutableList<OcrLine>>()
        nonBlank.sortedBy { it.rowY(slope) }.forEach { line ->
            val group = groups.lastOrNull()
            if (group != null && belongsToRow(group, line, slope)) group.add(line)
            else groups += mutableListOf(line)
        }
        return groups.map { group ->
            OcrLine(
                group.sortedBy { it.box.left }.joinToString(" ") { it.text },
                Box(group.minOf { it.box.left }, group.minOf { it.box.top },
                    group.maxOf { it.box.right }, group.maxOf { it.box.bottom }),
            )
        }
    }

    private fun validatedSlope(lines: List<OcrLine>): Double {
        val descriptions = lines.filter { descriptionHeader.matches(it.text.trim()) }
        val amounts = lines.filter { amountHeader.matches(it.text.trim()) }
        val candidates = descriptions.flatMap { description ->
            amounts.mapNotNull { price ->
                if (price.box.left <= description.box.right) return@mapNotNull null
                val slope = (price.box.centerY - description.box.centerY) /
                    (price.centerX - description.centerX)
                if (abs(slope) > 0.2 || abs(slope) < 0.01) return@mapNotNull null

                // Headers provide an independent baseline; item order alone cannot establish skew.
                val names = lines.filter {
                    quantityDescription.matches(it.text.trim()) &&
                        it.box.left <= description.box.right && it.box.right < price.box.left &&
                        it.rowY(slope) > description.rowY(slope) + description.box.height
                }
                val prices = lines.filter {
                    amount.matches(it.text.trim()) && it.box.left >= description.box.right &&
                        abs(it.centerX - price.centerX) <= price.box.right - price.box.left &&
                        it.rowY(slope) > price.rowY(slope) + price.box.height
                }
                val matched = uniqueMatches(names, prices, slope)
                if (matched.size < 3 || matched.size <= uniqueMatches(names, prices, 0.0).size) {
                    null
                } else {
                    // Box centers vary with glyph height. Refine from independently matched rows,
                    // retaining the header estimate if refinement would change any pairing.
                    val slopes = matched.map { (name, value) ->
                        (value.box.centerY - name.box.centerY) / (value.centerX - name.centerX)
                    }.sorted()
                    val refined = (slopes[(slopes.size - 1) / 2] + slopes[slopes.size / 2]) / 2
                    val preservesPairs = uniqueMatches(names, prices, refined).toSet() == matched.toSet()
                    val accepted = if (abs(refined) <= 0.2 && sameRow(description, price, refined) && preservesPairs) {
                        refined
                    } else {
                        slope
                    }
                    accepted to matched.size
                }
            }
        }
        return candidates.maxByOrNull { it.second }?.first ?: 0.0
    }

    private fun uniqueMatches(
        names: List<OcrLine>,
        prices: List<OcrLine>,
        slope: Double,
    ): List<Pair<OcrLine, OcrLine>> =
        names.mapNotNull { name ->
            val price = prices.singleOrNull { sameRow(name, it, slope) }
            if (price != null && names.count { sameRow(it, price, slope) } == 1) name to price else null
        }

    private fun belongsToRow(group: List<OcrLine>, line: OcrLine, slope: Double): Boolean {
        // A center averaged across fragments avoids letting a tiny quantity box anchor the row.
        // Overlapping columns are separate rows, even when their cropped vertical boxes are close.
        if (group.any { it.box.left < line.box.right && line.box.left < it.box.right }) return false
        val center = group.map { it.rowY(slope) }.average()
        val height = minOf(group.minOf { it.box.height }, line.box.height)
        return abs(center - line.rowY(slope)) <= height * 0.5
    }

    private fun sameRow(first: OcrLine, second: OcrLine, slope: Double): Boolean =
        abs(first.rowY(slope) - second.rowY(slope)) <= minOf(first.box.height, second.box.height) * 0.5

    private val OcrLine.centerX get() = (box.left + box.right) / 2.0

    private fun OcrLine.rowY(slope: Double) = box.centerY - slope * centerX
}
