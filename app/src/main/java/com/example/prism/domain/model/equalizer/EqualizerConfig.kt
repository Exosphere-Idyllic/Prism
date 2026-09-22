package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

/**
 * Estado completo inmutable de la configuración del ecualizador.
 *
 * @property enabled Indica si el procesamiento del ecualizador está activo. Si es false, el procesador opera en bypass bit-perfect.
 * @property mode Modo activo ([EqualizerMode.SIMPLE] o [EqualizerMode.ADVANCED]).
 * @property preampDb Ganancia de preamplificación previa al banco de filtros en dB (-12 dB a +12 dB).
 * @property selectedPreset Nombre del preset activo en modo simple ("Flat", "Rock", etc.) o null si se modificó manualmente ("Custom").
 * @property bands Lista de bandas de filtrado configuradas ([EqBand]).
 * @property limiterEnabled Si es true, activa la protección de saturación suave (soft-knee limiter) para prevenir clipping digital.
 */
@Serializable
data class EqualizerConfig(
    val enabled: Boolean = false,
    val mode: EqualizerMode = EqualizerMode.SIMPLE,
    val preampDb: Float = 0f,
    val selectedPreset: String? = "Flat",
    val bands: List<EqBand> = defaultBands(),
    val limiterEnabled: Boolean = true,
) {
    companion object {
        /** Frecuencias ISO estándar (31 Hz a 16 kHz) utilizadas en modo simple. */
        val SIMPLE_FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f
        )

        /**
         * Genera las 10 bandas por defecto para el modo [EqualizerMode.SIMPLE] con ganancia 0 dB.
         * La primera banda se configura como LOW_SHELF, la última como HIGH_SHELF, y las intermedias como BELL.
         */
        fun defaultBands(): List<EqBand> {
            return SIMPLE_FREQUENCIES.mapIndexed { index, freq ->
                EqBand(
                    id = index,
                    enabled = true,
                    type = when (index) {
                        0 -> EqFilterType.LOW_SHELF
                        SIMPLE_FREQUENCIES.lastIndex -> EqFilterType.HIGH_SHELF
                        else -> EqFilterType.BELL
                    },
                    frequencyHz = freq,
                    gainDb = 0f,
                    q = 1.414f,
                )
            }
        }

        /**
         * Genera las 8 bandas paramétricas por defecto para el modo [EqualizerMode.ADVANCED] con ganancia 0 dB.
         */
        fun defaultAdvancedBands(): List<EqBand> {
            val advFrequencies = floatArrayOf(60f, 150f, 400f, 1_000f, 2_500f, 6_000f, 12_000f, 16_000f)
            return advFrequencies.mapIndexed { index, freq ->
                EqBand(
                    id = index,
                    enabled = true,
                    type = when (index) {
                        0 -> EqFilterType.LOW_SHELF
                        advFrequencies.lastIndex -> EqFilterType.HIGH_SHELF
                        else -> EqFilterType.BELL
                    },
                    frequencyHz = freq,
                    gainDb = 0f,
                    q = 1.414f,
                )
            }
        }
    }
}
