package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tests de integración entre [BiquadCoefficients] y [BiquadFilter]:
 * verifica que la magnitud medida empíricamente procesando señales sinusoidales
 * concuerda con la predicción analítica de [BiquadCoefficients.magnitudeDb]
 * para todos los tipos de filtro.
 */
class BiquadMagnitudeIntegrationTest {

    private val sampleRate = 48_000
    private val tolerance = 1.5f // dB — tolerancia para comparar análítico vs empírico

    /**
     * Mide empíricamente la ganancia del filtro a una frecuencia dada
     * pasando una sinusoide de duración suficiente y calculando el RMS de la señal estabilizada.
     */
    private fun measureEmpiricalGainDb(
        coeffs: BiquadCoefficients,
        freqHz: Float,
        sampleCount: Int = 2048,
        warmupSamples: Int = 512,
    ): Float {
        val filter = BiquadFilter(maxChannels = 1)
        filter.coefficients = coeffs

        var inputSumSq = 0.0
        var outputSumSq = 0.0

        for (n in 0 until sampleCount) {
            val sample = sin(2.0 * PI * freqHz / sampleRate * n).toFloat()
            val out = filter.processSample(sample, channel = 0)

            if (n >= warmupSamples) {
                inputSumSq += (sample * sample).toDouble()
                outputSumSq += (out * out).toDouble()
            }
        }

        if (inputSumSq <= 0.0 || outputSumSq <= 0.0) return 0f
        return (10.0 * kotlin.math.log10(outputSumSq / inputSumSq)).toFloat()
    }

    // ─── BELL ──────────────────────────────────────────────────────────────

