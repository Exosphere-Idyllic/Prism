package com.example.prism.player.effects

import android.util.Log
import com.example.prism.BuildConfig

/**
 * Debug-only CPU probe for [EqualizerAudioProcessor]. No-op in release builds.
 *
 * Reports per-buffer processing time, buffer throughput, cumulative CPU load per second,
 * and clip events. Reset automatically after each report interval.
 *
 * Usage: `adb logcat -s PrismEqProbe`
 */
object EqualizerProbe {
    private const val TAG = "PrismEqProbe"
    private const val REPORT_INTERVAL_MS = 5_000L

    private var totalNanos: Long = 0
    private var totalBuffers: Int = 0
    private var lastReportMs: Long = 0
    private var clips: Int = 0

    /**
     * Call at the start of [EqualizerAudioProcessor.queueInput].
     * Returns a timestamp token to pass to [onQueueInputEnd]; zero in release builds.
     */
    fun onQueueInputStart(): Long = if (BuildConfig.DEBUG) System.nanoTime() else 0L

    /**
     * Call at the end of [EqualizerAudioProcessor.queueInput] (in a finally block).
     * @param startNanos Token returned by [onQueueInputStart].
     */
    fun onQueueInputEnd(startNanos: Long) {
        if (!BuildConfig.DEBUG || startNanos == 0L) return
        totalNanos += (System.nanoTime() - startNanos)
        totalBuffers++

        val now = System.currentTimeMillis()
        if (lastReportMs == 0L) {
            lastReportMs = now
            return
        }
        val elapsed = now - lastReportMs
        if (elapsed >= REPORT_INTERVAL_MS) {
            val avgMs = (totalNanos.toDouble() / totalBuffers) / 1_000_000.0
            val buffersPerSec = totalBuffers * 1000.0 / elapsed.coerceAtLeast(1)
            val cpuMsPerSec = avgMs * buffersPerSec
            Log.d(
                TAG,
                "avg=%.3fms/buffer buffers/s=%.1f cpu=%.2fms/s clips=%d".format(
                    avgMs, buffersPerSec, cpuMsPerSec, clips,
                ),
            )
            totalNanos = 0
            totalBuffers = 0
            lastReportMs = now
            clips = 0
        }
    }

    /**
     * Call whenever [EqualizerAudioProcessor.softClip] saturates a sample (|x| > 2/3).
     * No-op in release builds.
     */
    fun onClip() {
        if (BuildConfig.DEBUG) clips++
    }

    /** Resets all accumulators. Call when EQ is reconfigured or after a seek. */
    fun reset() {
        totalNanos = 0
        totalBuffers = 0
        lastReportMs = 0
        clips = 0
    }
}
