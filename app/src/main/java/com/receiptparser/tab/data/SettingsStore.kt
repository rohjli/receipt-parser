package com.receiptparser.tab.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.receiptparser.tab.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tabPreferences by preferencesDataStore("tab_preferences")
data class TabSettings(val gcash: String = "", val reminderHours: Int = 24, val quietStart: Int = 23, val quietEnd: Int = 8, val feeMode: FeeMode = FeeMode.EQUAL, val showOwnShare: Boolean = false)
@Singleton class SettingsStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val store get() = context.tabPreferences
    private val gcash = stringPreferencesKey("gcash")
    private val hours = intPreferencesKey("reminder_hours")
    private val quietStart = intPreferencesKey("quiet_start")
    private val quietEnd = intPreferencesKey("quiet_end")
    private val feeMode = stringPreferencesKey("fee_mode")
    private val own = booleanPreferencesKey("show_own")
    private val draft = stringPreferencesKey("draft")
    val settings = store.data.map { p -> TabSettings(p[gcash] ?: "", p[hours] ?: 24, p[quietStart] ?: 23, p[quietEnd] ?: 8, FeeMode.valueOf(p[feeMode] ?: "EQUAL"), p[own] ?: false) }
    suspend fun save(value: TabSettings) {
        require(value.reminderHours in 1..720) { "Choose a reminder delay from 1 to 720 hours." }
        require(value.quietStart in 0..23 && value.quietEnd in 0..23) { "Quiet hours must be from 0 to 23." }
        require(value.gcash.isBlank() || Regex("09\\d{9}").matches(value.gcash)) { "Use an 11-digit GCash number starting with 09." }
        store.edit { p -> p[gcash] = value.gcash; p[hours] = value.reminderHours; p[quietStart] = value.quietStart; p[quietEnd] = value.quietEnd; p[feeMode] = value.feeMode.name; p[own] = value.showOwnShare }
    }
    suspend fun readDraft(): ReceiptDraft? = store.data.first()[draft]?.let { Json.decodeFromString(it) }
    suspend fun saveDraft(value: ReceiptDraft?) { store.edit { p -> if (value == null) p.remove(draft) else p[draft] = Json.encodeToString(value) } }
}
