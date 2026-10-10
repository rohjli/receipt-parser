package com.receiptparser.tab

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import com.receiptparser.tab.ui.HomeScreen
import com.receiptparser.tab.ui.LedgerViewModel
import com.receiptparser.tab.ui.TabTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ClearBalanceSectionsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: TabDatabase
    private lateinit var repository: TabRepository
    private lateinit var settings: SettingsStore
    private lateinit var ledger: LedgerViewModel
    private lateinit var alice: String
    private lateinit var bob: String
    private lateinit var cara: String
    private val viewModels = ViewModelStore()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        repository = TabRepository(database)
        settings = SettingsStore(context)
        runBlocking {
            alice = repository.addFriend("Alice")
            bob = repository.addFriend("Bob")
            cara = repository.addFriend("Cara")
            repository.save(ReceiptDraft(
                venue = "Dinner",
                items = listOf(ReceiptItem(name = "Food", lineTotal = 30000)),
                total = 30000,
                participants = listOf(Participant(alice), Participant(bob)),
            ))
            repository.recordPayment(bob, 15000, System.currentTimeMillis(), "Advance")
        }
        instrumentation.runOnMainSync {
            ledger = LedgerViewModel(repository, settings)
            viewModels.put("ledger", ledger)
        }
        compose.waitUntil(5_000) { !ledger.loading && ledger.snapshot.friends.size == 3 }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { viewModels.clear() }
        database.close()
    }

    @Test fun balancesAppearInSeparateSectionsAndZeroFriendOpensHistory() {
        var openedFriend: String? = null
        runBlocking {
            val friend = repository.snapshot().friends.first { it.id == cara }
            repository.updateFriend(friend.copy(archived = true))
        }
        compose.waitUntil(5_000) { ledger.snapshot.friends.first { it.id == cara }.archived }
        showHome(onFriend = { openedFriend = it })

        compose.onNodeWithContentDescription("Owe you, 1 person").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("You owe, 1 person").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("No balance, 1 person").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nothing owed either way").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Archived").performScrollTo().assertIsDisplayed()
        screenshot("tab-clear-sections")
        compose.onNodeWithContentDescription("View history for Cara").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(cara, openedFriend) }
        compose.onNodeWithText("Credit to return").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Record payment").assertExists()
    }

    @Test fun noBalanceStartsExpandedAndCollapseSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HomeContent() }

        compose.onNodeWithContentDescription("View history for Cara").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("No balance, 1 person").performScrollTo().performClick()
        compose.onNodeWithContentDescription("View history for Cara").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("View history for Cara").assertDoesNotExist()
        compose.onNodeWithContentDescription("No balance, 1 person").performScrollTo().performClick()
        compose.onNodeWithContentDescription("View history for Cara").performScrollTo().assertIsDisplayed()
    }

    @Test fun recordPaymentRequiresSaveAndPartialFullUndoRegroupReactively() {
        val paymentIds = CopyOnWriteArrayList<String>()
        showHome(onPayment = { id, amount, date, note, success ->
            ledger.action {
                paymentIds += repository.recordPayment(id, amount, date, note)
                success()
            }
        })

        compose.onNodeWithText("Record payment").performScrollTo().performClick()
        compose.onNodeWithText("Payment from Alice").assertIsDisplayed()
        assertEquals(10000L, runBlocking { repository.snapshot().balance(alice) })
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(10000L, runBlocking { repository.snapshot().balance(alice) })

        compose.onNodeWithText("Record payment").performScrollTo().performClick()
        compose.onNodeWithText("Amount received").performTextReplacement("40.00")
        compose.onNodeWithText("Save payment").performClick()
        compose.waitUntil(5_000) { repositoryBalance(alice) == 6000L && !ledger.busy }
        compose.onAllNodesWithText("₱60.00").assertCountEquals(2)
        compose.onAllNodesWithText("₱60.00").onLast().performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Owe you, 1 person").assertExists()

        compose.onNodeWithText("Record payment").performScrollTo().performClick()
        compose.onNodeWithText("Amount received").performTextReplacement("60.00")
        compose.onNodeWithText("Save payment").performClick()
        compose.waitUntil(5_000) { repositoryBalance(alice) == 0L && !ledger.busy }
        compose.onNodeWithContentDescription("Owe you, 0 people").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("No balance, 2 people").performScrollTo().assertIsDisplayed()

        runBlocking { repository.undoPayment(paymentIds.last()) }
        compose.waitUntil(5_000) { ledger.snapshot.balance(alice) == 6000L }
        compose.onNodeWithContentDescription("Owe you, 1 person").performScrollTo().assertIsDisplayed()
    }

    @Test fun sectionsRemainUsableAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) { HomeContent() }
        }
        compose.onNodeWithContentDescription("Owe you, 1 person").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Record payment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("No balance, 1 person").performScrollTo().assertIsDisplayed()
        screenshot("tab-clear-sections-large-text")
    }

    @Test fun failedPaymentKeepsDialogFieldsAndDoesNotChangeBalance() {
        showHome(onPayment = { _, _, _, _, _ ->
            ledger.action { throw IllegalStateException("Could not save payment") }
        })

        compose.onNodeWithText("Record payment").performScrollTo().performClick()
        compose.onNodeWithText("Amount received").performTextReplacement("40.00")
        compose.onNodeWithText("Note (optional)").performTextInput("Bank transfer")
        compose.onNodeWithText("Save payment").performClick()
        compose.waitUntil(5_000) { !ledger.busy && ledger.error == "Could not save payment" }
        compose.onNodeWithText("Payment from Alice").assertIsDisplayed()
        compose.onNodeWithText("Amount received").assertTextContains("40.00")
        compose.onNodeWithText("Note (optional)").assertTextContains("Bank transfer")
        compose.onNode(hasText("Could not save payment") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals(10000L, repositoryBalance(alice))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Payment from Alice").assertDoesNotExist()
    }

    private fun repositoryBalance(id: String): Long = runBlocking { repository.snapshot().balance(id) }

    private fun showHome(onFriend: (String) -> Unit = {}) {
        compose.setContent { HomeContent(onFriend) }
    }

    private fun showHome(
        onFriend: (String) -> Unit = {},
        onPayment: (String, Long, Long, String, () -> Unit) -> Unit,
    ) {
        compose.setContent { HomeContent(onFriend, onPayment) }
    }

    @Composable private fun HomeContent(
        onFriend: (String) -> Unit = {},
        onPayment: (String, Long, Long, String, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    ) {
        TabTheme {
            HomeScreen(ledger, SnackbarHostState(), onFriend = onFriend, onEdit = {}, onShare = {},
                nudge = {}, onPayment = onPayment, onCapture = {})
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name.png"),
        ).use { it.readBytes() }
    }
}
