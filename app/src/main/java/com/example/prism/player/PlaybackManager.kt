package com.example.prism.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.player.toMediaItems
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

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
) : PlaybackManager, CustomCommandDispatcher {

    companion object {
        /**
         * Maximum number of MediaItems sent through Binder IPC in a single batch.
         * Enforces safety against [android.os.TransactionTooLargeException] (1 MB Binder cap).
         */
        private const val MAX_QUEUE_SIZE = 1000
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong = _currentSong.asStateFlow()

    private val _isPlayingState = MutableStateFlow(false)
    override val isPlayingState = _isPlayingState.asStateFlow()

    private val progressTracker = ProgressTracker(dispatchers, scope)
    override val progressState = progressTracker.progressState

    @Volatile
    private var activePlaylist: List<Song> = emptyList()

    private val pendingPlay = AtomicReference<Pair<Song, List<Song>>?>(null)

    @Volatile
    private var mediaController: MediaController? = null
    @Volatile
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playJob: Job? = null
    private var playerListener: Player.Listener? = null

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            Timber.w("MediaController disconnected from service")
            mediaController = null
            _isPlayingState.value = false
            progressTracker.attachController(null)
        }
    }

    init {
        initializeController()
    }

    @Synchronized
    private fun initializeController() {
        if (mediaController != null || controllerFuture != null) return

        try {
            val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val future = MediaController.Builder(app, sessionToken)
                .setListener(controllerListener)
                .buildAsync()
            controllerFuture = future

            future.addListener({
                try {
                    val controller = future.get() ?: return@addListener
                    mediaController = controller
                    setupController(controller)
                    pendingPlay.getAndSet(null)?.let { (song, playlist) ->
                        playSong(song, playlist)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "MediaController init failed")
                    mediaController = null
                } finally {
                    controllerFuture = null
                }
            }, ContextCompat.getMainExecutor(app))
        } catch (e: Exception) {
            Timber.e(e, "SessionToken or MediaController build failed")
            mediaController = null
            controllerFuture = null
        }
    }

    private fun setupController(controller: MediaController) {
        playerListener?.let { mediaController?.removeListener(it) }

        if (controller.mediaItemCount > 0) {
            syncStateFromController(controller)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingState.value = isPlaying
                progressTracker.onPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                progressTracker.onDurationChanged(controller.duration)
                if (playbackState == Player.STATE_ENDED) {
                    _isPlayingState.value = false
                    progressTracker.onPlayingChanged(false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "Playback error: ${error.errorCodeName}")
                _isPlayingState.value = false
                progressTracker.onPlayingChanged(false)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songId = mediaItem?.mediaId
                if (songId == null) {
                    _currentSong.value = null
                    return
                }

                val song = activePlaylist.find { it.id == songId }
                if (song != null) {
                    updateCurrentSong(song, controller)
                } else {
                    scope.launch(dispatchers.io) {
                        val dbSong = repository.getSongById(songId)
                        withContext(dispatchers.main) {
                            if (mediaController?.currentMediaItem?.mediaId == songId) {
                                updateCurrentSong(dbSong, controller)
                            }
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    progressTracker.onPositionDiscontinuity(newPosition.positionMs)
                }
            }
        }
        controller.addListener(listener)
        playerListener = listener
        progressTracker.attachController(controller)
    }

    private fun syncStateFromController(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val song = activePlaylist.find { it.id == currentMediaId }
        _currentSong.value = song
        _isPlayingState.value = controller.isPlaying
        progressTracker.syncFromController(controller)

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
        val resolvedDuration = when {
            controller.duration > 0L -> controller.duration
            (song?.duration ?: 0L) > 0L -> song!!.duration
            else -> 0L
        }
        progressTracker.onDurationChanged(resolvedDuration)
    }

    override fun playSong(song: Song, playlistSongs: List<Song>) {
        val controller = mediaController
        if (controller == null) {
            pendingPlay.set(song to playlistSongs)
            initializeController()
            return
        }
        playJob?.cancel()

        playJob = scope.launch(dispatchers.main) {
            val allSongs: List<Song> = withContext(dispatchers.io) {
                val base = playlistSongs.ifEmpty { repository.getAllSongs() }
                if (base.none { it.id == song.id }) listOf(song) + base else base
            }

            // Windowing to prevent TransactionTooLargeException:
            // Center a window of at most MAX_QUEUE_SIZE items around the selected song.
            val rawIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            val windowedSongs = if (allSongs.size > MAX_QUEUE_SIZE) {
                val halfWindow = MAX_QUEUE_SIZE / 2
                val start = (rawIndex - halfWindow).coerceAtLeast(0)
                val end = (start + MAX_QUEUE_SIZE).coerceAtMost(allSongs.size)
                val adjustedStart = (end - MAX_QUEUE_SIZE).coerceAtLeast(0)
                allSongs.subList(adjustedStart, end)
            } else {
                allSongs
            }
            activePlaylist = windowedSongs

            val startIndex = windowedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            val mediaItems: List<MediaItem> = withContext(dispatchers.default) {
                windowedSongs.toMediaItems()
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
        val controller = mediaController ?: run {
            initializeController()
            return
        }
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    override fun next() {
        mediaController?.seekToNext()
    }

    override fun previous() {
        mediaController?.seekToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        progressTracker.onSeek(positionMs)
        mediaController?.seekTo(positionMs)
    }

    override fun sendCustomCommand(action: String, args: android.os.Bundle) {
        val controller = mediaController
        if (controller != null) {
            val command = SessionCommand(action, android.os.Bundle.EMPTY)
            controller.sendCustomCommand(command, args)
        } else {
            Timber.w("MediaController not connected yet; custom command %s was dropped", action)
        }
    }

    override fun release() {
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController?.release()
        mediaController = null
        controllerFuture = null
        progressTracker.release()
        playJob?.cancel()
    }
}
