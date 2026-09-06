package com.example.prism.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ScannerRepository {
    val totalSongsCount: StateFlow<Int>
    val isLoading: StateFlow<Boolean>
    fun startObserving()
    fun triggerScan()
    fun stopObserving()
}
