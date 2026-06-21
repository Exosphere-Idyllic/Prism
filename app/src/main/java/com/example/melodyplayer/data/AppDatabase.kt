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

data class SongSyncInfo(
    val id: String,
    val dateModified: Long
)

@Dao
abstract class SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    abstract suspend fun getAllSongs(): List<Song>

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
}

@Database(entities = [Song::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

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
