package com.example.melodyplayer

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.example.melodyplayer.data.AlbumArtFetcher
import com.example.melodyplayer.data.MusicRepository
import com.example.melodyplayer.data.MusicRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import java.io.File

class MainApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        /**
         * Application-scoped [MusicRepository] singleton.
         */
        lateinit var repository: MusicRepository
            private set
    }

    /**
     * Long-lived scope tied to the application process — never cancelled.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch(Dispatchers.IO) {
            try {
                val oldCacheDir = File(cacheDir, "album_art")
                if (oldCacheDir.exists()) {
                    oldCacheDir.deleteRecursively()
                    Log.d("MainApplication", "Cleaned up old cache directory")
                }
            } catch (e: Exception) {
                Log.w("MainApplication", "Failed to cleanup old cache directory", e)
            }
        }

        repository = MusicRepositoryImpl(this, applicationScope)
        repository.startObserving()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // 20% RAM for artwork
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(
                        context.getDir("coil_cache", Context.MODE_PRIVATE)
                            .resolve("coil_image_cache")
                            .absolutePath
                            .toPath()
                    )
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB on disk
                    .build()
            }
            .components {
                add(AlbumArtFetcher.SongFactory())
                add(AlbumArtFetcher.AlbumFactory())
            }
            .build()
    }
}