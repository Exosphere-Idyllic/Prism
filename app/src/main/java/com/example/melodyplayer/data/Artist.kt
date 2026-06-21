package com.example.melodyplayer.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