    @Test
    fun bell_positiveGain_empiricalMatchesAnalytic() {
        val gainDb = 6f
        val freqHz = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL, frequencyHz = freqHz,
            sampleRate = sampleRate, gainDb = gainDb, q = 1.414f,
        )
        val empirical = measureEmpiricalGainDb(coeffs, freqHz)
        val analytic = coeffs.magnitudeDb(freqHz, sampleRate)
        assertEquals("Bell +6dB: empirical vs analytic", analytic, empirical, tolerance)
    }

    @Test
    fun bell_negativeGain_empiricalMatchesAnalytic() {
        val gainDb = -8f
        val freqHz = 2_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL, frequencyHz = freqHz,
            sampleRate = sampleRate, gainDb = gainDb, q = 2.0f,
        )
        val empirical = measureEmpiricalGainDb(coeffs, freqHz)
        val analytic = coeffs.magnitudeDb(freqHz, sampleRate)
        assertEquals("Bell -8dB: empirical vs analytic", analytic, empirical, tolerance)
    }

    @Test
    fun bell_centerFreq_gain_closeToDeclaredDb() {
        val gainDb = 6f
        val freqHz = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL, frequencyHz = freqHz,
            sampleRate = sampleRate, gainDb = gainDb, q = 1.414f,
        )
        val analytic = coeffs.magnitudeDb(freqHz, sampleRate)
        assertEquals("Bell center gain must equal declared +6dB", gainDb, analytic, 0.5f)
    }

    // ─── LOW_SHELF ─────────────────────────────────────────────────────────

    @Test
    fun lowShelf_positiveGain_boostsLowFreqButNotHighFreq() {
        val gainDb = 6f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.LOW_SHELF, frequencyHz = 200f,
            sampleRate = sampleRate, gainDb = gainDb,
        )
        val lowGain = coeffs.magnitudeDb(40f, sampleRate)   // well below shelf
        val highGain = coeffs.magnitudeDb(8_000f, sampleRate) // well above shelf

        assertTrue("Low shelf should boost low freq", lowGain > 2f)
        assertTrue("Low shelf should not significantly affect high freq: got $highGain dB", abs(highGain) < 1.5f)
    }

    @Test
    fun lowShelf_negativeGain_cutsLowFreqButNotHighFreq() {
        val gainDb = -6f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.LOW_SHELF, frequencyHz = 200f,
            sampleRate = sampleRate, gainDb = gainDb,
        )
        val lowGain = coeffs.magnitudeDb(40f, sampleRate)
        val highGain = coeffs.magnitudeDb(8_000f, sampleRate)

        assertTrue("Low shelf cut should attenuate low freq: $lowGain dB", lowGain < -2f)
        assertTrue("Low shelf cut should not affect high freq: $highGain dB", abs(highGain) < 1.5f)
    }

    // ─── HIGH_SHELF ────────────────────────────────────────────────────────

    @Test
    fun highShelf_positiveGain_boostsHighFreqButNotLowFreq() {
        val gainDb = 6f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.HIGH_SHELF, frequencyHz = 8_000f,
            sampleRate = sampleRate, gainDb = gainDb,
        )
        val lowGain = coeffs.magnitudeDb(100f, sampleRate)
        val highGain = coeffs.magnitudeDb(16_000f, sampleRate)

        assertTrue("High shelf should boost high freq: $highGain dB", highGain > 2f)
        assertTrue("High shelf should not affect low freq: $lowGain dB", abs(lowGain) < 1.5f)
    }

    // ─── LOW_PASS ──────────────────────────────────────────────────────────

    @Test
    fun lowPass_passesLowFreqAndAttenuatesHighFreq() {
        val cutoff = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.LOW_PASS, frequencyHz = cutoff,
            sampleRate = sampleRate,
        )
        val lowGain = coeffs.magnitudeDb(100f, sampleRate)   // well below cutoff
        val highGain = coeffs.magnitudeDb(10_000f, sampleRate) // well above cutoff

        assertTrue("Low-pass should pass 100Hz: $lowGain dB", abs(lowGain) < 3f)
        assertTrue("Low-pass should attenuate 10kHz: $highGain dB", highGain < -10f)
    }

    // ─── HIGH_PASS ─────────────────────────────────────────────────────────

    @Test
    fun highPass_passesHighFreqAndAttenuatesLowFreq() {
        val cutoff = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.HIGH_PASS, frequencyHz = cutoff,
            sampleRate = sampleRate,
        )
        val lowGain = coeffs.magnitudeDb(50f, sampleRate)    // well below cutoff
        val highGain = coeffs.magnitudeDb(10_000f, sampleRate) // well above cutoff

        assertTrue("High-pass should attenuate 50Hz: $lowGain dB", lowGain < -10f)
        assertTrue("High-pass should pass 10kHz: $highGain dB", abs(highGain) < 3f)
    }

    // ─── NOTCH ─────────────────────────────────────────────────────────────

    @Test
    fun notch_deeplyAttenuatesAtCenterFreq() {
        val centerFreq = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.NOTCH, frequencyHz = centerFreq,
            sampleRate = sampleRate, q = 4.0f,
        )
        val notchGain = coeffs.magnitudeDb(centerFreq, sampleRate)
        assertTrue("Notch should deeply attenuate at center freq: $notchGain dB", notchGain < -20f)
    }

    @Test
    fun notch_passesFreqsWellAwayFromCenter() {
        val centerFreq = 1_000f
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.NOTCH, frequencyHz = centerFreq,
            sampleRate = sampleRate, q = 4.0f,
        )
        val lowGain = coeffs.magnitudeDb(100f, sampleRate)
        val highGain = coeffs.magnitudeDb(10_000f, sampleRate)

        assertTrue("Notch should pass 100Hz (far from center): $lowGain dB", abs(lowGain) < 2f)
        assertTrue("Notch should pass 10kHz (far from center): $highGain dB", abs(highGain) < 2f)
    }

    // ─── BYPASS ────────────────────────────────────────────────────────────

    @Test
    fun bypass_coefficients_zeroGainAtAllFrequencies() {
        val coeffs = BiquadCoefficients.BYPASS
        val testFreqs = floatArrayOf(50f, 500f, 1_000f, 5_000f, 15_000f)
        for (freq in testFreqs) {
            val gain = coeffs.magnitudeDb(freq, sampleRate)
            assertEquals("BYPASS should produce 0dB at ${freq}Hz", 0f, gain, 0.001f)
        }
    }

    @Test
    fun zeroGainBell_returnsExactBypassCoefficients() {
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL, frequencyHz = 1000f,
            sampleRate = sampleRate, gainDb = 0.001f, // < 0.005 threshold
        )
        assertEquals("Near-zero gain must return BYPASS", BiquadCoefficients.BYPASS, coeffs)
    }
}
