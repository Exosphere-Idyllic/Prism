package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadCoefficientsTest {

    @Test
    fun zeroGain_returnsBypassCoefficients() {
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL,
            frequencyHz = 1000f,
            sampleRate = 44100,
            gainDb = 0f,
            q = 1.414f,
        )

        assertEquals(BiquadCoefficients.BYPASS, coeffs)
        assertEquals(0f, coeffs.magnitudeDb(1000f, 44100), 0.001f)
    }

    @Test
    fun bellFilter_atCenterFrequency_matchesTargetGain() {
        val sampleRate = 48000
        val centerFreq = 1000f
        val targetGain = 6.0f

        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL,
            frequencyHz = centerFreq,
            sampleRate = sampleRate,
            gainDb = targetGain,
            q = 1.414f,
        )

        // At center frequency, the magnitude should match target gain closely
        val centerMag = coeffs.magnitudeDb(centerFreq, sampleRate)
        assertEquals(targetGain, centerMag, 0.2f)

        // At frequencies far from center (e.g. 50 Hz and 15 kHz), response approaches 0 dB
        val lowMag = coeffs.magnitudeDb(50f, sampleRate)
        val highMag = coeffs.magnitudeDb(15000f, sampleRate)
        assertEquals(0f, lowMag, 0.5f)
        assertEquals(0f, highMag, 0.5f)
    }

    @Test
    fun lowShelf_boostsLowFrequencies_andLeavesHighFrequenciesFlat() {
        val sampleRate = 44100
        val cutoffFreq = 250f
        val targetGain = 5.0f

        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.LOW_SHELF,
            frequencyHz = cutoffFreq,
            sampleRate = sampleRate,
            gainDb = targetGain,
            q = 0.707f,
        )

        val lowMag = coeffs.magnitudeDb(30f, sampleRate)
        val highMag = coeffs.magnitudeDb(10000f, sampleRate)

        assertEquals(targetGain, lowMag, 0.5f)
        assertEquals(0f, highMag, 0.5f)
    }

    @Test
    fun highShelf_boostsHighFrequencies_andLeavesLowFrequenciesFlat() {
        val sampleRate = 44100
        val cutoffFreq = 4000f
        val targetGain = 4.0f

        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.HIGH_SHELF,
            frequencyHz = cutoffFreq,
            sampleRate = sampleRate,
            gainDb = targetGain,
            q = 0.707f,
        )

        val lowMag = coeffs.magnitudeDb(100f, sampleRate)
        val highMag = coeffs.magnitudeDb(16000f, sampleRate)

        assertEquals(0f, lowMag, 0.5f)
        assertEquals(targetGain, highMag, 0.5f)
    }

    @Test
    fun higherQ_producesNarrowerBandwidth() {
        val sampleRate = 48000
        val centerFreq = 1000f
        val gain = 6.0f

        val narrow = BiquadCoefficients.calculate(EqFilterType.BELL, centerFreq, sampleRate, gain, q = 5.0f)
        val wide = BiquadCoefficients.calculate(EqFilterType.BELL, centerFreq, sampleRate, gain, q = 0.5f)

        // At 500 Hz (half an octave away from 1 kHz):
        val narrowMag = narrow.magnitudeDb(500f, sampleRate)
        val wideMag = wide.magnitudeDb(500f, sampleRate)

        // The wide filter retains significantly more boost at 500 Hz than the narrow filter
        assertTrue("Wide filter magnitude ($wideMag) should be greater than narrow filter magnitude ($narrowMag)", wideMag > narrowMag)
    }

    @Test
    fun frequencyNearNyquist_isSafelyClamped() {
        val sampleRate = 44100
        // Request frequency at or above Nyquist (22050 Hz)
        val coeffs = BiquadCoefficients.calculate(
            type = EqFilterType.BELL,
            frequencyHz = 30000f,
            sampleRate = sampleRate,
            gainDb = 3.0f,
        )

        // Should not produce NaNs or Infinities
        assertTrue(coeffs.b0.isFinite())
        assertTrue(coeffs.b1.isFinite())
        assertTrue(coeffs.b2.isFinite())
        assertTrue(coeffs.a1.isFinite())
        assertTrue(coeffs.a2.isFinite())
    }
}
