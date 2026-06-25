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
                val original = decodeSampledBitmapFromBytes(bytes, 512, 512) ?: return emptyList()

                val square = if (original.width == original.height) original else {
                    val size = minOf(original.width, original.height)
                    Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
                }

                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

                if (!file128.exists()) {
                    square.scale(128, 128).compress(format, 80, FileOutputStream(file128))
                }
                if (file128.exists()) {
                    successSizes.add(128)
                }

                if (!file256.exists()) {
                    square.scale(256, 256).compress(format, 80, FileOutputStream(file256))
                }
                if (file256.exists()) {
                    successSizes.add(256)
                }

                if (square != original) square.recycle()
                original.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumb gen failed for $artworkUri", e)
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

            // Try loading from artworkUri first since it's faster and uses standard content resolver
            if (song.artworkUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(song.artworkUri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        bitmap = decodeSampledBitmapFromBytes(bytes, 512, 512)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load album art from artworkUri for song ${song.id}", e)
                }
            }

            // Fallback to MediaMetadataRetriever if artworkUri is empty or failed to load
            if (bitmap == null && song.mediaUri.isNotEmpty()) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(song.mediaUri))
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        bitmap = decodeSampledBitmapFromBytes(artBytes, 512, 512)
                    }
                } catch (e: Exception) {
                    // Not a fatal issue
                } finally {
                    try {
                        retriever?.close()
                    } catch (e: Exception) {}
                }
            }

            val original = bitmap ?: return emptyList()

            val square = if (original.width == original.height) original else {
                val size = minOf(original.width, original.height)
                Bitmap.createBitmap(original, (original.width - size)/2, (original.height - size)/2, size, size)
            }

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

            if (!file128.exists()) {
                square.scale(128, 128).compress(format, 80, FileOutputStream(file128))
            }
            if (file128.exists()) {
                successSizes.add(128)
            }

            if (!file256.exists()) {
                square.scale(256, 256).compress(format, 80, FileOutputStream(file256))
            }
            if (file256.exists()) {
                successSizes.add(256)
            }

            if (square != original) square.recycle()
            original.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Webp generation failed for song ${song.id}", e)
        }
        return successSizes
    }
}
