package com.example.melodyplayer.data

import android.net.Uri
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.ImageRequest

/**
 * Request params for a song artwork image.
 * Passed as the data payload in [ImageRequest]; the [ArtworkInterceptor] resolves
 * the actual URI at load time by checking whether a cached WebP exists on disk.
 */
data class SongArtworkParams(
    val song: Song,
    val size: Int = 128
)

/**
 * Request params for an album artwork image.
 */
data class AlbumArtworkParams(
    val albumId: Long,
    val coverUri: String,
    val size: Int = 256
)

/**
 * Coil [Interceptor] that centralises the "WebP cache → MediaStore fallback" logic.
 *
 * Instead of propagating boolean `hasWebp` flags through the entire Compose tree
 * (which causes N recompositions per thumbnail generated), we:
 *   1. Accept typed [SongArtworkParams] / [AlbumArtworkParams] as the request data.
 *   2. Check the in-memory Sets in [MusicRepository] to see if a WebP exists.
 *   3. Redirect the data to the local WebP file URI if available, or fall back to the
 *      original MediaStore / album-art URI.
 *
 * This makes artwork loading completely reactive without any StateFlow observations in
 * the UI — Coil automatically retries the request when the image key changes.
 */
class ArtworkInterceptor(private val repository: MusicRepository) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        return when (val data = request.data) {
            is SongArtworkParams -> {
                val resolvedUri = resolveSongUri(data)
                val newRequest = request.newBuilder().data(resolvedUri).build()
                chain.withRequest(newRequest).proceed()
            }
            is AlbumArtworkParams -> {
                val resolvedUri = resolveAlbumUri(data)
                val newRequest = request.newBuilder().data(resolvedUri).build()
                chain.withRequest(newRequest).proceed()
            }
            else -> chain.proceed()
        }
    }

    private fun resolveSongUri(params: SongArtworkParams): Any? {
        val song = params.song
        val size = params.size
        val sizeKey = if (size <= 128) 128 else 256

        // Check in-memory sets — O(1), no I/O
        val hasSongWebp = if (sizeKey == 128)
            repository.songThumbnail128Ids.value.contains(song.id)
        else
            repository.songThumbnail256Ids.value.contains(song.id)

        if (hasSongWebp) {
            val file = ThumbnailManager.getSongThumbnailFile(
                repository.appContext, song.id, sizeKey
            )
            return Uri.fromFile(file)
        }

        // Fall back to album WebP if available
        if (song.albumId > 0) {
            val hasAlbumWebp = if (sizeKey == 128)
                repository.albumThumbnail128Ids.value.contains(song.albumId)
            else
                repository.albumThumbnail256Ids.value.contains(song.albumId)

            if (hasAlbumWebp) {
                val file = ThumbnailManager.getAlbumThumbnailFile(
                    repository.appContext, song.albumId, sizeKey
                )
                return Uri.fromFile(file)
            }
        }

        // Fall back to original artwork URI
        return if (song.artworkUri.isNotEmpty()) song.artworkUri else null
    }

    private fun resolveAlbumUri(params: AlbumArtworkParams): Any? {
        val sizeKey = if (params.size <= 128) 128 else 256

        val hasAlbumWebp = if (sizeKey == 128)
            repository.albumThumbnail128Ids.value.contains(params.albumId)
        else
            repository.albumThumbnail256Ids.value.contains(params.albumId)

        if (hasAlbumWebp) {
            val file = ThumbnailManager.getAlbumThumbnailFile(
                repository.appContext, params.albumId, sizeKey
            )
            return Uri.fromFile(file)
        }

        return if (params.coverUri.isNotEmpty()) params.coverUri else null
    }
}
