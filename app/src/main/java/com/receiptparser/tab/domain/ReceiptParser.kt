package com.receiptparser.tab.domain

import kotlin.math.abs

/** Conservative heuristic parser. Unrecognized text stays available for human correction. */
object ReceiptParser {
    private val money = Regex("(?<![\\w.])(?:₱|PHP\\s*)?-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}(?!\\d)", RegexOption.IGNORE_CASE)
    private val service = Regex("^(?:SERVICE(?: CHARGE)?|SC|SVC(?: FEE| CHARGE)?|ADD'L|ADDITIONAL CHARGE)\\b", RegexOption.IGNORE_CASE)
    private val ignored = Regex("^(?:SUB\\s*TOTAL|VATABLE|VAT SALES|VAT EXEMPT|VAT-EXEMPT|ZERO[ -]RATED|NON[ -]TAXABLE(?: SALES)?|CASH|CHANGE|TENDERED|AMOUNT TENDERED|CARD|GCASH|VISA|MASTERCARD|PAYMENT|TIN|SOLD TO|INVOICE|RECEIPT NO|TEL|PHONE)\\b", RegexOption.IGNORE_CASE)
    private val quantityDetailStart = Regex(
        "^\\d+(?:\\.\\d+)?\\s*(?:(?:PCS?\\s*)?[x×]\\s*@?\\s*|\\s+)${money.pattern}(?:\\s|[Vv]?$)",
        RegexOption.IGNORE_CASE,
    )
    private val quantityDetail = Regex(
        "^(\\d{1,3})(?:\\.0+)?\\s*(?:PCS?\\s*)?[x×]\\s*@?\\s*(${money.pattern})(?:\\s+(${money.pattern}))?\\s*[Vv]?$",
        RegexOption.IGNORE_CASE,
    )
    // OCR can omit the multiplication mark; require both price columns in that case.
    private val quantityPriceColumns = Regex(
        "^(\\d{1,3})(?:\\.0+)?\\s+(${money.pattern})\\s+(${money.pattern})\\s*[Vv]?$",
        RegexOption.IGNORE_CASE,
    )
    private val metadata = Regex("^(?:NAME|NOTE|DATE|TIME|INV|CUSTOMER|ORDER|CASHIER|TICKET|BILL|PAX|SALES INVOICE|THANK|TOTAL|GRAND TOTAL|AMOUNT DUE|BALANCE DUE|NET AMOUNT|VAT|VALUE ADDED TAX|12% VAT|DISCOUNT|DISC)\\b", RegexOption.IGNORE_CASE)

    private data class QuantityDetail(val qty: Int, val lineTotal: Long)

    private fun parseQuantityDetail(row: String): QuantityDetail? {
        val match = quantityDetail.matchEntire(row) ?: quantityPriceColumns.matchEntire(row) ?: return null
        val qty = match.groupValues[1].toInt().takeIf { it > 0 } ?: return null
        val unitPrice = Money.parse(match.groupValues[2]) ?: return null
        val calculated = (unitPrice * qty).takeIf { it <= Money.MAX } ?: return null
        val printedTotal = match.groupValues[3].takeIf { it.isNotEmpty() }?.let(Money::parse)
        if (match.groupValues[3].isNotEmpty() && printedTotal != calculated) return null
        return QuantityDetail(qty, printedTotal ?: calculated)
    }

    private fun isItemName(row: String): Boolean = row.any { it.isLetter() } &&
        !ignored.containsMatchIn(row) && !service.containsMatchIn(row) &&
        !metadata.containsMatchIn(row) && !quantityDetailStart.containsMatchIn(row)

    fun parse(lines: List<OcrLine>): ReceiptDraft {
        val rows = reconstruct(lines).map { it.trim().replace(Regex("\\s+"), " ") }
        val items = mutableListOf<ReceiptItem>()
        val skipped = mutableListOf<String>()
        var venue = ""
        var serviceCharge = 0L
        var vat = 0L
        var discount = 0L
        var total = 0L
        var totalRank = 0
        var explicitInclusive = false
        var consumedDetail = -1
        var lastItemRow = -2
        rows.forEachIndexed { index, row ->
            if (row.isEmpty() || index == consumedDetail) return@forEachIndexed
            val detail = parseQuantityDetail(row)
            if (detail != null || quantityDetailStart.containsMatchIn(row)) {
                val previous = items.lastOrNull()
                if (detail != null && previous != null && lastItemRow == index - 1 && previous.lineTotal == detail.lineTotal) {
                    items[items.lastIndex] = previous.copy(qty = detail.qty, rawRow = previous.rawRow + "\n" + row)
                } else {
                    skipped += row
                }
                return@forEachIndexed
            }
            val token = money.findAll(row).lastOrNull()
            val amount = token?.value?.replace("-", "")?.let(Money::parse)
            if (token == null || amount == null) {
                val nextRow = rows.getOrNull(index + 1)
                val nextDetail = nextRow?.let(::parseQuantityDetail)
                if (venue.isNotBlank() && token == null && isItemName(row) && nextDetail != null) {
                    items += ReceiptItem(
                        name = row.take(200),
                        qty = nextDetail.qty,
                        lineTotal = nextDetail.lineTotal,
                        rawRow = row + "\n" + nextRow,
                    )
                    consumedDetail = index + 1
                    lastItemRow = index
                    return@forEachIndexed
                }
                if (venue.isBlank() && items.isEmpty() && row.any { it.isLetter() }) venue = row.take(120)
                else skipped += row
                return@forEachIndexed
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
                    if (name.isBlank()) skipped += row else {
                        items += ReceiptItem(name = name.take(200), qty = qty, lineTotal = amount, rawRow = row)
                        lastItemRow = index
                    }
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
