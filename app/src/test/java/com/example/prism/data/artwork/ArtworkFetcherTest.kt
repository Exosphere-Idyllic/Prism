package com.example.prism.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFetcherTest {

    @Test
    fun negativeCacheKey_generatesDistinctKeys() {
        val songKey = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "", "media1")
        val albumKey = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "", null)
        assertNotEquals(songKey, albumKey)
        assertTrue(songKey.startsWith("song_"))
        assertTrue(albumKey.startsWith("album_"))
    }

    @Test
    fun negativeCacheKey_includesDateModified_forSongs() {
        val keyOld = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "", "media1", dateModified = 1000L)
        val keyNew = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "", "media1", dateModified = 2000L)
        assertNotEquals(keyOld, keyNew)
        assertTrue(keyOld.contains("_m1000"))
        assertTrue(keyNew.contains("_m2000"))
    }

    @Test
    fun negativeCacheKey_handlesCustomArtworkUri() {
        val keyDefault = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "", "media1")
        val keyCustom = AlbumArtFetcher.negativeCacheKey(1L, "uri1", "custom_uri", "media1")
        assertNotEquals(keyDefault, keyCustom)
        assertTrue(keyCustom.contains("_c"))
    }

    @Test
    fun albumArtworkKeyer_producesValidCacheKeyWithoutIllegalChars() {
        val keyer = AlbumArtworkKeyer()
        val params = AlbumArtworkParams(
            albumId = 42L,
            coverUri = "content://media/external/audio/albums/42",
            customCoverUri = "https://example.com/art:work/1.jpg",
            size = 256,
        )
        val key = keyer.key(params, coil3.request.Options(android.app.Application()))
        assertTrue(key.matches(Regex("^[a-zA-Z0-9_-]+$")))
    }

    @Test
    fun songArtworkKeyer_unifiesKeyAcrossDifferentSizes() {
        val keyer = SongArtworkKeyer()
        val song = com.example.prism.data.entity.Song(
            id = "song_1",
            title = "Test",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            mediaUri = "content://media/1",
            artworkUri = "",
            duration = 180000L,
            dateModified = 123456L,
        )
        val options = coil3.request.Options(android.app.Application())
        val key160 = keyer.key(SongArtworkParams(song = song, size = 160), options)
        val key512 = keyer.key(SongArtworkParams(song = song, size = 512), options)
        assertEquals(key160, key512)
        assertEquals("sa_song_1_123456", key160)
    }

    @Test
    fun albumArtworkKeyer_unifiesKeyAcrossDifferentSizes() {
        val keyer = AlbumArtworkKeyer()
        val options = coil3.request.Options(android.app.Application())
        val key256 = keyer.key(AlbumArtworkParams(albumId = 42L, coverUri = "uri", size = 256), options)
        val key512 = keyer.key(AlbumArtworkParams(albumId = 42L, coverUri = "uri", size = 512), options)
        assertEquals(key256, key512)
    }
}

