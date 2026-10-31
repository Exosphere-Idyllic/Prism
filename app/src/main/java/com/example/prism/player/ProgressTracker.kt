package com.example.prism.player

import androidx.media3.session.MediaController
import com.example.prism.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tracks playback progress and position polling.
 * Follows the Single Responsibility Principle (SRP) by isolating progress polling,
 * jitter suppression, and UI subscription awareness from the playback lifecycle.
 *
 * ## Threading model
 * - [attachController], [syncFromController], [onPlayingChanged], [onDurationChanged],
 *   [onPositionDiscontinuity], [onSeek] are called from the main thread (MediaController listener).
 * - [release] must be called from the main thread (or the owning scope's thread).
 * - [progressState] is a [StateFlow] and can be collected from any thread.
 *
 * ## Lifecycle
 * The internal subscription-watcher job started in [init] is cancelled in [release]
 * via [collectJob]. Without this, the coroutine would leak after the owning component
 * is destroyed (e.g. after MediaController disconnects permanently).
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
    private var collectJob: Job? = null

    init {
        // Suspend/resume progress polling based on active UI subscribers to save battery.
        // collectJob is stored so it can be cancelled in release() — without this, the
        // coroutine would leak after the owning component is destroyed.
        collectJob = scope.launch(dispatchers.main) {
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
        val dur = controller.duration
        _progressState.value = ProgressState(
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = if (dur > 0L) dur else _progressState.value.duration,
        )
        evaluateProgressPolling()
    }

    fun onPlayingChanged(playing: Boolean) {
        isPlaying = playing
        evaluateProgressPolling()
    }

    fun onDurationChanged(durationMs: Long) {
        if (durationMs > 0L) {
            _progressState.value = _progressState.value.copy(duration = durationMs)
        }
    }

    fun onPositionDiscontinuity(positionMs: Long) {
        lastSeekTimestamp = 0L
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
            while (isActive) {
                val controller = mediaController ?: break
                // Avoid jitter if user recently sought and position hasn't confirmed yet
                if (System.currentTimeMillis() - lastSeekTimestamp > 1000L) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration
                    _progressState.value = _progressState.value.copy(
                        currentPosition = pos,
                        duration = if (dur > 0L) dur else _progressState.value.duration,
                    )
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
        collectJob?.cancel()
        collectJob = null
        mediaController = null
        isPlaying = false
    }
}
