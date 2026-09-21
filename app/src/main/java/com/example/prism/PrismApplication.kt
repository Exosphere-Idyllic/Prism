package com.example.prism

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.example.prism.data.artwork.AlbumArtFetcher
import com.example.prism.data.artwork.AlbumArtworkKeyer
import com.example.prism.domain.repository.ScannerRepository
import com.example.prism.data.artwork.SongArtworkKeyer
import com.example.prism.core.di.appModule
import com.example.prism.BuildConfig
import okio.Path.Companion.toPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class PrismApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@PrismApplication)
            modules(appModule)
        }

        val scannerRepository: ScannerRepository = get()
        scannerRepository.startObserving()
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
                        context.cacheDir
                            .resolve("coil_image_cache")
                            .absolutePath
                            .toPath()
                    )
                    .maxSizeBytes(15L * 1024 * 1024) // 15 MB
                    .build()
            }
            .components {
                add(SongArtworkKeyer())
                add(AlbumArtworkKeyer())
                add(AlbumArtFetcher.SongFactory())
                add(AlbumArtFetcher.AlbumFactory())
            }
            .build()
    }
}