package com.receiptparser.tab.domain

import kotlin.math.abs

/** Conservative heuristic parser. Unrecognized text stays available for human correction. */
object ReceiptParser {
    private val money = Regex("(?<![\\w.])(?:₱|PHP\\s*)?-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}(?!\\d)", RegexOption.IGNORE_CASE)
    private val service = Regex("^(?:SERVICE(?: CHARGE)?|SC|SVC(?: FEE| CHARGE)?|ADD'L|ADDITIONAL CHARGE)\\b", RegexOption.IGNORE_CASE)
    private val ignored = Regex("^(?:SUB\\s*TOTAL|VATABLE|VAT SALES|VAT EXEMPT|VAT-EXEMPT|ZERO RATED|CASH|CHANGE|TENDERED|AMOUNT TENDERED|CARD|GCASH|VISA|MASTERCARD|PAYMENT|TIN|SOLD TO|INVOICE|RECEIPT NO|TEL|PHONE)\\b", RegexOption.IGNORE_CASE)
    fun parse(lines: List<OcrLine>): ReceiptDraft {
        val rows = reconstruct(lines)
        val items = mutableListOf<ReceiptItem>()
        val skipped = mutableListOf<String>()
        var venue = ""
        var serviceCharge = 0L
        var vat = 0L
        var discount = 0L
        var total = 0L
        var totalRank = 0
        var explicitInclusive = false
        rows.forEach { raw ->
            val row = raw.trim().replace(Regex("\\s+"), " ")
            if (row.isEmpty()) return@forEach
            val token = money.findAll(row).lastOrNull()
            val amount = token?.value?.replace("-", "")?.let(Money::parse)
            if (token == null || amount == null) {
                if (venue.isBlank() && items.isEmpty() && row.any { it.isLetter() }) venue = row.take(120)
                else skipped += row
                return@forEach
            }
            val prefix = row.substring(0, token.range.first).trim()
            val upper = prefix.uppercase()
            when {
                ignored.containsMatchIn(upper) -> Unit
                service.containsMatchIn(upper) -> serviceCharge = amount
                upper.startsWith("VAT") || upper.startsWith("VALUE ADDED TAX") || upper.startsWith("12% VAT") -> { vat = amount; explicitInclusive = explicitInclusive || "INCL" in upper }
                "DISCOUNT" in upper || upper.startsWith("DISC ") || upper == "DISC" -> discount = amount
                Regex("^(GRAND TOTAL|TOTAL|AMOUNT DUE|BALANCE DUE|NET AMOUNT)\\b").containsMatchIn(upper) -> {
                    val rank = if (upper.startsWith("GRAND TOTAL") || upper.startsWith("AMOUNT DUE")) 2 else 1
                    if (rank >= totalRank) { total = amount; totalRank = rank }
                }
                prefix.any { it.isLetter() } && !token.value.contains('-') -> {
                    val quantityMatch = Regex("^(\\d{1,3})\\s*(?:[xX]\\s*|\\s+)(.+)").matchEntire(prefix)
                    val qty = quantityMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val name = (quantityMatch?.groupValues?.get(2) ?: prefix).replace(Regex("\\s+(?:@\\s*)?\\d[\\d,.]*\\s*$"), "").trim()
                    if (name.isBlank()) skipped += row else items += ReceiptItem(name = name.take(200), qty = qty, lineTotal = amount, rawRow = row)
                }
                else -> skipped += row
            }
        }
        val withoutVat = items.sumOf { it.lineTotal } + serviceCharge - discount
        val mode = if (!explicitInclusive && vat > 0 && withoutVat != total && withoutVat + vat == total) VatMode.ADDITIVE else VatMode.INCLUSIVE
        return ReceiptDraft(venue = venue, rawText = rows.joinToString("\n"), ocrLines = lines, items = items,
            serviceCharge = serviceCharge, vat = vat, vatMode = mode, discount = discount, total = total, skippedRows = skipped)
    }
    fun parseText(text: String): ReceiptDraft = parse(text.lines().mapIndexed { i, s -> OcrLine(s, Box(0, i * 30, 500, i * 30 + 20)) })
    private fun reconstruct(lines: List<OcrLine>): List<String> {
        val groups = mutableListOf<MutableList<OcrLine>>()
        lines.filter { it.text.isNotBlank() }.sortedBy { it.box.centerY }.forEach { line ->
            val group = groups.lastOrNull()
            val anchor = group?.firstOrNull()
            if (anchor != null && abs(anchor.box.centerY - line.box.centerY) <= minOf(anchor.box.height, line.box.height) * 0.5) group.add(line)
            else groups += mutableListOf(line)
        }
        return groups.map { group -> group.sortedBy { it.box.left }.joinToString(" ") { it.text } }
    }
}
