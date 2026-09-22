package com.example.prism.domain.repository

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Repositorio de dominio para la persistencia y consulta reactiva de la configuración del ecualizador.
 * Sigue el patrón CQRS: expone un [StateFlow] para lecturas reactivas y métodos suspendidos para mutaciones atómicas.
 */
interface EqualizerRepository {
    /** Flujo reactivo con el estado actual de la configuración del ecualizador. */
    val equalizerConfig: StateFlow<EqualizerConfig>

    /** Activa o desactiva el ecualizador globalmente. */
    suspend fun setEnabled(enabled: Boolean)

    /** Cambia el modo de operación entre [EqualizerMode.SIMPLE] y [EqualizerMode.ADVANCED]. */
    suspend fun setMode(mode: EqualizerMode)

    /** Configura el nivel de ganancia del preamplificador en dB (-12 dB a +12 dB). */
    suspend fun setPreamp(preampDb: Float)

    /** Actualiza los parámetros de una banda específica por su ID. */
    suspend fun setBand(band: EqBand)

    /** Actualiza la lista completa de bandas en un único paso atómico. */
    suspend fun setBands(bands: List<EqBand>)

    /** Aplica un perfil predefinido por nombre (ej. "Rock", "Pop", "Bass Boost"). */
    suspend fun applyPreset(presetName: String)

    /** Activa o desactiva la protección de saturación suave (limiter). */
    suspend fun setLimiterEnabled(enabled: Boolean)

    /** Restablece la configuración a sus valores iniciales por defecto. */
    suspend fun reset()

    /** Aplica una transformación atómica y libre de carreras a la configuración actual. */
    suspend fun updateConfig(transform: (EqualizerConfig) -> EqualizerConfig)
}
