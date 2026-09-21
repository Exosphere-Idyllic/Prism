package com.example.prism.data.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
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
import okio.source
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
 *  1. Custom user-selected cover URI ([customArtworkUri])
 *  2. `ContentResolver.loadThumbnail` (API 29+)
 *  3. Fallback [android.content.ContentResolver.openInputStream] on coverUri
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
            return if (mediaUri != null) "song_${albumId}_${mediaUri.hashCode()}$customHash$modHash"
            else "album_${albumId}_${artworkUri.hashCode()}$customHash"
        }

        /**
         * Clears the in-memory negative cache, e.g. after a MediaStore rescan.
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

        if (result == null) {
            noArtworkCache.add(cacheKey)
        }

        result
    }

    // ── Song artwork ─────────────────────────────────────────────────────────

    private fun fetchSongArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected artwork URI (highest priority — supports content://, file://, http://, https:// via OkHttp)
        if (customArtworkUri.isNotEmpty()) {
            fetchStreamFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: System thumbnail by song MediaStore URI (API 29+) — fast OS-level cache
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && !mediaUri.isNullOrEmpty()) {
            fetchSystemThumbnail(mediaUri.toUri(), signal)?.let { return bitmapToResult(it) }
        }

        // Strategy 3: System thumbnail by albumId (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (albumId > 0)) {
            val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
            fetchSystemThumbnail(albumUri, signal)?.let { return bitmapToResult(it) }
        }

        // Strategy 4: Embedded ID3 artwork via MediaMetadataRetriever (fallback)
        mediaUri?.let { extractEmbeddedArtwork(it) }?.let { return it }

        // Strategy 5: Fallback to generic album art URI
        if (artworkUri.isNotEmpty()) {
            fetchStreamFromContentUri(artworkUri)?.let { return it }
        }

        return null
    }

    // ── Album artwork ────────────────────────────────────────────────────────

    private fun fetchAlbumArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected cover URI (highest priority — supports content://, file://, http://, https:// via OkHttp)
        if (customArtworkUri.isNotEmpty()) {
            fetchStreamFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: System thumbnail (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (albumId > 0)) {
            val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
            fetchSystemThumbnail(albumUri, signal)?.let { return bitmapToResult(it) }
        }

        // Strategy 3: Fallback openInputStream on coverUri
        if (artworkUri.isNotEmpty()) {
            fetchStreamFromContentUri(artworkUri)?.let { return it }
        }

        return null
    }

    // ── Shared strategies ────────────────────────────────────────────────────

    /**
     * Unified thumbnail loader for both song and album URIs (API 29+).
     */
    private fun fetchSystemThumbnail(uri: android.net.Uri, signal: CancellationSignal): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val thumbSize = MASTER_ARTWORK_SIZE.coerceAtLeast(requestedSize)
            context.contentResolver.loadThumbnail(
                uri,
                Size(thumbSize, thumbSize),
                signal,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchStreamFromContentUri(uri: String): FetchResult? {
        return try {
            val parsedUri = uri.toUri()
            val stream = context.contentResolver.openInputStream(parsedUri)
            stream?.let { streamToResult(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchStreamFromCustomUri(uri: String): FetchResult? {
        return try {
            val parsedUri = uri.toUri()
            val scheme = parsedUri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                fetchNetworkStream(uri)
            } else {
                fetchStreamFromContentUri(uri)
            }
        } catch (_: Exception) { null }
    }

    private fun fetchNetworkStream(url: String): FetchResult? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body ?: return null
            streamToResult(body.byteStream())
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
            val buffer = Buffer().write(picture)
            SourceFetchResult(
                source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
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

    /**
     * Converts an [java.io.InputStream] to an Okio-backed [SourceFetchResult].
     * Uses [java.io.InputStream.source] (Okio extension) + buffering instead of a
     * manual 8 KB copy loop, capping at [MAX_STREAM_BYTES] to avoid OOMs.
     */
    private fun streamToResult(stream: java.io.InputStream): SourceFetchResult {
        val buffer = Buffer()
        stream.use { input ->
            buffer.write(input.source(), MAX_STREAM_BYTES)
        }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = null,
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
