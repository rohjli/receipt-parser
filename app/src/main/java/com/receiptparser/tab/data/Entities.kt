package com.receiptparser.tab.data

import androidx.room.*
import com.receiptparser.tab.domain.*
import kotlinx.serialization.json.Json

@Entity(tableName = "friend")
data class FriendEntity(@PrimaryKey val id: String, val name: String, val color: Int = 0, val gcashHint: String = "", val lastUsedAt: Long = 0, val archived: Boolean = false)
@Entity(tableName = "receipt")
data class ReceiptEntity(
    @PrimaryKey val id: String, val takenAt: Long, val venue: String, val imagePath: String?,
    val rawOcrText: String, val ocrJson: String, val skippedJson: String, val subtotal: Long,
    val serviceCharge: Long, val vat: Long, val vatMode: String, val discount: Long,
    val total: Long, val reconciled: Boolean, val feeSplitMode: String,
)
@Entity(tableName = "receipt_participant", primaryKeys = ["receiptId", "friendId"],
    foreignKeys = [ForeignKey(entity = ReceiptEntity::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE)], indices = [Index("friendId")])
data class ParticipantEntity(val receiptId: String, val friendId: String, val paysFees: Boolean, val feeShare: Long, val discountShare: Long, val rounding: Long)
@Entity(tableName = "line_item", foreignKeys = [ForeignKey(entity = ReceiptEntity::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE)], indices = [Index("receiptId")])
data class LineItemEntity(@PrimaryKey val id: String, val receiptId: String, val name: String, val qty: Int, val unitPrice: Long, val lineTotal: Long, val rawRow: String, val sortOrder: Int, val ownersJson: String)
@Entity(tableName = "item_share", primaryKeys = ["lineItemId", "friendId"], foreignKeys = [ForeignKey(entity = LineItemEntity::class, parentColumns = ["id"], childColumns = ["lineItemId"], onDelete = ForeignKey.CASCADE)], indices = [Index("friendId")])
data class ShareEntity(val lineItemId: String, val friendId: String, val amount: Long)
@Entity(tableName = "payment", foreignKeys = [ForeignKey(entity = FriendEntity::class, parentColumns = ["id"], childColumns = ["friendId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("friendId")])
data class PaymentEntity(@PrimaryKey val id: String, val friendId: String, val amount: Long, val paidAt: Long, val note: String)

data class LedgerSnapshot(
    val friends: List<FriendEntity> = emptyList(), val receipts: List<ReceiptEntity> = emptyList(),
    val participants: List<ParticipantEntity> = emptyList(), val items: List<LineItemEntity> = emptyList(),
    val shares: List<ShareEntity> = emptyList(), val payments: List<PaymentEntity> = emptyList(),
) {
    fun receiptAmount(receiptId: String, personId: String): Long {
        val ids = items.filter { it.receiptId == receiptId }.map { it.id }.toSet()
        val p = participants.find { it.receiptId == receiptId && it.friendId == personId }
        return shares.filter { it.lineItemId in ids && it.friendId == personId }.sumOf { it.amount } +
            (p?.let { it.feeShare - it.discountShare + it.rounding } ?: 0)
    }
    fun balance(id: String) = receipts.sumOf { receiptAmount(it.id, id) } - payments.filter { it.friendId == id }.sumOf { it.amount }
    fun oldestOutstanding(id: String) = Ledger.oldestOutstanding(
        receipts.map { LedgerCharge(receiptAmount(it.id, id), it.takenAt) },
        payments.filter { it.friendId == id }.map { LedgerPayment(it.amount, it.paidAt) },
    )
    fun draft(id: String): ReceiptDraft {
        val r = receipts.first { it.id == id }
        return ReceiptDraft(id = r.id, takenAt = r.takenAt, venue = r.venue, imagePath = r.imagePath,
            rawText = r.rawOcrText, ocrLines = Json.decodeFromString(r.ocrJson),
            items = items.filter { it.receiptId == id }.sortedBy { it.sortOrder }.map { ReceiptItem(it.id, it.name, it.qty, it.lineTotal, it.rawRow, Json.decodeFromString(it.ownersJson)) },
            serviceCharge = r.serviceCharge, vat = r.vat, vatMode = VatMode.valueOf(r.vatMode), discount = r.discount,
            total = r.total, feeMode = FeeMode.valueOf(r.feeSplitMode),
            participants = participants.filter { it.receiptId == id && it.friendId != SELF }.map { Participant(it.friendId, it.paysFees) },
            skippedRows = Json.decodeFromString(r.skippedJson), editing = true)
    }
}
