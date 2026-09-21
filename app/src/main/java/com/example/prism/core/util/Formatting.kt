package com.example.prism.core.util

import java.util.Locale

/**
 * Formats a duration in milliseconds into a standard mm:ss or hh:mm:ss elapsed time string.
 * Uses pure Kotlin formatting to allow execution in local JVM unit tests without Robolectric mocking.
 */
fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000L
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%02d:%02d", m, sec)
}

/**
 * Resolves the display name for an artist, falling back to [fallback] when blank or unknown.
 */
fun resolveArtistName(artist: String?, fallback: String): String =
    artist?.trim()?.takeUnless { it.isEmpty() || it.equals("unknown", true) || it.equals("<unknown>", true) } ?: fallback

