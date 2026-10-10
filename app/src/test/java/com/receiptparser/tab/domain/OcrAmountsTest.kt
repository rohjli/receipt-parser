package com.receiptparser.tab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAmountsTest {
    @Test fun repairsObservedOcrAmountPunctuation() {
        mapOf(
            "119. 70" to 11970L,
            "114.O0" to 11400L,
            "250,00" to 25000L,
            "1.493.00" to 149300L,
            "1.333.04" to 133304L,
        ).forEach { (text, expected) ->
            assertEquals(text, text, OcrAmounts.pattern.find(text)?.value)
            assertEquals(text, expected, OcrAmounts.parse(text))
        }
    }

    @Test fun retainsCurrencyGroupingAndZero() {
        mapOf(
            "₱1,493.00" to 149300L,
            "PHP 119. 70" to 11970L,
            "php 250,00" to 25000L,
            "1.493,00" to 149300L,
            "0.00" to 0L,
            "10,000,000.00" to Money.MAX,
        ).forEach { (text, expected) ->
            assertEquals(text, text, OcrAmounts.pattern.find(text)?.value)
            assertEquals(text, expected, OcrAmounts.parse(text))
        }
        assertNull(OcrAmounts.parse("10,000,000.01"))
    }

    @Test fun leavesAmbiguousLettersAndEmbeddedProductCodesUnparsed() {
        listOf("B4,00", "D.00", "O.00", "e32.00", "SKU114.O0", "114.00CODE", "114.00VAT").forEach { text ->
            assertNull(text, OcrAmounts.pattern.find(text))
            assertNull(text, OcrAmounts.parse(text))
        }
    }

    @Test fun doesNotExtractSmallerAmountsFromMalformedNumbersOrDates() {
        listOf(
            "1,49.00", "12,34.56", "1234.567.89", "1.49.00", "1.493.000",
            "1,234,56", "250.001", "250.00.00", "250.00,00", "1.2.03",
            "2025/12/31.00", "2025-12-31.00", "12:30.00", "31.12.25",
        ).forEach { text ->
            assertNull(text, OcrAmounts.pattern.find(text))
            assertNull(text, OcrAmounts.parse(text))
        }
    }

    @Test fun matchesOriginalOffsetsAndPreservesNegativeAndTaxMarkerSemantics() {
        val row = "1 OOLONG 119. 70V"
        val match = OcrAmounts.pattern.find(row)!!
        assertEquals("119. 70", match.value)
        assertEquals("1 OOLONG ", row.substring(0, match.range.first))
        assertEquals("119. 70", row.substring(match.range))
        assertEquals("-10.00", OcrAmounts.pattern.find("DISCOUNT -10.00")?.value)
        assertEquals("PHP -10.00", OcrAmounts.pattern.find("DISCOUNT PHP -10.00")?.value)
        assertNull(OcrAmounts.parse("-10.00"))
        assertEquals("100.00", OcrAmounts.pattern.find("TOTAL:100.00")?.value)
    }

    @Test fun repairsOnlyAmountTokensAndDoesNotJoinAmountsAcrossLines() {
        val row = "2 OOLONG B4,00 119. 70 250,00"
        assertEquals(listOf(11970L, 25000L), OcrAmounts.pattern.findAll(row).map { OcrAmounts.parse(it.value) }.toList())
        assertTrue(OcrAmounts.pattern.findAll("119.\n70").none())
    }

    @Test fun manualMoneyValidationRemainsStrict() {
        listOf("119. 70", "114.O0", "250,00", "1.493.00", "1.333.04").forEach { text ->
            assertNull(text, Money.parse(text))
        }
    }
}
