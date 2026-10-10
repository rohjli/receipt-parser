package com.receiptparser.tab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptRowsTest {
    @Test fun joinsSmallQuantityAndPriceBoxesWithTheirDescription() {
        val lines = listOf(
            OcrLine("2", Box(75, 338, 79, 347)),
            OcrLine("Steaed Rice D e32.00", Box(89, 337, 195, 351)),
            OcrLine("B4,00", Box(232, 343, 258, 351)),
            OcrLine(") SANGKATUTAK NA FISHBALLSD 130.00", Box(75, 348, 257, 364)),
        )

        assertEquals(listOf("2 Steaed Rice D e32.00 B4,00", ") SANGKATUTAK NA FISHBALLSD 130.00"),
            ReceiptRows.reconstruct(lines))
    }

    @Test fun doesNotBridgeAdjacentRowsThroughFragmentsInTheSameColumn() {
        val lines = listOf(
            OcrLine("1", Box(0, 5, 10, 15)),
            OcrLine("Rice", Box(20, 9, 100, 19)),
            OcrLine("20.00", Box(200, 11, 240, 21)),
            OcrLine("Tea", Box(20, 12, 100, 22)),
        )

        assertEquals(listOf("1 Rice 20.00", "Tea"), ReceiptRows.reconstruct(lines))
    }

    @Test fun preservesRiceAndAdjacentItemsFromEnlargedImageOcr() {
        val lines = listOf(
            OcrLine("1 FREE Caiote Frit D", Box(81, 324, 180, 339)),
            OcrLine("2 Steamed Rice D e32.00", Box(75, 337, 195, 350)),
            OcrLine(") SANGKATUTAK NA FISHBALLS D 130.00", Box(79, 349, 257, 364)),
            OcrLine("1 BEEF SPARERIBS CALDERE TA D 360.00", Box(79, 361, 257, 377)),
            OcrLine("D.00", Box(237, 329, 258, 339)),
            OcrLine("64.00", Box(232, 342, 258, 351)),
        )

        assertEquals(listOf(
            "1 FREE Caiote Frit D D.00",
            "2 Steamed Rice D e32.00 64.00",
            ") SANGKATUTAK NA FISHBALLS D 130.00",
            "1 BEEF SPARERIBS CALDERE TA D 360.00",
        ), ReceiptRows.reconstruct(lines))
    }

    @Test fun enlargedImageSkewKeepsSummaryAmountsWithTheirLabels() {
        val lines = listOf(
            OcrLine("ITEMS ORDERED", Box(116, 102, 193, 114)),
            OcrLine("1 SLAMDUNK WINGS", Box(120, 125, 212, 140)),
            OcrLine("1 LUMPIA", Box(121, 138, 164, 152)),
            OcrLine("SUBTOTAL", Box(108, 248, 209, 261)),
            OcrLine("GST 5%", Box(133, 263, 211, 275)),
            OcrLine("1 GRILLED PRK LIENPO", Box(122, 151, 235, 167)),
            OcrLine("1 GRILLED CHX INASAL", Box(121, 164, 236, 180)),
            OcrLine("1 SIZZLING SISIG", Box(120, 177, 215, 192)),
            OcrLine("1 UBE CAKE", Box(120, 190, 177, 204)),
            OcrLine("TOTAL DUE", Box(94, 307, 213, 321)),
            OcrLine("AMOUNT", Box(284, 84, 322, 103)),
            OcrLine("17.00", Box(295, 111, 326, 126)),
            OcrLine("15.00", Box(296, 124, 326, 139)),
            OcrLine("22.00", Box(296, 137, 329, 153)),
            OcrLine("24.00", Box(298, 150, 329, 166)),
            OcrLine("24.50", Box(300, 163, 333, 180)),
            OcrLine("11.50", Box(303, 176, 336, 195)),
            OcrLine("114.O0", Box(266, 235, 341, 258)),
            OcrLine("5.70", Box(290, 250, 343, 270)),
            OcrLine("119.70", Box(271, 295, 349, 317)),
        )

        assertEquals(listOf("SUBTOTAL 114.O0", "GST 5% 5.70", "TOTAL DUE 119.70"),
            ReceiptRows.reconstruct(lines).takeLast(3))
    }

    @Test fun skewedColumnsKeepEachPriceWithItsPrintedItem() {
        val rows = ReceiptRows.reconstruct(skewedItemArea().reversed())

        assertEquals(listOf(
            "ITEMS ORDERED AMOUNT",
            "1 SLAMDUNK WINGS 17.00",
            "1 LUMPIA 15.00",
            "1 GRILLED PRK LIENPO 22.00",
            "1 GRILLED CHX INASAL 24.00",
            "1 SIZZLING SISIG 24.50",
            "1 UBE CAKE 11.50",
            "SUBTOTAL 114.O0",
            "GST 5% 5.70",
            "TOTAL DUE 119. 70",
        ), rows)
    }

    @Test fun missingPriceDoesNotShiftRemainingPricesToAdjacentItems() {
        val rows = ReceiptRows.reconstruct(skewedItemArea().filterNot { it.text == "22.00" })

        assertTrue(rows.contains("1 GRILLED PRK LIENPO"))
        assertTrue(rows.contains("1 LUMPIA 15.00"))
        assertTrue(rows.contains("1 GRILLED CHX INASAL 24.00"))
        assertTrue(rows.contains("1 UBE CAKE 11.50"))
    }

    @Test fun misleadingHeadersDoNotSkewHorizontalItems() {
        val lines = listOf(
            OcrLine("ITEMS ORDERED", Box(0, 10, 100, 30)),
            OcrLine("AMOUNT", Box(280, 0, 320, 20)),
            OcrLine("1 Soup", Box(0, 50, 100, 60)),
            OcrLine("10.00", Box(280, 50, 320, 60)),
            OcrLine("1 Rice", Box(0, 80, 100, 90)),
            OcrLine("20.00", Box(280, 80, 320, 90)),
            OcrLine("1 Tea", Box(0, 110, 100, 120)),
            OcrLine("30.00", Box(280, 110, 320, 120)),
        )

        assertEquals(listOf("1 Soup 10.00", "1 Rice 20.00", "1 Tea 30.00"),
            ReceiptRows.reconstruct(lines).takeLast(3))
    }

    @Test fun preservesQuantityDetailsOnSeparateRows() {
        val lines = listOf(
            OcrLine("CAFE", Box(0, 0, 100, 20)),
            OcrLine("Rice", Box(0, 30, 100, 50)),
            OcrLine("70.00", Box(400, 62, 480, 82)),
            OcrLine("2.0", Box(0, 60, 40, 80)),
            OcrLine("x", Box(100, 61, 120, 81)),
            OcrLine("35.00", Box(250, 60, 320, 80)),
            OcrLine("TOTAL 70.00", Box(0, 90, 480, 110)),
        )

        assertEquals(listOf("CAFE", "Rice", "2.0 x 35.00 70.00", "TOTAL 70.00"),
            ReceiptRows.reconstruct(lines))
    }

    // Item/summary text and boxes only, transcribed from OCR; no photo or personal metadata.
    private fun skewedItemArea() = listOf(
        OcrLine("ITEMS ORDERED", Box(116, 101, 192, 115)),
        OcrLine("AMOUNT", Box(284, 84, 320, 103)),
        OcrLine("1 SLAMDUNK WINGS", Box(116, 124, 211, 139)),
        OcrLine("1 LUMPIA", Box(121, 138, 164, 152)),
        OcrLine("1 GRILLED PRK LIENPO", Box(116, 151, 235, 166)),
        OcrLine("1 GRILLED CHX INASAL", Box(116, 164, 236, 179)),
        OcrLine("1 SIZZLING SISIG", Box(116, 177, 214, 191)),
        OcrLine("1 UBE CAKE", Box(116, 191, 176, 204)),
        OcrLine("17.00", Box(295, 110, 324, 125)),
        OcrLine("15.00", Box(297, 123, 325, 138)),
        OcrLine("22.00", Box(297, 136, 329, 153)),
        OcrLine("24.00", Box(298, 149, 330, 166)),
        OcrLine("24.50", Box(300, 163, 331, 179)),
        OcrLine("11.50", Box(303, 176, 335, 195)),
        OcrLine("SUBTOTAL", Box(107, 247, 209, 261)),
        OcrLine("114.O0", Box(266, 235, 342, 258)),
        OcrLine("GST 5%", Box(134, 264, 211, 276)),
        OcrLine("5.70", Box(290, 251, 343, 269)),
        OcrLine("TOTAL DUE", Box(94, 307, 213, 321)),
        OcrLine("119. 70", Box(275, 292, 350, 317)),
    )
}
