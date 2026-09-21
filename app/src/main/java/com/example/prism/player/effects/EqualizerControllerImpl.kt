package com.example.prism.player.effects

import android.os.Bundle
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.CustomCommandDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class EqualizerControllerImpl(
    private val equalizerRepository: EqualizerRepository,
    private val commandDispatcher: CustomCommandDispatcher,
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EqualizerController {

    private val syncJob = scope.launch(dispatchers.default) {
        equalizerRepository.equalizerConfig.collectLatest { config ->
            dispatchConfig(config)
        }
    }

    private fun dispatchConfig(config: EqualizerConfig) {
        val args = Bundle().apply {
            putString(EqualizerCommands.EXTRA_CONFIG_JSON, json.encodeToString(EqualizerConfig.serializer(), config))
        }
        commandDispatcher.sendCustomCommand(EqualizerCommands.COMMAND_SET_EQ_CONFIG, args)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        equalizerRepository.setEnabled(enabled)
    }

    override suspend fun setMode(mode: EqualizerMode) {
        equalizerRepository.setMode(mode)
    }

    override suspend fun setPreamp(preampDb: Float) {
        equalizerRepository.setPreamp(preampDb)
    }

    override suspend fun setBand(band: EqBand) {
        equalizerRepository.setBand(band)
    }

    override suspend fun setBands(bands: List<EqBand>) {
        equalizerRepository.setBands(bands)
    }

    override suspend fun applyPreset(presetName: String) {
        equalizerRepository.applyPreset(presetName)
    }

    override suspend fun setLimiterEnabled(enabled: Boolean) {
        equalizerRepository.setLimiterEnabled(enabled)
    }

    override suspend fun reset() {
        equalizerRepository.reset()
    }

    override fun release() {
        syncJob.cancel()
    }
}
