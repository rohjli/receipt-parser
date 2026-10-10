package com.receiptparser.tab.domain

import org.junit.Assert.*
import org.junit.Test

class ReceiptLabelsTest {
    @Test fun gstIsAnAdditiveTaxAndSplitSuggestionIsNotAnItemOrTotal() {
        val draft = ReceiptParser.parseText("""
            KITCHEN
            1 Wings 17.00
            1 Lumpia 15.00
            1 Pork 22.00
            1 Chicken 24.00
            1 Sisig 24.50
            1 Cake 11.50
            SUBTOTAL 114.00
            GST 5% 5.70
            TOTAL DUE 119.70
            CHECK TOTAL SPLIT 3 WAYS 39.90
        """.trimIndent())
        assertEquals(6, draft.items.size)
        assertEquals(11400L, draft.subtotal)
        assertEquals(570L, draft.vat)
        assertEquals(VatMode.ADDITIVE, draft.vatMode)
        assertEquals(11970L, draft.total)
        assertTrue(draft.reconciled)
        assertTrue(draft.skippedRows.contains("CHECK TOTAL SPLIT 3 WAYS 39.90"))
        assertTrue(draft.rawText.contains("GST 5% 5.70"))
    }

    @Test fun observedTaxAndChangeLabelCorruptionsDoNotBecomeItems() {
        val draft = ReceiptParser.parseText("""
            KITCHEN
            Food 1,493.00
            Amount Due: 1,493.00
            UAT Sales 1,333.04
            UAT Exenpt Sales 0.00
            12: UAT 159.96
            Charnge Due: 7.00
        """.trimIndent())
        assertEquals(listOf("Food"), draft.items.map { it.name })
        assertEquals(15996L, draft.vat)
        assertEquals(VatMode.INCLUSIVE, draft.vatMode)
        assertEquals(149300L, draft.total)
        assertTrue(draft.reconciled)
        assertTrue(draft.skippedRows.contains("UAT Sales 1,333.04"))
        assertTrue(draft.skippedRows.contains("UAT Exenpt Sales 0.00"))
        assertTrue(draft.skippedRows.contains("Charnge Due: 7.00"))
        assertTrue(draft.rawText.contains("12: UAT 159.96"))
    }

    @Test fun scaledObservedTotalAndTaxLabelsAreRecognized() {
        val draft = ReceiptParser.parseText("KITCHEN\nFood 1,493.00\nAmournt Due: 1,493.00\n12* UAT 159.96")
        assertEquals(listOf("Food"), draft.items.map { it.name })
        assertEquals(149300L, draft.total)
        assertEquals(15996L, draft.vat)
        assertEquals(VatMode.INCLUSIVE, draft.vatMode)
        assertTrue(draft.reconciled)
    }

    @Test fun adjacentWrappedDescriptionKeepsSourceAndNextItem() {
        val draft = ReceiptParser.parse(listOf(
            OcrLine("KITCHEN", Box(0, 0, 200, 20)),
            OcrLine("1 SISIG D 189.00", Box(0, 30, 200, 50)),
            OcrLine("Bangus D", Box(25, 60, 100, 80)),
            OcrLine("1 SHRIMP D 250.00", Box(0, 90, 200, 110)),
            OcrLine("TOTAL 439.00", Box(0, 120, 200, 140)),
        ))
        assertEquals(listOf("SISIG D Bangus D", "SHRIMP D"), draft.items.map { it.name })
        assertEquals(listOf(18900L, 25000L), draft.items.map { it.lineTotal })
        assertEquals("1 SISIG D 189.00\nBangus D", draft.items.first().rawRow)
        assertFalse(draft.skippedRows.contains("Bangus D"))
        assertTrue(draft.reconciled)
    }

    @Test fun unindentedUnpricedDishStaysAvailableForCorrection() {
        val draft = ReceiptParser.parseText("KITCHEN\n1 SISIG D 189.00\nRice\n1 SHRIMP D 250.00\nTOTAL 439.00")
        assertEquals(listOf("SISIG D", "SHRIMP D"), draft.items.map { it.name })
        assertTrue(draft.skippedRows.contains("Rice"))
    }

