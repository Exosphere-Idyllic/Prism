package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode

/**
 * Controller interface for equalizer operations.
 * Separated from PlaybackManager according to the Interface Segregation Principle (ISP).
 */
interface EqualizerController {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setMode(mode: EqualizerMode)
    suspend fun setPreamp(preampDb: Float)
    suspend fun setBand(band: EqBand)
    suspend fun setBands(bands: List<EqBand>)
    suspend fun applyPreset(presetName: String)
    suspend fun setLimiterEnabled(enabled: Boolean)
    suspend fun reset()
    fun release()
}
