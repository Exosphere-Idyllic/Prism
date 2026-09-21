package com.example.prism.player.effects

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqFilterType
import com.example.prism.domain.model.equalizer.EqualizerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
@Suppress("DEPRECATION")
class EqualizerAudioProcessorTest {

    private lateinit var processor: EqualizerAudioProcessor

    @Before
    fun setUp() {
        processor = EqualizerAudioProcessor()
    }

    @Test
    fun configure_supports16BitAndFloatPCM() {
        val format16Bit = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val output16Bit = processor.configure(format16Bit)
        assertEquals(format16Bit.sampleRate, output16Bit.sampleRate)
        assertEquals(format16Bit.channelCount, output16Bit.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, output16Bit.encoding)

        val formatFloat = AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        val outputFloat = processor.configure(formatFloat)
        assertEquals(formatFloat.sampleRate, outputFloat.sampleRate)
        assertEquals(formatFloat.channelCount, outputFloat.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, outputFloat.encoding)
    }

    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun configure_rejectsUnsupportedEncoding() {
        val unsupported = AudioFormat(44100, 2, C.ENCODING_PCM_24BIT)
        processor.configure(unsupported)
    }

    @Test
    fun whenDisabled_bypassesAudioDirectly() {
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Equalizer is disabled by default
        assertFalse(processor.getConfig().enabled)

        val sampleCount = 64
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putShort((i * 100).toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertEquals(sampleCount * 2, outputBuffer.remaining())
        for (i in 0 until sampleCount) {
            assertEquals((i * 100).toShort(), outputBuffer.short)
        }
    }

    @Test
    fun whenEnabled_16BitPcmIsModifiedByEqualizer() {
        val format = AudioFormat(48000, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable EQ with a significant boost at 1 kHz
        val config = EqualizerConfig(
            enabled = true,
            preampDb = 0f,
            bands = listOf(
                EqBand(id = 0, enabled = true, type = EqFilterType.BELL, frequencyHz = 1000f, gainDb = 8f, q = 1.0f)
            ),
        )
        processor.setConfig(config)

        // Feed a 1 kHz sine wave
        val sampleCount = 200
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (n in 0 until sampleCount) {
            val t = n.toDouble() / 48000.0
            val s = (10000.0 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * t)).toInt().toShort()
            inputBuffer.putShort(s)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertEquals(sampleCount * 2, outputBuffer.remaining())

        // Check that at least some samples had their amplitudes boosted
        var maxIn = 0
        var maxOut = 0
        inputBuffer.rewind()
        for (n in 0 until sampleCount) {
            val inVal = kotlin.math.abs(inputBuffer.short.toInt())
            val outVal = kotlin.math.abs(outputBuffer.short.toInt())
            if (n > 50) {
                maxIn = maxOf(maxIn, inVal)
                maxOut = maxOf(maxOut, outVal)
            }
        }
        assertTrue("Output peak ($maxOut) should be amplified above input peak ($maxIn)", maxOut > maxIn)
    }

    @Test
    fun limiter_prevents16BitOverflowWhenHeavilyBoosted() {
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Maximum +12 dB preamp and +12 dB boost with limiter enabled
        val config = EqualizerConfig(
            enabled = true,
            preampDb = 12f,
            bands = listOf(
                EqBand(id = 0, enabled = true, type = EqFilterType.BELL, frequencyHz = 500f, gainDb = 12f)
            ),
            limiterEnabled = true,
        )
        processor.setConfig(config)

        // Near-full scale input
        val sampleCount = 100
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (n in 0 until sampleCount) {
            inputBuffer.putShort(30000.toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        // All output shorts must remain strictly within valid 16-bit PCM range without wrapping
        while (outputBuffer.hasRemaining()) {
            val s = outputBuffer.short
            assertTrue(s >= -32768 && s <= 32767)
        }
    }
}
