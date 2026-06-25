package com.example.melodyplayer.data

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import java.io.File
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
    private val thumbnailCacheDao = database.thumbnailCacheDao()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _totalSongsCount = MutableStateFlow(0)
    val totalSongsCount = _totalSongsCount.asStateFlow()

    // ── Thumbnail state (replaces global ThumbnailRegistry) ──────────────────
    // Each set holds IDs for which a WebP file is confirmed to exist.
    // StateFlow emissions are conflated, so rapid bulk inserts produce at most
    // one recomposition per frame rather than one per thumbnail write.
    private val _albumThumbnail128Ids = MutableStateFlow<Set<Long>>(emptySet())
    val albumThumbnail128Ids = _albumThumbnail128Ids.asStateFlow()

    private val _albumThumbnail256Ids = MutableStateFlow<Set<Long>>(emptySet())
    val albumThumbnail256Ids = _albumThumbnail256Ids.asStateFlow()

    private val _songThumbnail128Ids = MutableStateFlow<Set<String>>(emptySet())
    val songThumbnail128Ids = _songThumbnail128Ids.asStateFlow()

    private val _songThumbnail256Ids = MutableStateFlow<Set<String>>(emptySet())
    val songThumbnail256Ids = _songThumbnail256Ids.asStateFlow()

    // ── Internal helpers ──────────────────────────────────────────────────────
    private fun addAlbum128(albumId: Long) = _albumThumbnail128Ids.update { it + albumId }
    private fun addAlbum256(albumId: Long) = _albumThumbnail256Ids.update { it + albumId }
    private fun addSong128(songId: String) = _songThumbnail128Ids.update { it + songId }
    private fun addSong256(songId: String) = _songThumbnail256Ids.update { it + songId }

    // ─────────────────────────────────────────────────────────────────────────

    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var contentObserver: ContentObserver? = null
    private var scanJob: Job? = null

    private val prefs = app.getSharedPreferences("music_repository_prefs", Context.MODE_PRIVATE)
    private var lastScanTimestamp: Long
        get() = prefs.getLong("last_scan_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_scan_timestamp", value).apply()

    /** Limits concurrent WebP encoding to avoid saturating disk I/O. */
    private val thumbnailSemaphore = Semaphore(2)

    init {
        // Debounced MediaStore observer → scan
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            contentObserverEvents
                .debounce(1000.milliseconds)
                .collect {
                    performScan()
                }
        }

        // HIGH-priority thumbnail worker (visible items, current song)
        scope.launch(Dispatchers.IO) {
            ThumbnailQueue.highFlow().collect { request ->
                thumbnailSemaphore.withPermit { processThumbnailRequest(request) }
            }
        }
    }

    private var hasStarted = false

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
        if (!hasStarted) {
            hasStarted = true
            scope.launch { performScan() }
        }
    }

    fun triggerScan() {
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

            // 1. Load thumbnail index from Room into the new StateFlows
            loadThumbnailIndexFromDb()

            // 2. Get Room state
            val roomSongsInfo = songDao.getSongsSyncInfo().associateBy { it.id }

            // 3. Query MediaStore
            val scanFilter = if (roomSongsInfo.isEmpty()) 0L else lastScanTimestamp
            val mediaStoreIds = queryMediaStoreIds()
            val changedSongs = queryMediaStore(scanFilter)

            // 4. Identify changes (incremental)
            val toUpsert = mutableListOf<Song>()
            val toDelete = mutableListOf<String>()

            for (song in changedSongs) {
                val dbSongInfo = roomSongsInfo[song.id]
                if (dbSongInfo == null || song.dateModified > dbSongInfo.dateModified) {
                    toUpsert.add(song)
                }
            }

            for (dbId in roomSongsInfo.keys) {
                val dbIdLong = dbId.toLongOrNull() ?: -1L
                if (!mediaStoreIds.contains(dbIdLong)) {
                    toDelete.add(dbId)
                }
            }

            val toUpsertIds = toUpsert.map { it.id }
            val oldSongs = if (toUpsertIds.isNotEmpty()) songDao.getSongsByIds(toUpsertIds) else emptyList()
            val songsToDelete = if (toDelete.isNotEmpty()) songDao.getSongsByIds(toDelete) else emptyList()

            // 5. Apply changes
            if (toUpsert.isNotEmpty()) {
                toUpsert.chunked(200).forEach { chunk -> songDao.insertAll(chunk) }
            }
            if (toDelete.isNotEmpty()) {
                toDelete.chunked(200).forEach { chunk ->
                    songDao.deleteSongsByIds(chunk)
                    thumbnailCacheDao.deleteSongEntries(chunk)
                }
                // Remove deleted IDs from in-memory sets
                _songThumbnail128Ids.update { it - toDelete.toSet() }
                _songThumbnail256Ids.update { it - toDelete.toSet() }
            }

            // 6. Update albums and artists
            if (toUpsert.isNotEmpty() || toDelete.isNotEmpty()) {
                updateAlbumsAndArtistsIncrementally(toUpsert, oldSongs, songsToDelete)
            }

            lastScanTimestamp = (System.currentTimeMillis() / 1000L) - 5

            // 7. Finalize — use COUNT query instead of loading all songs
            _totalSongsCount.value = songDao.getSongCount()
            _isLoading.value = false

            // 8. Trigger WorkManager for background thumbnail pre-generation
            try {
                val workRequest = OneTimeWorkRequestBuilder<ThumbnailWorker>().build()
                WorkManager.getInstance(app).enqueueUniqueWork(
                    "thumbnail_pre_generation",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule background thumbnail work", e)
            }
        }
    }

    private fun queryMediaStoreIds(): Set<Long> {
        val set = mutableSetOf<Long>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            app.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    set.add(cursor.getLong(idCol))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore IDs", e)
        }
        return set
    }

    private fun queryMediaStore(lastScan: Long = 0L): List<Song> {
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
        val selection = if (lastScan > 0L) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATE_MODIFIED} > $lastScan"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
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

    private suspend fun updateAlbumsAndArtistsIncrementally(
        toUpsert: List<Song>,
        oldSongs: List<Song>,
        songsToDelete: List<Song>
    ) {
        val totalChanges = toUpsert.size + songsToDelete.size
        if (totalChanges > 50) {
            updateAlbumsAndArtists()
            return
        }

        val affectedAlbumIds = (
            toUpsert.map { it.albumId } +
            oldSongs.map { it.albumId } +
            songsToDelete.map { it.albumId }
        ).toSet()

        val affectedArtists = (
            toUpsert.map { it.artist } +
            oldSongs.map { it.artist } +
            songsToDelete.map { it.artist }
        ).toSet()

        for (albumId in affectedAlbumIds) {
            if (albumId <= 0L) continue
            val albumSongs = songDao.getSongsByAlbumSync(albumId)
            if (albumSongs.isEmpty()) {
                albumDao.deleteById(albumId)
            } else {
                val sample = albumSongs.first()
                albumDao.insert(Album(
                    id = albumId,
                    albumName = sample.album,
                    artist = sample.artist,
                    coverPath = sample.artworkUri,
                    songCount = albumSongs.size
                ))
            }
        }

        for (artistName in affectedArtists) {
            if (artistName.isEmpty()) continue
            val artistSongs = songDao.getSongsByArtistSync(artistName)
            if (artistSongs.isEmpty()) {
                artistDao.deleteById(artistName.hashCode().toLong())
            } else {
                val distinctAlbumCount = artistSongs.map { it.albumId }.distinct().size
                artistDao.insert(Artist(
                    id = artistName.hashCode().toLong(),
                    name = artistName,
                    songCount = artistSongs.size,
                    albumCount = distinctAlbumCount
                ))
            }
        }
    }

    private suspend fun updateAlbumsAndArtists() {
        val allSongs = songDao.getAllSongs()

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

        val artists = allSongs.groupBy { it.artist }.map { (name, songs) ->
            Artist(
                id = name.hashCode().toLong(),
                name = name,
                songCount = songs.size,
                albumCount = songs.map { it.albumId }.distinct().size
            )
        }
        artistDao.deleteAll()
        artistDao.insertAll(artists)
    }

    /**
     * Reads the thumbnail index from Room and populates the StateFlow sets.
     * Called once per scan at startup — after this, sets are updated incrementally
     * by processThumbnailRequest as new thumbnails are generated.
     */
    private suspend fun loadThumbnailIndexFromDb() {
        try {
            val entries = thumbnailCacheDao.getAll()
            val album128 = mutableSetOf<Long>()
            val album256 = mutableSetOf<Long>()
            val song128 = mutableSetOf<String>()
            val song256 = mutableSetOf<String>()

            for (entry in entries) {
                when (entry.type) {
                    "album" -> {
                        val albumId = entry.entityId.toLongOrNull() ?: continue
                        if (entry.size == 128) album128.add(albumId) else album256.add(albumId)
                    }
                    "song" -> {
                        if (entry.size == 128) song128.add(entry.entityId) else song256.add(entry.entityId)
                    }
                }
            }

            // Emit all four sets at once — single StateFlow update per set
            _albumThumbnail128Ids.value = album128
            _albumThumbnail256Ids.value = album256
            _songThumbnail128Ids.value = song128
            _songThumbnail256Ids.value = song256
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail index from DB", e)
        }
    }

    fun requestThumbnail(albumId: Long, artworkUri: String) {
        ThumbnailQueue.enqueueAlbum(albumId, artworkUri, ThumbnailQueue.HIGH)
    }

    fun requestSongThumbnail(song: Song) {
        ThumbnailQueue.enqueueSong(song, ThumbnailQueue.HIGH)
    }

    private suspend fun processThumbnailRequest(request: ThumbnailRequest) {
        val cacheDir = File(app.cacheDir, "album_art")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        when (request) {
            is ThumbnailRequest.Album -> {
                val albumId = request.albumId
                val file128 = File(cacheDir, "album_${albumId}_128.webp")
                val file256 = File(cacheDir, "album_${albumId}_256.webp")
                if (!file128.exists() || !file256.exists()) {
                    val sizes = ThumbnailHelper.generateWebpFromUri(app, request.artworkUri, file128, file256, albumId)
                    if (sizes.contains(128)) {
                        addAlbum128(albumId)
                        scope.launch(Dispatchers.IO) {
                            thumbnailCacheDao.insertAll(listOf(
                                ThumbnailCacheEntry("album_${albumId}_128", albumId.toString(), "album", 128)
                            ))
                        }
                    }
                    if (sizes.contains(256)) {
                        addAlbum256(albumId)
                        scope.launch(Dispatchers.IO) {
                            thumbnailCacheDao.insertAll(listOf(
                                ThumbnailCacheEntry("album_${albumId}_256", albumId.toString(), "album", 256)
                            ))
                        }
                    }
                } else {
                    if (file128.exists()) addAlbum128(albumId)
                    if (file256.exists()) addAlbum256(albumId)
                }
            }
            is ThumbnailRequest.SongRequest -> {
                val song = request.song
                val file128 = File(cacheDir, "song_${song.id}_128.webp")
                val file256 = File(cacheDir, "song_${song.id}_256.webp")
                if (!file128.exists() || !file256.exists()) {
                    val sizes = ThumbnailHelper.generateSongWebp(app, song, file128, file256)
                    if (sizes.contains(128)) {
                        addSong128(song.id)
                        scope.launch(Dispatchers.IO) {
                            thumbnailCacheDao.insertAll(listOf(
                                ThumbnailCacheEntry("song_${song.id}_128", song.id, "song", 128)
                            ))
                        }
                    }
                    if (sizes.contains(256)) {
                        addSong256(song.id)
                        scope.launch(Dispatchers.IO) {
                            thumbnailCacheDao.insertAll(listOf(
                                ThumbnailCacheEntry("song_${song.id}_256", song.id, "song", 256)
                            ))
                        }
                    }
                } else {
                    if (file128.exists()) addSong128(song.id)
                    if (file256.exists()) addSong256(song.id)
                }
            }
        }
    }

    // ── Data exposure ─────────────────────────────────────────────────────────

    fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            if (query.isEmpty()) songDao.getAllSongsPaging() else songDao.searchSongsPaging("%$query%")
        }.flow
    }

    fun getAlbumsFlow(query: String) =
        if (query.isEmpty()) albumDao.getAllAlbums() else albumDao.searchAlbums("%$query%")

    fun getArtistsFlow(query: String) =
        if (query.isEmpty()) artistDao.getAllArtists() else artistDao.searchArtists("%$query%")

    val playlistsFlow = playlistDao.getAllPlaylists()

    fun getFavoriteSongIds() = playlistDao.getPlaylistSongIdsFlow("Favoritas").map { it.toSet() }

    // ── Playlist actions ──────────────────────────────────────────────────────

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
        return playlistDao.getPlaylistByName(name)?.id
            ?: playlistDao.insert(Playlist(
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
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

    private suspend fun <T> Flow<T>.firstInFlow(): T = this.first()
}