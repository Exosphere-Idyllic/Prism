package com.example.melodyplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object SongListSerializer : Serializer<SongList> {
    override val defaultValue: SongList = SongList(emptyList())

    override suspend fun readFrom(input: InputStream): SongList {
        return try {
            Json.decodeFromString(
                deserializer = SongList.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: SongList, output: OutputStream) {
        withContext(Dispatchers.IO) {
            try {
                val json = Json.encodeToString(
                    serializer = SongList.serializer(),
                    value = t
                )
                output.write(json.encodeToByteArray())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

val Context.songDataStore: DataStore<SongList> by dataStore(
    fileName = "songs.json",
    serializer = SongListSerializer
)
