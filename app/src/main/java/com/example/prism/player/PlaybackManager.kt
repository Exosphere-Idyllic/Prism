package com.example.prism.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.artwork.AlbumArtFetcher
import com.example.prism.data.artwork.SongArtworkParams
import com.example.prism.data.entity.Song
import com.example.prism.data.preferences.PlaybackPreferences
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
    val shuffleModeState: StateFlow<Boolean>
    val repeatModeState: StateFlow<Int>

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList())
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun toggleRepeatMode()
    fun release()
}

class PlaybackManagerImpl(
    private val app: Application,
    private val repository: LibraryRepository,
    private val preferences: PlaybackPreferences,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) : PlaybackManager, CustomCommandDispatcher {

    companion object {
        /**
         * Window size for MediaItems sent to ExoPlayer via Binder IPC.
         * Enforces safety against [android.os.TransactionTooLargeException] (1 MB Binder cap).
         */
        private const val INITIAL_WINDOW = 51
        private const val EXPAND_THRESHOLD = 35
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong = _currentSong.asStateFlow()

    private val _isPlayingState = MutableStateFlow(false)
    override val isPlayingState = _isPlayingState.asStateFlow()

    private val _shuffleModeState = MutableStateFlow(false)
    override val shuffleModeState = _shuffleModeState.asStateFlow()

    private val _repeatModeState = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatModeState = _repeatModeState.asStateFlow()

    private val progressTracker = ProgressTracker(dispatchers, scope)
    override val progressState = progressTracker.progressState

    @Volatile
    private var originalQueueIds: List<String> = emptyList()

    @Volatile
    private var shuffledQueueIds: List<String> = emptyList()

    @Volatile
    private var activePlaylist: List<Song> = emptyList()

    @Volatile
    private var activeWindowStart: Int = 0

    @Volatile
    private var cachedContextSongs: Map<String, Song>? = null

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
        scope.launch(dispatchers.main) {
            val restoredShuffle = withContext(dispatchers.io) { preferences.getShuffleEnabled() }
            val restoredRepeat = withContext(dispatchers.io) { preferences.getRepeatMode() }
            _shuffleModeState.value = restoredShuffle
            _repeatModeState.value = restoredRepeat
            mediaController?.repeatMode = restoredRepeat
        }
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

        controller.repeatMode = _repeatModeState.value

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

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (_repeatModeState.value != repeatMode) {
                    _repeatModeState.value = repeatMode
                    scope.launch(dispatchers.io) {
                        preferences.setRepeatMode(repeatMode)
                    }
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

                // Incremental queue expansion when user advances near the end of active window
                val activeQueue = if (_shuffleModeState.value) shuffledQueueIds else originalQueueIds
                val currentIndex = controller.currentMediaItemIndex
                if (currentIndex >= EXPAND_THRESHOLD && (activeWindowStart + activePlaylist.size) < activeQueue.size) {
                    expandQueueIfNeeded(controller)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
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
                            warmUpArtworkCache(dbSong)
                        }
                    }
                }
            }
        } else if (song != null) {
            warmUpArtworkCache(song)
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
        if (song != null) {
            warmUpArtworkCache(song)
        }
    }

    private fun warmUpArtworkCache(song: Song) {
        val imageLoader = SingletonImageLoader.get(app)
        val request = ImageRequest.Builder(app)
            .data(SongArtworkParams(song = song, size = AlbumArtFetcher.MASTER_ARTWORK_SIZE))
            .size(AlbumArtFetcher.MASTER_ARTWORK_SIZE)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoader.enqueue(request)

        val mediaItemArtworkUri = when {
            song.customArtworkUri.isNotEmpty() -> song.customArtworkUri.toUri()
            song.artworkUri.isNotEmpty() -> song.artworkUri.toUri()
            song.albumId > 0L -> ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, song.albumId)
            song.mediaUri.isNotEmpty() -> song.mediaUri.toUri()
            else -> null
        }
        if (mediaItemArtworkUri != null) {
            val uriRequest = ImageRequest.Builder(app)
                .data(mediaItemArtworkUri)
                .size(AlbumArtFetcher.MASTER_ARTWORK_SIZE)
                .allowHardware(false) // Must be software-rendered for notifications
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(uriRequest)
        }
    }

    private suspend fun resolveSongs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val cached = cachedContextSongs
        return if (cached != null && cached.isNotEmpty()) {
            val resolvedFromCache = ids.mapNotNull { cached[it] }
            if (resolvedFromCache.size == ids.size) {
                resolvedFromCache
            } else {
                val missingIds = ids.filterNot { cached.containsKey(it) }
                val fromDb = repository.getSongsByIds(missingIds).associateBy { it.id }
                ids.mapNotNull { cached[it] ?: fromDb[it] }
            }
        } else {
            repository.getSongsByIds(ids)
        }
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
            val originalIds: List<String> = withContext(dispatchers.io) {
                if (playlistSongs.isNotEmpty()) {
                    cachedContextSongs = playlistSongs.associateBy { it.id }
                    val ids = playlistSongs.map { it.id }
                    if (!ids.contains(song.id)) listOf(song.id) + ids else ids
                } else {
                    cachedContextSongs = null
                    val ids = repository.getAllSongIds()
                    if (!ids.contains(song.id)) listOf(song.id) + ids else ids
                }
            }
            originalQueueIds = originalIds

            val isShuffle = _shuffleModeState.value
            val activeQueue = if (isShuffle) {
                val remaining = (originalIds - song.id).shuffled()
                val shuffled = listOf(song.id) + remaining
                shuffledQueueIds = shuffled
                shuffled
            } else {
                shuffledQueueIds = emptyList()
                originalIds
            }

            // Windowing to avoid IPC bottlenecks: center around selected song
            val rawIndex = activeQueue.indexOf(song.id).coerceAtLeast(0)
            val windowSize = INITIAL_WINDOW.coerceAtMost(activeQueue.size)
            val halfWindow = windowSize / 2
            val windowStart = (rawIndex - halfWindow).coerceIn(0, (activeQueue.size - windowSize).coerceAtLeast(0))
            val windowEnd = (windowStart + windowSize).coerceAtMost(activeQueue.size)

            val windowIds = activeQueue.subList(windowStart, windowEnd)
            activeWindowStart = windowStart

            val windowSongs = withContext(dispatchers.io) {
                resolveSongs(windowIds)
            }
            activePlaylist = windowSongs

            val startIndex = windowSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            val mediaItems: List<MediaItem> = withContext(dispatchers.default) {
                windowSongs.toMediaItems()
            }

            try {
                _currentSong.value = song
                controller.repeatMode = _repeatModeState.value
                controller.setMediaItems(mediaItems, startIndex, 0L)
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
            } catch (e: Exception) {
                Timber.e(e, "setMediaItems failed in playSong")
            }
        }
    }

    private fun expandQueueIfNeeded(controller: MediaController) {
        val activeQueue = if (_shuffleModeState.value) shuffledQueueIds else originalQueueIds
        val currentEnd = activeWindowStart + activePlaylist.size
        val newEnd = (currentEnd + INITIAL_WINDOW).coerceAtMost(activeQueue.size)
        if (newEnd <= currentEnd) return

        val idsToAdd = activeQueue.subList(currentEnd, newEnd)

        scope.launch(dispatchers.default) {
            val songsToAdd = withContext(dispatchers.io) {
                resolveSongs(idsToAdd)
            }
            val newItems = songsToAdd.toMediaItems()

            withContext(dispatchers.main) {
                try {
                    controller.addMediaItems(newItems)
                    activePlaylist = activePlaylist + songsToAdd
                    Timber.d("Expanded queue by %d items (total in active window: %d)", newItems.size, activePlaylist.size)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to expand queue in PlaybackManager")
                }
            }
        }
    }

    override fun toggleShuffle() {
        val newShuffle = !_shuffleModeState.value
        _shuffleModeState.value = newShuffle
        scope.launch(dispatchers.io) {
            preferences.setShuffleEnabled(newShuffle)
        }

        val controller = mediaController ?: return
        val currentSong = _currentSong.value ?: return
        if (originalQueueIds.isEmpty()) return

        playJob?.cancel()
        playJob = scope.launch(dispatchers.main) {
            val activeQueue = if (newShuffle) {
                val remaining = (originalQueueIds - currentSong.id).shuffled()
                val shuffled = listOf(currentSong.id) + remaining
                shuffledQueueIds = shuffled
                shuffled
            } else {
                shuffledQueueIds = emptyList()
                originalQueueIds
            }

            val rawIndex = activeQueue.indexOf(currentSong.id).coerceAtLeast(0)
            val windowSize = INITIAL_WINDOW.coerceAtMost(activeQueue.size)
            val halfWindow = windowSize / 2
            val windowStart = (rawIndex - halfWindow).coerceIn(0, (activeQueue.size - windowSize).coerceAtLeast(0))
            val windowEnd = (windowStart + windowSize).coerceAtMost(activeQueue.size)

            val windowIds = activeQueue.subList(windowStart, windowEnd)
            activeWindowStart = windowStart

            val windowSongs = withContext(dispatchers.io) {
                resolveSongs(windowIds)
            }
            activePlaylist = windowSongs

            val startIndex = windowSongs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            val currentPos = controller.currentPosition.coerceAtLeast(0L)

            val mediaItems = withContext(dispatchers.default) {
                windowSongs.toMediaItems()
            }

            try {
                controller.setMediaItems(mediaItems, startIndex, currentPos)
                controller.play()
            } catch (e: Exception) {
                Timber.e(e, "setMediaItems failed in toggleShuffle")
            }
        }
    }

    override fun toggleRepeatMode() {
        val nextMode = when (_repeatModeState.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatModeState.value = nextMode
        mediaController?.repeatMode = nextMode
        scope.launch(dispatchers.io) {
            preferences.setRepeatMode(nextMode)
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
        scope.launch(dispatchers.main) {
            val controller = mediaController
            if (controller != null) {
                val command = SessionCommand(action, android.os.Bundle.EMPTY)
                controller.sendCustomCommand(command, args)
            } else {
                Timber.w("MediaController not connected yet; custom command %s was dropped", action)
            }
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
