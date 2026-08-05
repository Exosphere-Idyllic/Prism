package com.example.melodyplayer.data

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background WorkManager task that pre-generates WebP thumbnails for every song
 * and album not yet in the thumbnail cache.
 *
 * NOTE: This worker writes results only to [thumbnailCacheDao] (Room) and to the
 * on-disk album_art directory. It does NOT update the in-memory StateFlow sets in
 * MusicRepository — those are loaded from Room the next time [performScan] runs
 * (i.e. on next app launch or when the ContentObserver fires). High-priority
 * thumbnails for visible items are handled by [ThumbnailQueue] which does update
 * the StateFlows immediately.
 */
class ThumbnailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ThumbnailWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Lower thread priority to minimize impact on the main thread
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (e: Exception) {
            // Non-fatal
        }

        Log.d(TAG, "doWork() started on thread=${Thread.currentThread().name}")

        val database = AppDatabase.getDatabase(applicationContext)
        val songDao = database.songDao()
        val thumbnailCacheDao = database.thumbnailCacheDao()

        val allSongs = songDao.getSongThumbnailInfo()
        val cached = thumbnailCacheDao.getAllKeys().toSet()

        Log.d(TAG, "Starting: ${allSongs.size} songs, ${cached.size} already cached")

        val missingAlbums = allSongs.asSequence()
            .filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
            .filter { !cached.contains("album_${it.albumId}_128") || !cached.contains("album_${it.albumId}_256") }
            .associateBy { it.albumId }

        Log.d(TAG, "Missing: ${missingAlbums.size} albums")

        val limitedDispatcher = Dispatchers.IO.limitedParallelism(2)
        val pendingEntries = java.util.concurrent.ConcurrentLinkedQueue<ThumbnailCacheEntry>()

        coroutineScope {
            missingAlbums.entries.chunked(10).forEach { batch ->
                batch.map { (albumId, song) ->
                    launch(limitedDispatcher) {
                        val file128 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 128)
                        val file256 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 256)

                        val entriesToInsert = mutableListOf<ThumbnailCacheEntry>()

                        if (file128.exists() && file128.length() > 0 && file256.exists() && file256.length() > 0) {
                            // Files already on disk but not registered in Room — add them.
                            listOf(128, 256).forEach { size ->
                                entriesToInsert.add(ThumbnailCacheEntry("album_${albumId}_$size", albumId.toString(), "album", size))
                            }
                            Log.d(TAG, "Album $albumId already on disk, registering in Room")
                        } else {
                            val sizes = ThumbnailHelper.generateWebpFromUri(
                                applicationContext, song.artworkUri, file128, file256, albumId
                            )
                            listOf(128, 256).forEach { size ->
                                if (sizes.contains(size)) {
                                    entriesToInsert.add(ThumbnailCacheEntry("album_${albumId}_$size", albumId.toString(), "album", size))
                                }
                            }
                            if (sizes.isEmpty()) {
                                Log.w(TAG, "Failed to generate thumbnail for albumId=$albumId artworkUri=${song.artworkUri}")
                            }
                        }

                        if (entriesToInsert.isNotEmpty()) {
                            pendingEntries.addAll(entriesToInsert)
                        }
                    }
                }.forEach { it.join() }
                // Throttling delay between batches to release disk/CPU for active UI
                kotlinx.coroutines.delay(50)
            }
        }

        val allNewEntries = pendingEntries.toList()
        if (allNewEntries.isNotEmpty()) {
            allNewEntries.chunked(100).forEach { chunk ->
                thumbnailCacheDao.insertAll(chunk)
            }
            Log.d(TAG, "Persisted ${allNewEntries.size} total thumbnail entries to Room")
        }

        Log.d(TAG, "Done. All thumbnail entries persisted to Room.")
        Result.success()
    }
}