package com.example.prism.data.artwork

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ArtworkStorage {

    private const val COVERS_DIR = "covers"

    /**
     * Copies an image from a content URI (e.g., from Android PhotoPicker) into internal app storage
     * so that permission grants do not expire when the app process is terminated or recreated.
     * Returns a file:// URI string pointing to the persistent copy, or null on failure.
     */
    suspend fun copyUriToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        var targetFile: File? = null
        try {
            val coversDir = File(context.filesDir, COVERS_DIR).apply {
                if (!exists()) mkdirs()
            }
            val fileName = "cover_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val file = File(coversDir, fileName)
            targetFile = file

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                if (file.exists()) file.delete()
                return@withContext null
            }

            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            targetFile?.let { if (it.exists()) it.delete() }
            null
        }
    }

    /**
     * Clean up an old custom cover file from internal storage if it starts with file:// and lives in covers dir.
     */
    suspend fun deleteIfInternalFile(context: Context, uriString: String) = withContext(Dispatchers.IO) {
        try {
            val parsedUri = Uri.parse(uriString)
            if (parsedUri.scheme == "file") {
                val path = parsedUri.path
                if (path != null) {
                    val coversDir = File(context.filesDir, COVERS_DIR).canonicalFile
                    val coversDirPath = coversDir.path + File.separator
                    val file = File(path).canonicalFile
                    if (file.path.startsWith(coversDirPath) && file.exists()) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore deletion failures
        }
    }
}
