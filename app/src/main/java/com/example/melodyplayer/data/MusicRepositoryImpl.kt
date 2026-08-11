package com.example.melodyplayer.data

import android.app.Application
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Default implementation of [MusicRepository].
 * Coordinates between specialized managers and exposes data via [Flow]s.
 */
class MusicRepositoryImpl(
    app: Application,
    private val scope: CoroutineScope
) : MusicRepository {

    private val database = AppDatabase.getDatabase(app)
    private val songDao = database.songDao()
    private val albumDao = database.albumDao()
    private val artistDao = database.artistDao()

    val playlistRepository = PlaylistRepository(database)

    private val scannerManager = MusicScannerManager(
        app = app,
        scope = scope,
        database = database,
        onTotalCountChanged = { /* Optionally notify observers */ }
    )

    override val totalSongsCount: StateFlow<Int> = scannerManager.totalSongsCount
    override val isLoading: StateFlow<Boolean> = scannerManager.isLoading

    override suspend fun getSongById(id: String?): Song? = withContext(Dispatchers.IO) {
        if (id == null) return@withContext null
        songDao.getSongByIdSync(id)
    }

    override suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongs()
    }

    override fun startObserving() = scannerManager.startObserving()
    override fun triggerScan() = scannerManager.triggerScan()
    override fun stopObserving() = scannerManager.stopObserving()

    // ── Data exposure ─────────────────────────────────────────────────────────

    override fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 40
            ),
            pagingSourceFactory = {
                if (query.isEmpty()) songDao.getAllSongsPaging() else songDao.searchSongsPaging("%$query%")
            }
        ).flow.cachedIn(scope)
    }

    override fun getAlbumsFlow(query: String): Flow<List<Album>> =
        if (query.isEmpty()) albumDao.getAllAlbums() else albumDao.searchAlbums("%$query%")

    override fun getArtistsFlow(query: String): Flow<List<Artist>> =
        if (query.isEmpty()) artistDao.getAllArtists() else artistDao.searchArtists("%$query%")

    override val playlistsFlow: Flow<List<Playlist>> = playlistRepository.playlistsFlow
    override val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>> = playlistRepository.playlistsWithCountsFlow

    override fun getFavoriteSongIds(): Flow<Set<String>> = playlistRepository.getFavoriteSongIds()

    // ── Playlist actions ──────────────────────────────────────────────────────

    override suspend fun toggleFavorite(song: Song) = playlistRepository.toggleFavorite(song)
    override suspend fun createPlaylist(name: String) = playlistRepository.createPlaylist(name)
    override suspend fun deletePlaylist(id: Long) = playlistRepository.deletePlaylist(id)
    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) = playlistRepository.addSongToPlaylist(playlistId, songId)
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = playlistRepository.removeSongFromPlaylist(playlistId, songId)

    override fun getSongsForPlaylist(id: Long): Flow<List<Song>> = playlistRepository.getSongsForPlaylist(id)
    override fun getSongsByAlbum(id: Long): Flow<List<Song>> = songDao.getSongsByAlbum(id)
    override fun getSongsByArtist(name: String): Flow<List<Song>> = songDao.getSongsByArtist(name)
}
