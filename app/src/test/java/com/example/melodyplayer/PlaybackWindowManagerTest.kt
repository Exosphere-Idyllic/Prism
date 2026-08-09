package com.example.melodyplayer

import com.example.melodyplayer.data.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWindowManagerTest {

    private val testScope = TestScope()
    private val windowManager = PlaybackWindowManager(testScope)

    private fun createSong(id: String, title: String = "Title $id"): Song {
        return Song(
            id = id,
            title = title,
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
    fun buildPlaybackWindow_centersWindowAroundCurrentSong() {
        val playlist = (1..100).map { createSong(it.toString()) }
        val currentSong = playlist[49] // Song with id "50" (index 49)

        val (windowSongs, windowIndex) = windowManager.buildPlaybackWindow(currentSong, playlist, windowSize = 50)

        assertEquals(50, windowSongs.size)
        assertEquals("25", windowSongs.first().id)
        assertEquals("74", windowSongs.last().id)
        assertEquals(25, windowIndex)
        assertEquals(currentSong.id, windowSongs[windowIndex].id)
    }

    @Test
    fun buildPlaybackWindow_handlesIndexNearStartOfPlaylist() {
        val playlist = (1..100).map { createSong(it.toString()) }
        val currentSong = playlist[2] // Song with id "3" (index 2)

        val (windowSongs, windowIndex) = windowManager.buildPlaybackWindow(currentSong, playlist, windowSize = 50)

        assertEquals(27, windowSongs.size)
        assertEquals("1", windowSongs.first().id)
        assertEquals(2, windowIndex)
        assertEquals(currentSong.id, windowSongs[windowIndex].id)
    }

    @Test
    fun buildPlaybackWindow_handlesSongNotInPlaylist() {
        val playlist = (1..10).map { createSong(it.toString()) }
        val unknownSong = createSong("999")

        val (windowSongs, windowIndex) = windowManager.buildPlaybackWindow(unknownSong, playlist, windowSize = 50)

        assertEquals(1, windowSongs.size)
        assertEquals("999", windowSongs.first().id)
        assertEquals(0, windowIndex)
    }
}
