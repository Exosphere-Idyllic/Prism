package com.example.prism.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.prism.domain.model.equalizer.EqualizerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

private val Context.equalizerDataStore: DataStore<Preferences> by preferencesDataStore(name = "equalizer_preferences")

class EqualizerPreferences(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val configKey = stringPreferencesKey("equalizer_config_json")

    val equalizerConfigFlow: Flow<EqualizerConfig> = context.equalizerDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading equalizer preferences DataStore")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val jsonStr = prefs[configKey]
            if (jsonStr != null) {
                try {
                    json.decodeFromString<EqualizerConfig>(jsonStr)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode EqualizerConfig from DataStore, falling back to default")
                    EqualizerConfig()
                }
            } else {
                EqualizerConfig()
            }
        }

    suspend fun saveEqualizerConfig(config: EqualizerConfig) {
        try {
            val jsonStr = json.encodeToString(config)
            context.equalizerDataStore.edit { prefs ->
                prefs[configKey] = jsonStr
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving equalizer configuration to DataStore")
        }
    }
}
