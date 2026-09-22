package com.example.prism.ui.equalizer

import com.example.prism.core.util.DispatcherProvider
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.model.equalizer.EqualizerPresets
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.effects.EqualizerController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val configFlow = MutableStateFlow(EqualizerConfig(enabled = false, preampDb = 0f))
    private val mockRepository = mockk<EqualizerRepository>(relaxed = true)
    private val mockController = mockk<EqualizerController>(relaxed = true)

    private lateinit var viewModel: EqualizerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockRepository.equalizerConfig } returns configFlow

        viewModel = EqualizerViewModel(
            repository = mockRepository,
            controller = mockController,
            dispatchers = testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsRepositoryConfig() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.enabled)
        assertEquals(0f, state.preampDb)
        assertEquals("Flat", state.selectedPreset)
        assertEquals(10, state.bands.size)
    }

    @Test
    fun toggleEnabled_invertsStateAndCallsController() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.toggleEnabled()
        assertTrue(viewModel.uiState.value.enabled)

        advanceUntilIdle()
        coVerify(exactly = 1) { mockController.setEnabled(true) }

        viewModel.toggleEnabled()
        assertFalse(viewModel.uiState.value.enabled)

        advanceUntilIdle()
        coVerify(exactly = 1) { mockController.setEnabled(false) }
    }

    @Test
    fun setMode_updatesStateAndCallsController() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setMode(EqualizerMode.ADVANCED)
        assertEquals(EqualizerMode.ADVANCED, viewModel.uiState.value.mode)

        advanceUntilIdle()
        coVerify(exactly = 1) { mockController.setMode(EqualizerMode.ADVANCED) }
    }

    @Test
    fun setPreamp_debouncesCallsToController() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setPreamp(3.5f)
        assertEquals(3.5f, viewModel.uiState.value.preampDb)

        // Advance 50ms (less than debounce 100ms)
        advanceTimeBy(50)
        coVerify(exactly = 0) { mockController.setPreamp(any()) }

        // Advance past debounce
        advanceTimeBy(60)
        coVerify(exactly = 1) { mockController.setPreamp(3.5f) }
    }

    @Test
    fun setBandGain_updatesBandGainAndClearsPreset_withDebouncedSync() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setBandGain(0, 4.0f)
        assertEquals(4.0f, viewModel.uiState.value.bands[0].gainDb)
        assertNull(viewModel.uiState.value.selectedPreset)

        advanceTimeBy(110)
        coVerify(exactly = 1) { mockController.setBands(match { it[0].gainDb == 4.0f }) }
    }

    @Test
    fun selectPreset_updatesGainsAndCallsController() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.selectPreset("Rock")
        assertEquals("Rock", viewModel.uiState.value.selectedPreset)

        val rockGains = EqualizerPresets.getGains("Rock")!!
        viewModel.uiState.value.bands.forEachIndexed { index, band ->
            assertEquals(rockGains[index], band.gainDb, 0.001f)
        }

        advanceUntilIdle()
        coVerify(exactly = 1) { mockController.applyPreset("Rock") }
    }

    @Test
    fun reset_delegatesToController() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.reset()

        advanceUntilIdle()
        coVerify(exactly = 1) { mockController.reset() }
    }
}
