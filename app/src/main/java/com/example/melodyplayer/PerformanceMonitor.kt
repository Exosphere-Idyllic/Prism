package com.example.melodyplayer

import android.app.Activity
import android.util.Log
import androidx.metrics.performance.JankStats
import java.lang.ref.WeakReference

/**
 * Tracks UI jank (dropped frames) via [JankStats] while an [Activity] is in the foreground.
 *
 * Uses a [WeakReference] so the Activity window is never retained after [stop] is called or
 * the Activity is destroyed, preventing a memory / window leak.
 */
object PerformanceMonitor {
    private var jankStatsRef: WeakReference<JankStats>? = null
    private const val TAG = "JankStats"

    fun start(activity: Activity) {
        stop()
        val stats = JankStats.createAndTrack(activity.window) { frameData ->
            if (frameData.isJank) {
                val uiDurationMs = frameData.frameDurationUiNanos / 1_000_000.0
                Log.w(TAG, "JANK DETECTED: ${uiDurationMs}ms (UI Thread)")
            }
        }
        jankStatsRef = WeakReference(stats)
    }

    fun stop() {
        jankStatsRef?.get()?.isTrackingEnabled = false
        jankStatsRef = null
    }
}
