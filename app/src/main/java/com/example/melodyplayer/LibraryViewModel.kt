package com.example.melodyplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.melodyplayer.data.MusicRepository
import com.example.melodyplayer.data.Song
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.milliseconds

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    // Use the application-scoped singleton so the repository (and its scan) survive
    // ViewModel re-creations (screen rotations) without restarting.
    private val repository: MusicRepository = MainApplication.repository

    val isLoading = repository.isLoading
    val totalSongsCount = repository.totalSongsCount

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<String>.debounceSearch(): Flow<String> = transformLatest { query ->
        if (query.isEmpty()) {
            emit(query)
        } else {
            delay(300.milliseconds)
            emit(query)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val songsFlow: Flow<PagingData<Song>> = _searchQuery
        .debounceSearch()
        .flatMapLatest { query ->
            repository.getSongsFlow(query)
        }
        .cachedIn(viewModelScope)

    // SharingStarted.Lazily keeps the query alive as long as the ViewModel lives,
    // even when there are no active subscribers (e.g. while switching tabs).
    // This means switching back to Albums/Artists never triggers a fresh Room query.
    @OptIn(ExperimentalCoroutinesApi::class)
    val albumsFlow = _searchQuery
        .debounceSearch()
        .flatMapLatest { query ->
            repository.getAlbumsFlow(query)
        }
        .map { it.toImmutableList() }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsFlow = _searchQuery
        .debounceSearch()
        .flatMapLatest { query ->
            repository.getArtistsFlow(query)
        }
        .map { it.toImmutableList() }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    val playlistsFlow = repository.playlistsFlow
    val playlistsWithCountsFlow = repository.playlistsWithCountsFlow
        .map { it.toImmutableList() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val favoriteSongIds: Flow<ImmutableSet<String>> = repository.getFavoriteSongIds()
        .map { it.toImmutableSet() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentSetOf())

    // The repository is a singleton managed by MainApplication — we do NOT call
    // startObserving() or stopObserving() here to avoid conflicting lifecycle management.

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


}