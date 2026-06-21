package com.example.melodyplayer.data

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.milliseconds

class MusicRepository(private val app: Application, private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "MusicRepository"
    }

    private val database = AppDatabase.getDatabase(app)
    private val songDao = database.songDao()
    private val albumDao = database.albumDao()
    private val artistDao = database.artistDao()
    private val playlistDao = database.playlistDao()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _totalSongsCount = MutableStateFlow(0)
    val totalSongsCount = _totalSongsCount.asStateFlow()

    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var contentObserver: ContentObserver? = null
    private var scanJob: Job? = null

    init {
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            contentObserverEvents
                .debounce(1000.milliseconds)
                .collect {
                    performScan()
                }
        }
    }

    fun startObserving() {
        if (contentObserver == null) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    scope.launch { contentObserverEvents.emit(Unit) }
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
        // Always trigger initial scan on calling startObserving
        scope.launch { performScan() }
    }

    fun stopObserving() {
        contentObserver?.let {
            app.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    private fun performScan() {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            _isLoading.value = true
            initThumbnailRegistry()
            
            // 1. Get Room State
            val roomSongsInfo = songDao.getSongsSyncInfo().associateBy { it.id }
            
            // 2. Query MediaStore
            val mediaStoreSongs = queryMediaStore()
            val mediaStoreIds = mediaStoreSongs.map { it.id }.toSet()

            // 3. Identify Changes (Incremental)
            val toUpsert = mutableListOf<Song>()
            val toDelete = mutableListOf<String>()

            for (song in mediaStoreSongs) {
                val dbSongInfo = roomSongsInfo[song.id]
                if (dbSongInfo == null || song.dateModified > dbSongInfo.dateModified) {
                    toUpsert.add(song)
                }
            }

            for (dbId in roomSongsInfo.keys) {
                if (!mediaStoreIds.contains(dbId)) {
                    toDelete.add(dbId)
                }
            }

            // 4. Apply Changes to Songs
            if (toUpsert.isNotEmpty()) {
                toUpsert.chunked(200).forEach { chunk -> songDao.insertAll(chunk) }
            }
            if (toDelete.isNotEmpty()) {
                toDelete.chunked(200).forEach { chunk -> songDao.deleteSongsByIds(chunk) }
            }

            // 5. Update Albums and Artists (Re-generate from current Song table for consistency)
            if (toUpsert.isNotEmpty() || toDelete.isNotEmpty()) {
                updateAlbumsAndArtists()
            }

            // 6. Finalize
            val allSongs = songDao.getAllSongs()
            val finalCount = allSongs.size
            _totalSongsCount.value = finalCount
            _isLoading.value = false

            // 7. Background: Thumbnails
            generateMissingThumbnails(allSongs)
        }
    }

    private fun queryMediaStore(): List<Song> {
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
            app.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else ""

                    list.add(Song(
                        id = id.toString(),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        album = cursor.getString(albumCol) ?: "Unknown",
                        albumId = albumId,
                        mediaUri = ContentUris.withAppendedId(uri, id).toString(),
                        artworkUri = artworkUri,
                        duration = cursor.getLong(durationCol),
                        dateModified = cursor.getLong(dateModCol),
                        track = cursor.getInt(trackCol)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }
        return list
    }

    private suspend fun updateAlbumsAndArtists() {
        val allSongs = songDao.getAllSongs()
        
        // Albums
        val albums = allSongs.groupBy { it.albumId }.map { (albumId, songs) ->
            val sample = songs.first()
            Album(
                id = albumId,
                albumName = sample.album,
                artist = sample.artist,
                coverPath = sample.artworkUri,
                songCount = songs.size
            )
        }
        albumDao.deleteAll()
        albumDao.insertAll(albums)

        // Artists
        var artistId = 1L
        val artists = allSongs.groupBy { it.artist }.map { (name, songs) ->
            Artist(
                id = artistId++,
                name = name,
                songCount = songs.size,
                albumCount = songs.map { it.albumId }.distinct().size
            )
        }
        artistDao.deleteAll()
        artistDao.insertAll(artists)
    }

    private fun initThumbnailRegistry() {
        try {
            val cacheDir = File(app.cacheDir, "album_art")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        val name = file.name
                        if (name.endsWith(".webp")) {
                            if (name.startsWith("album_")) {
                                val parts = name.substring(6, name.length - 5).split("_")
                                if (parts.size == 2) {
                                    val albumId = parts[0].toLongOrNull()
                                    val size = parts[1]
                                    if (albumId != null) {
                                        if (size == "128") ThumbnailRegistry.add128(albumId)
                                        else if (size == "256") ThumbnailRegistry.add256(albumId)
                                    }
                                }
                            } else if (name.startsWith("song_")) {
                                val parts = name.substring(5, name.length - 5).split("_")
                                if (parts.size == 2) {
                                    val songId = parts[0]
                                    val size = parts[1]
                                    if (size == "128") ThumbnailRegistry.addSong128(songId)
                                    else if (size == "256") ThumbnailRegistry.addSong256(songId)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing thumbnail registry", e)
        }
    }

    private suspend fun generateMissingThumbnails(songs: List<Song>) {
        val cacheDir = File(app.cacheDir, "album_art")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // 1. Generate Album-level Thumbnails
        val uniqueAlbums = songs.filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
            .associateBy { it.albumId }

        uniqueAlbums.forEach { (albumId, song) ->
            val file128 = File(cacheDir, "album_${albumId}_128.webp")
            val file256 = File(cacheDir, "album_${albumId}_256.webp")
            
            if (!file128.exists() || !file256.exists()) {
                generateWebpFromUri(song.artworkUri, file128, file256)
            }
            
            if (file128.exists()) ThumbnailRegistry.add128(albumId)
            if (file256.exists()) ThumbnailRegistry.add256(albumId)
        }

        // 2. Generate Song-level Thumbnails (for individual song covers)
        songs.forEach { song ->
            if (song.mediaUri.isNotEmpty()) {
                val file128 = File(cacheDir, "song_${song.id}_128.webp")
                val file256 = File(cacheDir, "song_${song.id}_256.webp")
                
                if (!file128.exists() || !file256.exists()) {
                    generateSongWebp(song, file128, file256)
                }
                
                if (file128.exists()) ThumbnailRegistry.addSong128(song.id)
                if (file256.exists()) ThumbnailRegistry.addSong256(song.id)
            }
        }
    }

    private fun generateWebpFromUri(artworkUri: String, file128: File, file256: File) {
        try {
            val uri = artworkUri.toUri()
            app.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return
                
                val square = if (original.width == original.height) original else {
                    val size = minOf(original.width, original.height)
                    Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
                }

                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

                if (!file128.exists()) {
                    square.scale(128, 128).compress(format, 80, FileOutputStream(file128))
                }
                if (!file256.exists()) {
                    square.scale(256, 256).compress(format, 80, FileOutputStream(file256))
                }

                if (square != original) square.recycle()
                original.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumb gen failed for $artworkUri", e)
        }
    }

    private fun generateSongWebp(song: Song, file128: File, file256: File) {
        try {
            var bitmap: Bitmap? = null
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(app, Uri.parse(song.mediaUri))
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {
                // Not a fatal issue; many songs do not have custom embedded artwork
            } finally {
                try {
                    retriever?.close()
                } catch (e: Exception) {}
            }

            // Fallback to media store album art if no embedded artwork is found in file
            if (bitmap == null && song.artworkUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(song.artworkUri)
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        bitmap = BitmapFactory.decodeStream(input)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load fallback album art for song ${song.id}", e)
                }
            }

            val original = bitmap ?: return

            val square = if (original.width == original.height) original else {
                val size = minOf(original.width, original.height)
                Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
            }

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

            if (!file128.exists()) {
                square.scale(128, 128).compress(format, 80, FileOutputStream(file128))
            }
            if (!file256.exists()) {
                square.scale(256, 256).compress(format, 80, FileOutputStream(file256))
            }

            if (square != original) square.recycle()
            original.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Webp generation failed for song ${song.id}", e)
        }
    }

    // Data Exposure
    fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            if (query.isEmpty()) songDao.getAllSongsPaging() else songDao.searchSongsPaging("%$query%")
        }.flow
    }

    fun getAlbumsFlow(query: String) = if (query.isEmpty()) albumDao.getAllAlbums() else albumDao.searchAlbums("%$query%")
    fun getArtistsFlow(query: String) = if (query.isEmpty()) artistDao.getAllArtists() else artistDao.searchArtists("%$query%")
    val playlistsFlow = playlistDao.getAllPlaylists()
    
    fun getFavoriteSongIds() = playlistDao.getPlaylistSongIdsFlow("Favoritas").map { it.toSet() }

    // Playlist actions
    suspend fun toggleFavorite(song: Song) {
        val favId = getOrCreatePlaylist("Favoritas")
        val isFav = playlistDao.getPlaylistSongIdsFlow("Favoritas").map { it.contains(song.id) }.firstInFlow()
        if (isFav) {
            playlistDao.deletePlaylistSong(favId, song.id)
        } else {
            val pos = playlistDao.getPlaylistSongCountSync(favId)
            playlistDao.insertPlaylistSong(PlaylistSong(favId, song.id, pos))
        }
    }

    private suspend fun getOrCreatePlaylist(name: String): Long {
        return playlistDao.getPlaylistByName(name)?.id ?: playlistDao.insert(Playlist(name = name, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String) {
        if (name.isNotBlank()) getOrCreatePlaylist(name.trim())
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylist(id)
        playlistDao.clearPlaylistSongs(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        val pos = playlistDao.getPlaylistSongCountSync(playlistId)
        playlistDao.insertPlaylistSong(PlaylistSong(playlistId, songId, pos))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.deletePlaylistSong(playlistId, songId)
    }

    fun getSongsForPlaylist(id: Long) = playlistDao.getSongsForPlaylist(id)
    fun getSongsByAlbum(id: Long) = songDao.getSongsByAlbum(id)
    fun getSongsByArtist(name: String) = songDao.getSongsByArtist(name)
    
    // Helper extension
    private suspend fun <T> Flow<T>.firstInFlow(): T {
        return this.first()
    }
}
