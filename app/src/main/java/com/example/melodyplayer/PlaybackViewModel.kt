package com.example.melodyplayer

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.example.melodyplayer.data.AppDatabase
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.ThumbnailRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

        // Sync media items from Room database reactively
        viewModelScope.launch {
            database.songDao().getAllSongsFlow().collect { songs ->
                _allSongs.value = songs
                songIdToIndexMap = songs.indices.associateBy { songs[it].id }
                _uiState.value = _uiState.value.copy(
                    totalSongsCount = songs.size
                )
                mediaController?.let { updateControllerMediaItems(it, songs) }
            }
        }
    }

    private fun preloadSongArtwork(song: Song) {
        if (song.artworkUri.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val hasWebp = ThumbnailRegistry.thumbnail256Set.containsKey(song.albumId)
            val model = if (hasWebp) {
                val cacheFile = File(app.cacheDir, "album_art/album_${song.albumId}_256.webp")
                Uri.fromFile(cacheFile).toString()
            } else {
                song.artworkUri
            }
            val request = ImageRequest.Builder(app)
                .data(model)
                .size(256)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            app.imageLoader.enqueue(request)
        }
    }

    private val dominantColorsCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val _currentSongColor = MutableStateFlow<Int?>(null)
    val currentSongColor = _currentSongColor.asStateFlow()

    private fun updateDominantColor(song: Song?) {
        if (song == null) {
            _currentSongColor.value = null
            return
        }
        val cached = dominantColorsCache[song.id]
        if (cached != null) {
            _currentSongColor.value = cached
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sizeSuffix = "256"
                val hasWebp = ThumbnailRegistry.thumbnail256Set.containsKey(song.albumId)
                val bitmap = if (hasWebp) {
                    val cacheFile = File(app.cacheDir, "album_art/album_${song.albumId}_$sizeSuffix.webp")
                    BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else if (song.artworkUri.isNotEmpty()) {
                    app.contentResolver.openInputStream(song.artworkUri.toUri())?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } else {
                    null
                }

                if (bitmap != null) {
                    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                    val color = palette.getDarkMutedColor(
                        palette.getDarkVibrantColor(
                            palette.getDominantColor(0xFF1E1B4B.toInt())
                        )
                    )
                    dominantColorsCache[song.id] = color
                    _currentSongColor.value = color
                    bitmap.recycle()
                } else {
                    _currentSongColor.value = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract dominant color for song ${song.id}", e)
                _currentSongColor.value = null
            }
        }
    }

    private fun syncStateFromController(controller: MediaController, songs: List<Song> = _allSongs.value) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val currentSong = if (currentMediaId != null) {
            val index = songIdToIndexMap[currentMediaId]
            if (index != null && index != -1 && index < songs.size) songs[index] else songs.firstOrNull()
        } else {
            songs.firstOrNull()
        }

        _uiState.value = _uiState.value.copy(
            currentSong = currentSong,
            isPlaying = controller.isPlaying
        )
        updateDominantColor(currentSong)

        val newPos = controller.currentPosition.coerceAtLeast(0L)
        val newDur = controller.duration.coerceAtLeast(0L)
        val currentProgress = _progressState.value
        if (currentProgress.currentPosition != newPos || currentProgress.duration != newDur) {
            _progressState.value = ProgressState(
                currentPosition = newPos,
                duration = newDur
            )
        }
    }

    private fun updateControllerMediaItems(controller: MediaController, songs: List<Song>) {
        val isSameList = lastControllerSongs === songs || (
            lastControllerSongs.size == songs.size &&
            lastControllerSongs.indices.all { lastControllerSongs[it].id == songs[it].id && lastControllerSongs[it].dateModified == songs[it].dateModified }
        )
        if (isSameList) {
            syncStateFromController(controller, songs)
            return
        }
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
                val isIdleOrEmpty = controller.playbackState == Player.STATE_IDLE || controller.mediaItemCount == 0
                controller.setMediaItems(mediaItems, /* resetPosition = */ isIdleOrEmpty)
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                syncStateFromController(controller, songs)
            }
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            app,
            ComponentName(app, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
            } catch (e: java.util.concurrent.ExecutionException) {
                Log.e(TAG, "Failed to initialize MediaController", e.cause ?: e)
            } catch (e: java.util.concurrent.CancellationException) {
                Log.i(TAG, "MediaController initialization was cancelled", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during MediaController initialization", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController(controller: MediaController) {
        playerListener?.let { controller.removeListener(it) }

        val songsToUse = _allSongs.value
        if (controller.mediaItemCount == 0) {
            updateControllerMediaItems(controller, songsToUse)
        }

        syncStateFromController(controller, songsToUse)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songs = _allSongs.value
                val mediaId = mediaItem?.mediaId
                val songIndex = if (mediaId != null) songIdToIndexMap[mediaId] ?: -1 else -1
                val song = if (songIndex != -1) songs[songIndex] else null
                val currentSong = song ?: _uiState.value.currentSong
                _uiState.value = _uiState.value.copy(
                    currentSong = currentSong
                )
                updateDominantColor(currentSong)

                if (songIndex != -1) {
                    for (i in 1..3) {
                        if (songIndex + i < songs.size) {
                            preloadSongArtwork(songs[songIndex + i])
                        }
                    }
                }

                val newDur = controller.duration.coerceAtLeast(0L)
                val currentProgress = _progressState.value
                if (currentProgress.duration != newDur) {
                    _progressState.value = currentProgress.copy(duration = newDur)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val newDur = controller.duration.coerceAtLeast(0L)
                val currentProgress = _progressState.value
                if (currentProgress.duration != newDur) {
                    _progressState.value = currentProgress.copy(duration = newDur)
                }
            }
        }
        controller.addListener(listener)
        playerListener = listener

        if (controller.isPlaying) startProgressUpdate()
    }

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        val controller = mediaController ?: return
        val listToUse = playlistSongs.ifEmpty { _allSongs.value }
        updateControllerMediaItems(controller, listToUse)
        
        val currentMapping = listToUse.indices.associateBy { listToUse[it].id }
        val index = currentMapping[song.id] ?: -1
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
        val current = _progressState.value
        if (current.currentPosition != positionMs) {
            _progressState.value = current.copy(currentPosition = positionMs)
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    val newPos = controller.currentPosition.coerceAtLeast(0L)
                    val newDur = controller.duration.coerceAtLeast(0L)
                    val current = _progressState.value
                    if (current.currentPosition != newPos || current.duration != newDur) {
                        _progressState.value = ProgressState(
                            currentPosition = newPos,
                            duration = newDur
                        )
                    }
                }
                delay(250.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        playerListener?.let { listener ->
            mediaController?.removeListener(listener)
            playerListener = null
        }
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