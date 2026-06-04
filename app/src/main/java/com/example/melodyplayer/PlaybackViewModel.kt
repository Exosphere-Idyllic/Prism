package com.example.melodyplayer

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.melodyplayer.data.MockPlaylist
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val context = getApplication<Application>().applicationContext
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController(controller: MediaController) {
        // Initialize playlist in the controller if empty
        if (controller.mediaItemCount == 0) {
            val mediaItems = MockPlaylist.songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.mediaUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(android.net.Uri.parse(song.artworkUri))
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems)
            controller.prepare()
        }

        // Synchronize initial state
        updateStateFromController(controller)

        // Listen for changes
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val song = MockPlaylist.songs.find { it.id == mediaItem?.mediaId }
                _uiState.value = _uiState.value.copy(
                    currentSong = song ?: _uiState.value.currentSong,
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }
        })

        if (controller.isPlaying) {
            startProgressUpdate()
        }
    }

    private fun updateStateFromController(controller: MediaController) {
        val currentMediaItem = controller.currentMediaItem
        val song = MockPlaylist.songs.find { it.id == currentMediaItem?.mediaId }
        _uiState.value = PlaybackUiState(
            currentSong = song ?: MockPlaylist.songs.firstOrNull(),
            isPlaying = controller.isPlaying,
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L),
            playlist = MockPlaylist.songs
        )
    }

    fun playSong(song: Song) {
        val controller = mediaController ?: return
        val index = MockPlaylist.songs.indexOfFirst { it.id == song.id }
        if (index != -1) {
            controller.seekTo(index, 0)
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPosition = positionMs)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    _uiState.value = _uiState.value.copy(
                        currentPosition = controller.currentPosition.coerceAtLeast(0L),
                        duration = controller.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        stopProgressUpdate()
    }
}

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playlist: List<Song> = emptyList()
)
