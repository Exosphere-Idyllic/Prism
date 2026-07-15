package com.example.melodyplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.example.melodyplayer.data.AlbumArtworkParams
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.SongArtworkParams

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
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current

    val imageRequest = remember(song?.id, size, crossfade) {
        if (song == null) null
        else ImageRequest.Builder(context)
            .data(SongArtworkParams(song = song, size = size))
            .crossfade(crossfade)
            .size(size)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            placeholder = DarkGrayPainter,
            error = DarkGrayPainter
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
    iconSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    val context = LocalContext.current

    val imageRequest = remember(albumId, coverUri, size, crossfade) {
        ImageRequest.Builder(context)
            .data(AlbumArtworkParams(albumId = albumId, coverUri = coverUri, size = size))
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
