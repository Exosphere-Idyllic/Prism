package com.example.prism.player.effects

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
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
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val MAX_BANDS = 16
        private const val MAX_CHANNELS = 8
    }

    private val configRef = AtomicReference(EqualizerConfig())
    private val filters = Array(MAX_BANDS) { BiquadFilter(maxChannels = MAX_CHANNELS) }

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
        val count = bands.size.coerceAtMost(filters.size)

        for (i in 0 until count) {
            val band = bands[i]
            if (band.enabled) {
                filters[i].coefficients = BiquadCoefficients.calculate(
                    type = band.type,
                    frequencyHz = band.frequencyHz,
                    sampleRate = sampleRate,
                    gainDb = band.gainDb,
                    q = band.q,
                )
            } else {
                filters[i].coefficients = BiquadCoefficients.BYPASS
            }
        }

        for (i in count until filters.size) {
            filters[i].coefficients = BiquadCoefficients.BYPASS
        }
        activeBandCount = count
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // If equalizer is disabled, bypass processing with a direct byte copy (zero latency, zero CPU overhead)
        if (!isEqEnabled) {
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

        if (encoding == C.ENCODING_PCM_16BIT) {
            val sampleCount = remaining / 2
            var channel = 0

            for (i in 0 until sampleCount) {
                val rawShort = inputBuffer.short
                var sample = rawShort * (1.0f / 32768.0f)

                // Apply preamp
                sample *= preamp

                // Apply cascaded biquad filters
                for (b in 0 until bandCount) {
                    sample = filters[b].processSample(sample, channel)
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

                // Apply cascaded biquad filters
                for (b in 0 until bandCount) {
                    sample = filters[b].processSample(sample, channel)
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
    }

    override fun onFlush() {
        for (i in 0 until activeBandCount) {
            filters[i].reset()
        }
    }

    override fun onReset() {
        for (i in filters.indices) {
            filters[i].reset()
        }
        activeBandCount = 0
    }

    /**
     * Polynomial soft-knee limiter / saturation function.
     * Linearly transparent for signal levels below 2/3 (~ -3.5 dBFS), smoothly compressing
     * above 2/3 toward 1.0 to eliminate harsh digital clipping without expensive transcendentals.
     */
    private fun softClip(x: Float): Float {
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
