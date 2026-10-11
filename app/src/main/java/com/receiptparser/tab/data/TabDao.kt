package com.receiptparser.tab.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface TabDao {
    @Query("SELECT (SELECT COUNT(*) FROM friend) + (SELECT COUNT(*) FROM receipt) + (SELECT COUNT(*) FROM line_item) + (SELECT COUNT(*) FROM item_share) + (SELECT COUNT(*) FROM receipt_participant) + (SELECT COUNT(*) FROM payment)")
    fun observeChanges(): Flow<Long>
    @Query("SELECT * FROM friend ORDER BY lastUsedAt DESC, name COLLATE NOCASE") suspend fun friends(): List<FriendEntity>
    @Query("SELECT * FROM receipt ORDER BY takenAt DESC") suspend fun receipts(): List<ReceiptEntity>
    @Query("SELECT * FROM receipt_participant") suspend fun participants(): List<ParticipantEntity>
    @Query("SELECT * FROM line_item ORDER BY sortOrder") suspend fun items(): List<LineItemEntity>
    @Query("SELECT * FROM item_share") suspend fun shares(): List<ShareEntity>
    @Query("SELECT * FROM payment ORDER BY paidAt DESC") suspend fun payments(): List<PaymentEntity>
    @Query("SELECT * FROM restore_state WHERE id = 1") suspend fun restoreState(): RestoreStateEntity?
    @Upsert suspend fun putRestoreState(state: RestoreStateEntity)
    @Query("DELETE FROM restore_state") suspend fun clearRestoreState()
    @Query("DELETE FROM payment") suspend fun clearPayments()
    @Query("DELETE FROM receipt") suspend fun clearReceipts()
    @Query("DELETE FROM friend") suspend fun clearFriends()
    @Insert suspend fun putFriends(rows: List<FriendEntity>)
    @Insert suspend fun putReceipts(rows: List<ReceiptEntity>)
    @Insert suspend fun putPayments(rows: List<PaymentEntity>)
    @Upsert suspend fun putFriend(friend: FriendEntity)
    @Upsert suspend fun putReceipt(receipt: ReceiptEntity)
    @Insert suspend fun putParticipants(rows: List<ParticipantEntity>)
    @Insert suspend fun putItems(rows: List<LineItemEntity>)
    @Insert suspend fun putShares(rows: List<ShareEntity>)
    @Insert suspend fun putPayment(payment: PaymentEntity)
    @Query("DELETE FROM payment WHERE id = :id") suspend fun deletePayment(id: String)
    @Query("DELETE FROM receipt WHERE id = :id") suspend fun deleteReceipt(id: String)
    @Query("DELETE FROM line_item WHERE receiptId = :id") suspend fun deleteItems(id: String)
    @Query("DELETE FROM receipt_participant WHERE receiptId = :id") suspend fun deleteParticipants(id: String)
}
