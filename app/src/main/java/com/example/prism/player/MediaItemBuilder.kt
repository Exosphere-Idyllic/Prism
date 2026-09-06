package com.example.prism.player

import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.prism.data.entity.Song

fun Song.toMediaItem(): MediaItem {
    val uriString = customArtworkUri.ifEmpty {
        if (artworkUri.startsWith("content://media/external/audio/albumart/") && albumId > 0) {
            ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId).toString()
        } else {
            artworkUri
        }
    }
    val parsedArtworkUri = if (uriString.isNotEmpty()) uriString.toUri() else null

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(mediaUri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(parsedArtworkUri)
                .build()
        )
        .build()
}

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

