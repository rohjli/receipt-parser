package com.receiptparser.tab.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DomainTest {
    private fun receipt(vararg amounts: Long) = ReceiptDraft(items = amounts.mapIndexed { i, a -> ReceiptItem(id = "i$i", name = "Food", lineTotal = a) }, total = amounts.sum(), participants = listOf(Participant("a"), Participant("b")))
    @Test fun moneyIsExactAndRejectsAmbiguousInput() {
        assertEquals(123456L, Money.parse("₱1,234.56"))
        assertEquals(10L, Money.parse("0.10"))
        listOf("-2", "NaN", "1.234", "", "9,99.00", "10000000000000000000").forEach { assertNull(it, Money.parse(it)) }
        assertEquals("₱1,234.56", Money.format(123456))
        assertEquals("1234.56", Money.input(123456))
    }
    @Test fun threeWayHundredRoundsResidualToRobert() {
        val result = SplitEngine.split(receipt(10000))
        assertEquals(listOf(3400L, 3300L, 3300L), result.people.map { it.total })
        assertEquals(10000L, result.items.sumOf { it.amount })
    }
    @Test fun unassignedIsEveryoneAndMineOnlyIsSelf() {
        val base = receipt(10000, 6000)
        val result = SplitEngine.split(base.copy(items = base.items.mapIndexed { i, x -> if (i == 0) x.copy(owners = setOf(SELF)) else x }))
        assertEquals(12000L, result.people.first().total)
        assertEquals(2000L, result.people[1].total)
    }
    @Test fun itemRemainderGoesToRobertEvenWhenUntagged() {
        val base = receipt(10001)
        val r = SplitEngine.split(base.copy(items = base.items.map { it.copy(owners = setOf("a", "b")) }))
        assertEquals(1L, r.items.single { it.personId == SELF }.amount)
        assertEquals(10001L, r.people.sumOf { it.total })
    }
    @Test fun feeExcludedFriendHasNoFeeAndDiscountIsProportional() {
        val base = receipt(10000, 20000)
        val r = SplitEngine.split(base.copy(items = listOf(base.items[0].copy(owners = setOf("a")), base.items[1].copy(owners = setOf(SELF))), participants = listOf(Participant("a", false)), serviceCharge = 3000, vat = 1200, vatMode = VatMode.ADDITIVE, discount = 3000, total = 31200, feeMode = FeeMode.PROPORTIONAL))
        val a = r.people.single { it.personId == "a" }
        assertEquals(0L, a.fee)
        assertEquals(1000L, a.discount)
        assertEquals(9000L, a.total)
        assertEquals(31200L, r.people.sumOf { it.total })
    }
    @Test fun inclusiveVatIsNotAddedAgainAndZeroWeightFeesAreAllocated() {
        val r = SplitEngine.split(receipt(0).copy(serviceCharge = 900, vat = 300, total = 900, feeMode = FeeMode.PROPORTIONAL))
        assertEquals(listOf(300L, 300L, 300L), r.people.map { it.fee })
    }
    @Test fun mismatchStaysWithRobert() {
        val r = SplitEngine.split(receipt(10000).copy(total = 11000))
        assertEquals(4400L, r.people.first().total)
        assertEquals(11000L, r.people.sumOf { it.total })
    }
    @Test fun invalidParticipantsAndAmountsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SplitEngine.split(receipt(-1)) }
        assertThrows(IllegalArgumentException::class.java) { SplitEngine.split(receipt(1).copy(participants = listOf(Participant("a"), Participant("a")))) }
        val b = receipt(1)
        assertThrows(IllegalArgumentException::class.java) { SplitEngine.split(b.copy(items = b.items.map { it.copy(owners = setOf("missing")) })) }
        assertThrows(IllegalArgumentException::class.java) { SplitEngine.split(b.copy(discount = 2)) }
    }
    @Test fun randomSplitsConserveEveryCentavo() {
        val random = Random(42)
        repeat(1000) {
            val amounts = LongArray(1 + random.nextInt(8)) { random.nextLong(1, 200000) }
            val b = receipt(*amounts).copy(serviceCharge = 137, total = amounts.sum() + 137)
            val r = SplitEngine.split(b)
            assertEquals(b.total, r.people.sumOf { it.total })
            b.items.forEach { item -> assertEquals(item.lineTotal, r.items.filter { it.itemId == item.id }.sumOf { it.amount }) }
        }
    }
    @Test fun parserReconstructsColumnsAndRoutesInclusiveVat() {
        val r = ReceiptParser.parse(listOf(OcrLine("DINNER PLACE", Box(0,0,200,20)), OcrLine("2 Chicken", Box(0,30,150,50)), OcrLine("300.00", Box(350,32,450,52)), OcrLine("Rice 100.00", Box(0,60,450,80)), OcrLine("SC 40.00", Box(0,90,450,110)), OcrLine("VAT 47.14", Box(0,120,450,140)), OcrLine("TOTAL 440.00", Box(0,150,450,170)), OcrLine("CASH 500.00", Box(0,180,450,200)), OcrLine("CHANGE 60.00", Box(0,210,450,230))))
        assertEquals("DINNER PLACE", r.venue)
        assertEquals(2, r.items.size)
        assertEquals(2, r.items[0].qty)
        assertEquals(30000L, r.items[0].lineTotal)
        assertEquals(4000L, r.serviceCharge)
        assertEquals(VatMode.INCLUSIVE, r.vatMode)
        assertTrue(r.reconciled)
    }
    @Test fun parserDetectsAdditiveVatAndDiscount() {
        val r = ReceiptParser.parseText("CAFE\nPasta 100.00\nVAT 12.00\nDiscount 5.00\nTOTAL 107.00")
        assertEquals(VatMode.ADDITIVE, r.vatMode)
        assertEquals(500L, r.discount)
        assertTrue(r.reconciled)
    }
    @Test fun parserDoesNotInventTotalOrDiscardUnparsedRows() {
        val r = ReceiptParser.parseText("CAFE\nPasta 100.00\nUnreadable noodles")
        assertEquals(0L, r.total)
        assertFalse(r.reconciled)
        assertTrue(r.skippedRows.contains("Unreadable noodles"))
    }
    @Test fun paymentOffsetsOldestDebtIncludingPrepayments() {
        val charges = listOf(LedgerCharge(10000, 10), LedgerCharge(20000, 20))
        assertEquals(20L, Ledger.oldestOutstanding(charges, listOf(LedgerPayment(15000, 15))))
        assertNull(Ledger.oldestOutstanding(charges, listOf(LedgerPayment(35000, 0))))
        assertEquals(20L, Ledger.oldestOutstanding(charges, listOf(LedgerPayment(10000, 0))))
    }
    @Test fun quietHoursCrossMidnightAndSameDay() {
        assertTrue(Ledger.isQuietHour(23,23,8)); assertTrue(Ledger.isQuietHour(7,23,8))
        assertFalse(Ledger.isQuietHour(8,23,8)); assertFalse(Ledger.isQuietHour(22,23,8))
        assertTrue(Ledger.isQuietHour(13,12,15)); assertFalse(Ledger.isQuietHour(16,12,15))
        assertFalse(Ledger.isQuietHour(12,12,12))
    }
}
