package com.example.prism.player

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerPresets
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.effects.EqualizerAudioProcessor
import com.example.prism.player.effects.EqualizerCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), KoinComponent {

    private var mediaSession: MediaSession? = null
    private val equalizerAudioProcessor: EqualizerAudioProcessor by inject()
    private val equalizerRepository: EqualizerRepository by inject()

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs              */ 15_000,
                /* maxBufferMs              */ 30_000,
                /* bufferForPlaybackMs      */ 1_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .build()

        val renderersFactory = PrismRenderersFactory(this, equalizerAudioProcessor)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true) // Automatically handles audio focus
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keeps CPU awake for playback with screen off
            .setLoadControl(loadControl)
            .build()

        // Initialize equalizer configuration from persistent state
        val initialEqConfig = equalizerRepository.equalizerConfig.value
        equalizerAudioProcessor.setConfig(initialEqConfig)
        updateAudioOffload(player, initialEqConfig.enabled)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: android.content.Intent(this, com.example.prism.MainActivity::class.java)
        val sessionActivity = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val customCommands = connectionResult.availableSessionCommands.buildUpon()
                        .add(SessionCommand(EqualizerCommands.COMMAND_SET_EQ_CONFIG, Bundle.EMPTY))
                        .add(SessionCommand(EqualizerCommands.COMMAND_SET_EQ_ENABLED, Bundle.EMPTY))
                        .add(SessionCommand(EqualizerCommands.COMMAND_SET_EQ_PREAMP, Bundle.EMPTY))
                        .add(SessionCommand(EqualizerCommands.COMMAND_SET_EQ_BAND, Bundle.EMPTY))
                        .add(SessionCommand(EqualizerCommands.COMMAND_APPLY_PRESET, Bundle.EMPTY))
                        .add(SessionCommand(EqualizerCommands.COMMAND_RESET_EQ, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        customCommands,
                        connectionResult.availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        EqualizerCommands.COMMAND_SET_EQ_CONFIG -> {
                            val configJson = args.getString(EqualizerCommands.EXTRA_CONFIG_JSON)
                            if (configJson != null) {
                                try {
                                    val config = json.decodeFromString<EqualizerConfig>(configJson)
                                    equalizerAudioProcessor.setConfig(config)
                                    updateAudioOffload(player, config.enabled)
                                } catch (e: Exception) {
                                    Timber.e(e, "Error decoding EqualizerConfig in onCustomCommand")
                                }
                            }
                        }
                        EqualizerCommands.COMMAND_SET_EQ_ENABLED -> {
                            val enabled = args.getBoolean(EqualizerCommands.EXTRA_ENABLED, false)
                            val current = equalizerAudioProcessor.getConfig()
                            equalizerAudioProcessor.setConfig(current.copy(enabled = enabled))
                            updateAudioOffload(player, enabled)
                        }
                        EqualizerCommands.COMMAND_SET_EQ_PREAMP -> {
                            val preamp = args.getFloat(EqualizerCommands.EXTRA_PREAMP_DB, 0f)
                            val current = equalizerAudioProcessor.getConfig()
                            equalizerAudioProcessor.setConfig(current.copy(preampDb = preamp))
                        }
                        EqualizerCommands.COMMAND_SET_EQ_BAND -> {
                            val bandJson = args.getString(EqualizerCommands.EXTRA_BAND_JSON)
                            if (bandJson != null) {
                                try {
                                    val band = json.decodeFromString<EqBand>(bandJson)
                                    val current = equalizerAudioProcessor.getConfig()
                                    val updatedBands = current.bands.map { if (it.id == band.id) band else it }
                                    equalizerAudioProcessor.setConfig(current.copy(bands = updatedBands))
                                } catch (e: Exception) {
                                    Timber.e(e, "Error decoding EqBand in onCustomCommand")
                                }
                            }
                        }
                        EqualizerCommands.COMMAND_APPLY_PRESET -> {
                            val presetName = args.getString(EqualizerCommands.EXTRA_PRESET_NAME)
                            if (presetName != null) {
                                val gains = EqualizerPresets.getGains(presetName)
                                if (gains != null) {
                                    val current = equalizerAudioProcessor.getConfig()
                                    val updatedBands = current.bands.mapIndexed { index, band ->
                                        val newGain = gains.getOrNull(index) ?: band.gainDb
                                        band.copy(gainDb = newGain)
                                    }
                                    equalizerAudioProcessor.setConfig(current.copy(bands = updatedBands, selectedPreset = presetName))
                                }
                            }
                        }
                        EqualizerCommands.COMMAND_RESET_EQ -> {
                            val current = equalizerAudioProcessor.getConfig()
                            val defaultBands = EqualizerConfig.defaultBands()
                            equalizerAudioProcessor.setConfig(
                                current.copy(
                                    preampDb = 0f,
                                    selectedPreset = "Flat",
                                    bands = defaultBands
                                )
                            )
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> {
                    val resolvedItems = mediaItems.map { item ->
                        if (item.localConfiguration == null) {
                            val uri = item.requestMetadata.mediaUri ?: (
                                item.mediaId.toLongOrNull()?.let { id ->
                                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                } ?: item.mediaId.toUri()
                            )
                            item.buildUpon().setUri(uri).build()
                        } else {
                            item
                        }
                    }
                    return Futures.immediateFuture(resolvedItems)
                }
            })
            .build()
    }

    private fun updateAudioOffload(player: ExoPlayer, eqEnabled: Boolean) {
        val offloadMode = if (eqEnabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        }
        val preferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(offloadMode)
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(preferences)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if ((player?.playWhenReady == false) || (player?.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
