package com.example.prism.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.PlaylistSong
import com.example.prism.data.entity.Song

@Database(
    entities = [
        Song::class,
        Album::class,
        Artist::class,
        Playlist::class,
        PlaylistSong::class,
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "music_database",
            )
                .addCallback(
                    object : Callback() {
                        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }
                    },
                )
                .build()
    }
}
