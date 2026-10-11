package com.receiptparser.tab.data

import com.receiptparser.tab.domain.FeeMode
import com.receiptparser.tab.domain.Money
import com.receiptparser.tab.domain.OcrLine
import com.receiptparser.tab.domain.SELF
import com.receiptparser.tab.domain.VatMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable snapshots only: local database files and unfinished drafts never enter the archive. */
object BackupArchive {
    private const val MAX_MANIFEST = 16L * 1024 * 1024
    private const val MAX_IMAGE = 20L * 1024 * 1024
    private const val MAX_TOTAL = 512L * 1024 * 1024
    private const val MAX_RECORDS = 100_000
    private const val MAX_IMAGES = 10_000
    private val imagePath = Regex("images/[A-Za-z0-9_-]{1,100}\\.jpg")
    private val hash = Regex("[a-f0-9]{64}")
    private val json = Json { encodeDefaults = true }

    fun write(output: OutputStream, manifest: BackupManifest, images: Map<String, File>) {
        validate(manifest)
        require(images.keys == manifest.images.map { it.path }.toSet()) { "Backup photos do not match the ledger." }
        val metadata = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        require(metadata.size <= MAX_MANIFEST) { "The ledger is too large to back up." }
        require(metadata.size + manifest.images.sumOf { it.size } <= MAX_TOTAL) { "The backup is too large." }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(metadata)
            zip.closeEntry()
            manifest.images.forEach { photo ->
                zip.putNextEntry(ZipEntry(photo.path))
                val result = images.getValue(photo.path).inputStream().use { copyBounded(it, zip, MAX_IMAGE) }
                require(result == photo.size to photo.sha256) { "A receipt photo changed or could not be read. Try again." }
                zip.closeEntry()
            }
        }
    }

    /** The caller supplies a fresh staging directory; a failed read removes only that directory. */
    fun read(input: InputStream, directory: File): PreparedBackup {
        require(!directory.exists() || (directory.isDirectory && directory.listFiles()?.isEmpty() == true)) { "Backup staging directory is not empty." }
        require(directory.mkdirs() || directory.isDirectory) { "Could not create backup staging storage." }
        try {
            var manifestBytes: ByteArray? = null
            val seen = mutableSetOf<String>()
            val extracted = mutableMapOf<String, Pair<Long, String>>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name == "manifest.json" || imagePath.matches(entry.name))) { "Backup contains an unsafe or unsupported file path." }
                    require(seen.add(entry.name)) { "Backup contains duplicate files." }
                    require(seen.size <= MAX_IMAGES + 1) { "Backup contains too many files." }
                    val limit = minOf(if (entry.name == "manifest.json") MAX_MANIFEST else MAX_IMAGE, MAX_TOTAL - total)
                    require(entry.size < 0 || entry.size <= limit) { "Backup exceeds the size limit." }
                    if (entry.name == "manifest.json") {
                        val bytes = ByteArrayOutputStream()
                        total += copyBounded(zip, bytes, limit).first
                        manifestBytes = bytes.toByteArray()
                    } else {
                        val target = File(directory, entry.name)
                        require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Unsafe backup file path." }
                        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) { "Could not stage backup photos." }
                        val result = target.outputStream().use { copyBounded(zip, it, limit) }
                        total += result.first
                        extracted[entry.name] = result
                    }
                    zip.closeEntry()
                }
            }
            val metadata = requireNotNull(manifestBytes) { "This file is not a Tungaon backup." }
            val document = json.parseToJsonElement(metadata.toString(Charsets.UTF_8))
            val envelope = requireFields(document, setOf("format", "version", "createdAt", "ledger", "settings", "images"))
            require(envelope.getValue("format").jsonPrimitive.content == "tungaon-backup") { "This file is not a Tungaon backup." }
            require(envelope.getValue("version").jsonPrimitive.intOrNull == 1) { "This backup version is not supported. Update Tungaon and try again." }
            val tables = requireFields(envelope.getValue("ledger"), setOf("friends", "receipts", "participants", "items", "shares", "payments"))
            requireFields(envelope.getValue("settings"), setOf("gcash", "reminderHours", "quietStart", "quietEnd", "feeMode", "showOwnShare"))
            tables.getValue("friends").jsonArray.forEach { friend ->
                requireFields(friend, setOf("id", "name", "color", "gcashHint", "lastUsedAt", "archived"))
            }
            val manifest = json.decodeFromJsonElement<BackupManifest>(document)
            validate(manifest)
            require(extracted.keys == manifest.images.map { it.path }.toSet()) { "Backup photos are missing or unexpected." }
            manifest.images.forEach { photo ->
                require(extracted[photo.path] == photo.size to photo.sha256) { "A backup photo is damaged or incomplete." }
            }
            return PreparedBackup(manifest, directory)
        } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
    }

    private fun requireFields(value: JsonElement, fields: Set<String>): JsonObject {
        val objectValue = value as? JsonObject ?: throw IllegalArgumentException("A backup section is invalid.")
        require(objectValue.keys.containsAll(fields)) { "The backup is incomplete. Required saved data is missing." }
        return objectValue
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            size += count
            require(size <= limit) { "Backup exceeds the size limit." }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        return size to digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validate(manifest: BackupManifest) {
        require(manifest.format == "tungaon-backup") { "This file is not a Tungaon backup." }
        require(manifest.version == 1) { "This backup version is not supported. Update Tungaon and try again." }
        require(manifest.createdAt >= 0) { "Backup date is invalid." }
        manifest.settings.validate()
        val ledger = manifest.ledger
        val recordCount = ledger.friends.size.toLong() + ledger.receipts.size + ledger.items.size + ledger.participants.size + ledger.shares.size + ledger.payments.size
        require(recordCount <= MAX_RECORDS) { "Backup contains too many ledger records." }
        unique(ledger.friends.map { it.id }); unique(ledger.receipts.map { it.id }); unique(ledger.items.map { it.id }); unique(ledger.payments.map { it.id })
        require(ledger.participants.map { it.receiptId to it.friendId }.toSet().size == ledger.participants.size) { "Backup contains duplicate participants." }
        require(ledger.shares.map { it.lineItemId to it.friendId }.toSet().size == ledger.shares.size) { "Backup contains duplicate shares." }
        require(manifest.images.size <= MAX_IMAGES) { "Backup contains too many photos." }
        unique(manifest.images.map { it.path })
        manifest.images.forEach {
            require(imagePath.matches(it.path) && it.size in 1..MAX_IMAGE && hash.matches(it.sha256)) { "Backup photo metadata is invalid." }
        }
        require(manifest.images.sumOf { it.size } <= MAX_TOTAL) { "Backup exceeds the size limit." }
        require(ledger.receipts.mapNotNull { it.imagePath }.toSet() == manifest.images.map { it.path }.toSet()) { "Backup photos do not match the receipts." }
        val friends = ledger.friends.map { it.id }.toSet()
        val receipts = ledger.receipts.map { it.id }.toSet()
        val items = ledger.items.associateBy { it.id }
        val receiptPeople = ledger.participants.groupBy { it.receiptId }
        val participants = receiptPeople.mapValues { (_, list) -> list.map { it.friendId }.toSet() }
        ledger.friends.forEach {
            require(it.id != SELF && it.name.isNotBlank() && it.name.length <= 60 && it.lastUsedAt >= 0) { "Backup friend is invalid." }
        }
        ledger.receipts.forEach {
            require(it.takenAt >= 0 && it.venue.length <= 120) { "Backup receipt is invalid." }
            money(it.subtotal); money(it.serviceCharge); money(it.vat); money(it.discount); money(it.total)
            require(it.discount <= it.subtotal) { "Backup discount is invalid." }
            VatMode.valueOf(it.vatMode); FeeMode.valueOf(it.feeSplitMode)
            json.decodeFromString<List<OcrLine>>(it.ocrJson)
            json.decodeFromString<List<String>>(it.skippedJson)
        }
        ledger.participants.forEach {
            require(it.receiptId in receipts && (it.friendId == SELF || it.friendId in friends)) { "Backup participant references a missing receipt or friend." }
            // Fees can include both additive VAT and service charge. Self absorbs reconciliation differences.
            money(it.feeShare, 2 * Money.MAX); money(it.discountShare)
            require(it.rounding in -4 * Money.MAX..4 * Money.MAX) { "Backup rounding amount is invalid." }
        }
        ledger.items.forEach {
            require(it.receiptId in receipts && it.name.isNotBlank() && it.name.length <= 200 && it.qty in 1..999 && it.sortOrder >= 0) { "Backup item is invalid." }
            money(it.unitPrice); money(it.lineTotal)
            val owners = json.decodeFromString<List<String>>(it.ownersJson)
            require(owners.size == owners.toSet().size && owners.all { owner -> owner in participants[it.receiptId].orEmpty() }) { "Backup item references a person outside its group." }
        }
        ledger.shares.forEach {
            val item = requireNotNull(items[it.lineItemId]) { "Backup share references a missing item." }
            require(it.friendId in participants[item.receiptId].orEmpty()) { "Backup share references a person outside its group." }
            money(it.amount)
        }
        ledger.payments.forEach {
            require(it.friendId in friends && it.amount in 1..Money.MAX && it.paidAt >= 0 && it.note.length <= 250) { "Backup payment is invalid." }
        }
        // Compare persisted accounting facts; never rerun today's split algorithm on historical records.
        val receiptItems = ledger.items.groupBy { it.receiptId }
        val itemShares = ledger.shares.groupBy { it.lineItemId }
        ledger.receipts.forEach { receipt ->
            val rows = receiptItems[receipt.id].orEmpty()
            val people = receiptPeople[receipt.id].orEmpty()
            require(rows.size in 1..500 && people.size in 1..51 && people.any { it.friendId == SELF }) { "Backup receipt is missing items or participants." }
            require(rows.map { it.sortOrder }.toSet().size == rows.size) { "Backup item ordering is invalid." }
            require(rows.sumOf { it.lineTotal } == receipt.subtotal) { "Backup item subtotal does not match its receipt." }
            rows.forEach { item ->
                require(item.unitPrice == item.lineTotal / item.qty) { "Backup item price is inconsistent." }
                require(itemShares[item.id].orEmpty().sumOf { it.amount } == item.lineTotal) { "Backup item shares do not match its amount." }
            }
            val fees = receipt.serviceCharge + if (receipt.vatMode == VatMode.ADDITIVE.name) receipt.vat else 0
            require(people.sumOf { it.feeShare } == fees && people.sumOf { it.discountShare } == receipt.discount) { "Backup fee or discount shares are inconsistent." }
            val computed = receipt.subtotal + fees - receipt.discount
            require(computed + people.sumOf { it.rounding } == receipt.total) { "Backup shares do not match the receipt total." }
            require(receipt.reconciled == (receipt.total == computed)) { "Backup reconciliation status is inconsistent." }
        }
        // Record and per-amount bounds keep all these aggregates well below Long overflow.
    }

    private fun unique(ids: List<String>) {
        require(ids.all { it.isNotBlank() && it.length <= 200 } && ids.size == ids.toSet().size) { "Backup contains duplicate or invalid record IDs." }
    }

    private fun money(value: Long, max: Long = Money.MAX) {
        require(value in 0..max) { "Backup contains an invalid amount." }
    }
}
