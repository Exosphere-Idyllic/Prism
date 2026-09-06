package com.example.prism.domain.repository

import androidx.paging.PagingData
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Song
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getSongsFlow(query: String): Flow<PagingData<Song>>
    fun getAlbumsFlow(query: String): Flow<List<Album>>
    fun getArtistsFlow(query: String): Flow<List<Artist>>
    suspend fun getSongById(id: String?): Song?
    suspend fun getAllSongs(): List<Song>
    fun getSongsByAlbum(id: Long): Flow<List<Song>>
    fun getSongsByArtist(name: String): Flow<List<Song>>
}
