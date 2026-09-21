package com.example.prism.data.lyrics

import com.example.prism.domain.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class EmbeddedLyricsExtractorTest {

    @Test
    fun extractFromStream_nonId3Stream_returnsNull() {
        val nonId3 = "RANDOM_DATA_THAT_IS_NOT_ID3".toByteArray(StandardCharsets.UTF_8)
        val result = EmbeddedLyricsExtractor.extractFromStream(ByteArrayInputStream(nonId3))
        assertNull(result)
    }

    @Test
    fun extractFromStream_emptyStream_returnsNull() {
        val empty = ByteArray(0)
        val result = EmbeddedLyricsExtractor.extractFromStream(ByteArrayInputStream(empty))
        assertNull(result)
    }

    @Test
    fun extractFromStream_id3v23WithUsltFrame_extractsUnsyncedLyrics() {
        val lyricsText = "Hello from embedded lyrics\nSecond line here"
        val out = ByteArrayOutputStream()

        // ID3 header: "ID3", v2.3, flags=0
        out.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        out.write(3) // v2.3
        out.write(0) // revision
        out.write(0) // flags

        // Frame content for USLT:
        // encoding (0 = ISO-8859-1), lang (eng), descriptor (""\0), text
        val frameContent = ByteArrayOutputStream()
        frameContent.write(0) // ISO-8859-1
        frameContent.write("eng".toByteArray(StandardCharsets.US_ASCII))
        frameContent.write(0) // empty descriptor null-terminated
        frameContent.write(lyricsText.toByteArray(StandardCharsets.ISO_8859_1))
        val frameBytes = frameContent.toByteArray()

        // Frame header: USLT (4 bytes), size (4 bytes 32-bit int), flags (2 bytes)
        val frameHeader = ByteArrayOutputStream()
        frameHeader.write("USLT".toByteArray(StandardCharsets.US_ASCII))
        val size = frameBytes.size
        frameHeader.write((size shr 24) and 0xFF)
        frameHeader.write((size shr 16) and 0xFF)
        frameHeader.write((size shr 8) and 0xFF)
        frameHeader.write(size and 0xFF)
        frameHeader.write(0) // flags
        frameHeader.write(0)

        val totalTagPayload = frameHeader.size() + frameBytes.size
        // 4 bytes syncsafe tag size
        out.write((totalTagPayload shr 21) and 0x7F)
        out.write((totalTagPayload shr 14) and 0x7F)
        out.write((totalTagPayload shr 7) and 0x7F)
        out.write(totalTagPayload and 0x7F)

        out.write(frameHeader.toByteArray())
        out.write(frameBytes)

        val stream = ByteArrayInputStream(out.toByteArray())
        val result = EmbeddedLyricsExtractor.extractFromStream(stream)

        assertNotNull(result)
        assertEquals(LyricsSource.EMBEDDED, result!!.source)
        assertEquals(2, result.lines.size)
        assertEquals("Hello from embedded lyrics", result.lines[0].text)
        assertEquals("Second line here", result.lines[1].text)
    }
}
