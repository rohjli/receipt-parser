package com.receiptparser.tab.data

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class BackupManifest(
    val format: String = "tungaon-backup",
    val version: Int = 1,
    val createdAt: Long,
    val ledger: LedgerSnapshot,
    val settings: TabSettings,
    val images: List<BackupImage>,
)

@Serializable
data class BackupImage(val path: String, val size: Long, val sha256: String)

data class PreparedBackup(val manifest: BackupManifest, val directory: File)
