package com.receiptparser.tab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class BackupPreviewSummary(
    val createdAt: Long,
    val receipts: Int,
    val friends: Int,
    val payments: Int,
    val photos: Int,
)

data class BackupUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val recoveryRequired: Boolean = false,
    val hasDraft: Boolean = false,
    val enabled: Boolean = true,
    val progress: String? = null,
    val error: String? = null,
    val message: String? = null,
    val preview: BackupPreviewSummary? = null,
)

@Composable
fun BackupScreen(
    vm: BackupViewModel,
    hasDraft: Boolean,
    onCreateBackup: () -> Unit,
    onChooseBackup: () -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true,
) {
    val preview = vm.preview?.manifest?.let {
        BackupPreviewSummary(it.createdAt, it.ledger.receipts.size, it.ledger.friends.size,
            it.ledger.payments.size, it.images.size)
    }
    BackupContent(
        state = BackupUiState(
            loading = vm.loading,
            busy = vm.busy,
            recoveryRequired = vm.recoveryRequired,
            hasDraft = hasDraft,
            enabled = enabled,
            progress = vm.progress,
            error = vm.error,
            message = vm.message,
            preview = preview,
        ),
        onCreateBackup = onCreateBackup,
        onChooseBackup = onChooseBackup,
        onBack = { vm.cancelPreview(); onBack() },
        onRestore = { if (enabled && !hasDraft) vm.restore() },
        onCancel = vm::cancelPreview,
        onDismissError = vm::clearError,
        onRetryRecovery = vm::retryRecovery,
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    onCreateBackup: () -> Unit,
    onChooseBackup: () -> Unit,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onRetryRecovery: () -> Unit,
) {
    val available = state.enabled && !state.loading && !state.busy && !state.recoveryRequired && !state.hasDraft
    Page {
        TextButton(onClick = onBack, enabled = !state.busy && !state.loading && !state.recoveryRequired) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text("Settings")
        }
        Heading("Backup & restore", "Keep a copy of your shared meals and payments.")
        if (state.preview == null) ErrorNotice(state.error, onDismissError)
        state.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.primary) }
        state.progress?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.hasDraft) {
            Panel { Text("Finish or discard your receipt draft before creating or restoring a backup.") }
        } else if (!state.enabled) {
            Text("Wait for the current operation to finish before using backups.")
        }
        if (state.recoveryRequired && !state.loading) {
            Panel {
                Text("Your ledger needs to finish recovering before you can continue.")
                PrimaryAction("Retry recovery", onRetryRecovery, busy = state.busy)
            }
        }
        Panel {
            SectionLabel(Icons.Outlined.Backup, "Create a backup")
            Text("Includes saved receipts and their photos, friends, payments and saved settings. Unfinished drafts and unsaved settings changes are not included.")
            Text("The file contains personal information, including payment details and your GCash number. It is not encrypted. Save it somewhere only you trust.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryAction("Create backup", onCreateBackup, enabled = available && state.preview == null)
        }
        Panel {
            SectionLabel(Icons.Outlined.Restore, "Restore from a backup")
            Text("Choose a Tungaon backup to check its contents before replacing this device’s ledger and saved settings.")
            OutlinedButton(onClick = onChooseBackup, enabled = available && state.preview == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Choose backup") }
        }
        Text("Back up regularly and before uninstalling Tungaon or changing phones.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) onCancel() },
            title = { Text("Replace this device’s ledger?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backup from ${dateText(preview.createdAt)}")
                    Text(listOf(count(preview.receipts, "receipt"), count(preview.friends, "friend"),
                        count(preview.payments, "payment"), count(preview.photos, "photo")).joinToString(" · "))
                    Text("Saved settings are included.")
                    Text("This replaces all receipts, friends, payments, photos and saved settings on this device. It does not merge them. This cannot be undone.")
                    Text("Create a backup of your current ledger first if you want to keep it.")
                    if (state.hasDraft) Text("Finish or discard your receipt draft before restoring.")
                    ErrorNotice(state.error, onDismissError)
                }
            },
            confirmButton = {
                TextButton(onClick = onRestore, enabled = available,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(if (state.busy) "Restoring…" else "Replace and restore")
                }
            },
            dismissButton = { TextButton(onClick = onCancel, enabled = !state.busy) { Text("Cancel") } },
        )
    }
}

private fun count(value: Int, noun: String) = "$value $noun${if (value == 1) "" else "s"}"
