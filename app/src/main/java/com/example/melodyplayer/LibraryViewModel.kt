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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application, viewModelScope)

    val isLoading = repository.isLoading
    val totalSongsCount = repository.totalSongsCount

    // ── Thumbnail state (StateFlow — safe for derivedStateOf in composables) ──
    // These replace the old global ThumbnailRegistry mutableStateMapOf objects.
    // Each flow holds the set of IDs for which a WebP thumbnail is confirmed on disk.
    // Using StateFlow means emissions are conflated: rapid bulk-inserts produce a
    // single recomposition per frame instead of one per thumbnail write.
    val albumThumbnail128Ids = repository.albumThumbnail128Ids
    val albumThumbnail256Ids = repository.albumThumbnail256Ids
    val songThumbnail128Ids  = repository.songThumbnail128Ids
    val songThumbnail256Ids  = repository.songThumbnail256Ids

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
    val playlistsWithCountsFlow = repository.playlistsWithCountsFlow
    val favoriteSongIds = repository.getFavoriteSongIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
        repository.triggerScan()
    }

    fun requestThumbnail(albumId: Long, artworkUri: String) {
        repository.requestThumbnail(albumId, artworkUri)
    }

    fun requestSongThumbnail(song: Song) {
        repository.requestSongThumbnail(song)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopObserving()
    }
}