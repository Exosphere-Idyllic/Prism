package com.example.prism.data.db

data class SongSyncInfo(
    val id: String,
    val dateModified: Long,
)

data class AlbumCustomCover(
    val id: Long,
    val customCoverUri: String,
)

