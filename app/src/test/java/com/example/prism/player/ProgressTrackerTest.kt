package com.example.prism.player

import com.example.prism.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressTrackerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var progressTracker: ProgressTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        progressTracker = ProgressTracker(
            dispatchers = testDispatcherProvider,
            scope = testScope,
        )
    }

    @After
    fun tearDown() {
        progressTracker.release()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isZero() {
        assertEquals(0L, progressTracker.progressState.value.currentPosition)
        assertEquals(0L, progressTracker.progressState.value.duration)
    }

    @Test
    fun onSeek_updatesCurrentPositionImmediately() {
        progressTracker.onSeek(42000L)
        assertEquals(42000L, progressTracker.progressState.value.currentPosition)
    }

    @Test
    fun onDurationChanged_updatesDuration() {
        progressTracker.onDurationChanged(240000L)
        assertEquals(240000L, progressTracker.progressState.value.duration)
    }

    @Test
    fun onPositionDiscontinuity_updatesPosition() {
        progressTracker.onPositionDiscontinuity(15000L)
        assertEquals(15000L, progressTracker.progressState.value.currentPosition)
    }
}
