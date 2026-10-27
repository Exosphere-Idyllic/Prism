package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Query
import com.example.prism.data.entity.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("""
        SELECT * FROM artists
        WHERE rowid IN (SELECT docid FROM artists_fts WHERE artists_fts MATCH :query)
        ORDER BY name ASC
    """)
    fun searchArtistsFts(query: String): Flow<List<Artist>>


    @Query("DELETE FROM artists WHERE name NOT IN (SELECT DISTINCT artist FROM songs)")
    suspend fun deleteOrphans()

    @Query(
        """
        INSERT INTO artists (name, songCount, albumCount)
        SELECT artist, COUNT(*), COUNT(DISTINCT albumId)
        FROM songs
        GROUP BY artist
        ON CONFLICT(name) DO UPDATE SET
            songCount = excluded.songCount,
            albumCount = excluded.albumCount
        """,
    )
    suspend fun syncAllFromSongs()

    @Query(
        """
        INSERT INTO artists (name, songCount, albumCount)
        SELECT artist, COUNT(*), COUNT(DISTINCT albumId)
        FROM songs
        WHERE artist IN (:names)
        GROUP BY artist
        ON CONFLICT(name) DO UPDATE SET
            songCount = excluded.songCount,
            albumCount = excluded.albumCount
        """,
    )
    suspend fun syncFromSongsForNames(names: List<String>)
}
