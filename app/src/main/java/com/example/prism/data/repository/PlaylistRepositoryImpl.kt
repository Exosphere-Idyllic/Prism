package com.example.prism.data.repository

import androidx.room.withTransaction
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.Song
import com.example.prism.domain.model.PlaylistWithCount
import com.example.prism.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepositoryImpl(
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : PlaylistRepository {

    companion object {
        const val FAVORITES_PLAYLIST_NAME = "Favorites"
    }

    private val playlistDao = database.playlistDao()

    override val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>> =
        playlistDao.getAllPlaylistsWithCounts()

    override fun getFavoriteSongIds(): Flow<Set<String>> =
        playlistDao.getPlaylistSongIdsFlow(FAVORITES_PLAYLIST_NAME).map { it.toSet() }

    /**
     * Toggles favorite status for [song].
     *
     * The entire read-check-write cycle runs inside a [withTransaction] block so that
     * concurrent calls cannot produce duplicate playlist rows or duplicate PlaylistSong rows.
     * [getOrCreateFavorites] uses INSERT OR IGNORE + SELECT to prevent races at the
     * playlist-creation level as well.
     */
    override suspend fun toggleFavorite(song: Song) = withContext(dispatchers.io) {
        database.withTransaction {
            val playlist = playlistDao.getOrCreateFavorites(System.currentTimeMillis())
            val isFav = playlistDao.isSongInPlaylist(playlist.id, song.id)
            if (isFav) {
                playlistDao.deletePlaylistSong(playlist.id, song.id)
            } else {
                playlistDao.insertPlaylistSongAtEnd(playlist.id, song.id)
            }
        }
    }

    override suspend fun createPlaylist(name: String) = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        playlistDao.insert(Playlist(name = name, createdAt = now, updatedAt = now))
        Unit
    }

    override suspend fun deletePlaylist(id: Long) = withContext(dispatchers.io) {
        playlistDao.deletePlaylist(id)
    }

    /**
     * Adds [songId] to [playlistId] at the next available position.
     *
     * Uses [insertPlaylistSongAtEnd] which performs `MAX(position)+1` atomically inside
     * a single SQL statement, eliminating the read-then-insert race that caused
     * duplicate position values under concurrent access.
     */
    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) =
        withContext(dispatchers.io) {
            playlistDao.insertPlaylistSongAtEnd(playlistId, songId)
        }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) =
        withContext(dispatchers.io) {
            playlistDao.deletePlaylistSong(playlistId, songId)
        }

    override fun getSongsForPlaylist(id: Long): Flow<List<Song>> =
        playlistDao.getSongsForPlaylist(id)
}

fun isFavoritesPlaylist(name: String): Boolean =
    name.equals(PlaylistRepositoryImpl.FAVORITES_PLAYLIST_NAME, ignoreCase = true)
