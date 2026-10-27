package com.example.prism.data.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.prism.data.entity.Song
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class SearchBenchmarkTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedDatabase(count: Int) = runBlocking {
        val artists = listOf("Post Malone", "Rihanna", "Queen", "The Beatles", "Daft Punk", "Eminem", "Pink Floyd")
        val albums = listOf("Beerbongs & Bentleys", "Good Girl Gone Bad", "A Night at the Opera", "Abbey Road", "Discovery")
        val titles = listOf("Rockstar", "Umbrella", "Bohemian Rhapsody", "Come Together", "Harder Better Faster Stronger", "Lose Yourself", "Comfortably Numb", "Rock You Like a Hurricane", "Rocket Man")

        val songs = (1..count).map { i ->
            Song(
                id = i.toString(),
                title = "${titles[i % titles.size]} $i",
                artist = artists[i % artists.size],
                album = albums[i % albums.size],
                albumId = (i % albums.size).toLong(),
                mediaUri = "content://media/external/audio/media/$i",
                artworkUri = "",
                duration = 200_000L + i,
                dateModified = 1700000000L + i,
                track = i % 12,
            )
        }
        db.songDao().upsertPreservingUserFields(songs)
    }

    @Test
    fun benchmarkSearch() = runBlocking {
        seedDatabase(10_000)
        val query = "rock"

        val pagingParams = PagingSource.LoadParams.Refresh<Int>(
            key = null,
            loadSize = 40,
            placeholdersEnabled = false,
        )

        // Warmup
        repeat(10) {
            db.songDao().searchSongsPaging("%$query%").load(pagingParams)
            db.songDao().searchSongsFts("rock*").load(pagingParams)
        }

        val likeTime = measureNanoTime {
            repeat(100) {
                db.songDao().searchSongsPaging("%$query%").load(pagingParams)
            }
        } / 100

        val ftsTime = measureNanoTime {
            repeat(100) {
                db.songDao().searchSongsFts("rock*").load(pagingParams)
            }
        } / 100

        println("BENCHMARK - LIKE: ${likeTime / 1_000_000.0} ms, FTS: ${ftsTime / 1_000_000.0} ms")
    }
}
