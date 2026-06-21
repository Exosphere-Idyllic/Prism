package com.example.melodyplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.example.melodyplayer.data.AppDatabase
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.ThumbnailRegistry
import kotlinx.coroutines.flow.map
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.milliseconds

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val database = AppDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState = _progressState.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    private var songIdToIndexMap = emptyMap<String, Int>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val songsFlow: Flow<PagingData<Song>> = _searchQuery
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(
                    pageSize = 30,
                    enablePlaceholders = false,
                    prefetchDistance = 10
                )
            ) {
                if (query.isEmpty()) {
                    database.songDao().getAllSongsPaging()
                } else {
                    database.songDao().searchSongsPaging("%$query%")
                }
            }.flow
        }
        .cachedIn(viewModelScope)

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

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

        // Scan existing thumbnails at startup on a background thread
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val cacheDir = File(app.cacheDir, "album_art")
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                files?.forEach { file ->
                    val name = file.name
                    if (name.endsWith(".webp")) {
                        val parts = name.removeSuffix(".webp").split("_")
                        if (parts.size == 2) {
                            val songId = parts[0]
                            val size = parts[1]
                            withContext(Dispatchers.Main) {
                                if (size == "128") {
                                    ThumbnailRegistry.add128(songId)
                                } else if (size == "256") {
                                    ThumbnailRegistry.add256(songId)
                                }
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            contentObserverEvents
                .debounce(500.milliseconds)
                .collect {
                    loadLocalSongs()
                }
        }
    }

    fun checkAndLoadSongs() {
        val app = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED) {
            registerContentObserver()
            loadLocalSongs()
        } else {
            _allSongs.value = emptyList()
            _uiState.value = _uiState.value.copy(playlist = emptyList(), totalSongsCount = 0)
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
            getApplication<Application>().contentResolver.registerContentObserver(
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
            if (_uiState.value.totalSongsCount == 0) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // 1. Cargar datos cacheados inmediatamente desde Room para una respuesta visual instantánea
            val cachedSongs = withContext(Dispatchers.IO) {
                database.songDao().getAllSongs()
            }
            _allSongs.value = cachedSongs
            songIdToIndexMap = cachedSongs.indices.associateBy { cachedSongs[it].id }
            _uiState.value = _uiState.value.copy(
                totalSongsCount = cachedSongs.size,
                isLoading = cachedSongs.isEmpty()
            )
            mediaController?.let { updateControllerMediaItems(it, cachedSongs) }

            // 2. Realizar escaneo incremental en segundo plano en Dispatchers.IO
            val (finalSongs, songsUpserted) = withContext(Dispatchers.IO) {
                val list = mutableListOf<Song>()
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val albumArtBaseUri = "content://media/external/audio/albumart".toUri()

                try {
                    getApplication<Application>().contentResolver.query(
                        uri,
                        projection,
                        selection,
                        null,
                        "${MediaStore.Audio.Media.TITLE} ASC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val title = cursor.getString(titleColumn) ?: "Unknown Title"
                            val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                            val album = cursor.getString(albumColumn) ?: "Unknown Album"
                            val albumId = cursor.getLong(albumIdColumn)
                            val duration = cursor.getLong(durationColumn)
                            val dateModified = cursor.getLong(dateModifiedColumn)

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
                                    album = album,
                                    mediaUri = mediaUri,
                                    artworkUri = artworkUri,
                                    duration = duration,
                                    dateModified = dateModified
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query MediaStore", e)
                }

                // Sincronización Incremental
                val roomSongs = database.songDao().getSongsSyncInfo().associateBy { it.id }
                val mediaStoreSongsMap = list.associateBy { it.id }

                val toUpsert = mutableListOf<Song>()
                val toDelete = mutableListOf<String>()

                for (song in list) {
                    val dbSong = roomSongs[song.id]
                    if (dbSong == null || song.dateModified > dbSong.dateModified) {
                        toUpsert.add(song)
                    }
                }

                for (dbId in roomSongs.keys) {
                    if (!mediaStoreSongsMap.containsKey(dbId)) {
                        toDelete.add(dbId)
                    }
                }

                if (toUpsert.isNotEmpty()) {
                    toUpsert.chunked(500).forEach { chunk ->
                        database.songDao().insertAll(chunk)
                    }
                }
                if (toDelete.isNotEmpty()) {
                    toDelete.chunked(500).forEach { chunk ->
                        database.songDao().deleteSongsByIds(chunk)
                    }
                    val cacheDir = getApplication<Application>().cacheDir
                    toDelete.forEach { id ->
                        File(cacheDir, "album_art/${id}_128.webp").delete()
                        File(cacheDir, "album_art/${id}_256.webp").delete()
                    }
                }

                Pair(list, toUpsert)
            }

            // 3. Actualizar la UI y el reproductor con la lista final escaneada
            _allSongs.value = finalSongs
            songIdToIndexMap = finalSongs.indices.associateBy { finalSongs[it].id }
            _uiState.value = _uiState.value.copy(
                totalSongsCount = finalSongs.size,
                isLoading = false
            )
            mediaController?.let { updateControllerMediaItems(it, finalSongs) }

            // Generar portadas WebP para las canciones insertadas/modificadas
            if (songsUpserted.isNotEmpty()) {
                val app = getApplication<Application>()
                viewModelScope.launch(Dispatchers.IO) {
                    songsUpserted.forEach { song ->
                        generateWebpThumbnails(app, song)
                    }
                }
            }

            // Escaneo pasivo en background para regenerar portadas WebP que falten (ej. si se borró la caché)
            val app = getApplication<Application>()
            viewModelScope.launch(Dispatchers.IO) {
                val allWithArtwork = database.songDao().getAllSongsWithArtwork()
                allWithArtwork.forEach { song ->
                    generateWebpThumbnails(app, song)
                }
            }
        }
    }

    private suspend fun generateWebpThumbnails(context: Context, song: Song) {
        if (song.artworkUri.isEmpty()) return
        val cacheDir = File(context.cacheDir, "album_art")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file128 = File(cacheDir, "${song.id}_128.webp")
        val file256 = File(cacheDir, "${song.id}_256.webp")

        if (file128.exists() && file256.exists()) return

        try {
            val artUri = song.artworkUri.toUri()
            context.contentResolver.openInputStream(artUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }

                    // Crop to center square to avoid stretching rectangular album art
                    val squareBitmap = if (bitmap.width == bitmap.height) {
                        bitmap
                    } else {
                        val minSize = minOf(bitmap.width, bitmap.height)
                        val x = (bitmap.width - minSize) / 2
                        val y = (bitmap.height - minSize) / 2
                        Bitmap.createBitmap(bitmap, x, y, minSize, minSize)
                    }

                    if (!file128.exists()) {
                        val bitmap128 = squareBitmap.scale(128, 128, true)
                        FileOutputStream(file128).use { out ->
                            bitmap128.compress(webpFormat, 80, out)
                        }
                        bitmap128.recycle()
                    }

                    if (!file256.exists()) {
                        val bitmap256 = squareBitmap.scale(256, 256, true)
                        FileOutputStream(file256).use { out ->
                            bitmap256.compress(webpFormat, 80, out)
                        }
                        bitmap256.recycle()
                    }

                    if (squareBitmap != bitmap) {
                        squareBitmap.recycle()
                    }
                    bitmap.recycle()

                    // Register new thumbnails in-memory
                    withContext(Dispatchers.Main) {
                        if (file128.exists()) ThumbnailRegistry.add128(song.id)
                        if (file256.exists()) ThumbnailRegistry.add256(song.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate WebP thumbnail for song ${song.id}: ${e.message}")
        }
    }

    private fun preloadSongArtwork(song: Song) {
        if (song.artworkUri.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val hasWebp = ThumbnailRegistry.thumbnail256Set.containsKey(song.id)
            val model = if (hasWebp) {
                val cacheFile = File(context.cacheDir, "album_art/${song.id}_256.webp")
                Uri.fromFile(cacheFile).toString()
            } else {
                song.artworkUri
            }
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(256)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            context.imageLoader.enqueue(request)
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
                val context = getApplication<Application>()
                val sizeSuffix = "256"
                val hasWebp = ThumbnailRegistry.thumbnail256Set.containsKey(song.id)
                val bitmap = if (hasWebp) {
                    val cacheFile = File(context.cacheDir, "album_art/${song.id}_$sizeSuffix.webp")
                    BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else if (song.artworkUri.isNotEmpty()) {
                    context.contentResolver.openInputStream(song.artworkUri.toUri())?.use {
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
            playlist = songs.take(30),
            currentSong = currentSong
        )
        updateDominantColor(currentSong)
        _progressState.value = _progressState.value.copy(
            duration = controller.duration.coerceAtLeast(0L)
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun initializeController() {
        val context = getApplication<Application>()
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
                val songIndex = songs.indexOfFirst { it.id == mediaItem?.mediaId }
                val song = if (songIndex != -1) songs[songIndex] else null
                val currentSong = song ?: _uiState.value.currentSong
                _uiState.value = _uiState.value.copy(
                    currentSong = currentSong
                )
                updateDominantColor(currentSong)

                // Precargar la portada de las próximas 3 canciones
                if (songIndex != -1) {
                    for (i in 1..3) {
                        if (songIndex + i < songs.size) {
                            preloadSongArtwork(songs[songIndex + i])
                        }
                    }
                }

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
        val currentSong = song ?: songs.firstOrNull()
        _uiState.value = _uiState.value.copy(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            playlist = _uiState.value.playlist.ifEmpty { songs.take(30) }
        )
        updateDominantColor(currentSong)
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

        contentObserver?.let { observer ->
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(observer)
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