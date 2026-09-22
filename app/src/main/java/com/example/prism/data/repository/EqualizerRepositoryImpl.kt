package com.example.prism.data.repository

import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.preferences.EqualizerPreferences
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.model.equalizer.EqualizerPresets
import com.example.prism.domain.repository.EqualizerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EqualizerRepositoryImpl(
    private val preferences: EqualizerPreferences,
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : EqualizerRepository {

    private val saveMutex = Mutex()
    private val _equalizerConfig = MutableStateFlow(EqualizerConfig())
    override val equalizerConfig: StateFlow<EqualizerConfig> = _equalizerConfig.asStateFlow()

    init {
        scope.launch(dispatchers.io) {
            preferences.equalizerConfigFlow.collect { updated ->
                _equalizerConfig.value = updated
            }
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        updateConfig { it.copy(enabled = enabled) }
    }

    override suspend fun setMode(mode: EqualizerMode) {
        updateConfig { current ->
            if (current.mode == mode) current
            else {
                val newBands = when (mode) {
                    EqualizerMode.SIMPLE -> EqualizerConfig.defaultBands()
                    EqualizerMode.ADVANCED -> EqualizerConfig.defaultAdvancedBands()
                }
                current.copy(mode = mode, bands = newBands, selectedPreset = if (mode == EqualizerMode.SIMPLE) "Flat" else null)
            }
        }
    }

    override suspend fun setPreamp(preampDb: Float) {
        updateConfig { it.copy(preampDb = preampDb) }
    }

    override suspend fun setBand(band: EqBand) {
        updateConfig { current ->
            val updatedBands = current.bands.map { existing ->
                if (existing.id == band.id) band else existing
            }
            current.copy(
                bands = updatedBands,
                selectedPreset = if (current.mode == EqualizerMode.SIMPLE) "Custom" else null,
            )
        }
    }

    override suspend fun setBands(bands: List<EqBand>) {
        updateConfig { current ->
            current.copy(
                bands = bands,
                selectedPreset = if (current.mode == EqualizerMode.SIMPLE) "Custom" else null,
            )
        }
    }

    override suspend fun applyPreset(presetName: String) {
        val gains = EqualizerPresets.getGains(presetName) ?: return
        updateConfig { current ->
            if (current.mode != EqualizerMode.SIMPLE) return@updateConfig current
            val updatedBands = current.bands.mapIndexed { index, band ->
                val newGain = gains.getOrNull(index) ?: band.gainDb
                band.copy(gainDb = newGain)
            }
            current.copy(selectedPreset = presetName, bands = updatedBands)
        }
    }

    override suspend fun setLimiterEnabled(enabled: Boolean) {
        updateConfig { it.copy(limiterEnabled = enabled) }
    }

    override suspend fun reset() {
        updateConfig { current ->
            val defaultBands = when (current.mode) {
                EqualizerMode.SIMPLE -> EqualizerConfig.defaultBands()
                EqualizerMode.ADVANCED -> EqualizerConfig.defaultAdvancedBands()
            }
            current.copy(
                preampDb = 0f,
                selectedPreset = if (current.mode == EqualizerMode.SIMPLE) "Flat" else null,
                bands = defaultBands,
            )
        }
    }

    override suspend fun updateConfig(transform: (EqualizerConfig) -> EqualizerConfig) {
        val newConfig = _equalizerConfig.updateAndGet(transform)
        withContext(dispatchers.io) {
            saveMutex.withLock {
                preferences.saveEqualizerConfig(newConfig)
            }
        }
    }
}
