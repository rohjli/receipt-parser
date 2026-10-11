package com.receiptparser.tab.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Committed with the ledger so interrupted DataStore updates can finish on restart. */
@Entity(tableName = "restore_state")
data class RestoreStateEntity(@PrimaryKey val id: Int = 1, val settingsJson: String)
