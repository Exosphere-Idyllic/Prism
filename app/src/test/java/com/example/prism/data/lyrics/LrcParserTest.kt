package com.example.prism.data.lyrics

import com.example.prism.domain.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_emptyString_returnsEmptyUnsynced() {
        val result = LrcParser.parse("")
        assertFalse(result.isSynced)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun parse_standardLrcLines_parsedAndSorted() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:15.50]Second line
            [00:05.00]First line
            [01:02.100]Third line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result.isSynced)
        assertEquals(LyricsSource.LRC_FILE, result.source)
        assertEquals(3, result.lines.size)

        assertEquals("First line", result.lines[0].text)
        assertEquals(5_000L, result.lines[0].timestampMs)

        assertEquals("Second line", result.lines[1].text)
        assertEquals(15_500L, result.lines[1].timestampMs)

        assertEquals("Third line", result.lines[2].text)
        assertEquals(62_100L, result.lines[2].timestampMs)
    }

    @Test
    fun parse_multipleTimestampsOnSingleLine_expandsAndSorts() {
        val lrc = """
            [00:10.00][00:30.00]Chorus line
            [00:20.00]Verse line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result.isSynced)
        assertEquals(3, result.lines.size)

        assertEquals(10_000L, result.lines[0].timestampMs)
        assertEquals("Chorus line", result.lines[0].text)

        assertEquals(20_000L, result.lines[1].timestampMs)
        assertEquals("Verse line", result.lines[1].text)

        assertEquals(30_000L, result.lines[2].timestampMs)
        assertEquals("Chorus line", result.lines[2].text)
    }

    @Test
    fun parse_offsetTag_adjustsTimestamps() {
        val lrc = """
            [offset:+500]
            [00:05.00]First line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result.isSynced)
        assertEquals(1, result.lines.size)
        assertEquals(5_500L, result.lines[0].timestampMs)
    }

    @Test
    fun parse_unsyncedPlainText_returnsUnsyncedLines() {
        val plainText = """
            Line one of lyrics
            Line two of lyrics
            Line three of lyrics
        """.trimIndent()

        val result = LrcParser.parse(plainText, source = LyricsSource.EMBEDDED)
        assertFalse(result.isSynced)
        assertEquals(LyricsSource.EMBEDDED, result.source)
        assertEquals(3, result.lines.size)
        assertEquals(-1L, result.lines[0].timestampMs)
        assertEquals("Line one of lyrics", result.lines[0].text)
        assertEquals("Line two of lyrics", result.lines[1].text)
    }
}
