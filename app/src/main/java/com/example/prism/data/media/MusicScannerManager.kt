package com.example.prism.data.media

import android.app.Application
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.artwork.AlbumArtFetcher
import com.example.prism.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the [MediaStoreScanner] and handles scan completion.
 * Decouples the scanning logic from the main [MusicRepository].
 */
class MusicScannerManager(
    private val app: Application,
    private val scope: CoroutineScope,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) {
    private val _totalSongsCount = MutableStateFlow(0)
    val totalSongsCount = _totalSongsCount.asStateFlow()

    private val mediaStoreScanner: MediaStoreScanner = MediaStoreScannerImpl(
        app = app,
        scope = scope,
        database = database,
        dispatchers = dispatchers,
        onScanChanged = { _, _, _ ->
            AlbumArtFetcher.clearNegativeCache()
        }
    )

    val isLoading = mediaStoreScanner.isLoading

    init {
        // Subscribe to Room's count Flow: emits automatically whenever the songs
        // table is inserted into or deleted from, removing the need for an extra
        // SELECT COUNT(*) query after every scan.
        scope.launch {
            database.songDao().getSongCountFlow().collect { count ->
                _totalSongsCount.value = count
            }
        }
    }

    fun startObserving() = mediaStoreScanner.startObserving()
    fun triggerScan() = mediaStoreScanner.triggerScan()
    fun stopObserving() = mediaStoreScanner.stopObserving()
}
