package com.example.melodyplayer

import android.app.Activity
import androidx.metrics.performance.JankStats
import androidx.core.content.ContextCompat
import android.util.Log

object PerformanceMonitor {
    private var jankStats: JankStats? = null
    private const val TAG = "JankStats"

    fun start(activity: Activity) {
        // createAndTrack requires (window, executor, listener) in 1.0.0-alpha01
        jankStats = JankStats.createAndTrack(
            activity.window,
            ContextCompat.getMainExecutor(activity)
        ) { frameData ->
            if (frameData.isJank) {
                val uiDurationMs = frameData.frameDurationUiNanos / 1_000_000.0
                Log.w(TAG, "JANK DETECTED: ${uiDurationMs}ms (UI Thread)")
            }
        }
    }

    fun stop() {
        jankStats?.isTrackingEnabled = false
    }
}