    @Test fun continuationNeverConsumesMetadataOrNextItemWithSeparatePriceDetail() {
        listOf("TABLE 7", "Seat 1", "THANK YOU", "UAT Sales", "GST 5%", "CHECK TOTAL SPLIT 3 WAYS").forEach { boundary ->
            val draft = ReceiptParser.parseText("KITCHEN\n1 SISIG D 189.00\n$boundary\n1 SHRIMP D 250.00\nTOTAL 439.00")
            assertEquals(boundary, listOf("SISIG D", "SHRIMP D"), draft.items.map { it.name })
            assertTrue(boundary, draft.skippedRows.contains(boundary))
        }
        val draft = ReceiptParser.parseText("KITCHEN\n1 SISIG D 189.00\nTea\n2 x 35.00 70.00\nTOTAL 259.00")
        assertEquals(listOf("SISIG D", "Tea"), draft.items.map { it.name })
        assertEquals(2, draft.items.last().qty)
        assertTrue(draft.reconciled)
    }

    @Test fun uncertainStandaloneNamesAndSimilarItemNamesStayUnchanged() {
        val draft = ReceiptParser.parseText("""
            KITCHEN
            1 SISIG D 189.00
            Mystery dish
            TOTAL 189.00
            UAT smoothie 50.00
            Charnge smoothie 60.00
            GST sandwich 70.00
        """.trimIndent())
        assertEquals(listOf("SISIG D", "UAT smoothie", "Charnge smoothie", "GST sandwich"), draft.items.map { it.name })
        assertTrue(draft.skippedRows.contains("Mystery dish"))
    }
    @Test fun explicitFreeItemRecoversObservedZeroGlyphAndPreservesSource() {
        val draft = ReceiptParser.parseText("KITCHEN\n1 FREE Caiote Frit D D.00\n1 Tea 50.00\nTOTAL 50.00")
        assertEquals(listOf("FREE Caiote Frit D", "Tea"), draft.items.map { it.name })
        assertEquals(listOf(0L, 5000L), draft.items.map { it.lineTotal })
        assertEquals("1 FREE Caiote Frit D D.00", draft.items.first().rawRow)
        assertTrue(draft.reconciled)
    }

    @Test fun freeGlyphRecoveryDoesNotInventZeroForUncertainOrNonzeroItems() {
        listOf("1 Food D.00", "FREE Food D.00", "0 FREE Food D.00", "1 FREEDOM Food D.00").forEach { row ->
            val draft = ReceiptParser.parseText("KITCHEN\n$row\nTOTAL 0.00")
            assertTrue(row, draft.items.isEmpty())
            assertTrue(row, draft.skippedRows.contains(row))
        }
        val draft = ReceiptParser.parseText("KITCHEN\n1 FREE Food 20.00\nTOTAL 20.00")
        assertEquals(2000L, draft.items.single().lineTotal)
    }

    @Test fun verifiedRiceUnitPriceIsRemovedFromNameWithoutChangingRawSource() {
        val draft = ReceiptParser.parseText("KITCHEN\n2 Steamed Rice D e32.00 64.00\nTOTAL 64.00")
        assertEquals("Steamed Rice D", draft.items.single().name)
        assertEquals(2, draft.items.single().qty)
        assertEquals(6400L, draft.items.single().lineTotal)
        assertEquals("2 Steamed Rice D e32.00 64.00", draft.items.single().rawRow)
        assertTrue(draft.reconciled)
    }

    @Test fun inconsistentPriceLikeNameSuffixIsPreservedForCorrection() {
        val draft = ReceiptParser.parseText("KITCHEN\n2 Steamed Rice D e32.00 60.00\nTOTAL 60.00")
        assertEquals("Steamed Rice D e32.00", draft.items.single().name)
        assertEquals(6000L, draft.items.single().lineTotal)
    }

}
