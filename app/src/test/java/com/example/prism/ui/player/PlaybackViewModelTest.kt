package com.example.prism.ui.player

import com.example.prism.data.entity.Song
import com.example.prism.player.PlaybackManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackViewModelTest {

    private val currentSongFlow = MutableStateFlow<Song?>(null)
    private val isPlayingFlow = MutableStateFlow(false)
    private val progressFlow = MutableStateFlow(ProgressState())

    private val playbackManager: PlaybackManager = mockk(relaxed = true) {
        every { currentSong } returns currentSongFlow
        every { isPlayingState } returns isPlayingFlow
        every { progressState } returns progressFlow
    }

    private val viewModel = PlaybackViewModel(playbackManager)

    private fun createSong(id: String): Song {
        return Song(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            mediaUri = "content://media/$id",
            artworkUri = "",
            duration = 180000L,
            dateModified = 1000L
        )
    }

    @Test
    fun playSong_delegatesToPlaybackManager() {
        val song = createSong("1")
        val playlist = listOf(song)

        viewModel.playSong(song, playlist)

        verify(exactly = 1) { playbackManager.playSong(song, playlist) }
    }

    @Test
    fun togglePlayPause_delegatesToPlaybackManager() {
        viewModel.togglePlayPause()

        verify(exactly = 1) { playbackManager.togglePlayPause() }
    }

    @Test
    fun next_delegatesToPlaybackManager() {
        viewModel.next()

        verify(exactly = 1) { playbackManager.next() }
    }

    @Test
    fun previous_delegatesToPlaybackManager() {
        viewModel.previous()

        verify(exactly = 1) { playbackManager.previous() }
    }

    @Test
    fun seekTo_delegatesToPlaybackManager() {
        viewModel.seekTo(15000L)

        verify(exactly = 1) { playbackManager.seekTo(15000L) }
    }

    @Test
    fun stateFlows_exposePlaybackManagerFlows() {
        val song = createSong("42")
        currentSongFlow.value = song
        isPlayingFlow.value = true
        progressFlow.value = ProgressState(currentPosition = 12000L, duration = 180000L)

        assertEquals(song, viewModel.currentSong.value)
        assertEquals(true, viewModel.isPlayingState.value)
        assertEquals(ProgressState(12000L, 180000L), viewModel.progressState.value)
    }
}
