package com.example.prism.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.PlaylistSong
import com.example.prism.data.entity.Song

data class SongSyncInfo(
    val id: String,
    val dateModified: Long,
)

data class AlbumCustomCover(
    val id: Long,
    val customCoverUri: String,
)

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
)

data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSong::class,
            parentColumn = "playlistId",
            entityColumn = "songId",
        )
    )
    val songs: List<Song>,
)
