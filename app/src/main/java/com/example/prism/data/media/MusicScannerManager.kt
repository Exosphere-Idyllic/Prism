package com.example.prism.data.media

import android.app.Application
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.artwork.AlbumArtFetcher
import com.example.prism.data.db.AppDatabase
import com.example.prism.domain.repository.ScannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Orchestrates the [MediaStoreScanner] and handles scan completion.
 * Decouples the scanning logic from the main repository.
 */
class MusicScannerManager(
    private val app: Application,
    private val scope: CoroutineScope,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : ScannerRepository {

    override val totalSongsCount: StateFlow<Int> = database.songDao()
        .getSongCountFlow()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val mediaStoreScanner: MediaStoreScanner = MediaStoreScannerImpl(
        app = app,
        scope = scope,
        database = database,
        dispatchers = dispatchers,
        onScanChanged = { _, _, _ ->
            AlbumArtFetcher.clearNegativeCache()
        },
    )

    override val isLoading: StateFlow<Boolean> = mediaStoreScanner.isLoading

    override fun startObserving() = mediaStoreScanner.startObserving()
    override fun triggerScan() = mediaStoreScanner.triggerScan()
    override fun stopObserving() = mediaStoreScanner.stopObserving()
}
