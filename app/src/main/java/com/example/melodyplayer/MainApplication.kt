package com.example.melodyplayer

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import android.content.Context
import okio.Path.Companion.toPath

class MainApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // 20% de RAM para carátulas
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_image_cache").absolutePath.toPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB en disco
                    .build()
            }
            .build()
    }
}