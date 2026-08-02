package com.example.melodyplayer

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the sliding media-item window loaded into the [MediaController].
 *
 * ExoPlayer performs best when the queue is a bounded window around the current
 * track rather than the entire library. [PlaybackWindowManager] keeps a window
 * of [windowSize] songs (default 50) centred on the currently playing track and
 * shifts it lazily as playback approaches either edge.
 *
 * Thread-safety note (A3):
 * [activePlaylist] and [controllerSongs] hold immutable [List] references.
 * Assignments are @Volatile so any thread reading the reference after a write
 * sees the updated list immediately. Element-level mutations never happen —
 * each update replaces the whole reference atomically, so no
 * [ConcurrentModificationException] can occur.
 */
class PlaybackWindowManager(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "PlaybackWindowManager"
    }

    // A3: @Volatile ensures cross-thread visibility for reference swaps.
    @Volatile var activePlaylist: List<Song> = emptyList()
        private set

    @Volatile var controllerSongs: List<Song> = emptyList()
        private set

    @Volatile var pendingControllerSongs: List<Song>? = null
        private set

    private var shiftWindowJob: Job? = null

    fun setActivePlaylist(songs: List<Song>) {
        activePlaylist = songs
    }

    fun setControllerSongs(songs: List<Song>) {
        controllerSongs = songs
    }

    fun buildPlaybackWindow(song: Song, playlist: List<Song>, windowSize: Int = 50): Pair<List<Song>, Int> {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index == -1) return Pair(listOf(song), 0)

        val half = windowSize / 2
        val start = (index - half).coerceAtLeast(0)
        val end = (index + half).coerceAtMost(playlist.size)
        val windowSongs = playlist.subList(start, end)
        val windowIndex = index - start
        return Pair(windowSongs, windowIndex)
    }

    fun updateControllerMediaItems(
        controller: MediaController,
        songs: List<Song>,
        currentSong: Song? = null,
        onComplete: (songs: List<Song>) -> Unit = {}
    ) {
        if ((pendingControllerSongs ?: controllerSongs) == songs && controller.mediaItemCount > 0) {
            onComplete(songs)
            return
        }
        pendingControllerSongs = songs
        val currentSongId = currentSong?.id

        scope.launch(Dispatchers.Default) {
            try {
                val mediaItems = MediaItemBuilder.buildMediaItems(songs, currentSongId)
                withContext(Dispatchers.Main) {
                    try {
                        controller.setMediaItems(mediaItems)
                        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                        controllerSongs = songs
                        onComplete(songs)
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaController setMediaItems failed in updateControllerMediaItems", e)
                    }
                }
            } finally {
                pendingControllerSongs = null
            }
        }
    }

    fun shiftWindow(controller: MediaController, currentSong: Song, immediate: Boolean = false) {
        val playlist = activePlaylist
        if (playlist.isEmpty()) return

        val (windowSongs, windowIndex) = buildPlaybackWindow(currentSong, playlist)
        if ((pendingControllerSongs ?: controllerSongs) == windowSongs) return

        val activeMediaId = controller.currentMediaItem?.mediaId
        if (controller.isPlaying && activeMediaId == currentSong.id && controllerSongs.any { it.id == currentSong.id }) {
            val currIndex = controllerSongs.indexOfFirst { it.id == currentSong.id }
            if (currIndex > 0 && currIndex < controllerSongs.size - 1) {
                return
            }
        }

        shiftWindowJob?.cancel()
        pendingControllerSongs = windowSongs
        val currentSongId = currentSong.id

        shiftWindowJob = scope.launch(Dispatchers.Default) {
            try {
                if (!immediate) {
                    delay(300)
                }

                val mediaItems = MediaItemBuilder.buildMediaItems(windowSongs, currentSongId)
                withContext(Dispatchers.Main) {
                    try {
                        val currentPos = controller.currentPosition
                        val wasPlaying = controller.isPlaying
                        controller.setMediaItems(mediaItems)
                        controller.seekTo(windowIndex, currentPos)
                        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                        if (wasPlaying) controller.play()
                        controllerSongs = windowSongs
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaController setMediaItems failed in shiftWindow", e)
                    }
                }
            } finally {
                pendingControllerSongs = null
            }
        }
    }

    fun cancel() {
        shiftWindowJob?.cancel()
    }
}
