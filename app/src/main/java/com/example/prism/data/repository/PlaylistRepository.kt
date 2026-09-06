package com.example.prism.data.repository

import androidx.room.withTransaction
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.db.PlaylistWithCount
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.PlaylistSong
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val FAVORITES_PLAYLIST_NAME = "Favoritas"

class PlaylistRepositoryImpl(
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : PlaylistRepository {

    private val playlistDao = database.playlistDao()

    override val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>> = playlistDao.getAllPlaylistsWithCounts()

    override fun getFavoriteSongIds(): Flow<Set<String>> =
        playlistDao.getPlaylistSongIdsFlow(FAVORITES_PLAYLIST_NAME).map { it.toSet() }

    override suspend fun toggleFavorite(song: Song): Unit = withContext(dispatchers.io) {
        val favId = getOrCreatePlaylist(FAVORITES_PLAYLIST_NAME)
        database.withTransaction {
            val isFav = playlistDao.isSongInPlaylist(favId, song.id) > 0
            if (isFav) {
                playlistDao.deletePlaylistSong(favId, song.id)
            } else {
                val pos = playlistDao.getPlaylistSongCountSync(favId)
                playlistDao.insertPlaylistSong(PlaylistSong(favId, song.id, pos))
            }
        }
    }

    private val playlistMutex = Mutex()

    suspend fun getOrCreatePlaylist(name: String): Long = withContext(dispatchers.io) {
        playlistMutex.withLock {
            val existing = playlistDao.getPlaylistByName(name)
            if (existing != null) return@withLock existing.id

            val insertedId = playlistDao.insert(
                Playlist(
                    name = name,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (insertedId != -1L) {
                insertedId
            } else {
                playlistDao.getPlaylistByName(name)?.id ?: -1L
            }
        }
    }

    override suspend fun createPlaylist(name: String): Unit = withContext(dispatchers.io) {
        if (name.isNotBlank()) {
            getOrCreatePlaylist(name.trim())
        }
    }

    override suspend fun deletePlaylist(id: Long): Unit = withContext(dispatchers.io) {
        playlistDao.deletePlaylist(id)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: String): Unit = withContext(dispatchers.io) {
        database.withTransaction {
            if (playlistDao.isSongInPlaylist(playlistId, songId) == 0) {
                val pos = playlistDao.getPlaylistSongCountSync(playlistId)
                playlistDao.insertPlaylistSong(PlaylistSong(playlistId, songId, pos))
            }
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String): Unit = withContext(dispatchers.io) {
        playlistDao.deletePlaylistSong(playlistId, songId)
    }

    override fun getSongsForPlaylist(id: Long): Flow<List<Song>> = playlistDao.getSongsForPlaylist(id)
}
