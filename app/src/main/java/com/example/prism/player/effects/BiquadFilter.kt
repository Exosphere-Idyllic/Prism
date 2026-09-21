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
     * @param sample The input sample x[n].
     * @param channel The channel index (0 for Left/Mono, 1 for Right, etc.).
     * @return The filtered sample y[n].
     */
    fun processSample(sample: Float, channel: Int): Float {
        val coeffs = coefficients
        if (coeffs == BiquadCoefficients.BYPASS) return sample

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
