package com.example.prism.domain.repository

import com.example.prism.data.entity.Song
import com.example.prism.domain.model.SongLyrics

/**
 * Repository defining operations for querying and persisting song lyrics.
 * Segregated interface according to the Interface Segregation Principle (ISP).
 */
interface LyricsRepository {
    /**
     * Resolves all available lyrics for a song (embedded and/or external .lrc).
     */
    suspend fun getLyrics(song: Song): SongLyrics

    /**
     * Associates a custom lyrics file URI (e.g. user selected .lrc file) with the specified song.
     */
    suspend fun setCustomLyricsUri(songId: String, lyricsUri: String)
}
