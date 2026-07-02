package com.example.melodyplayer.data

import android.content.Context
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val songDao = database.songDao()
        val thumbnailCacheDao = database.thumbnailCacheDao()

        val allSongs = songDao.getSongThumbnailInfo()
        val cacheDir = File(applicationContext.cacheDir, "album_art")
        if (!cacheDir.exists()) cacheDir.mkdirs()

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

        missingAlbums.forEach { (albumId, song) ->
            semaphore.withPermit {
                val file128 = File(cacheDir, "album_${albumId}_128.webp")
                val file256 = File(cacheDir, "album_${albumId}_256.webp")
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
        }

        missingSongs.forEach { songInfo ->
            semaphore.withPermit {
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
                val file128 = File(cacheDir, "song_${song.id}_128.webp")
                val file256 = File(cacheDir, "song_${song.id}_256.webp")
                val sizes = ThumbnailHelper.generateSongWebp(applicationContext, song, file128, file256)
                if (sizes.contains(128)) newEntries.add(
                    ThumbnailCacheEntry("song_${song.id}_128", song.id, "song", 128)
                )
                if (sizes.contains(256)) newEntries.add(
                    ThumbnailCacheEntry("song_${song.id}_256", song.id, "song", 256)
                )
            }
        }

        // Batch-insert all new entries in one call instead of one per thumbnail
        if (newEntries.isNotEmpty()) {
            thumbnailCacheDao.insertAll(newEntries)
        }

        Result.success()
    }
}