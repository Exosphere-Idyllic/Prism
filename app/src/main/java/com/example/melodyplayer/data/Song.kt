package com.example.melodyplayer.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Serializable
@Entity(
    tableName = "songs",
    indices = [Index(value = ["title"])]
)
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val mediaUri: String,
    val artworkUri: String
)
