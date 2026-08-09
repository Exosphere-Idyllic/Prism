package com.example.melodyplayer

import android.content.Context
import androidx.paging.PagingData
import com.example.melodyplayer.data.Album
import com.example.melodyplayer.data.Artist
import com.example.melodyplayer.data.MusicRepository
import com.example.melodyplayer.data.Playlist
import com.example.melodyplayer.data.PlaylistWithCount
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class FakeMusicRepository : MusicRepository {
    private val songs = mutableListOf<Song>()

    override val appContext: Context
        get() = throw UnsupportedOperationException("Not needed in fake")

    override val totalSongsCount: StateFlow<Int> = MutableStateFlow(0)
    override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override val albumThumbnail128Ids: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
    override val albumThumbnail256Ids: StateFlow<Set<Long>> = MutableStateFlow(emptySet())

    override val playlistsFlow: Flow<List<Playlist>> = flowOf(emptyList())
    override val playlistsWithCountsFlow: Flow<List<PlaylistWithCount>> = flowOf(emptyList())

    fun setSongs(songList: List<Song>) {
        songs.clear()
        songs.addAll(songList)
    }

    override suspend fun getSongById(id: String?): Song? {
        return songs.find { it.id == id }
    }

    override suspend fun getAllSongs(): List<Song> {
        return songs.toList()
    }

    override fun startObserving() {}
    override fun triggerScan() {}
    override fun stopObserving() {}

    override fun getSongsFlow(query: String): Flow<PagingData<Song>> = flowOf(PagingData.empty())
    override fun getAlbumsFlow(query: String): Flow<List<Album>> = flowOf(emptyList())
    override fun getArtistsFlow(query: String): Flow<List<Artist>> = flowOf(emptyList())

    override fun getFavoriteSongIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun toggleFavorite(song: Song) {}
    override suspend fun createPlaylist(name: String) {}
    override suspend fun deletePlaylist(id: Long) {}
    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) {}
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {}

    override fun getSongsForPlaylist(id: Long): Flow<List<Song>> = flowOf(emptyList())
    override fun getSongsByAlbum(id: Long): Flow<List<Song>> = flowOf(emptyList())
    override fun getSongsByArtist(name: String): Flow<List<Song>> = flowOf(emptyList())
}
