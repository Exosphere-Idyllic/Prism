package com.example.melodyplayer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Persists which WebP thumbnails have been generated so that the app does not
 * need to scan the filesystem on every start-up.
 *
 * The [cacheKey] is the canonical file stem, e.g. "album_42_128" or "song_7_256".
 */
@Entity(tableName = "thumbnail_cache")
data class ThumbnailCacheEntry(
    @PrimaryKey val cacheKey: String,   // "album_<id>_<size>" | "song_<id>_<size>"
    val entityId: String,               // albumId or songId (as String)
    val type: String,                   // "album" | "song"
    val size: Int                       // 128 | 256
)

@Dao
interface ThumbnailCacheDao {

    @Query("SELECT * FROM thumbnail_cache")
    suspend fun getAll(): List<ThumbnailCacheEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<ThumbnailCacheEntry>)

    /** Remove orphaned entries when songs are deleted from the library. */
    @Query("DELETE FROM thumbnail_cache WHERE type = 'song' AND entityId IN (:songIds)")
    suspend fun deleteSongEntries(songIds: List<String>)

    /** Remove orphaned album entries (e.g. after a full library rebuild). */
    @Query("DELETE FROM thumbnail_cache WHERE type = 'album' AND entityId NOT IN (:albumIds)")
    suspend fun deleteOrphanedAlbumEntries(albumIds: List<String>)
}
