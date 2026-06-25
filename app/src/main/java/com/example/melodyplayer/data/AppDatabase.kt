package com.example.melodyplayer.data

import android.content.Context
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

data class SongSyncInfo(
    val id: String,
    val dateModified: Long
)

@Dao
abstract class SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract fun getAllSongsFlow(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract fun getAllSongsPaging(): PagingSource<Int, Song>

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query ORDER BY title ASC")
    abstract fun searchSongsPaging(query: String): PagingSource<Int, Song>

    @Query("SELECT id, dateModified FROM songs")
    abstract suspend fun getSongsSyncInfo(): List<SongSyncInfo>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    abstract suspend fun deleteSongsByIds(ids: List<String>)

    @Query("SELECT * FROM songs WHERE artworkUri != ''")
    abstract suspend fun getAllSongsWithArtwork(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY track ASC, title ASC")
    abstract fun getSongsByAlbum(albumId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    abstract fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun getSongsByIds(ids: List<String>): List<Song>

    @Query("SELECT * FROM songs WHERE albumId = :albumId")
    abstract suspend fun getSongsByAlbumSync(albumId: Long): List<Song>

    @Query("SELECT * FROM songs WHERE artist = :artist")
    abstract suspend fun getSongsByArtistSync(artist: String): List<Song>

    @Query("SELECT COUNT(*) FROM songs")
    abstract suspend fun getSongCount(): Int
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

    @Query("DELETE FROM artists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

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

    @Transaction
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)
}

@Database(
    entities = [Song::class, Album::class, Artist::class, Playlist::class, PlaylistSong::class, ThumbnailCacheEntry::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "melody_player_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}