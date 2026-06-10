package com.example.melodyplayer.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val mediaUri: String,
    val artworkUri: String
)

@Serializable
data class SongList(
    val songs: List<Song> = emptyList()
)


object MockPlaylist {
    val songs = listOf(
        Song(
            id = "1",
            title = "Acoustic Journey",
            artist = "SoundHelix Band",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
        ),
        Song(
            id = "2",
            title = "Electric Symphony",
            artist = "Retro Synth",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUri = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500"
        ),
        Song(
            id = "3",
            title = "Chilled Horizon",
            artist = "Lofi Dreams",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkUri = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=500"
        ),
        Song(
            id = "4",
            title = "Neon Velocity",
            artist = "Synthwave Grid",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500"
        )
    )
}
