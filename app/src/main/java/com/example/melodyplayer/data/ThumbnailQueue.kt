package com.example.melodyplayer.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A priority-aware thumbnail work queue.
 *
 * Producers call [enqueueAlbum] / [enqueueSong] from any thread.
 * A HIGH-priority worker coroutine in [MusicRepository] consumes items via [activeRequestFlow].
 * Background library generation is delegated to [ThumbnailWorker] (WorkManager).
 *
 * Priority tiers (lower value = higher priority):
 *  - [CRITICAL] — currently-playing song or mini-player art.
 *  - [HIGH]     — items visible on screen right now.
 */
object ThumbnailQueue {

    const val CRITICAL = 0
    const val HIGH = 1

    private val highChannel = Channel<ThumbnailRequest>(
        capacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    fun enqueueAlbum(albumId: Long, artworkUri: String, priority: Int = HIGH) {
        highChannel.trySend(ThumbnailRequest.Album(albumId, artworkUri, priority))
    }

    fun enqueueSong(song: Song, priority: Int = HIGH) {
        highChannel.trySend(ThumbnailRequest.SongRequest(song, priority))
    }

    fun activeRequestFlow(): Flow<ThumbnailRequest> = highChannel.receiveAsFlow()
}

sealed class ThumbnailRequest(open val priority: Int) {
    data class Album(
        val albumId: Long,
        val artworkUri: String,
        override val priority: Int
    ) : ThumbnailRequest(priority)

    data class SongRequest(
        val song: Song,
        override val priority: Int
    ) : ThumbnailRequest(priority)
}
