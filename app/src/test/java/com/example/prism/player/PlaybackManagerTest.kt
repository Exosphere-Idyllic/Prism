package com.example.prism.player

import android.app.Application
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            dispatchers = testDispatcherProvider,
            scope = testScope
        )
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
    }

    @Test
    fun seekTo_updatesProgressStateImmediately() {
        playbackManager.seekTo(45000L)

        assertEquals(45000L, playbackManager.progressState.value.currentPosition)
    }

    @Test
    fun playSong_whenNotConnected_queuesPendingPlay() = runTest {
        val song = createSong("1")
        coEvery { repository.getAllSongs() } returns listOf(song)

        playbackManager.playSong(song)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(playbackManager.isPlayingState.value)
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
}
