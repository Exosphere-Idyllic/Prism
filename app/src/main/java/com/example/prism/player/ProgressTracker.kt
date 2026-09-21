package com.example.prism.player

import androidx.media3.session.MediaController
import com.example.prism.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tracks playback progress and position polling.
 * Follows the Single Responsibility Principle (SRP) by isolating progress polling,
 * jitter suppression, and UI subscription awareness from the playback lifecycle.
 */
class ProgressTracker(
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    @Volatile
    private var mediaController: MediaController? = null

    @Volatile
    private var isPlaying: Boolean = false

    @Volatile
    private var lastSeekTimestamp = 0L

    private var progressJob: Job? = null

    init {
        // Suspend/resume progress polling based on active UI subscribers to save battery
        scope.launch(dispatchers.main) {
            _progressState.subscriptionCount.collect {
                evaluateProgressPolling()
            }
        }
    }

    fun attachController(controller: MediaController?) {
        mediaController = controller
        if (controller != null) {
            syncFromController(controller)
        } else {
            evaluateProgressPolling()
        }
    }

    fun syncFromController(controller: MediaController) {
        mediaController = controller
        isPlaying = controller.isPlaying
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L),
        )
        evaluateProgressPolling()
    }

    fun onPlayingChanged(playing: Boolean) {
        isPlaying = playing
        evaluateProgressPolling()
    }

    fun onDurationChanged(durationMs: Long) {
        _progressState.value = _progressState.value.copy(
            duration = durationMs.coerceAtLeast(0L),
        )
    }

    fun onPositionDiscontinuity(positionMs: Long) {
        _progressState.value = _progressState.value.copy(
            currentPosition = positionMs.coerceAtLeast(0L),
        )
    }

    fun onSeek(positionMs: Long) {
        lastSeekTimestamp = System.currentTimeMillis()
        _progressState.value = _progressState.value.copy(
            currentPosition = positionMs.coerceAtLeast(0L),
        )
    }

    private fun evaluateProgressPolling() {
        val hasSubscribers = _progressState.subscriptionCount.value > 0
        if (hasSubscribers && isPlaying && mediaController != null) {
            startProgressUpdate()
        } else {
            stopProgressUpdate()
        }
    }

    private fun startProgressUpdate() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch(dispatchers.main) {
            while (true) {
                mediaController?.let {
                    // Avoid jitter if user recently sought and position hasn't confirmed yet
                    if (System.currentTimeMillis() - lastSeekTimestamp > 1000L) {
                        _progressState.value = _progressState.value.copy(
                            currentPosition = it.currentPosition.coerceAtLeast(0L),
                            duration = it.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(250.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdate()
        mediaController = null
        isPlaying = false
    }
}
