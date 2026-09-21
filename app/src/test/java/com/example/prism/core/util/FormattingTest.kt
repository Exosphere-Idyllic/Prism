package com.example.prism.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun formatTime_formatsMillisecondsCorrectly() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:05", formatTime(5000L))
        assertEquals("01:05", formatTime(65000L))
        assertEquals("1:00:00", formatTime(3600000L))
    }

    @Test
    fun resolveArtistName_returnsOriginalWhenValid() {
        assertEquals("Coldplay", resolveArtistName("Coldplay", "Unknown"))
        assertEquals("Pink Floyd", resolveArtistName("Pink Floyd", "Desconocido"))
    }

    @Test
    fun resolveArtistName_returnsFallbackWhenNullOrBlank() {
        assertEquals("Artista Desconocido", resolveArtistName(null, "Artista Desconocido"))
        assertEquals("Artista Desconocido", resolveArtistName("", "Artista Desconocido"))
        assertEquals("Artista Desconocido", resolveArtistName("   ", "Artista Desconocido"))
    }

    @Test
    fun resolveArtistName_returnsFallbackWhenUnknownLiteral() {
        assertEquals("Artista Desconocido", resolveArtistName("Unknown", "Artista Desconocido"))
        assertEquals("Artista Desconocido", resolveArtistName("unknown", "Artista Desconocido"))
        assertEquals("Artista Desconocido", resolveArtistName("UNKNOWN", "Artista Desconocido"))
    }
}
