package com.example.melodyplayer.data

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PlaylistRepository(private val database: AppDatabase) {

    private val playlistDao = database.playlistDao()

    val playlistsFlow = playlistDao.getAllPlaylists()
    val playlistsWithCountsFlow = playlistDao.getAllPlaylistsWithCounts()

    fun getFavoriteSongIds(): Flow<Set<String>> =
        playlistDao.getPlaylistSongIdsFlow("Favoritas").map { it.toSet() }

    suspend fun toggleFavorite(song: Song) {
        val favId = getOrCreatePlaylist("Favoritas")
        val isFav = playlistDao.getPlaylistSongIdsFlow("Favoritas").map { it.contains(song.id) }.first()
        if (isFav) {
            playlistDao.deletePlaylistSong(favId, song.id)
        } else {
            val pos = playlistDao.getPlaylistSongCountSync(favId)
            playlistDao.insertPlaylistSong(PlaylistSong(favId, song.id, pos))
        }
    }

    suspend fun getOrCreatePlaylist(name: String): Long {
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
}
