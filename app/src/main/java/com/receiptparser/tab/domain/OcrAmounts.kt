package com.receiptparser.tab.domain

/** OCR-only repairs; matches retain source offsets so receipt evidence need not be rewritten. */
object OcrAmounts {
    private const val SPACE = "[ \\t]*"
    private const val CENTS = "[0-9Oo]{2}"
    private const val NUMBER = "(?:" +
        "\\d{1,3}(?:,\\d{3})+$SPACE\\.$SPACE$CENTS|" +
        "\\d{1,3}(?:\\.\\d{3})+$SPACE[.,]$SPACE$CENTS|" +
        "\\d+$SPACE[.,]$SPACE$CENTS)"

    // Reject partial numbers, dates and product codes. A solitary V remains a tax marker.
    val pattern = Regex(
        "(?<![\\p{L}\\p{N}_.,/\\-])(?<!\\d:)" +
            "(?:₱|PHP$SPACE)?-?$NUMBER" +
            "(?=$|[^\\p{L}\\p{N}_.,/:\\-]|[Vv](?![\\p{L}\\p{N}_.,/:\\-]))",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): Long? {
        val token = text.trim()
        if (!pattern.matches(token)) return null
        val compact = token.replace(" ", "").replace("\t", "")
        val decimalAt = maxOf(compact.lastIndexOf('.'), compact.lastIndexOf(','))
        val whole = compact.substring(0, decimalAt).replace(",", "").replace(".", "")
        // O is repaired only in the two cent digits of an otherwise valid numeric token.
        val cents = compact.substring(decimalAt + 1).replace('O', '0').replace('o', '0')
        return Money.parse("$whole.$cents")
    }
}
