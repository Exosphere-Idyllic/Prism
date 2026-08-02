package com.example.melodyplayer.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Centralized thumbnail storage location management.
 * Saves thumbnails in app-internal storage (context.filesDir/thumbnails/)
 * — no extra permissions required, always writable.
 *
 * On first access, migrates any existing thumbnails from the old external
 * storage location (Music/Prism/.thumbnails).
 */
object ThumbnailManager {
    private const val TAG = "ThumbnailManager"
    private const val THUMBNAIL_SUBDIR = "thumbnails"

    /** Cached directory — resolved once, reused forever. */
    @Volatile
    private var cachedDir: File? = null

    /**
     * Returns the persistent thumbnail directory, creating it if necessary.
     * @throws IOException if the directory cannot be created.
     */
    @Throws(IOException::class)
    fun getThumbnailDir(context: Context): File {
        cachedDir?.let { if (it.exists()) return it }

        synchronized(this) {
            cachedDir?.let { if (it.exists()) return it }

            val dir = File(context.filesDir, THUMBNAIL_SUBDIR)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Failed to create thumbnail directory: ${dir.absolutePath}")
            }
            Log.d(TAG, "Thumbnail directory ready: ${dir.absolutePath}")

            // One-time migration from old external storage location
            migrateFromOldLocation(context, dir)

            cachedDir = dir
            return dir
        }
    }

    /**
     * Get the path for a specific album thumbnail file.
     * Example: "album_42_128.webp"
     */
    fun getAlbumThumbnailFile(context: Context, albumId: Long, size: Int): File {
        val dir = try {
            getThumbnailDir(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get thumbnail dir, falling back to filesDir/thumbnails", e)
            File(context.filesDir, THUMBNAIL_SUBDIR).apply { if (!exists()) mkdirs() }
        }
        return File(dir, "album_${albumId}_${size}.webp")
    }



    /**
     * Migrates thumbnails from the old external storage location to the new
     * internal location. Runs once (old dir is deleted after migration).
     */
    private fun migrateFromOldLocation(context: Context, newDir: File) {
        try {
            // Old primary location
            val oldPrimary = File(
                android.os.Environment.getExternalStorageDirectory(),
                "Music/Prism/.thumbnails"
            )
            migrateDir(oldPrimary, newDir)

            // Old fallback location
            val oldFallback = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?.let { File(it, "Prism/.thumbnails") }
            if (oldFallback != null) {
                migrateDir(oldFallback, newDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail migration failed (non-fatal)", e)
        }
    }

    private fun migrateDir(oldDir: File, newDir: File) {
        if (!oldDir.exists() || !oldDir.isDirectory) return
        val files = oldDir.listFiles() ?: return
        var migrated = 0
        for (file in files) {
            if (file.isFile && file.extension == "webp") {
                try {
                    val dest = File(newDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = false)
                        migrated++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to migrate file ${file.name}", e)
                }
            }
        }
        // Clean up old directory after migration
        try {
            oldDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete old thumbnail directory ${oldDir.absolutePath}", e)
        }
        if (migrated > 0) {
            Log.d(TAG, "Migrated $migrated thumbnails to internal storage")
        }
    }
}
