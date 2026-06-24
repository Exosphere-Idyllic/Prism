package com.example.melodyplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.melodyplayer.data.MusicRepository
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application, viewModelScope)

    val isLoading = repository.isLoading
    val totalSongsCount = repository.totalSongsCount

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val songsFlow: Flow<PagingData<Song>> = _searchQuery
        .debounce(300.milliseconds)
        .flatMapLatest { query ->
            repository.getSongsFlow(query)
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val albumsFlow = _searchQuery
        .debounce(300.milliseconds)
        .flatMapLatest { query ->
            repository.getAlbumsFlow(query)
        }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val artistsFlow = _searchQuery
        .debounce(300.milliseconds)
        .flatMapLatest { query ->
            repository.getArtistsFlow(query)
        }

    val playlistsFlow = repository.playlistsFlow
    val favoriteSongIds = repository.getFavoriteSongIds()

    init {
        repository.startObserving()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repository.toggleFavorite(song) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun getSongsForPlaylist(id: Long) = repository.getSongsForPlaylist(id)
    fun getSongsByAlbum(id: Long) = repository.getSongsByAlbum(id)
    fun getSongsByArtist(name: String) = repository.getSongsByArtist(name)

    fun loadLocalSongs() {
        // The observer is already registered by init; only trigger a new scan.
        repository.triggerScan()
    }

    /** Requests HIGH-priority WebP generation for an album visible on screen. */
    fun requestThumbnail(albumId: Long, artworkUri: String) {
        repository.requestThumbnail(albumId, artworkUri)
    }

    /** Requests HIGH-priority WebP generation for a song visible on screen or currently playing. */
    fun requestSongThumbnail(song: com.example.melodyplayer.data.Song) {
        repository.requestSongThumbnail(song)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopObserving()
    }
}
