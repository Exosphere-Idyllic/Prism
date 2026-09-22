package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

/**
 * Representa una banda individual dentro de la cascada del ecualizador.
 *
 * @property id Identificador único de la banda (0-indexado).
 * @property enabled Indica si la banda se encuentra activa en el procesamiento de audio.
 * @property type Tipo de filtro biquad aplicado ([EqFilterType]).
 * @property frequencyHz Frecuencia central o de corte en Hertz (rango operativo: 20 Hz a 20,000 Hz).
 * @property gainDb Ganancia en decibelios en el rango de -12 dB a +12 dB. Aplica a filtros BELL, LOW_SHELF y HIGH_SHELF.
 * @property q Factor de calidad Q (ancho de banda y resonancia del filtro).
 */
@Serializable
data class EqBand(
    val id: Int,
    val enabled: Boolean = true,
    val type: EqFilterType = EqFilterType.BELL,
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 1.414f,
)
