package com.example.prism.data.media

import timber.log.Timber
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Song

/**
 * Handles incremental updates to the Album and Artist tables based on changes in the Song table.
 * This avoids a full recalculation of the library's metadata on every scan.
 *
 * **Custom cover preservation**: The [Album.customCoverUri] field is user-set and
 * lives only in the `albums` table — it does not come from the songs aggregation
 * query. Before re-inserting aggregated albums (which have `customCoverUri = ""`),
 * existing custom covers are read and merged back so they are never lost during a
 * MediaStore rescan.
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

                // Preserve user-set custom covers before wiping the table
                val existingCovers = albumDao.getAllCustomCoverUris()
                    .associate { it.id to it.customCoverUri }

                val mergedAlbums = mergeCustomCovers(allAlbums, existingCovers)

                albumDao.deleteAll()
                artistDao.deleteAll()
                albumDao.insertAll(mergedAlbums)
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

                // Re-aggregate only the affected albums/artists — avoids a
                // full-table GROUP BY on large libraries for every incremental scan.
                // Chunk queries to stay safely within SQLite parameter limits.
                val affectedAlbumsData = affectedAlbumIds.toList().chunked(200).flatMap {
                    songDao.getAggregatedAlbumsForIds(it)
                }
                val affectedArtistsData = affectedArtistNames.toList().chunked(200).flatMap {
                    songDao.getAggregatedArtistsForNames(it)
                }

                val newAlbumIds = affectedAlbumsData.asSequence().map { it.id }.toSet()
                val newArtistNames = affectedArtistsData.asSequence().map { it.name }.toSet()

                // Delete entities that are affected but no longer present in the aggregation
                val albumIdsToDelete = affectedAlbumIds.filter { !newAlbumIds.contains(it) }
                val artistNamesToDelete = affectedArtistNames.filter { !newArtistNames.contains(it) }

                if (albumIdsToDelete.isNotEmpty()) {
                    albumIdsToDelete.chunked(200).forEach { albumDao.deleteByIds(it) }
                }
                if (artistNamesToDelete.isNotEmpty()) {
                    artistNamesToDelete.chunked(200).forEach { artistDao.deleteByNames(it) }
                }

                if (affectedAlbumsData.isNotEmpty()) {
                    // Preserve user-set custom covers before REPLACE overwrites them
                    val existingCovers = affectedAlbumsData.map { it.id }.chunked(200).flatMap {
                        albumDao.getCustomCoverUris(it)
                    }.associate { it.id to it.customCoverUri }

                    affectedAlbumsData.chunked(200).forEach {
                        albumDao.insertAll(mergeCustomCovers(it, existingCovers))
                    }
                }
                if (affectedArtistsData.isNotEmpty()) {
                    affectedArtistsData.chunked(200).forEach { artistDao.insertAll(it) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata incrementally")
            throw e
        }
    }

    /**
     * Merges existing [Album.customCoverUri] values back into freshly-aggregated albums
     * that come from the songs table with `customCoverUri = ""`.
     */
    private fun mergeCustomCovers(
        albums: List<Album>,
        existingCovers: Map<Long, String>
    ): List<Album> {
        if (existingCovers.isEmpty()) return albums
        return albums.map { album ->
            val custom = existingCovers[album.id]
            if (custom != null) album.copy(customCoverUri = custom) else album
        }
    }
}
