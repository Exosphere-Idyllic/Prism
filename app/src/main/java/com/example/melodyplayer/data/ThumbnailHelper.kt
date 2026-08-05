package com.example.melodyplayer.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun decodeSampledBitmapFromStream(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSize(reqWidth, reqHeight)
                    // B3: ALLOCATOR_SOFTWARE is intentional here — the decoded bitmap is immediately
                    // passed to writeBitmapAtomically() which calls Bitmap.compress() via a Canvas
                    // operation. Hardware bitmaps cannot be read back by the CPU (compress() would
                    // throw), so we force a software-backed allocation even though ALLOCATOR_DEFAULT
                    // would be more efficient for display-only use cases (API 34+).
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                } ?: return null

                opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight)
                opts.inJustDecodeBounds = false

                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    BitmapFactory.decodeStream(input2, null, opts)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "decodeSampledBitmapFromStream failed for uri=$uri: ${e.message}")
            null
        }
    }

    /**
     * Converts a [Bitmap] with [Bitmap.Config.HARDWARE] configuration to a software
     * bitmap ([Bitmap.Config.ARGB_8888]). Returns the original bitmap if it is already software-backed,
     * or null if software copy failed.
     */
    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bitmap.config == Bitmap.Config.HARDWARE) {
            val softwareBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (softwareBmp != null) {
                bitmap.recycle()
                return softwareBmp
            }
            return null
        }
        return bitmap
    }

    /**
     * Crops [bitmap] to a square (center-crop). Returns the same object if already square,
     * or null if crop failed/bitmap recycled.
     */
    private fun cropToSquare(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null
        if (bitmap.width == bitmap.height) return bitmap
        val size = minOf(bitmap.width, bitmap.height)
        if (size <= 0) return null
        return try {
            Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
        } catch (e: Exception) {
            Log.e(TAG, "cropToSquare failed", e)
            null
        }
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
     *
     * P5: Simplified the fallback chain — instead of trying deprecated WEBP then JPEG
     * (two extra FileOutputStream opens), we fall back directly to JPEG on any primary
     * format failure. This saves one FileOutputStream allocation on devices where the
     * primary WEBP_LOSSY format fails.
     */
    private fun writeBitmapAtomically(
        bitmap: Bitmap,
        dest: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Cannot write recycled bitmap to ${dest.name}")
            return false
        }
        val parent = dest.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "Cannot create parent directory for ${dest.absolutePath}")
            return false
        }
        val tmp = File(dest.parent, "${dest.name}_${java.util.UUID.randomUUID()}.tmp")
        return try {
            var compressed = FileOutputStream(tmp).use { out ->
                bitmap.compress(format, quality, out)
            }

            // P5: single JPEG fallback instead of deprecated-WEBP then JPEG
            if (!compressed && format != Bitmap.CompressFormat.JPEG) {
                Log.w(TAG, "Primary compression format $format failed for ${dest.name}, falling back to JPEG")
                if (tmp.exists()) tmp.delete()
                compressed = FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
            }

            if (!compressed || !tmp.exists() || tmp.length() == 0L) {
                Log.e(TAG, "Bitmap compression failed or produced 0 bytes for ${dest.name}")
                if (tmp.exists()) tmp.delete()
                return false
            }
            if (dest.exists()) dest.delete()
            val success = tmp.renameTo(dest)
            if (!success) {
                try {
                    tmp.copyTo(dest, overwrite = true)
                } catch (copyEx: Exception) {
                    Log.e(TAG, "Bitmap copyTo fallback failed for ${dest.name}", copyEx)
                    if (tmp.exists()) tmp.delete()
                    if (dest.exists() && dest.length() == 0L) dest.delete()
                    return false
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write bitmap to ${dest.name}", e)
            if (tmp.exists()) tmp.delete()
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
            val bmp = context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            ensureSoftwareBitmap(bmp)
        } catch (e: Exception) {
            Log.d(TAG, "loadThumbnail failed for uri=$uri: ${e.message}")
            null
        }
    }

    /**
     * Reads a bitmap from a content URI via legacy [android.content.ContentResolver.openInputStream].
     * Works for URIs that are readable with READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE.
     */
    private fun loadBitmapViaStream(context: Context, uri: Uri): Bitmap? {
        val bmp = decodeSampledBitmapFromStream(context, uri, 512, 512) ?: return null
        return ensureSoftwareBitmap(bmp)
    }

    /**
     * Extracts embedded artwork directly from an audio file using [android.media.MediaMetadataRetriever].
     * Wrapped in a Kotlin `.use { ... }` block to guarantee native retriever release and avoid leaks.
     */
    fun extractEmbeddedArtwork(context: Context, uri: Uri): Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.use { r ->
                r.setDataSource(context, uri)
                val picture = r.embeddedPicture ?: return@use null
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, opts)
                opts.inSampleSize = calculateInSampleSize(opts, 512, 512)
                opts.inJustDecodeBounds = false
                val bmp = BitmapFactory.decodeByteArray(picture, 0, picture.size, opts) ?: return@use null
                ensureSoftwareBitmap(bmp)
            }
        } catch (e: Exception) {
            Log.d(TAG, "extractEmbeddedArtwork failed for uri=$uri: ${e.message}")
            null
        }
    }

    /**
     * Loads album artwork using a prioritized strategy:
     *  1. [MediaStore.Audio.Albums] URI via [loadThumbnail] (API 29+) — most reliable on Scoped Storage
     *  2. Legacy [openInputStream] on the raw [artworkUri] — fallback for API < 29
     *  3. Embedded ID3 metadata via [MediaMetadataRetriever.use] block
     */
    private fun loadAlbumBitmap(context: Context, artworkUri: String, albumId: Long): Bitmap? {
        // 1. Modern path — loadThumbnail via Albums content URI (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val albumsUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            )
            val bmp = loadThumbnailCompat(context, albumsUri, 512)
            if (bmp != null && !bmp.isRecycled) {
                Log.d(TAG, "loadThumbnail(Albums URI) OK for albumId=$albumId")
                return bmp
            }
            // 1b. Fallback within API29+: try the raw artworkUri via loadThumbnail
            if (artworkUri.isNotEmpty()) {
                val bmpFallback = loadThumbnailCompat(context, artworkUri.toUri(), 512)
                if (bmpFallback != null && !bmpFallback.isRecycled) {
                    Log.d(TAG, "loadThumbnail(artworkUri) OK for albumId=$albumId")
                    return bmpFallback
                }
            }
        }

        // 2. Legacy path — openInputStream on the artworkUri
        if (artworkUri.isNotEmpty()) {
            val bmp = loadBitmapViaStream(context, artworkUri.toUri())
            if (bmp != null && !bmp.isRecycled) {
                Log.d(TAG, "openInputStream OK for albumId=$albumId")
                return bmp
            }
            // 3. Fallback path — extract embedded picture via MediaMetadataRetriever.use
            val embeddedBmp = extractEmbeddedArtwork(context, artworkUri.toUri())
            if (embeddedBmp != null && !embeddedBmp.isRecycled) {
                Log.d(TAG, "extractEmbeddedArtwork OK for albumId=$albumId")
                return embeddedBmp
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
        val has128 = file128.exists() && file128.length() > 0
        val has256 = file256.exists() && file256.length() > 0
        if (has128 && has256) {
            return listOf(128, 256)
        }

        val successSizes = mutableListOf<Int>()
        var original: Bitmap? = null
        var square: Bitmap? = null
        try {
            original = loadAlbumBitmap(context, artworkUri, albumId) ?: run {
                Log.w(TAG, "No bitmap available for albumId=$albumId")
                return if (has128) listOf(128) else if (has256) listOf(256) else emptyList()
            }
            square = cropToSquare(original) ?: original

            if (!has128) {
                val scaled = if (square.width == 128 && square.height == 128) square else square.scale(128, 128)
                val ok = writeBitmapAtomically(scaled, file128, webpFormat, 80)
                if (scaled != square && !scaled.isRecycled) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote album_${albumId}_128.webp (${file128.length()} bytes)")
                else if (file128.exists() && file128.length() == 0L) file128.delete()
            }
            if (file128.exists() && file128.length() > 0) successSizes.add(128)

            if (!has256) {
                val scaled = if (square.width == 256 && square.height == 256) square else square.scale(256, 256)
                val ok = writeBitmapAtomically(scaled, file256, webpFormat, 80)
                if (scaled != square && !scaled.isRecycled) scaled.recycle()
                if (ok) Log.d(TAG, "Wrote album_${albumId}_256.webp (${file256.length()} bytes)")
                else if (file256.exists() && file256.length() == 0L) file256.delete()
            }
            if (file256.exists() && file256.length() > 0) successSizes.add(256)
        } catch (e: Exception) {
            Log.e(TAG, "Thumb gen failed for albumId=$albumId", e)
        } finally {
            if (square != null && square != original && !square.isRecycled) {
                square.recycle()
            }
            if (original != null && !original.isRecycled) {
                original.recycle()
            }
        }
        return successSizes
    }
}
