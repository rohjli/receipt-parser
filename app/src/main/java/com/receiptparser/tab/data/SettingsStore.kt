package com.receiptparser.tab.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.receiptparser.tab.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tabPreferences by preferencesDataStore("tab_preferences")
@Serializable
data class TabSettings(val gcash: String = "", val reminderHours: Int = 24, val quietStart: Int = 23, val quietEnd: Int = 8, val feeMode: FeeMode = FeeMode.EQUAL, val showOwnShare: Boolean = false) {
    fun validate() {
        require(reminderHours in 1..720) { "Choose a reminder delay from 1 to 720 hours." }
        require(quietStart in 0..23 && quietEnd in 0..23) { "Quiet hours must be from 0 to 23." }
        require(gcash.isBlank() || Regex("09\\d{9}").matches(gcash)) { "Use an 11-digit GCash number starting with 09." }
    }
}

@Singleton class SettingsStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val store get() = context.tabPreferences
    private val gcash = stringPreferencesKey("gcash")
    private val hours = intPreferencesKey("reminder_hours")
    private val quietStart = intPreferencesKey("quiet_start")
    private val quietEnd = intPreferencesKey("quiet_end")
    private val feeMode = stringPreferencesKey("fee_mode")
    private val own = booleanPreferencesKey("show_own")
    private val draft = stringPreferencesKey("draft")
    val settings = store.data.map(::settingsFrom)

    private fun settingsFrom(p: Preferences) = TabSettings(p[gcash] ?: "", p[hours] ?: 24, p[quietStart] ?: 23, p[quietEnd] ?: 8, FeeMode.valueOf(p[feeMode] ?: "EQUAL"), p[own] ?: false)
    private fun putSettings(p: MutablePreferences, value: TabSettings) {
        p[gcash] = value.gcash
        p[hours] = value.reminderHours
        p[quietStart] = value.quietStart
        p[quietEnd] = value.quietEnd
        p[feeMode] = value.feeMode.name
        p[own] = value.showOwnShare
    }

    suspend fun save(value: TabSettings) = StorageGate.access {
        value.validate()
        store.edit { putSettings(it, value) }
        Unit
    }

    suspend fun readDraft(): ReceiptDraft? = StorageGate.read { readDraftRaw() }
    internal suspend fun readDraftRaw(): ReceiptDraft? = store.data.first()[draft]?.let { Json.decodeFromString(it) }
    internal suspend fun readSettingsRaw(): TabSettings = settingsFrom(store.data.first())
    internal suspend fun replaceForRestore(value: TabSettings) {
        value.validate()
        store.edit { p -> putSettings(p, value); p.remove(draft) }
    }
    suspend fun saveDraft(value: ReceiptDraft?) = StorageGate.access {
        store.edit { p -> if (value == null) p.remove(draft) else p[draft] = Json.encodeToString(value) }
        Unit
    }
}
