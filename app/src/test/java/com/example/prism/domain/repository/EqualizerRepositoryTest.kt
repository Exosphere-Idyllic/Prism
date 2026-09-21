package com.example.prism.domain.repository

import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.preferences.EqualizerPreferences
import com.example.prism.data.repository.EqualizerRepositoryImpl
import com.example.prism.domain.model.equalizer.EqFilterType
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class EqualizerRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val mockPreferences = mockk<EqualizerPreferences>(relaxed = true)
    private lateinit var repository: EqualizerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockPreferences.equalizerConfigFlow } returns flowOf(EqualizerConfig())
        repository = EqualizerRepositoryImpl(
            preferences = mockPreferences,
            scope = testScope,
            dispatchers = testDispatcherProvider,
        )
        testScope.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_matchesDefaultConfig() = testScope.runTest {
        val config = repository.equalizerConfig.value
        assertFalse(config.enabled)
        assertEquals(EqualizerMode.SIMPLE, config.mode)
        assertEquals(0f, config.preampDb, 0.001f)
        assertEquals("Flat", config.selectedPreset)
        assertEquals(10, config.bands.size)
        assertTrue(config.limiterEnabled)
    }

    @Test
    fun setEnabled_updatesStateAndSavesToPreferences() = testScope.runTest {
        repository.setEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.equalizerConfig.value.enabled)
        coVerify { mockPreferences.saveEqualizerConfig(match { it.enabled }) }
    }

    @Test
    fun setPreamp_updatesPreampDb() = testScope.runTest {
        repository.setPreamp(3.5f)
        advanceUntilIdle()

        assertEquals(3.5f, repository.equalizerConfig.value.preampDb, 0.001f)
    }

    @Test
    fun setBand_updatesBandAndSetsPresetToCustom() = testScope.runTest {
        val originalBand = repository.equalizerConfig.value.bands[2]
        val modifiedBand = originalBand.copy(gainDb = 4.0f)

        repository.setBand(modifiedBand)
        advanceUntilIdle()

        val updated = repository.equalizerConfig.value
        assertEquals("Custom", updated.selectedPreset)
        assertEquals(4.0f, updated.bands[2].gainDb, 0.001f)
    }

    @Test
    fun applyPreset_updatesAllBandsAndPresetName() = testScope.runTest {
        repository.applyPreset("Rock")
        advanceUntilIdle()

        val updated = repository.equalizerConfig.value
        assertEquals("Rock", updated.selectedPreset)
        assertEquals(4.5f, updated.bands[0].gainDb, 0.001f)
        assertEquals(3.0f, updated.bands[1].gainDb, 0.001f)
        assertEquals(-1.5f, updated.bands[2].gainDb, 0.001f)
    }

    @Test
    fun setMode_switchesBetweenSimpleAndAdvanced() = testScope.runTest {
        repository.setMode(EqualizerMode.ADVANCED)
        advanceUntilIdle()

        val advConfig = repository.equalizerConfig.value
        assertEquals(EqualizerMode.ADVANCED, advConfig.mode)
        assertEquals(8, advConfig.bands.size)
        assertNull(advConfig.selectedPreset)

        repository.setMode(EqualizerMode.SIMPLE)
        advanceUntilIdle()

        val simpleConfig = repository.equalizerConfig.value
        assertEquals(EqualizerMode.SIMPLE, simpleConfig.mode)
        assertEquals(10, simpleConfig.bands.size)
        assertEquals("Flat", simpleConfig.selectedPreset)
    }

    @Test
    fun reset_restoresDefaultBandsAndPreamp() = testScope.runTest {
        repository.setPreamp(5.0f)
        repository.applyPreset("Bass Boost")
        advanceUntilIdle()

        repository.reset()
        advanceUntilIdle()

        val resetConfig = repository.equalizerConfig.value
        assertEquals(0f, resetConfig.preampDb, 0.001f)
        assertEquals("Flat", resetConfig.selectedPreset)
        assertTrue(resetConfig.bands.all { it.gainDb == 0f })
    }
}
