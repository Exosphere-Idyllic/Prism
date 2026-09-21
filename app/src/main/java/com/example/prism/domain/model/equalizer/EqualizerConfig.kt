package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

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
        val SIMPLE_FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f
        )

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
