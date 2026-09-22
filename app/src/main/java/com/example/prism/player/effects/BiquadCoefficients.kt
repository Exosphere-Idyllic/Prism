package com.example.prism.player.effects

import com.example.prism.domain.model.equalizer.EqFilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct Form II biquad coefficients normalized such that a0 = 1.0.
 *
 * H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
 *
 * Formulas based on Robert Bristow-Johnson's Audio EQ Cookbook.
 */
data class BiquadCoefficients(
    val b0: Float = 1f,
    val b1: Float = 0f,
    val b2: Float = 0f,
    val a1: Float = 0f,
    val a2: Float = 0f,
) {
    companion object {
        val BYPASS = BiquadCoefficients(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)

        /**
         * Computes normalized biquad coefficients for the given filter parameters.
         *
         * @param type The [EqFilterType] (BELL, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH).
         * @param frequencyHz Center / cutoff frequency in Hertz.
         * @param sampleRate Sampling rate of the audio stream in Hertz.
         * @param gainDb Gain in decibels (used for Bell and Shelf filters).
         * @param q Quality factor Q (controls filter bandwidth/resonance).
         */
        fun calculate(
            type: EqFilterType,
            frequencyHz: Float,
            sampleRate: Int,
            gainDb: Float = 0f,
            q: Float = 1.414f,
        ): BiquadCoefficients {
            if (sampleRate <= 0) return BYPASS

            // Clamp frequency strictly below Nyquist (Fs / 2) to prevent numerical instability
            val nyquist = sampleRate * 0.5f
            val safeFreq = frequencyHz.coerceIn(10f, nyquist * 0.98f)
            val safeQ = q.coerceIn(0.1f, 50f)

            // If gain is essentially 0 dB for peaking/shelf, it's a bypass
            if ((type == EqFilterType.BELL || type == EqFilterType.LOW_SHELF || type == EqFilterType.HIGH_SHELF) &&
                kotlin.math.abs(gainDb) < 0.005f
            ) {
                return BYPASS
            }

            val omega = (2.0 * PI * safeFreq / sampleRate)
            val cosOmega = cos(omega)
            val sinOmega = sin(omega)
            val alpha = sinOmega / (2.0 * safeQ)
            val aLinear = 10.0.pow(gainDb / 40.0) // A = 10^(gain/40) = sqrt(10^(gain/20))

            var b0: Double
            var b1: Double
            var b2: Double
            var a0: Double
            var a1: Double
            var a2: Double

            when (type) {
                EqFilterType.BELL -> {
                    // Peaking EQ
                    b0 = 1.0 + alpha * aLinear
                    b1 = -2.0 * cosOmega
                    b2 = 1.0 - alpha * aLinear
                    a0 = 1.0 + alpha / aLinear
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - alpha / aLinear
                }
                EqFilterType.LOW_SHELF -> {
                    val sqrtA = sqrt(aLinear)
                    val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                    b0 = aLinear * ((aLinear + 1.0) - (aLinear - 1.0) * cosOmega + twoSqrtAAlpha)
                    b1 = 2.0 * aLinear * ((aLinear - 1.0) - (aLinear + 1.0) * cosOmega)
                    b2 = aLinear * ((aLinear + 1.0) - (aLinear - 1.0) * cosOmega - twoSqrtAAlpha)
                    a0 = (aLinear + 1.0) + (aLinear - 1.0) * cosOmega + twoSqrtAAlpha
                    a1 = -2.0 * ((aLinear - 1.0) + (aLinear + 1.0) * cosOmega)
                    a2 = (aLinear + 1.0) + (aLinear - 1.0) * cosOmega - twoSqrtAAlpha
                }
                EqFilterType.HIGH_SHELF -> {
                    val sqrtA = sqrt(aLinear)
                    val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                    b0 = aLinear * ((aLinear + 1.0) + (aLinear - 1.0) * cosOmega + twoSqrtAAlpha)
                    b1 = -2.0 * aLinear * ((aLinear - 1.0) + (aLinear + 1.0) * cosOmega)
                    b2 = aLinear * ((aLinear + 1.0) + (aLinear - 1.0) * cosOmega - twoSqrtAAlpha)
                    a0 = (aLinear + 1.0) - (aLinear - 1.0) * cosOmega + twoSqrtAAlpha
                    a1 = 2.0 * ((aLinear - 1.0) - (aLinear + 1.0) * cosOmega)
                    a2 = (aLinear + 1.0) - (aLinear - 1.0) * cosOmega - twoSqrtAAlpha
                }
                EqFilterType.LOW_PASS -> {
                    b0 = (1.0 - cosOmega) / 2.0
                    b1 = 1.0 - cosOmega
                    b2 = (1.0 - cosOmega) / 2.0
                    a0 = 1.0 + alpha
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - alpha
                }
                EqFilterType.HIGH_PASS -> {
                    b0 = (1.0 + cosOmega) / 2.0
                    b1 = -(1.0 + cosOmega)
                    b2 = (1.0 + cosOmega) / 2.0
                    a0 = 1.0 + alpha
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - alpha
                }
                EqFilterType.NOTCH -> {
                    b0 = 1.0
                    b1 = -2.0 * cosOmega
                    b2 = 1.0
                    a0 = 1.0 + alpha
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - alpha
                }
            }

            if (kotlin.math.abs(a0) < 1e-9) return BYPASS

            val invA0 = 1.0 / a0
            return BiquadCoefficients(
                b0 = (b0 * invA0).toFloat(),
                b1 = (b1 * invA0).toFloat(),
                b2 = (b2 * invA0).toFloat(),
                a1 = (a1 * invA0).toFloat(),
                a2 = (a2 * invA0).toFloat(),
            )
        }
    }

    /**
     * Evaluates the frequency response magnitude |H(f)| in dB at frequency [freqHz].
     *
     * Used for mathematical computation and real-time visualization of the equalizer's
     * frequency response curve without running an FFT analyzer.
     */
    fun magnitudeDb(freqHz: Float, sampleRate: Int): Float {
        if (this == BYPASS || sampleRate <= 0) return 0f

        val omega = (2.0 * PI * freqHz / sampleRate)
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val cos2Omega = cos(2.0 * omega)
        val sin2Omega = sin(2.0 * omega)

        // Numerator = b0 + b1*e^(-j*omega) + b2*e^(-2j*omega)
        val numRe = b0.toDouble() + b1.toDouble() * cosOmega + b2.toDouble() * cos2Omega
        val numIm = -b1.toDouble() * sinOmega - b2.toDouble() * sin2Omega

        // Denominator = 1 + a1*e^(-j*omega) + a2*e^(-2j*omega)
        val denRe = 1.0 + a1.toDouble() * cosOmega + a2.toDouble() * cos2Omega
        val denIm = -a1.toDouble() * sinOmega - a2.toDouble() * sin2Omega

        val numMagSq = numRe * numRe + numIm * numIm
        val denMagSq = denRe * denRe + denIm * denIm
        if (denMagSq <= 1e-12) return 0f

        val safeNumMagSq = maxOf(numMagSq, 1e-12)
        val magSq = safeNumMagSq / denMagSq
        return (10.0 * log10(magSq)).toFloat()
    }
}
