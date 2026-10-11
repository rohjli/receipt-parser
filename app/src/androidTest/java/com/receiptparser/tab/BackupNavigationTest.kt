package com.receiptparser.tab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Hilt/navigation composition without modifying the installed ledger. */
@RunWith(AndroidJUnit4::class)
class BackupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsOpensBackupAndRecreationKeepsThatDestination() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Open backup & restore").performScrollTo().performClick()
        compose.onNodeWithText("Keep a copy of your shared meals and payments.").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Keep a copy of your shared meals and payments.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Keep a copy of your shared meals and payments.").assertIsDisplayed()
        compose.onNodeWithText("Create backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose backup").performScrollTo().assertIsDisplayed()
    }
}
