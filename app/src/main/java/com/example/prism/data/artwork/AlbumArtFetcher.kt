package com.example.prism.data.artwork

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.InputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Coil [Fetcher] that loads artwork using a multi-strategy fallback approach.
 *
 * ## Fetch strategies (in order)
 *
 * ### For Songs:
 *  1. Custom user-selected artwork URI ([customArtworkUri] — HTTP/HTTPS, file://, or content://)
 *  2. `ContentResolver.loadThumbnail` (API 29+) by song MediaStore URI — OS-level cached thumbnail
 *  3. `ContentResolver.loadThumbnail` (API 29+) by album ID via [MediaStore.Audio.Albums]
 *  4. Embedded ID3 artwork from the song file itself via [MediaMetadataRetriever]
 *  5. Fallback to generic [MediaStore.Audio.Albums] artwork by albumId content URI
 *  6. Returns `null` — the UI shows a hardcoded default drawable
 *
 * ### For Albums:
 *  1. Custom user-selected cover URI
 *  2. `ContentResolver.loadThumbnail` (API 29+)
 *  3. Fallback [ContentResolver.openInputStream] on coverUri
 *  4. Returns `null` — the UI shows a hardcoded default drawable
 */
class AlbumArtFetcher(
    private val albumId: Long,
    private val artworkUri: String,
    private val customArtworkUri: String,
    private val mediaUri: String?,
    private val context: Context,
    private val requestedSize: Int,
    private val dateModified: Long = 0L,
) : Fetcher {

    companion object {
        /** Maximum bytes read from a content/file stream for artwork. */
        private const val MAX_STREAM_BYTES = 5L * 1024 * 1024 // 5 MB

        /** Standard master resolution for cached thumbnails. */
        private const val MASTER_ARTWORK_SIZE = 512

        /**
         * Thread-safe LRU negative cache.
         * Eviction: access-order LRU with a cap of 2000 entries (~80 KB RAM).
         */
        private val noArtworkCache: MutableSet<String> = Collections.newSetFromMap(
            Collections.synchronizedMap(
                object : LinkedHashMap<String, Boolean>(512, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                        return size > 2000
                    }
                },
            )
        )

        /**
         * Builds the negative-cache key for a given artwork request.
         */
        fun negativeCacheKey(
            albumId: Long,
            artworkUri: String,
            customArtworkUri: String,
            mediaUri: String?,
            dateModified: Long = 0L
        ): String {
            val customHash = if (customArtworkUri.isNotEmpty()) "_c${customArtworkUri.hashCode()}" else ""
            val modHash = if (dateModified > 0L) "_m$dateModified" else ""
            return if (mediaUri != null) "song_${albumId}_${mediaUri.hashCode()}$customHash$modHash"
            else "album_${albumId}_${artworkUri.hashCode()}$customHash"
        }

        /**
         * Clears the in-memory negative cache, e.g. after a MediaStore rescan.
         */
        fun clearNegativeCache() {
            noArtworkCache.clear()
        }
    }

    override suspend fun fetch(): FetchResult? {
        val cacheKey = negativeCacheKey(albumId, artworkUri, customArtworkUri, mediaUri, dateModified)

        // Fast path: in-memory negative cache
        if (noArtworkCache.contains(cacheKey)) {
            return null
        }

        val result = if (!mediaUri.isNullOrEmpty()) {
            fetchSongArtwork()
        } else {
            fetchAlbumArtwork()
        }

        if (result == null) {
            noArtworkCache.add(cacheKey)
        }

        return result
    }

    // ── Song artwork ─────────────────────────────────────────────────────────

    private fun fetchSongArtwork(): FetchResult? {
        // Strategy 1: Custom user-selected artwork URI (highest priority)
        if (customArtworkUri.isNotEmpty()) {
            val customResult = fetchFromUri(customArtworkUri)
            if (customResult != null) return customResult
        }

        // Strategy 2: System thumbnail by song MediaStore URI (API 29+) — fast OS-level cache
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && !mediaUri.isNullOrEmpty()) {
            val songThumb = fetchSystemThumbnailBySongId(mediaUri)
            if (songThumb != null) {
                return bitmapToResult(songThumb)
            }
        }

        // Strategy 3: System thumbnail by albumId (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && albumId > 0) {
            val thumbnailBitmap = fetchSystemThumbnailBitmap(albumId)
            if (thumbnailBitmap != null) {
                return bitmapToResult(thumbnailBitmap)
            }
        }

        // Strategy 4: Embedded ID3 artwork via MediaMetadataRetriever (fallback)
        val embeddedResult = mediaUri?.let { extractEmbeddedArtwork(it) }
        if (embeddedResult != null) {
            return embeddedResult
        }

        // Strategy 5: Fallback to generic album art URI
        if (artworkUri.isNotEmpty()) {
            val fallbackBitmap = fetchBitmapFromContentUri(artworkUri)
            if (fallbackBitmap != null) {
                return bitmapToResult(fallbackBitmap)
            }
        }

        return null
    }

    // ── Album artwork ────────────────────────────────────────────────────────

    private fun fetchAlbumArtwork(): FetchResult? {
        // Strategy 1: Custom user-selected cover URI (highest priority)
        if (customArtworkUri.isNotEmpty()) {
            val customResult = fetchFromUri(customArtworkUri)
            if (customResult != null) return customResult
        }

        // Strategy 2: System thumbnail (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (albumId > 0)) {
            val thumbnailBitmap = fetchSystemThumbnailBitmap(albumId)
            if (thumbnailBitmap != null) {
                return bitmapToResult(thumbnailBitmap)
            }
        }

        // Strategy 3: Fallback openInputStream on coverUri
        if (artworkUri.isNotEmpty()) {
            val fallbackBitmap = fetchBitmapFromContentUri(artworkUri)
            if (fallbackBitmap != null) {
                return bitmapToResult(fallbackBitmap)
            }
        }

        return null
    }

    // ── Shared strategies ────────────────────────────────────────────────────

    private fun fetchFromUri(uri: String): FetchResult? {
        return fetchStreamFromContentUri(uri)
    }

    private fun fetchSystemThumbnailBitmap(albumId: Long): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val albumsUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
            )
            val thumbSize = MASTER_ARTWORK_SIZE.coerceAtLeast(requestedSize)
            var bitmap: Bitmap = context.contentResolver.loadThumbnail(
                albumsUri,
                Size(thumbSize, thumbSize),
                CancellationSignal()
            )
            bitmap = downscaleBitmapIfNeeded(bitmap, thumbSize)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchSystemThumbnailBySongId(songMediaUri: String): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val uri = songMediaUri.toUri()
            val thumbSize = MASTER_ARTWORK_SIZE.coerceAtLeast(requestedSize)
            var bitmap: Bitmap = context.contentResolver.loadThumbnail(
                uri,
                Size(thumbSize, thumbSize),
                CancellationSignal()
            )
            bitmap = downscaleBitmapIfNeeded(bitmap, thumbSize)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchBitmapFromContentUri(uri: String): Bitmap? {
        return try {
            val parsedUri = uri.toUri()

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: return null

            val targetSize = MASTER_ARTWORK_SIZE.coerceAtLeast(requestedSize)
            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetSize)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            downscaleBitmapIfNeeded(bitmap, targetSize)
        } catch (_: Exception) { null }
    }

    private fun fetchStreamFromContentUri(uri: String): FetchResult? {
        return try {
            val stream = context.contentResolver.openInputStream(uri.toUri())
            stream?.let { streamToResult(it) }
        } catch (_: Exception) { null }
    }

    private fun extractEmbeddedArtwork(uri: String): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            val parsedUri = uri.toUri()
            if (parsedUri.scheme.isNullOrEmpty()) {
                retriever.setDataSource(uri)
            } else {
                retriever.setDataSource(context, parsedUri)
            }
            val picture = retriever.embeddedPicture ?: return null

            val targetSize = MASTER_ARTWORK_SIZE.coerceAtLeast(requestedSize)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, boundsOpts)

            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetSize)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOpts)
                ?: return null

            val scaledBitmap = downscaleBitmapIfNeeded(bitmap, targetSize)
            bitmapToResult(scaledBitmap)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    // ── Bitmap helpers ───────────────────────────────────────────────────────

    private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
        var inSampleSize = 1
        if ((width > targetSize) || (height > targetSize)) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= targetSize &&
                   (halfHeight / inSampleSize) >= targetSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, targetSize: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val limit = targetSize * 2
        if (maxDim <= limit) return bitmap

        val scale = limit.toFloat() / maxDim
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(newW, newH, filter = true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun bitmapToResult(bitmap: Bitmap): FetchResult {
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun streamToResult(stream: InputStream): SourceFetchResult {
        val buffer = Buffer()
        stream.use { input ->
            val tmp = ByteArray(8192)
            var totalRead = 0L
            while (true) {
                val n = input.read(tmp)
                if (n == -1) break
                buffer.write(tmp, 0, n)
                totalRead += n
                if (totalRead > MAX_STREAM_BYTES) break
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
                customArtworkUri = data.song.customArtworkUri,
                mediaUri = data.song.mediaUri,
                context = options.context,
                requestedSize = data.size,
                dateModified = data.song.dateModified,
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
                customArtworkUri = data.customCoverUri,
                mediaUri = null,
                context = options.context,
                requestedSize = data.size,
                dateModified = 0L,
            )
        }
    }
}
