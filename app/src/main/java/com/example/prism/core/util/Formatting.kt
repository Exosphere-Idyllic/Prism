package com.example.prism.core.util

import android.text.format.DateUtils

/**
 * Formats a duration in milliseconds into a standard mm:ss or hh:mm:ss elapsed time string.
 */
fun formatTime(ms: Long): String = DateUtils.formatElapsedTime(ms.coerceAtLeast(0L) / 1000)
