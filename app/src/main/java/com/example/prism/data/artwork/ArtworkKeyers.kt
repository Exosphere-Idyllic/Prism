package com.example.prism.data.artwork

import coil3.key.Keyer
import coil3.request.Options

/**
 * Coil [Keyer] for [SongArtworkParams] — enables disk caching for song artwork.
 *
 * Without a Keyer, Coil cannot map custom data types to a cache key, so the
 * disk cache is never written to or read from, causing every artwork fetch to
 * hit the [AlbumArtFetcher] (and potentially JNI / MediaMetadataRetriever)
 * from scratch on every scroll.
 */
class SongArtworkKeyer : Keyer<SongArtworkParams> {
    override fun key(data: SongArtworkParams, options: Options): String {
        // Use hashCode() for URI fields to keep disk-cache key lengths compact.
        // This matches the pattern used in AlbumArtFetcher.negativeCacheKey().
        val customPart = if (data.song.customArtworkUri.isNotEmpty())
            "_c${data.song.customArtworkUri.hashCode()}" else ""
        val artPart = if (data.song.artworkUri.isNotEmpty())
            "_a${data.song.artworkUri.hashCode()}" else ""
        return "sa_${data.song.id}_${data.song.dateModified}_${data.size}$customPart$artPart"
    }
}

/**
 * Coil [Keyer] for [AlbumArtworkParams].
 */
class AlbumArtworkKeyer : Keyer<AlbumArtworkParams> {
    override fun key(data: AlbumArtworkParams, options: Options): String {
        val customPart = if (data.customCoverUri.isNotEmpty()) "_custom_${data.customCoverUri}" else ""
        val coverPart = if (data.coverUri.isNotEmpty()) "_cover_${data.coverUri}" else ""
        return "album_art_${data.albumId}_${data.size}$customPart$coverPart"
    }
}
