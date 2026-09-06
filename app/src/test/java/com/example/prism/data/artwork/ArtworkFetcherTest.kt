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
}

