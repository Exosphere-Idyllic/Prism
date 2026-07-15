package com.example.melodyplayer

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.example.melodyplayer.data.ArtworkInterceptor
import com.example.melodyplayer.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

class MainApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        /**
         * Application-scoped [MusicRepository] singleton.
         *
         * Keeping the repository in the Application means:
         *  - A single MediaStore scan runs even across ViewModel re-creations (rotations).
         *  - The thumbnail in-memory Sets survive config changes without re-loading.
         *  - The [ArtworkInterceptor] always has direct access to the live Sets.
         */
        lateinit var repository: MusicRepository
            private set
    }

    /** Long-lived scope tied to the application process — never cancelled. */
    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this, applicationScope)
        // Start MediaStore observation immediately so the first ViewModel attach is instant.
        repository.startObserving()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // 20% de RAM para carátulas
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
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB en disco
                    .build()
            }
            // Centralises WebP-cache → MediaStore fallback logic.
            // Eliminates the need to propagate hasWebp flags through Compose.
            .components {
                add(ArtworkInterceptor(repository))
            }
            .build()
    }
}