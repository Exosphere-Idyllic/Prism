package com.example.prism.data.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * High-performance Coil [Fetcher] that loads artwork using a multi-strategy fallback approach.
 *
 * All successful fetches decode directly to a sampled, memory-efficient [Bitmap] (RGB_565 for thumbnails)
 * returning an [ImageFetchResult], bypassing intermediate Okio buffer copies and format sniffers.
 *
 * ## Fetch strategies:
 * ### For Songs:
 *  1. Custom user-selected artwork URI ([customArtworkUri] — HTTP/HTTPS, file://, or content://)
 *  2. `ContentResolver.loadThumbnail` (API 29+) by song MediaStore URI
 *  3. Embedded ID3 artwork from the song file itself via [MediaMetadataRetriever]
 *  4. Fallback on API < 29 via album content URI
 *
 * ### For Albums:
 *  1. Custom user-selected cover URI ([customArtworkUri])
 *  2. Query representative song for the album and fetch its system thumbnail / embedded ID3 art
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
            ),
        )

        /**
         * Builds the negative-cache key for a given artwork request.
         */
        fun negativeCacheKey(
            albumId: Long,
            artworkUri: String,
            customArtworkUri: String,
            mediaUri: String?,
            dateModified: Long = 0L,
        ): String {
            val customHash = if (customArtworkUri.isNotEmpty()) "_c${customArtworkUri.hashCode()}" else ""
            val modHash = if (dateModified > 0L) "_m$dateModified" else ""
            return if (!mediaUri.isNullOrEmpty()) "song_${albumId}_${mediaUri.hashCode()}$customHash$modHash"
            else "album_${albumId}_${artworkUri.hashCode()}$customHash"
        }

        /**
         * Clears the in-memory negative cache, e.g. after a MediaStore rescan or artwork update.
         */
        fun clearNegativeCache() {
            noArtworkCache.clear()
        }

        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10L, TimeUnit.SECONDS)
                .readTimeout(15L, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Decodes and downsamples a [ByteArray] to the requested resolution using RGB_565
         * to cut heap memory allocation in half and avoid GC pauses during fast scrolling.
         */
        fun decodeSampledBitmap(data: ByteArray, targetSize: Int): Bitmap? {
            if (data.isEmpty()) return null
            val reqSize = if (targetSize > 0) targetSize.coerceIn(64, 1024) else MASTER_ARTWORK_SIZE

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)

            var inSampleSize = 1
            val height = boundsOptions.outHeight
            val width = boundsOptions.outWidth
            if (height > reqSize || width > reqSize) {
                while ((height / (inSampleSize * 2)) >= reqSize && (width / (inSampleSize * 2)) >= reqSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return try {
                BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val cacheKey = negativeCacheKey(albumId, artworkUri, customArtworkUri, mediaUri, dateModified)

        // Fast path: in-memory negative cache
        if (noArtworkCache.contains(cacheKey)) {
            return@withContext null
        }

        val signal = CancellationSignal()
        val job = coroutineContext[Job]
        job?.invokeOnCompletion { signal.cancel() }

        val result = if (!mediaUri.isNullOrEmpty()) {
            fetchSongArtwork(signal)
        } else {
            fetchAlbumArtwork(signal)
        }

        if (result == null && coroutineContext.isActive && !signal.isCanceled) {
            noArtworkCache.add(cacheKey)
        }

        result
    }

    // ── Song artwork ─────────────────────────────────────────────────────────

    private fun fetchSongArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected artwork URI (highest priority)
        if (customArtworkUri.isNotEmpty()) {
            fetchFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: System thumbnail by song MediaStore URI (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && !mediaUri.isNullOrEmpty()) {
            fetchSystemThumbnail(mediaUri.toUri(), signal)?.let { return bitmapToResult(it) }
        }

        // Strategy 3: Embedded ID3 artwork via MediaMetadataRetriever
        if (!mediaUri.isNullOrEmpty()) {
            extractEmbeddedArtwork(mediaUri)?.let { return it }
        }

        // Strategy 4: Fallback on legacy Android (< API 29)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && artworkUri.isNotEmpty()) {
            fetchFromCustomUri(artworkUri)?.let { return it }
        }

        return null
    }

    // ── Album artwork ────────────────────────────────────────────────────────

    private fun fetchAlbumArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected cover URI
        if (customArtworkUri.isNotEmpty()) {
            fetchFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: Look up a song from this album and extract its artwork
        if (albumId > 0) {
            fetchAlbumSongArtwork(albumId, signal)?.let { return it }
        }

        // Strategy 3: Fallback on legacy Android (< API 29)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && artworkUri.isNotEmpty()) {
            fetchFromCustomUri(artworkUri)?.let { return it }
        }

        return null
    }

    private fun fetchAlbumSongArtwork(albumId: Long, signal: CancellationSignal): FetchResult? {
        val songUri = querySongUriForAlbum(albumId) ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fetchSystemThumbnail(songUri, signal)?.let { return bitmapToResult(it) }
        }

        return extractEmbeddedArtwork(songUri.toString())
    }

    private fun querySongUriForAlbum(albumId: Long): android.net.Uri? {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val selectionArgs = arrayOf(albumId.toString())
        return try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(uri, id)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Shared strategies ────────────────────────────────────────────────────

    private fun fetchSystemThumbnail(uri: android.net.Uri, signal: CancellationSignal): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val thumbSize = if (requestedSize > 0) requestedSize.coerceIn(64, 1024) else MASTER_ARTWORK_SIZE
            context.contentResolver.loadThumbnail(
                uri,
                Size(thumbSize, thumbSize),
                signal,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromCustomUri(uri: String): FetchResult? {
        return try {
            val parsedUri = uri.toUri()
            val scheme = parsedUri.scheme?.lowercase()
            when {
                scheme == "http" || scheme == "https" -> fetchNetworkBitmap(uri)
                scheme == "file" || scheme.isNullOrEmpty() -> {
                    val path = parsedUri.path ?: uri
                    val file = File(path)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        decodeSampledBitmap(bytes, requestedSize)?.let { bitmapToResult(it) }
                    } else null
                }
                else -> {
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        val bytes = stream.readBytes()
                        decodeSampledBitmap(bytes, requestedSize)?.let { bitmapToResult(it) }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchNetworkBitmap(url: String): FetchResult? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val bytes = response.body?.bytes() ?: return null
            decodeSampledBitmap(bytes, requestedSize)?.let { bitmapToResult(it) }
        } catch (_: Exception) {
            null
        }
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
            decodeSampledBitmap(picture, requestedSize)?.let { bitmapToResult(it) }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun bitmapToResult(bitmap: Bitmap): FetchResult {
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class SongFactory : Fetcher.Factory<SongArtworkParams> {
        override fun create(
            data: SongArtworkParams,
            options: Options,
            imageLoader: ImageLoader,
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
            imageLoader: ImageLoader,
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
