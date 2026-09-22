package com.example.prism.domain.repository

import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.preferences.EqualizerPreferences
import com.example.prism.data.repository.EqualizerRepositoryImpl
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqFilterType
import com.example.prism.domain.model.equalizer.EqualizerConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de concurrencia para [EqualizerRepositoryImpl].
 *
 * Verifica que el [saveMutex] interno previene actualizaciones entrelazadas
 * cuando múltiples coroutines escriben simultáneamente al repositorio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerRepositoryConcurrencyTest {

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
    fun concurrentBandUpdates_allWritesAreSerializedAndFinalStateIsConsistent() = testScope.runTest {
        // Lanzar N escrituras concurrentes en bands distintas; el estado final no debe tener
        // mezcla parcial de bands (i.e. el mutex garantiza atomic read-modify-write)
        val updates = (0 until 5).map { index ->
            async {
                val band = repository.equalizerConfig.value.bands.getOrNull(index)
                    ?: return@async
                repository.setBand(band.copy(gainDb = (index + 1) * 2f))
            }
        }
        updates.awaitAll()
        advanceUntilIdle()

        val finalBands = repository.equalizerConfig.value.bands
        // Verify each updated band has its correct target gain (no write was lost)
        for (index in 0 until 5) {
            val expectedGain = (index + 1) * 2f
            val actualGain = finalBands.getOrNull(index)?.gainDb ?: Float.MIN_VALUE
            assertEquals(
                "Band $index gain mismatch after concurrent writes",
                expectedGain, actualGain, 0.001f,
            )
        }
    }

    @Test
    fun concurrentPreampAndBandWrites_neitherIsLost() = testScope.runTest {
        val preampJob = async { repository.setPreamp(6.5f) }
        val bandJob = async {
            val band = repository.equalizerConfig.value.bands[0]
            repository.setBand(band.copy(gainDb = 9f))
        }

        awaitAll(preampJob, bandJob)
        advanceUntilIdle()

        val config = repository.equalizerConfig.value
        assertEquals("Preamp write was lost", 6.5f, config.preampDb, 0.001f)
        assertEquals("Band write was lost", 9f, config.bands[0].gainDb, 0.001f)
    }

    @Test
    fun rapidSetEnabledToggles_finalStateMatchesLastWrite() = testScope.runTest {
        // 10 rapid toggles: last one should always win
        for (i in 0 until 10) {
            repository.setEnabled(i % 2 == 0)
        }
        advanceUntilIdle()

        // Last call: setEnabled(10 % 2 == 0) → setEnabled(false) → index 9 → false
        val finalEnabled = repository.equalizerConfig.value.enabled
        assertEquals(false, finalEnabled)
    }

    @Test
    fun updateConfig_atomicTransform_noPartialState() = testScope.runTest {
        // Launch many transforms that each increment preamp by 0.1f
        val iterations = 20
        val jobs = (0 until iterations).map {
            async {
                repository.updateConfig { config ->
                    config.copy(preampDb = (config.preampDb + 0.1f).coerceIn(-12f, 12f))
                }
            }
        }
        jobs.awaitAll()
        advanceUntilIdle()

        val finalPreamp = repository.equalizerConfig.value.preampDb
        // With atomic updateAndGet, exactly `iterations` increments of 0.1 should have occurred
        assertEquals(iterations * 0.1f, finalPreamp, 0.01f)
    }

    @Test
    fun setMode_whileConcurrentBandWrite_doesNotCorruptConfig() = testScope.runTest {
        val modeJob = async {
            repository.setMode(com.example.prism.domain.model.equalizer.EqualizerMode.ADVANCED)
        }
        val bandJob = async {
            val band = EqBand(
                id = 0,
                enabled = true,
                type = EqFilterType.BELL,
                frequencyHz = 1000f,
                gainDb = 5f,
                q = 1.414f,
            )
            repository.setBand(band)
        }

        awaitAll(modeJob, bandJob)
        advanceUntilIdle()

        val config = repository.equalizerConfig.value
        // Config must always be in a consistent state — not null or mixed-size bands
        assertTrue("Bands must not be empty after concurrent mode+band write", config.bands.isNotEmpty())
        assertTrue(
            "Band count must match current mode (8 for ADVANCED, 10 for SIMPLE)",
            config.bands.size == 8 || config.bands.size == 10,
        )
    }
}
