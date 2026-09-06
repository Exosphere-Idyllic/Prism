package com.example.prism.ui.library

import app.cash.turbine.test
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.domain.repository.MusicRepository
import com.example.prism.data.entity.Song
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }
    private val repository: MusicRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.playlistsWithCountsFlow } returns flowOf(emptyList())
        every { repository.getFavoriteSongIds() } returns flowOf(emptySet())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSearchQuery_updatesSearchQueryFlow() = runTest {
        val viewModel = LibraryViewModel(repository, dispatchers = testDispatcherProvider)

        viewModel.searchQuery.test {
            assertEquals("", awaitItem())

            viewModel.setSearchQuery("Coldplay")
            assertEquals("Coldplay", awaitItem())
        }
    }

    @Test
    fun toggleFavorite_delegatesToRepository() = runTest {
        val viewModel = LibraryViewModel(repository, dispatchers = testDispatcherProvider)
        val song = Song(
            id = "1",
            title = "Viva La Vida",
            artist = "Coldplay",
            album = "Viva La Vida",
            albumId = 1L,
            mediaUri = "content://media/1",
            artworkUri = "",
            duration = 240000L,
            dateModified = 1000L
        )

        viewModel.toggleFavorite(song)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.toggleFavorite(song) }
    }

    @Test
    fun createPlaylist_delegatesToRepository() = runTest {
        val viewModel = LibraryViewModel(repository, dispatchers = testDispatcherProvider)

        viewModel.createPlaylist("Rock Classics")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.createPlaylist("Rock Classics") }
    }

    @Test
    fun loadLocalSongs_callsTriggerScan() = runTest {
        val viewModel = LibraryViewModel(repository, dispatchers = testDispatcherProvider)

        viewModel.loadLocalSongs()

        coVerify(exactly = 1) { repository.triggerScan() }
    }
}
