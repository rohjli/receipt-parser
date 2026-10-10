package com.receiptparser.tab.domain

object Ledger {
    /** Payments are fungible credit: consume the oldest charges first, including prepayments. */
    fun oldestOutstanding(charges: List<LedgerCharge>, payments: List<LedgerPayment>): Long? {
        var credit = payments.sumOf { it.amount } + charges.filter { it.amount < 0 }.sumOf { -it.amount }
        charges.filter { it.amount > 0 }.sortedBy { it.at }.forEach {
            if (credit < it.amount) return it.at
            credit -= it.amount
        }
        return null
    }
    fun isQuietHour(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }
}
