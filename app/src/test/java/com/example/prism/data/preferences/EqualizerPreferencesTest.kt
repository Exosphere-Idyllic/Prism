package com.example.prism.data.preferences

import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqFilterType
import com.example.prism.domain.model.equalizer.EqualizerConfig
import com.example.prism.domain.model.equalizer.EqualizerMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPreferencesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun equalizerConfig_serializationRoundTrip_preservesAllFields() {
        val original = EqualizerConfig(
            enabled = true,
            mode = EqualizerMode.ADVANCED,
            preampDb = -2.5f,
            selectedPreset = "Custom",
            bands = listOf(
                EqBand(
                    id = 0,
                    enabled = true,
                    type = EqFilterType.LOW_SHELF,
                    frequencyHz = 80f,
                    gainDb = 3.5f,
                    q = 0.707f,
                ),
                EqBand(
                    id = 1,
                    enabled = false,
                    type = EqFilterType.NOTCH,
                    frequencyHz = 1000f,
                    gainDb = -6.0f,
                    q = 5.0f,
                )
            ),
            limiterEnabled = false,
        )

        val serialized = json.encodeToString(EqualizerConfig.serializer(), original)
        val deserialized = json.decodeFromString(EqualizerConfig.serializer(), serialized)

        assertEquals(original, deserialized)
        assertTrue(deserialized.enabled)
        assertEquals(EqualizerMode.ADVANCED, deserialized.mode)
        assertEquals(-2.5f, deserialized.preampDb, 0.001f)
        assertEquals("Custom", deserialized.selectedPreset)
        assertEquals(2, deserialized.bands.size)
        assertEquals(EqFilterType.NOTCH, deserialized.bands[1].type)
        assertEquals(5.0f, deserialized.bands[1].q, 0.001f)
    }

    @Test
    fun emptyOrIncompleteJson_fallsBackGracefullyWithDefaults() {
        val emptyObjectJson = "{}"
        val deserialized = json.decodeFromString(EqualizerConfig.serializer(), emptyObjectJson)

        assertEquals(EqualizerMode.SIMPLE, deserialized.mode)
        assertEquals(0f, deserialized.preampDb, 0.001f)
        assertEquals("Flat", deserialized.selectedPreset)
        assertEquals(10, deserialized.bands.size)
    }
}
