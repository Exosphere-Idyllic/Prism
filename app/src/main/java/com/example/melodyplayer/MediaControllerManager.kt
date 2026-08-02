package com.example.melodyplayer

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.melodyplayer.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles MediaController lifecycle, listeners, and playback commands.
 * Provides a clean interface for the ViewModel.
 */
class MediaControllerManager(
    private val app: Application,
    private val onControllerReady: (MediaController) -> Unit,
    private val onStateChanged: (MediaController) -> Unit
) {
    companion object {
        private const val TAG = "MediaControllerManager"
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isReleased = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun initialize() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            if (isReleased) return@addListener
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
                onControllerReady(controller)
            } catch (e: Exception) {
                Log.e(TAG, "MediaController init failed", e)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                onStateChanged(controller)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                onStateChanged(controller)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onStateChanged(controller)
            }
        })
        _isPlaying.value = controller.isPlaying
    }

    fun play() { mediaController?.play() }
    fun pause() { mediaController?.pause() }
    fun next() { mediaController?.seekToNext() }
    fun previous() { mediaController?.seekToPrevious() }
    fun seekTo(pos: Long) { mediaController?.seekTo(pos) }

    fun release() {
        isReleased = true
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    fun getController(): MediaController? = mediaController
}
