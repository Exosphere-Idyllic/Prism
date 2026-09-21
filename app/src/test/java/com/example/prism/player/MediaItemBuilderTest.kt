package com.example.prism.player

import android.content.ContentUris
import android.provider.MediaStore
import com.example.prism.data.entity.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemBuilderTest {

    @Test
    fun buildMediaItem_setsCorrectMetadata() {
        val song = Song(
            id = "42",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            albumId = 7L,
            mediaUri = "content://media/external/audio/media/42",
            artworkUri = "content://media/external/audio/albumart/7",
            duration = 354000L,
            dateModified = 123456789L,
        )

        val mediaItem = song.toMediaItem()

        assertEquals("42", mediaItem.mediaId)
        assertEquals("Bohemian Rhapsody", mediaItem.mediaMetadata.title.toString())
        assertEquals("Queen", mediaItem.mediaMetadata.artist.toString())
        assertEquals("A Night at the Opera", mediaItem.mediaMetadata.albumTitle.toString())
    }

    @Test
    fun buildMediaItem_setsTrackNumberWhenGreaterThanZero() {
        val song = Song(
            id = "42",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            albumId = 7L,
            mediaUri = "content://media/external/audio/media/42",
            artworkUri = "",
            duration = 354000L,
            dateModified = 123456789L,
            track = 11,
        )

        val mediaItem = song.toMediaItem()

        assertEquals(11, mediaItem.mediaMetadata.trackNumber)
    }

    @Test
    fun buildMediaItem_fallsBackToAlbumIdUriWhenArtworkUriEmpty() {
        val song = Song(
            id = "42",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            albumId = 7L,
            mediaUri = "content://media/external/audio/media/42",
            artworkUri = "",
            duration = 354000L,
            dateModified = 123456789L,
        )

        val mediaItem = song.toMediaItem()
        val expectedUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, 7L).toString()

        assertEquals(expectedUri, mediaItem.mediaMetadata.artworkUri.toString())
    }

    @Test
    fun buildMediaItem_prefersCustomArtworkUri() {
        val song = Song(
            id = "43",
            title = "Another One Bites the Dust",
            artist = "Queen",
            album = "The Game",
            albumId = 8L,
            mediaUri = "content://media/external/audio/media/43",
            artworkUri = "content://media/external/audio/albumart/8",
            customArtworkUri = "file:///custom/art.jpg",
            duration = 215000L,
            dateModified = 123456789L,
        )

        val mediaItem = song.toMediaItem()

        assertEquals("file:///custom/art.jpg", mediaItem.mediaMetadata.artworkUri.toString())
    }

    @Test
    fun buildMediaItems_mapsAllSongs() {
        val songs = listOf(
            Song(id = "1", title = "Song 1", artist = "Artist 1", album = "Album 1", albumId = 1L, mediaUri = "content://media/1", artworkUri = "", duration = 1000L, dateModified = 1L),
            Song(id = "2", title = "Song 2", artist = "Artist 2", album = "Album 2", albumId = 2L, mediaUri = "content://media/2", artworkUri = "", duration = 2000L, dateModified = 2L),
        )

        val mediaItems = songs.toMediaItems()

        assertEquals(2, mediaItems.size)
        assertEquals("1", mediaItems[0].mediaId)
        assertEquals("2", mediaItems[1].mediaId)
    }
}
