package com.receiptparser.tab

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
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
class GuidedAssignmentTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: TabDatabase
    private lateinit var repository: TabRepository
    private lateinit var settings: SettingsStore
    private lateinit var receipt: ReceiptViewModel
    private lateinit var ledger: LedgerViewModel
    private lateinit var alice: String
    private lateinit var bob: String
    private val viewModels = ViewModelStore()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val saved = AtomicBoolean(false)
    private var previousDraft: ReceiptDraft? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        repository = TabRepository(database)
        settings = SettingsStore(context)
        // Draft recovery uses the application DataStore; keep its existing draft intact.
        runBlocking {
            previousDraft = settings.readDraft()
            settings.saveDraft(null)
            alice = repository.addFriend("Alice")
            bob = repository.addFriend("Bob")
        }
        instrumentation.runOnMainSync {
            receipt = ReceiptViewModel(repository, settings)
            ledger = LedgerViewModel(repository, settings)
            viewModels.put("receipt", receipt)
            viewModels.put("ledger", ledger)
        }
        compose.waitUntil(5_000) {
            receipt.ready && !ledger.loading && ledger.snapshot.friends.size == 2
        }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { viewModels.clear() }
        runBlocking { settings.saveDraft(previousDraft) }
        database.close()
    }

    @Test fun unassignedItemStartsWithEveryoneCheckedAndDoesNotSaveTheReceipt() {
        showAssignment()

        compose.onNodeWithText("Who had this?").assertIsDisplayed()
        compose.onNodeWithText("Item 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Noodles").performScrollTo().assertIsDisplayed()
        person("You").assertIsOn()
        person("Alice").assertIsOn()
        person("Bob").assertIsOn()
        listOf("You", "Alice", "Bob").forEach { name ->
            attendee(name).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Paying").assertExists()
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        if (rootBounds.right - rootBounds.left >= 360.dp) {
            val youTop = attendee("You").getUnclippedBoundsInRoot().top
            val addTop = compose.onNodeWithText("Add people").getUnclippedBoundsInRoot().top
            assertTrue("Add people should share the avatar row at standard widths", kotlin.math.abs((youTop - addTop).value) <= 1f)
        }
        compose.onNodeWithText("Everyone").assertExists()
        compose.onNodeWithText("Just me").assertExists()
        compose.onNodeWithText("Next item").assertIsEnabled()
        compose.onNodeWithText("Save split").assertDoesNotExist()
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/avatar-three-person.png"),
        ).use { it.readBytes() }
    }

    @Test fun largeTextKeepsOwnersReachableAndProgressActionVisible() {
        showAssignment(fontScale = 1.8f)
        listOf("You", "Alice", "Bob").forEach { name ->
            attendee(name).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Paying").assertExists()
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        if (rootBounds.right - rootBounds.left >= 360.dp) {
            val youTop = attendee("You").getUnclippedBoundsInRoot().top
            val aliceTop = attendee("Alice").getUnclippedBoundsInRoot().top
            assertTrue("Large text should keep two attendee columns at standard widths", kotlin.math.abs((youTop - aliceTop).value) <= 1f)
        }
        compose.onNodeWithContentDescription("Edit people on this receipt")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("Add people").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasText("Add friend") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        val longName = "Alexandra Beatrice Santos"
        compose.onNodeWithText("Friend’s name").performTextInput(longName)
        compose.onAllNodes(hasText("Add friend") and hasClickAction() and hasAnyAncestor(isDialog()))
            .onLast().performClick()
        compose.waitUntil(5_000) {
            receipt.draft?.participants?.size == 3 && !ledger.busy
        }
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        attendee(longName).performScrollTo().assertIsDisplayed()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/avatar-large-text.png"),
        ).use { it.readBytes() }
        person("Bob").performScrollTo().assertIsDisplayed().performClick().assertIsOff()
        compose.onNodeWithText("Next item").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("Item 2 of 3").performScrollTo().assertIsDisplayed()
        person("Alice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next item").assertIsDisplayed()
    }

    @Test fun clearingEveryPersonBlocksProgressAndPreservesTheLastValidStoredAssignment() {
        showAssignment()
        compose.onNodeWithText("Just me").performScrollTo().performClick()
        awaitStoredOwners("noodles", setOf(SELF))

        person("You").performScrollTo().performClick().assertIsOff()
        person("Alice").assertIsOff()
        person("Bob").assertIsOff()
        compose.onNodeWithText("Choose at least one person, or tap Everyone.").assertExists()
        compose.onNodeWithText("Next item").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(setOf(SELF), receipt.draft!!.items.first().owners)
        }
        assertEquals(setOf(SELF), runBlocking { settings.readDraft() }!!.items.first().owners)

        compose.onNodeWithText("Everyone").performScrollTo().performClick()
        person("You").assertIsOn()
        person("Alice").assertIsOn()
        person("Bob").assertIsOn()
        compose.onNodeWithText("Next item").assertIsEnabled().performClick()
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Just me").performScrollTo().performClick()
        awaitStoredOwners("tea", setOf(SELF))
        person("You").performScrollTo().performClick()
        compose.onNodeWithText("Review split").assertIsNotEnabled()
        compose.onNodeWithText("Previous item").assertIsNotEnabled()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit people on this receipt").assertIsNotEnabled()
        compose.onNodeWithText("Add people").assertIsNotEnabled()
        compose.onNodeWithText("View all items").assertIsNotEnabled()
        Espresso.pressBack()
        compose.onNodeWithText("Item 3 of 3").performScrollTo().assertIsDisplayed()
        person("You").assertIsOff()
        person("Alice").assertIsOff()
        person("Bob").assertIsOff()
        compose.runOnIdle {
            assertEquals(setOf(SELF), receipt.draft!!.items.last().owners)
        }
        assertEquals(setOf(SELF), runBlocking { settings.readDraft() }!!.items.last().owners)
        assertFalse(saved.get())
    }

    @Test fun stateRestorationPreservesTheCurrentItemAndItsEmptyPendingSelection() {
        val restoration = StateRestorationTester(compose)
        showAssignment(restoration)
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Just me").performScrollTo().performClick()
        awaitStoredOwners("rice", setOf(SELF))
        person("You").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Next item").assertIsNotEnabled()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Item 2 of 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rice").assertExists()
        person("You").assertIsOff()
        person("Alice").assertIsOff()
        person("Bob").assertIsOff()
        compose.onNodeWithText("Next item").assertIsNotEnabled()
        compose.onNodeWithText("Previous item").assertIsNotEnabled()
        compose.onNodeWithText("Choose at least one person, or tap Everyone.").assertExists()
        awaitStoredOwners("rice", setOf(SELF))

        compose.onNodeWithText("Everyone").performScrollTo().performClick()
        person("You").assertIsOn()
        person("Alice").assertIsOn()
        person("Bob").assertIsOn()
        compose.onNodeWithText("Next item").assertIsEnabled().performClick()
        compose.onNodeWithText("Item 3 of 3").assertIsDisplayed()
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())
    }

    @Test fun previousItemAndSummaryEditingRestoreEachItemsSelections() {
        showAssignment()
        compose.onNodeWithText("Just me").performScrollTo().performClick()
        compose.onNodeWithText("Next item").performClick()
        person("You").performScrollTo().performClick()
        person("Bob").performScrollTo().performClick()
        awaitStoredOwners("rice", setOf(alice))
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Item 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("Previous item").performClick()

        compose.onNodeWithText("Item 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Rice").assertIsDisplayed()
        person("You").assertIsOff()
        person("Alice").assertIsOn()
        person("Bob").assertIsOff()
        compose.onNodeWithText("View all items").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Noodles").performScrollTo().performClick()

        compose.onNodeWithText("Item 1 of 3").assertIsDisplayed()
        person("You").assertIsOn()
        person("Alice").assertIsOff()
        person("Bob").assertIsOff()
        person("Alice").performScrollTo().performClick()
        awaitStoredOwners("noodles", setOf(SELF, alice))
        compose.onNodeWithText("View all items").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Noodles").performScrollTo().performClick()
        person("You").assertIsOn()
        person("Alice").assertIsOn()
        person("Bob").assertIsOff()
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())
    }

    @Test fun singleOwnerAndSharedItemsSaveExactSharesOnlyAfterReview() {
        val draft = showAssignment()
        person("You").performScrollTo().performClick()
        person("Bob").performScrollTo().performClick()
        awaitStoredOwners("noodles", setOf(alice))
        compose.onNodeWithText("Next item").performClick()
        person("You").performScrollTo().performClick()
        awaitStoredOwners("rice", setOf(alice, bob))
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Just me").performScrollTo().performClick()
        awaitStoredOwners("tea", setOf(SELF))
        compose.onNodeWithText("Save split").assertDoesNotExist()
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())

        compose.onNodeWithText("Review split").assertIsEnabled().performClick()
        compose.onNodeWithText("Save split").assertIsEnabled()
        assertFalse(saved.get())
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())
        compose.onNodeWithText("Save split").performClick()
        compose.waitUntil(5_000) { saved.get() }

        val snapshot = runBlocking { repository.snapshot() }
        assertEquals(listOf(draft.id), snapshot.receipts.map { it.id })
        assertEquals(4, snapshot.shares.size)
        assertEquals(15_000L, snapshot.receiptAmount(draft.id, alice))
        assertEquals(3_000L, snapshot.receiptAmount(draft.id, bob))
        assertEquals(3_000L, snapshot.receiptAmount(draft.id, SELF))
        assertEquals(21_000L, snapshot.shares.sumOf { it.amount })
        val restored = snapshot.draft(draft.id)
        assertEquals(setOf(alice), restored.items.single { it.id == "noodles" }.owners)
        assertEquals(setOf(alice, bob), restored.items.single { it.id == "rice" }.owners)
        assertEquals(setOf(SELF), restored.items.single { it.id == "tea" }.owners)
        assertNull(runBlocking { settings.readDraft() })
    }

    @Test fun bothPeopleActionsChangeParticipantsWithoutAdvancingTheCurrentItem() {
        showAssignment()
        compose.onNodeWithText("Next item").performClick()
        compose.onNodeWithText("Everyone").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit people on this receipt").performScrollTo().performClick()
        compose.onNode(hasText("Add friend") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText("Bob") and isToggleable() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick().assertIsOff()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()

        compose.onNodeWithText("Item 2 of 3").performScrollTo().assertIsDisplayed()
        person("You").performScrollTo().assertIsOn()
        person("Alice").performScrollTo().assertIsOn()
        person("Bob").assertDoesNotExist()
        compose.runOnIdle {
            val draft = requireNotNull(receipt.draft)
            assertEquals(listOf(alice), draft.participants.map { it.id })
            assertTrue(draft.items.single { it.id == "rice" }.owners.isEmpty())
            assertEquals(listOf(12_000L, 6_000L, 3_000L), draft.items.map { it.lineTotal })
            assertEquals(21_000L, SplitEngine.split(draft).items.sumOf { it.amount })
        }

        compose.onNodeWithText("Add people").performScrollTo().performClick()
        compose.onNode(hasText("Bob") and isToggleable() and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick().assertIsOn()
        compose.onNode(hasText("Done") and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        compose.onNodeWithText("Item 2 of 3").performScrollTo().assertIsDisplayed()
        person("You").performScrollTo().assertIsOn()
        person("Alice").performScrollTo().assertIsOn()
        person("Bob").performScrollTo().assertIsOn()
        compose.runOnIdle {
            val draft = requireNotNull(receipt.draft)
            assertEquals(listOf(alice, bob), draft.participants.map { it.id })
            assertTrue(draft.items.single { it.id == "rice" }.owners.isEmpty())
            assertEquals(21_000L, draft.total)
            assertEquals(21_000L, SplitEngine.split(draft).items.sumOf { it.amount })
        }
        assertTrue(runBlocking { repository.snapshot() }.receipts.isEmpty())
    }

    private fun showAssignment(restoration: StateRestorationTester? = null, fontScale: Float = 1f): ReceiptDraft {
        val draft = ReceiptDraft(
            venue = "Guided assignment",
            items = listOf(
                ReceiptItem(id = "noodles", name = "Noodles", lineTotal = 12_000),
                ReceiptItem(id = "rice", name = "Rice", lineTotal = 6_000),
                ReceiptItem(id = "tea", name = "Tea", lineTotal = 3_000),
            ),
            total = 21_000,
        )
        val started = AtomicBoolean(false)
        instrumentation.runOnMainSync { receipt.start(draft) { started.set(true) } }
        compose.waitUntil(5_000) { started.get() && !receipt.busy }
        instrumentation.runOnMainSync {
            receipt.change { it.copy(participants = listOf(Participant(alice), Participant(bob))) }
        }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TabTheme {
                    AssignScreen(receipt, ledger, back = {}, editItems = {}, done = { saved.set(true) })
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        return draft
    }

    private fun attendee(name: String) = compose.onNode(
        hasText(name) and !isToggleable() and !hasClickAction(),
    )

    private fun person(name: String) = compose.onNode(hasText(name) and isToggleable())

    private fun awaitStoredOwners(itemId: String, owners: Set<String>) {
        compose.runOnIdle {
            assertEquals(owners, receipt.draft!!.items.single { it.id == itemId }.owners)
        }
        compose.waitUntil(5_000) {
            runBlocking { settings.readDraft() }?.items?.single { it.id == itemId }?.owners == owners
        }
    }
}
