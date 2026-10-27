package com.example.prism.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.AlbumFts
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.ArtistFts
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
        AlbumFts::class,
        ArtistFts::class,
    ],
    version = 5,
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

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Tabla virtual FTS4 con external content
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `albums_fts`
                    USING FTS4(
                        `albumName` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        content=`albums`,
                        tokenize=unicode61
                    )
                """.trimIndent())

                // 2. Triggers de sincronización
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_albums_fts_BEFORE_UPDATE`
                    BEFORE UPDATE ON `albums`
                    BEGIN
                        DELETE FROM `albums_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_albums_fts_BEFORE_DELETE`
                    BEFORE DELETE ON `albums`
                    BEGIN
                        DELETE FROM `albums_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_albums_fts_AFTER_UPDATE`
                    AFTER UPDATE ON `albums`
                    BEGIN
                        INSERT INTO `albums_fts`(`docid`, `albumName`, `artist`)
                        VALUES (NEW.`rowid`, NEW.`albumName`, NEW.`artist`);
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_albums_fts_AFTER_INSERT`
                    AFTER INSERT ON `albums`
                    BEGIN
                        INSERT INTO `albums_fts`(`docid`, `albumName`, `artist`)
                        VALUES (NEW.`rowid`, NEW.`albumName`, NEW.`artist`);
                    END
                """.trimIndent())

                // 3. Rebuild
                db.execSQL("INSERT INTO `albums_fts`(`albums_fts`) VALUES ('rebuild')")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Tabla virtual FTS4 con external content
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `artists_fts`
                    USING FTS4(
                        `name` TEXT NOT NULL,
                        content=`artists`,
                        tokenize=unicode61
                    )
                """.trimIndent())

                // 2. Triggers de sincronización
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_artists_fts_BEFORE_UPDATE`
                    BEFORE UPDATE ON `artists`
                    BEGIN
                        DELETE FROM `artists_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_artists_fts_BEFORE_DELETE`
                    BEFORE DELETE ON `artists`
                    BEGIN
                        DELETE FROM `artists_fts` WHERE `docid` = OLD.`rowid`;
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_artists_fts_AFTER_UPDATE`
                    AFTER UPDATE ON `artists`
                    BEGIN
                        INSERT INTO `artists_fts`(`docid`, `name`)
                        VALUES (NEW.`rowid`, NEW.`name`);
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_artists_fts_AFTER_INSERT`
                    AFTER INSERT ON `artists`
                    BEGIN
                        INSERT INTO `artists_fts`(`docid`, `name`)
                        VALUES (NEW.`rowid`, NEW.`name`);
                    END
                """.trimIndent())

                // 3. Rebuild
                db.execSQL("INSERT INTO `artists_fts`(`artists_fts`) VALUES ('rebuild')")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "music_database",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
