package com.example.prism.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.domain.repository.PlaylistRepository
import com.example.prism.domain.repository.ScannerRepository
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the music library screens (Songs, Albums, Artists, Playlists).
 * Adheres to the Interface Segregation Principle (ISP) by depending on fine-grained
 * repositories ([LibraryRepository], [PlaylistRepository], [ScannerRepository]) rather
 * than a monolithic God interface.
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val scannerRepository: ScannerRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {

    val isLoading = scannerRepository.isLoading
    val totalSongsCount = scannerRepository.totalSongsCount

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private fun searchDebounce(query: String): Long = if (query.isEmpty()) 0L else 300L

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val songsFlow: Flow<PagingData<Song>> = _searchQuery
        .debounce(::searchDebounce)
        .flatMapLatest { query ->
            libraryRepository.getSongsFlow(query)
        }
        .cachedIn(viewModelScope)

    // SharingStarted.Lazily keeps the query alive as long as the ViewModel lives,
    // even when there are no active subscribers (e.g. while switching tabs).
    // This means switching back to Albums/Artists never triggers a fresh Room query.
    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val albumsFlow = _searchQuery
        .debounce(::searchDebounce)
        .flatMapLatest { query ->
            libraryRepository.getAlbumsFlow(query)
        }
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val artistsFlow = _searchQuery
        .debounce(::searchDebounce)
        .flatMapLatest { query ->
            libraryRepository.getArtistsFlow(query)
        }
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    val playlistsWithCountsFlow = playlistRepository.playlistsWithCountsFlow
        .map { it.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val favoriteSongIds: StateFlow<ImmutableSet<String>> = playlistRepository.getFavoriteSongIds()
        .map { it.toImmutableSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentSetOf())

    // The scanner repository is a singleton managed by MainApplication — we do NOT call
    // startObserving() or stopObserving() here to avoid conflicting lifecycle management.

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(dispatchers.io) { playlistRepository.toggleFavorite(song) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(dispatchers.io) { playlistRepository.createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(dispatchers.io) { playlistRepository.deletePlaylist(id) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(dispatchers.io) { playlistRepository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(dispatchers.io) { playlistRepository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun getSongsForPlaylist(id: Long) = playlistRepository.getSongsForPlaylist(id)
    fun getSongsByAlbum(id: Long) = libraryRepository.getSongsByAlbum(id)
    fun getSongsByArtist(name: String) = libraryRepository.getSongsByArtist(name)

    fun updateSongArtwork(songId: String, artworkUri: String) {
        viewModelScope.launch(dispatchers.io) {
            libraryRepository.updateSongArtwork(songId, artworkUri)
        }
    }

    fun updateAlbumCover(albumId: Long, coverUri: String) {
        viewModelScope.launch(dispatchers.io) {
            libraryRepository.updateAlbumCover(albumId, coverUri)
        }
    }

    fun loadLocalSongs() {
        scannerRepository.triggerScan()
    }
}