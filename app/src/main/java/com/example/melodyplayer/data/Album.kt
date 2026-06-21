package com.example.melodyplayer.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "albums")
data class Album(
    @PrimaryKey val id: Long,
    val albumName: String,
    val artist: String,
    val coverPath: String,
    val songCount: Int
)
