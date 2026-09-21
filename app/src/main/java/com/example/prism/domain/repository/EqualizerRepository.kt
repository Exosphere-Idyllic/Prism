package com.example.prism.domain.repository

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import kotlinx.coroutines.flow.StateFlow

interface EqualizerRepository {
    val equalizerConfig: StateFlow<EqualizerConfig>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun setMode(mode: EqualizerMode)
    suspend fun setPreamp(preampDb: Float)
    suspend fun setBand(band: EqBand)
    suspend fun setBands(bands: List<EqBand>)
    suspend fun applyPreset(presetName: String)
    suspend fun setLimiterEnabled(enabled: Boolean)
    suspend fun reset()
    suspend fun updateConfig(transform: (EqualizerConfig) -> EqualizerConfig)
}
