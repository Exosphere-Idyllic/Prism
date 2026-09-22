package com.example.prism.data.media

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.entity.Song
import com.example.prism.data.preferences.ScanPreferences
import kotlinx.coroutines.CoroutineScope
import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

interface MediaStoreScanner {
    val isLoading: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun startObserving()
    fun stopObserving()
    fun triggerScan()
}

class MediaStoreScannerImpl(
    private val app: Application,
    scope: CoroutineScope,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val scanPreferences: ScanPreferences = ScanPreferences(app),
    /** Called after a scan with the touched song and album IDs. */
    private val onScanChanged: ((changedSongIds: List<String>, changedAlbumIds: List<Long>, isFullScan: Boolean) -> Unit)? = null,
) : MediaStoreScanner {

    private val songDao = database.songDao()
    private val metadataUpdater = IncrementalMetadataUpdater(database)

    private val _isLoading = MutableStateFlow(value = false)
    override val isLoading = _isLoading.asStateFlow()

    private val contentObserverEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var contentObserver: ContentObserver? = null

    /**
     * Conflated channel ensuring thread-safe, sequential scanning without race conditions.
     * If multiple events or scan triggers arrive while doScan() is executing, exactly
     * one follow-up scan is triggered immediately after the active pass finishes.
     */
    private val scanRequests = Channel<Unit>(Channel.CONFLATED)

    private val hasStarted = AtomicBoolean(false)

    // Shared MediaStore projection — declared once, reused by all query helpers.
    private val songProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.TRACK,
    )

    init {
        scope.launch(dispatchers.io) {
            launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                contentObserverEvents
                    .debounce(1000.milliseconds)
                    .collect {
                        scanRequests.trySend(Unit)
                    }
            }

            while (true) {
                scanRequests.receive()
                doScan()
            }
        }
    }

    override fun startObserving() {
        if (contentObserver == null) {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    contentObserverEvents.tryEmit(Unit)
                }
            }
            try {
                app.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )
                contentObserver = observer
            } catch (e: Exception) {
                Timber.e(e, "Failed to register content observer")
            }
        }
        if (hasStarted.compareAndSet(false, true)) {
            scanRequests.trySend(Unit)
        }
    }

    override fun triggerScan() {
        scanRequests.trySend(Unit)
    }

    override fun stopObserving() {
        contentObserver?.let {
            app.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
        hasStarted.set(false)
    }

    private suspend fun doScan() {
        _isLoading.value = true
        try {
            val lastScanTimestamp = scanPreferences.getLastScanTimestamp()
            val roomSongsInfo = songDao.getSongsSyncInfo().associateBy { it.id }
            val mediaStoreIds = queryMediaStoreIds()

            val toUpsert = mutableListOf<Song>()
            val toDelete = mutableListOf<String>()
            val isFullScan = roomSongsInfo.isEmpty()

            if (mediaStoreIds != null) {
                if (isFullScan) {
                    toUpsert.addAll(querySongsFromMediaStore())
                } else {
                    val roomIdsLong = roomSongsInfo.keys.asSequence().mapNotNull { it.toLongOrNull() }.toSet()
                    val addedIds = mediaStoreIds - roomIdsLong
                    val upsertedIds = mutableSetOf<String>()

                    // 1. Detect newly added songs
                    if (addedIds.isNotEmpty()) {
                        val addedSongs = querySongsFromMediaStore(byIds = addedIds)
                        toUpsert.addAll(addedSongs)
                        addedSongs.forEach { upsertedIds.add(it.id) }
                    }

                    // 2. Detect modified songs based on modification timestamp
                    if (lastScanTimestamp > 0L) {
                        val modifiedSongs = querySongsFromMediaStore(modifiedAfter = lastScanTimestamp)
                        for (song in modifiedSongs) {
                            val dbInfo = roomSongsInfo[song.id]
                            if ((dbInfo == null) || (song.dateModified > dbInfo.dateModified)) {
                                if (upsertedIds.add(song.id)) {
                                    toUpsert.add(song)
                                }
                            }
                        }
                    }

                    // 3. Detect deleted songs
                    for (dbId in roomSongsInfo.keys) {
                        val dbIdLong = dbId.toLongOrNull() ?: -1L
                        if (!mediaStoreIds.contains(dbIdLong)) {
                            toDelete.add(dbId)
                        }
                    }
                }
            } else {
                Timber.w("MediaStore ID query failed; performing safe incremental query")
                val changed = querySongsFromMediaStore(modifiedAfter = if (isFullScan) 0L else lastScanTimestamp)
                toUpsert.addAll(changed)
            }

            // On isFullScan, oldSongs and songsToDelete are never needed.
            // On incremental scan, chunk to prevent SQLite maximum variable limit exception.
            val oldSongs = if (!isFullScan && toUpsert.isNotEmpty()) {
                toUpsert.map { it.id }.chunked(200).flatMap { songDao.getSongsByIds(it) }
            } else {
                emptyList()
            }
            val songsToDelete = if (!isFullScan && toDelete.isNotEmpty()) {
                toDelete.chunked(200).flatMap { songDao.getSongsByIds(it) }
            } else {
                emptyList()
            }

            withContext(NonCancellable) {
                database.withTransaction {
                    if (toUpsert.isNotEmpty()) {
                        toUpsert.chunked(200).forEach { chunk -> songDao.upsertPreservingUserFields(chunk) }
                    }
                    if (toDelete.isNotEmpty()) {
                        toDelete.chunked(200).forEach { chunk ->
                            songDao.deleteSongsByIds(chunk)
                        }
                    }

                    if (toUpsert.isNotEmpty() || toDelete.isNotEmpty() || isFullScan) {
                        metadataUpdater.updateMetadata(toUpsert, oldSongs, songsToDelete, isFullScan)
                    }
                }
            }

            if (toUpsert.isNotEmpty() || toDelete.isNotEmpty() || isFullScan) {
                val changedSongIds = (toUpsert.map { it.id } + toDelete).distinct()
                val changedAlbumIds = (toUpsert.map { it.albumId } + oldSongs.map { it.albumId } + songsToDelete.map { it.albumId }).distinct()
                onScanChanged?.invoke(changedSongIds, changedAlbumIds, isFullScan)
            }

            scanPreferences.setLastScanTimestamp((System.currentTimeMillis() / 1000L) - 5)
        } catch (e: Exception) {
            Timber.e(e, "Error during scan")
        } finally {
            _isLoading.value = false
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
            Timber.e(e, "Failed to query MediaStore IDs")
            return null
        }
        return set
    }

    /**
     * Unified MediaStore query — replaces the former duplicated `queryMediaStore` /
     * `queryMediaStoreByIds` pair.  All cursor projection, column mapping, and row parsing
     * live here exactly once.
     *
     * @param modifiedAfter  When > 0, adds a `DATE_MODIFIED > ?` filter for incremental scans.
     * @param byIds          When non-empty, queries only the given MediaStore IDs (in chunks).
     */
    private fun querySongsFromMediaStore(
        modifiedAfter: Long = 0L,
        byIds: Set<Long> = emptySet(),
    ): List<Song> {
        val result = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val albumArtBaseUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI

        if (byIds.isNotEmpty()) {
            byIds.chunked(200).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val selection = "${MediaStore.Audio.Media._ID} IN ($placeholders) AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                try {
                    app.contentResolver.query(uri, songProjection, selection, selectionArgs, null)
                        ?.use { cursor ->
                            val cols = SongColumnIndices.from(cursor)
                            while (cursor.moveToNext()) {
                                result.add(parseSong(cursor, uri, albumArtBaseUri, cols))
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "MediaStore chunk query failed")
                }
            }
            return result
        }

        val (selection, selectionArgs) = if (modifiedAfter > 0L) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?" to
                arrayOf(modifiedAfter.toString())
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0" to null
        }

        try {
            app.contentResolver.query(uri, songProjection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val cols = SongColumnIndices.from(cursor)
                    while (cursor.moveToNext()) {
                        result.add(parseSong(cursor, uri, albumArtBaseUri, cols))
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "MediaStore query failed")
        }
        return result
    }

    // ── Cursor helpers ────────────────────────────────────────────────────────

    private class SongColumnIndices(
        val idCol: Int,
        val titleCol: Int,
        val artistCol: Int,
        val albumCol: Int,
        val albumIdCol: Int,
        val durationCol: Int,
        val dateModifiedCol: Int,
        val trackCol: Int,
    ) {
        companion object {
            fun from(cursor: Cursor) = SongColumnIndices(
                idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
                trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
            )
        }
    }

    private fun parseSong(
        cursor: Cursor,
        uri: Uri,
        albumArtBaseUri: Uri,
        cols: SongColumnIndices,
    ): Song {
        val id = cursor.getLong(cols.idCol)
        val albumId = cursor.getLong(cols.albumIdCol)
        val artworkUri = if (albumId > 0) {
            ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
        } else ""

        return Song(
            id = id.toString(),
            title = cursor.getString(cols.titleCol) ?: "Unknown",
            artist = cursor.getString(cols.artistCol) ?: "Unknown",
            album = cursor.getString(cols.albumCol) ?: "Unknown",
            albumId = albumId,
            mediaUri = ContentUris.withAppendedId(uri, id).toString(),
            artworkUri = artworkUri,
            duration = cursor.getLong(cols.durationCol),
            dateModified = cursor.getLong(cols.dateModifiedCol),
            track = cursor.getInt(cols.trackCol),
        )
    }
}
