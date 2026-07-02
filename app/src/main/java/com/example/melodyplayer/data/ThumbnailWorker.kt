package com.example.melodyplayer.data

import android.content.Context
import android.os.Process
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

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
        private const val CHUNK_SIZE = 25
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Lower thread priority to minimize impact on the main thread
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (e: Exception) {
            // Non-fatal
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val songDao = database.songDao()
        val thumbnailCacheDao = database.thumbnailCacheDao()

        val allSongs = songDao.getSongThumbnailInfo()
        val cached = thumbnailCacheDao.getAllKeys().toSet()

        val uniqueAlbums = allSongs
            .filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
            .associateBy { it.albumId }

        val missingAlbums = uniqueAlbums.filter { (albumId, _) ->
            !cached.contains("album_${albumId}_128") || !cached.contains("album_${albumId}_256")
        }

        val missingSongs = allSongs.filter { song ->
            song.mediaUri.isNotEmpty() &&
                (!cached.contains("song_${song.id}_128") || !cached.contains("song_${song.id}_256"))
        }

        val semaphore = Semaphore(2)
        val newEntries = mutableListOf<ThumbnailCacheEntry>()

        suspend fun checkFlushEntries(force: Boolean = false) {
            if (newEntries.isNotEmpty() && (force || newEntries.size >= CHUNK_SIZE)) {
                val toInsert = ArrayList(newEntries)
                newEntries.clear()
                thumbnailCacheDao.insertAll(toInsert)
            }
        }

        missingAlbums.forEach { (albumId, song) ->
            semaphore.withPermit {
                val file128 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 128)
                val file256 = ThumbnailManager.getAlbumThumbnailFile(applicationContext, albumId, 256)

                if (file128.exists() && file256.exists()) {
                    newEntries.add(
                        ThumbnailCacheEntry("album_${albumId}_128", albumId.toString(), "album", 128)
                    )
                    newEntries.add(
                        ThumbnailCacheEntry("album_${albumId}_256", albumId.toString(), "album", 256)
                    )
                } else {
                    val sizes = ThumbnailHelper.generateWebpFromUri(
                        applicationContext, song.artworkUri, file128, file256, albumId
                    )
                    if (sizes.contains(128)) newEntries.add(
                        ThumbnailCacheEntry("album_${albumId}_128", albumId.toString(), "album", 128)
                    )
                    if (sizes.contains(256)) newEntries.add(
                        ThumbnailCacheEntry("album_${albumId}_256", albumId.toString(), "album", 256)
                    )
                }
                checkFlushEntries()
            }
        }

        missingSongs.forEach { songInfo ->
            semaphore.withPermit {
                val file128 = ThumbnailManager.getSongThumbnailFile(applicationContext, songInfo.id, 128)
                val file256 = ThumbnailManager.getSongThumbnailFile(applicationContext, songInfo.id, 256)

                if (file128.exists() && file256.exists()) {
                    newEntries.add(
                        ThumbnailCacheEntry("song_${songInfo.id}_128", songInfo.id, "song", 128)
                    )
                    newEntries.add(
                        ThumbnailCacheEntry("song_${songInfo.id}_256", songInfo.id, "song", 256)
                    )
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
                    if (sizes.contains(128)) newEntries.add(
                        ThumbnailCacheEntry("song_${song.id}_128", song.id, "song", 128)
                    )
                    if (sizes.contains(256)) newEntries.add(
                        ThumbnailCacheEntry("song_${song.id}_256", song.id, "song", 256)
                    )
                }
                checkFlushEntries()
            }
        }

        checkFlushEntries(force = true)

        Result.success()
    }
}