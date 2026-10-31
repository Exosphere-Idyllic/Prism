package com.example.prism.core.util

import android.util.Log
import com.example.prism.BuildConfig

object PlayTapProbe {
    private const val TAG = "PrismPlayTap"
    private var startNs = 0L
    private var lastNs = 0L

    fun onPlayStart(hasPlaylist: Boolean) {
        if (!BuildConfig.DEBUG) return
        startNs = System.nanoTime()
        lastNs = startNs
        Log.d(TAG, "BEGIN (playlist=$hasPlaylist)")
    }

    fun onPhase(name: String) {
        if (!BuildConfig.DEBUG || startNs == 0L) return
        val now = System.nanoTime()
        Log.d(TAG, "  %-22s %5dms".format(name, (now - lastNs) / 1_000_000))
        lastNs = now
    }

    fun onPlayEnd() {
        if (!BuildConfig.DEBUG || startNs == 0L) return
        Log.d(TAG, "END total=${(System.nanoTime() - startNs) / 1_000_000}ms")
        startNs = 0L
    }
}
