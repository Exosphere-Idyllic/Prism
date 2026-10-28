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
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.source

/**
 * High-performance Coil [Fetcher] that loads artwork using a multi-strategy fallback approach.
 *
 * All successful fetches return a [SourceFetchResult] containing raw image bytes, enabling Coil 3 to:
 * 1. Write the source data to its [coil3.disk.DiskCache] under a single unified master key.
 * 2. Downsample and decode directly to the exact target size requested by the UI.
 * 3. Store the downsampled [Bitmap] in [coil3.memory.MemoryCache] for instantaneous scroll rendering.
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
        const val MASTER_ARTWORK_SIZE = 512

        private data class NegativeEntry(val timestampMs: Long)

        /**
         * Thread-safe LRU negative cache.
         * Eviction: access-order LRU with a cap of 2000 entries (~80 KB RAM) and 5-min TTL.
         */
        private val noArtworkCache: MutableMap<String, NegativeEntry> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, NegativeEntry>(512, 0.75f, true) {
                    override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, NegativeEntry>?
                    ): Boolean = size > 2000
                }
            )

        internal var NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L

        internal fun hasRecentNegative(key: String, now: Long = System.currentTimeMillis()): Boolean {
            val entry = noArtworkCache[key] ?: return false
            if (now - entry.timestampMs > NEGATIVE_CACHE_TTL_MS) {
                noArtworkCache.remove(key)
                return false
            }
            return true
        }

        internal fun recordNegative(key: String, now: Long = System.currentTimeMillis()) {
            noArtworkCache[key] = NegativeEntry(now)
        }

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

        /**
         * Clears negative cache entries for a specific song or album when modified.
         */
        fun clearNegativeCacheFor(albumId: Long, mediaUri: String?) {
            if (!mediaUri.isNullOrEmpty()) {
                val prefix = "song_${albumId}_${mediaUri.hashCode()}"
                synchronized(noArtworkCache) {
                    noArtworkCache.keys.removeIf { it.startsWith(prefix) }
                }
            } else {
                val prefix = "album_${albumId}_"
                synchronized(noArtworkCache) {
                    noArtworkCache.keys.removeIf { it.startsWith(prefix) }
                }
            }
        }

        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10L, TimeUnit.SECONDS)
                .readTimeout(15L, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Limits concurrent [MediaMetadataRetriever] instances to prevent native memory exhaustion
         * and binder thread saturation during fast fling scrolling.
         */
        private val retrieverSemaphore = Semaphore(3)
    }

    // Note: Uses Dispatchers.IO directly instead of DispatcherProvider because Coil Fetcher
    // instances are created by Coil's Factory system without access to the DI container.
    // This is an intentional exception to the project's DispatcherProvider convention.
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        ArtworkProbe.onTotalFetch()
        ArtworkProbe.maybeReport()

        val cacheKey = negativeCacheKey(albumId, artworkUri, customArtworkUri, mediaUri, dateModified)

        // Fast path: in-memory negative cache
        if (hasRecentNegative(cacheKey)) {
            ArtworkProbe.onNegativeHit()
            return@withContext null
        }
        ArtworkProbe.onNegativeMiss()

        val signal = CancellationSignal()
        val job = coroutineContext[Job]
        job?.invokeOnCompletion { signal.cancel() }

        val result = if (!mediaUri.isNullOrEmpty()) {
            fetchSongArtwork(signal)
        } else {
            fetchAlbumArtwork(signal)
        }

        if (result == null && coroutineContext.isActive && !signal.isCanceled) {
            recordNegative(cacheKey)
            ArtworkProbe.onNullFetch()
        }

        result
    }

    // ── Song artwork ─────────────────────────────────────────────────────────

    private suspend fun fetchSongArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected artwork URI (highest priority)
        if (customArtworkUri.isNotEmpty()) {
            fetchFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: System thumbnail by song MediaStore URI (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && !mediaUri.isNullOrEmpty()) {
            fetchSystemThumbnail(mediaUri.toUri(), signal)?.let { bitmap ->
                ArtworkProbe.onSystemThumb()
                return bitmapToSourceResult(bitmap)
            }
        }

        // Strategy 3: Embedded ID3 artwork via MediaMetadataRetriever
        if (!mediaUri.isNullOrEmpty()) {
            extractEmbeddedArtwork(mediaUri)?.let {
                ArtworkProbe.onEmbedded()
                return it
            }
        }

        // Strategy 4: Fallback on legacy Android (< API 29)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && artworkUri.isNotEmpty()) {
            fetchFromCustomUri(artworkUri)?.let { return it }
        }

        return null
    }

    // ── Album artwork ────────────────────────────────────────────────────────

    private suspend fun fetchAlbumArtwork(signal: CancellationSignal): FetchResult? {
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

    private suspend fun fetchAlbumSongArtwork(albumId: Long, signal: CancellationSignal): FetchResult? {
        val songUri = querySongUriForAlbum(albumId) ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fetchSystemThumbnail(songUri, signal)?.let { bitmap ->
                ArtworkProbe.onSystemThumb()
                return bitmapToSourceResult(bitmap)
            }
        }

        return extractEmbeddedArtwork(songUri.toString())?.also {
            ArtworkProbe.onEmbedded()
        }
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
            context.contentResolver.loadThumbnail(
                uri,
                Size(MASTER_ARTWORK_SIZE, MASTER_ARTWORK_SIZE),
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
                    fileToSourceResult(File(path))?.also { ArtworkProbe.onCustomUri() }
                }
                else -> {
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        streamToSourceResult(stream)
                    }?.also { ArtworkProbe.onCustomUri() }
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
            ArtworkProbe.onNetwork()
            bytesToSourceResult(bytes, DataSource.NETWORK)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun extractEmbeddedArtwork(uri: String): FetchResult? {
        return retrieverSemaphore.withPermit {
            val retriever = MediaMetadataRetriever()
            try {
                val parsedUri = uri.toUri()
                if (parsedUri.scheme.isNullOrEmpty()) {
                    retriever.setDataSource(uri)
                } else {
                    retriever.setDataSource(context, parsedUri)
                }
                val picture = retriever.embeddedPicture ?: return@withPermit null
                bytesToSourceResult(picture)
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }
    }

    private fun bytesToSourceResult(bytes: ByteArray, dataSource: DataSource = DataSource.NETWORK): FetchResult {
        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = dataSource,
        )
    }

    private fun bitmapToSourceResult(bitmap: Bitmap, dataSource: DataSource = DataSource.NETWORK): FetchResult {
        val squareBitmap = cropToSquare(bitmap)
        val stream = ByteArrayOutputStream()
        squareBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        if (squareBitmap !== bitmap) {
            squareBitmap.recycle()
        }
        val bytes = stream.toByteArray()
        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = dataSource,
        )
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width == height) return bitmap
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun fileToSourceResult(file: File): FetchResult? {
        if (!file.exists()) return null
        return try {
            val buffer = Buffer()
            file.source().use { source ->
                buffer.writeAll(source)
            }
            SourceFetchResult(
                source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun streamToSourceResult(stream: java.io.InputStream): FetchResult {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
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

    class UriFactory : Fetcher.Factory<android.net.Uri> {
        override fun create(
            data: android.net.Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val uriStr = data.toString()
            val isAudioAlbum = uriStr.contains("audio/albums") || uriStr.contains("audio/albumart")
            val isAudioMedia = uriStr.contains("audio/media") ||
                (data.authority == MediaStore.AUTHORITY && data.path?.contains("audio") == true && data.path?.contains("albums") != true)

            return when {
                isAudioAlbum -> {
                    val albumId = try {
                        ContentUris.parseId(data)
                    } catch (_: Exception) {
                        0L
                    }
                    if (albumId > 0L) {
                        AlbumArtFetcher(
                            albumId = albumId,
                            artworkUri = uriStr,
                            customArtworkUri = "",
                            mediaUri = null,
                            context = options.context,
                            requestedSize = MASTER_ARTWORK_SIZE,
                        )
                    } else null
                }
                isAudioMedia -> {
                    AlbumArtFetcher(
                        albumId = 0L,
                        artworkUri = "",
                        customArtworkUri = "",
                        mediaUri = uriStr,
                        context = options.context,
                        requestedSize = MASTER_ARTWORK_SIZE,
                    )
                }
                else -> null
            }
        }
    }
}
