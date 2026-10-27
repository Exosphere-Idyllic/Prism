package com.example.prism.player.effects

/**
 * Direct Form II Transposed biquad filter.
 *
 * Implements:
 *   y[n] = b0 * x[n] + d1[n-1]
 *   d1[n] = b1 * x[n] - a1 * y[n] + d2[n-1]
 *   d2[n] = b2 * x[n] - a2 * y[n]
 *
 * Maintains independent delay state registers (d1, d2) per audio channel.
 * Designed for low latency, high numerical stability in single-precision float,
 * and zero allocations during sample processing.
 *
 * ## Threading model
 * [coefficients] is `@Volatile` to guarantee visibility between the UI thread (writer)
 * and the audio thread (reader). The caller **must** read [coefficients] once per buffer
 * and pass the snapshot to [processSample]; reading it once per sample would cost
 * ~960 000 volatile reads/sec at 48 kHz stereo with 10 bands.
 */
class BiquadFilter(
    @Volatile var coefficients: BiquadCoefficients = BiquadCoefficients.BYPASS,
    maxChannels: Int = 2,
) {
    private val d1 = FloatArray(maxChannels.coerceAtLeast(1))
    private val d2 = FloatArray(maxChannels.coerceAtLeast(1))

    /**
     * Processes a single audio sample for the given channel.
     *
     * @param sample  Input sample x[n].
     * @param channel Channel index (0 = Left/Mono, 1 = Right, etc.).
     * @param coeffs  Coefficients pre-snapshotted by the caller once per buffer.
     *                The caller must read [coefficients] once at buffer start and pass
     *                the same instance here for every sample in the buffer.
     *                No BYPASS check is performed here: the caller ([EqualizerAudioProcessor])
     *                compacts active filters and guarantees this method is only called
     *                for non-BYPASS bands.
     * @return The filtered sample y[n].
     */
    fun processSample(sample: Float, channel: Int, coeffs: BiquadCoefficients): Float {
        val ch = if (channel in d1.indices) channel else 0
        val y = coeffs.b0 * sample + d1[ch]
        d1[ch] = coeffs.b1 * sample - coeffs.a1 * y + d2[ch]
        d2[ch] = coeffs.b2 * sample - coeffs.a2 * y
        return y
    }

    /**
     * Resets the filter's delay memory to zero.
     * Call this when seeking or flushing the audio pipeline to prevent audible pops/clicks.
     */
    fun reset() {
        d1.fill(0f)
        d2.fill(0f)
    }
}
