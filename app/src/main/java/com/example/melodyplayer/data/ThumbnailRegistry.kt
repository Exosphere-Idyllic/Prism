package com.example.melodyplayer.data

import androidx.compose.runtime.mutableStateMapOf

object ThumbnailRegistry {
    val thumbnail128Set = mutableStateMapOf<Long, Boolean>()
    val thumbnail256Set = mutableStateMapOf<Long, Boolean>()

    val songThumbnail128Set = mutableStateMapOf<String, Boolean>()
    val songThumbnail256Set = mutableStateMapOf<String, Boolean>()

    fun add128(albumId: Long) {
        thumbnail128Set[albumId] = true
    }

    fun add256(albumId: Long) {
        thumbnail256Set[albumId] = true
    }

    fun addSong128(songId: String) {
        songThumbnail128Set[songId] = true
    }

    fun addSong256(songId: String) {
        songThumbnail256Set[songId] = true
    }
}
