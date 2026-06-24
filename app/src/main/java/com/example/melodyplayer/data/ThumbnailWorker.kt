package com.example.melodyplayer.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class ThumbnailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val songDao = database.songDao()
        val thumbnailCacheDao = database.thumbnailCacheDao()

        val allSongs = songDao.getAllSongs()
        val cacheDir = File(applicationContext.cacheDir, "album_art")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // Get already cached thumbnail entry IDs
        val cached = thumbnailCacheDao.getAll().associateBy { it.cacheKey }

        // Find missing albums
        val uniqueAlbums = allSongs
            .filter { it.albumId > 0 && it.artworkUri.isNotEmpty() }
            .associateBy { it.albumId }

        val missingAlbums = uniqueAlbums.filter { (albumId, _) ->
            !cached.containsKey("album_${albumId}_128") || !cached.containsKey("album_${albumId}_256")
        }

        // Find missing songs
        val missingSongs = allSongs.filter { song ->
            song.mediaUri.isNotEmpty() && (!cached.containsKey("song_${song.id}_128") || !cached.containsKey("song_${song.id}_256"))
        }

        val semaphore = Semaphore(2)

        // Process missing albums
        missingAlbums.forEach { (albumId, song) ->
            semaphore.withPermit {
                val file128 = File(cacheDir, "album_${albumId}_128.webp")
                val file256 = File(cacheDir, "album_${albumId}_256.webp")
                val sizes = ThumbnailHelper.generateWebpFromUri(applicationContext, song.artworkUri, file128, file256, albumId)
                if (sizes.contains(128)) {
                    ThumbnailRegistry.add128(albumId)
                    thumbnailCacheDao.insertAll(listOf(ThumbnailCacheEntry("album_${albumId}_128", albumId.toString(), "album", 128)))
                }
                if (sizes.contains(256)) {
                    ThumbnailRegistry.add256(albumId)
                    thumbnailCacheDao.insertAll(listOf(ThumbnailCacheEntry("album_${albumId}_256", albumId.toString(), "album", 256)))
                }
            }
        }

        // Process missing songs
        missingSongs.forEach { song ->
            semaphore.withPermit {
                val file128 = File(cacheDir, "song_${song.id}_128.webp")
                val file256 = File(cacheDir, "song_${song.id}_256.webp")
                val sizes = ThumbnailHelper.generateSongWebp(applicationContext, song, file128, file256)
                if (sizes.contains(128)) {
                    ThumbnailRegistry.addSong128(song.id)
                    thumbnailCacheDao.insertAll(listOf(ThumbnailCacheEntry("song_${song.id}_128", song.id, "song", 128)))
                }
                if (sizes.contains(256)) {
                    ThumbnailRegistry.addSong256(song.id)
                    thumbnailCacheDao.insertAll(listOf(ThumbnailCacheEntry("song_${song.id}_256", song.id, "song", 256)))
                }
            }
        }

        Result.success()
    }
}
