package com.example.melodyplayer.data

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

interface MediaStoreScanner {
    val isLoading: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun startObserving()
    fun stopObserving()
    fun triggerScan()
}

class MediaStoreScannerImpl(
    private val app: Application,
    private val scope: CoroutineScope,
    private val database: AppDatabase,
    private val onScanCompleted: suspend (upserted: List<Song>, deletedIds: List<String>, isFullScan: Boolean) -> Unit
) : MediaStoreScanner {

    companion object {
        private const val TAG = "MediaStoreScanner"
    }

    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val metadataUpdater = IncrementalMetadataUpdater(database)

    private val _isLoading = MutableStateFlow(false)
    override val isLoading = _isLoading.asStateFlow()

    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var contentObserver: ContentObserver? = null
    private var scanJob: Job? = null
    private var hasStarted = false

    private val prefs = app.getSharedPreferences("music_repository_prefs", Context.MODE_PRIVATE)
    private var lastScanTimestamp: Long
        get() = prefs.getLong("last_scan_timestamp", 0L)
        set(value) = prefs.edit { putLong("last_scan_timestamp", value) }

    private var lastMediaStoreVersion: String?
        get() = prefs.getString("last_mediastore_version", null)
        set(value) = prefs.edit { putString("last_mediastore_version", value) }

    init {
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            contentObserverEvents
                .debounce(1000.milliseconds)
                .collect {
                    performScan()
                }
        }
    }

    override fun startObserving() {
        if (contentObserver == null) {
            // B1: Handler.createAsync avoids the "Handler() is deprecated" lint warning
            // (API 28+). The async variant still dispatches callbacks on the main looper
            // but does not block waiting for each message to be handled, which is fine
            // because our onChange callback only emits to a SharedFlow (non-blocking).
            val handler = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Handler.createAsync(Looper.getMainLooper())
            } else {
                Handler(Looper.getMainLooper())
            }
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    scope.launch { contentObserverEvents.emit(Unit) }
                }
            }
            try {
                app.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
                contentObserver = observer
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register content observer", e)
            }
        }
        if (!hasStarted) {
            hasStarted = true
            scope.launch { performScan() }
        }
    }

    override fun triggerScan() {
        scope.launch { performScan() }
    }

    override fun stopObserving() {
        contentObserver?.let {
            app.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    /**
     * P6: Converted to a [suspend] function so it participates correctly in structured
     * concurrency. Callers wrap it in [scope.launch] so that cancellation and exception
     * propagation work through the coroutine hierarchy rather than being silently swallowed.
     * The previous non-suspend design spawned a nested coroutine that couldn't be
     * cancelled by the outer [scanJob] without an explicit [Job.cancel] call.
     */
    private suspend fun performScan() {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val roomSongsInfo = songDao.getSongsSyncInfo().associateBy { it.id }

                // Check MediaStore version short-circuit optimization (API 29+)
                var currentVersion: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        currentVersion = MediaStore.getVersion(app, MediaStore.VOLUME_EXTERNAL)
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not fetch MediaStore version", e)
                    }
                }

                if (roomSongsInfo.isNotEmpty() && currentVersion != null && currentVersion == lastMediaStoreVersion && lastScanTimestamp > 0L) {
                    Log.d(TAG, "MediaStore version unchanged ($currentVersion), skipping full scan")
                    return@launch
                }

                val scanFilter = if (roomSongsInfo.isEmpty()) 0L else lastScanTimestamp
                val changedSongs = queryMediaStore(scanFilter)
                val mediaStoreIds = if (scanFilter == 0L) {
                    changedSongs.asSequence().map { it.id.toLongOrNull() ?: -1L }.toSet()
                } else {
                    queryMediaStoreIds()
                }

                val toUpsert = mutableListOf<Song>()
                val toDelete = mutableListOf<String>()

                if (mediaStoreIds != null) {
                    for (song in changedSongs) {
                        val dbSongInfo = roomSongsInfo[song.id]
                        if (dbSongInfo == null || song.dateModified > dbSongInfo.dateModified) {
                            toUpsert.add(song)
                        }
                    }

                    for (dbId in roomSongsInfo.keys) {
                        val dbIdLong = dbId.toLongOrNull() ?: -1L
                        if (!mediaStoreIds.contains(dbIdLong)) {
                            toDelete.add(dbId)
                        }
                    }
                } else {
                    Log.w(TAG, "MediaStore ID query failed; skipping deletions to prevent data loss")
                    toUpsert.addAll(changedSongs)
                }

                val toUpsertIds = toUpsert.map { it.id }
                val oldSongs = if (toUpsertIds.isNotEmpty()) songDao.getSongsByIds(toUpsertIds) else emptyList()
                val songsToDelete = if (toDelete.isNotEmpty()) songDao.getSongsByIds(toDelete) else emptyList()

                val isFullScan = roomSongsInfo.isEmpty()

                withContext(NonCancellable) {
                    database.withTransaction {
                        if (toUpsert.isNotEmpty()) {
                            toUpsert.chunked(200).forEach { chunk -> songDao.insertAll(chunk) }
                        }
                        if (toDelete.isNotEmpty()) {
                            toDelete.chunked(200).forEach { chunk ->
                                songDao.deleteSongsByIds(chunk)
                                playlistDao.deletePlaylistSongsForSongIds(chunk)
                            }
                        }

                        if (toUpsert.isNotEmpty() || toDelete.isNotEmpty() || isFullScan) {
                            metadataUpdater.updateMetadata(toUpsert, oldSongs, songsToDelete, isFullScan)
                        }
                    }
                }

                lastScanTimestamp = (System.currentTimeMillis() / 1000L) - 5
                if (currentVersion != null) {
                    lastMediaStoreVersion = currentVersion
                }

                onScanCompleted(toUpsert, toDelete, isFullScan)
            } catch (e: Exception) {
                Log.e(TAG, "Error during scan", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun queryMediaStoreIds(): Set<Long>? {
        val set = mutableSetOf<Long>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            app.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    set.add(cursor.getLong(idCol))
                }
            } ?: return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore IDs", e)
            return null
        }
        return set
    }

    private fun queryMediaStore(lastScan: Long = 0L): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK
        )
        val selection = if (lastScan > 0L) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATE_MODIFIED} > $lastScan"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        val albumArtBaseUri = "content://media/external/audio/albumart".toUri()

        try {
            app.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else ""

                    list.add(Song(
                        id = id.toString(),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        album = cursor.getString(albumCol) ?: "Unknown",
                        albumId = albumId,
                        mediaUri = ContentUris.withAppendedId(uri, id).toString(),
                        artworkUri = artworkUri,
                        duration = cursor.getLong(durationCol),
                        dateModified = cursor.getLong(dateModCol),
                        track = cursor.getInt(trackCol)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }
        return list
    }

}
