package com.example.prism.data.lyrics

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.prism.domain.model.LyricsContent
import com.example.prism.domain.model.LyricsLine
import com.example.prism.domain.model.LyricsSource
import timber.log.Timber
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Extracts embedded lyrics from audio files (ID3v2 USLT/SYLT, Vorbis comments)
 * using standard Android stream I/O without requiring third-party libraries.
 */
object EmbeddedLyricsExtractor {

    private const val MAX_TAG_HEADER_SEARCH_BYTES = 512 * 1024 // 512 KB

    fun extract(context: Context, mediaUriString: String): LyricsContent? {
        return try {
            val uri = mediaUriString.toUri()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractFromStream(stream)
            }
        } catch (e: Exception) {
            Timber.d(e, "Could not extract embedded lyrics from %s", mediaUriString)
            null
        }
    }

    fun extractFromStream(stream: InputStream): LyricsContent? {
        val header = ByteArray(10)
        var read = 0
        while (read < 10) {
            val r = stream.read(header, read, 10 - read)
            if (r == -1) return null
            read += r
        }

        // Check for ID3v2 tag: "ID3"
        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val majorVersion = header[3].toInt() and 0xFF
            val tagSize = decodeSyncSafeInt(header, 6)
            if (tagSize <= 0) return null

            val actualReadSize = minOf(tagSize, MAX_TAG_HEADER_SEARCH_BYTES)
            val tagData = ByteArray(actualReadSize)
            var totalRead = 0
            while (totalRead < actualReadSize) {
                val r = stream.read(tagData, totalRead, actualReadSize - totalRead)
                if (r == -1) break
                totalRead += r
            }

            return parseId3v2Tag(tagData, totalRead, majorVersion)
        }

        return null
    }

    private fun parseId3v2Tag(tagData: ByteArray, length: Int, majorVersion: Int): LyricsContent? {
        var offset = 0
        while (offset + 10 <= length) {
            // Check for padding (zero bytes)
            if (tagData[offset] == 0.toByte()) break

            val frameId = String(tagData, offset, 4, StandardCharsets.US_ASCII)
            val frameSize = if (majorVersion >= 4) {
                decodeSyncSafeInt(tagData, offset + 4)
            } else {
                decode32BitInt(tagData, offset + 4)
            }

            offset += 10
            if (frameSize <= 0 || offset + frameSize > length) break

            when (frameId) {
                "USLT" -> {
                    val lyricsText = parseUsltFrame(tagData, offset, frameSize)
                    if (!lyricsText.isNullOrBlank()) {
                        // Embedded USLT often contains LRC-formatted text.
                        // LrcParser will detect timestamps if present.
                        val parsed = LrcParser.parse(lyricsText, source = LyricsSource.EMBEDDED)
                        if (parsed.lines.isNotEmpty()) return parsed
                    }
                }
                "SYLT" -> {
                    val syltLyrics = parseSyltFrame(tagData, offset, frameSize)
                    if (syltLyrics != null && syltLyrics.lines.isNotEmpty()) {
                        return syltLyrics
                    }
                }
            }

            offset += frameSize
        }
        return null
    }

    private fun parseUsltFrame(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 5) return null
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)

        // 3 bytes language code (offset + 1 .. offset + 3)
        var descriptorEnd = offset + 4
        val delimiterSize = if (encodingByte == 1 || encodingByte == 2) 2 else 1

        // Skip descriptor string
        while (descriptorEnd + delimiterSize <= offset + size) {
            if (delimiterSize == 1) {
                if (data[descriptorEnd] == 0.toByte()) {
                    descriptorEnd += 1
                    break
                }
                descriptorEnd++
            } else {
                if (data[descriptorEnd] == 0.toByte() && data[descriptorEnd + 1] == 0.toByte()) {
                    descriptorEnd += 2
                    break
                }
                descriptorEnd += 2
            }
        }

        val textLength = (offset + size) - descriptorEnd
        if (textLength <= 0) return null

        return String(data, descriptorEnd, textLength, charset).trim()
    }

    private fun parseSyltFrame(data: ByteArray, offset: Int, size: Int): LyricsContent? {
        if (size < 7) return null
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val timeFormat = data[offset + 4].toInt() and 0xFF
        // ID3v2 spec: 2 = ms, 1 = MPEG frames (some taggers use 1 for ms)
        if (timeFormat != 1 && timeFormat != 2) return null

        var pos = offset + 6
        val delimiterSize = if (encodingByte == 1 || encodingByte == 2) 2 else 1

        // Skip content descriptor
        while (pos + delimiterSize <= offset + size) {
            if (delimiterSize == 1 && data[pos] == 0.toByte()) {
                pos += 1
                break
            } else if (delimiterSize == 2 && data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                pos += 2
                break
            }
            pos += delimiterSize
        }

        val lines = mutableListOf<LyricsLine>()
        while (pos + 4 <= offset + size) {
            val textStart = pos
            while (pos + delimiterSize <= offset + size) {
                if (delimiterSize == 1 && data[pos] == 0.toByte()) {
                    pos += 1
                    break
                } else if (delimiterSize == 2 && data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    pos += 2
                    break
                }
                pos += delimiterSize
            }
            val textLen = (pos - delimiterSize) - textStart
            if (pos + 4 > offset + size) break
            val timestampMs = decode32BitInt(data, pos).toLong().coerceAtLeast(0L)
            pos += 4

            if (textLen > 0) {
                val text = String(data, textStart, textLen, charset).trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricsLine(timestampMs = timestampMs, text = text))
                }
            }
        }

        return if (lines.isNotEmpty()) {
            lines.sortBy { it.timestampMs }
            LyricsContent(source = LyricsSource.EMBEDDED, isSynced = true, lines = lines)
        } else null
    }

    private fun getCharset(encodingByte: Int): Charset = when (encodingByte) {
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }

    private fun decodeSyncSafeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
                ((data[offset + 1].toInt() and 0x7F) shl 14) or
                ((data[offset + 2].toInt() and 0x7F) shl 7) or
                (data[offset + 3].toInt() and 0x7F)
    }

    private fun decode32BitInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }
}
