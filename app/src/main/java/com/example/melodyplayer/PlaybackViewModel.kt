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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val app = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState = _progressState.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    private var songIdToIndexMap = emptyMap<String, Int>()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var lastControllerSongs: List<Song> = emptyList()
    private var playerListener: Player.Listener? = null

    init {
        initializeController()
        
        // Load songs from DB for player state
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) { database.songDao().getAllSongs() }
            _allSongs.value = songs
            songIdToIndexMap = songs.indices.associateBy { songs[it].id }
            mediaController?.let { updateControllerMediaItems(it, songs) }
        }
    }

    private val dominantColorsCache = ConcurrentHashMap<String, Int>()
    private val _currentSongColor = MutableStateFlow<Int?>(null)
    val currentSongColor = _currentSongColor.asStateFlow()

    // Simplified color logic (can be further improved with a dedicated helper)
    private fun updateDominantColor(song: Song?) {
        if (song == null) {
            _currentSongColor.value = null
            return
        }
        // For now, keeping it simple or skipping if not critical for build fix
        _currentSongColor.value = null 
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
        updateDominantColor(song)
    }

    private fun updateControllerMediaItems(controller: MediaController, songs: List<Song>) {
        if (lastControllerSongs == songs && controller.mediaItemCount > 0) return
        lastControllerSongs = songs

        viewModelScope.launch(Dispatchers.Default) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.mediaUri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(if (song.artworkUri.isNotEmpty()) song.artworkUri.toUri() else null)
                            .build()
                    )
                    .build()
            }
            withContext(Dispatchers.Main) {
                controller.setMediaItems(mediaItems)
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                syncStateFromController(controller, songs)
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
        
        if (controller.mediaItemCount == 0 && _allSongs.value.isNotEmpty()) {
            updateControllerMediaItems(controller, _allSongs.value)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songs = _allSongs.value
                val song = songs.find { it.id == mediaItem?.mediaId }
                _uiState.value = _uiState.value.copy(currentSong = song)
                _progressState.value = _progressState.value.copy(duration = controller.duration.coerceAtLeast(0L))
                updateDominantColor(song)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _progressState.value = _progressState.value.copy(duration = controller.duration.coerceAtLeast(0L))
            }
        }
        controller.addListener(listener)
        playerListener = listener
        if (controller.isPlaying) startProgressUpdate()
        syncStateFromController(controller, _allSongs.value)
    }

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        val controller = mediaController ?: return
        val listToUse = playlistSongs.ifEmpty { _allSongs.value }
        
        if (lastControllerSongs != listToUse) {
            updateControllerMediaItems(controller, listToUse)
        }
        
        val index = listToUse.indexOfFirst { it.id == song.id }
        if (index != -1) {
            controller.seekTo(index, 0)
            controller.play()
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
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let {
                    _progressState.value = _progressState.value.copy(
                        currentPosition = it.currentPosition.coerceAtLeast(0L),
                        duration = it.duration.coerceAtLeast(0L)
                    )
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
