package com.example.prism.player.effects

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqFilterType
import com.example.prism.domain.model.equalizer.EqualizerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests de rendimiento y regresión para [EqualizerAudioProcessor].
 *
 * Verifica:
 * - Modo bypass: los bytes de salida son idénticos a los de entrada (sin modificación).
 * - Modo bypass: no se introducen samples extra ni bytes de relleno.
 * - Float PCM: el EQ modifica el audio correctamente en formato flotante.
 * - Procesamiento con múltiples bandas: 10 bandas activas simultáneas.
 * - Limiter en Float: la señal permanece en el rango [-1f, 1f].
 * - Configuración con zero-gain: BYPASS automático no modifica el audio.
 * - Reset de filtros: los registros internos se limpian en flush/reset.
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION")
class EqualizerAudioProcessorPerformanceTest {

    private lateinit var processor: EqualizerAudioProcessor

    @Before
    fun setUp() {
        processor = EqualizerAudioProcessor()
    }

    // ─── Bypass correctness ────────────────────────────────────────────────

    @Test
    fun bypass_16Bit_outputBytesMatchInputBytesExactly() {
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val sampleCount = 512
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putShort((i * 37 % 32768).toShort()) // pseudo-random pattern
        }
        inputBuffer.flip()

        // Save expected bytes for comparison
        val expectedBytes = ByteArray(inputBuffer.remaining())
        inputBuffer.mark()
        inputBuffer.get(expectedBytes)
        inputBuffer.reset()

        processor.queueInput(inputBuffer)
        val output = processor.output

