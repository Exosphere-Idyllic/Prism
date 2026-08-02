package com.example.melodyplayer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import com.example.melodyplayer.data.AppDatabase
import com.example.melodyplayer.data.Song
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
    private val windowManager = PlaybackWindowManager(viewModelScope)

    private val controllerManager = MediaControllerManager(
        app = app,
        onControllerReady = { controller -> setupInitialState(controller) },
        onStateChanged = { controller -> syncStateFromController(controller) }
    )

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState = _progressState.asStateFlow()

    val currentSong = _uiState
        .map { it.currentSong }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlayingState = controllerManager.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var lastSeekTime = 0L
    private var progressJob: Job? = null

    init {
        controllerManager.initialize()
    }

    private fun setupInitialState(controller: MediaController) {
        if (controller.mediaItemCount > 0) {
            viewModelScope.launch {
                val mediaIds = (0 until controller.mediaItemCount).mapNotNull { controller.getMediaItemAt(it).mediaId }
                if (mediaIds.isNotEmpty()) {
                    val songs = withContext(Dispatchers.IO) { database.songDao().getSongsByIds(mediaIds) }
                    val idToSong = songs.associateBy { it.id }
                    val orderedSongs = mediaIds.mapNotNull { idToSong[it] }
                    if (orderedSongs.isNotEmpty()) {
                        windowManager.setControllerSongs(orderedSongs)
                        if (windowManager.activePlaylist.isEmpty()) windowManager.setActivePlaylist(orderedSongs)
                        syncStateFromController(controller)
                    }
                }
            }
        }
    }

    private fun syncStateFromController(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        
        viewModelScope.launch {
            val song = if (currentMediaId != null) {
                windowManager.controllerSongs.firstOrNull { it.id == currentMediaId }
                    ?: windowManager.activePlaylist.firstOrNull { it.id == currentMediaId }
                    ?: repository.getSongById(currentMediaId)
            } else null
            
            _uiState.value = _uiState.value.copy(
                currentSong = song,
                isPlaying = controller.isPlaying
            )
            _progressState.value = ProgressState(
                currentPosition = controller.currentPosition.coerceAtLeast(0L),
                duration = controller.duration.coerceAtLeast(0L)
            )

            if (song != null && controller.isPlaying) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }
    }

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        val controller = controllerManager.getController() ?: return

        viewModelScope.launch {
            val listToUse = when {
                // Explicit playlist supplied by the caller — highest priority.
                playlistSongs.isNotEmpty() -> playlistSongs

                // activePlaylist already contains this song — reuse it for free
                // (no DB round-trip needed; the window manager already holds it).
                windowManager.activePlaylist.any { it.id == song.id } ->
                    windowManager.activePlaylist

                // Cold-start / external-intent fallback: fetch only the songs
                // immediately surrounding this track instead of the full library.
                else -> withContext(Dispatchers.IO) {
                    val window = database.songDao().getSongsWindowAroundId(song.id, half = 25)
                    // The UNION ALL query returns songs in two sorted segments
                    // (before DESC, self, after ASC). Re-sort to title ASC so the
                    // window matches the order the rest of the app expects.
                    window.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
            }
            windowManager.setActivePlaylist(listToUse)

            val (windowSongs, windowIndex) = windowManager.buildPlaybackWindow(song, listToUse)
            windowManager.updateControllerMediaItems(controller, windowSongs, currentSong = song) {
                syncStateFromController(controller)
                controller.seekTo(windowIndex, 0)
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        val controller = controllerManager.getController() ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() { controllerManager.next() }
    fun previous() { controllerManager.previous() }

    fun seekTo(positionMs: Long) {
        controllerManager.seekTo(positionMs)
        _progressState.value = _progressState.value.copy(currentPosition = positionMs)
        lastSeekTime = System.currentTimeMillis()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500.milliseconds)
                controllerManager.getController()?.let {
                    if (System.currentTimeMillis() - lastSeekTime > 500) {
                        _progressState.value = _progressState.value.copy(
                            currentPosition = it.currentPosition.coerceAtLeast(0L),
                            duration = it.duration.coerceAtLeast(0L)
                        )
                    }
                }
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    override fun onCleared() {
        super.onCleared()
        controllerManager.release()
        stopProgressUpdate()
        windowManager.cancel()
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
