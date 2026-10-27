package com.example.prism.data.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(contentEntity = Album::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "albums_fts")
data class AlbumFts(
    val albumName: String,
    val artist: String,
)
