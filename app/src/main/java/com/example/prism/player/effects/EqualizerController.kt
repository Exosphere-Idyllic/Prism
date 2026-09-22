package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode

/**
 * Interfaz de control del ecualizador para la capa de presentación / servicios.
 * Diseñada siguiendo el Principio de Segregación de Interfaces (ISP), desacoplada
 * completamente de las responsabilidades generales de reproducción de [PlaybackManager].
 */
interface EqualizerController {
    /** Activa o desactiva el ecualizador y sincroniza con el [EqualizerAudioProcessor] en el servicio. */
    suspend fun setEnabled(enabled: Boolean)

    /** Cambia el modo entre Simple y Avanzado. */
    suspend fun setMode(mode: EqualizerMode)

    /** Modifica la ganancia del preamplificador. */
    suspend fun setPreamp(preampDb: Float)

    /** Actualiza una banda individual. */
    suspend fun setBand(band: EqBand)

    /** Actualiza la lista completa de bandas. */
    suspend fun setBands(bands: List<EqBand>)

    /** Aplica un preset por nombre. */
    suspend fun applyPreset(presetName: String)

    /** Activa o desactiva el limitador contra clipping. */
    suspend fun setLimiterEnabled(enabled: Boolean)

    /** Restablece el ecualizador a valores por defecto. */
    suspend fun reset()

    /** Libera recursos y cancela suscripciones reactivas. */
    fun release()
}
