package com.receiptparser.tab.domain

import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {
    // Manually transcribed item/tax layouts from the reported photos, not captured ML Kit output.
    @Test fun quantityDetailsDoNotDuplicateItemsOrTaxBreakdown() {
        val draft = ReceiptParser.parseText("""
            SALES INVOICE
            Tonkotsu Ramen 555.00V
            1 PCS x @ 555.00
            Sashimi Special 895.00V
            1 PCS x @ 895.00
            Coke Zero 210.00V
            2 PCS x @ 105.00
            SUBTOTAL 1,660.00
            Service Charge 166.00
            TOTAL 1,826.00
            VATable Sales 1,482.14
            VAT Amount 177.86
            VAT Exempt Sales 0.00
            Zero-Rated Sales 0.00
            Non-Taxable Sales 166.00
        """.trimIndent())
        assertEquals(listOf("Tonkotsu Ramen", "Sashimi Special", "Coke Zero"), draft.items.map { it.name })
        assertEquals(listOf(1, 1, 2), draft.items.map { it.qty })
        assertEquals(listOf(55500L, 89500L, 21000L), draft.items.map { it.lineTotal })
        assertEquals(166000L, draft.subtotal)
        assertEquals(16600L, draft.serviceCharge)
        assertEquals(17786L, draft.vat)
        assertEquals(VatMode.INCLUSIVE, draft.vatMode)
        assertEquals(182600L, draft.total)
        assertTrue(draft.reconciled)
        assertTrue(draft.items.last().rawRow.contains("2 PCS x @ 105.00"))
    }

    @Test fun namesAboveDecimalQuantityRowsKeepNamesQuantitiesAndLineTotals() {
        val draft = ReceiptParser.parseText("""
            BIK21 Cafe
            CUSTOMER INFORMATION
            NAME: T1
            NOTE: 981
            --------------------
            SHRIMP CAJUN BOWL
            1.0 x 640.00 640.00
            Rice
            2.0 x 35.00 70.00
            DULCE CARAMEL BLANCO 22oz
            1.0 x 190.00 190.00
            OCTOBITS N CHEESE
            1.0 x 110.00 110.00
            5.0 Item(s)
            SUBTOTAL 1,010.00
            TOTAL 1,010.00
            PAYMENT RECEIVED: 1,010.00
            GCash
            CHANGE AMOUNT: 0.00
        """.trimIndent())
        assertEquals("BIK21 Cafe", draft.venue)
        assertEquals(listOf("SHRIMP CAJUN BOWL", "Rice", "DULCE CARAMEL BLANCO 22oz", "OCTOBITS N CHEESE"), draft.items.map { it.name })
        assertEquals(listOf(1, 2, 1, 1), draft.items.map { it.qty })
        assertEquals(listOf(64000L, 7000L, 19000L, 11000L), draft.items.map { it.lineTotal })
        assertEquals(101000L, draft.total)
        assertTrue(draft.reconciled)
        assertFalse(draft.skippedRows.contains("Rice"))
    }

    @Test fun reconstructsSeparateQuantityUnitPriceAndTotalColumns() {
        val draft = ReceiptParser.parse(listOf(
            OcrLine("CAFE", Box(0, 0, 100, 20)),
            OcrLine("Rice", Box(0, 30, 100, 50)),
            OcrLine("70.00", Box(400, 62, 480, 82)),
            OcrLine("2.0", Box(0, 60, 40, 80)),
            OcrLine("x", Box(100, 61, 120, 81)),
            OcrLine("35.00", Box(250, 60, 320, 80)),
            OcrLine("TOTAL 70.00", Box(0, 90, 480, 110)),
        ))
        assertEquals("Rice", draft.items.single().name)
        assertEquals(2, draft.items.single().qty)
        assertEquals(7000L, draft.items.single().lineTotal)
        assertTrue(draft.reconciled)
    }

    @Test fun missingMultiplicationMarkStillUsesMatchingPriceColumns() {
        val draft = ReceiptParser.parseText("CAFE\nOCTOBITS N CHEESE\n1.0 110.00 110.00\nTOTAL 110.00")
        assertEquals("OCTOBITS N CHEESE", draft.items.single().name)
        assertEquals(1, draft.items.single().qty)
        assertEquals(11000L, draft.items.single().lineTotal)
        assertTrue(draft.reconciled)
    }

    @Test fun sameLineItemNamesStartingWithDigitsAreNotQuantityDetails() {
        val draft = ReceiptParser.parseText("CAFE\n2 x 7UP 100.00\n1 x 3-in-1 Coffee 50.00\nTOTAL 150.00")
        assertEquals(listOf("7UP", "3-in-1 Coffee"), draft.items.map { it.name })
        assertEquals(listOf(2, 1), draft.items.map { it.qty })
        assertTrue(draft.reconciled)
    }

    @Test fun ambiguousQuantityRowsAreNotTurnedIntoItems() {
        listOf("1.5 x 100.00 150.00", "0 x 100.00 0.00", "2 x 35.00 60.00", "2.0 35.00 60.00", "1.0 110.00", "2.0 PHP 35.00 PHP 60.00", "1.5 PHP 100.00 PHP 150.00").forEach { detail ->
            val draft = ReceiptParser.parseText("CAFE\nFood\n$detail\nTOTAL 110.00")
            assertTrue(detail, draft.items.isEmpty())
            assertTrue(detail, draft.skippedRows.contains(detail))
            assertTrue(detail, draft.skippedRows.contains("Food"))
        }
    }

    @Test fun orphanAndMismatchedDetailsStayAvailableForCorrection() {
        val draft = ReceiptParser.parseText("CAFE\n1 PCS x @ 555.00\nTea 70.00\n2 PCS x @ 30.00\nTOTAL 70.00")
        assertEquals(listOf("Tea"), draft.items.map { it.name })
        assertEquals(1, draft.items.single().qty)
        assertTrue(draft.skippedRows.contains("1 PCS x @ 555.00"))
        assertTrue(draft.skippedRows.contains("2 PCS x @ 30.00"))
    }

    @Test fun detailRowsDoNotAttachAcrossSeparatorsOrToMetadata() {
        listOf("NAME: T1", "NOTE: 981", "SUBTOTAL", "--------------------").forEach { boundary ->
            val draft = ReceiptParser.parseText("CAFE\nTea 70.00\n$boundary\n2 PCS x @ 35.00\nTOTAL 70.00")
            assertEquals(boundary, listOf("Tea"), draft.items.map { it.name })
            assertEquals(1, draft.items.single().qty)
            assertTrue(draft.skippedRows.contains("2 PCS x @ 35.00"))
        }
    }
}
