package com.example.prism.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_preferences")

class PlaybackPreferences(private val context: Context) {

    private val shuffleEnabledKey = booleanPreferencesKey("shuffle_enabled")
    private val repeatModeKey = intPreferencesKey("repeat_mode")

    val shuffleEnabledFlow: Flow<Boolean> = context.playbackDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading shuffle preference from DataStore")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[shuffleEnabledKey] ?: false
        }

    val repeatModeFlow: Flow<Int> = context.playbackDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading repeat mode from DataStore")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[repeatModeKey] ?: Player.REPEAT_MODE_OFF
        }

    suspend fun getShuffleEnabled(): Boolean {
        return try {
            shuffleEnabledFlow.first()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        try {
            context.playbackDataStore.edit { prefs ->
                prefs[shuffleEnabledKey] = enabled
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving shuffle state to DataStore")
        }
    }

    suspend fun getRepeatMode(): Int {
        return try {
            repeatModeFlow.first()
        } catch (_: Exception) {
            Player.REPEAT_MODE_OFF
        }
    }

    suspend fun setRepeatMode(repeatMode: Int) {
        try {
            context.playbackDataStore.edit { prefs ->
                prefs[repeatModeKey] = repeatMode
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving repeat mode to DataStore")
        }
    }
}
