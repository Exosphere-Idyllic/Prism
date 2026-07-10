package com.example.melodyplayer.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

object ThumbnailHelper {
    private const val TAG = "ThumbnailHelper"

    // ─────────────────────────────────────────────────────────────────────────
    //  Bitmap utilities
    // ─────────────────────────────────────────────────────────────────────────

    fun calculateInSampleSize(opts: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = opts.outHeight to opts.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun decodeSampledBitmapFromBytes(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    /**
     * Crops [bitmap] to a square (center-crop). Returns the same object if already square.
     */
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap
        val size = minOf(bitmap.width, bitmap.height)
        return Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
    }

    /**
     * Returns the WebP CompressFormat appropriate for this API level.
     * WEBP_LOSSY (API 30+) vs the generic deprecated WEBP flag.
     */
    private val webpFormat: Bitmap.CompressFormat
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Bitmap.CompressFormat.WEBP_LOSSY
        else
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    /**
     * Writes a [bitmap] to [dest] atomically via a sibling `.tmp` file.
     * Guarantees [dest] is never in a partially-written state.
     */
    private fun writeBitmapAtomically(
        bitmap: Bitmap,
        dest: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean {
        val parent = dest.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "Cannot create parent directory for ${dest.absolutePath}")
            return false
        }
        val tmp = File(dest.parent, "${dest.name}.tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(format, quality, out)
                out.flush()
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write bitmap to ${dest.name}", e)
            tmp.delete()
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-strategy bitmap loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempts [ContentResolver.loadThumbnail] for [uri] (API 29+).
     * Returns null on failure or on API < 29.
     */
    private fun loadThumbnailCompat(context: Context, uri: Uri, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        } catch (e: Exception) {
            Log.d(TAG, "loadThumbnail failed for uri=$uri: ${e.message}")
            null
        }
    }

    /**
     * Reads a bitmap from a content URI via legacy [openInputStream].
     * Works for URIs that are readable with READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE.
     */
    private fun loadBitmapViaStream(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) null
                else decodeSampledBitmapFromBytes(bytes, 512, 512)
            }
        } catch (e: Exception) {
            Log.d(TAG, "openInputStream failed for uri=$uri: ${e.message}")
            null
        }
    }

    /**
     * Loads album artwork using a prioritized strategy:
     *  1. [MediaStore.Audio.Albums] URI via [loadThumbnail] (API 29+) — most reliable on Scoped Storage
     *  2. Legacy [openInputStream] on the raw [artworkUri] — fallback for API < 29
     */
    private fun loadAlbumBitmap(context: Context, artworkUri: String, albumId: Long): Bitmap? {
        // 1. Modern path — loadThumbnail via Albums content URI (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val albumsUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            )
            val bmp = loadThumbnailCompat(context, albumsUri, 512)
            if (bmp != null) {
                Log.d(TAG, "loadThumbnail(Albums URI) OK for albumId=$albumId")
                return bmp
            }
            // 1b. Fallback within API29+: try the raw artworkUri via loadThumbnail
            if (artworkUri.isNotEmpty()) {
                val bmpFallback = loadThumbnailCompat(context, artworkUri.toUri(), 512)
                if (bmpFallback != null) {
                    Log.d(TAG, "loadThumbnail(artworkUri) OK for albumId=$albumId")
                    return bmpFallback
                }
            }
        }

        // 2. Legacy path — openInputStream on the artworkUri
        if (artworkUri.isNotEmpty()) {
            val bmp = loadBitmapViaStream(context, artworkUri.toUri())
            if (bmp != null) {
                Log.d(TAG, "openInputStream OK for albumId=$albumId")
                return bmp
            }
        }

        Log.w(TAG, "No artwork found for albumId=$albumId")
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public generation API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates 128 px and 256 px WebP thumbnails for an album from [artworkUri].
     * Outputs to [file128] and [file256] in the internal thumbnail cache directory.
     * Returns the list of sizes successfully written.
     */
    fun generateWebpFromUri(
        context: Context,
        artworkUri: String,
        file128: File,
        file256: File,
        albumId: Long
    ): List<Int> {
        val successSizes = mutableListOf<Int>()
        try {
            val original = loadAlbumBitmap(context, artworkUri, albumId) ?: run {
                Log.w(TAG, "No bitmap available for albumId=$albumId")
                return emptyList()
            }
            val square = cropToSquare(original)

            if (!file128.exists()) {
                val scaled = square.scale(128, 128)
                val ok = writeBitmapAtomically(scaled, file128, webpFormat, 80)
                if (scaled != square) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote album_${albumId}_128.webp (${file128.length()} bytes)")
            }
            if (file128.exists() && file128.length() > 0) successSizes.add(128)

            if (!file256.exists()) {
                val scaled = square.scale(256, 256)
                val ok = writeBitmapAtomically(scaled, file256, webpFormat, 80)
                if (scaled != square) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote album_${albumId}_256.webp (${file256.length()} bytes)")
            }
            if (file256.exists() && file256.length() > 0) successSizes.add(256)

            if (square != original) square.recycle()
            original.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Thumb gen failed for albumId=$albumId", e)
        }
        return successSizes
    }

    /**
     * Generates 128 px and 256 px WebP thumbnails for a song.
     * Strategy for loading the source bitmap:
     *  1. Embedded art via [MediaMetadataRetriever] (song-specific cover art)
     *  2. [loadThumbnail] on the song's media URI (API 29+)
     *  3. [loadThumbnail] on the album's Albums URI (API 29+)
     *  4. Legacy [openInputStream] on the song's [artworkUri] (API < 29 fallback)
     */
    fun generateSongWebp(
        context: Context,
        song: Song,
        file128: File,
        file256: File
    ): List<Int> {
        val successSizes = mutableListOf<Int>()
        try {
            var bitmap: Bitmap? = null

            // 1. Embedded artwork via MediaMetadataRetriever — song-specific, highest fidelity
            if (song.mediaUri.isNotEmpty()) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(song.mediaUri))
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        bitmap = decodeSampledBitmapFromBytes(artBytes, 512, 512)
                        if (bitmap != null) Log.d(TAG, "Embedded art found for song ${song.id}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No embedded art for song ${song.id}: ${e.message}")
                } finally {
                    try { retriever?.close() } catch (_: Exception) {}
                }
            }

            // 2. loadThumbnail on the media URI (API 29+)
            if (bitmap == null && song.mediaUri.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitmap = loadThumbnailCompat(context, Uri.parse(song.mediaUri), 512)
                if (bitmap != null) Log.d(TAG, "loadThumbnail(mediaUri) OK for song ${song.id}")
            }

            // 3. loadThumbnail on the album's Albums URI (API 29+)
            if (bitmap == null && song.albumId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val albumsUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    song.albumId
                )
                bitmap = loadThumbnailCompat(context, albumsUri, 512)
                if (bitmap != null) Log.d(TAG, "loadThumbnail(albumsUri) OK for song ${song.id}")
            }

            // 4. Legacy fallback: openInputStream on artworkUri
            if (bitmap == null && song.artworkUri.isNotEmpty()) {
                bitmap = loadBitmapViaStream(context, Uri.parse(song.artworkUri))
                if (bitmap != null) Log.d(TAG, "openInputStream OK for song ${song.id}")
            }

            val original = bitmap ?: run {
                Log.d(TAG, "No artwork source found for song ${song.id}")
                return emptyList()
            }

            val square = cropToSquare(original)

            if (!file128.exists()) {
                val scaled = square.scale(128, 128)
                val ok = writeBitmapAtomically(scaled, file128, webpFormat, 80)
                if (scaled != square) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote song_${song.id}_128.webp (${file128.length()} bytes)")
            }
            if (file128.exists() && file128.length() > 0) successSizes.add(128)

            if (!file256.exists()) {
                val scaled = square.scale(256, 256)
                val ok = writeBitmapAtomically(scaled, file256, webpFormat, 80)
                if (scaled != square) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote song_${song.id}_256.webp (${file256.length()} bytes)")
            }
            if (file256.exists() && file256.length() > 0) successSizes.add(256)

            if (square != original) square.recycle()
            original.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Webp generation failed for song ${song.id}", e)
        }
        return successSizes
    }
}
