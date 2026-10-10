package com.receiptparser.tab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.After
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.receiptparser.tab.data.SettingsStore
import kotlinx.coroutines.runBlocking
import androidx.test.rule.GrantPermissionRule
import android.Manifest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/$name.png")).use { it.readBytes() }
    }
    @After fun clearUnfinishedTestDraft() = runBlocking { screenshot("tab-last-state"); SettingsStore(ApplicationProvider.getApplicationContext<Context>()).saveDraft(null) }
    @Test fun invalidQuantitySurvivesRecreationWithoutEnablingContinue() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Enter manually").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enter manually").performScrollTo().performClick()
        compose.onNodeWithText("Add item").performScrollTo().performClick()
        compose.onNodeWithText("Item name").performTextInput("Rice")
        compose.onNodeWithText("Line total").performTextReplacement("20.00")
        compose.onNodeWithText("Printed total").performScrollTo().performTextReplacement("20.00")
        compose.onNodeWithText("Qty").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Assign items").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Assign items").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Qty").performScrollTo().performTextReplacement("1")
        compose.onNodeWithText("Line total").performTextReplacement("invalid")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Line total").performScrollTo().assertTextContains("invalid")
        compose.onNodeWithText("Assign items").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("Discard draft").performClick()
    }
    @Test fun manualReceiptFriendPaymentAndEditing() {
        val name = "Carlo"
        compose.waitUntil(15000) { compose.onAllNodesWithText("Enter manually").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Friends", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Add friend").performClick()
        compose.onNodeWithText("Friend’s name").performTextInput(name)
        compose.onAllNodesWithText("Add friend").onLast().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Capture", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Enter manually").performScrollTo().performClick()
        compose.onNodeWithText("Venue").performTextInput("Sunday lunch")
        compose.onNodeWithText("Add item").performScrollTo().performClick()
        compose.onNodeWithText("Item name").performTextInput("Chicken inasal")
        compose.onNodeWithText("Line total").performTextReplacement("200.00")
        compose.onNodeWithText("Printed total").performScrollTo().performTextReplacement("200.00")
        compose.onNodeWithText("Assign items").performScrollTo().performClick()
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithText("Save split").performScrollTo()
        screenshot("tab-assign")
        compose.onNodeWithText("Save split").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Your tab").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithText("Record payment").performClick()
        compose.onNodeWithText("Amount received").performTextReplacement("40.00")
        compose.onNodeWithText("Save payment").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("₱60.00").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Show breakdown").performScrollTo().performClick()
        compose.onNodeWithText("Edit receipt").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Edit the receipt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Line total").performScrollTo().performTextReplacement("300.00")
        compose.onNodeWithText("Printed total").performScrollTo().performTextReplacement("300.00")
        compose.onNodeWithText("Assign items").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("₱110.00").fetchSemanticsNodes().isNotEmpty() }
        screenshot("tab-ledger")
        compose.onNodeWithText(name).performScrollTo().performClick()
        screenshot("tab-friend")
    }
}
