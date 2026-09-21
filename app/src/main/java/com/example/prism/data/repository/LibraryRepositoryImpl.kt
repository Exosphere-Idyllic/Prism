package com.example.prism.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
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

    override fun getSongsFlow(query: String): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 40,
            ),
        ) {
            if (query.isEmpty()) songDao.getAllSongsPaging() else songDao.searchSongsPaging("%$query%")
        }.flow
    }

    override fun getAlbumsFlow(query: String): Flow<List<Album>> =
        if (query.isEmpty()) albumDao.getAllAlbums() else albumDao.searchAlbums("%$query%")

    override fun getArtistsFlow(query: String): Flow<List<Artist>> =
        if (query.isEmpty()) artistDao.getAllArtists() else artistDao.searchArtists("%$query%")

    override fun getSongsByAlbum(id: Long): Flow<List<Song>> = songDao.getSongsByAlbum(id)
    override fun getSongsByArtist(name: String): Flow<List<Song>> = songDao.getSongsByArtist(name)

    override suspend fun updateSongArtwork(songId: String, artworkUri: String) = withContext(dispatchers.io) {
        songDao.updateCustomArtworkUri(songId, artworkUri)
    }

    override suspend fun updateAlbumCover(albumId: Long, coverUri: String) = withContext(dispatchers.io) {
        albumDao.updateCustomCoverUri(albumId, coverUri)
    }
}
