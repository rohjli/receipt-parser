package com.receiptparser.tab

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupStorageTest {
    @get:Rule val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TabDatabase::class.java)

    @Test fun versionOneMigrationPreservesExistingFriendsAndPayments() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-migration-${UUID.randomUUID()}.db"
        try {
            migrationHelper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO friend (id,name,color,gcashHint,lastUsedAt,archived) VALUES ('existing-friend','Old ledger friend',2,'',123,1)")
                execSQL("INSERT INTO payment (id,friendId,amount,paidAt,note) VALUES ('existing-payment','existing-friend',1234,123,'Previous payment')")
                close()
            }
            migrationHelper.runMigrationsAndValidate(name, 2, true, TabDatabase.MIGRATION_1_2).close()
            val migrated = Room.databaseBuilder(context, TabDatabase::class.java, name).addMigrations(TabDatabase.MIGRATION_1_2).build()
            try {
                val snapshot = TabRepository(migrated).snapshot()
                assertEquals("Old ledger friend", snapshot.friends.single().name)
                assertTrue(snapshot.friends.single().archived)
                assertEquals("existing-payment", snapshot.payments.single().id)
                assertEquals(-1234L, snapshot.balance("existing-friend"))
                assertNull(migrated.dao().restoreState())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun restoresExactLedgerSettingsAndPortablePhotos() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        val settings = SettingsStore(context)
        val beforeSettings = settings.settings.first()
        val beforeDraft = settings.readDraft()
        val repository = TabRepository(db)
        val service = BackupService(context, db, settings)
        val image = File(context.filesDir, "receipts/test-${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val archive = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        var prepared: PreparedBackup? = null
        try {
            service.recoverPending()
            settings.saveDraft(null)
            val friend = repository.addFriend("Backup friend")
            val receipt = ReceiptDraft(venue = "Backup dinner", imagePath = image.absolutePath, items = listOf(ReceiptItem(name = "Rice", lineTotal = 12345)), total = 12345, participants = listOf(Participant(friend)))
            repository.save(receipt)
            repository.recordPayment(friend, 1200, System.currentTimeMillis(), "Partial payment")
            repository.updateFriend(repository.snapshot().friends.single().copy(archived = true))
            val expected = repository.snapshot()
            val oldGeneration = StorageGate.generation
            val expectedSettings = TabSettings(gcash = "09123456789", reminderHours = 48, quietStart = 22, quietEnd = 9, feeMode = FeeMode.PROPORTIONAL, showOwnShare = true)
            settings.save(expectedSettings)
            service.exportTo(android.net.Uri.fromFile(archive))
            prepared = service.inspect(android.net.Uri.fromFile(archive))
            repository.deleteReceipt(receipt.id)
            repository.addFriend("Remove on restore")
            settings.save(TabSettings())
            val beforeRestore = repository.snapshot()
            settings.saveDraft(ReceiptDraft(venue = "Started after preview"))
            assertTrue(runCatching { service.restore(prepared!!) }.isFailure)
            assertEquals(beforeRestore, repository.snapshot())
            settings.saveDraft(null)
            val stagedPhoto = File(prepared!!.directory, prepared!!.manifest.images.single().path)
            val receiptDirectory = image.parentFile!!
            val filesBeforeRestore = receiptDirectory.list()!!.toSet()
            stagedPhoto.writeBytes(byteArrayOf(4, 3, 2, 1))
            val corruptPhotoFailure = runCatching { service.restore(prepared!!) }.exceptionOrNull()
            assertEquals("The prepared backup is incomplete. Choose the backup again.", corruptPhotoFailure?.message)
            assertEquals(beforeRestore, repository.snapshot())
            assertEquals(TabSettings(), settings.settings.first())
            assertNull(db.dao().restoreState())
            assertFalse(service.recoveryRequired.value)
            assertEquals(filesBeforeRestore, receiptDirectory.list()!!.toSet())
            stagedPhoto.writeBytes(image.readBytes())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON friend BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END")
            try {
                assertTrue(runCatching { service.restore(prepared!!) }.isFailure)
                assertEquals(beforeRestore, repository.snapshot())
                assertEquals(TabSettings(), settings.settings.first())
                assertNull(db.dao().restoreState())
                assertFalse(service.recoveryRequired.value)
            } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore") }
            service.restore(prepared!!)
            val actual = repository.snapshot()
            assertEquals(expected.copy(receipts = expected.receipts.map { it.copy(imagePath = actual.receipts.single().imagePath) }), actual)
            assertEquals(expectedSettings, settings.settings.first())
            assertArrayEquals(image.readBytes(), File(actual.receipts.single().imagePath!!).readBytes())
            assertNotEquals(image.absolutePath, actual.receipts.single().imagePath)
            assertNull(settings.readDraft())
            assertFalse(service.recoveryRequired.value)
            assertNull(db.dao().restoreState())
            assertFalse(prepared!!.directory.exists())
            repository.undoPayment(expected.payments.single().id, oldGeneration)
            assertEquals(expected.payments, repository.snapshot().payments)
            repository.undoPayment(expected.payments.single().id, StorageGate.generation)
            assertTrue(repository.snapshot().payments.isEmpty())
            File(actual.receipts.single().imagePath!!).parentFile!!.deleteRecursively()
        } finally {
            prepared?.let(service::discard)
            settings.save(beforeSettings); settings.saveDraft(beforeDraft)
            image.delete(); archive.delete(); db.close()
        }
    }

    @Test fun unfinishedDraftAndMissingPhotoRejectExportWithoutChangingLedger() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        val settings = SettingsStore(context)
        val originalDraft = settings.readDraft()
        val repository = TabRepository(db)
        val service = BackupService(context, db, settings)
        val file = File(context.cacheDir, "rejected-${UUID.randomUUID()}.zip")
        try {
            service.recoverPending()
            repository.save(ReceiptDraft(items = listOf(ReceiptItem(name = "Rice", lineTotal = 1000)), total = 1000, imagePath = File(context.filesDir, "missing-${UUID.randomUUID()}.jpg").path))
            val original = repository.snapshot()
            settings.saveDraft(ReceiptDraft())
            assertTrue(runCatching { service.exportTo(android.net.Uri.fromFile(file)) }.isFailure)
            settings.saveDraft(null)
            assertTrue(runCatching { service.exportTo(android.net.Uri.fromFile(file)) }.isFailure)
            assertEquals(original, repository.snapshot())
        } finally { settings.saveDraft(originalDraft); file.delete(); db.close() }
    }

    @Test fun recoversCommittedSettingsMarkerAndClearsDraft() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        val settings = SettingsStore(context)
        val originalSettings = settings.settings.first()
        val originalDraft = settings.readDraft()
        val service = BackupService(context, db, settings)
        val expected = TabSettings(reminderHours = 72)
        try {
            settings.saveDraft(ReceiptDraft(venue = "Old draft"))
            db.dao().putRestoreState(RestoreStateEntity(settingsJson = Json.encodeToString(expected)))
            service.recoverPending()
            assertEquals(expected, settings.settings.first())
            assertNull(settings.readDraft())
            assertNull(db.dao().restoreState())
            service.recoverPending()
            assertEquals(expected, settings.settings.first())
        } finally { settings.save(originalSettings); settings.saveDraft(originalDraft); db.close() }
    }
    @Test fun failedSettingsRecoveryBlocksWritesAndResumesWaitingReadsAfterRetry() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        val settings = SettingsStore(context)
        val originalSettings = settings.settings.first()
        val originalDraft = settings.readDraft()
        val repository = TabRepository(db)
        val service = BackupService(context, db, settings)
        try {
            service.recoverPending()
            settings.saveDraft(null)
            val friend = repository.addFriend("Already restored friend")
            // Represents a committed restore whose preference application has failed.
            db.dao().putRestoreState(RestoreStateEntity(settingsJson = Json.encodeToString(TabSettings(reminderHours = 0))))
            assertTrue(runCatching { service.recoverPending() }.isFailure)
            assertTrue(service.recoveryRequired.value)
            assertTrue(runCatching { repository.addFriend("Must not be written") }.isFailure)
            val waitingSnapshot = async { repository.snapshots.first() }
            delay(100)
            assertFalse(waitingSnapshot.isCompleted)
            db.dao().putRestoreState(RestoreStateEntity(settingsJson = Json.encodeToString(originalSettings)))
            service.recoverPending()
            assertEquals(listOf(friend), withTimeout(5_000) { waitingSnapshot.await() }.friends.map { it.id })
            assertFalse(service.recoveryRequired.value)
            repository.addFriend("Writable after recovery")
        } finally {
            db.dao().putRestoreState(RestoreStateEntity(settingsJson = Json.encodeToString(originalSettings)))
            service.recoverPending()
            settings.saveDraft(originalDraft)
            db.close()
        }
    }

}
