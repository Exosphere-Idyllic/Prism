package com.example.prism.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Playlist
import com.example.prism.data.entity.PlaylistSong
import com.example.prism.data.entity.Song
import com.example.prism.data.entity.SongFts

@Database(
    entities = [
        Song::class,
        Album::class,
        Artist::class,
        Playlist::class,
        PlaylistSong::class,
        SongFts::class,
    ],
    version = 3,
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
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Tabla virtual FTS4 con external content
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `songs_fts`
                    USING FTS4(
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        content=`songs`,
                        tokenize=unicode61
                    )
                """.trimIndent())

                // 2. Triggers de sincronización (Room usa este patrón para contentEntity)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_BEFORE_UPDATE`
                    BEFORE UPDATE ON `songs`
                    BEGIN
                        DELETE FROM `songs_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_BEFORE_DELETE`
                    BEFORE DELETE ON `songs`
                    BEGIN
                        DELETE FROM `songs_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_AFTER_UPDATE`
                    AFTER UPDATE ON `songs`
                    BEGIN
                        INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`)
                        VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`);
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_AFTER_INSERT`
                    AFTER INSERT ON `songs`
                    BEGIN
                        INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`)
                        VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`);
                    END
                """.trimIndent())

                // 3. Rebuild: CRÍTICO. Sin esto, usuarios con DB existente tendrían
                //    songs_fts vacío hasta que un UPDATE disparara el trigger.
                db.execSQL("INSERT INTO `songs_fts`(`songs_fts`) VALUES ('rebuild')")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "music_database",
            )
                .addMigrations(MIGRATION_2_3)
                .addCallback(
                    object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }
                    },
                )
                .build()
    }
}
