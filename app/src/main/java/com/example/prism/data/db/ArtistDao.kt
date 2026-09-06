package com.example.prism.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.prism.data.entity.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE name LIKE :query ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<Artist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<Artist>)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()

    @Query("DELETE FROM artists WHERE name IN (:names)")
    suspend fun deleteByNames(names: List<String>)

    @Query("DELETE FROM artists WHERE name NOT IN (SELECT DISTINCT artist FROM songs)")
    suspend fun deleteOrphanedArtists()
}
