package com.example.melodyplayer.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Centralized thumbnail storage location management.
 * Saves thumbnails in internal storage / Music / Prism / .thumbnails
 */
object ThumbnailManager {
    private const val THUMBNAIL_DIR = "Music/Prism/.thumbnails"

    /**
     * Get the persistent thumbnail directory.
     * Creates it if it doesn't exist.
     */
    fun getThumbnailDir(context: Context): File {
        val baseDir = Environment.getExternalStorageDirectory()
        var dir = File(baseDir, THUMBNAIL_DIR)
        if (!dir.exists()) {
            val success = dir.mkdirs()
            if (!success) {
                // Fallback to app-specific external files dir if public is blocked
                val externalMusic = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                dir = File(externalMusic, "Prism/.thumbnails")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }
        return dir
    }

    /**
     * Get the path for a specific album thumbnail file.
     * Example: "album_42_128.webp"
     */
    fun getAlbumThumbnailFile(context: Context, albumId: Long, size: Int): File {
        return File(getThumbnailDir(context), "album_${albumId}_${size}.webp")
    }

    /**
     * Get the path for a specific song thumbnail file.
     * Example: "song_abc123_256.webp"
     */
    fun getSongThumbnailFile(context: Context, songId: String, size: Int): File {
        return File(getThumbnailDir(context), "song_${songId}_${size}.webp")
    }
}
