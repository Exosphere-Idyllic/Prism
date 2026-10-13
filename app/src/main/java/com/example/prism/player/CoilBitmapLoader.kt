package com.example.prism.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.BitmapLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.prism.data.artwork.AlbumArtFetcher
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [BitmapLoader] for [androidx.media3.session.MediaSession] that delegates all bitmap
 * loading to Coil's [SingletonImageLoader].
 *
 * Media3's default [androidx.media3.datasource.DataSourceBitmapLoader] queries
 * `audio_albums` using the legacy `_data` column which was removed in Android 10 (Q),
 * causing a [android.database.sqlite.SQLiteException] on modern devices. This loader
 * replaces that path entirely by routing through Coil and [AlbumArtFetcher.UriFactory],
 * which handles `content://media/external/audio/albums/…` and `audio/media/…` URIs
 * correctly via `ContentResolver.loadThumbnail` and [android.media.MediaMetadataRetriever].
 *
 * Bitmaps for notifications **must not** be hardware-backed, so [allowHardware] is
 * explicitly set to `false` for every request dispatched by this loader.
 */
@UnstableApi
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(AlbumArtFetcher.MASTER_ARTWORK_SIZE)
                    .allowHardware(false) // Notification bitmaps must be software-rendered
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    future.set(result.image.toBitmap())
                } else {
                    future.setException(
                        IllegalStateException("Coil returned a non-success result for byte array")
                    )
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        future.addListener({
            if (future.isCancelled) {
                job.cancel()
            }
        }, ContextCompat.getMainExecutor(context))
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(AlbumArtFetcher.MASTER_ARTWORK_SIZE)
                    .allowHardware(false) // Notification bitmaps must be software-rendered
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    future.set(result.image.toBitmap())
                } else {
                    future.setException(
                        IllegalStateException("Coil returned a non-success result for URI: $uri")
                    )
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        future.addListener({
            if (future.isCancelled) {
                job.cancel()
            }
        }, ContextCompat.getMainExecutor(context))
        return future
    }
}
