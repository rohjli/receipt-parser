package com.receiptparser.tab.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptparser.tab.data.BackupService
import com.receiptparser.tab.data.PreparedBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(private val service: BackupService) : ViewModel() {
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var recoveryRequired by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf<String?>(null); private set
    var preview by mutableStateOf<PreparedBackup?>(null); private set
    var restoreCompleted by mutableStateOf(false); private set
    private var restoreNeedsRecovery = false

    init {
        viewModelScope.launch { service.recoveryRequired.collect { recoveryRequired = it } }
        retryRecovery()
    }

    fun retryRecovery() {
        if (busy) return
        busy = true
        loading = true
        error = null
        progress = "Checking your ledger…"
        viewModelScope.launch {
            try {
                service.recoverPending()
                recoveryRequired = service.recoveryRequired.value
                if (restoreNeedsRecovery && !recoveryRequired) {
                    preview?.let { prepared -> withContext(Dispatchers.IO) { service.discard(prepared) } }
                    preview = null
                    restoreNeedsRecovery = false
                    restoreCompleted = true
                    message = "Backup restored."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn’t finish recovering your ledger. Try again."
            } finally {
                loading = false
                busy = false
                progress = null
            }
        }
    }

    fun export(uri: Uri) = operation("Creating backup…") {
        service.exportTo(uri)
        message = "Backup created. Keep the file somewhere safe."
    }

    fun inspect(uri: Uri) = operation("Checking backup…") {
        preview?.let { old -> withContext(Dispatchers.IO) { service.discard(old) } }
        preview = null
        preview = service.inspect(uri)
    }

    fun restore() {
        val prepared = preview ?: return
        operation("Restoring backup…") {
            try {
                service.restore(prepared)
            } catch (e: Exception) {
                restoreNeedsRecovery = service.recoveryRequired.value
                throw e
            }
            preview = null
            message = "Backup restored."
            restoreCompleted = true
        }
    }

    fun cancelPreview() {
        if (busy || loading) return
        val prepared = preview ?: return
        preview = null
        error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.discard(prepared) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn’t remove the temporary backup copy. Close and reopen Tungaon to retry."
            }
        }
    }

    fun consumeRestoreCompletion() { restoreCompleted = false }

    fun reportError(value: String) { error = value }
    fun clearError() { error = null }

    private fun operation(label: String, block: suspend () -> Unit) {
        if (busy || loading || recoveryRequired) return
        busy = true
        error = null
        message = null
        progress = label
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn’t complete the backup operation. Try again."
            } finally {
                recoveryRequired = service.recoveryRequired.value
                busy = false
                progress = null
            }
        }
    }
}
