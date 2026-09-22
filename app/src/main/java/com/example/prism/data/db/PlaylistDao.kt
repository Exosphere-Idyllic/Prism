package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.Song
import com.example.prism.domain.model.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // Single query for the whole list — avoids one Flow<Int> subscription per row.
    // LEFT JOIN + GROUP BY so playlists with zero songs still come back with count = 0.
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, p.updatedAt AS updatedAt,
               COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.name ASC
    """,
    )
    fun getAllPlaylistsWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean

    /** Returns the song IDs in a playlist, identified by name. */
    @Query("SELECT songId FROM playlist_songs JOIN playlists ON playlists.id = playlist_songs.playlistId WHERE playlists.name = :name ORDER BY playlist_songs.position ASC")
    fun getPlaylistSongIdsFlow(name: String): Flow<List<String>>

    @Query(
        """
        SELECT s.* FROM songs s
        JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """,
    )
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>

    /**
     * Inserts a song into a playlist at the next available position atomically.
     * Using MAX(position) + 1 inside the INSERT avoids the race condition of
     * reading the count, then inserting — which could produce duplicate positions
     * under concurrent access.
     */
    @Query(
        """
        INSERT OR IGNORE INTO playlist_songs (playlistId, songId, position)
        SELECT :playlistId, :songId, COALESCE(MAX(position) + 1, 0)
        FROM playlist_songs WHERE playlistId = :playlistId
    """,
    )
    suspend fun insertPlaylistSongAtEnd(playlistId: Long, songId: String)

    /**
     * Atomically ensures the "Favorites" playlist exists and returns its row.
     * Uses INSERT OR IGNORE + SELECT to prevent duplicate-name races.
     */
    @Transaction
    suspend fun getOrCreateFavorites(now: Long): Playlist {
        insert(Playlist(name = "Favorites", createdAt = now, updatedAt = now))
        return requireNotNull(getPlaylistByName("Favorites")) {
            "Failed to retrieve or create Favorites playlist"
        }
    }
}
