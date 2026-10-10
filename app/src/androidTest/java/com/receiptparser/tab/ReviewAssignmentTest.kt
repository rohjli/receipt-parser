package com.receiptparser.tab

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import com.receiptparser.tab.ui.AssignScreen
import com.receiptparser.tab.ui.LedgerViewModel
import com.receiptparser.tab.ui.ReceiptViewModel
import com.receiptparser.tab.ui.TabTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ReviewAssignmentTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: TabDatabase
    private lateinit var repository: TabRepository
    private lateinit var settings: SettingsStore
    private lateinit var receipt: ReceiptViewModel
    private lateinit var ledger: LedgerViewModel
    private val viewModels = ViewModelStore()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousDraft: ReceiptDraft? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        repository = TabRepository(database)
        settings = SettingsStore(context)
        // SettingsStore uses the application's DataStore. Preserve its draft while
        // keeping every friend, receipt and allocation in this test's in-memory DB.
        runBlocking {
            previousDraft = settings.readDraft()
            settings.saveDraft(null)
        }
        instrumentation.runOnMainSync {
            receipt = ReceiptViewModel(repository, settings)
            ledger = LedgerViewModel(repository, settings)
            viewModels.put("receipt", receipt)
            viewModels.put("ledger", ledger)
        }
        compose.waitUntil(5_000) { receipt.ready && !ledger.loading }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { viewModels.clear() }
        runBlocking { settings.saveDraft(previousDraft) }
        database.close()
    }

    @Test fun multiItemAssignmentAndAttendeeRemovalSaveExactSharesAndRestoreTheGroup() {
        val alice = runBlocking { repository.addFriend("Alice") }
        val bob = runBlocking { repository.addFriend("Bob") }
        val draft = ReceiptDraft(
            venue = "Assignment review",
            items = listOf(
                ReceiptItem(id = "noodles", name = "Noodles", lineTotal = 12_000),
                ReceiptItem(id = "rice", name = "Rice", lineTotal = 6_000),
                ReceiptItem(id = "tea", name = "Tea", lineTotal = 3_000),
            ),
            total = 21_000,
        )
        start(draft)
        compose.waitUntil(5_000) { ledger.snapshot.friends.size == 2 }
        val saved = AtomicBoolean(false)
        compose.setContent {
            TabTheme {
                AssignScreen(receipt, ledger, back = {}, editItems = {}, done = { saved.set(true) })
            }
        }

        compose.onNodeWithText("Add people").performScrollTo().performClick()
        assignmentPerson("Alice").performScrollTo().performClick().assertIsOn()
        assignmentPerson("Bob").performScrollTo().performClick().assertIsOn()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("Noodles").assertIsDisplayed()
        compose.onNode(hasText("You") and isToggleable()).performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Rice").assertIsDisplayed()
        compose.onNode(hasText("You") and isToggleable()).performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Next item").performClick()

        compose.runOnIdle {
            val assigned = requireNotNull(receipt.draft)
            assertEquals(setOf(alice, bob), assigned.items[0].owners)
            assertEquals(setOf(alice, bob), assigned.items[1].owners)
            assertTrue(assigned.items[2].owners.isEmpty())
        }
        compose.onNodeWithText("View all items").performScrollTo().performClick()
        compose.onNodeWithText("The split").performScrollTo()
        compose.onAllNodesWithText("₱100.00").assertCountEquals(2)
        compose.onNodeWithText("₱10.00").assertExists()

        compose.onNodeWithContentDescription("Edit people on this receipt").performScrollTo().performClick()
        assignmentPerson("Bob").performScrollTo().performClick().assertIsOff()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.runOnIdle {
            val remaining = requireNotNull(receipt.draft)
            assertEquals(listOf(alice), remaining.participants.map { it.id })
            assertEquals(setOf(alice), remaining.items[0].owners)
            assertEquals(setOf(alice), remaining.items[1].owners)
            assertTrue(remaining.items[2].owners.isEmpty())
        }
        compose.onNodeWithText("The split").performScrollTo()
        compose.onNodeWithText("₱195.00").assertExists()
        compose.onNodeWithText("₱15.00").assertExists()
        compose.onNodeWithText("Save split").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { saved.get() }

        val snapshot = runBlocking { repository.snapshot() }
        assertEquals(listOf(draft.id), snapshot.receipts.map { it.id })
        assertEquals(3, snapshot.items.size)
        assertEquals(4, snapshot.shares.size)
        assertEquals(setOf(SELF, alice), snapshot.participants.map { it.friendId }.toSet())
        assertEquals(19_500L, snapshot.receiptAmount(draft.id, alice))
        assertEquals(1_500L, snapshot.receiptAmount(draft.id, SELF))
        assertEquals(21_000L, snapshot.shares.sumOf { it.amount })
        assertFalse(snapshot.shares.any { it.friendId == bob })
        assertEquals(setOf(alice), snapshot.draft(draft.id).items[0].owners)
        assertTrue(snapshot.friends.single { it.id == alice }.lastUsedAt > 0)
        assertEquals(0L, snapshot.friends.single { it.id == bob }.lastUsedAt)
        assertNull(runBlocking { settings.readDraft() })

        start(ReceiptDraft(venue = "Next dinner"))
        compose.runOnIdle {
            assertEquals(listOf(alice), requireNotNull(receipt.draft).participants.map { it.id })
        }
    }

    @Test fun startingAReceiptRestoresOnlyActiveFriendsFromTheLatestReceipt() {
        val older = runBlocking { repository.addFriend("Older group") }
        val latest = runBlocking { repository.addFriend("Latest group") }
        val archived = runBlocking { repository.addFriend("Archived attendee") }
        runBlocking {
            repository.save(ReceiptDraft(
                takenAt = 1_000,
                items = listOf(ReceiptItem(name = "Older meal", lineTotal = 10_000)),
                total = 10_000,
                participants = listOf(Participant(older)),
            ))
            repository.save(ReceiptDraft(
                takenAt = 2_000,
                items = listOf(ReceiptItem(name = "Latest meal", lineTotal = 12_000)),
                total = 12_000,
                participants = listOf(Participant(latest), Participant(archived)),
            ))
            val friend = repository.snapshot().friends.single { it.id == archived }
            repository.updateFriend(friend.copy(archived = true))
        }

        val next = ReceiptDraft(venue = "New dinner")
        start(next)

        instrumentation.runOnMainSync {
            assertEquals(listOf(latest), requireNotNull(receipt.draft).participants.map { it.id })
        }
        val persisted = runBlocking { settings.readDraft() }
        assertEquals(next.id, persisted?.id)
        assertEquals(listOf(latest), persisted?.participants?.map { it.id })
    }

    private fun start(draft: ReceiptDraft) {
        val started = AtomicBoolean(false)
        instrumentation.runOnMainSync { receipt.start(draft) { started.set(true) } }
        compose.waitUntil(5_000) { started.get() && !receipt.busy }
    }

    private fun assignmentPerson(name: String) = compose.onNode(
        hasText(name) and isToggleable() and hasAnyAncestor(isDialog()),
    )
}
