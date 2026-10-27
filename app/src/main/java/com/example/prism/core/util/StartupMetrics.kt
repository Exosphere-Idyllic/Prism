package com.example.prism.core.util

import android.util.Log
import com.example.prism.BuildConfig

/**
 * Lightweight startup phase tracer. **Debug-only**: every method is a no-op in release builds,
 * so there is zero runtime cost outside of `BuildConfig.DEBUG`.
 *
 * Usage:
 * 1. Call [start] as the very first line of `Application.onCreate`.
 * 2. Call [mark] after each significant phase completes.
 * 3. Call [dump] once the first meaningful frame is drawn to print the full breakdown.
 *
 * Read with: `adb logcat -s PrismStartup`
 */
object StartupMetrics {
    private const val TAG = "PrismStartup"
    private var t0 = 0L
    private val phases = mutableListOf<Pair<String, Long>>()

    /** Records the absolute start of the process and clears any previous state. */
    fun start() {
        if (!BuildConfig.DEBUG) return
        t0 = System.nanoTime()
        phases.clear()
        mark("begin")
    }

    /** Records a named checkpoint with cumulative ms since [start]. */
    fun mark(name: String) {
        if (!BuildConfig.DEBUG) return
        val now = System.nanoTime()
        val deltaMs = (now - t0) / 1_000_000
        phases += name to deltaMs
        Log.d(TAG, "PHASE $name: ${deltaMs}ms (cumulative)")
    }

    /**
     * Prints the full phase table: each row shows the delta since the previous mark
     * and the cumulative time since [start].
     */
    fun dump() {
        if (!BuildConfig.DEBUG) return
        val total = phases.lastOrNull()?.second ?: 0
        Log.d(TAG, "=== STARTUP BREAKDOWN (total ${total}ms) ===")
        var prev = 0L
        phases.forEach { (name, cumulative) ->
            val delta = cumulative - prev
            Log.d(TAG, "  %-35s %4dms  (cumulative %4dms)".format(name, delta, cumulative))
            prev = cumulative
        }
        Log.d(TAG, "=== END STARTUP BREAKDOWN ===")
    }
}
