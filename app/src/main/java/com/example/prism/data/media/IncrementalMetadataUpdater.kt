package com.example.prism.data.media

import com.example.prism.data.db.AppDatabase
import com.example.prism.data.entity.Song
import timber.log.Timber

/**
 * Handles atomic synchronization of the Album and Artist materialized tables from the Song table.
 *
 * Employs native SQLite UPSERT queries (`INSERT ... ON CONFLICT DO UPDATE`) directly inside
 * the database engine, avoiding large heap allocations and guaranteeing that user-customized
 * cover URIs are never lost during full or incremental scans.
 */
class IncrementalMetadataUpdater(database: AppDatabase) {
    private val albumDao = database.albumDao()
    private val artistDao = database.artistDao()

    suspend fun updateMetadata(
        toUpsert: List<Song>,
        oldSongs: List<Song>,
        songsToDelete: List<Song>,
        isFullScan: Boolean,
    ) {
        try {
            if (isFullScan) {
                // Delete orphaned records whose songs no longer exist
                albumDao.deleteOrphans()
                artistDao.deleteOrphans()

                // Atomically re-aggregate all albums and artists preserving user-customized covers
                albumDao.syncAllFromSongs()
                artistDao.syncAllFromSongs()
            } else {
                val affectedAlbumIds = mutableSetOf<Long>()
                val affectedArtistNames = mutableSetOf<String>()

                toUpsert.forEach {
                    affectedAlbumIds.add(it.albumId)
                    affectedArtistNames.add(it.artist)
                }
                oldSongs.forEach {
                    affectedAlbumIds.add(it.albumId)
                    affectedArtistNames.add(it.artist)
                }
                songsToDelete.forEach {
                    affectedAlbumIds.add(it.albumId)
                    affectedArtistNames.add(it.artist)
                }

                if (affectedAlbumIds.isEmpty() && affectedArtistNames.isEmpty()) return

                // Re-sync affected albums and artists in batches to respect SQLite bind limits
                if (affectedAlbumIds.isNotEmpty()) {
                    affectedAlbumIds.chunked(200).forEach { chunk ->
                        albumDao.syncFromSongsForIds(chunk)
                    }
                    albumDao.deleteOrphans()
                }

                if (affectedArtistNames.isNotEmpty()) {
                    affectedArtistNames.chunked(200).forEach { chunk ->
                        artistDao.syncFromSongsForNames(chunk)
                    }
                    artistDao.deleteOrphans()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata atomically")
            throw e
        }
    }
}
