package com.receiptparser.tab.domain

/** Conservative heuristic parser. Unrecognized text stays available for human correction. */
object ReceiptParser {
    private val money = OcrAmounts.pattern
    private val service = Regex("^(?:SERVICE(?: CHARGE)?|SC|SVC(?: FEE| CHARGE)?|ADD'L|ADDITIONAL CHARGE)\\b", RegexOption.IGNORE_CASE)
    private val ignored = Regex("^(?:SUB\\s*TOTAL|VATABLE|VAT SALES|VAT EXEMPT|VAT-EXEMPT|ZERO[ -]RATED|NON[ -]TAXABLE(?: SALES)?|CASH|CHANGE|TENDERED|AMOUNT TENDERED|CARD|GCASH|VISA|MASTERCARD|PAYMENT|TIN|SOLD TO|INVOICE|RECEIPT NO|TEL|PHONE)\\b", RegexOption.IGNORE_CASE)
    // Only observed label corruptions are accepted; item descriptions are never fuzzy-corrected.
    private val observedIgnored = Regex(
        "^(?:UAT (?:SALES|EXENPT SALES)|CHARNGE DUE|CHECK TOTAL SPLIT \\d+ WAYS)\\s*:?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val additionalTax = Regex(
        "^(?:GST(?:\\s*\\d+(?:\\.\\d+)?%)?|12\\s*[:*]\\s*UAT)\\s*:?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val totalLabel = Regex("^(GRAND TOTAL|TOTAL|AMOUNT DUE|AMOURNT DUE|BALANCE DUE|NET AMOUNT)\\b")
    private val numberedItem = Regex("^\\d{1,3}\\s+(?:[xX]\\s*)?[\\p{L}]")
    private val freeZeroRow = Regex("^([1-9]\\d{0,2})\\s+(?:[xX]\\s+)?FREE\\b.+\\sD\\.00$")
    private val observedUnitPriceSuffix = Regex("^(.+)\\s+e(\\d+\\.\\d{2})$")
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
    private val metadata = Regex("^(?:NAME|NOTE|DATE|TIME|INV|CUSTOMER|ORDER|CASHIER|TICKET|BILL|PAX|TABLE|SEAT|CHECK|DUPLICATE CHECK|DINING|ITEMS ORDERED|CLOSED|SALES INVOICE|THANK|TOTAL|GRAND TOTAL|AMOUNT DUE|AMOURNT DUE|BALANCE DUE|NET AMOUNT|VAT|VALUE ADDED TAX|12% VAT|DISCOUNT|DISC)\\b", RegexOption.IGNORE_CASE)

    private data class QuantityDetail(val qty: Int, val lineTotal: Long)

    private fun parseQuantityDetail(row: String): QuantityDetail? {
        val match = quantityDetail.matchEntire(row) ?: quantityPriceColumns.matchEntire(row) ?: return null
        val qty = match.groupValues[1].toInt().takeIf { it > 0 } ?: return null
        val unitPrice = OcrAmounts.parse(match.groupValues[2]) ?: return null
        val calculated = (unitPrice * qty).takeIf { it <= Money.MAX } ?: return null
        val printedTotal = match.groupValues[3].takeIf { it.isNotEmpty() }?.let(OcrAmounts::parse)
        if (match.groupValues[3].isNotEmpty() && printedTotal != calculated) return null
        return QuantityDetail(qty, printedTotal ?: calculated)
    }

    private fun isItemName(row: String): Boolean = row.any { it.isLetter() } &&
        !ignored.containsMatchIn(row) && !observedIgnored.matches(row) &&
        !additionalTax.matches(row) && !service.containsMatchIn(row) &&
        !metadata.containsMatchIn(row) && !quantityDetailStart.containsMatchIn(row)

    fun parse(lines: List<OcrLine>): ReceiptDraft {
        val reconstructed = ReceiptRows.reconstructLines(lines)
        val rows = reconstructed.map { it.text.trim().replace(Regex("\\s+"), " ") }
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
            // D is repaired only in the observed zero token on an explicitly free, numbered item.
            val amountRow = if (freeZeroRow.matches(row)) row.dropLast(4) + "0.00" else row
            val token = money.findAll(amountRow).lastOrNull()
            val amount = token?.value?.replace("-", "")?.let(OcrAmounts::parse)
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
                val previous = items.lastOrNull()
                // A short indented continuation is accepted only inside numbered priced items.
                // An unpriced name before a total or an unrelated row stays available for correction.
                if (token == null && previous != null && lastItemRow == index - 1 &&
                    numberedItem.containsMatchIn(previous.rawRow) &&
                    row.length <= 80 && row.all { it.isLetter() || it.isWhitespace() || it in "-'’&" } &&
                    isItemName(row) && nextRow != null && numberedItem.containsMatchIn(nextRow) &&
                    money.containsMatchIn(nextRow) &&
                    reconstructed[index].box.left > maxOf(
                        reconstructed[index - 1].box.left, reconstructed[index + 1].box.left,
                    ) + minOf(reconstructed[index - 1].box.height, reconstructed[index + 1].box.height) * 0.5
                ) {
                    items[items.lastIndex] = previous.copy(
                        name = (previous.name + " " + row).take(200),
                        rawRow = previous.rawRow + "\n" + row,
                    )
                    return@forEachIndexed
                }
                if (venue.isBlank() && items.isEmpty() && row.any { it.isLetter() }) venue = row.take(120)
                else skipped += row
                return@forEachIndexed
            }
            val prefix = row.substring(0, token.range.first).trim()
            val upper = prefix.uppercase()
            when {
                ignored.containsMatchIn(upper) || observedIgnored.matches(prefix) -> skipped += row
                service.containsMatchIn(upper) -> serviceCharge = amount
                upper.startsWith("VAT") || upper.startsWith("VALUE ADDED TAX") || upper.startsWith("12% VAT") || additionalTax.matches(prefix) -> { vat = amount; explicitInclusive = explicitInclusive || "INCL" in upper }
                "DISCOUNT" in upper || upper.startsWith("DISC ") || upper == "DISC" -> discount = amount
                totalLabel.containsMatchIn(upper) -> {
                    val rank = if (upper.startsWith("GRAND TOTAL") || upper.startsWith("AMOUNT DUE") || upper.startsWith("AMOURNT DUE") || upper.startsWith("TOTAL DUE")) 2 else 1
                    if (rank >= totalRank) { total = amount; totalRank = rank }
                }
                prefix.any { it.isLetter() } && !token.value.contains('-') -> {
                    val quantityMatch = Regex("^(\\d{1,3})\\s*(?:[xX]\\s*|\\s+)(.+)").matchEntire(prefix)
                    val qty = quantityMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val itemPrefix = quantityMatch?.groupValues?.get(2) ?: prefix
                    val unitSuffix = observedUnitPriceSuffix.matchEntire(itemPrefix)
                    val unitPrice = unitSuffix?.groupValues?.get(2)?.let(OcrAmounts::parse)
                    // Remove the observed e-prefixed unit price only when the printed total verifies it.
                    val description = if (qty > 1 && unitSuffix != null && unitPrice != null && unitPrice * qty == amount) {
                        unitSuffix.groupValues[1]
                    } else itemPrefix
                    val name = description.replace(Regex("\\s+(?:@\\s*)?\\d[\\d,.]*\\s*$"), "").trim()
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
}
