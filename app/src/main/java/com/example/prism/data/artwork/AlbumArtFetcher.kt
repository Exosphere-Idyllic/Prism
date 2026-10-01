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
         * Limits concurrent [MediaMetadataRetriever] instances to prevent native memory exhaustion
         * and binder thread saturation during fast fling scrolling.
         */
        private val retrieverSemaphore = Semaphore(3)
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

    private suspend fun fetchSongArtwork(signal: CancellationSignal): FetchResult? {
        // Strategy 1: Custom user-selected artwork URI (highest priority)
        if (customArtworkUri.isNotEmpty()) {
            fetchFromCustomUri(customArtworkUri)?.let { return it }
        }

        // Strategy 2: System thumbnail by song MediaStore URI (API 29+)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && !mediaUri.isNullOrEmpty()) {
            fetchSystemThumbnail(mediaUri.toUri(), signal)?.let { return bitmapToSourceResult(it) }
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
            fetchSystemThumbnail(songUri, signal)?.let { return bitmapToSourceResult(it) }
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
                    fileToSourceResult(File(path))
                }
                else -> {
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        streamToSourceResult(stream)
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
            val bytes = response.body.bytes()
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

    private fun bytesToSourceResult(bytes: ByteArray, dataSource: DataSource = DataSource.DISK): FetchResult {
        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = dataSource,
        )
    }

    private fun bitmapToSourceResult(bitmap: Bitmap): FetchResult {
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
            dataSource = DataSource.DISK,
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
}
