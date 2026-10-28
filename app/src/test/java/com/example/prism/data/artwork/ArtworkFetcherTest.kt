package com.example.prism.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun mediaStoreArtworkKeyer_mapsAlbumUrisCorrectly() {
        val keyer = MediaStoreArtworkKeyer()
        val options = coil3.request.Options(android.app.Application())
        val albumUri = android.net.Uri.parse("content://media/external/audio/albums/42")
        val key = keyer.key(albumUri, options)
        assertEquals("aa_42", key)
    }

    @Test
    fun albumArtworkKeyer_matchesMediaStoreArtworkKeyerForAlbumUris() {
        val albumKeyer = AlbumArtworkKeyer()
        val mediaStoreKeyer = MediaStoreArtworkKeyer()
        val options = coil3.request.Options(android.app.Application())
        val albumUri = android.net.Uri.parse("content://media/external/audio/albums/42")

        val keyFromParams = albumKeyer.key(
            AlbumArtworkParams(albumId = 42L, coverUri = albumUri.toString()),
            options
        )
        val keyFromUri = mediaStoreKeyer.key(albumUri, options)

        assertEquals("aa_42", keyFromParams)
        assertEquals(keyFromParams, keyFromUri)
    }

    @Test
    fun mediaStoreArtworkKeyer_mapsSongUrisCorrectly() {
        val keyer = MediaStoreArtworkKeyer()
        val options = coil3.request.Options(android.app.Application())
        val songUri = android.net.Uri.parse("content://media/external/audio/media/100")
        val key = keyer.key(songUri, options)
        assertEquals("sa_media_100", key)
    }

    @Test
    fun mediaStoreArtworkKeyer_returnsNullForNonAudioUris() {
        val keyer = MediaStoreArtworkKeyer()
        val options = coil3.request.Options(android.app.Application())
        val imageUri = android.net.Uri.parse("content://media/external/images/media/5")
        val key = keyer.key(imageUri, options)
        org.junit.Assert.assertNull(key)
    }

    @Test
    fun uriFactory_createsFetcherForAudioUris() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val factory = AlbumArtFetcher.UriFactory()
        val options = coil3.request.Options(app)
        val imageLoader = coil3.ImageLoader(app)

        val albumUri = android.net.Uri.parse("content://media/external/audio/albums/42")
        val fetcherAlbum = factory.create(albumUri, options, imageLoader)
        org.junit.Assert.assertNotNull(fetcherAlbum)

        val songUri = android.net.Uri.parse("content://media/external/audio/media/100")
        val fetcherSong = factory.create(songUri, options, imageLoader)
        org.junit.Assert.assertNotNull(fetcherSong)

        val webUri = android.net.Uri.parse("https://example.com/art.jpg")
        val fetcherWeb = factory.create(webUri, options, imageLoader)
        org.junit.Assert.assertNull(fetcherWeb)
    }

    @Test
    fun negativeCache_ttlExpiration() {
        val key = "test_key"
        val t0 = 100_000L
        AlbumArtFetcher.clearNegativeCache()

        AlbumArtFetcher.recordNegative(key, now = t0)
        // Immediately after insertion
        org.junit.Assert.assertTrue(AlbumArtFetcher.hasRecentNegative(key, now = t0))
        // 4 minutes later (within 5-min TTL)
        org.junit.Assert.assertTrue(AlbumArtFetcher.hasRecentNegative(key, now = t0 + 4 * 60 * 1000L))
        // Exactly 5 minutes + 1 ms later (expired)
        org.junit.Assert.assertFalse(AlbumArtFetcher.hasRecentNegative(key, now = t0 + AlbumArtFetcher.NEGATIVE_CACHE_TTL_MS + 1L))
        // Once expired, subsequent lookup is false as it got removed
        org.junit.Assert.assertFalse(AlbumArtFetcher.hasRecentNegative(key, now = t0 + 10L))
    }

    @Test
    fun negativeCache_clearNegativeCacheFor_removesMatchingPrefix() {
        AlbumArtFetcher.clearNegativeCache()
        val songKey = AlbumArtFetcher.negativeCacheKey(albumId = 5L, artworkUri = "", customArtworkUri = "", mediaUri = "content://media/10")
        val otherSongKey = AlbumArtFetcher.negativeCacheKey(albumId = 6L, artworkUri = "", customArtworkUri = "", mediaUri = "content://media/20")
        AlbumArtFetcher.recordNegative(songKey)
        AlbumArtFetcher.recordNegative(otherSongKey)

        org.junit.Assert.assertTrue(AlbumArtFetcher.hasRecentNegative(songKey))
        org.junit.Assert.assertTrue(AlbumArtFetcher.hasRecentNegative(otherSongKey))

        AlbumArtFetcher.clearNegativeCacheFor(albumId = 5L, mediaUri = "content://media/10")
        org.junit.Assert.assertFalse(AlbumArtFetcher.hasRecentNegative(songKey))
        org.junit.Assert.assertTrue(AlbumArtFetcher.hasRecentNegative(otherSongKey))
    }
}

