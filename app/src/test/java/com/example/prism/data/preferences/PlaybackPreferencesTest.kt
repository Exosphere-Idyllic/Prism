package com.example.prism.data.preferences

import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackPreferencesTest {

    private lateinit var preferences: PlaybackPreferences

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        preferences = PlaybackPreferences(context)
    }

    @Test
    fun defaultValues_areSensible() = runTest {
        assertFalse(preferences.getShuffleEnabled())
        assertEquals(Player.REPEAT_MODE_OFF, preferences.getRepeatMode())
    }

    @Test
    fun setShuffleEnabled_persistsAndUpdates() = runTest {
        preferences.setShuffleEnabled(true)
        assertTrue(preferences.getShuffleEnabled())

        preferences.setShuffleEnabled(false)
        assertFalse(preferences.getShuffleEnabled())
    }

    @Test
    fun setRepeatMode_persistsAndUpdates() = runTest {
        preferences.setRepeatMode(Player.REPEAT_MODE_ALL)
        assertEquals(Player.REPEAT_MODE_ALL, preferences.getRepeatMode())

        preferences.setRepeatMode(Player.REPEAT_MODE_ONE)
        assertEquals(Player.REPEAT_MODE_ONE, preferences.getRepeatMode())

        preferences.setRepeatMode(Player.REPEAT_MODE_OFF)
        assertEquals(Player.REPEAT_MODE_OFF, preferences.getRepeatMode())
    }
}
