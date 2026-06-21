package com.example.melodyplayer.data

import androidx.compose.runtime.mutableStateMapOf

object ThumbnailRegistry {
    val thumbnail128Set = mutableStateMapOf<Long, Boolean>()
    val thumbnail256Set = mutableStateMapOf<Long, Boolean>()

    fun add128(albumId: Long) {
        thumbnail128Set[albumId] = true
    }

    fun add256(albumId: Long) {
        thumbnail256Set[albumId] = true
    }
}
