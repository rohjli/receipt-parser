package com.receiptparser.tab.data

import androidx.room.withTransaction
import com.receiptparser.tab.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TabRepository @Inject constructor(private val db: TabDatabase) {
    private val dao get() = db.dao()
    val snapshots = dao.observeChanges().map { snapshot() }
    suspend fun snapshot(): LedgerSnapshot = db.withTransaction { LedgerSnapshot(dao.friends(), dao.receipts(), dao.participants(), dao.items(), dao.shares(), dao.payments()) }
    suspend fun addFriend(name: String): String = db.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 60) { "Use a name between 1 and 60 characters." }
        require(dao.friends().none { it.name.equals(trimmed, true) }) { "That name is already in your friends, possibly archived." }
        val id = UUID.randomUUID().toString()
        dao.putFriend(FriendEntity(id, trimmed, color = dao.friends().size % 6))
        id
    }
    suspend fun updateFriend(friend: FriendEntity) = db.withTransaction {
        require(friend.name.trim().isNotEmpty() && friend.name.length <= 60) { "Enter a name of 1–60 characters." }
        require(dao.friends().none { it.id != friend.id && it.name.equals(friend.name.trim(), true) }) { "That name is already in your friends." }
        dao.putFriend(friend.copy(name = friend.name.trim()))
    }
    suspend fun save(draft: ReceiptDraft) {
        val split = SplitEngine.split(draft)
        require(draft.venue.length <= 120) { "Keep the venue under 120 characters." }
        db.withTransaction {
            val friends = dao.friends().associateBy { it.id }
            require(draft.participants.all { it.id in friends }) { "A selected friend no longer exists. Check the group." }
            val existing = dao.receipts().firstOrNull { it.id == draft.id }
            require(!draft.editing || existing != null) { "This receipt was deleted. Start a new receipt." }
            dao.putReceipt(ReceiptEntity(draft.id, existing?.takenAt ?: draft.takenAt, draft.venue.trim().ifEmpty { "Dinner" }, draft.imagePath, draft.rawText,
                Json.encodeToString(draft.ocrLines), Json.encodeToString(draft.skippedRows), draft.subtotal, draft.serviceCharge, draft.vat, draft.vatMode.name,
                draft.discount, draft.total, draft.reconciled, draft.feeMode.name))
            dao.deleteItems(draft.id)
            dao.deleteParticipants(draft.id)
            dao.putItems(draft.items.mapIndexed { index, item -> LineItemEntity(item.id, draft.id, item.name.trim(), item.qty, item.lineTotal / item.qty, item.lineTotal, item.rawRow, index, Json.encodeToString(item.owners)) })
            dao.putShares(split.items.map { ShareEntity(it.itemId, it.personId, it.amount) })
            dao.putParticipants(split.people.map { person -> ParticipantEntity(draft.id, person.personId, draft.participants.find { it.id == person.personId }?.paysFees ?: true, person.fee, person.discount, person.rounding) })
            draft.participants.forEach { p -> dao.putFriend(friends.getValue(p.id).copy(lastUsedAt = System.currentTimeMillis())) }
        }
    }
    suspend fun deleteReceipt(id: String) = db.withTransaction { dao.deleteReceipt(id) }
    suspend fun recordPayment(friendId: String, amount: Long, paidAt: Long, note: String): String = db.withTransaction {
        require(amount in 1..Money.MAX) { "Enter a positive amount up to ₱10,000,000." }
        require(paidAt in 0..System.currentTimeMillis()) { "Payment date cannot be in the future." }
        require(note.length <= 250) { "Keep the note under 250 characters." }
        require(dao.friends().any { it.id == friendId }) { "Friend not found." }
        val id = UUID.randomUUID().toString()
        dao.putPayment(PaymentEntity(id, friendId, amount, paidAt, note.trim()))
        id
    }
    suspend fun undoPayment(id: String) = dao.deletePayment(id)
}
