package com.example.prism.player.effects

import android.os.Bundle
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.CustomCommandDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EqualizerControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val configFlow = MutableStateFlow(EqualizerConfig())
    private val mockRepository = mockk<EqualizerRepository>(relaxed = true)
    private val mockDispatcher = mockk<CustomCommandDispatcher>(relaxed = true)

    private lateinit var controller: EqualizerController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockRepository.equalizerConfig } returns configFlow

        controller = EqualizerControllerImpl(
            equalizerRepository = mockRepository,
            commandDispatcher = mockDispatcher,
            scope = appScope,
            dispatchers = testDispatcherProvider,
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        controller.release()
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun repositoryConfigUpdates_areDispatchedAsCustomCommands() = runTest(testDispatcher) {
        io.mockk.clearMocks(mockDispatcher, answers = false)

        val updatedConfig = EqualizerConfig(enabled = true, preampDb = 3.0f)
        configFlow.value = updatedConfig
        advanceUntilIdle()

        val actionSlot = slot<String>()
        val bundleSlot = slot<Bundle>()

        verify {
            mockDispatcher.sendCustomCommand(capture(actionSlot), capture(bundleSlot))
        }

        assertEquals(EqualizerCommands.COMMAND_SET_EQ_CONFIG, actionSlot.captured)
        val jsonStr = bundleSlot.captured.getString(EqualizerCommands.EXTRA_CONFIG_JSON)
        assertTrue(jsonStr != null && jsonStr.contains("\"enabled\":true"))
    }

    @Test
    fun controllerMethods_delegateToRepository() = runTest(testDispatcher) {
        controller.setEnabled(true)
        controller.setPreamp(2.5f)
        controller.setMode(EqualizerMode.ADVANCED)
        controller.applyPreset("Rock")
        controller.reset()

        advanceUntilIdle()

        coVerify { mockRepository.setEnabled(true) }
        coVerify { mockRepository.setPreamp(2.5f) }
        coVerify { mockRepository.setMode(EqualizerMode.ADVANCED) }
        coVerify { mockRepository.applyPreset("Rock") }
        coVerify { mockRepository.reset() }
    }
}
