package com.example.prism.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.prism.domain.repository.MusicRepository
import com.example.prism.data.entity.Song
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
class LibraryViewModel(
    private val repository: MusicRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {

    val isLoading = repository.isLoading
    val totalSongsCount = repository.totalSongsCount

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val songsFlow: Flow<PagingData<Song>> = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            repository.getSongsFlow(query)
        }
        .cachedIn(viewModelScope)

    // SharingStarted.Lazily keeps the query alive as long as the ViewModel lives,
    // even when there are no active subscribers (e.g. while switching tabs).
    // This means switching back to Albums/Artists never triggers a fresh Room query.
    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val albumsFlow = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            repository.getAlbumsFlow(query)
        }
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val artistsFlow = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            repository.getArtistsFlow(query)
        }
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    val playlistsWithCountsFlow = repository.playlistsWithCountsFlow
        .map { it.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val favoriteSongIds: StateFlow<ImmutableSet<String>> = repository.getFavoriteSongIds()
        .map { it.toImmutableSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentSetOf())

    // The repository is a singleton managed by MainApplication — we do NOT call
    // startObserving() or stopObserving() here to avoid conflicting lifecycle management.

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(dispatchers.io) { repository.toggleFavorite(song) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(dispatchers.io) { repository.createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(dispatchers.io) { repository.deletePlaylist(id) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(dispatchers.io) { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(dispatchers.io) { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun getSongsForPlaylist(id: Long) = repository.getSongsForPlaylist(id)
    fun getSongsByAlbum(id: Long) = repository.getSongsByAlbum(id)
    fun getSongsByArtist(name: String) = repository.getSongsByArtist(name)

    fun loadLocalSongs() {
        repository.triggerScan()
    }


}