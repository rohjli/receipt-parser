package com.receiptparser.tab

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import com.receiptparser.tab.ui.ReceiptViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRecoveryTest {
    @Test fun openingHistoryCannotOverwriteARecoveringDraft() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        val repo = TabRepository(db)
        val settings = SettingsStore(context)
        val original = ReceiptDraft(venue = "Unfinished dinner")
        val saved = ReceiptDraft(venue = "Old dinner", items = listOf(ReceiptItem(name = "Rice", lineTotal = 2000)), total = 2000)
        repo.save(saved)
        settings.saveDraft(original)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: ReceiptViewModel
        val store = ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                vm = ReceiptViewModel(repo, settings)
                store.put("draft", vm)
                vm.edit(saved.id) { }
            }
            val deadline = System.currentTimeMillis() + 5000
            var finished = false
            while (!finished && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { finished = vm.ready && !vm.busy }
                Thread.sleep(10)
            }
            assertTrue("Recovery should finish", finished)
            instrumentation.runOnMainSync { assertEquals(original.id, vm.draft?.id) }
            assertEquals(original.id, settings.readDraft()?.id)
        } finally { instrumentation.runOnMainSync { store.clear() }; settings.saveDraft(null); db.close() }
    }
}
