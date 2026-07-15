package com.example.melodyplayer

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.net.toUri
import com.example.melodyplayer.data.AppDatabase
import com.example.melodyplayer.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val app = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)
    private val repository = MainApplication.repository

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState = _progressState.asStateFlow()

    val currentSong = _uiState
        .map { it.currentSong }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlayingState = _uiState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private var activePlaylist: List<Song> = emptyList()
    private var controllerSongs: List<Song> = emptyList()
    private var pendingControllerSongs: List<Song>? = null
    private var lastSeekTime = 0L

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var shiftWindowJob: Job? = null
    private var playerListener: Player.Listener? = null


    init {
        initializeController()
        // Songs are loaded lazily in playSong() only when playback is triggered.
        // This avoids the startup bottleneck of loading 597+ rows into memory.
    }

    private fun syncStateFromController(controller: MediaController, songs: List<Song>) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val song = songs.find { it.id == currentMediaId }
        _uiState.value = _uiState.value.copy(
            currentSong = song ?: songs.firstOrNull(),
            isPlaying = controller.isPlaying
        )
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L)
        )
        if (activePlaylist.isEmpty() && controller.mediaItemCount > 0) {
            activePlaylist = songs
        }
    }

    private fun updateControllerMediaItems(
        controller: MediaController,
        songs: List<Song>,
        currentSong: Song? = null,
        onComplete: () -> Unit = {}
    ) {
        if ((pendingControllerSongs ?: controllerSongs) == songs && controller.mediaItemCount > 0) {
            onComplete()
            return
        }
        pendingControllerSongs = songs

        // Only the current song needs artwork immediately (notification, mini-player).
        // All other items in the window get null artwork to avoid a 50-item decode burst.
        val currentSongId = currentSong?.id

        viewModelScope.launch(Dispatchers.Default) {
            val mediaItems = songs.map { song ->
                val artworkUri = if (song.id == currentSongId && song.artworkUri.isNotEmpty())
                    song.artworkUri.toUri()
                else
                    null
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.mediaUri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            }
            withContext(Dispatchers.Main) {
                try {
                    controller.setMediaItems(mediaItems)
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controllerSongs = songs
                    pendingControllerSongs = null
                    syncStateFromController(controller, songs)
                    onComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "MediaController setMediaItems failed in updateControllerMediaItems", e)
                }
            }
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
            } catch (e: Exception) {
                Log.e(TAG, "MediaController init failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController(controller: MediaController) {
        playerListener?.let { controller.removeListener(it) }

        // Do NOT push all songs into ExoPlayer on startup — that IPC call with
        // thousands of MediaItems is the primary cause of the 50s+ startup freeze.
        // Songs are loaded lazily only when the user actually triggers playback.
        // If the service already has items (resumed session), just sync UI state.
        if (controller.mediaItemCount > 0) {
            syncStateFromController(controller, controllerSongs)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Look up in controllerSongs (the window currently loaded in ExoPlayer)
                // then fall back to the full library so next/previous always resolves.
                val song = controllerSongs.find { it.id == mediaItem?.mediaId }
                    ?: repository.allSongs.value.find { it.id == mediaItem?.mediaId }
                _uiState.value = _uiState.value.copy(currentSong = song)
                _progressState.value = _progressState.value.copy(duration = controller.duration.coerceAtLeast(0L))

                if (song != null && activePlaylist.isNotEmpty()) {
                    val currentIndex = controllerSongs.indexOfFirst { it.id == song.id }
                    if (currentIndex != -1) {
                        val threshold = 5
                        val isSeek = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                        val nearEdge = currentIndex < threshold || currentIndex >= controllerSongs.size - threshold
                        if (isSeek || nearEdge) {
                            val immediate = isSeek || currentIndex < 2 || currentIndex >= controllerSongs.size - 2
                            shiftWindow(controller, song, immediate = immediate)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _progressState.value = _progressState.value.copy(duration = controller.duration.coerceAtLeast(0L))
            }
        }
        controller.addListener(listener)
        playerListener = listener
        if (controller.isPlaying) startProgressUpdate()
    }

    private fun buildPlaybackWindow(song: Song, playlist: List<Song>, windowSize: Int = 50): Pair<List<Song>, Int> {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index == -1) return Pair(listOf(song), 0)
        
        val half = windowSize / 2
        val start = (index - half).coerceAtLeast(0)
        val end = (index + half).coerceAtMost(playlist.size)
        val windowSongs = playlist.subList(start, end)
        val windowIndex = index - start
        return Pair(windowSongs, windowIndex)
    }

    private fun shiftWindow(controller: MediaController, currentSong: Song, immediate: Boolean = false) {
        val playlist = activePlaylist
        if (playlist.isEmpty()) return

        val (windowSongs, windowIndex) = buildPlaybackWindow(currentSong, playlist)
        if ((pendingControllerSongs ?: controllerSongs) == windowSongs) return

        // Cancel any in-flight shift that hasn't run yet (debounce for rapid skips).
        shiftWindowJob?.cancel()
        pendingControllerSongs = windowSongs

        // Only the current song needs artwork immediately; the rest carry null to
        // avoid a ~50-item artwork-decode burst on every window slide.
        val currentSongId = currentSong.id

        shiftWindowJob = viewModelScope.launch(Dispatchers.Default) {
            // Absorb rapid consecutive transitions before doing any real work.
            if (!immediate) {
                delay(300)
            }

            val mediaItems = windowSongs.map { song ->
                val artworkUri = if (song.id == currentSongId && song.artworkUri.isNotEmpty())
                    song.artworkUri.toUri()
                else
                    null
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.mediaUri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            }
            withContext(Dispatchers.Main) {
                try {
                    val currentPos = controller.currentPosition
                    val wasPlaying = controller.isPlaying
                    controller.setMediaItems(mediaItems, windowIndex, currentPos)
                    if (wasPlaying) {
                        controller.play()
                    }
                    controllerSongs = windowSongs
                    pendingControllerSongs = null
                } catch (e: Exception) {
                    Log.e(TAG, "MediaController setMediaItems failed in shiftWindow", e)
                }
            }
        }
    }

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        val controller = mediaController ?: return
        
        viewModelScope.launch {
            val listToUse = if (playlistSongs.isNotEmpty()) {
                playlistSongs
            } else {
                val cached = repository.allSongs.value
                if (cached.isNotEmpty()) {
                    cached
                } else {
                    val dbSongs = withContext(Dispatchers.IO) { database.songDao().getAllSongs() }
                    dbSongs
                }
            }
            activePlaylist = listToUse

            val (windowSongs, windowIndex) = buildPlaybackWindow(song, listToUse)
            updateControllerMediaItems(controller, windowSongs, currentSong = song) {
                controller.seekTo(windowIndex, 0)
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { mediaController?.seekToNext() }
    fun previous() { mediaController?.seekToPrevious() }
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _progressState.value = _progressState.value.copy(currentPosition = positionMs)
        lastSeekTime = System.currentTimeMillis()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let {
                    if (System.currentTimeMillis() - lastSeekTime > 500) {
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

    override fun onCleared() {
        super.onCleared()
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        stopProgressUpdate()
        shiftWindowJob?.cancel()
    }
}

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val totalSongsCount: Int = 0
)

data class ProgressState(
    val currentPosition: Long = 0L,
    val duration: Long = 0L
)
