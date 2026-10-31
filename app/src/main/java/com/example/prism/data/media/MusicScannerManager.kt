package com.example.prism.data.media

import android.app.Application
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.domain.repository.ScannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import com.example.prism.data.preferences.ScanPreferences

/**
 * Orchestrates the [MediaStoreScanner] and handles scan completion.
 * Decouples the scanning logic from the main repository.
 */
class MusicScannerManager(
    private val app: Application,
    private val scope: CoroutineScope,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val scanPreferences: ScanPreferences = ScanPreferences(app),
) : ScannerRepository {

    override val totalSongsCount: StateFlow<Int> = database.songDao()
        .getSongCountFlow()
        .conflate()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private val mediaStoreScanner: MediaStoreScanner = MediaStoreScannerImpl(
        app = app,
        scope = scope,
        database = database,
        dispatchers = dispatchers,
        scanPreferences = scanPreferences,
        onScanChanged = { _, _, _ ->
            // NO-OP intencional.
            // La caché negativa se invalida por:
            //   1. dateModified en la clave (AlbumArtFetcher.negativeCacheKey)
            //   2. customArtworkUri.hashCode() en la clave
            //   3. TTL de 5 min (Commit 5)
            //   4. AppModule.onArtworkChanged cuando el usuario cambia portada manualmente
            // Borrar toda la caché en cada scan destruía su propósito (evitar reintentos
            // costosos sobre canciones sin arte).
        },
    )

    override val isLoading: StateFlow<Boolean> = mediaStoreScanner.isLoading

    override fun startObserving() = mediaStoreScanner.startObserving()
    override fun triggerScan() = mediaStoreScanner.triggerScan()
    override fun stopObserving() = mediaStoreScanner.stopObserving()
}
