package com.example.prism.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.db.search.SearchQuery
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Implementation of [LibraryRepository] handling music library queries.
 * Follows the Single Responsibility Principle (SRP).
 */
class LibraryRepositoryImpl(
    database: AppDatabase,
    private val dispatchers: DispatcherProvider,
    private val onArtworkChanged: () -> Unit = {},
) : LibraryRepository {

    private val songDao = database.songDao()
    private val albumDao = database.albumDao()
    private val artistDao = database.artistDao()

    override suspend fun getSongById(id: String?): Song? = withContext(dispatchers.io) {
        if (id == null) return@withContext null
        songDao.getSongByIdSync(id)
    }

    override suspend fun getAllSongs(): List<Song> = withContext(dispatchers.io) {
        songDao.getAllSongs()
    }

    override suspend fun getAllSongIds(): List<String> = withContext(dispatchers.io) {
        songDao.getAllSongIds()
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> = withContext(dispatchers.io) {
        if (ids.isEmpty()) return@withContext emptyList()
        val songs = songDao.getSongsByIds(ids)
        val songMap = songs.associateBy { it.id }
        ids.mapNotNull { songMap[it] }
    }

    override fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 40,
                initialLoadSize = 40,
                prefetchDistance = 25,
                maxSize = 400,
                enablePlaceholders = false,
            ),
        ) {
            // minLength = 1: "r" → "r*", comportamiento Spotify-style.
            // query vacía → getAllSongsPaging (sin filtro).
            val ftsQuery = if (query.isNotEmpty()) SearchQuery.toFtsMatch(query, minLength = 1) else null
            if (ftsQuery != null) songDao.searchSongsFts(ftsQuery)
            else songDao.getAllSongsPaging()
        }.flow
    }

    override fun getAlbumsFlow(query: String): Flow<List<Album>> {
        val ftsQuery = if (query.isNotEmpty()) SearchQuery.toFtsMatch(query, minLength = 1) else null
        return if (ftsQuery != null) albumDao.searchAlbumsFts(ftsQuery)
        else albumDao.getAllAlbums()
    }

    override fun getArtistsFlow(query: String): Flow<List<Artist>> {
        val ftsQuery = if (query.isNotEmpty()) SearchQuery.toFtsMatch(query, minLength = 1) else null
        return if (ftsQuery != null) artistDao.searchArtistsFts(ftsQuery)
        else artistDao.getAllArtists()
    }

    override fun getSongsByAlbum(id: Long): Flow<List<Song>> = songDao.getSongsByAlbum(id)
    override fun getSongsByArtist(name: String): Flow<List<Song>> = songDao.getSongsByArtist(name)

    override suspend fun updateSongArtwork(songId: String, artworkUri: String) = withContext(dispatchers.io) {
        songDao.updateCustomArtworkUri(songId, artworkUri)
        onArtworkChanged()
    }

    override suspend fun updateAlbumCover(albumId: Long, coverUri: String) = withContext(dispatchers.io) {
        albumDao.updateCustomCoverUri(albumId, coverUri)
        onArtworkChanged()
    }
}
