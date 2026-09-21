package com.example.prism.ui.player

import com.example.prism.data.entity.Song
import com.example.prism.domain.model.LyricsContent
import com.example.prism.domain.model.LyricsLine
import com.example.prism.domain.model.LyricsSource
import com.example.prism.domain.model.SongLyrics
import com.example.prism.domain.repository.LyricsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private lateinit var viewModel: LyricsViewModel

    private val testSong = Song(
        id = "101",
        title = "Test Track",
        artist = "Test Artist",
        album = "Test Album",
        albumId = 1L,
        mediaUri = "content://media/101",
        artworkUri = "",
        duration = 200000L,
        dateModified = 5000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LyricsViewModel(lyricsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadLyrics_whenSongProvided_fetchesAndUpdatesState() = runTest(testDispatcher) {
        val expected = SongLyrics(
            songId = testSong.id,
            lrcLyrics = LyricsContent(
                source = LyricsSource.LRC_FILE,
                isSynced = true,
                lines = listOf(LyricsLine(1000L, "Sample")),
            ),
            selectedSource = LyricsSource.LRC_FILE,
        )
        coEvery { lyricsRepository.getLyrics(testSong) } returns expected

        viewModel.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        val actual = viewModel.songLyrics.value
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun selectSource_switchesBetweenAvailableSources() = runTest(testDispatcher) {
        val initialLyrics = SongLyrics(
            songId = testSong.id,
            embeddedLyrics = LyricsContent(LyricsSource.EMBEDDED, false, listOf(LyricsLine(-1L, "Embedded"))),
            lrcLyrics = LyricsContent(LyricsSource.LRC_FILE, true, listOf(LyricsLine(1000L, "LRC"))),
            selectedSource = LyricsSource.LRC_FILE,
        )
        coEvery { lyricsRepository.getLyrics(testSong) } returns initialLyrics

        viewModel.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LyricsSource.LRC_FILE, viewModel.songLyrics.value?.selectedSource)

        viewModel.selectSource(LyricsSource.EMBEDDED)
        assertEquals(LyricsSource.EMBEDDED, viewModel.songLyrics.value?.selectedSource)
        assertEquals("Embedded", viewModel.songLyrics.value?.activeLyrics?.lines?.first()?.text)
    }

    @Test
    fun loadLyrics_nullSong_clearsState() = runTest(testDispatcher) {
        viewModel.loadLyrics(null)
        assertNull(viewModel.songLyrics.value)
    }
}
