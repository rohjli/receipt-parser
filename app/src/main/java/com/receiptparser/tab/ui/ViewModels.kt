package com.receiptparser.tab.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel class LedgerViewModel @Inject constructor(val repository: TabRepository, val settingsStore: SettingsStore) : ViewModel() {
    var snapshot by mutableStateOf(LedgerSnapshot()); private set
    var settings by mutableStateOf(TabSettings()); private set
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    init {
        viewModelScope.launch { repository.snapshots.catch { error = "Couldn’t read the ledger. Close and reopen Tungaon to retry."; loading = false }.collect { snapshot = it; loading = false } }
        viewModelScope.launch { settingsStore.settings.catch { error = "Couldn’t read settings. Close and reopen Tungaon to retry." }.collect { settings = it } }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn’t save. Try again." } finally { busy = false } }
    }
}

@HiltViewModel class ReceiptViewModel @Inject constructor(private val repository: TabRepository, private val settings: SettingsStore) : ViewModel() {
    var draft by mutableStateOf<ReceiptDraft?>(null); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    private var persistJob: Job? = null
    init { viewModelScope.launch {
        try { draft = settings.readDraft() } catch (e: Exception) { error = "Couldn’t recover the draft. Saved receipts are still in the ledger." }
        ready = true
    } }
    fun change(transform: (ReceiptDraft) -> ReceiptDraft) {
        if (busy) return
        draft = draft?.let(transform)
        persistJob?.cancel()
        val value = draft
        persistJob = viewModelScope.launch { try { settings.saveDraft(value) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Draft not saved to storage. Keep Tungaon open and try again." } }
    }
    fun start(receipt: ReceiptDraft = ReceiptDraft(), then: (Boolean) -> Unit) {
        if (busy || !ready || draft != null) return
        busy = true; error = null
        viewModelScope.launch {
            try {
                val snapshot = repository.snapshot()
                val last = snapshot.receipts.firstOrNull()
                val active = snapshot.friends.filter { !it.archived }.map { it.id }.toSet()
                val group = snapshot.participants.filter { it.receiptId == last?.id && it.friendId in active }.map { Participant(it.friendId) }
                val next = receipt.copy(participants = group, feeMode = settings.settings.first().feeMode)
                settings.saveDraft(next); draft = next; then(next.reconciled)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Couldn’t start the receipt: ${e.message}" } finally { busy = false }
        }
    }
    fun edit(id: String, then: () -> Unit) {
        if (!ready) { error = "Wait for your saved draft to load."; return }
        if (busy || draft != null) { error = "Finish or discard your current receipt first."; return }
        busy = true
        viewModelScope.launch { try { val next = repository.snapshot().draft(id); settings.saveDraft(next); draft = next; then() } catch (e: Exception) { error = "Couldn’t open that receipt: ${e.message}" } finally { busy = false } }
    }
    fun discard(then: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch { try { persistJob?.cancelAndJoin(); settings.saveDraft(null); draft = null; error = null; then() } catch (e: Exception) { error = "Couldn’t discard the draft. Try again." } finally { busy = false } }
    }
    fun save(then: () -> Unit) {
        val current = draft ?: return
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try {
            persistJob?.cancelAndJoin()
            repository.save(current)
            settings.saveDraft(null); draft = null; then()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Receipt wasn’t saved. Try again." } finally { busy = false } }
    }
}
