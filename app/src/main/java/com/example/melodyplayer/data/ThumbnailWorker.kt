package com.example.melodyplayer.data

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        private const val CHUNK_SIZE = 25
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

        val uniqueAlbums = allSongs
            .filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
            .associateBy { it.albumId }

        val missingAlbums = uniqueAlbums.filter { (albumId, _) ->
            !cached.contains("album_${albumId}_128") || !cached.contains("album_${albumId}_256")
        }

        // We disable mass background pre-generation of song-specific thumbnails to prevent
        // startup lag and I/O bottlenecks. Custom/embedded song art will be generated on-demand.
        val missingSongs = emptyList<SongThumbnailInfo>()

        Log.d(TAG, "Missing: ${missingAlbums.size} albums, ${missingSongs.size} songs")

        val semaphore = Semaphore(2)
        val newEntries = mutableListOf<ThumbnailCacheEntry>()

        suspend fun checkFlushEntries(force: Boolean = false) {
            if (newEntries.isNotEmpty() && (force || newEntries.size >= CHUNK_SIZE)) {
                val toInsert = ArrayList(newEntries)
                newEntries.clear()
                thumbnailCacheDao.insertAll(toInsert)
                Log.d(TAG, "Flushed ${toInsert.size} cache entries to Room")
            }
        }

        // Use explicit `for` loop so the suspend function `withPermit` is called
        // correctly in a suspendable context (Map.forEach {} with a suspend lambda
        // compiles but runs the semaphore logic in a blocking manner).
        for ((albumId, song) in missingAlbums) {
            semaphore.withPermit {
                val file128 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 128)
                val file256 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 256)

                if (file128.exists() && file128.length() > 0 && file256.exists() && file256.length() > 0) {
                    // Files already on disk but not registered in Room — add them.
                    listOf(128, 256).forEach { size ->
                        newEntries.add(ThumbnailCacheEntry("album_${albumId}_$size", albumId.toString(), "album", size))
                    }
                    Log.d(TAG, "Album $albumId already on disk, registering in Room")
                } else {
                    val sizes = ThumbnailHelper.generateWebpFromUri(
                        applicationContext, song.artworkUri, file128, file256, albumId
                    )
                    listOf(128, 256).forEach { size ->
                        if (sizes.contains(size)) {
                            newEntries.add(ThumbnailCacheEntry("album_${albumId}_$size", albumId.toString(), "album", size))
                        }
                    }
                    if (sizes.isEmpty()) {
                        Log.w(TAG, "Failed to generate thumbnail for albumId=$albumId artworkUri=${song.artworkUri}")
                    }
                }
                checkFlushEntries()
            }
        }

        for (songInfo in missingSongs) {
            semaphore.withPermit {
                val file128 = ThumbnailManager.getSongThumbnailFile(applicationContext, songInfo.id, 128)
                val file256 = ThumbnailManager.getSongThumbnailFile(applicationContext, songInfo.id, 256)

                if (file128.exists() && file128.length() > 0 && file256.exists() && file256.length() > 0) {
                    // Files already on disk but not in Room — register them.
                    listOf(128, 256).forEach { size ->
                        newEntries.add(ThumbnailCacheEntry("song_${songInfo.id}_$size", songInfo.id, "song", size))
                    }
                    Log.d(TAG, "Song ${songInfo.id} already on disk, registering in Room")
                } else {
                    val song = Song(
                        id = songInfo.id,
                        title = "",
                        artist = "",
                        album = "",
                        albumId = songInfo.albumId,
                        mediaUri = songInfo.mediaUri,
                        artworkUri = songInfo.artworkUri,
                        duration = 0L,
                        dateModified = 0L,
                        track = 0
                    )
                    val sizes = ThumbnailHelper.generateSongWebp(applicationContext, song, file128, file256)
                    listOf(128, 256).forEach { size ->
                        if (sizes.contains(size)) {
                            newEntries.add(ThumbnailCacheEntry("song_${song.id}_$size", song.id, "song", size))
                        }
                    }
                    if (sizes.isEmpty()) {
                        Log.w(TAG, "Failed to generate thumbnail for songId=${song.id} mediaUri=${songInfo.mediaUri}")
                    }
                }
                checkFlushEntries()
            }
        }

        checkFlushEntries(force = true)

        Log.d(TAG, "Done. Total new entries persisted to Room: ${newEntries.size} (plus any flushed mid-run)")
        Result.success()
    }
}