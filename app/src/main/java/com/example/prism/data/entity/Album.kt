package com.example.prism.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["albumName"]),
        Index(value = ["artist"])
    ]
)
data class Album(
    @PrimaryKey val id: Long,
    val albumName: String,
    val artist: String,
    val coverPath: String,
    val songCount: Int,
    val customCoverUri: String = "",
)
