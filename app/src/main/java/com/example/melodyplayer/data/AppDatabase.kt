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

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM songs ORDER BY title ASC LIMIT :limit OFFSET :offset")
    suspend fun getSongsPaginated(limit: Int, offset: Int): List<Song>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongsCount(): Int

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query ORDER BY title ASC LIMIT :limit OFFSET :offset")
    suspend fun searchSongsPaginated(query: String, limit: Int, offset: Int): List<Song>

    @Query("SELECT COUNT(*) FROM songs WHERE title LIKE :query OR artist LIKE :query")
    suspend fun searchSongsCount(query: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteRemovedSongs(ids: List<String>)

    @Transaction
    suspend fun updateSongsTransaction(songs: List<Song>) {
        insertAll(songs)
        deleteRemovedSongs(songs.map { it.id })
    }
}

@Database(entities = [Song::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
