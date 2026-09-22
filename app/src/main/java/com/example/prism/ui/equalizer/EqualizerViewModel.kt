package com.example.prism.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.model.equalizer.EqualizerPresets
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.effects.EqualizerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar el estado de la pantalla de ecualización ([EqualizerScreen]).
 *
 * Mantiene la reactividad frente a cambios en [EqualizerRepository] e implementa
 * un mecanismo de debouncing de 100 ms para sincronizar modificaciones intensivas
 * de sliders o gestos en Canvas hacia el [EqualizerController] sin sobrecargar el hilo de audio IPC.
 */
class EqualizerViewModel(
    private val repository: EqualizerRepository,
    private val controller: EqualizerController,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.main) {
            repository.equalizerConfig.collectLatest { config ->
                _uiState.update { current ->
                    current.copy(
                        enabled = config.enabled,
                        mode = config.mode,
                        preampDb = config.preampDb,
                        selectedPreset = config.selectedPreset,
                        bands = config.bands,
                        limiterEnabled = config.limiterEnabled,
                        selectedBandId = current.selectedBandId ?: config.bands.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    /** Alterna el estado activo/inactivo del ecualizador globalmente. */
    fun toggleEnabled() {
        val newEnabled = !_uiState.value.enabled
        _uiState.update { it.copy(enabled = newEnabled) }
        viewModelScope.launch(dispatchers.io) {
            controller.setEnabled(newEnabled)
        }
    }

    /** Cambia el modo de operación entre SIMPLE y ADVANCED. */
    fun setMode(mode: EqualizerMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode, selectedBandId = null) }
        viewModelScope.launch(dispatchers.io) {
            controller.setMode(mode)
        }
    }

    /** Selecciona una banda para inspección y edición detallada en modo avanzado. */
    fun selectBand(bandId: Int) {
        _uiState.update { it.copy(selectedBandId = bandId) }
    }

    /** Ajusta el nivel de preamplificación en dB (con debouncing de 100 ms). */
    fun setPreamp(preampDb: Float) {
        val clamped = preampDb.coerceIn(-12f, 12f)
        _uiState.update { it.copy(preampDb = clamped) }
        scheduleDebouncedSync()
    }

    /** Ajusta la ganancia de una banda en dB (con debouncing de 100 ms). */
    fun setBandGain(bandId: Int, gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        _uiState.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.id == bandId) band.copy(gainDb = clamped) else band
            }
            current.copy(
                bands = updatedBands,
                selectedPreset = null // Manual adjustment clears the active preset name
            )
        }
        scheduleDebouncedSync()
    }

    /** Ajusta la frecuencia central o de corte de una banda (con debouncing de 100 ms). */
    fun setBandFrequency(bandId: Int, freqHz: Float) {
        val clamped = freqHz.coerceIn(20f, 20_000f)
        _uiState.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.id == bandId) band.copy(frequencyHz = clamped) else band
            }
            current.copy(bands = updatedBands, selectedPreset = null)
        }
        scheduleDebouncedSync()
    }

    /** Ajusta el factor de calidad Q de una banda (con debouncing de 100 ms). */
    fun setBandQ(bandId: Int, q: Float) {
        val clamped = q.coerceIn(0.1f, 10f)
        _uiState.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.id == bandId) band.copy(q = clamped) else band
            }
            current.copy(bands = updatedBands, selectedPreset = null)
        }
        scheduleDebouncedSync()
    }

    /** Cambia la topología de filtro biquad de una banda (con debouncing de 100 ms). */
    fun setBandFilterType(bandId: Int, type: com.example.prism.domain.model.equalizer.EqFilterType) {
        _uiState.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.id == bandId) band.copy(type = type) else band
            }
            current.copy(bands = updatedBands, selectedPreset = null)
        }
        scheduleDebouncedSync()
    }

    /** Actualiza frecuencia y ganancia simultáneamente (utilizado por el arrastre en Canvas). */
    fun updateBandParametric(bandId: Int, freqHz: Float, gainDb: Float) {
        val clampedFreq = freqHz.coerceIn(20f, 20_000f)
        val clampedGain = gainDb.coerceIn(-12f, 12f)
        _uiState.update { current ->
            val updatedBands = current.bands.map { band ->
                if (band.id == bandId) band.copy(frequencyHz = clampedFreq, gainDb = clampedGain) else band
            }
            current.copy(bands = updatedBands, selectedPreset = null)
        }
        scheduleDebouncedSync()
    }

    /** Aplica un perfil predefinido de ecualización. */
    fun selectPreset(presetName: String) {
        val gains = EqualizerPresets.getGains(presetName) ?: return
        _uiState.update { current ->
            val updatedBands = current.bands.mapIndexed { index, band ->
                if (index < gains.size) band.copy(gainDb = gains[index]) else band
            }
            current.copy(
                selectedPreset = presetName,
                bands = updatedBands
            )
        }
        viewModelScope.launch(dispatchers.io) {
            controller.applyPreset(presetName)
        }
    }

    /** Alterna el limitador suave contra clipping. */
    fun toggleLimiter() {
        val newLimiter = !_uiState.value.limiterEnabled
        _uiState.update { it.copy(limiterEnabled = newLimiter) }
        viewModelScope.launch(dispatchers.io) {
            controller.setLimiterEnabled(newLimiter)
        }
    }

    /** Restablece todas las bandas y el preamp a los valores por defecto. */
    fun reset() {
        viewModelScope.launch(dispatchers.io) {
            controller.reset()
        }
    }

    private fun scheduleDebouncedSync() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch(dispatchers.io) {
            delay(100L)
            val currentState = _uiState.value
            controller.setPreamp(currentState.preampDb)
            controller.setBands(currentState.bands)
        }
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
    }
}
