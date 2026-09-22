package com.example.prism.ui.equalizer

import com.example.prism.ui.equalizer.components.dbToNormalizedY
import com.example.prism.ui.equalizer.components.freqToNormalizedX
import com.example.prism.ui.equalizer.components.normalizedXToFreq
import com.example.prism.ui.equalizer.components.normalizedYToDb
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * Tests unitarios para las funciones matemáticas de coordenadas del [ParametricEqCanvas].
 *
 * Verifica:
 * - Round-trips de frecuencia (Hz ↔ normalized X)
 * - Round-trips de ganancia dB (dB ↔ normalized Y)
 * - Clamping en límites (borde inferior y superior)
 * - Monotonía del escalado logarítmico de X
 * - Monotonía del escalado lineal de Y
 */
class ParametricEqCanvasTest {

    // ─── freqToNormalizedX ──────────────────────────────────────────────────

    @Test
    fun freqToNormalizedX_minFreqMapsToZero() {
        val x = freqToNormalizedX(20f)
        assertEquals(0f, x, 1e-4f)
    }

    @Test
    fun freqToNormalizedX_maxFreqMapsToOne() {
        val x = freqToNormalizedX(20_000f)
        assertEquals(1f, x, 1e-4f)
    }

    @Test
    fun freqToNormalizedX_1kHzMapsToApproximatelyMiddle() {
        // log10(1000) = 3.0; logMin=log10(20)≈1.301; logMax=log10(20000)≈4.301
        // expected ≈ (3.0 - 1.301) / (4.301 - 1.301) = 1.699 / 3.0 ≈ 0.566
        val x = freqToNormalizedX(1_000f)
        assertEquals(0.566f, x, 0.01f)
    }

    @Test
    fun freqToNormalizedX_belowMinClampsToZero() {
        val x = freqToNormalizedX(1f) // below 20 Hz
        assertEquals(0f, x, 1e-4f)
    }

    @Test
    fun freqToNormalizedX_aboveMaxClampsToOne() {
        val x = freqToNormalizedX(25_000f) // above 20 kHz
        assertEquals(1f, x, 1e-4f)
    }

    @Test
    fun freqToNormalizedX_isMonotonicallyIncreasing() {
        val freqs = floatArrayOf(20f, 100f, 500f, 1_000f, 5_000f, 10_000f, 20_000f)
        val xs = freqs.map { freqToNormalizedX(it) }
        for (i in 1 until xs.size) {
            assert(xs[i] > xs[i - 1]) {
                "Expected X[${i}]=${xs[i]} > X[${i-1}]=${xs[i-1]} for freq=${freqs[i]}"
            }
        }
    }

    // ─── normalizedXToFreq ─────────────────────────────────────────────────

    @Test
    fun normalizedXToFreq_zeroMapsToMinFreq() {
        val freq = normalizedXToFreq(0f)
        assertEquals(20f, freq, 0.01f)
    }

    @Test
    fun normalizedXToFreq_oneMapsToMaxFreq() {
        val freq = normalizedXToFreq(1f)
        assertEquals(20_000f, freq, 0.1f)
    }

    @Test
    fun normalizedXToFreq_belowZeroClampsToMinFreq() {
        val freq = normalizedXToFreq(-0.5f)
        assertEquals(20f, freq, 0.01f)
    }

    @Test
    fun normalizedXToFreq_aboveOneClampsToMaxFreq() {
        val freq = normalizedXToFreq(1.5f)
        assertEquals(20_000f, freq, 0.1f)
    }

    // ─── Round-trip freq ───────────────────────────────────────────────────

    @Test
    fun freqToX_andBack_roundTrip_within1Percent() {
        val testFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        for (freq in testFreqs) {
            val x = freqToNormalizedX(freq)
            val recovered = normalizedXToFreq(x)
            val relError = abs((recovered - freq) / freq)
            assert(relError < 0.01f) {
                "Round-trip error for ${freq}Hz: recovered=${recovered}Hz, relError=${relError}"
            }
        }
    }

    // ─── dbToNormalizedY ──────────────────────────────────────────────────

    @Test
    fun dbToNormalizedY_maxGainMapsToZero() {
        // +15 dB → normalized 0 (top of canvas)
        val y = dbToNormalizedY(15f)
        assertEquals(0f, y, 1e-4f)
    }

    @Test
    fun dbToNormalizedY_minGainMapsToOne() {
        // -15 dB → normalized 1 (bottom of canvas)
        val y = dbToNormalizedY(-15f)
        assertEquals(1f, y, 1e-4f)
    }

    @Test
    fun dbToNormalizedY_zeroDbMapsToHalfway() {
        val y = dbToNormalizedY(0f)
        assertEquals(0.5f, y, 1e-4f)
    }

    @Test
    fun dbToNormalizedY_aboveMaxClampsToZero() {
        val y = dbToNormalizedY(20f) // above +15 dB
        assertEquals(0f, y, 1e-4f)
    }

    @Test
    fun dbToNormalizedY_belowMinClampsToOne() {
        val y = dbToNormalizedY(-20f) // below -15 dB
        assertEquals(1f, y, 1e-4f)
    }

    @Test
    fun dbToNormalizedY_isMonotonicallyDecreasing() {
        // Higher dB → smaller Y (higher on canvas)
        val dbs = floatArrayOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)
        val ys = dbs.map { dbToNormalizedY(it) }
        for (i in 1 until ys.size) {
            assert(ys[i] < ys[i - 1]) {
                "Expected Y[${i}]=${ys[i]} < Y[${i-1}]=${ys[i-1]} for db=${dbs[i]}"
            }
        }
    }

    // ─── normalizedYToDb ──────────────────────────────────────────────────

    @Test
    fun normalizedYToDb_zeroMapsToMaxDb() {
        val db = normalizedYToDb(0f)
        assertEquals(15f, db, 1e-4f)
    }

    @Test
    fun normalizedYToDb_oneMapsToMinDb() {
        val db = normalizedYToDb(1f)
        assertEquals(-15f, db, 1e-4f)
    }

    @Test
    fun normalizedYToDb_halfMapsToZeroDb() {
        val db = normalizedYToDb(0.5f)
        assertEquals(0f, db, 1e-4f)
    }

    // ─── Round-trip gain ──────────────────────────────────────────────────

    @Test
    fun dbToY_andBack_roundTrip_withinTolerance() {
        val testGains = floatArrayOf(-15f, -12f, -6f, 0f, 3f, 6f, 12f, 15f)
        for (gain in testGains) {
            val y = dbToNormalizedY(gain)
            val recovered = normalizedYToDb(y)
            assertEquals("Round-trip failed for ${gain}dB", gain, recovered, 0.001f)
        }
    }
}
