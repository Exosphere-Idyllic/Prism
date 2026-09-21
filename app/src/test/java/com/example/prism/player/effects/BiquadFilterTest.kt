package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadFilterTest {

    @Test
    fun bypassFilter_processesSamplesUnchanged() {
        val filter = BiquadFilter(coefficients = BiquadCoefficients.BYPASS, maxChannels = 2)

        val inputSamples = floatArrayOf(0.1f, -0.5f, 0.8f, -0.2f, 0.0f)
        for (sample in inputSamples) {
            val out = filter.processSample(sample, channel = 0)
            assertEquals(sample, out, 0.0001f)
        }
    }

    @Test
    fun peakingFilter_boostsSignalAmplitude() {
        val sampleRate = 48000
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL,
            frequencyHz = 1000f,
            sampleRate = sampleRate,
            gainDb = 6.0f,
            q = 1.0f,
        )
        val filter = BiquadFilter(coefficients = coeffs, maxChannels = 1)

        // Feed a 1 kHz sine wave into the filter and verify peak output is higher than input
        var maxInput = 0f
        var maxOutput = 0f

        val numSamples = 480 // 10 ms
        for (n in 0 until numSamples) {
            val t = n.toDouble() / sampleRate
            val input = (0.5 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * t)).toFloat()
            val output = filter.processSample(input, channel = 0)

            if (n > 100) { // allow transient to settle
                maxInput = maxOf(maxInput, kotlin.math.abs(input))
                maxOutput = maxOf(maxOutput, kotlin.math.abs(output))
            }
        }

        // 6 dB boost is roughly a factor of 2 in amplitude (0.5 -> ~1.0)
        assertTrue("Max output ($maxOutput) should exceed max input ($maxInput)", maxOutput > maxInput * 1.5f)
    }

    @Test
    fun reset_clearsDelayStateRegisters() {
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL,
            frequencyHz = 500f,
            sampleRate = 44100,
            gainDb = 6.0f,
        )
        val filter = BiquadFilter(coefficients = coeffs, maxChannels = 1)

        // Excite filter with an impulse
        filter.processSample(1.0f, channel = 0)
        filter.processSample(0.0f, channel = 0)

        // Reset filter
        filter.reset()

        // After reset, 0.0 input must immediately yield 0.0 output (no ring-down tail)
        val outAfterReset = filter.processSample(0.0f, channel = 0)
        assertEquals(0.0f, outAfterReset, 0.00001f)
    }

    @Test
    fun stereoChannels_areProcessedIndependently() {
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.LOW_SHELF,
            frequencyHz = 200f,
            sampleRate = 44100,
            gainDb = 6.0f,
        )
        val filter = BiquadFilter(coefficients = coeffs, maxChannels = 2)

        // Excite channel 0 only
        filter.processSample(1.0f, channel = 0)

        // Channel 1 should still have zero state registers
        val ch1Out = filter.processSample(0.0f, channel = 1)
        assertEquals(0.0f, ch1Out, 0.00001f)
    }
}