        assertEquals("Output byte count must match input", sampleCount * 2, output.remaining())
        val outputBytes = ByteArray(output.remaining())
        output.get(outputBytes)
        for (i in outputBytes.indices) {
            assertEquals("Byte $i differs in bypass mode", expectedBytes[i], outputBytes[i])
        }
    }

    @Test
    fun bypass_float_outputBytesMatchInputBytesExactly() {
        val format = AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        val sampleCount = 256
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putFloat((i * 0.001f) % 1f)
        }
        inputBuffer.flip()

        val expectedBytes = ByteArray(inputBuffer.remaining())
        inputBuffer.mark()
        inputBuffer.get(expectedBytes)
        inputBuffer.reset()

        processor.queueInput(inputBuffer)
        val output = processor.output

        assertEquals("Output byte count must match input in float bypass", sampleCount * 4, output.remaining())
        val outputBytes = ByteArray(output.remaining())
        output.get(outputBytes)
        for (i in outputBytes.indices) {
            assertEquals("Float byte $i differs in bypass mode", expectedBytes[i], outputBytes[i])
        }
    }

    // ─── Float PCM EQ modification ─────────────────────────────────────────

    @Test
    fun whenEnabled_floatPcmIsModifiedByEqualizer() {
        val format = AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        processor.setConfig(
            EqualizerConfig(
                enabled = true,
                preampDb = 0f,
                bands = listOf(
                    EqBand(id = 0, enabled = true, type = EqFilterType.BELL,
                        frequencyHz = 1000f, gainDb = 8f, q = 1.0f),
                ),
            )
        )

        val sampleCount = 200
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        for (n in 0 until sampleCount) {
            val t = n.toDouble() / 48000.0
            inputBuffer.putFloat((0.3f * kotlin.math.sin(2.0 * Math.PI * 1000.0 * t)).toFloat())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output

        assertEquals(sampleCount * 4, output.remaining())

        var maxIn = 0f
        var maxOut = 0f
        inputBuffer.rewind()
        for (n in 0 until sampleCount) {
            val inVal = kotlin.math.abs(inputBuffer.float)
            val outVal = kotlin.math.abs(output.float)
            if (n > 50) {
                maxIn = maxOf(maxIn, inVal)
                maxOut = maxOf(maxOut, outVal)
            }
        }
        assertTrue("Float output peak ($maxOut) should be amplified above input peak ($maxIn)", maxOut > maxIn)
    }

    // ─── Multi-band processing ─────────────────────────────────────────────

    @Test
    fun tenActiveBands_doesNotCrashAndProducesCorrectByteCount() {
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val frequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        val config = EqualizerConfig(
            enabled = true,
            preampDb = 0f,
            bands = frequencies.mapIndexed { idx, freq ->
                EqBand(id = idx, enabled = true, type = EqFilterType.BELL,
                    frequencyHz = freq, gainDb = 3f, q = 1.414f)
            },
        )
        processor.setConfig(config)

        val sampleCount = 1024
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putShort(10000.toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output

        assertEquals("10-band output must have same byte count as input", sampleCount * 2, output.remaining())
    }

    // ─── Limiter on Float PCM ──────────────────────────────────────────────

    @Test
    fun limiter_floatPcmStaysWithinNormalRange() {
        val format = AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        processor.setConfig(
            EqualizerConfig(
                enabled = true,
                preampDb = 12f,
                bands = listOf(
                    EqBand(id = 0, enabled = true, type = EqFilterType.BELL,
                        frequencyHz = 500f, gainDb = 12f),
                ),
                limiterEnabled = true,
            )
        )

        val sampleCount = 200
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putFloat(0.9f) // near full-scale
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output

        while (output.hasRemaining()) {
            val s = output.float
            assertTrue("Float output $s exceeds [-1.0, 1.0]", s >= -1.0f && s <= 1.0f)
        }
    }

    // ─── Zero-gain bypass optimization ────────────────────────────────────

    @Test
    fun zeroGainBands_bypassOptimizationDoesNotModifyAudio() {
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        processor.setConfig(
            EqualizerConfig(
                enabled = true,
                preampDb = 0f, // no preamp
                bands = listOf(
                    EqBand(id = 0, enabled = true, type = EqFilterType.BELL,
                        frequencyHz = 1000f, gainDb = 0.001f, q = 1.414f), // < 0.005 → BYPASS
                ),
            )
        )

        val sampleCount = 64
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            inputBuffer.putShort(20000.toShort())
        }
        inputBuffer.flip()

        val inputCopy = ByteArray(inputBuffer.remaining())
        inputBuffer.mark()
        inputBuffer.get(inputCopy)
        inputBuffer.reset()

        processor.queueInput(inputBuffer)
        val output = processor.output

        // Near-zero gain → BiquadCoefficients.BYPASS → samples remain within 1 LSB quantization precision
        assertEquals("Output size must match input", sampleCount * 2, output.remaining())
        inputBuffer.rewind()
        while (output.hasRemaining()) {
            val inVal = inputBuffer.short.toInt()
            val outVal = output.short.toInt()
            assertTrue("Sample out ($outVal) should equal in ($inVal) within 1 LSB", kotlin.math.abs(inVal - outVal) <= 1)
        }
    }

    // ─── Filter reset after flush ──────────────────────────────────────────

    @Test
    fun afterFlush_filterStateIsCleared_noResidualRinging() {
        val format = AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable a narrow high-Q filter (strong resonance)
        processor.setConfig(
            EqualizerConfig(
                enabled = true,
                preampDb = 0f,
                bands = listOf(
                    EqBand(id = 0, enabled = true, type = EqFilterType.BELL,
                        frequencyHz = 1000f, gainDb = 10f, q = 8.0f),
                ),
            )
        )

        // Feed a burst to excite resonance
        val burstCount = 100
        val burstBuffer = ByteBuffer.allocateDirect(burstCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until burstCount) {
            burstBuffer.putShort(30000.toShort())
        }
        burstBuffer.flip()
        processor.queueInput(burstBuffer)
        // Drain output
        processor.output

        // Now flush → should clear filter registers
        processor.flush()

        // Feed silence after flush; output should also be silence (no ringing)
        val silenceCount = 50
        val silenceBuffer = ByteBuffer.allocateDirect(silenceCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until silenceCount) {
            silenceBuffer.putShort(0)
        }
        silenceBuffer.flip()

        processor.queueInput(silenceBuffer)
        val output = processor.output

        var maxAbsOut = 0
        while (output.hasRemaining()) {
            maxAbsOut = maxOf(maxAbsOut, kotlin.math.abs(output.short.toInt()))
        }
        // After flush, filter state is zero so silence in → silence out
        assertEquals("Filter state should be cleared after flush: max output=$maxAbsOut", 0, maxAbsOut)
    }
}
