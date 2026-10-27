package com.example.prism.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate2To3_populatesFts() {
        // 1. Crear DB versión 2 con datos
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO songs (id, title, artist, album, albumId, mediaUri, artworkUri,
                                   duration, dateModified, track)
                VALUES ('1', 'Rihanna Umbrella', 'Rihanna', 'Good Girl Gone Bad',
                        0, '', '', 0, 0, 0)
            """)
            execSQL("""
                INSERT INTO songs (id, title, artist, album, albumId, mediaUri, artworkUri,
                                   duration, dateModified, track)
                VALUES ('2', 'Rockstar', 'Post Malone', 'Beerbongs & Bentleys',
                        0, '', '', 0, 0, 0)
            """)
            close()
        }

        // 2. Ejecutar migración
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        // 3. Verificar que FTS matchea
        val count = db.query("SELECT COUNT(*) FROM songs_fts WHERE songs_fts MATCH 'rih*'")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }
        assertEquals(1, count)

        val count2 = db.query("SELECT COUNT(*) FROM songs_fts WHERE songs_fts MATCH 'rock*'")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }
        assertEquals(1, count2)
    }

    @Test
    fun migrate2To3_triggersKeepSyncOnInsert() {
        helper.createDatabase(TEST_DB, 2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.execSQL("""
            INSERT INTO songs (id, title, artist, album, albumId, mediaUri, artworkUri,
                               duration, dateModified, track)
            VALUES ('3', 'Bohemian Rhapsody', 'Queen', 'A Night at the Opera',
                    0, '', '', 0, 0, 0)
        """)

        val count = db.query("SELECT COUNT(*) FROM songs_fts WHERE songs_fts MATCH 'bohemian*'")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }
        assertEquals(1, count)
    }
}
