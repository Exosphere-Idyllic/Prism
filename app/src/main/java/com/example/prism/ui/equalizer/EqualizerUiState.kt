package com.example.prism.ui.equalizer

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.model.equalizer.EqualizerPresets

data class EqualizerUiState(
    val enabled: Boolean = false,
    val mode: EqualizerMode = EqualizerMode.SIMPLE,
    val preampDb: Float = 0f,
    val selectedPreset: String? = "Flat",
    val availablePresets: List<String> = EqualizerPresets.presetNames,
    val bands: List<EqBand> = EqualizerConfig.defaultBands(),
    val limiterEnabled: Boolean = true,
    val selectedBandId: Int? = null,
)
