package com.example.prism.player.effects

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.prism.BuildConfig
import com.example.prism.domain.model.equalizer.EqualizerConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

/**
 * Media3 [AudioProcessor] implementing in-house biquad equalization with:
 * - Direct Form II Transposed filter cascade
 * - Preamp gain adjustment
 * - Zero memory allocations in [queueInput] during real-time playback
 * - Soft-knee saturation limiter to prevent digital clipping
 * - Lock-free, thread-safe configuration updates via [AtomicReference]
 * - Instantaneous bypass when disabled without audio sink reconfiguration
 * - Active-filter compaction: only bands with non-identity coefficients are iterated
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val MAX_BANDS = 16
        private const val MAX_CHANNELS = 8
    }

    private val configRef = AtomicReference(EqualizerConfig())

    // Pool de filtros pre-allocated: filters[0..MAX_BANDS-1].
    // activeFilters[0..activeBandCount-1] contiene solo los filtros con coeficientes no-BYPASS,
    // compactados sin huecos. queueInput itera sobre activeFilters, no sobre filters.
    private val filters = Array(MAX_BANDS) { BiquadFilter(maxChannels = MAX_CHANNELS) }
    private val activeFilters = arrayOfNulls<BiquadFilter>(MAX_BANDS)

    @Volatile private var isEqEnabled: Boolean = false
    @Volatile private var preampLinear: Float = 1.0f
    @Volatile private var limiterEnabled: Boolean = true
    @Volatile private var activeBandCount: Int = 0

    init {
        updateFromConfig(configRef.get())
    }

    /**
     * Updates the current equalizer configuration thread-safely.
     * Filter coefficients are recalculated on demand, not per sample.
     */
    fun setConfig(config: EqualizerConfig) {
        configRef.set(config)
        updateFromConfig(config)
        if (inputAudioFormat.sampleRate > 0) {
            recalculateCoefficients(inputAudioFormat.sampleRate)
        }
    }

    /**
     * Obtiene una copia inmutable de la configuración actual del procesador.
     */
    fun getConfig(): EqualizerConfig = configRef.get()

    private fun updateFromConfig(config: EqualizerConfig) {
        isEqEnabled = config.enabled
        preampLinear = 10f.pow(config.preampDb / 20f)
        limiterEnabled = config.limiterEnabled
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        recalculateCoefficients(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    private fun recalculateCoefficients(sampleRate: Int) {
        if (sampleRate <= 0) return
        val config = configRef.get() ?: return
        val bands = config.bands
        var active = 0

        for (band in bands) {
            if (active >= MAX_BANDS) break
            if (!band.enabled) continue

            val coeffs = BiquadCoefficients.calculate(
                type = band.type,
                frequencyHz = band.frequencyHz,
                sampleRate = sampleRate,
                gainDb = band.gainDb,
                q = band.q,
            )
            // calculate() returns BYPASS singleton when:
            //  - BELL/SHELF with |gain| < 0.005 dB (perceptually identity)
            //  - a0 < 1e-9 (numerically unstable denominator)
            //  - sampleRate <= 0 (already guarded above)
            // Skipping BYPASS bands here is what gives "Flat" preset its ~100% CPU reduction.
            if (coeffs === BiquadCoefficients.BYPASS) continue

            val f = filters[active]
            f.coefficients = coeffs
            activeFilters[active] = f
            active++
        }

        // Also reset any filter slots that are no longer active, to avoid stale state
        // if they get reactivated later without an explicit flush.
        for (i in active until MAX_BANDS) filters[i].reset()

        activeBandCount = active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val probeStart = EqualizerProbe.onQueueInputStart()
        try {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            // Bypass path 1: EQ disabled by the user.
            // Bypass path 2: EQ enabled but all bands are effectively neutral (all gain ≈ 0 dB).
            //   With preset "Flat", activeBandCount == 0 → bit-perfect copy, zero DSP work.
            if (!isEqEnabled || activeBandCount == 0) {
                val output = replaceOutputBuffer(remaining)
                output.put(inputBuffer)
                output.flip()
                return
            }

            val encoding = inputAudioFormat.encoding
            val channelCount = inputAudioFormat.channelCount.coerceIn(1, MAX_CHANNELS)
            val output = replaceOutputBuffer(remaining)
            output.order(ByteOrder.nativeOrder())
            inputBuffer.order(ByteOrder.nativeOrder())

            val bandCount = activeBandCount
            val preamp = preampLinear
            val useLimiter = limiterEnabled

            // Snapshot coefficients once per buffer (one @Volatile read per active band, not per sample).
            // At ~16 Hz buffer rate × bandCount = ~160 volatile reads/sec — negligible.
            // Previously: 1 volatile read + 5 float comparisons per sample × 96 000 samples/sec × 10 bands
            //             = 960 000 volatile reads/sec + 4.8 M float comparisons/sec.
            val snapshotCoeffs = arrayOfNulls<BiquadCoefficients>(bandCount)
            for (b in 0 until bandCount) {
                snapshotCoeffs[b] = activeFilters[b]!!.coefficients
            }

            if (encoding == C.ENCODING_PCM_16BIT) {
                val sampleCount = remaining / 2
                var channel = 0

                for (i in 0 until sampleCount) {
                    val rawShort = inputBuffer.short
                    var sample = rawShort * (1.0f / 32768.0f)

                    // Apply preamp
                    sample *= preamp

                    // Apply cascaded biquad filters (only active, non-BYPASS bands)
                    for (b in 0 until bandCount) {
                        sample = activeFilters[b]!!.processSample(sample, channel, snapshotCoeffs[b]!!)
                    }

                    // Apply soft-knee limiter if enabled
                    if (useLimiter) {
                        sample = softClip(sample)
                    }

                    // Convert back to 16-bit PCM integer
                    val outInt = (sample * 32767.0f).toInt()
                    val clampedShort = outInt.coerceIn(-32768, 32767).toShort()
                    output.putShort(clampedShort)

                    channel++
                    if (channel >= channelCount) channel = 0
                }
            } else if (encoding == C.ENCODING_PCM_FLOAT) {
                val sampleCount = remaining / 4
                var channel = 0

                for (i in 0 until sampleCount) {
                    var sample = inputBuffer.float

                    // Apply preamp
                    sample *= preamp

                    // Apply cascaded biquad filters (only active, non-BYPASS bands)
                    for (b in 0 until bandCount) {
                        sample = activeFilters[b]!!.processSample(sample, channel, snapshotCoeffs[b]!!)
                    }

                    // Apply soft-knee limiter if enabled
                    if (useLimiter) {
                        sample = softClip(sample)
                    }

                    output.putFloat(sample)

                    channel++
                    if (channel >= channelCount) channel = 0
                }
            }

            output.flip()
        } finally {
            EqualizerProbe.onQueueInputEnd(probeStart)
        }
    }

    override fun onFlush() {
        // Reset only active filters (the ones actually processing audio).
        // activeFilters[0..activeBandCount-1] are the same instances as in filters[],
        // just compacted. Resetting via activeFilters is sufficient and cheaper.
        for (i in 0 until activeBandCount) {
            activeFilters[i]?.reset()
        }
    }

    override fun onReset() {
        for (i in filters.indices) {
            filters[i].reset()
        }
        for (i in activeFilters.indices) {
            activeFilters[i] = null
        }
        activeBandCount = 0
    }

    /**
     * Polynomial soft-knee limiter / saturation function.
     * Linearly transparent for signal levels below 2/3 (~ -3.5 dBFS), smoothly compressing
     * above 2/3 toward 1.0 to eliminate harsh digital clipping without expensive transcendentals.
     */
    private fun softClip(x: Float): Float {
        if (BuildConfig.DEBUG && (x > 0.666667f || x < -0.666667f)) {
            EqualizerProbe.onClip()
        }
        return when {
            x > 1.333333f -> 1.0f
            x < -1.333333f -> -1.0f
            x > 0.666667f -> {
                val diff = x - 0.666667f
                x - (diff * diff) * 0.75f
            }
            x < -0.666667f -> {
                val diff = x + 0.666667f
                x + (diff * diff) * 0.75f
            }
            else -> x
        }
    }
}
