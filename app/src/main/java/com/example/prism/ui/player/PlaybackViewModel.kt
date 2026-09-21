package com.example.prism.ui.player

import androidx.lifecycle.ViewModel
import com.example.prism.data.entity.Song
import com.example.prism.player.PlaybackManager
import kotlinx.coroutines.flow.StateFlow

import com.example.prism.player.ProgressState

class PlaybackViewModel(
    private val playbackManager: PlaybackManager,
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val isPlayingState: StateFlow<Boolean> = playbackManager.isPlayingState
    val progressState: StateFlow<ProgressState> = playbackManager.progressState

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList()) {
        playbackManager.playSong(song, playlistSongs)
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun next() {
        playbackManager.next()
    }

    fun previous() {
        playbackManager.previous()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }
}