package com.example.prism.core.util

import android.util.Log
import com.example.prism.BuildConfig

/**
 * Debug-only phase tracer for MediaStoreScanner.doScan().
 * No-op in release builds. Read with: adb logcat -s PrismScanProbe
 */
object ScanProbe {
    private const val TAG = "PrismScanProbe"
    private var scanStartNs = 0L
    private var lastMarkNs = 0L
    private var isFullScan = false

    fun onScanStart(full: Boolean) {
        if (!BuildConfig.DEBUG) return
        scanStartNs = System.nanoTime()
        lastMarkNs = scanStartNs
        isFullScan = full
        Log.d(TAG, "BEGIN (full=$full)")
    }

    fun onPhase(name: String) {
        if (!BuildConfig.DEBUG || scanStartNs == 0L) return
        val now = System.nanoTime()
        val deltaMs = (now - lastMarkNs) / 1_000_000
        val cumulativeMs = (now - scanStartNs) / 1_000_000
        Log.d(TAG, "  %-22s %5dms  (cumulative %5dms)".format(name, deltaMs, cumulativeMs))
        lastMarkNs = now
    }

    fun onScanEnd(upserts: Int, deletes: Int) {
        if (!BuildConfig.DEBUG || scanStartNs == 0L) return
        val totalMs = (System.nanoTime() - scanStartNs) / 1_000_000
        Log.d(TAG, "END (full=$isFullScan, total=${totalMs}ms, upserts=$upserts, deletes=$deletes)")
        scanStartNs = 0L
    }
}
