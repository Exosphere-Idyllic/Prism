package com.example.melodyplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.net.toUri
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

object ThumbnailHelper {
    private const val TAG = "ThumbnailHelper"

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
     * Writes a [bitmap] to [dest] atomically: first encodes into a sibling `.tmp` file,
     * then renames it into place. This guarantees that [dest] is never left in a
     * partially-written / zero-byte state if the process is interrupted mid-write.
     *
     * @return true if the file was successfully written and renamed.
     */
    private fun writeBitmapAtomically(bitmap: Bitmap, dest: File, format: Bitmap.CompressFormat, quality: Int): Boolean {
        val tmp = File(dest.parent, "${dest.name}.tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(format, quality, out)
                out.flush()
            }
            // Atomically replace the destination
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write bitmap to ${dest.name}", e)
            tmp.delete()
            false
        }
    }

    fun generateWebpFromUri(
        context: Context,
        artworkUri: String,
        file128: File,
        file256: File,
        albumId: Long
    ): List<Int> {
        val successSizes = mutableListOf<Int>()
        try {
            val uri = artworkUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) {
                    Log.w(TAG, "Empty artwork bytes for albumId=$albumId uri=$artworkUri")
                    return emptyList()
                }
                val original = decodeSampledBitmapFromBytes(bytes, 512, 512) ?: run {
                    Log.w(TAG, "Failed to decode bitmap for albumId=$albumId")
                    return emptyList()
                }

                val square = if (original.width == original.height) original else {
                    val size = minOf(original.width, original.height)
                    Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
                }

                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

                if (!file128.exists()) {
                    val scaled128 = square.scale(128, 128)
                    val ok = writeBitmapAtomically(scaled128, file128, format, 80)
                    if (scaled128 != square) scaled128.recycle()
                    if (ok) {
                        Log.d(TAG, "Wrote album_${albumId}_128.webp (${file128.length()} bytes)")
                    }
                }
                if (file128.exists() && file128.length() > 0) successSizes.add(128)

                if (!file256.exists()) {
                    val scaled256 = square.scale(256, 256)
                    val ok = writeBitmapAtomically(scaled256, file256, format, 80)
                    if (scaled256 != square) scaled256.recycle()
                    if (ok) {
                        Log.d(TAG, "Wrote album_${albumId}_256.webp (${file256.length()} bytes)")
                    }
                }
                if (file256.exists() && file256.length() > 0) successSizes.add(256)

                if (square != original) square.recycle()
                original.recycle()
            } ?: Log.w(TAG, "openInputStream returned null for albumId=$albumId uri=$artworkUri")
        } catch (e: Exception) {
            Log.e(TAG, "Thumb gen failed for albumId=$albumId uri=$artworkUri", e)
        }
        return successSizes
    }

    fun generateSongWebp(
        context: Context,
        song: Song,
        file128: File,
        file256: File
    ): List<Int> {
        val successSizes = mutableListOf<Int>()
        try {
            var bitmap: Bitmap? = null

            // Try MediaMetadataRetriever FIRST — this extracts the song's own
            // embedded artwork (ID3 tag / Vorbis picture), giving each track
            // its individual cover even when multiple songs share the same album.
            if (song.mediaUri.isNotEmpty()) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(song.mediaUri))
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        bitmap = decodeSampledBitmapFromBytes(artBytes, 512, 512)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No embedded art for song ${song.id}: ${e.message}")
                    // Not a fatal issue — fall through to artworkUri
                } finally {
                    try {
                        retriever?.close()
                    } catch (_: Exception) {}
                }
            }

            // Fallback to album artworkUri if no embedded art was found
            if (bitmap == null && song.artworkUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(song.artworkUri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        bitmap = decodeSampledBitmapFromBytes(bytes, 512, 512)
                    } ?: Log.w(TAG, "openInputStream null for song ${song.id} artworkUri=${song.artworkUri}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load album art from artworkUri for song ${song.id}", e)
                }
            }

            val original = bitmap ?: run {
                Log.d(TAG, "No artwork source found for song ${song.id}")
                return emptyList()
            }

            val square = if (original.width == original.height) original else {
                val size = minOf(original.width, original.height)
                Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
            }

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

            if (!file128.exists()) {
                val scaled128 = square.scale(128, 128)
                val ok = writeBitmapAtomically(scaled128, file128, format, 80)
                if (scaled128 != square) scaled128.recycle()
                if (ok) {
                    Log.d(TAG, "Wrote song_${song.id}_128.webp (${file128.length()} bytes)")
                }
            }
            if (file128.exists() && file128.length() > 0) successSizes.add(128)

            if (!file256.exists()) {
                val scaled256 = square.scale(256, 256)
                val ok = writeBitmapAtomically(scaled256, file256, format, 80)
                if (scaled256 != square) scaled256.recycle()
                if (ok) {
                    Log.d(TAG, "Wrote song_${song.id}_256.webp (${file256.length()} bytes)")
                }
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
