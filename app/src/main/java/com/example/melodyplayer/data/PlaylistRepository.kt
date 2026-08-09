package com.example.melodyplayer.data

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

const val FAVORITES_PLAYLIST_NAME = "Favoritas"

class PlaylistRepository(private val database: AppDatabase) {

    private val playlistDao = database.playlistDao()

    val playlistsFlow = playlistDao.getAllPlaylists()
    val playlistsWithCountsFlow = playlistDao.getAllPlaylistsWithCounts()

    fun getFavoriteSongIds(): Flow<Set<String>> =
        playlistDao.getPlaylistSongIdsFlow(FAVORITES_PLAYLIST_NAME).map { it.toSet() }

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        val favId = getOrCreatePlaylist(FAVORITES_PLAYLIST_NAME)
        val isFav = playlistDao.getPlaylistSongIdsFlow(FAVORITES_PLAYLIST_NAME).map { it.contains(song.id) }.first()
        if (isFav) {
            playlistDao.deletePlaylistSong(favId, song.id)
        } else {
            val pos = playlistDao.getPlaylistSongCountSync(favId)
            playlistDao.insertPlaylistSong(PlaylistSong(favId, song.id, pos))
        }
    }

    suspend fun getOrCreatePlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistByName(name)?.id
            ?: playlistDao.insert(Playlist(
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
    }

    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        if (name.isNotBlank()) getOrCreatePlaylist(name.trim())
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(id)
        playlistDao.clearPlaylistSongs(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        val pos = playlistDao.getPlaylistSongCountSync(playlistId)
        playlistDao.insertPlaylistSong(PlaylistSong(playlistId, songId, pos))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistSong(playlistId, songId)
    }

    fun getSongsForPlaylist(id: Long) = playlistDao.getSongsForPlaylist(id)
}
