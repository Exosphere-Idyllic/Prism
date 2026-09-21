package com.example.prism.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int
)
