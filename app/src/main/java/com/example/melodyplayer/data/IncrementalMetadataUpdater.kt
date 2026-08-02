package com.example.melodyplayer.data

import android.util.Log

/**
 * Handles incremental updates to the Album and Artist tables based on changes in the Song table.
 * This avoids a full recalculation of the library's metadata on every scan.
 */
class IncrementalMetadataUpdater(database: AppDatabase) {
    private val songDao = database.songDao()
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
                val allAlbums = songDao.getAggregatedAlbums()
                val allArtists = songDao.getAggregatedArtists()
                
                albumDao.deleteAll()
                artistDao.deleteAll()
                albumDao.insertAll(allAlbums)
                artistDao.insertAll(allArtists)
            } else {
                // Identify affected albums and artists
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

                // Re-aggregate everything to ensure consistency
                val allAlbums = songDao.getAggregatedAlbums()
                val allArtists = songDao.getAggregatedArtists()

                val newAlbumIds = allAlbums.asSequence().map { it.id }.toSet()
                val newArtistNames = allArtists.asSequence().map { it.name }.toSet()

                // Delete entities that are affected but no longer present in the aggregation
                val albumIdsToDelete = affectedAlbumIds.filter { !newAlbumIds.contains(it) }
                val artistNamesToDelete = affectedArtistNames.filter { !newArtistNames.contains(it) }

                if (albumIdsToDelete.isNotEmpty()) {
                    albumDao.deleteByIds(albumIdsToDelete)
                }
                if (artistNamesToDelete.isNotEmpty()) {
                    artistDao.deleteByNames(artistNamesToDelete)
                }

                // Upsert affected entities that still exist
                val albumsToUpsert = allAlbums.filter { affectedAlbumIds.contains(it.id) }
                val artistsToUpsert = allArtists.filter { affectedArtistNames.contains(it.name) }

                if (albumsToUpsert.isNotEmpty()) albumDao.insertAll(albumsToUpsert)
                if (artistsToUpsert.isNotEmpty()) artistDao.insertAll(artistsToUpsert)
            }
        } catch (e: Exception) {
            Log.e("MetadataUpdater", "Failed to update metadata incrementally", e)
        }
    }
}
