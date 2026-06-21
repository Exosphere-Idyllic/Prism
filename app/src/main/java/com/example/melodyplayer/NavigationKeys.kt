package com.example.melodyplayer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object SongList : NavKey
@Serializable data object Player : NavKey
@Serializable data class AlbumDetail(val albumId: Long, val albumName: String) : NavKey
@Serializable data class ArtistDetail(val artistName: String) : NavKey
@Serializable data class PlaylistDetail(val playlistId: Long, val playlistName: String) : NavKey
