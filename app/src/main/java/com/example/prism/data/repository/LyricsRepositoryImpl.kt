package com.example.prism.data.repository

import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.entity.Song
import com.example.prism.data.lyrics.EmbeddedLyricsExtractor
import com.example.prism.data.lyrics.LrcParser
import com.example.prism.domain.model.LyricsContent
import com.example.prism.domain.model.LyricsSource
import com.example.prism.domain.model.SongLyrics
import com.example.prism.domain.repository.LyricsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Implementation of [LyricsRepository] handling lyrics resolution from
 * embedded ID3 tags, external .lrc sidecars, and user-selected files.
 * Follows the Single Responsibility Principle (SRP) and ISP.
 */
class LyricsRepositoryImpl(
    context: Context,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : LyricsRepository {

    private val context = context.applicationContext
    private val songDao = database.songDao()

    override suspend fun getLyrics(song: Song): SongLyrics = withContext(dispatchers.io) {
        val lrcDeferred = async { resolveLrcLyrics(song) }
        val embeddedDeferred = async { resolveEmbeddedLyrics(song) }
        val lrcLyrics = lrcDeferred.await()
        val embeddedLyrics = embeddedDeferred.await()

        SongLyrics(
            songId = song.id,
            embeddedLyrics = embeddedLyrics,
            lrcLyrics = lrcLyrics,
            selectedSource = when {
                lrcLyrics != null -> LyricsSource.LRC_FILE
                embeddedLyrics != null -> LyricsSource.EMBEDDED
                else -> null
            },
        )
    }

    override suspend fun setCustomLyricsUri(songId: String, lyricsUri: String) = withContext(dispatchers.io) {
        songDao.updateCustomLyricsUri(songId, lyricsUri)
    }

    private fun resolveLrcLyrics(song: Song): LyricsContent? {
        // 1. User-selected custom lyrics URI (highest priority for LRC)
        if (song.customLyricsUri.isNotEmpty()) {
            try {
                val uri = song.customLyricsUri.toUri()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().use { it.readText() }
                    val parsed = LrcParser.parse(text, LyricsSource.LRC_FILE)
                    if (parsed.lines.isNotEmpty()) return parsed
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read custom lyrics URI: %s", song.customLyricsUri)
            }
        }

        // 2. Discover auto-placed .lrc sidecar file next to the audio file
        val sidecarText = findSidecarLrcText(song)
        if (!sidecarText.isNullOrBlank()) {
            val parsed = LrcParser.parse(sidecarText, LyricsSource.LRC_FILE)
            if (parsed.lines.isNotEmpty()) return parsed
        }

        return null
    }

    private fun resolveEmbeddedLyrics(song: Song): LyricsContent? {
        return EmbeddedLyricsExtractor.extract(context, song.mediaUri)
    }

    @Suppress("DEPRECATION")
    private fun findSidecarLrcText(song: Song): String? {
        return try {
            val uri = song.mediaUri.toUri()
            var filePath: String? = null

            if (uri.scheme == "file") {
                filePath = uri.path
            } else if (uri.scheme == "content") {
                // MediaStore.Audio.Media.DATA is deprecated since API 29, but remains the primary
                // fallback for locating local sibling .lrc sidecars on device storage.
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Audio.Media.DATA),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (col != -1 && cursor.moveToFirst()) {
                        filePath = cursor.getString(col)
                    }
                }
            }

            if (!filePath.isNullOrEmpty()) {
                val mediaFile = File(filePath)

                // Check same name with .lrc extension: song.mp3 -> song.lrc
                val dotIndex = filePath.lastIndexOf('.')
                val lrcPath = if (dotIndex != -1) filePath.substring(0, dotIndex) + ".lrc" else "$filePath.lrc"
                val lrcFile = File(lrcPath)
                if (lrcFile.exists() && lrcFile.canRead()) {
                    return lrcFile.readText()
                }

                // Check directory for file matching song title: <title>.lrc
                val parent = mediaFile.parentFile
                if (parent != null && parent.exists()) {
                    val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val titleLrc = File(parent, "$safeTitle.lrc")
                    if (titleLrc.exists() && titleLrc.canRead()) {
                        return titleLrc.readText()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.d(e, "Failed to resolve sidecar .lrc for %s", song.title)
            null
        }
    }
}
