package com.example.prism.domain.repository

import com.example.prism.data.db.PlaylistWithCount
import com.example.prism.data.entity.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>>
    fun getFavoriteSongIds(): Flow<Set<String>>
    suspend fun toggleFavorite(song: Song)
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addSongToPlaylist(playlistId: Long, songId: String)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    fun getSongsForPlaylist(id: Long): Flow<List<Song>>
}
