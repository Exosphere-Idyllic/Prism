package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Query
import com.example.prism.data.entity.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY albumName ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("""
        SELECT * FROM albums
        WHERE rowid IN (SELECT docid FROM albums_fts WHERE albums_fts MATCH :query)
        ORDER BY albumName ASC
    """)
    fun searchAlbumsFts(query: String): Flow<List<Album>>

    @Query("UPDATE albums SET customCoverUri = :customCoverUri WHERE id = :id")
    suspend fun updateCustomCoverUri(id: Long, customCoverUri: String)


    @Query("DELETE FROM albums WHERE NOT EXISTS (SELECT 1 FROM songs WHERE songs.albumId = albums.id)")
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
