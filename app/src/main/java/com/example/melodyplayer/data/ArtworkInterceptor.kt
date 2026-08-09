package com.example.melodyplayer.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.InputStream

/**
 * Request params for a song artwork image.
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
 * Coil [Fetcher] that loads album artwork using a multi-strategy approach:
 *  1. [ContentResolver.loadThumbnail] via Albums URI (API 29+)
 *  2. [ContentResolver.openInputStream] on the artwork URI
 *  3. Embedded ID3 artwork via [MediaMetadataRetriever]
 *
 * All caching (memory + disk), resizing, and bitmap management is delegated
 * entirely to Coil's built-in pipeline, eliminating custom thumbnail classes.
 */
class AlbumArtFetcher(
    private val albumId: Long,
    private val artworkUri: String,
    private val mediaUri: String?,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Strategy 1: loadThumbnail via Albums content URI (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && albumId > 0) {
            val albumsUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
            )
            try {
                val stream = context.contentResolver.openInputStream(albumsUri)
                if (stream != null) {
                    return streamToResult(stream)
                }
            } catch (_: Exception) { /* fall through */ }

            // Try the raw artworkUri via openInputStream
            if (artworkUri.isNotEmpty()) {
                try {
                    val stream = context.contentResolver.openInputStream(artworkUri.toUri())
                    if (stream != null) {
                        return streamToResult(stream)
                    }
                } catch (_: Exception) { /* fall through */ }
            }
        }

        // Strategy 2: openInputStream on the artworkUri
        if (artworkUri.isNotEmpty()) {
            try {
                val stream = context.contentResolver.openInputStream(artworkUri.toUri())
                if (stream != null) {
                    return streamToResult(stream)
                }
            } catch (_: Exception) { /* fall through */ }
        }

        // Strategy 3: Extract embedded artwork via MediaMetadataRetriever
        val uriForRetriever = if (!mediaUri.isNullOrEmpty()) mediaUri
            else if (artworkUri.isNotEmpty()) artworkUri
            else return null

        return extractEmbeddedArtwork(uriForRetriever)
    }

    private fun extractEmbeddedArtwork(uri: String): FetchResult? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            val picture = retriever.embeddedPicture ?: return null
            val buffer = Buffer().write(picture)
            return SourceFetchResult(
                source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        } catch (_: Exception) {
            return null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun streamToResult(stream: InputStream): SourceFetchResult {
        val buffer = Buffer().apply {
            stream.use { inputStream ->
                val bytes = inputStream.readBytes()
                write(bytes)
            }
        }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class SongFactory : Fetcher.Factory<SongArtworkParams> {
        override fun create(
            data: SongArtworkParams,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return AlbumArtFetcher(
                albumId = data.song.albumId,
                artworkUri = data.song.artworkUri,
                mediaUri = data.song.mediaUri,
                context = options.context
            )
        }
    }

    class AlbumFactory : Fetcher.Factory<AlbumArtworkParams> {
        override fun create(
            data: AlbumArtworkParams,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return AlbumArtFetcher(
                albumId = data.albumId,
                artworkUri = data.coverUri,
                mediaUri = null,
                context = options.context
            )
        }
    }
}
