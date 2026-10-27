package com.example.prism.data.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    contentEntity = Song::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "songs_fts")
data class SongFts(
    val title: String,
    val artist: String,
    val album: String,
)
