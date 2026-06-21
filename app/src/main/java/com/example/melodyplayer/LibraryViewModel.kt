package com.example.melodyplayer

import android.Manifest
import android.app.Application
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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.melodyplayer.data.Album
import com.example.melodyplayer.data.Artist
import com.example.melodyplayer.data.AppDatabase
import com.example.melodyplayer.data.Playlist
import com.example.melodyplayer.data.PlaylistSong
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.ThumbnailRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.milliseconds

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val app = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _totalSongsCount = MutableStateFlow(0)
    val totalSongsCount = _totalSongsCount.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumsFlow: Flow<List<Album>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                database.albumDao().getAllAlbums()
            } else {
                database.albumDao().searchAlbums("%$query%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsFlow: Flow<List<Artist>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                database.artistDao().getAllArtists()
            } else {
                database.artistDao().searchArtists("%$query%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsFlow: StateFlow<List<Playlist>> = database.playlistDao()
        .getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongIds: StateFlow<Set<String>> = database.playlistDao()
        .getPlaylistSongIdsFlow("Favoritas")
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var contentObserver: ContentObserver? = null
    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        checkAndLoadSongs()

        // Scan existing WebP thumbnails at startup by albumId
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(app.cacheDir, "album_art")
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                files?.forEach { file ->
                    val name = file.name
                    if (name.endsWith(".webp")) {
                        val parts = name.removeSuffix(".webp").split("_")
                        if (parts.size == 3 && parts[0] == "album") {
                            val albumId = parts[1].toLongOrNull()
                            val size = parts[2]
                            if (albumId != null) {
                                if (size == "128") {
                                    ThumbnailRegistry.add128(albumId)
                                } else if (size == "256") {
                                    ThumbnailRegistry.add256(albumId)
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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED) {
            registerContentObserver()
            loadLocalSongs()
        } else {
            _totalSongsCount.value = 0
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
            app.contentResolver.registerContentObserver(
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
            _isLoading.value = true

            // Load from Room first for instant UI response
            val cachedSongs = withContext(Dispatchers.IO) {
                database.songDao().getAllSongs()
            }
            _totalSongsCount.value = cachedSongs.size
            if (cachedSongs.isNotEmpty()) {
                _isLoading.value = false
            }

            // Perform scan in background
            val (scannedSongs, songsUpserted) = withContext(Dispatchers.IO) {
                val list = mutableListOf<Song>()
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.TRACK
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val albumArtBaseUri = "content://media/external/audio/albumart".toUri()

                try {
                    app.contentResolver.query(
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
                        val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val title = cursor.getString(titleColumn) ?: "Unknown Title"
                            val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                            val album = cursor.getString(albumColumn) ?: "Unknown Album"
                            val albumId = cursor.getLong(albumIdColumn)
                            val duration = cursor.getLong(durationColumn)
                            val dateModified = cursor.getLong(dateModifiedColumn)
                            val track = cursor.getInt(trackColumn)

                            val mediaUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString()

                            val artworkUri = if (albumId > 0) {
                                ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                            } else {
                                ""
                            }

                            list.add(
                                Song(
                                    id = id.toString(),
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    albumId = albumId,
                                    mediaUri = mediaUri,
                                    artworkUri = artworkUri,
                                    duration = duration,
                                    dateModified = dateModified,
                                    track = track
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query MediaStore", e)
                }

                // Sync Songs Table
                val roomSongs = database.songDao().getSongsSyncInfo().associateBy { it.id }
                val toUpsert = mutableListOf<Song>()
                val toDelete = mutableListOf<String>()

                for (song in list) {
                    val dbSong = roomSongs[song.id]
                    if (dbSong == null || song.dateModified > dbSong.dateModified) {
                        toUpsert.add(song)
                    }
                }

                for (dbId in roomSongs.keys) {
                    if (!list.any { it.id == dbId }) {
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
                }

                // Generate and sync Albums
                val albumsMap = list.groupBy { it.albumId }
                val albumsToInsert = albumsMap.map { (albumId, songsInAlbum) ->
                    val firstSong = songsInAlbum.first()
                    Album(
                        id = albumId,
                        albumName = firstSong.album,
                        artist = firstSong.artist,
                        coverPath = firstSong.artworkUri,
                        songCount = songsInAlbum.size
                    )
                }
                database.albumDao().deleteAll()
                database.albumDao().insertAll(albumsToInsert)

                // Generate and sync Artists
                val artistsMap = list.groupBy { it.artist }
                var artistIdCounter = 1L
                val artistsToInsert = artistsMap.map { (artistName, songsInArtist) ->
                    val uniqueAlbumsCount = songsInArtist.map { it.albumId }.distinct().size
                    Artist(
                        id = artistIdCounter++,
                        name = artistName,
                        songCount = songsInArtist.size,
                        albumCount = uniqueAlbumsCount
                    )
                }
                database.artistDao().deleteAll()
                database.artistDao().insertAll(artistsToInsert)

                Pair(list, toUpsert)
            }

            _totalSongsCount.value = scannedSongs.size
            _isLoading.value = false

            // Ensure "Favoritas" playlist exists
            getOrCreateFavoritesPlaylist()

            // Generate WebP thumbnails by albumId asynchronously
            viewModelScope.launch(Dispatchers.IO) {
                // Group newly upserted songs by albumId to only trigger once per album
                val uniqueAlbumsToGen = songsUpserted.filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
                    .associateBy { it.albumId }

                uniqueAlbumsToGen.forEach { (albumId, song) ->
                    generateWebpThumbnails(app, albumId, song.artworkUri)
                }

                // Passive background scan to regenerate missing album arts in cache
                val allSongs = database.songDao().getAllSongs()
                val albumsWithArtwork = allSongs.filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
                    .associateBy { it.albumId }
                albumsWithArtwork.forEach { (albumId, song) ->
                    generateWebpThumbnails(app, albumId, song.artworkUri)
                }
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private suspend fun generateWebpThumbnails(context: Context, albumId: Long, artworkUri: String) {
        if (artworkUri.isEmpty() || albumId <= 0) return
        val cacheDir = File(context.cacheDir, "album_art")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file128 = File(cacheDir, "album_${albumId}_128.webp")
        val file256 = File(cacheDir, "album_${albumId}_256.webp")

        if (file128.exists() && file256.exists()) {
            ThumbnailRegistry.add128(albumId)
            ThumbnailRegistry.add256(albumId)
            return
        }

        try {
            val artUri = artworkUri.toUri()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(artUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val targetSize = 256
            val sampleSize = calculateInSampleSize(options, targetSize, targetSize)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            context.contentResolver.openInputStream(artUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                if (bitmap != null) {
                    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }

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

                    if (file128.exists()) ThumbnailRegistry.add128(albumId)
                    if (file256.exists()) ThumbnailRegistry.add256(albumId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate WebP thumbnail for album $albumId: ${e.message}")
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Playlists & Favorites management
    suspend fun getOrCreateFavoritesPlaylist(): Long {
        return withContext(Dispatchers.IO) {
            val list = database.playlistDao().getPlaylistByName("Favoritas")
            if (list != null) {
                list.id
            } else {
                database.playlistDao().insert(
                    Playlist(
                        name = "Favoritas",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val favPlaylistId = getOrCreateFavoritesPlaylist()
            val isFavorite = favoriteSongIds.value.contains(song.id)
            if (isFavorite) {
                database.playlistDao().deletePlaylistSong(favPlaylistId, song.id)
            } else {
                val count = database.playlistDao().getPlaylistSongCountSync(favPlaylistId)
                database.playlistDao().insertPlaylistSong(
                    PlaylistSong(
                        playlistId = favPlaylistId,
                        songId = song.id,
                        position = count
                    )
                )
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.trim().isNotEmpty()) {
                database.playlistDao().insert(
                    Playlist(
                        name = name.trim(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.playlistDao().deletePlaylist(playlistId)
            database.playlistDao().clearPlaylistSongs(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = database.playlistDao().getPlaylistSongCountSync(playlistId)
            database.playlistDao().insertPlaylistSong(
                PlaylistSong(
                    playlistId = playlistId,
                    songId = songId,
                    position = count
                )
            )
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.playlistDao().deletePlaylistSong(playlistId, songId)
        }
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return database.playlistDao().getSongsForPlaylist(playlistId)
    }

    fun getSongsByAlbum(albumId: Long): Flow<List<Song>> {
        return database.songDao().getSongsByAlbum(albumId)
    }

    fun getSongsByArtist(artist: String): Flow<List<Song>> {
        return database.songDao().getSongsByArtist(artist)
    }

    override fun onCleared() {
        super.onCleared()
        contentObserver?.let { observer ->
            try {
                app.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister content observer", e)
            }
            contentObserver = null
        }
    }
}
