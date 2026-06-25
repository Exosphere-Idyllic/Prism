package com.example.melodyplayer

import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerThread: HandlerThread? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val thread = HandlerThread("ExoPlayerThread")
        thread.start()
        playerThread = thread

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Automatically handles audio focus
            .setLooper(thread.looper)
            .build()
        
        mediaSession = MediaSession.Builder(this, player)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            val p = player
            p.applicationLooper.let { looper ->
                if (looper.thread == Thread.currentThread()) {
                    p.release()
                } else {
                    android.os.Handler(looper).post { p.release() }
                }
            }
            release()
            mediaSession = null
        }
        playerThread?.quitSafely()
        playerThread = null
        super.onDestroy()
    }
}
