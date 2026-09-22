package com.example.prism.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.prism.data.entity.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCountFlow(): Flow<Int>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsPaging(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query ORDER BY title ASC")
    fun searchSongsPaging(query: String): PagingSource<Int, Song>

    @Query("SELECT id, dateModified FROM songs")
    suspend fun getSongsSyncInfo(): List<SongSyncInfo>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(songs: List<Song>): List<Long>

    @Query(
        """
        UPDATE songs SET 
            title = :title, artist = :artist, album = :album, albumId = :albumId,
            mediaUri = :mediaUri, artworkUri = :artworkUri, duration = :duration,
            dateModified = :dateModified, track = :track
        WHERE id = :id
        """,
    )
    suspend fun updateSongPreservingCustomFields(
        id: String, title: String, artist: String, album: String, albumId: Long,
        mediaUri: String, artworkUri: String, duration: Long, dateModified: Long, track: Int,
    )

    /**
     * Upserts songs while preserving user-set fields like [Song.customArtworkUri].
     * New songs are inserted; existing songs have only scanner-sourced columns updated.
     */
    @Transaction
    suspend fun upsertPreservingUserFields(songs: List<Song>) {
        val rowIds = insertAllIgnore(songs)
        for (i in songs.indices) {
            if (rowIds.getOrNull(i) == -1L) {
                val song = songs[i]
                updateSongPreservingCustomFields(
                    id = song.id, title = song.title, artist = song.artist,
                    album = song.album, albumId = song.albumId, mediaUri = song.mediaUri,
                    artworkUri = song.artworkUri, duration = song.duration,
                    dateModified = song.dateModified, track = song.track
                )
            }
        }
    }

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY track ASC, title ASC")
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<String>): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongByIdSync(id: String): Song?

    @Query("UPDATE songs SET customLyricsUri = :lyricsUri WHERE id = :id")
    suspend fun updateCustomLyricsUri(id: String, lyricsUri: String)

    @Query("UPDATE songs SET customArtworkUri = :artworkUri WHERE id = :id")
    suspend fun updateCustomArtworkUri(id: String, artworkUri: String)
}
