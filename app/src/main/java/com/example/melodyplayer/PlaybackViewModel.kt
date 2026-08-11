package com.example.melodyplayer

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.melodyplayer.data.Song
import com.google.common.util.concurrent.ListenableFuture
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

    // Songs currently loaded in the ExoPlayer queue (used for next/prev lookup)
    @Volatile private var queuedSongs: List<Song> = emptyList()
    private var lastSeekTime = 0L

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        initializeController()
    }

    // ── Controller setup ──────────────────────────────────────────────────────

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
        }, ContextCompat.getMainExecutor(app))
    }

    private fun setupController(controller: MediaController) {
        playerListener?.let { controller.removeListener(it) }

        // If the service already has items (resumed session), restore UI state.
        if (controller.mediaItemCount > 0) {
            syncStateFromController(controller)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songId = mediaItem?.mediaId
                val song = queuedSongs.find { it.id == songId }
                if (song != null) {
                    _uiState.value = _uiState.value.copy(currentSong = song)
                    _progressState.value = _progressState.value.copy(
                        duration = controller.duration.coerceAtLeast(0L)
                    )
                } else if (songId != null) {
                    // Fallback: look up the song in the database (e.g. after a session restore)
                    viewModelScope.launch(Dispatchers.IO) {
                        val dbSong = repository.getSongById(songId)
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(currentSong = dbSong)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _progressState.value = _progressState.value.copy(
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }
        }
        controller.addListener(listener)
        playerListener = listener
        if (controller.isPlaying) startProgressUpdate()
    }

    private fun syncStateFromController(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val song = queuedSongs.find { it.id == currentMediaId }
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            isPlaying = controller.isPlaying
        )
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L)
        )
    }

    // ── Playback commands ─────────────────────────────────────────────────────

    /**
     * Plays [song] in the context of [playlistSongs].
     *
     * If [playlistSongs] is empty, fetches the full library sorted by title (the
     * same order shown in the UI) on a background thread, so there is no Main-thread
     * stall and the correct song is always at the correct index.
     *
     * Fix: previously getSongsWindow used SQLite rowid (unordered) to compute an
     * offset into a title-sorted query, causing a completely wrong set of songs to
     * be loaded, which is why a random song played and Next/Previous were broken.
     */
    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        val controller = mediaController ?: return

        viewModelScope.launch {
            // Fetch / use the context playlist on a background thread
            val songs: List<Song> = withContext(Dispatchers.IO) {
                playlistSongs.ifEmpty { repository.getAllSongs() }
            }

            // Find the index of the requested song in the ordered list
            val startIndex = songs.indexOfFirst { it.id == song.id }
                .coerceAtLeast(0)

            // Build lightweight MediaItems off the main thread (no bitmap decoding)
            val mediaItems: List<MediaItem> = withContext(Dispatchers.Default) {
                MediaItemBuilder.buildMediaItems(songs, song.id)
            }

            // Switch back to Main for ExoPlayer IPC calls
            withContext(Dispatchers.Main) {
                try {
                    queuedSongs = songs
                    _uiState.value = _uiState.value.copy(currentSong = song)
                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
                } catch (e: Exception) {
                    Log.e(TAG, "setMediaItems failed in playSong", e)
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _progressState.value = _progressState.value.copy(currentPosition = positionMs)
        lastSeekTime = System.currentTimeMillis()
    }

    // ── Progress polling ──────────────────────────────────────────────────────

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let {
                    // Skip update briefly after a manual seek to avoid position
                    // jumping back before ExoPlayer confirms the new position.
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