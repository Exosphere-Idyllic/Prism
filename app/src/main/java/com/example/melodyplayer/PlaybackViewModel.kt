package com.example.melodyplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
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
import java.util.concurrent.atomic.AtomicBoolean
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)

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

    // Paginación y Lazy Loading
    private var currentOffset = 0
    private val pageSize = 30
    private var isLastPage = false
    private val isLoadingPage = AtomicBoolean(false)
    private var lastControllerSongs: List<Song> = emptyList()

    private var contentObserver: ContentObserver? = null
    private var playerListener: Player.Listener? = null

    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        initializeController()
        checkAndLoadSongs()

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            contentObserverEvents
                .debounce(500)
                .collect {
                    loadLocalSongs()
                }
        }
    }

    fun checkAndLoadSongs() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            registerContentObserver()
            loadLocalSongs()
        } else {
            _allSongs.value = emptyList()
            _uiState.value = _uiState.value.copy(playlist = emptyList())
        }
    }

    private fun registerContentObserver() {
        if (contentObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                viewModelScope.launch {
                    contentObserverEvents.emit(Unit)
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentObserver = observer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register content observer", e)
        }
    }

    private var loadSongsJob: Job? = null

    fun loadLocalSongs() {
        loadSongsJob?.cancel()
        loadSongsJob = viewModelScope.launch {
            // Set loading state if the current playlist is empty
            if (_uiState.value.playlist.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // 1. Cargar primera página inmediatamente desde Room (síncrona/secuencial en la corrutina)
            loadFirstPageSuspended()

            // Turn off loading once cache is loaded
            _uiState.value = _uiState.value.copy(isLoading = false)

            // 2. Realizar consulta en background a MediaStore en Dispatchers.IO
            val finalSongs = withContext(Dispatchers.IO) {
                val list = mutableListOf<Song>()
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

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

                            val mediaUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString()

                            val artworkUri = if (albumId > 0) {
                                ContentUris.withAppendedId(
                                    albumArtBaseUri,
                                    albumId
                                ).toString()
                            } else {
                                ""
                            }

                            list.add(
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
                    Log.e(TAG, "Failed to query MediaStore", e)
                }

                if (list.isNotEmpty()) {
                    database.songDao().updateSongsTransaction(list)
                }
                list
            }

            // 3. Recargar primera página con los nuevos datos
            loadFirstPageSuspended()

            // 4. Actualizar todos los media items del controller sin consulta redundante
            _allSongs.value = finalSongs
            songIdToIndexMap = finalSongs.indices.associateBy { finalSongs[it].id }
            mediaController?.let { updateControllerMediaItems(it, finalSongs) }
            preDecodeImages(finalSongs.take(20))
        }
    }

    private suspend fun loadFirstPageSuspended() {
        if (isLoadingPage.getAndSet(true)) return
        try {
            currentOffset = 0
            isLastPage = false
            val (songs, count) = withContext(Dispatchers.IO) {
                val query = _searchQuery.value
                if (query.isEmpty()) {
                    val list = database.songDao().getSongsPaginated(pageSize, currentOffset)
                    val total = database.songDao().getSongsCount()
                    Pair(list, total)
                } else {
                    val list = database.songDao().searchSongsPaginated("%$query%", pageSize, currentOffset)
                    val total = database.songDao().searchSongsCount("%$query%")
                    Pair(list, total)
                }
            }
            currentOffset += songs.size
            if (songs.size < pageSize) {
                isLastPage = true
            }
            _uiState.value = _uiState.value.copy(
                playlist = songs,
                totalSongsCount = count
            )
        } finally {
            isLoadingPage.set(false)
        }
    }

    fun loadNextPage() {
        if (isLastPage || isLoadingPage.getAndSet(true)) return
        viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    val query = _searchQuery.value
                    if (query.isEmpty()) {
                        database.songDao().getSongsPaginated(pageSize, currentOffset)
                    } else {
                        database.songDao().searchSongsPaginated("%$query%", pageSize, currentOffset)
                    }
                }
                if (songs.isNotEmpty()) {
                    val currentList = _uiState.value.playlist
                    val newList = currentList + songs
                    currentOffset += songs.size
                    _uiState.value = _uiState.value.copy(
                        playlist = newList
                    )
                }
                if (songs.size < pageSize) {
                    isLastPage = true
                }
            } finally {
                isLoadingPage.set(false)
            }
        }
    }

    private fun preDecodeImages(songs: List<Song>) {
        val imageLoader = context.imageLoader
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                if (song.artworkUri.isNotEmpty()) {
                    val request = ImageRequest.Builder(context)
                        .data(song.artworkUri)
                        .size(120)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    private fun updateControllerMediaItems(controller: MediaController, songs: List<Song>) {
        if (lastControllerSongs == songs) {
            syncUiWithController(controller, songs)
            return
        }
        lastControllerSongs = songs

        viewModelScope.launch(Dispatchers.Default) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(Uri.parse(song.mediaUri))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(if (song.artworkUri.isNotEmpty()) Uri.parse(song.artworkUri) else null)
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
                syncUiWithController(controller, songs)
            }
        }
    }

    private fun syncUiWithController(controller: MediaController, songs: List<Song>) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val currentSong = if (currentMediaId != null) {
            val index = songIdToIndexMap[currentMediaId]
            if (index != null && index != -1 && index < songs.size) songs[index] else songs.firstOrNull()
        } else {
            songs.firstOrNull()
        }

        _uiState.value = _uiState.value.copy(
            playlist = _uiState.value.playlist.ifEmpty { songs.take(pageSize) },
            currentSong = currentSong
        )
        _progressState.value = _progressState.value.copy(
            duration = controller.duration.coerceAtLeast(0L)
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            loadFirstPageSuspended()
        }
    }

    private fun initializeController() {
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

        updateStateFromController(controller)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val songs = _allSongs.value
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
        }
        controller.addListener(listener)
        playerListener = listener

        if (controller.isPlaying) startProgressUpdate()
    }

    private fun updateStateFromController(controller: MediaController) {
        val currentMediaItem = controller.currentMediaItem
        val songs = _allSongs.value
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
        playerListener?.let { listener ->
            mediaController?.removeListener(listener)
            playerListener = null
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        stopProgressUpdate()
        
        contentObserver?.let { observer ->
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister content observer", e)
            }
            contentObserver = null
        }
    }
}

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val playlist: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val totalSongsCount: Int = 0
)

data class ProgressState(
    val currentPosition: Long = 0L,
    val duration: Long = 0L
)