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
        // Size is excluded so all components (list, mini player, full player) share the same disk cache entry.
        val customPart = if (data.song.customArtworkUri.isNotEmpty())
            "_c${data.song.customArtworkUri.hashCode()}" else ""
        val artPart = if (data.song.artworkUri.isNotEmpty())
            "_a${data.song.artworkUri.hashCode()}" else ""
        return "sa_${data.song.id}_${data.song.dateModified}$customPart$artPart"
    }
}

/**
 * Coil [Keyer] for [AlbumArtworkParams].
 */
class AlbumArtworkKeyer : Keyer<AlbumArtworkParams> {
    override fun key(data: AlbumArtworkParams, options: Options): String {
        val customPart = if (data.customCoverUri.isNotEmpty()) "_c${data.customCoverUri.hashCode()}" else ""
        val coverPart = if (data.coverUri.isNotEmpty() && !data.coverUri.contains("audio/albums") && !data.coverUri.contains("audio/albumart"))
            "_a${data.coverUri.hashCode()}" else ""
        return "aa_${data.albumId}$customPart$coverPart"
    }
}

/**
 * Coil [Keyer] for MediaStore [android.net.Uri] instances.
 * Maps album URIs to the unified album cache key so notifications and UI share cached bitmaps.
 */
class MediaStoreArtworkKeyer : Keyer<android.net.Uri> {
    override fun key(data: android.net.Uri, options: Options): String? {
        val uriStr = data.toString()
        return when {
            uriStr.contains("audio/albums") || uriStr.contains("audio/albumart") -> {
                val albumId = try {
                    android.content.ContentUris.parseId(data)
                } catch (_: Exception) {
                    0L
                }
                if (albumId > 0L) "aa_$albumId" else null
            }
            uriStr.contains("audio/media") -> {
                val songId = try {
                    android.content.ContentUris.parseId(data)
                } catch (_: Exception) {
                    0L
                }
                if (songId > 0L) "sa_media_$songId" else null
            }
            else -> null
        }
    }
}

