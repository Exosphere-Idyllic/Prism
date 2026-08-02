package com.example.melodyplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.melodyplayer.MainApplication
import com.example.melodyplayer.data.AlbumArtworkParams
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.SongArtworkParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf

private val DarkGrayPainter = ColorPainter(Color(0xFF1E1E2C))

/**
 * Displays artwork for a [Song].
 *
 * The actual URI resolution (WebP cache → album WebP → MediaStore URI) is handled
 * by [com.example.melodyplayer.data.ArtworkInterceptor] inside Coil — no `hasWebp`
 * boolean flags are needed here, eliminating cascading recompositions.
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 128,
    crossfade: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val context = LocalContext.current

    // FIX #10: Observe thumbnail availability so the Coil memory/disk cache key
    // changes the moment a new WebP is generated — forcing a cache-miss and a
    // fresh load. Accessing the singleton repository here avoids threading a
    // `hasWebp` flag through the entire composable tree.
    val albumId = song?.albumId ?: -1L
    val hasWebp by remember(albumId, size) {
        if (albumId > 0) {
            val repo = MainApplication.repository
            val flow = if (size <= 128) repo.albumThumbnail128Ids else repo.albumThumbnail256Ids
            flow.map { it.contains(albumId) }.distinctUntilChanged()
        } else {
            flowOf(false)
        }
    }.collectAsStateWithLifecycle(false, context = Dispatchers.Default)

    val imageRequest = remember(song?.id, size, crossfade, hasWebp) {
        song?.let {
            ImageRequest.Builder(context)
                .data(SongArtworkParams(song = it, size = size))
                .memoryCacheKey("song_art_${it.id}_${size}_$hasWebp")
                .diskCacheKey("song_art_${it.id}_${size}_$hasWebp")
                .crossfade(crossfade)
                .size(size)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            placeholder = DarkGrayPainter,
            error = DarkGrayPainter,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Displays artwork for an album.
 *
 * URI resolution is handled by [com.example.melodyplayer.data.ArtworkInterceptor].
 */
@Composable
fun AlbumArtwork(
    albumId: Long,
    coverUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 256,
    crossfade: Boolean = false,
) {
    val context = LocalContext.current

    // FIX #10: Same cache-invalidation strategy as SongArtwork.
    val hasWebp by remember(albumId, size) {
        val repo = MainApplication.repository
        val flow = if (size <= 128) repo.albumThumbnail128Ids else repo.albumThumbnail256Ids
        flow.map { it.contains(albumId) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false, context = Dispatchers.Default)

    val imageRequest = remember(albumId, coverUri, size, crossfade, hasWebp) {
        ImageRequest.Builder(context)
            .data(AlbumArtworkParams(albumId = albumId, coverUri = coverUri, size = size))
            .memoryCacheKey("album_art_${albumId}_${size}_$hasWebp")
            .diskCacheKey("album_art_${albumId}_${size}_$hasWebp")
            .crossfade(crossfade)
            .size(size)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        placeholder = DarkGrayPainter,
        error = DarkGrayPainter
    )
}
