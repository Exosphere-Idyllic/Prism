package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.prism.data.entity.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY albumName ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE albumName LIKE :query OR artist LIKE :query ORDER BY albumName ASC")
    fun searchAlbums(query: String): Flow<List<Album>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)

    @Query("SELECT id, customCoverUri FROM albums WHERE customCoverUri != ''")
    suspend fun getAllCustomCoverUris(): List<AlbumCustomCover>

    @Query("SELECT id, customCoverUri FROM albums WHERE id IN (:ids) AND customCoverUri != ''")
    suspend fun getCustomCoverUris(ids: List<Long>): List<AlbumCustomCover>

    @Query("UPDATE albums SET customCoverUri = :uri WHERE id = :id")
    suspend fun updateCustomCover(id: Long, uri: String)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM songs)")
    suspend fun deleteOrphanedAlbums()
}
