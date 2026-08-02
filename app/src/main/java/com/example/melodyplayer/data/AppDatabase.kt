package com.example.melodyplayer.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import java.io.File

data class SongSyncInfo(
    val id: String,
    val dateModified: Long
)

data class SongArtworkInfo(
    val id: String,
    val albumId: Long,
    val artworkUri: String
)

data class SongThumbnailInfo(
    val id: String,
    val artworkUri: String,
    val mediaUri: String,
    val albumId: Long
)

@Dao
abstract class SongDao {
    @Query("SELECT id, artworkUri, mediaUri, albumId FROM songs")
    abstract suspend fun getSongThumbnailInfo(): List<SongThumbnailInfo>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract fun getAllSongsPaging(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query ORDER BY title ASC")
    abstract fun searchSongsPaging(query: String): PagingSource<Int, Song>

    @Query("SELECT id, dateModified FROM songs")
    abstract suspend fun getSongsSyncInfo(): List<SongSyncInfo>

    @Query("SELECT id, albumId, artworkUri FROM songs")
    abstract suspend fun getSongsArtworkInfo(): List<SongArtworkInfo>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    abstract suspend fun deleteSongsByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY track ASC, title ASC")
    abstract fun getSongsByAlbum(albumId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    abstract fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun getSongByIdSync(id: String): Song?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun getSongsByIds(ids: List<String>): List<Song>

    /**
     * Fetches a bounded window of songs centred on [id] (sorted by title ASC).
     *
     * The window contains up to [half] songs before the target, the target
     * itself, and up to [half] songs after — at most [half]*2+1 rows total.
     * This replaces the full-library [getAllSongs] fallback in playback
     * scenarios where no explicit playlist is available.
     *
     * The UNION ALL approach avoids a full-table scan: each sub-query uses the
     * (title) index and the two LIMIT clauses cap the row count hard.
     */
    @Query("""
        SELECT * FROM (
            SELECT * FROM songs
            WHERE title < (SELECT title FROM songs WHERE id = :id)
               OR (title = (SELECT title FROM songs WHERE id = :id) AND id < :id)
            ORDER BY title DESC, id DESC
            LIMIT :half
        )

        UNION ALL

        SELECT * FROM songs WHERE id = :id

        UNION ALL

        SELECT * FROM (
            SELECT * FROM songs
            WHERE title > (SELECT title FROM songs WHERE id = :id)
               OR (title = (SELECT title FROM songs WHERE id = :id) AND id > :id)
            ORDER BY title ASC, id ASC
            LIMIT :half
        )
    """)
    abstract suspend fun getSongsWindowAroundId(id: String, half: Int = 25): List<Song>

    @Query("SELECT COUNT(*) FROM songs")
    abstract suspend fun getSongCount(): Int

    @Query("""
        SELECT albumId AS id, MIN(album) AS albumName, MIN(artist) AS artist, 
               COALESCE(MAX(CASE WHEN artworkUri != '' THEN artworkUri ELSE NULL END), '') AS coverPath,
               COUNT(*) AS songCount
        FROM songs
        GROUP BY albumId
    """)
    abstract suspend fun getAggregatedAlbums(): List<Album>

    @Query("""
        SELECT artist AS name, COUNT(*) AS songCount,
               COUNT(DISTINCT albumId) AS albumCount
        FROM songs
        GROUP BY artist
    """)
    abstract suspend fun getAggregatedArtists(): List<Artist>

    @Query("SELECT * FROM songs WHERE albumId IN (:albumIds)")
    abstract suspend fun getSongsByAlbumIdsSync(albumIds: List<Long>): List<Song>

    @Query("SELECT * FROM songs WHERE artist IN (:artists)")
    abstract suspend fun getSongsByArtistsSync(artists: List<String>): List<Song>
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY albumName ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE albumName LIKE :query OR artist LIKE :query ORDER BY albumName ASC")
    fun searchAlbums(query: String): Flow<List<Album>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE name LIKE :query ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<Artist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<Artist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: Artist)

    @Query("SELECT * FROM artists WHERE name = :name")
    suspend fun getByName(name: String): Artist?

    @Query("DELETE FROM artists WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM artists WHERE name IN (:names)")
    suspend fun deleteByNames(names: List<String>)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    // Single query for the whole list — avoids one Flow<Int> subscription per row.
    // LEFT JOIN + GROUP BY so playlists with zero songs still come back with count = 0.
    @Query("""
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, p.updatedAt AS updatedAt,
               COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getAllPlaylistsWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: String)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.songId 
        WHERE ps.playlistId = :playlistId 
        ORDER BY ps.position ASC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCountSync(playlistId: Long): Int

    @Query("""
        SELECT songId FROM playlist_songs 
        INNER JOIN playlists ON playlists.id = playlist_songs.playlistId 
        WHERE playlists.name = :name
    """)
    fun getPlaylistSongIdsFlow(name: String): Flow<List<String>>

    @Query("DELETE FROM playlist_songs WHERE songId IN (:songIds)")
    suspend fun deletePlaylistSongsForSongIds(songIds: List<String>)

    @Transaction
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)
}

@Database(
    entities = [Song::class, Album::class, Artist::class, Playlist::class, PlaylistSong::class, ThumbnailCacheEntry::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao

    companion object {
        /**
         * A1: Kotlin [lazy] with SYNCHRONIZED mode is equivalent to double-checked locking
         * but is idiomatic, compiler-verified, and eliminates the need for a manual @Volatile field.
         */
        private val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                // ApplicationProvider is not used here; context is passed by callers.
                // The real context is injected via the factory lambda below.
                // This property is initialised lazily on the first getDatabase() call.
                // Note: this lambda is called once and the result is cached forever.
                _context!!.applicationContext,
                AppDatabase::class.java,
                "melody_player_db"
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // FIX #5: Allow destructive migration from historical versions (1-8).
                // dropAllTables=true ensures a clean slate; without it Room would
                // attempt partial migration and crash with no Migration objects defined.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8)
                .build()
        }

        // Holds the application Context until the lazy is first accessed.
        @Volatile private var _context: Context? = null

        fun getDatabase(context: Context): AppDatabase {
            // Store the context so the lazy lambda can use it.
            // Only written once (on first call); subsequent writes are no-ops because
            // the lazy is only initialised once anyway.
            if (_context == null) _context = context.applicationContext
            return instance
        }
    }
}