package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Query
import com.example.prism.data.entity.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY albumName ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE albumName LIKE :query OR artist LIKE :query ORDER BY albumName ASC")
    fun searchAlbums(query: String): Flow<List<Album>>

    @Query("UPDATE albums SET customCoverUri = :customCoverUri WHERE id = :id")
    suspend fun updateCustomCoverUri(id: Long, customCoverUri: String)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM songs)")
    suspend fun deleteOrphans()

    @Query(
        """
        INSERT INTO albums (id, albumName, artist, coverPath, songCount, customCoverUri)
        SELECT albumId, MIN(album), MIN(artist), MAX(artworkUri), COUNT(*), ''
        FROM songs
        GROUP BY albumId
        ON CONFLICT(id) DO UPDATE SET
            albumName = excluded.albumName,
            artist = excluded.artist,
            coverPath = excluded.coverPath,
            songCount = excluded.songCount
        """,
    )
    suspend fun syncAllFromSongs()

    @Query(
        """
        INSERT INTO albums (id, albumName, artist, coverPath, songCount, customCoverUri)
        SELECT albumId, MIN(album), MIN(artist), MAX(artworkUri), COUNT(*), ''
        FROM songs
        WHERE albumId IN (:albumIds)
        GROUP BY albumId
        ON CONFLICT(id) DO UPDATE SET
            albumName = excluded.albumName,
            artist = excluded.artist,
            coverPath = excluded.coverPath,
            songCount = excluded.songCount
        """,
    )
    suspend fun syncFromSongsForIds(albumIds: List<Long>)
}
