package com.example.prism.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_repository_prefs")

class ScanPreferences(private val context: Context) {
    private val lastScanKey = longPreferencesKey("last_scan_timestamp")

    suspend fun getLastScanTimestamp(): Long {
        return try {
            context.dataStore.data.map { it[lastScanKey] ?: 0L }.first()
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun setLastScanTimestamp(timestamp: Long) {
        try {
            context.dataStore.edit { it[lastScanKey] = timestamp }
        } catch (_: Exception) { }
    }
}
