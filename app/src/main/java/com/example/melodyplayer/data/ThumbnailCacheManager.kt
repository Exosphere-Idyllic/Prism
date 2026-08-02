package com.example.melodyplayer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages the reactive StateFlow sets of cached thumbnail IDs.
 * Observes the [ThumbnailCacheDao] and updates in-memory sets used by the UI
 * and [ArtworkInterceptor].
 */
class ThumbnailCacheManager(
    private val thumbnailCacheDao: ThumbnailCacheDao,
    private val scope: CoroutineScope
) {
    private val _albumThumbnail128Ids = MutableStateFlow<Set<Long>>(emptySet())
    val albumThumbnail128Ids = _albumThumbnail128Ids.asStateFlow()

    private val _albumThumbnail256Ids = MutableStateFlow<Set<Long>>(emptySet())
    val albumThumbnail256Ids = _albumThumbnail256Ids.asStateFlow()

    init {
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            thumbnailCacheDao.getAllInfoFlow()
                .debounce(150.milliseconds)
                .map { list ->
                    val album128 = mutableSetOf<Long>()
                    val album256 = mutableSetOf<Long>()

                    for (info in list) {
                        if (info.type == "album") {
                            val id = info.entityId.toLongOrNull() ?: continue
                            if (info.size == 128) album128.add(id)
                            else if (info.size == 256) album256.add(id)
                        }
                    }
                    ThumbnailCachePartition(album128, album256)
                }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .collect { partition ->
                    _albumThumbnail128Ids.value = partition.album128
                    _albumThumbnail256Ids.value = partition.album256
                }
        }
    }
}

private data class ThumbnailCachePartition(
    val album128: Set<Long>,
    val album256: Set<Long>
)
