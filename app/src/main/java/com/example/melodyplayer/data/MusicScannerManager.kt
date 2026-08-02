package com.example.melodyplayer.data

import android.app.Application
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
    private val onTotalCountChanged: (Int) -> Unit
) {
    private val _totalSongsCount = MutableStateFlow(0)
    val totalSongsCount = _totalSongsCount.asStateFlow()

    private val mediaStoreScanner: MediaStoreScanner = MediaStoreScannerImpl(
        app = app,
        scope = scope,
        database = database,
        onScanCompleted = { _, _, _ ->
            val count = database.songDao().getSongCount()
            _totalSongsCount.value = count
            onTotalCountChanged(count)
        }
    )

    val isLoading = mediaStoreScanner.isLoading

    fun startObserving() = mediaStoreScanner.startObserving()
    fun triggerScan() = mediaStoreScanner.triggerScan()
    fun stopObserving() = mediaStoreScanner.stopObserving()
}
