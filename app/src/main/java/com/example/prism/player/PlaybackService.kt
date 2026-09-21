package com.example.prism.player

import android.content.ContentUris
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Tune buffer sizes for audio-only playback.
        // ExoPlayer's defaults are tuned for video (large video buffers).
        // These values reduce RAM footprint while keeping pre-buffered audio
        // smooth even on slow storage.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs              */ 15_000,
                /* maxBufferMs              */ 30_000,
                /* bufferForPlaybackMs      */ 1_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Automatically handles audio focus
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keeps CPU awake for playback with screen off
            .setLoadControl(loadControl)
            .build()

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
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<androidx.media3.common.MediaItem>
                ): com.google.common.util.concurrent.ListenableFuture<List<androidx.media3.common.MediaItem>> {
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
                    return com.google.common.util.concurrent.Futures.immediateFuture(resolvedItems)
                }
            })
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
