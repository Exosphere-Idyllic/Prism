package com.example.melodyplayer

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.melodyplayer.data.Song

object MediaItemBuilder {

    fun buildMediaItem(song: Song, includeArtwork: Boolean): MediaItem {
        val artworkUri = if (includeArtwork && song.artworkUri.isNotEmpty()) {
            song.artworkUri.toUri()
        } else {
            null
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.mediaUri.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
    }

    fun buildMediaItems(songs: List<Song>, currentSongId: String?): List<MediaItem> {
        return songs.map { song ->
            buildMediaItem(song, includeArtwork = (song.id == currentSongId))
        }
    }
}
