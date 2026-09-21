package com.example.prism.ui.library

import app.cash.turbine.test
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.entity.Song
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.domain.repository.PlaylistRepository
import com.example.prism.domain.repository.ScannerRepository
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
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val scannerRepository: ScannerRepository = mockk(relaxed = true)

    private fun createViewModel() = LibraryViewModel(
        libraryRepository = libraryRepository,
        playlistRepository = playlistRepository,
        scannerRepository = scannerRepository,
        dispatchers = testDispatcherProvider,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playlistRepository.playlistsWithCountsFlow } returns flowOf(emptyList())
        every { playlistRepository.getFavoriteSongIds() } returns flowOf(emptySet())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSearchQuery_updatesSearchQueryFlow() = runTest {
        val viewModel = createViewModel()

        viewModel.searchQuery.test {
            assertEquals("", awaitItem())

            viewModel.setSearchQuery("Coldplay")
            assertEquals("Coldplay", awaitItem())
        }
    }

    @Test
    fun toggleFavorite_delegatesToRepository() = runTest {
        val viewModel = createViewModel()
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

        coVerify(exactly = 1) { playlistRepository.toggleFavorite(song) }
    }

    @Test
    fun createPlaylist_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.createPlaylist("Rock Classics")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepository.createPlaylist("Rock Classics") }
    }

    @Test
    fun loadLocalSongs_callsTriggerScan() = runTest {
        val viewModel = createViewModel()

        viewModel.loadLocalSongs()

        coVerify(exactly = 1) { scannerRepository.triggerScan() }
    }

    @Test
    fun updateSongArtwork_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.updateSongArtwork("song-123", "https://example.com/art.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { libraryRepository.updateSongArtwork("song-123", "https://example.com/art.jpg") }
    }

    @Test
    fun updateAlbumCover_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.updateAlbumCover(42L, "content://media/picker/image/42")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { libraryRepository.updateAlbumCover(42L, "content://media/picker/image/42") }
    }
}
