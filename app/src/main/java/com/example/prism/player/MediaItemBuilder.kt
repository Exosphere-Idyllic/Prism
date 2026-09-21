package com.example.prism.player

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.prism.data.entity.Song

/**
 * Builds an AndroidX Media3 [MediaItem] with complete metadata for playback,
 * lock-screen controls, and system notifications.
 *
 * Artwork URI resolution order:
 *  1. [Song.customArtworkUri] (user-selected custom art — highest priority)
 *  2. [Song.artworkUri] (MediaStore-resolved album art URI)
 *  3. MediaStore Albums provider URI via [MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI]
 *  4. null (fallback to placeholder in UI)
 */
fun Song.toMediaItem(): MediaItem {
    val parsedArtworkUri: Uri? = when {
        customArtworkUri.isNotEmpty() -> customArtworkUri.toUri()
        artworkUri.isNotEmpty() -> artworkUri.toUri()
        albumId > 0 -> ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        else -> null
    }

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(parsedArtworkUri)
        .apply {
            if (track > 0) setTrackNumber(track)
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(mediaUri.toUri())
        .setMediaMetadata(metadata)
        .build()
}

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }
