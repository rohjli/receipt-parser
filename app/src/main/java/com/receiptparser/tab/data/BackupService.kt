package com.receiptparser.tab.data

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Portable snapshots with a durable marker bridging Room and preferences. */
@Singleton
class BackupService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: TabDatabase,
    private val settings: SettingsStore,
) {
    private val repository = TabRepository(db)
    private val dao get() = db.dao()
    private val previewRoot get() = File(context.cacheDir, "backup-previews")
    private val receiptRoot get() = File(context.filesDir, "receipts")
    private var cleanedPreviews = false
    private val mutableRecoveryRequired = MutableStateFlow(true)
    val recoveryRequired: StateFlow<Boolean> = mutableRecoveryRequired.asStateFlow()

    suspend fun recoverPending() = withContext(Dispatchers.IO) {
        StorageGate.exclusive {
            // A cancelled background check must not strand the global storage gate.
            withContext(NonCancellable) {
                try {
                    val pending = dao.restoreState()
                    if (pending != null) {
                        StorageGate.requireRecovery()
                        mutableRecoveryRequired.value = true
                    }
                    finishPendingRestore(pending)
                    if (!cleanedPreviews) {
                        previewRoot.listFiles()?.filter { ownsPreview(it) }?.forEach { it.deleteRecursively() }
                        cleanedPreviews = true
                    }
                } catch (error: Throwable) {
                    StorageGate.requireRecovery()
                    mutableRecoveryRequired.value = true
                    throw error
                }
            }
        }
    }

    private suspend fun finishPendingRestore(pending: RestoreStateEntity?) {
        if (pending != null) {
            StorageGate.invalidateSnapshot()
            settings.replaceForRestore(Json.decodeFromString<TabSettings>(pending.settingsJson))
            dao.clearRestoreState()
            context.getSystemService(NotificationManager::class.java).cancel(100)
        }
        StorageGate.recoveryFinished()
        mutableRecoveryRequired.value = false
    }

    private suspend fun requireAvailableWithoutDraft() {
        check(!mutableRecoveryRequired.value) { "Finish recovering your backup before continuing." }
        check(settings.readDraftRaw() == null) { "Finish or discard your current receipt before using backup and restore." }
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        StorageGate.access {
            requireAvailableWithoutDraft()
            val ledger = repository.snapshotRaw()
            val savedSettings = settings.readSettingsRaw()
            val files = linkedMapOf<String, File>()
            val paths = mutableMapOf<String, String>()
            val images = mutableListOf<BackupImage>()
            val receipts = ledger.receipts.map { receipt ->
                val sourcePath = receipt.imagePath ?: return@map receipt
                val source = File(sourcePath).canonicalFile
                check(source.path.startsWith(receiptRoot.canonicalPath + File.separator) && source.isFile) {
                    "A saved receipt photo is missing or unreadable. Restore the photo before creating a complete backup."
                }
                val archivePath = paths.getOrPut(source.path) {
                    val path = "images/${UUID.randomUUID()}.jpg"
                    files[path] = source
                    images += BackupImage(path, source.length(), sha256(source))
                    path
                }
                receipt.copy(imagePath = archivePath)
            }
            val manifest = BackupManifest(createdAt = System.currentTimeMillis(), ledger = ledger.copy(receipts = receipts), settings = savedSettings, images = images)
            val temporary = File.createTempFile("tungaon-backup-", ".zip", context.cacheDir)
            try {
                temporary.outputStream().use { BackupArchive.write(it, manifest, files) }
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Couldn’t open the backup destination. Choose another folder.")
                output.use { destination -> temporary.inputStream().use { it.copyTo(destination) } }
            } finally { temporary.delete() }
        }
    }

    suspend fun inspect(uri: Uri): PreparedBackup = withContext(Dispatchers.IO) {
        StorageGate.access {
            requireAvailableWithoutDraft()
            val directory = File(previewRoot, UUID.randomUUID().toString())
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Couldn’t read this backup. Choose the file again.")
                input.use { BackupArchive.read(it, directory) }
            } catch (error: Throwable) {
                directory.deleteRecursively()
                throw error
            }
        }
    }

    suspend fun restore(prepared: PreparedBackup) = withContext(Dispatchers.IO) {
        StorageGate.access {
            requireAvailableWithoutDraft()
            require(ownsPreview(prepared.directory)) { "Choose the backup again before restoring." }
            BackupArchive.validate(prepared.manifest)
            val destination = File(receiptRoot, "restored-${UUID.randomUUID()}")
            check(destination.mkdirs()) { "Couldn’t prepare receipt photo storage. Free some space and try again." }
            var committed = false
            try {
                val paths = prepared.manifest.images.associate { image ->
                    val source = File(prepared.directory, image.path)
                    require(source.isFile && source.length() == image.size) {
                        "The prepared backup is incomplete. Choose the backup again."
                    }
                    val target = File(destination, File(image.path).name)
                    FileOutputStream(target).use { output ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val size = DigestInputStream(source.inputStream(), digest).use { it.copyTo(output) }
                        require(size == image.size && digest.hexDigest() == image.sha256) {
                            "The prepared backup is incomplete. Choose the backup again."
                        }
                        output.fd.sync()
                    }
                    image.path to target.absolutePath
                }
                val ledger = prepared.manifest.ledger.let { snapshot ->
                    snapshot.copy(receipts = snapshot.receipts.map { receipt ->
                        receipt.copy(imagePath = receipt.imagePath?.let(paths::getValue))
                    })
                }
                // Once replacement starts, cancellation cannot leave preferences half applied.
                // A process interruption is handled by the marker committed with these rows.
                withContext(NonCancellable) {
                    StorageGate.requireRecovery()
                    mutableRecoveryRequired.value = true
                    try {
                        db.withTransaction {
                            dao.clearPayments()
                            dao.clearReceipts()
                            dao.clearFriends()
                            dao.putFriends(ledger.friends)
                            dao.putReceipts(ledger.receipts)
                            dao.putParticipants(ledger.participants)
                            dao.putItems(ledger.items)
                            dao.putShares(ledger.shares)
                            dao.putPayments(ledger.payments)
                            dao.putRestoreState(RestoreStateEntity(settingsJson = Json.encodeToString(prepared.manifest.settings)))
                        }
                        committed = true
                        finishPendingRestore(dao.restoreState())
                        discard(prepared)
                    } catch (error: Throwable) {
                        if (!committed) {
                            StorageGate.recoveryFinished()
                            mutableRecoveryRequired.value = false
                        }
                        throw error
                    }
                }
            } finally {
                // Old files remain valid even if replacement fails; never delete user photos here.
                if (!committed) destination.deleteRecursively()
            }
        }
    }

    suspend fun readForReminder(): Pair<TabSettings, LedgerSnapshot> = withContext(Dispatchers.IO) {
        recoverPending()
        StorageGate.access { settings.readSettingsRaw() to repository.snapshotRaw() }
    }

    fun discard(prepared: PreparedBackup) {
        if (ownsPreview(prepared.directory)) prepared.directory.deleteRecursively()
    }

    private fun ownsPreview(directory: File): Boolean =
        directory.canonicalFile.parentFile == previewRoot.canonicalFile &&
            runCatching { UUID.fromString(directory.name) }.isSuccess

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.hexDigest()
    }
    private fun MessageDigest.hexDigest() = digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
