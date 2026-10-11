package com.receiptparser.tab.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes ledger/preferences changes with portable backup and restore. */
internal object StorageGate {
    private val mutex = Mutex()
    @Volatile var generation: Long = 0
        private set
    private val ready = MutableStateFlow(true)
    val blocked get() = !ready.value

    suspend fun <T> access(block: suspend () -> T): T = mutex.withLock {
        check(!blocked) { "Finish restoring your backup before changing the ledger." }
        block()
    }

    suspend fun <T> read(block: suspend () -> T): T {
        while (true) {
            ready.first { it }
            mutex.withLock { if (!blocked) return block() }
        }
    }

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
    fun invalidateSnapshot() { generation++ }
    fun requireRecovery() { ready.value = false }
    fun recoveryFinished() { ready.value = true }
}
