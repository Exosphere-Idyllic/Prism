package com.example.prism.player

import android.app.Application
import androidx.media3.common.Player
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.data.preferences.PlaybackPreferences
import com.example.prism.domain.repository.LibraryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val repository: LibraryRepository = mockk(relaxed = true)
    private val preferences: PlaybackPreferences = mockk(relaxed = true) {
        coEvery { getShuffleEnabled() } returns false
        coEvery { getRepeatMode() } returns Player.REPEAT_MODE_OFF
        coEvery { shuffleEnabledFlow } returns flowOf(false)
        coEvery { repeatModeFlow } returns flowOf(Player.REPEAT_MODE_OFF)
    }
    private lateinit var app: Application
    private lateinit var playbackManager: PlaybackManagerImpl

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = RuntimeEnvironment.getApplication()
        playbackManager = PlaybackManagerImpl(
            app = app,
            repository = repository,
            preferences = preferences,
            dispatchers = testDispatcherProvider,
            scope = testScope
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        if (::playbackManager.isInitialized) {
            playbackManager.release()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasSensibleDefaults() {
        assertNull(playbackManager.currentSong.value)
        assertFalse(playbackManager.isPlayingState.value)
        assertEquals(0L, playbackManager.progressState.value.currentPosition)
        assertEquals(0L, playbackManager.progressState.value.duration)
        assertFalse(playbackManager.shuffleModeState.value)
        assertEquals(Player.REPEAT_MODE_OFF, playbackManager.repeatModeState.value)
    }

    @Test
    fun seekTo_updatesProgressStateImmediately() {
        playbackManager.seekTo(45000L)

        assertEquals(45000L, playbackManager.progressState.value.currentPosition)
    }

    @Test
    fun playSong_whenNotConnected_queuesPendingPlay() = runTest {
        val song = createSong("1")

        playbackManager.playSong(song)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(playbackManager.isPlayingState.value)
    }

    @Test
    fun toggleShuffle_togglesStateAndPersists() = runTest {
        assertFalse(playbackManager.shuffleModeState.value)

        playbackManager.toggleShuffle()
        assertTrue(playbackManager.shuffleModeState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { preferences.setShuffleEnabled(true) }

        playbackManager.toggleShuffle()
        assertFalse(playbackManager.shuffleModeState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { preferences.setShuffleEnabled(false) }
    }

    @Test
    fun toggleRepeatMode_cyclesRepeatModesAndPersists() = runTest {
        assertEquals(Player.REPEAT_MODE_OFF, playbackManager.repeatModeState.value)

        playbackManager.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_ALL, playbackManager.repeatModeState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { preferences.setRepeatMode(Player.REPEAT_MODE_ALL) }

        playbackManager.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_ONE, playbackManager.repeatModeState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { preferences.setRepeatMode(Player.REPEAT_MODE_ONE) }

        playbackManager.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_OFF, playbackManager.repeatModeState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { preferences.setRepeatMode(Player.REPEAT_MODE_OFF) }
    }

    @Test
    fun togglePlayPause_whenNotConnected_doesNotCrash() {
        playbackManager.togglePlayPause()
        assertFalse(playbackManager.isPlayingState.value)
    }

    @Test
    fun nextAndPrevious_whenNotConnected_doNotCrash() {
        playbackManager.next()
        playbackManager.previous()
        assertFalse(playbackManager.isPlayingState.value)
    }

    @Test
    fun playbackError_initialStateIsNull() {
        assertNull(playbackManager.playbackError.value)
    }

    @Test
    fun clearPlaybackError_clearsErrorState() {
        playbackManager.clearPlaybackError()
        assertNull(playbackManager.playbackError.value)
    }
}
