package com.example.melodyplayer.data

import android.app.Application
import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for the data layer.
 * Coordinates between specialized managers and exposes data via [Flow]s.
 * Optimized to avoid holding large song lists in memory.
 */
class MusicRepository(private val app: Application, private val scope: CoroutineScope) {

    /** Exposed so [ArtworkInterceptor] can resolve thumbnail file paths. */
    val appContext: Context get() = app

    private val database = AppDatabase.getDatabase(app)
    private val songDao = database.songDao()
    private val albumDao = database.albumDao()
    private val artistDao = database.artistDao()
    private val thumbnailCacheDao = database.thumbnailCacheDao()

    val playlistRepository = PlaylistRepository(database)

    private val scannerManager = MusicScannerManager(
        app = app,
        scope = scope,
        database = database,
        onTotalCountChanged = { /* Optionally notify observers */ }
    )

    private val thumbnailCacheManager = ThumbnailCacheManager(
        thumbnailCacheDao = thumbnailCacheDao,
        scope = scope
    )

    val totalSongsCount = scannerManager.totalSongsCount
    val isLoading = scannerManager.isLoading
    val albumThumbnail128Ids = thumbnailCacheManager.albumThumbnail128Ids
    val albumThumbnail256Ids = thumbnailCacheManager.albumThumbnail256Ids

    /**
     * O(1) lookup is no longer possible without a cache.
     * Most UI components already have the [Song] object; this is only used for
     * cross-referencing media IDs.
     */
    suspend fun getSongById(id: String?): Song? {
        if (id == null) return null
        return songDao.getSongByIdSync(id)
    }

    fun startObserving() = scannerManager.startObserving()
    fun triggerScan() = scannerManager.triggerScan()
    fun stopObserving() = scannerManager.stopObserving()

    // ── Data exposure ─────────────────────────────────────────────────────────

    fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 40
            )
        ) {
            if (query.isEmpty()) songDao.getAllSongsPaging() else songDao.searchSongsPaging("%$query%")
        }.flow.cachedIn(scope)
    }

    fun getAlbumsFlow(query: String) =
        if (query.isEmpty()) albumDao.getAllAlbums() else albumDao.searchAlbums("%$query%")

    fun getArtistsFlow(query: String) =
        if (query.isEmpty()) artistDao.getAllArtists() else artistDao.searchArtists("%$query%")

    val playlistsFlow = playlistRepository.playlistsFlow
    val playlistsWithCountsFlow = playlistRepository.playlistsWithCountsFlow

    fun getFavoriteSongIds() = playlistRepository.getFavoriteSongIds()

    // ── Playlist actions ──────────────────────────────────────────────────────

    suspend fun toggleFavorite(song: Song) = playlistRepository.toggleFavorite(song)
    suspend fun createPlaylist(name: String) = playlistRepository.createPlaylist(name)
    suspend fun deletePlaylist(id: Long) = playlistRepository.deletePlaylist(id)
    suspend fun addSongToPlaylist(playlistId: Long, songId: String) = playlistRepository.addSongToPlaylist(playlistId, songId)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = playlistRepository.removeSongFromPlaylist(playlistId, songId)

    fun getSongsForPlaylist(id: Long) = playlistRepository.getSongsForPlaylist(id)
    fun getSongsByAlbum(id: Long) = songDao.getSongsByAlbum(id)
    fun getSongsByArtist(name: String) = songDao.getSongsByArtist(name)
}
