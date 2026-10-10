package com.receiptparser.tab

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun editAndDeleteAreAtomicAndPreservePayments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TabDatabase::class.java).build()
        try {
            val repo = TabRepository(db)
            val a = repo.addFriend("Carlo")
            val draft = ReceiptDraft(venue = "Dinner", items = listOf(ReceiptItem(name = "Pasta", lineTotal = 20000)), total = 20000, participants = listOf(Participant(a)))
            repo.save(draft)
            assertEquals(10000L, repo.snapshot().balance(a))
            val payment = repo.recordPayment(a, 4000, System.currentTimeMillis(), "GCash")
            assertEquals(6000L, repo.snapshot().balance(a))
            val edit = repo.snapshot().draft(draft.id)
            assertTrue(edit.items.single().owners.isEmpty())
            repo.save(edit.copy(items = edit.items.map { it.copy(lineTotal = 30000) }, total = 30000))
            assertEquals(11000L, repo.snapshot().balance(a))
            assertEquals(1, repo.snapshot().receipts.size)
            assertEquals(2, repo.snapshot().shares.size)
            repo.deleteReceipt(draft.id)
            assertEquals(-4000L, repo.snapshot().balance(a))
            assertEquals(1, repo.snapshot().payments.size)
            repo.undoPayment(payment)
            assertEquals(0L, repo.snapshot().balance(a))
            assertTrue(repo.snapshot().shares.isEmpty())
        } finally { db.close() }
    }
}
