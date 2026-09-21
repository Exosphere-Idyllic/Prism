package com.example.prism.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a single line of lyrics, either synchronized with a timestamp
 * or unsynchronized ([timestampMs] = -1L).
 */
@Immutable
data class LyricsLine(
    val timestampMs: Long = -1L,
    val text: String,
) {
    val isSynced: Boolean get() = timestampMs >= 0L
}

/**
 * Origin of the lyrics.
 */
enum class LyricsSource {
    /** Embedded directly inside the audio file (ID3 USLT/SYLT, Vorbis comments, MP4 tags). */
    EMBEDDED,

    /** Loaded from an external .lrc file (sidecar file or user-selected). */
    LRC_FILE,
}

/**
 * Represents the parsed lyrics content for a specific source.
 */
@Immutable
data class LyricsContent(
    val source: LyricsSource,
    val isSynced: Boolean,
    val lines: List<LyricsLine>,
)

/**
 * Complete lyrics state for a song, holding both embedded and LRC sources if present,
 * and allowing the user to seamlessly choose between them.
 */
@Immutable
data class SongLyrics(
    val songId: String,
    val embeddedLyrics: LyricsContent? = null,
    val lrcLyrics: LyricsContent? = null,
    val selectedSource: LyricsSource? = null,
) {
    /**
     * Resolves the active lyrics content based on user selection or available fallback.
     */
    val activeLyrics: LyricsContent?
        get() = when (selectedSource) {
            LyricsSource.EMBEDDED -> embeddedLyrics ?: lrcLyrics
            LyricsSource.LRC_FILE -> lrcLyrics ?: embeddedLyrics
            null -> lrcLyrics ?: embeddedLyrics
        }

    val hasLyrics: Boolean
        get() = embeddedLyrics != null || lrcLyrics != null

    val hasBothSources: Boolean
        get() = embeddedLyrics != null && lrcLyrics != null
}
