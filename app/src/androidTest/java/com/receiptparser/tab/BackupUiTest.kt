package com.receiptparser.tab

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptparser.tab.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unfinishedDraftExplainsAndBlocksBothActions() {
        show(BackupUiState(hasDraft = true))
        compose.onNodeWithText("Finish or discard your receipt draft before creating or restoring a backup.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create backup").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Choose backup").performScrollTo().assertIsNotEnabled()
    }

    @Test fun choosingBackupDoesNotRestoreUntilExplicitConfirmation() {
        var restores = 0
        var cancels = 0
        show(BackupUiState(preview = BackupPreviewSummary(1_700_000_000_000, 3, 4, 2, 1)),
            onRestore = { restores++ }, onCancel = { cancels++ })
        compose.onNodeWithText("3 receipts · 4 friends · 2 payments · 1 photo").assertIsDisplayed()
        screenshot("backup-confirmation")
        compose.onNodeWithText("This replaces all receipts, friends, payments, photos and saved settings on this device. It does not merge them. This cannot be undone.")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, restores) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, restores) }
        compose.onNodeWithText("Replace and restore").performClick()
        compose.runOnIdle { assertEquals(1, restores) }
    }

    @Test fun busyConfirmationPreventsDuplicateRestoreAndCancel() {
        show(BackupUiState(busy = true, progress = "Restoring backup…",
            preview = BackupPreviewSummary(1_700_000_000_000, 1, 1, 1, 0)))
        compose.onNodeWithText("Restoring…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test fun largeTextKeepsConfirmationActionsReachableAndErrorsVisible() {
        show(BackupUiState(error = "Couldn’t read this backup. Choose another file.",
            preview = BackupPreviewSummary(1_700_000_000_000, 20, 15, 40, 20)), fontScale = 1.8f)
        compose.onNodeWithText("Replace and restore").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        screenshot("backup-confirmation-large-text")
        compose.onNodeWithText("Couldn’t read this backup. Choose another file.")
            .performScrollTo().assertIsDisplayed()
    }

    private fun show(
        state: BackupUiState,
        fontScale: Float = 1f,
        onRestore: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TabTheme {
                    BackupContent(state, {}, {}, {}, onRestore, onCancel, {}, {})
                }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose idleness does not wait for the native dialog window entrance animation.
        android.os.SystemClock.sleep(400)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/$name.png"),
        ).use { it.readBytes() }
    }
}
