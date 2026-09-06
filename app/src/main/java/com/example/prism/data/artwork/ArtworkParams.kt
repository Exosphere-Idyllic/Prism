package com.example.prism.data.artwork

import com.example.prism.data.entity.Song

/**
 * Request parameters for a song artwork image in Coil.
 */
data class SongArtworkParams(
    val song: Song,
    val size: Int = 128,
)

/**
 * Request parameters for an album artwork image in Coil.
 */
data class AlbumArtworkParams(
    val albumId: Long,
    val coverUri: String,
    val customCoverUri: String = "",
    val size: Int = 256,
)
