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

    @Test
    fun selectBand_updatesSelectedBandId() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.selectBand(3)
        assertEquals(3, viewModel.uiState.value.selectedBandId)
    }

    @Test
    fun setBandFrequency_updatesFrequencyAndDebouncesSync() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setBandFrequency(1, 800f)
        assertEquals(800f, viewModel.uiState.value.bands[1].frequencyHz)

        advanceTimeBy(110)
        coVerify(exactly = 1) { mockController.setBands(match { it[1].frequencyHz == 800f }) }
    }

    @Test
    fun setBandQ_updatesQAndDebouncesSync() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setBandQ(2, 2.5f)
        assertEquals(2.5f, viewModel.uiState.value.bands[2].q)

        advanceTimeBy(110)
        coVerify(exactly = 1) { mockController.setBands(match { it[2].q == 2.5f }) }
    }

    @Test
    fun setBandFilterType_updatesFilterTypeAndDebouncesSync() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setBandFilterType(0, com.example.prism.domain.model.equalizer.EqFilterType.LOW_PASS)
        assertEquals(com.example.prism.domain.model.equalizer.EqFilterType.LOW_PASS, viewModel.uiState.value.bands[0].type)

        advanceTimeBy(110)
        coVerify(exactly = 1) { mockController.setBands(match { it[0].type == com.example.prism.domain.model.equalizer.EqFilterType.LOW_PASS }) }
    }

    @Test
    fun updateBandParametric_updatesFrequencyAndGainTogether() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateBandParametric(4, 2500f, 6.0f)
        val band = viewModel.uiState.value.bands[4]
        assertEquals(2500f, band.frequencyHz)
        assertEquals(6.0f, band.gainDb)

        advanceTimeBy(110)
        coVerify(exactly = 1) {
            mockController.setBands(match {
                it[4].frequencyHz == 2500f && it[4].gainDb == 6.0f
            })
        }
    }
}
