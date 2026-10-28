package com.example.prism.data.artwork

import android.util.Log
import com.example.prism.BuildConfig

/**
 * Debug-only instrumentation for AlbumArtFetcher. No-op in release.
 *
 * Tracks hits/misses of the in-memory negative cache and the fetch strategy
 * that resolved each successful request. Read with:
 *   adb logcat -s PrismArtworkProbe
 */
object ArtworkProbe {
    private const val TAG = "PrismArtworkProbe"
    private const val REPORT_INTERVAL_MS = 10_000L

    // Negative cache counters
    private var negHits = 0
    private var negMisses = 0

    // Fetch strategy counters
    private var customUriHits = 0
    private var systemThumbHits = 0
    private var embeddedHits = 0
    private var networkHits = 0
    private var totalFetches = 0
    private var nullFetches = 0

    private var lastReportMs = 0L

    @Synchronized fun onNegativeHit() { if (BuildConfig.DEBUG) negHits++ }
    @Synchronized fun onNegativeMiss() { if (BuildConfig.DEBUG) negMisses++ }
    @Synchronized fun onCustomUri() { if (BuildConfig.DEBUG) customUriHits++ }
    @Synchronized fun onSystemThumb() { if (BuildConfig.DEBUG) systemThumbHits++ }
    @Synchronized fun onEmbedded() { if (BuildConfig.DEBUG) embeddedHits++ }
    @Synchronized fun onNetwork() { if (BuildConfig.DEBUG) networkHits++ }
    @Synchronized fun onTotalFetch() { if (BuildConfig.DEBUG) totalFetches++ }
    @Synchronized fun onNullFetch() { if (BuildConfig.DEBUG) nullFetches++ }

    @Synchronized
    fun maybeReport() {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        if (lastReportMs == 0L) { lastReportMs = now; return }
        val elapsed = now - lastReportMs
        if (elapsed < REPORT_INTERVAL_MS) return

        val totalNeg = negHits + negMisses
        val hitRate = if (totalNeg > 0) negHits * 100.0 / totalNeg else 0.0
        val fetchesPerSec = totalFetches * 1000.0 / elapsed.coerceAtLeast(1)

        Log.d(TAG, buildString {
            append("neg_cache hits=%d misses=%d (%.1f%% hit-rate)".format(negHits, negMisses, hitRate))
            append(" | fetches total=%d rate=%.1f/s".format(totalFetches, fetchesPerSec))
            append(" custom=%d thumb=%d embedded=%d net=%d null=%d".format(
                customUriHits, systemThumbHits, embeddedHits, networkHits, nullFetches))
        })

        negHits = 0; negMisses = 0
        customUriHits = 0; systemThumbHits = 0; embeddedHits = 0
        networkHits = 0; totalFetches = 0; nullFetches = 0
        lastReportMs = now
    }

    @Synchronized
    fun reset() {
        negHits = 0; negMisses = 0
        customUriHits = 0; systemThumbHits = 0; embeddedHits = 0
        networkHits = 0; totalFetches = 0; nullFetches = 0
        lastReportMs = 0
    }
}
