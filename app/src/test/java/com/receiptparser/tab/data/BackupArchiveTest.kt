package com.receiptparser.tab.data

import com.receiptparser.tab.domain.SELF
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val codec = Json { encodeDefaults = true }
    private val photo = byteArrayOf(1, 2, 3, 4)
    private val path = "images/photo.jpg"
    private fun manifest() = BackupManifest(createdAt = 123L, settings = TabSettings(gcash = "09123456789"), ledger = LedgerSnapshot(
        friends = listOf(FriendEntity("friend", "Wife", archived = true)),
        receipts = listOf(ReceiptEntity("receipt", 42, "Dinner", path, "OCR", "[]", "[\"skipped\"]", 101, 0, 0, "INCLUSIVE", 0, 101, true, "EQUAL")),
        participants = listOf(ParticipantEntity("receipt", SELF, true, 0, 0, 0), ParticipantEntity("receipt", "friend", true, 0, 0, 0)),
        items = listOf(LineItemEntity("item", "receipt", "Rice", 1, 101, 101, "Rice 1.01", 0, "[]")),
        shares = listOf(ShareEntity("item", SELF, 51), ShareEntity("item", "friend", 50)),
        payments = listOf(PaymentEntity("payment", "friend", 20, 43, "Partial")),
    ), images = listOf(BackupImage(path, photo.size.toLong(), digest(photo))))
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun zip(manifest: BackupManifest = manifest(), entries: Map<String, ByteArray> = mapOf(path to photo), metadata: String = codec.encodeToString(manifest)): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(metadata.toByteArray()); zip.closeEntry()
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }.toByteArray()
    private fun rejects(bytes: ByteArray) {
        val directory = File(temporary.root, "stage-${System.nanoTime()}")
        assertThrows(Exception::class.java) { BackupArchive.read(bytes.inputStream(), directory) }
        assertFalse("Failed staging must be removed", directory.exists())
    }
    @Test fun roundTripPreservesAllRecordsSettingsAndPhotoBytes() {
        val source = temporary.newFile().apply { writeBytes(photo) }
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, manifest(), mapOf(path to source))
        val restored = BackupArchive.read(output.toByteArray().inputStream(), File(temporary.root, "restored"))
        assertEquals(manifest(), restored.manifest)
        assertEquals(30L, restored.manifest.ledger.balance("friend"))
        assertArrayEquals(photo, File(restored.directory, path).readBytes())
    }
    @Test fun rejectsTraversalAndAbsolutePathsWithoutWritingOutsideStaging() {
        listOf("../escaped.jpg", "/escaped.jpg", "images/../../escaped.jpg", "images\\escaped.jpg").forEach { rejects(zip(entries = mapOf(it to photo))) }
        assertFalse(File(temporary.root, "escaped.jpg").exists())
    }
    @Test fun rejectsMissingUnexpectedAndCorruptImages() {
        rejects(zip(entries = emptyMap()))
        rejects(zip(entries = mapOf(path to photo, "images/extra.jpg" to photo)))
        rejects(zip(entries = mapOf(path to byteArrayOf(4, 3, 2, 1))))
        rejects(zip(manifest().copy(images = listOf(BackupImage(path, 999, digest(photo))))))
    }
    @Test fun rejectsMissingSectionsRatherThanRestoringDefaults() {
        val original = codec.encodeToJsonElement(manifest()).jsonObject
        for (section in listOf("ledger", "settings")) {
            val fields = original.getValue(section).jsonObject
            for (field in fields.keys) {
                val changed = JsonObject(original + (section to JsonObject(fields - field)))
                rejects(zip(metadata = changed.toString()))
            }
        }
        for (field in original.keys) {
            rejects(zip(metadata = JsonObject(original - field).toString()))
        }
        val ledger = original.getValue("ledger").jsonObject
        val friend = ledger.getValue("friends").jsonArray.single().jsonObject
        for (field in friend.keys) {
            val changedLedger = JsonObject(ledger + ("friends" to JsonArray(listOf(JsonObject(friend - field)))))
            rejects(zip(metadata = JsonObject(original + ("ledger" to changedLedger)).toString()))
        }
    }
    @Test fun rejectsUnsupportedFormatAndSchema() {
        rejects(zip(manifest().copy(version = 2)))
        rejects(zip(manifest().copy(format = "another-app")))
    }
    @Test fun rejectsDuplicateRecordsAndOrphanRelationships() {
        val m = manifest(); val l = m.ledger
        rejects(zip(m.copy(ledger = l.copy(friends = l.friends + l.friends))))
        rejects(zip(m.copy(ledger = l.copy(participants = l.participants + l.participants))))
        rejects(zip(m.copy(ledger = l.copy(shares = listOf(ShareEntity("missing", "friend", 1))))))
        rejects(zip(m.copy(ledger = l.copy(payments = listOf(PaymentEntity("p", SELF, 1, 0, ""))))))
        rejects(zip(m.copy(ledger = l.copy(participants = listOf(ParticipantEntity("missing", "friend", true, 0, 0, 0))))))
    }
    @Test fun rejectsInvalidMoneySettingsAndEmbeddedJson() {
        val m = manifest(); val l = m.ledger
        rejects(zip(m.copy(ledger = l.copy(payments = listOf(PaymentEntity("p", "friend", Long.MAX_VALUE, 0, ""))))))
        rejects(zip(m.copy(ledger = l.copy(shares = listOf(ShareEntity("item", SELF, -1))))))
        rejects(zip(m.copy(ledger = l.copy(receipts = listOf(l.receipts.single().copy(vatMode = "INVALID"))))))
        rejects(zip(m.copy(ledger = l.copy(items = listOf(l.items.single().copy(ownersJson = "[\"missing\"]"))))))
        rejects(zip(m.copy(ledger = l.copy(receipts = listOf(l.receipts.single().copy(ocrJson = "{}"))))))
        rejects(zip(m.copy(settings = m.settings.copy(quietStart = 24))))
    }
    @Test fun rejectsDamagedAccountingWithoutRecalculatingHistoricAllocations() {
        val m = manifest(); val l = m.ledger
        rejects(zip(m.copy(ledger = l.copy(items = emptyList(), shares = emptyList()))))
        rejects(zip(m.copy(ledger = l.copy(participants = l.participants.filter { it.friendId != SELF }))))
        rejects(zip(m.copy(ledger = l.copy(shares = l.shares.map { it.copy(amount = 0) }))))
        rejects(zip(m.copy(ledger = l.copy(items = l.items.map { it.copy(unitPrice = 99) }))))
        rejects(zip(m.copy(ledger = l.copy(receipts = l.receipts.map { it.copy(subtotal = 100) }))))
        rejects(zip(m.copy(ledger = l.copy(participants = l.participants.map { it.copy(feeShare = 1) }))))
        rejects(zip(m.copy(ledger = l.copy(participants = l.participants.map { it.copy(discountShare = 1) }))))
        rejects(zip(m.copy(ledger = l.copy(participants = l.participants.map { it.copy(rounding = 1) }))))
        rejects(zip(m.copy(ledger = l.copy(receipts = l.receipts.map { it.copy(reconciled = false) }))))
    }
    @Test fun preservesUnreconciledReceiptWithHistoricalSelfResidual() {
        val m = manifest(); val l = m.ledger
        val altered = m.copy(ledger = l.copy(
            receipts = l.receipts.map { it.copy(total = 99, reconciled = false) },
            participants = l.participants.map { if (it.friendId == SELF) it.copy(rounding = -2) else it },
        ))
        val restored = BackupArchive.read(zip(altered).inputStream(), File(temporary.root, "unreconciled"))
        assertEquals(altered, restored.manifest)
        assertEquals(49L, restored.manifest.ledger.receiptAmount("receipt", SELF))
    }
    @Test fun rejectsInflatedImageBeforeUnboundedExtraction() {
        val bytes = ByteArray(20 * 1024 * 1024 + 1)
        rejects(zip(entries = mapOf(path to bytes)))
    }
    @Test fun rejectsDuplicateZipEntries() {
        val bytes = zip(entries = mapOf(path to photo, "images/other.jpg" to photo))
        val original = "images/other.jpg".toByteArray()
        val replacement = path.toByteArray()
        for (offset in 0..bytes.size - original.size) {
            if (original.indices.all { bytes[offset + it] == original[it] }) {
                replacement.copyInto(bytes, offset)
            }
        }
        rejects(bytes)
    }
    @Test fun rejectsMalformedManifest() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write("{invalid".toByteArray()); zip.closeEntry()
        }
        rejects(output.toByteArray())
    }
    @Test fun propagatesDestinationFailureInsteadOfReportingSuccessfulExport() {
        val source = temporary.newFile().apply { writeBytes(photo) }
        val output = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun close() { throw java.io.IOException("Destination disconnected") }
        }
        assertThrows(java.io.IOException::class.java) { BackupArchive.write(output, manifest(), mapOf(path to source)) }
    }
    @Test fun doesNotDeleteExistingStagingContents() {
        val directory = temporary.newFolder()
        val existing = File(directory, "keep").apply { writeText("keep") }
        assertThrows(Exception::class.java) { BackupArchive.read(zip().inputStream(), directory) }
        assertEquals("keep", existing.readText())
    }
}
