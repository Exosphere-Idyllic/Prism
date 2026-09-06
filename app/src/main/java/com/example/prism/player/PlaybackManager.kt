package com.example.prism.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.ui.player.ProgressState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

interface PlaybackManager {
    val currentSong: StateFlow<Song?>
    val isPlayingState: StateFlow<Boolean>
    val progressState: StateFlow<ProgressState>

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList())
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun release()
}

class PlaybackManagerImpl(
    private val app: Application,
    private val repository: LibraryRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) : PlaybackManager {

    companion object {
        private const val SEEK_CONFIRM_TIMEOUT_MS = 2000L
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong = _currentSong.asStateFlow()

    private val _isPlayingState = MutableStateFlow(false)
    override val isPlayingState = _isPlayingState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    override val progressState = _progressState.asStateFlow()

    private var activePlaylist: List<Song> = emptyList()
    private var pendingPlay: Pair<Song, List<Song>>? = null

    @Volatile
    private var pendingSeekPositionMs: Long? = null
    private var seekConfirmTimeoutJob: Job? = null

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playJob: Job? = null
    private var progressJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
                pendingPlay?.let { (song, playlist) ->
                    pendingPlay = null
                    playSong(song, playlist)
                }
            } catch (e: Exception) {
                Timber.e(e, "MediaController init failed")
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun setupController(controller: MediaController) {
        playerListener?.let { controller.removeListener(it) }

        if (controller.mediaItemCount > 0) {
            syncStateFromController(controller)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingState.value = isPlaying
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songId = mediaItem?.mediaId
                val song = activePlaylist.find { it.id == songId }
                if (song != null) {
                    updateCurrentSong(song, controller)
                } else if (songId != null) {
                    scope.launch(dispatchers.io) {
                        val dbSong = repository.getSongById(songId)
                        withContext(dispatchers.main) {
                            updateCurrentSong(dbSong, controller)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _progressState.value = _progressState.value.copy(
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    clearPendingSeek(newPosition.positionMs)
                }
            }
        }
        controller.addListener(listener)
        playerListener = listener
        if (controller.isPlaying) startProgressUpdate()
    }

    private fun syncStateFromController(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val song = activePlaylist.find { it.id == currentMediaId }
        _currentSong.value = song
        _isPlayingState.value = controller.isPlaying
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L)
        )

        if (song == null && currentMediaId != null) {
            scope.launch(dispatchers.io) {
                val dbSong = repository.getSongById(currentMediaId)
                if (dbSong != null) {
                    withContext(dispatchers.main) {
                        if (mediaController?.currentMediaItem?.mediaId == currentMediaId) {
                            _currentSong.value = dbSong
                        }
                    }
                }
            }
        }
    }

    private fun updateCurrentSong(song: Song?, controller: MediaController) {
        _currentSong.value = song
        _progressState.value = _progressState.value.copy(
            duration = controller.duration.coerceAtLeast(0L)
        )
    }

    override fun playSong(song: Song, playlistSongs: List<Song>) {
        val controller = mediaController
        if (controller == null) {
            pendingPlay = song to playlistSongs
            return
        }
        playJob?.cancel()

        playJob = scope.launch(dispatchers.main) {
            val songs: List<Song> = withContext(dispatchers.io) {
                playlistSongs.ifEmpty { repository.getAllSongs() }
            }
            activePlaylist = songs

            val startIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            val mediaItems: List<MediaItem> = withContext(dispatchers.default) {
                songs.toMediaItems()
            }

            try {
                _currentSong.value = song
                controller.setMediaItems(mediaItems, startIndex, 0L)
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
            } catch (e: Exception) {
                Timber.e(e, "setMediaItems failed in playSong")
            }
        }
    }

    override fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun next() {
        mediaController?.seekToNext()
    }

    override fun previous() {
        mediaController?.seekToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _progressState.value = _progressState.value.copy(currentPosition = positionMs)
        markPendingSeek(positionMs)
    }

    private fun markPendingSeek(positionMs: Long) {
        pendingSeekPositionMs = positionMs
        seekConfirmTimeoutJob?.cancel()
        seekConfirmTimeoutJob = scope.launch(dispatchers.main) {
            delay(SEEK_CONFIRM_TIMEOUT_MS.milliseconds)
            pendingSeekPositionMs = null
        }
    }

    private fun clearPendingSeek(confirmedPositionMs: Long) {
        pendingSeekPositionMs = null
        seekConfirmTimeoutJob?.cancel()
        _progressState.value = _progressState.value.copy(
            currentPosition = confirmedPositionMs.coerceAtLeast(0L)
        )
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch(dispatchers.main) {
            while (true) {
                mediaController?.let {
                    if (pendingSeekPositionMs == null) {
                        _progressState.value = _progressState.value.copy(
                            currentPosition = it.currentPosition.coerceAtLeast(0L),
                            duration = it.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(250.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() = progressJob?.cancel()

    override fun release() {
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        stopProgressUpdate()
        playJob?.cancel()
        seekConfirmTimeoutJob?.cancel()
    }
}
