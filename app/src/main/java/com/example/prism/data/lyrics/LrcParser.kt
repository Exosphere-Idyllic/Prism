package com.example.prism.data.lyrics

import com.example.prism.domain.model.LyricsContent
import com.example.prism.domain.model.LyricsLine
import com.example.prism.domain.model.LyricsSource
import java.util.regex.Pattern

/**
 * High-performance, zero-dependency parser for LRC (Lyric) format files and raw lyrics text.
 *
 * Supports:
 * - Standard timestamps: `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss]`
 * - Multiple timestamps per line: `[00:12.00][00:18.50]Repeated lyric`
 * - Offset tag: `[offset:+/-ms]` applied across all timestamps
 * - Unsynced plain text lines (where timestamps are absent)
 */
object LrcParser {

    private val TIMESTAMP_PATTERN: Pattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val OFFSET_PATTERN: Pattern = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)
    private val METADATA_TAG_PATTERN: Pattern = Pattern.compile("^\\[[a-zA-Z]+:.*?\\]$")

    fun parse(rawText: String, source: LyricsSource = LyricsSource.LRC_FILE): LyricsContent {
        if (rawText.isBlank()) {
            return LyricsContent(source = source, isSynced = false, lines = emptyList())
        }

        val lines = rawText.lines()
        var offsetMs = 0L

        // Scan for [offset: +/- ms]
        for (line in lines) {
            val trimmed = line.trim()
            val matcher = OFFSET_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                offsetMs = matcher.group(1)?.toLongOrNull() ?: 0L
                break
            }
        }

        val parsedTimedLines = mutableListOf<LyricsLine>()
        val plainLines = mutableListOf<LyricsLine>()
        var foundAnyTimestamp = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Skip pure metadata headers like [ti:Song], [ar:Artist], [al:Album]
            if (METADATA_TAG_PATTERN.matcher(trimmed).matches()) {
                continue
            }

            val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
            val lineTimestamps = mutableListOf<Long>()

            var lastEnd = 0
            while (matcher.find()) {
                foundAnyTimestamp = true
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3)

                val millis = when {
                    fractionStr == null -> 0L
                    fractionStr.length == 1 -> fractionStr.toLong() * 100L
                    fractionStr.length == 2 -> fractionStr.toLong() * 10L
                    else -> fractionStr.take(3).toLong()
                }

                val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis + offsetMs
                lineTimestamps.add(totalMs.coerceAtLeast(0L))
                lastEnd = matcher.end()
            }

            val lyricText = if (lineTimestamps.isNotEmpty()) {
                trimmed.substring(lastEnd).trim()
            } else {
                trimmed
            }

            if (lineTimestamps.isNotEmpty()) {
                for (ts in lineTimestamps) {
                    parsedTimedLines.add(LyricsLine(timestampMs = ts, text = lyricText))
                }
            } else if (lyricText.isNotEmpty()) {
                plainLines.add(LyricsLine(timestampMs = -1L, text = lyricText))
            }
        }

        return if (foundAnyTimestamp && parsedTimedLines.isNotEmpty()) {
            parsedTimedLines.sortBy { it.timestampMs }
            LyricsContent(source = source, isSynced = true, lines = parsedTimedLines)
        } else {
            LyricsContent(source = source, isSynced = false, lines = plainLines)
        }
    }
}
