package com.example.melodyplayer.data

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main entry point interface for the data layer.
 * Coordinates between specialized managers and exposes data via [Flow]s.
 */
interface MusicRepository {
    val totalSongsCount: StateFlow<Int>
    val isLoading: StateFlow<Boolean>

    val playlistsFlow: Flow<List<Playlist>>
    val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>>

    suspend fun getSongById(id: String?): Song?
    suspend fun getAllSongs(): List<Song>
    suspend fun getSongsWindow(currentSongId: String, windowSize: Int = 50): List<Song>

    fun startObserving()
    fun triggerScan()
    fun stopObserving()

    fun getSongsFlow(query: String): Flow<PagingData<Song>>
    fun getAlbumsFlow(query: String): Flow<List<Album>>
    fun getArtistsFlow(query: String): Flow<List<Artist>>

    fun getFavoriteSongIds(): Flow<Set<String>>

    suspend fun toggleFavorite(song: Song)
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addSongToPlaylist(playlistId: Long, songId: String)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    fun getSongsForPlaylist(id: Long): Flow<List<Song>>
    fun getSongsByAlbum(id: Long): Flow<List<Song>>
    fun getSongsByArtist(name: String): Flow<List<Song>>
}
