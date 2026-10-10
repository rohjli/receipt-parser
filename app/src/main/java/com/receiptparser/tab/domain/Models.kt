package com.receiptparser.tab.domain

import kotlinx.serialization.Serializable
import java.util.UUID

const val SELF = "__self__"
@Serializable enum class VatMode { INCLUSIVE, ADDITIVE }
@Serializable enum class FeeMode { EQUAL, PROPORTIONAL }
@Serializable data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY get() = (top + bottom) / 2.0
    val height get() = (bottom - top).coerceAtLeast(1)
}
@Serializable data class OcrLine(val text: String, val box: Box)
@Serializable data class ReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val qty: Int = 1,
    val lineTotal: Long = 0,
    val rawRow: String = "",
    val owners: Set<String> = emptySet(),
)
@Serializable data class Participant(val id: String, val paysFees: Boolean = true)
@Serializable data class ReceiptDraft(
    val id: String = UUID.randomUUID().toString(),
    val takenAt: Long = System.currentTimeMillis(),
    val venue: String = "",
    val imagePath: String? = null,
    val rawText: String = "",
    val ocrLines: List<OcrLine> = emptyList(),
    val items: List<ReceiptItem> = emptyList(),
    val serviceCharge: Long = 0,
    val vat: Long = 0,
    val vatMode: VatMode = VatMode.INCLUSIVE,
    val discount: Long = 0,
    val total: Long = 0,
    val feeMode: FeeMode = FeeMode.EQUAL,
    val participants: List<Participant> = emptyList(),
    val skippedRows: List<String> = emptyList(),
    val editing: Boolean = false,
) {
    val subtotal get() = items.sumOf { it.lineTotal }
    val computedTotal get() = subtotal + serviceCharge + (if (vatMode == VatMode.ADDITIVE) vat else 0) - discount
    val reconciled get() = items.isNotEmpty() && total == computedTotal
}
data class ItemShare(val itemId: String, val personId: String, val amount: Long)
data class PersonShare(val personId: String, val items: Long, val fee: Long, val discount: Long, val rounding: Long) {
    val total get() = items + fee - discount + rounding
}
data class SplitResult(val items: List<ItemShare>, val people: List<PersonShare>)
data class LedgerCharge(val amount: Long, val at: Long)
data class LedgerPayment(val amount: Long, val at: Long)
