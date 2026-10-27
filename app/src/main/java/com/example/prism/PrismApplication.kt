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
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.core.util.StartupMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber

class PrismApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        StartupMetrics.start()                             // ← Phase 0: absolute t0
        super.onCreate()
        StartupMetrics.mark("super.onCreate done")         // ← Phase 1

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger(org.koin.core.logger.Level.ERROR)
            }
            androidContext(this@PrismApplication)
            modules(appModule)
        }
        StartupMetrics.mark("startKoin done")              // ← Phase 2

        // Defer MediaStore observation to first onStart (ProcessLifecycleOwner).
        // This removes the artificial delay(300) and ensures the scanner only starts
        // after the first Activity is visible — never competing with the initial Paging
        // query and Compose layout during the cold start critical path.
        val appScope: CoroutineScope = get()
        val dispatchers: DispatcherProvider = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch(dispatchers.io) {
                    SingletonImageLoader.get(this@PrismApplication)
                    val scannerRepository: ScannerRepository = get()
                    scannerRepository.startObserving()
                }
            }
        })
        StartupMetrics.mark("scopes resolved")             // ← Phase 3
    }


    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .precision(coil3.size.Precision.INEXACT)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // 25% RAM for artwork to guarantee smooth scrolling
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
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB disk cache
                    .build()
            }
            .components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory())
                add(SongArtworkKeyer())
                add(AlbumArtworkKeyer())
                add(com.example.prism.data.artwork.MediaStoreArtworkKeyer())
                add(AlbumArtFetcher.SongFactory())
                add(AlbumArtFetcher.AlbumFactory())
                add(AlbumArtFetcher.UriFactory())
            }
            .build()
    }
}