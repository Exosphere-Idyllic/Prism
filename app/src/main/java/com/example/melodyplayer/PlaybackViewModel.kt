package com.example.melodyplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.melodyplayer.data.MockPlaylist
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState = _progressState.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    private var songIdToIndexMap = emptyMap<String, Int>()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    init {
        initializeController()
        checkAndLoadSongs()
    }

    fun checkAndLoadSongs() {
        val context = getApplication<Application>().applicationContext
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            loadLocalSongs()
        } else {
            // Default to mock playlist before permissions are granted so UI shows something
            _allSongs.value = MockPlaylist.songs
            filterSongs()
        }
    }

    fun loadLocalSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val songList = mutableListOf<Song>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown Title"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val albumId = cursor.getLong(albumIdColumn)

                        val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()

                        songList.add(
                            Song(
                                id = id.toString(),
                                title = title,
                                artist = artist,
                                mediaUri = mediaUri,
                                artworkUri = artworkUri
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // If local storage is empty, fallback to mock so application is testable
            val finalSongs = if (songList.isNotEmpty()) songList else MockPlaylist.songs
            val newIdMap = finalSongs.withIndex().associate { it.value.id to it.index }
            
            withContext(Dispatchers.Main) {
                _allSongs.value = finalSongs
                songIdToIndexMap = newIdMap
                filterSongs()
                mediaController?.let { updateControllerMediaItems(it, finalSongs) }
                
                // Pre-calentamiento de carátulas (las primeras 15)
                prewarmImageCache(finalSongs.take(15))
            }
        }
    }

    private fun prewarmImageCache(songs: List<Song>) {
        val context = getApplication<Application>().applicationContext
        songs.forEach { song ->
            if (song.artworkUri.isNotEmpty()) {
                Glide.with(context)
                    .load(song.artworkUri)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(200) // Debe coincidir con el override de la UI
                    .preload()
            }
        }
    }

    private fun updateControllerMediaItems(controller: MediaController, songs: List<Song>) {
        viewModelScope.launch(Dispatchers.Default) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(Uri.parse(song.mediaUri))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(Uri.parse(song.artworkUri))
                            .build()
                    )
                    .build()
            }
            withContext(Dispatchers.Main) {
                controller.setMediaItems(mediaItems)
                controller.prepare()

                val currentMediaId = controller.currentMediaItem?.mediaId
                val currentSong = if (currentMediaId != null) {
                    val index = songIdToIndexMap[currentMediaId]
                    if (index != null && index != -1) songs[index] else songs.firstOrNull()
                } else {
                    songs.firstOrNull()
                }
                
                _uiState.value = _uiState.value.copy(
                    playlist = if (_uiState.value.playlist.isNotEmpty()) _uiState.value.playlist else songs,
                    currentSong = currentSong
                )
                _progressState.value = _progressState.value.copy(
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterSongs()
    }

    private fun filterSongs() {
        viewModelScope.launch {
            val query = _searchQuery.value
            val allSongs = _allSongs.value
            val filtered = withContext(Dispatchers.Default) {
                if (query.isEmpty()) {
                    allSongs
                } else {
                    allSongs.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(playlist = filtered)
        }
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
        val songsToUse = if (_allSongs.value.isNotEmpty()) _allSongs.value else MockPlaylist.songs
        if (controller.mediaItemCount == 0) {
            updateControllerMediaItems(controller, songsToUse)
        }

        updateStateFromController(controller)

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
                val songs = if (_allSongs.value.isNotEmpty()) _allSongs.value else MockPlaylist.songs
                val song = songs.find { it.id == mediaItem?.mediaId }
                _uiState.value = _uiState.value.copy(
                    currentSong = song ?: _uiState.value.currentSong
                )
                _progressState.value = _progressState.value.copy(
                    duration = controller.duration.coerceAtLeast(0L)
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _progressState.value = _progressState.value.copy(
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
        val songs = if (_allSongs.value.isNotEmpty()) _allSongs.value else MockPlaylist.songs
        val song = songs.find { it.id == currentMediaItem?.mediaId }
        _uiState.value = PlaybackUiState(
            currentSong = song ?: songs.firstOrNull(),
            isPlaying = controller.isPlaying,
            playlist = if (_uiState.value.playlist.isNotEmpty()) _uiState.value.playlist else songs
        )
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L)
        )
    }

    fun playSong(song: Song) {
        val controller = mediaController ?: return
        val index = songIdToIndexMap[song.id] ?: -1
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
        _progressState.value = _progressState.value.copy(currentPosition = positionMs)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    _progressState.value = ProgressState(
                        currentPosition = controller.currentPosition.coerceAtLeast(0L),
                        duration = controller.duration.coerceAtLeast(0L)
                    )
                }
                delay(250)
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
    val playlist: List<Song> = emptyList()
)

data class ProgressState(
    val currentPosition: Long = 0L,
    val duration: Long = 0L
)
