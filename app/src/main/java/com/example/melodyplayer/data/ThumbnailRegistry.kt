package com.example.melodyplayer.data

import androidx.compose.runtime.mutableStateMapOf

object ThumbnailRegistry {
    val thumbnail128Set = mutableStateMapOf<String, Boolean>()
    val thumbnail256Set = mutableStateMapOf<String, Boolean>()

    fun add128(songId: String) {
        thumbnail128Set[songId] = true
    }

    fun add256(songId: String) {
        thumbnail256Set[songId] = true
    }

    fun clear() {
        thumbnail128Set.clear()
        thumbnail256Set.clear()
    }
}
