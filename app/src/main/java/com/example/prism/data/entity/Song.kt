package com.example.prism.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["albumId"]),
        Index(value = ["dateModified"]),
    ]
)
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val mediaUri: String,
    val artworkUri: String,
    val duration: Long,
    val dateModified: Long,
    val track: Int = 0,
    val customArtworkUri: String = "",
    @ColumnInfo(defaultValue = "''")
    val customLyricsUri: String = ""
)
