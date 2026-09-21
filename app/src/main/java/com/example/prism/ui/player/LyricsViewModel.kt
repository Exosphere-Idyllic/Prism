package com.example.prism.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prism.data.entity.Song
import com.example.prism.domain.model.LyricsSource
import com.example.prism.domain.model.SongLyrics
import com.example.prism.domain.repository.LyricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated strictly to lyrics resolution, selection, and persistence.
 * Follows the Single Responsibility Principle (SRP) by decoupling lyrics logic
 * from [PlaybackViewModel].
 */
class LyricsViewModel(
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    private val _songLyrics = MutableStateFlow<SongLyrics?>(null)
    val songLyrics: StateFlow<SongLyrics?> = _songLyrics.asStateFlow()

    private var currentSongId: String? = null
    private var loadJob: Job? = null

    fun loadLyrics(song: Song?) {
        if (song == null) {
            currentSongId = null
            _songLyrics.value = null
            loadJob?.cancel()
            return
        }

        if (song.id == currentSongId && _songLyrics.value != null) {
            return
        }

        currentSongId = song.id
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val lyrics = lyricsRepository.getLyrics(song)
            if (currentSongId == song.id) {
                _songLyrics.value = lyrics
            }
        }
    }

    fun selectSource(source: LyricsSource) {
        val current = _songLyrics.value ?: return
        _songLyrics.value = current.copy(selectedSource = source)
    }

    fun setCustomLyricsUri(song: Song, uri: String) {
        viewModelScope.launch {
            lyricsRepository.setCustomLyricsUri(song.id, uri)
            // Reload lyrics after updating custom URI
            currentSongId = null
            loadLyrics(song.copy(customLyricsUri = uri))
        }
    }
}
