package com.example.melodyplayer.ui

import android.net.Uri
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
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.ThumbnailManager

private val DarkGrayPainter = ColorPainter(Color(0xFF1E1E2C))

@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    hasWebp: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 128,
    crossfade: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current
    val sizeSuffix = if (size <= 128) "128" else "256"

    val model = remember(song?.id, hasWebp, sizeSuffix) {
        if (song == null) {
            null
        } else if (hasWebp) {
            val sizeInt = if (sizeSuffix == "128") 128 else 256
            val cacheFile = ThumbnailManager.getSongThumbnailFile(context, song.id, sizeInt)
            Uri.fromFile(cacheFile).toString()
        } else if (song.artworkUri.isNotEmpty()) {
            song.artworkUri
        } else {
            null
        }
    }

    val imageRequest = remember(model, size, crossfade) {
        if (model == null) null
        else ImageRequest.Builder(context)
            .data(model)
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

@Composable
fun AlbumArtwork(
    albumId: Long,
    coverUri: String,
    contentDescription: String?,
    hasWebp: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 256,
    crossfade: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    val context = LocalContext.current
    val sizeSuffix = if (size <= 128) "128" else "256"

    val model = remember(albumId, hasWebp, sizeSuffix) {
        if (hasWebp) {
            val sizeInt = if (sizeSuffix == "128") 128 else 256
            val cacheFile = ThumbnailManager.getAlbumThumbnailFile(context, albumId, sizeInt)
            Uri.fromFile(cacheFile).toString()
        } else if (coverUri.isNotEmpty()) {
            coverUri
        } else {
            null
        }
    }

    val imageRequest = remember(model, size, crossfade) {
        if (model == null) null
        else ImageRequest.Builder(context)
            .data(model)
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
